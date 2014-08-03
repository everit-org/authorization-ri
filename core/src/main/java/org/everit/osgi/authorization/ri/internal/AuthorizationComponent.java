/**
 * This file is part of Everit - Authorization RI.
 *
 * Everit - Authorization RI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Authorization RI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Authorization RI.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.authorization.ri.internal;

import java.sql.Connection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.everit.osgi.authorization.AuthorizationManager;
import org.everit.osgi.authorization.PermissionChecker;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermission;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermissionInheritance;
import org.everit.osgi.cache.CacheConfiguration;
import org.everit.osgi.cache.CacheFactory;
import org.everit.osgi.cache.CacheHolder;
import org.everit.osgi.querydsl.support.QuerydslSupport;
import org.everit.osgi.resource.ri.schema.qdsl.QResource;
import org.everit.osgi.transaction.helper.api.TransactionHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

import com.mysema.query.sql.Configuration;
import com.mysema.query.sql.SQLQuery;
import com.mysema.query.sql.dml.SQLDeleteClause;
import com.mysema.query.sql.dml.SQLInsertClause;

@Component(name = AuthorizationRIConstants.SERVICE_FACTORYPID_AUTHORIZATION, configurationFactory = true,
        policy = ConfigurationPolicy.REQUIRE, metatype = true)
@Properties({
        @Property(name = AuthorizationRIConstants.PROP_QUERYDSL_SUPPORT_TARGET),
        @Property(name = AuthorizationRIConstants.PROP_CACHE_FACTORY_TARGET, value = "(cacheName=noop)"),
        @Property(name = AuthorizationRIConstants.PROP_PERMISSION_CACHE_CONFIGURATION_TARGET,
                value = "(cacheName=noop)"),
        @Property(name = AuthorizationRIConstants.PROP_PERMISSION_INHERITANCE_CACHE_CONFIGURATION_TARGET,
                value = "(cacheName=noop)"),
        @Property(name = AuthorizationRIConstants.PROP_TRANSACTION_HELPER_TARGET)
})
@Service
public class AuthorizationComponent implements AuthorizationManager, PermissionChecker {

    private static long[] convertCollectionToLongArray(Collection<Long> collection) {
        long[] result = new long[collection.size()];
        Iterator<Long> iterator = collection.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            Long element = iterator.next();
            result[i] = element;
            i++;
        }
        return result;
    }

    @Reference(bind = "setCacheFactory")
    private CacheFactory cacheFactory;

    private CacheHolder<String, Boolean> pCacheHolder;

    @Reference(bind = "setPermissionCacheConfiguration")
    private CacheConfiguration<String, Boolean> permissionCacheConfiguration;

    @Reference(bind = "setPermissionInheritanceCacheConfiguration")
    private CacheConfiguration<Long, long[]> permissionInheritanceCacheConfiguration;

    private CacheHolder<Long, long[]> piCacheHolder = null;

    @Reference(name = "querydslSupport", bind = "setQdsl")
    private QuerydslSupport qdsl;

    @Reference(name = "transactionHelper", bind = "setTh")
    private TransactionHelper th;

    @Activate
    public void activate(BundleContext bundleContext) {
        ClassLoader classLoader = resolveClassLoader(bundleContext);
        piCacheHolder = cacheFactory.createCache(permissionInheritanceCacheConfiguration, classLoader);
        pCacheHolder = cacheFactory.createCache(permissionCacheConfiguration,
                classLoader);
    }

    private void addParentsRecurseToScope(long resourceId, Set<Long> authorizationScope,
            ConcurrentMap<Long, long[]> piCache) {
        long[] parentResourceIds = piCache.get(resourceId);
        if (parentResourceIds == null) {
            parentResourceIds = th.required(() -> {
                lockOnResource(resourceId);
                long[] tmpParentResourceIds = readParentResourceIdsFromDatabase(resourceId);
                piCache.put(resourceId, tmpParentResourceIds);
                return tmpParentResourceIds;
            });

        }
        for (Long parentResourceId : parentResourceIds) {
            if (!authorizationScope.contains(parentResourceId)) {
                authorizationScope.add(parentResourceId);
                addParentsRecurseToScope(parentResourceId, authorizationScope, piCache);
            }
        }
    }

    @Override
    public void addPermission(long authorizedResourceId, long targetResourceId, String action) {
        Objects.requireNonNull(action);

        th.required(() -> qdsl
                .execute((connection, configuration) -> {
                    boolean authorizedResourceExists = lockOnResource(connection, configuration, authorizedResourceId);

                    if (!authorizedResourceExists) {
                        throw new IllegalArgumentException("Authorized resource does not exist with id "
                                + authorizedResourceId);
                    }

                    QPermission p = QPermission.permission;
                    SQLInsertClause insert = new SQLInsertClause(connection, configuration, p);

                    insert
                            .set(p.authorizedResourceId, authorizedResourceId)
                            .set(p.targetResourceId, targetResourceId)
                            .set(p.action, action)
                            .execute();

                    pCacheHolder.getCache().put(generatePermissionKey(authorizedResourceId, targetResourceId, action),
                            true);

                    return null;
                }));
    }

    @Override
    public void addPermissionInheritance(long parentResourceId, long childResourceId) {
        th.required(() -> qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, childResourceId);

            QPermissionInheritance permissionInheritance = QPermissionInheritance.permissionInheritance;
            SQLInsertClause insert = new SQLInsertClause(connection, configuration, permissionInheritance);
            insert
                    .set(permissionInheritance.parentResourceId, parentResourceId)
                    .set(permissionInheritance.childResourceId, childResourceId)
                    .execute();

            piCacheHolder.getCache().remove(childResourceId);
            return null;
        }));
    }

    @Override
    public void clearCache() {
        th.required(() -> {
            piCacheHolder.getCache().clear();
            pCacheHolder.getCache().clear();
            return null;
        });

    }

    @Deactivate
    public void deactivate() {
        if (piCacheHolder != null) {
            piCacheHolder.close();
        }
        if (pCacheHolder != null) {
            pCacheHolder.close();
        }
    }

    private String generatePermissionKey(long authorizedResourceId, long targetResourceId, String action) {
        return "{" + authorizedResourceId + "," + targetResourceId + "," + action + "}";
    }

    @Override
    public long[] getAuthorizationScope(long resourceId) {
        ConcurrentMap<Long, long[]> piCache = piCacheHolder.getCache();
        Set<Long> authorizationScope = new LinkedHashSet<Long>();
        authorizationScope.add(resourceId);
        addParentsRecurseToScope(resourceId, authorizationScope, piCache);

        return convertCollectionToLongArray(authorizationScope);
    }

    @Override
    public boolean hasPermission(long authorizedResourceId, long targetResourceId, String action) {
        Objects.requireNonNull(action);

        long[] authorizationScope = getAuthorizationScope(authorizedResourceId);
        boolean permissionFound = false;

        ConcurrentMap<String, Boolean> pCache = pCacheHolder.getCache();
        for (int i = 0, n = authorizationScope.length; !permissionFound && i < n; i++) {
            long resourceIdFromScope = authorizationScope[i];
            String permissionKey = generatePermissionKey(resourceIdFromScope, targetResourceId, action);
            Boolean cachedPermission = pCache.get(permissionKey);
            if (cachedPermission == null) {
                permissionFound = th.required(() -> {
                    boolean exists = lockOnResource(authorizedResourceId);
                    if (!exists && resourceIdFromScope == authorizedResourceId) {
                        return false;
                    }
                    boolean tmpHasPermission = readPermissionFromDatabase(resourceIdFromScope, targetResourceId,
                            action);
                    pCache.put(permissionKey, tmpHasPermission);
                    return tmpHasPermission;
                });
            } else {
                permissionFound = cachedPermission;
            }

        }
        return permissionFound;
    }

    private boolean lockOnResource(Connection connection, Configuration configuration, long resourceId) {
        SQLQuery query = new SQLQuery(connection, configuration);
        QResource resource = QResource.resource;
        List<Long> results = query.from(resource).where(resource.resourceId.eq(resourceId)).forUpdate()
                .list(resource.resourceId);
        if (results.size() == 0) {
            return false;
        } else {
            return true;
        }
    }

    private boolean lockOnResource(long resourceId) {
        return qdsl.execute((connection, configuration) -> {
            return lockOnResource(connection, configuration, resourceId);
        });
    }

    private long[] readParentResourceIdsFromDatabase(long resourceId) {
        return qdsl.execute((connection, configuration) -> {
            SQLQuery query = new SQLQuery(connection, configuration);
            QPermissionInheritance permissioninheritance = QPermissionInheritance.permissionInheritance;
            List<Long> result = query.from(permissioninheritance)
                    .where(permissioninheritance.childResourceId.eq(resourceId))
                    .list(permissioninheritance.parentResourceId);

            return convertCollectionToLongArray(result);
        });
    }

    private boolean readPermissionFromDatabase(long authorizedResourceId, long targetResourceId, String action) {
        return qdsl.execute((connection, configuration) -> {
            SQLQuery query = new SQLQuery(connection, configuration);
            QPermission permission = QPermission.permission;
            return query.from(permission)
                    .where(permission.authorizedResourceId.eq(authorizedResourceId)
                            .and(permission.targetResourceId.eq(targetResourceId))
                            .and(permission.action.eq(action)))
                    .exists();
        });
    }

    @Override
    public void removePermission(long authorizedResourceId, long targetResourceId, String action) {
        Objects.requireNonNull(action);

        th.required(() -> qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, authorizedResourceId);
            QPermission permission = QPermission.permission;
            SQLDeleteClause sql = new SQLDeleteClause(connection, configuration, permission);

            sql.where(
                    permission.authorizedResourceId.eq(authorizedResourceId)
                            .and(permission.targetResourceId.eq(targetResourceId))
                            .and(permission.action.eq(action)))
                    .execute();

            String permissionKey = generatePermissionKey(authorizedResourceId, targetResourceId, action);
            pCacheHolder.getCache().put(permissionKey, false);
            return null;
        }));
    }

    @Override
    public void removePermissionInheritance(long parentResourceId, long childResourceId) {
        th.required(() -> qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, childResourceId);

            QPermissionInheritance permissioninheritance = QPermissionInheritance.permissionInheritance;
            SQLDeleteClause sql = new SQLDeleteClause(connection, configuration, permissioninheritance);

            sql.where(
                    permissioninheritance.parentResourceId.eq(parentResourceId)
                            .and(permissioninheritance.childResourceId.eq(childResourceId)))
                    .execute();

            piCacheHolder.getCache().remove(childResourceId);
            return null;
        }));
    }

    /**
     * In case this class is used outside OSGi, this method should be overridden.
     *
     * @param bundleContext
     *            The context of the bundle.
     * @return The classloader.
     */
    protected ClassLoader resolveClassLoader(BundleContext bundleContext) {
        Bundle bundle = bundleContext.getBundle();
        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
        return bundleWiring.getClassLoader();
    }

    public void setCacheFactory(CacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    public void setPermissionCacheConfiguration(CacheConfiguration<String, Boolean> permissionCacheConfiguration) {
        this.permissionCacheConfiguration = permissionCacheConfiguration;
    }

    public void setPermissionInheritanceCacheConfiguration(
            CacheConfiguration<Long, long[]> permissionInheritanceCacheConfiguration) {
        this.permissionInheritanceCacheConfiguration = permissionInheritanceCacheConfiguration;
    }

    public void setQdsl(QuerydslSupport qdsl) {
        this.qdsl = qdsl;
    }

    public void setTh(TransactionHelper th) {
        this.th = th;
    }

}
