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
package org.everit.osgi.authorization.ri;

public final class AuthorizationRIConstants {

    public static final String DEFAULT_SERVICE_DESCRIPTION = "Default Authorization";

    public static final String PROP_PERMISSION_CACHE_TARGET = "permissionCache.target";

    public static final String PROP_PERMISSION_INHERITANCE_CACHE_TARGET = "permissionInheritanceCache.target";

    public static final String PROP_QUERYDSL_SUPPORT_TARGET = "querydslSupport.target";

    public static final String PROP_PROPERTY_MANAGER_TARGET = "propertyManager.target";

    public static final String PROP_TRANSACTION_HELPER_TARGET = "transactionHelper.target";

    public static final String SERVICE_FACTORYPID_AUTHORIZATION = "org.everit.osgi.authorization.ri.Authorization";

    public static final String PROP_SYSTEM_RESOURCE_ID = "org.everit.osgi.authorization.ri.SYSTEM_RESOURCE_ID";

    public static final String PROP_RESOURCE_SERVICE_TARGET = "resourceService.target";

    private AuthorizationRIConstants() {
    }
}
