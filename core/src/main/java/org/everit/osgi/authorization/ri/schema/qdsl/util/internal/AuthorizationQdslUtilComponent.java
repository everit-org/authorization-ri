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
package org.everit.osgi.authorization.ri.schema.qdsl.util.internal;

import java.util.Objects;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.everit.osgi.authorization.PermissionChecker;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermission;
import org.everit.osgi.authorization.ri.schema.qdsl.util.AuthorizationQdslUtil;

import com.mysema.query.sql.SQLSubQuery;
import com.mysema.query.types.Expression;
import com.mysema.query.types.expr.BooleanExpression;

@Component(name = "org.everit.osgi.authorization.ri.schema.qdsl.util.AuthorizationQdslUtil",
        configurationFactory = true, policy = ConfigurationPolicy.REQUIRE, metatype = true)
@Properties({ @Property(name = "permissionChecker.target") })
@Service
public class AuthorizationQdslUtilComponent implements AuthorizationQdslUtil {

    @Reference(bind = "setPermissionChecker")
    private PermissionChecker permissionChecker;

    @Override
    public BooleanExpression createPermissionCheckBooleanExpression(long authorizedResourceId,
            Expression<Long> targetResourceId, String... actions) {

        Objects.requireNonNull(targetResourceId, "Parameter targetResourceId must not be null");
        Objects.requireNonNull(actions, "Parameter actions must not be null");
        if (actions.length == 0) {
            throw new IllegalArgumentException("Action collection must contain at least one value");
        }

        long[] authorizationScope = permissionChecker.getAuthorizationScope(authorizedResourceId);

        SQLSubQuery subQuery = new SQLSubQuery();

        QPermission permission = QPermission.permission;

        BooleanExpression authorizedResourceIdPredicate;
        if (authorizationScope.length == 1) {
            authorizedResourceIdPredicate = permission.authorizedResourceId.eq(authorizationScope[0]);
        } else {
            // More than one as the scope contains at least one value (other branch)
            Long[] authorizationScopeLongArray = new Long[authorizationScope.length];
            for (int i = 0, n = authorizationScope.length; i < n; i++) {
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

    public void setPermissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

}
