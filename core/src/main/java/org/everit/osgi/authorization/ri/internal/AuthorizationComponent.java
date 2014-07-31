package org.everit.osgi.authorization.ri.internal;

import java.sql.Connection;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.everit.osgi.authorization.AuthorizationManager;
import org.everit.osgi.authorization.PermissionChecker;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermission;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermissionInheritance;
import org.everit.osgi.cache.api.CacheConfiguration;
import org.everit.osgi.cache.api.CacheFactory;
import org.everit.osgi.cache.api.CacheHolder;
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
@Properties({ @Property(name = "querydslSupport.target"), @Property(name = "transactionHelper.target"),
        @Property(name = "cacheFactory.target", value = "(cacheName=noop)") })
public class AuthorizationComponent implements AuthorizationManager, PermissionChecker {

    @Reference(bind = "setCacheFactory")
    private CacheFactory cacheFactory;

    @Reference(bind = "setPermissionCacheConfiguration")
    private CacheConfiguration<String, Boolean> permissionCacheConfiguration;

    @Reference(bind = "setPermissionInheritanceCacheConfiguration")
    private CacheConfiguration<Long, long[]> permissionInheritanceCacheConfiguration;

    private CacheHolder<Long, long[]> piCacheHolder = null;

    @Reference(name = "querydslSupport", bind = "setQdsl")
    private QuerydslSupport qdsl;

    @Reference(name = "transactionHelper", bind = "setTh")
    private TransactionHelper th;

    private CacheHolder<String, Boolean> pCacheHolder;

    @Activate
    public void activate(BundleContext bundleContext) {
        ClassLoader classLoader = resolveClassLoader(bundleContext);
        piCacheHolder = cacheFactory.createCache(permissionInheritanceCacheConfiguration, classLoader);
        pCacheHolder = cacheFactory.createCache(permissionCacheConfiguration,
                classLoader);
    }

    @Override
    public void addPermission(long authorizedResourceId, long targetResourceId, String action) {
        th.required(() -> qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, authorizedResourceId);

            QPermission p = QPermission.permission;
            SQLInsertClause insert = new SQLInsertClause(connection, configuration, p);

            insert
                    .set(p.authorizedResourceId, authorizedResourceId)
                    .set(p.targetResourceId, targetResourceId)
                    .set(p.action, action)
                    .execute();

            pCacheHolder.getCache().put(generatePermissionKey(authorizedResourceId, targetResourceId, action), true);

            return null;
        }));
    }

    private void lockOnResource(long resourceId) {
        qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, resourceId);
            return null;
        });
    }

    private void lockOnResource(Connection connection, Configuration configuration, long resourceId) {
        SQLQuery query = new SQLQuery(connection, configuration);
        QResource resource = QResource.resource;
        List<Long> results = query.from(resource).where(resource.resourceId.eq(resourceId)).forUpdate()
                .list(resource.resourceId);
        if (results.size() == 0) {
            throw new IllegalArgumentException("Resource does not exist: " + resourceId);
        }
    }

    @Override
    public void addPermissionInheritance(long parentResourceId, long childResourceId) {
        th.required(() -> qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, parentResourceId);

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

    @Override
    public Set<Long> getAuthorizationScope(long resourceId) {
        ConcurrentMap<Long, long[]> piCache = piCacheHolder.getCache();
        Set<Long> authorizationScope = new HashSet<Long>();
        authorizationScope.add(resourceId);
        addParentsRecurseToScope(resourceId, authorizationScope, piCache);

        return authorizationScope;
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

    @Override
    public boolean hasPermission(long authorizedResourceId, long targetResourceId, String action) {
        Set<Long> authorizationScope = getAuthorizationScope(authorizedResourceId);
        boolean permissionFound = false;
        Iterator<Long> scopeIterator = authorizationScope.iterator();
        ConcurrentMap<String, Boolean> pCache = pCacheHolder.getCache();
        while (!permissionFound && scopeIterator.hasNext()) {
            String permissionKey = generatePermissionKey(authorizedResourceId, targetResourceId, action);
            Boolean cachedPermission = pCache.get(permissionKey);
            if (cachedPermission == null) {
                permissionFound = th.required(() -> {
                    lockOnResource(authorizedResourceId);
                    boolean tmpHasPermission = readPermissionFromDatabase(authorizedResourceId, targetResourceId,
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

    private String generatePermissionKey(long authorizedResourceId, long targetResourceId, String action) {
        return "{" + authorizedResourceId + "," + targetResourceId + "," + action + "}";
    }

    @Override
    public void removePermission(long authorizedResourceId, long targetResourceId, String action) {
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
            QPermissionInheritance permissioninheritance = QPermissionInheritance.permissionInheritance;
            SQLDeleteClause sql = new SQLDeleteClause(connection, configuration, permissioninheritance);

            sql.where(
                    permissioninheritance.parentResourceId.eq(parentResourceId)
                            .and(permissioninheritance.childResourceId.eq(childResourceId)))
                    .execute();
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
