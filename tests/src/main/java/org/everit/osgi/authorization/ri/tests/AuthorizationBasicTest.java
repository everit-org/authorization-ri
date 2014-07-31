/**
 * This file is part of Everit - Authorization Tests.
 *
 * Everit - Authorization Tests is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Authorization Tests is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Authorization Tests.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.authorization.ri.tests;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.everit.osgi.authorization.AuthorizationManager;
import org.everit.osgi.authorization.PermissionChecker;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermission;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermissionInheritance;
import org.everit.osgi.dev.testrunner.TestDuringDevelopment;
import org.everit.osgi.dev.testrunner.TestRunnerConstants;
import org.everit.osgi.querydsl.support.QuerydslSupport;
import org.everit.osgi.resource.ResourceService;
import org.junit.Assert;
import org.junit.Test;

import com.mysema.query.sql.RelationalPathBase;
import com.mysema.query.sql.dml.SQLDeleteClause;

/**
 * Testing the basic functionality of authorization
 */
@Component(immediate = true, policy = ConfigurationPolicy.REQUIRE, metatype = true)
@Properties({ @Property(name = TestRunnerConstants.SERVICE_PROPERTY_TESTRUNNER_ENGINE_TYPE, value = "junit4"),
        @Property(name = TestRunnerConstants.SERVICE_PROPERTY_TEST_ID, value = "AuthorizationBasicTest"),
        @Property(name = "authorizationManager.target"), @Property(name = "permissionChecker.target") })
@Service(value = AuthorizationBasicTest.class)
@TestDuringDevelopment
public class AuthorizationBasicTest {

    private static final long INVALID_RESOURCE_ID = -1;

    private static long[] convert(Collection<Long> collection) {
        long[] result = new long[collection.size()];
        Iterator<Long> iterator = collection.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            Long element = iterator.next();
            result[i] = element;
            i++;
        }
        Arrays.sort(result);
        return result;
    }

    private static long[] sort(long[] data) {
        Arrays.sort(data);
        return data;
    }

    @Reference(bind = "setAuthorizationManager")
    private AuthorizationManager authorizationManager;

    @Reference(bind = "setPermissionChecker")
    private PermissionChecker permissionChecker;

    @Reference(name = "querydslSupport", bind = "setQdsl")
    private QuerydslSupport qdsl;

    @Reference(bind = "setResourceService")
    private ResourceService resourceService;

    private void clearResourceTable() {
        clearTable(QPermission.permission);
    }

    private void clearTable(RelationalPathBase<?> path) {
        qdsl.execute((connection, configuration) -> {
            new SQLDeleteClause(connection, configuration, path).execute();
            return null;
        });
    }

    public void setAuthorizationManager(AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

    public void setPermissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    public void setQdsl(QuerydslSupport qdsl) {
        this.qdsl = qdsl;
    }

    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Test(expected = RuntimeException.class)
    public void testAddPermisisonInheritanceInvalidChildResource() {
        long resourceId = resourceService.createResource();
        try {
            authorizationManager.addPermissionInheritance(INVALID_RESOURCE_ID, resourceId);
        } finally {
            clearResourceTable();
        }
    }

    @Test(expected = RuntimeException.class)
    public void testAddPermisisonInheritanceInvalidParentResource() {
        long resourceId = resourceService.createResource();
        try {
            authorizationManager.addPermissionInheritance(INVALID_RESOURCE_ID, resourceId);
        } finally {
            clearResourceTable();
        }
    }

    @Test(expected = RuntimeException.class)
    public void testAddPermissionInvalidAuthorizedResourceId() {
        long resourceId = resourceService.createResource();
        try {
            authorizationManager.addPermission(INVALID_RESOURCE_ID, resourceId, "");
        } finally {
            clearResourceTable();
        }
    }

    @Test(expected = RuntimeException.class)
    public void testAddPermissionInvalidTargetResourceId() {
        long resourceId = resourceService.createResource();
        try {
            authorizationManager.addPermission(resourceId, INVALID_RESOURCE_ID, "");
        } finally {
            clearResourceTable();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testAddPermissionNullAction() {
        authorizationManager.addPermission(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID, null);
    }

    @Test
    public void testPermissionceManipulation() {
        long a1 = resourceService.createResource();
        long a2 = resourceService.createResource();
        long a3 = resourceService.createResource();
        long a4 = resourceService.createResource();

        authorizationManager.addPermissionInheritance(a3, a4);

        Assert.assertArrayEquals(new long[] { a1 }, convert(permissionChecker.getAuthorizationScope(a1)));
        Assert.assertArrayEquals(sort(new long[] { a3, a4 }), convert(permissionChecker.getAuthorizationScope(a4)));

        authorizationManager.addPermissionInheritance(a1, a3);

        Assert.assertArrayEquals(sort(new long[] { a1, a3, a4 }), convert(permissionChecker.getAuthorizationScope(a4)));
        Assert.assertArrayEquals(sort(new long[] { a1, a3 }), convert(permissionChecker.getAuthorizationScope(a3)));

        authorizationManager.addPermissionInheritance(a1, a2);
        authorizationManager.addPermissionInheritance(a2, a4);

        Assert.assertArrayEquals(sort(new long[] { a1, a2, a3, a4 }),
                convert(permissionChecker.getAuthorizationScope(a4)));

        authorizationManager.removePermissionInheritance(a3, a4);

        Assert.assertArrayEquals(sort(new long[] { a1, a2, a4 }), convert(permissionChecker.getAuthorizationScope(a4)));

        authorizationManager.removePermissionInheritance(a3, a4);

        clearTable(QPermission.permission);
        clearTable(QPermissionInheritance.permissionInheritance);
        clearResourceTable();
    }

    @Test
    public void testPermissionCheckInvalidResource() {
        long resourceId = resourceService.createResource();

        Assert.assertFalse(permissionChecker.hasPermission(INVALID_RESOURCE_ID, resourceId, ""));

        Assert.assertFalse(permissionChecker.hasPermission(resourceId, INVALID_RESOURCE_ID, ""));

        clearResourceTable();
    }

    @Test(expected = NullPointerException.class)
    public void testPermissionCheckNullAction() {
        permissionChecker.hasPermission(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID, null);
    }

    public void testRemovePermissionInheritanceInvalidResources() {
        authorizationManager.removePermissionInheritance(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID);
    }

    @Test(expected = NullPointerException.class)
    public void testRemovePermissionNullAction() {
        authorizationManager.removePermission(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID, null);
    }
}
