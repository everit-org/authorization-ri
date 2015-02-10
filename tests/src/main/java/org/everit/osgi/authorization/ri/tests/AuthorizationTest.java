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
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.everit.osgi.authorization.AuthorizationManager;
import org.everit.osgi.authorization.PermissionChecker;
import org.everit.osgi.authorization.qdsl.util.AuthorizationQdslUtil;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermission;
import org.everit.osgi.authorization.ri.schema.qdsl.QPermissionInheritance;
import org.everit.osgi.dev.testrunner.TestDuringDevelopment;
import org.everit.osgi.dev.testrunner.TestRunnerConstants;
import org.everit.osgi.querydsl.support.QuerydslSupport;
import org.everit.osgi.resource.ResourceService;
import org.everit.osgi.resource.ri.schema.qdsl.QResource;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.log.LogService;

import com.mysema.query.sql.RelationalPathBase;
import com.mysema.query.sql.SQLQuery;
import com.mysema.query.sql.dml.SQLDeleteClause;
import com.mysema.query.types.expr.BooleanExpression;

/**
 * Testing the basic functionality of authorization
 */
@Component(immediate = true, configurationFactory = true, policy = ConfigurationPolicy.REQUIRE, metatype = true)
@Properties({ @Property(name = TestRunnerConstants.SERVICE_PROPERTY_TESTRUNNER_ENGINE_TYPE, value = "junit4"),
        @Property(name = TestRunnerConstants.SERVICE_PROPERTY_TEST_ID, value = "AuthorizationBasicTest"),
        @Property(name = "authorizationManager.target"), @Property(name = "authorizationQdslUtil.target"),
        @Property(name = "permissionChecker.target"), @Property(name = "querydslSupport.target"),
        @Property(name = "resourceService.target"), @Property(name = "logService.target") })
@Service(value = AuthorizationTest.class)
@TestDuringDevelopment
public class AuthorizationTest {

    private static long[] convert(final Collection<Long> collection) {
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

    private static long[] sort(final long[] data) {
        Arrays.sort(data);
        return data;
    }

    private static final long INVALID_RESOURCE_ID = -1;

    @Reference(bind = "setAuthorizationManager")
    private AuthorizationManager authorizationManager;

    @Reference(bind = "setAuthorizationQdslUtil")
    private AuthorizationQdslUtil authorizationQdslUtil;

    @Reference(name = "logService", bind = "setLog")
    private LogService log;

    @Reference(bind = "setPermissionChecker")
    private PermissionChecker permissionChecker;

    @Reference(name = "querydslSupport", bind = "setQdsl")
    private QuerydslSupport qdsl;

    @Reference(bind = "setResourceService")
    private ResourceService resourceService;

    private void clearResourceTable() {
        qdsl.execute((connection, configuration) -> {
            QResource resource = QResource.resource;
            new SQLDeleteClause(connection, configuration, resource).where(
                    resource.resourceId.ne(permissionChecker.getSystemResourceId())).execute();
            return null;
        });
    }

    private void clearTable(final RelationalPathBase<?> path) {
        qdsl.execute((connection, configuration) -> {
            new SQLDeleteClause(connection, configuration, path).execute();
            return null;
        });
    }

    private long[] resolveTargetResourcesWithPermission(final long a1, final String action1) {
        return qdsl.execute((connection, configuration) -> {
            SQLQuery query = new SQLQuery(connection, configuration);
            QResource targetResource = new QResource("tr");

            BooleanExpression permissionCheck = authorizationQdslUtil.authorizationPredicate(a1,
                    targetResource.resourceId, action1);

            List<Long> list = query.from(targetResource).where(permissionCheck)
                    .list(targetResource.resourceId);

            return AuthorizationTest.convert(list);
        });
    }

    private long[] resolveTargetResourcesWithPermission(final long a1, final String[] actions) {
        return qdsl.execute((connection, configuration) -> {
            SQLQuery query = new SQLQuery(connection, configuration);
            QResource targetResource = new QResource("tr");

            BooleanExpression permissionCheckBooleanExpression = authorizationQdslUtil
                    .authorizationPredicate(a1, targetResource.resourceId, actions);

            List<Long> list = query.from(targetResource).where(permissionCheckBooleanExpression)
                    .list(targetResource.resourceId);

            return AuthorizationTest.convert(list);
        });
    }

    public void setAuthorizationManager(final AuthorizationManager authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

    public void setAuthorizationQdslUtil(final AuthorizationQdslUtil authorizationQdslUtil) {
        this.authorizationQdslUtil = authorizationQdslUtil;
    }

    public void setLog(final LogService log) {
        this.log = log;
    }

    public void setPermissionChecker(final PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    public void setQdsl(final QuerydslSupport qdsl) {
        this.qdsl = qdsl;
    }

    public void setResourceService(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    private void stressTest(final long[] authorizedResourceIds, final long[] targetResourceIds, final String action) {
        log.log(LogService.LOG_INFO, "Starting stress test");
        final long iterationNum = 10000000;
        final int threadNum = 2;
        final Random r = new Random();
        final AtomicInteger runningThreads = new AtomicInteger(threadNum);
        final Object mutex = new Object();

        long startTime = System.currentTimeMillis();

        for (int thi = 0; thi < threadNum; thi++) {
            new Thread(() -> {
                for (long i = 0; i < iterationNum; i++) {
                    long authorizedResourceId = authorizedResourceIds[r.nextInt(authorizedResourceIds.length)];
                    long targetResourceId = targetResourceIds[r.nextInt(targetResourceIds.length)];

                    permissionChecker.hasPermission(authorizedResourceId, targetResourceId, action);
                }
                int runningThreadNum = runningThreads.decrementAndGet();
                if (runningThreadNum == 0) {
                    synchronized (mutex) {
                        mutex.notify();
                    }
                }
            }).start();
        }

        synchronized (mutex) {
            if (runningThreads.get() > 0) {
                try {
                    mutex.wait();
                } catch (InterruptedException e) {
                    log.log(LogService.LOG_ERROR, "Waiting for test threads to finish was interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
        }

        long endTime = System.currentTimeMillis();
        log.log(LogService.LOG_INFO, "Stress test finished: " + (endTime - startTime) + " ms");
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
    public void testCyclicPermissionInheritance() {
        long a1 = resourceService.createResource();
        long a2 = resourceService.createResource();
        long a3 = resourceService.createResource();

        authorizationManager.addPermissionInheritance(a1, a2);
        authorizationManager.addPermissionInheritance(a2, a1);
        authorizationManager.addPermissionInheritance(a1, a3);
        authorizationManager.addPermissionInheritance(a2, a3);

        Assert.assertArrayEquals(AuthorizationTest.sort(new long[] { a1, a2, a3 }),
                AuthorizationTest.sort(permissionChecker.getAuthorizationScope(a3)));
        Assert.assertArrayEquals(AuthorizationTest.sort(new long[] { a1, a2 }),
                AuthorizationTest.sort(permissionChecker.getAuthorizationScope(a1)));

        authorizationManager.clearCache();
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
        permissionChecker.hasPermission(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID, (String[]) null);
    }

    @Test(expected = NullPointerException.class)
    public void testPermissionCheckNullActionInArray() {
        permissionChecker.hasPermission(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID, (String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPermissionCheckZeroAction() {
        permissionChecker.hasPermission(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID);
    }

    @Test
    public void testPermissionManipulation() {

        long a1 = resourceService.createResource();
        long a2 = resourceService.createResource();
        long a3 = resourceService.createResource();
        long a4 = resourceService.createResource();
        long a5 = resourceService.createResource();
        long a6 = resourceService.createResource();
        long a7 = resourceService.createResource();
        long a8 = resourceService.createResource();

        long t1 = resourceService.createResource();
        long t2 = resourceService.createResource();
        long t3 = resourceService.createResource();
        long t4 = resourceService.createResource();
        long t5 = resourceService.createResource();
        long t6 = resourceService.createResource();
        long t7 = resourceService.createResource();
        long t8 = resourceService.createResource();

        final String action1 = "action1";

        authorizationManager.addPermission(a1, t1, action1);
        authorizationManager.addPermission(a2, t2, action1);
        authorizationManager.addPermission(a3, t3, action1);
        authorizationManager.addPermission(a4, t4, action1);
        authorizationManager.addPermission(a5, t5, action1);
        authorizationManager.addPermission(a6, t6, action1);
        authorizationManager.addPermission(a7, t7, action1);
        authorizationManager.addPermission(a8, t8, action1);

        authorizationManager.addPermissionInheritance(a1, a3);
        authorizationManager.addPermissionInheritance(a1, a4);
        authorizationManager.addPermissionInheritance(a2, a4);
        authorizationManager.addPermissionInheritance(a2, a5);
        authorizationManager.addPermissionInheritance(a3, a6);
        authorizationManager.addPermissionInheritance(a4, a6);
        authorizationManager.addPermissionInheritance(a4, a7);
        authorizationManager.addPermissionInheritance(a5, a7);
        authorizationManager.addPermissionInheritance(a6, a8);
        authorizationManager.addPermissionInheritance(a7, a8);

        Assert.assertArrayEquals(AuthorizationTest.sort(new long[] { a1, a2, a3, a6, a4 }),
                AuthorizationTest.sort(permissionChecker.getAuthorizationScope(a6)));

        Assert.assertFalse(permissionChecker.hasPermission(a1, t1, "x"));
        Assert.assertTrue(permissionChecker.hasPermission(a1, t1, action1));
        Assert.assertTrue(permissionChecker.hasPermission(a8, t1, action1));
        Assert.assertFalse(permissionChecker.hasPermission(a1, t8, action1));
        Assert.assertTrue(permissionChecker.hasPermission(a8, t4, action1));

        authorizationManager.removePermissionInheritance(a4, a7);

        Assert.assertTrue(permissionChecker.hasPermission(a8, t4, action1));

        authorizationManager.removePermissionInheritance(a4, a6);

        Assert.assertFalse(permissionChecker.hasPermission(a8, t4, action1));
        Assert.assertTrue(permissionChecker.hasPermission(a8, t2, action1));
        Assert.assertArrayEquals(AuthorizationTest.sort(new long[] { a1, a3, a6 }),
                AuthorizationTest.sort(permissionChecker.getAuthorizationScope(a6)));

        authorizationManager.removePermissionInheritance(a2, a5);

        Assert.assertFalse(permissionChecker.hasPermission(a8, t2, action1));

        Assert.assertTrue(permissionChecker.hasPermission(a8, t1, action1));
        authorizationManager.removePermission(a1, t1, action1);
        Assert.assertFalse(permissionChecker.hasPermission(a8, t1, action1));

        // Uncomment if you want some stress testing
        // stressTest(new long[] { a1, a2, a3, a4, a5, a6, a7, a8 }, new long[] { t1, t2, t3, t4, t5, t6, t7, t8 },
        // action1);

        authorizationManager.clearCache();

        Assert.assertFalse(permissionChecker.hasPermission(a8, t1, action1));
        Assert.assertTrue(permissionChecker.hasPermission(a8, t8, action1));

        authorizationManager.clearCache();
        clearTable(QPermission.permission);
        clearTable(QPermissionInheritance.permissionInheritance);
        clearResourceTable();
    }

    @Test
    public void testQueryExtension() {
        long a1 = resourceService.createResource();
        long a2 = resourceService.createResource();
        long a3 = resourceService.createResource();

        long t1 = resourceService.createResource();
        long t2 = resourceService.createResource();
        long t3 = resourceService.createResource();

        final String action1 = "action1";
        final String action2 = "action2";
        final String action3 = "action3";
        final String action4 = "action4";

        authorizationManager.addPermission(a1, t1, action1);
        authorizationManager.addPermission(a1, t1, action2);
        authorizationManager.addPermission(a2, t2, action1);
        authorizationManager.addPermission(a3, t3, action3);

        authorizationManager.addPermissionInheritance(a1, a2);
        authorizationManager.addPermissionInheritance(a1, a3);

        long[] resources = resolveTargetResourcesWithPermission(a1, action1);
        Assert.assertArrayEquals(new long[] { t1 }, resources);

        resources = resolveTargetResourcesWithPermission(a2, action1);
        Assert.assertArrayEquals(new long[] { t1, t2 }, resources);

        resources = resolveTargetResourcesWithPermission(a2, action2);
        Assert.assertArrayEquals(new long[] { t1 }, resources);

        resources = resolveTargetResourcesWithPermission(a2, action4);
        Assert.assertArrayEquals(new long[] {}, resources);

        resources = resolveTargetResourcesWithPermission(a2, new String[] { action4 });
        Assert.assertArrayEquals(new long[] {}, resources);

        resources = resolveTargetResourcesWithPermission(a3, new String[] { action3, action2 });
        Assert.assertArrayEquals(new long[] { t1, t3 }, resources);

        authorizationManager.clearCache();
        clearTable(QPermission.permission);
        clearTable(QPermissionInheritance.permissionInheritance);
        clearResourceTable();
    }

    public void testRemovePermissionInheritanceInvalidResources() {
        authorizationManager.removePermissionInheritance(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID);
    }

    @Test(expected = NullPointerException.class)
    public void testRemovePermissionNullAction() {
        authorizationManager.removePermission(INVALID_RESOURCE_ID, INVALID_RESOURCE_ID, null);
    }

    @Test
    public void testSystemResource() {
        long systemResourceId = permissionChecker.getSystemResourceId();
        Assert.assertTrue(permissionChecker.hasPermission(systemResourceId, INVALID_RESOURCE_ID, ""));

        long a1 = resourceService.createResource();

        authorizationManager.addPermissionInheritance(systemResourceId, a1);

        Assert.assertTrue(permissionChecker.hasPermission(a1, INVALID_RESOURCE_ID, ""));

        Assert.assertArrayEquals(AuthorizationTest.sort(new long[] { systemResourceId, a1 }),
                resolveTargetResourcesWithPermission(systemResourceId, ""));

        Assert.assertArrayEquals(AuthorizationTest.sort(new long[] { systemResourceId, a1 }),
                resolveTargetResourcesWithPermission(a1, ""));

        clearTable(QPermission.permission);
        clearTable(QPermissionInheritance.permissionInheritance);
        clearResourceTable();
    }
}
