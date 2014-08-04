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
     * Creates a predicate for a Querydsl based SQL query. If the predicate is appended to the where part of the main
     * query, the results will be filtered based on authorization.<br>
     * <br>
     * E.g.: Users and books are resources. Users can view and edit books. The authorized resource id is the resourceId
     * of the user that is currently authenticated by the system. To list the books that the currently authenticated
     * user can view or edit, the following code snippet should be used:
     *
     * <pre>
     * QBook book = QBook.book;
     * BooleanExpression authrPredicate =
     *         authorizationQdslUtil.authorizationPredicate(userResourceId, book.resourceId, "read", "edit");
     *
     * SQLQuery query = new SQLQuery(connection, configuration);
     *
     * return query.from(book)...where(authrPredicate)...list(...);
     * </pre>
     *
     * @param authorizedResourceId
     *            The id of the resource that should be authorized. The predicate will provide true if the passed
     *            resource or any of its parent has rights to do any of the actions on the provided target resourceId
     *            expression.
     * @param targetResourceId
     *            Expression that provides the resource that the permission is defined on. This
     * @param actions
     *            If the authorized resource has permission to do any of the actions on the target resource record
     *            (directly or by inheritance), the result will provide <code>true</code>.
     * @return An expression that can be used as a predicate in the main query.
     * @throws NullPointerException
     *             if the targetResourceId parameter is null, the actions parameter is null or the actions array
     *             contains null value.
     * @throws IllegalArgumentException
     *             if the actions parameter is a zero length array.
     */
    BooleanExpression authorizationPredicate(long authorizedResourceId,
            Expression<Long> targetResourceId, String... actions);

}
