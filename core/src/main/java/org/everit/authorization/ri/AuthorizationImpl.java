/*
 * Copyright (C) 2011 Everit Kft. (http://www.everit.biz)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.authorization.ri;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.everit.authorization.AuthorizationManager;
import org.everit.authorization.PermissionChecker;
import org.everit.authorization.qdsl.util.AuthorizationQdslUtil;
import org.everit.authorization.ri.schema.qdsl.QPermission;
import org.everit.authorization.ri.schema.qdsl.QPermissionInheritance;
import org.everit.persistence.querydsl.support.QuerydslSupport;
import org.everit.props.PropertyManager;
import org.everit.resource.ResourceService;
import org.everit.resource.ri.schema.qdsl.QResource;
import org.everit.transaction.propagator.TransactionPropagator;

import com.mysema.query.sql.Configuration;
import com.mysema.query.sql.SQLQuery;
import com.mysema.query.sql.SQLSubQuery;
import com.mysema.query.sql.dml.SQLDeleteClause;
import com.mysema.query.sql.dml.SQLInsertClause;
import com.mysema.query.types.Expression;
import com.mysema.query.types.expr.BooleanExpression;
import com.mysema.query.types.template.BooleanTemplate;

/**
 * Implementation of {@link AuthorizationManager}, {@link PermissionChecker} and
 * {@link AuthorizationQdslUtil}.
 */
public class AuthorizationImpl
    implements AuthorizationManager, PermissionChecker, AuthorizationQdslUtil {

  public static final String PROP_SYSTEM_RESOURCE_ID =
      "org.everit.osgi.authorization.ri.SYSTEM_RESOURCE_ID";

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

  private ConcurrentMap<String, Boolean> permissionCache;

  private ConcurrentMap<Long, long[]> permissionInheritanceCache;

  private QuerydslSupport querydslSupport;

  private long systemResourceId;

  private TransactionPropagator transactionPropagator;

  /**
   * Constructor.
   *
   * @param propertyManager
   *          the {@link PropertyManager} instance.
   * @param resourceService
   *          the {@link ResourceService} instance.
   * @param transactionPropagator
   *          the {@link TransactionPropagator} instance.
   * @param querydslSupport
   *          the {@link QuerydslSupport} instance.
   * @param permissionCache
   *          the {@link ConcurrentMap} that stores the permission records.
   * @param permissionInheritanceCache
   *          the {@link ConcurrentMap} that stores the permission inheritance records.
   *
   * @throws NullPointerException
   *           if one of the parameter is <code>null</code>.
   */
  public AuthorizationImpl(final PropertyManager propertyManager,
      final ResourceService resourceService, final TransactionPropagator transactionPropagator,
      final QuerydslSupport querydslSupport, final ConcurrentMap<String, Boolean> permissionCache,
      final ConcurrentMap<Long, long[]> permissionInheritanceCache) {
    Objects.requireNonNull(propertyManager, "propertyManager cannot be null");
    Objects.requireNonNull(resourceService, "resourceService cannot be null");
    this.transactionPropagator =
        Objects.requireNonNull(transactionPropagator, "transactionPropagator cannot be null");
    this.querydslSupport =
        Objects.requireNonNull(querydslSupport, "transactionPropagator cannot be null");
    this.permissionCache =
        Objects.requireNonNull(permissionCache, "permissionCache cannot be null");
    this.permissionInheritanceCache = Objects.requireNonNull(permissionInheritanceCache,
        "permissionInheritanceCache cannot be null");

    init(propertyManager, resourceService);
  }

  private void addParentsRecurseToScope(final long resourceId, final Set<Long> authorizationScope) {
    long[] parentResourceIds = permissionInheritanceCache.get(resourceId);
    if (parentResourceIds == null) {
      parentResourceIds = transactionPropagator.required(() -> {
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
  public void addPermission(final long authorizedResourceId, final long targetResourceId,
      final String action) {
    Objects.requireNonNull(action);

    transactionPropagator.required(() -> querydslSupport.execute((connection, configuration) -> {
      boolean authorizedResourceExists =
          lockOnResource(connection, configuration, authorizedResourceId);

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

      permissionCache.put(generatePermissionKey(authorizedResourceId, targetResourceId, action),
          true);

      return null;
    }));
  }

  @Override
  public void addPermissionInheritance(final long parentResourceId, final long childResourceId) {
    transactionPropagator.required(() -> querydslSupport.execute((connection, configuration) -> {
      lockOnResource(connection, configuration, childResourceId);

      QPermissionInheritance permissionInheritance = QPermissionInheritance.permissionInheritance;
      SQLInsertClause insert =
          new SQLInsertClause(connection, configuration, permissionInheritance);
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

      authorizedResourceIdPredicate =
          permission.authorizedResourceId.in(authorizationScopeLongArray);
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
    transactionPropagator.required(() -> {
      permissionInheritanceCache.clear();
      permissionCache.clear();
      return null;
    });

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

    return AuthorizationImpl.convertCollectionToLongArray(authorizationScope);
  }

  @Override
  public long getSystemResourceId() {
    return systemResourceId;
  }

  @Override
  public boolean hasPermission(final long authorizedResourceId, final long targetResourceId,
      final String... actions) {
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
          permissionFound = transactionPropagator.required(() -> {
            boolean exists = lockOnResource(authorizedResourceId);
            if (!exists && (resourceIdFromScope == authorizedResourceId)) {
              return false;
            }
            boolean tmpHasPermission =
                readPermissionFromDatabase(resourceIdFromScope, targetResourceId,
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

  private void init(final PropertyManager propertyManager, final ResourceService resourceService) {
    String systemResourceIdProperty = propertyManager.getProperty(PROP_SYSTEM_RESOURCE_ID);
    if ((systemResourceIdProperty == null) || "".equals(systemResourceIdProperty)) {
      systemResourceId = resourceService.createResource();
      propertyManager.addProperty(PROP_SYSTEM_RESOURCE_ID, String.valueOf(systemResourceId));
    } else {
      systemResourceId = Long.parseLong(systemResourceIdProperty);
    }
  }

  private boolean lockOnResource(final Connection connection, final Configuration configuration,
      final long resourceId) {
    SQLQuery query = new SQLQuery(connection, configuration);
    QResource resource = QResource.resource;
    List<Long> results = query.from(resource).where(resource.resourceId.eq(resourceId)).forUpdate()
        .list(resource.resourceId);
    return !(results.size() == 0);
  }

  private boolean lockOnResource(final long resourceId) {
    return querydslSupport.execute((connection, configuration) -> {
      return lockOnResource(connection, configuration, resourceId);
    });
  }

  private long[] readParentResourceIdsFromDatabase(final long resourceId) {
    return querydslSupport.execute((connection, configuration) -> {
      SQLQuery query = new SQLQuery(connection, configuration);
      QPermissionInheritance permissioninheritance = QPermissionInheritance.permissionInheritance;
      List<Long> result = query.from(permissioninheritance)
          .where(permissioninheritance.childResourceId.eq(resourceId))
          .list(permissioninheritance.parentResourceId);

      return AuthorizationImpl.convertCollectionToLongArray(result);
    });
  }

  private boolean readPermissionFromDatabase(final long authorizedResourceId,
      final long targetResourceId,
      final String action) {
    return querydslSupport.execute((connection, configuration) -> {
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
  public void removePermission(final long authorizedResourceId, final long targetResourceId,
      final String action) {
    Objects.requireNonNull(action);

    transactionPropagator.required(() -> querydslSupport.execute((connection, configuration) -> {
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
    transactionPropagator.required(() -> querydslSupport.execute((connection, configuration) -> {
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

  private void validateActionsParameter(final String... actions) {
    Objects.requireNonNull(actions, "Parameter actions must not be null");
    if (actions.length == 0) {
      throw new IllegalArgumentException("Action collection must contain at least one value");
    }
    for (String action : actions) {
      Objects.requireNonNull(action,
          "Null action was passed in actions parameter: " + Arrays.toString(actions));
    }
  }

}
