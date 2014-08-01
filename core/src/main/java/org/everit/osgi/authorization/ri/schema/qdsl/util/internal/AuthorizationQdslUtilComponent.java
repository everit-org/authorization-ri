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

import java.util.Collection;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.everit.osgi.authorization.PermissionChecker;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermission;
import org.everit.osgi.authorization.ri.schema.qdsl.util.AuthorizationQdslUtil;

import com.mysema.query.sql.SQLSubQuery;
import com.mysema.query.types.Expression;
import com.mysema.query.types.expr.BooleanExpression;

@Component(configurationFactory = true, policy = ConfigurationPolicy.REQUIRE)
@Service
public class AuthorizationQdslUtilComponent implements AuthorizationQdslUtil {

    @Reference(bind = "setPermissionChecker")
    private PermissionChecker permissionChecker;

    @Override
    public BooleanExpression createPermissionCheckBooleanExpression(long authorizedResourceId,
            Expression<Long> targetResourceId, Collection<String> actions) {

        Set<Long> authorizationScope = permissionChecker.getAuthorizationScope(authorizedResourceId);

        SQLSubQuery subQuery = new SQLSubQuery();

        QPermission permission = QPermission.permission;

        return subQuery.from(permission)
                .where(permission.targetResourceId.eq(targetResourceId).and(
                        permission.action.in(actions).and(permission.authorizedResourceId.in(authorizationScope))))
                .exists();
    }

    @Override
    public BooleanExpression createPermissionCheckBooleanExpression(long authorizedResourceId,
            Expression<Long> targetResourceId, String action) {

        Set<Long> authorizationScope = permissionChecker.getAuthorizationScope(authorizedResourceId);

        SQLSubQuery subQuery = new SQLSubQuery();

        QPermission permission = QPermission.permission;

        return subQuery.from(permission)
                .where(permission.targetResourceId.eq(targetResourceId).and(
                        permission.action.eq(action).and(permission.authorizedResourceId.in(authorizationScope))))
                .exists();
    }

    public void setPermissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

}
