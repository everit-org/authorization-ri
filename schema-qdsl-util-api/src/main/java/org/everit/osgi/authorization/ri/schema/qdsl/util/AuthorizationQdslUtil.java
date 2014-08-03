/**
 * This file is part of Everit - Authorization RI Querydsl Schema Util API.
 *
 * Everit - Authorization RI Querydsl Schema Util API is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Authorization RI Querydsl Schema Util API is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Authorization RI Querydsl Schema Util API.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.authorization.ri.schema.qdsl.util;

import com.mysema.query.types.Expression;
import com.mysema.query.types.expr.BooleanExpression;

public interface AuthorizationQdslUtil {

    /**
     * The same as {@link #createPermissionCheckBooleanExpression(long, Expression, String)} but with multiple actions.
     * If the authorized resource has permission to do any of the actions, the Predicate will provide <code>true</code>.
     *
     * @param authorizedResourceId
     *            The id of the resource that should be authorized.
     * @param targetResourceId
     *            Expression that provides the resource that the permission is defined on.
     * @param actions
     *            If the authorized resource has permission to do any of the actions on the target resource (directly or
     *            by inheritance), the result will provide <code>true</code>.
     * @return An expression that can be used as a predicate in the main query.
     */
    BooleanExpression createPermissionCheckBooleanExpression(long authorizedResourceId,
            Expression<Long> targetResourceId, String... actions);

}
