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
import java.util.Arrays;
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
import org.everit.osgi.authorization.ri.AuthorizationRIConstants;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermission;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermissionInheritance;
import org.everit.osgi.authorization.ri.schema.qdsl.util.AuthorizationQdslUtil;
import org.everit.osgi.props.PropertyManager;
import org.everit.osgi.querydsl.support.QuerydslSupport;
import org.everit.osgi.resource.ResourceService;
import org.everit.osgi.resource.ri.schema.qdsl.QResource;
import org.everit.osgi.transaction.helper.api.TransactionHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

import com.mysema.query.sql.Configuration;
import com.mysema.query.sql.SQLQuery;
import com.mysema.query.sql.SQLSubQuery;
import com.mysema.query.sql.dml.SQLDeleteClause;
import com.mysema.query.sql.dml.SQLInsertClause;
import com.mysema.query.types.Expression;
import com.mysema.query.types.expr.BooleanExpression;
import com.mysema.query.types.template.BooleanTemplate;

@Component(name = AuthorizationRIConstants.SERVICE_FACTORYPID_AUTHORIZATION, configurationFactory = true,
        policy = ConfigurationPolicy.REQUIRE, metatype = true)
@Properties({
        @Property(name = Constants.SERVICE_DESCRIPTION, propertyPrivate = false,
                value = AuthorizationRIConstants.DEFAULT_SERVICE_DESCRIPTION),
        @Property(name = AuthorizationRIConstants.PROP_QUERYDSL_SUPPORT_TARGET),
        @Property(name = AuthorizationRIConstants.PROP_PROPERTY_MANAGER_TARGET),
        @Property(name = AuthorizationRIConstants.PROP_RESOURCE_SERVICE_TARGET),
        @Property(name = AuthorizationRIConstants.PROP_PERMISSION_CACHE_TARGET,
                value = "(cache.name=noop)"),
        @Property(name = AuthorizationRIConstants.PROP_PERMISSION_INHERITANCE_CACHE_TARGET,
                value = "(cache.name=noop)"),
        @Property(name = AuthorizationRIConstants.PROP_TRANSACTION_HELPER_TARGET)
})
@Service
public class AuthorizationComponent implements AuthorizationManager, PermissionChecker, AuthorizationQdslUtil {

    private static long[] convertCollectionToLongArray(final Collection<Long> collection) {
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

    @Reference(bind = "setPermissionCache")
    private ConcurrentMap<String, Boolean> permissionCache;

    @Reference(bind = "setPermissionInheritanceCache")
    private ConcurrentMap<Long, long[]> permissionInheritanceCache;

    @Reference(name = "querydslSupport", bind = "setQdsl")
    private QuerydslSupport qdsl;

    @Reference(name = "transactionHelper", bind = "setTh")
    private TransactionHelper th;

    @Reference(bind = "setPropertyManager")
    private PropertyManager propertyManager;

    @Reference(bind = "setResourceService")
    private ResourceService resourceService;

    private long systemResourceId;

    @Activate
    public void activate(final BundleContext bundleContext) {
        String systemResourceIdProperty =
                propertyManager.getProperty(AuthorizationRIConstants.PROP_SYSTEM_RESOURCE_ID);
        if ((systemResourceIdProperty == null) || "".equals(systemResourceIdProperty)) {
            systemResourceId = resourceService.createResource();
            propertyManager.addProperty(
                    AuthorizationRIConstants.PROP_SYSTEM_RESOURCE_ID, String.valueOf(systemResourceId));
        } else {
            systemResourceId = Long.valueOf(systemResourceIdProperty).longValue();
        }
    }

    private void addParentsRecurseToScope(final long resourceId, final Set<Long> authorizationScope) {
        long[] parentResourceIds = permissionInheritanceCache.get(resourceId);
        if (parentResourceIds == null) {
            parentResourceIds = th.required(() -> {
                lockOnResource(resourceId);
                long[] tmpParentResourceIds = readParentResourceIdsFromDatabase(resourceId);
                permissionInheritanceCache.put(resourceId, tmpParentResourceIds);
                return tmpParentResourceIds;
            });

        }
        for (Long parentResourceId : parentResourceIds) {
            if (!authorizationScope.contains(parentResourceId)) {
                authorizationScope.add(parentResourceId);
                addParentsRecurseToScope(parentResourceId, authorizationScope);
            }
        }
    }

    @Override
    public void addPermission(final long authorizedResourceId, final long targetResourceId, final String action) {
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

                    permissionCache.put(generatePermissionKey(authorizedResourceId, targetResourceId, action), true);

                    return null;
                }));
    }

    @Override
    public void addPermissionInheritance(final long parentResourceId, final long childResourceId) {
        th.required(() -> qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, childResourceId);

            QPermissionInheritance permissionInheritance = QPermissionInheritance.permissionInheritance;
            SQLInsertClause insert = new SQLInsertClause(connection, configuration, permissionInheritance);
            insert
                    .set(permissionInheritance.parentResourceId, parentResourceId)
                    .set(permissionInheritance.childResourceId, childResourceId)
                    .execute();

            permissionInheritanceCache.remove(childResourceId);
            return null;
        }));
    }

    @Override
    public BooleanExpression authorizationPredicate(final long authorizedResourceId,
            final Expression<Long> targetResourceId, final String... actions) {
        if (authorizedResourceId == systemResourceId) {
            return BooleanTemplate.TRUE;
        }

        Objects.requireNonNull(targetResourceId, "Parameter targetResourceId must not be null");
        validateActionsParameter(actions);

        long[] authorizationScope = getAuthorizationScope(authorizedResourceId);

        SQLSubQuery subQuery = new SQLSubQuery();

        QPermission permission = QPermission.permission;

        BooleanExpression authorizedResourceIdPredicate;
        if (authorizationScope.length == 1) {
            authorizedResourceIdPredicate = permission.authorizedResourceId.eq(authorizationScope[0]);
        } else {
            // More than one as the scope contains at least one value (other branch)
            Long[] authorizationScopeLongArray = new Long[authorizationScope.length];
            for (int i = 0, n = authorizationScope.length; i < n; i++) {
                if (authorizationScope[i] == systemResourceId) {
                    return BooleanTemplate.TRUE;
                }
                authorizationScopeLongArray[i] = authorizationScope[i];
            }

            authorizedResourceIdPredicate = permission.authorizedResourceId.in(authorizationScopeLongArray);
        }

        BooleanExpression actionPredicate = null;

        if (actions.length == 1) {
            actionPredicate = permission.action.eq(actions[0]);
        } else {
            actionPredicate = permission.action.in(actions);
        }

        return subQuery.from(permission)
                .where(permission.targetResourceId.eq(targetResourceId).and(
                        actionPredicate.and(authorizedResourceIdPredicate)))
                .exists();
    }

    @Override
    public void clearCache() {
        th.required(() -> {
            permissionInheritanceCache.clear();
            permissionCache.clear();
            return null;
        });

    }

    @Deactivate
    public void deactivate() {
        clearCache();
    }

    private String generatePermissionKey(final long authorizedResourceId, final long targetResourceId,
            final String action) {
        return "{" + authorizedResourceId + "," + targetResourceId + "," + action + "}";
    }

    @Override
    public long[] getAuthorizationScope(final long resourceId) {
        Set<Long> authorizationScope = new LinkedHashSet<Long>();
        authorizationScope.add(resourceId);
        addParentsRecurseToScope(resourceId, authorizationScope);

        return AuthorizationComponent.convertCollectionToLongArray(authorizationScope);
    }

    @Override
    public long getSystemResourceId() {
        return systemResourceId;
    }

    @Override
    public boolean hasPermission(final long authorizedResourceId, final long targetResourceId, final String... actions) {
        if (authorizedResourceId == systemResourceId) {
            return true;
        }

        validateActionsParameter(actions);

        long[] authorizationScope = getAuthorizationScope(authorizedResourceId);
        boolean permissionFound = false;

        for (int j = 0, m = actions.length; !permissionFound && (j < m); j++) {
            final String action = actions[j];

            for (int i = 0, n = authorizationScope.length; !permissionFound && (i < n); i++) {
                long resourceIdFromScope = authorizationScope[i];

                if (resourceIdFromScope == systemResourceId) {
                    return true;
                }

                String permissionKey = generatePermissionKey(resourceIdFromScope, targetResourceId, action);
                Boolean cachedPermission = permissionCache.get(permissionKey);

                if (cachedPermission == null) {
                    permissionFound = th.required(() -> {
                        boolean exists = lockOnResource(authorizedResourceId);
                        if (!exists && (resourceIdFromScope == authorizedResourceId)) {
                            return false;
                        }
                        boolean tmpHasPermission = readPermissionFromDatabase(resourceIdFromScope, targetResourceId,
                                action);
                        permissionCache.put(permissionKey, tmpHasPermission);
                        return tmpHasPermission;
                    });
                } else {
                    permissionFound = cachedPermission;
                }

            }
        }
        return permissionFound;
    }

    private boolean lockOnResource(final Connection connection, final Configuration configuration, final long resourceId) {
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

    private boolean lockOnResource(final long resourceId) {
        return qdsl.execute((connection, configuration) -> {
            return lockOnResource(connection, configuration, resourceId);
        });
    }

    private long[] readParentResourceIdsFromDatabase(final long resourceId) {
        return qdsl.execute((connection, configuration) -> {
            SQLQuery query = new SQLQuery(connection, configuration);
            QPermissionInheritance permissioninheritance = QPermissionInheritance.permissionInheritance;
            List<Long> result = query.from(permissioninheritance)
                    .where(permissioninheritance.childResourceId.eq(resourceId))
                    .list(permissioninheritance.parentResourceId);

            return AuthorizationComponent.convertCollectionToLongArray(result);
        });
    }

    private boolean readPermissionFromDatabase(final long authorizedResourceId, final long targetResourceId,
            final String action) {
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
    public void removePermission(final long authorizedResourceId, final long targetResourceId, final String action) {
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
            permissionCache.put(permissionKey, false);
            return null;
        }));
    }

    @Override
    public void removePermissionInheritance(final long parentResourceId, final long childResourceId) {
        th.required(() -> qdsl.execute((connection, configuration) -> {
            lockOnResource(connection, configuration, childResourceId);

            QPermissionInheritance permissioninheritance = QPermissionInheritance.permissionInheritance;
            SQLDeleteClause sql = new SQLDeleteClause(connection, configuration, permissioninheritance);

            sql.where(
                    permissioninheritance.parentResourceId.eq(parentResourceId)
                            .and(permissioninheritance.childResourceId.eq(childResourceId)))
                    .execute();

            permissionInheritanceCache.remove(childResourceId);
            return null;
        }));
    }

    public void setPermissionCache(final ConcurrentMap<String, Boolean> permissionCache) {
        this.permissionCache = permissionCache;
    }

    public void setPermissionInheritanceCache(final ConcurrentMap<Long, long[]> permissionInheritanceCache) {
        this.permissionInheritanceCache = permissionInheritanceCache;
    }

    public void setPropertyManager(final PropertyManager propertyManager) {
        this.propertyManager = propertyManager;
    }

    public void setQdsl(final QuerydslSupport qdsl) {
        this.qdsl = qdsl;
    }

    public void setResourceService(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public void setTh(final TransactionHelper th) {
        this.th = th;
    }

    private void validateActionsParameter(final String... actions) {
        Objects.requireNonNull(actions, "Parameter actions must not be null");
        if (actions.length == 0) {
            throw new IllegalArgumentException("Action collection must contain at least one value");
        }
        for (String action : actions) {
            Objects.requireNonNull(action, "Null action was passed in actions parameter: " + Arrays.toString(actions));
        }
    }

}
