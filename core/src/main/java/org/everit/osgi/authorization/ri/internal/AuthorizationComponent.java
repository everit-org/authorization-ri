package org.everit.osgi.authorization.ri.internal;

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
import org.everit.osgi.cache.api.CacheConfiguration;
import org.everit.osgi.cache.api.CacheFactory;
import org.everit.osgi.cache.api.CacheHolder;
import org.everit.osgi.querydsl.support.QuerydslSupport;
import org.everit.osgi.transaction.helper.api.TransactionHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

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
    private CacheConfiguration<Long, Long[]> permissionInheritanceCacheConfiguration;

    private CacheHolder<Long, Long[]> piCacheHolder = null;

    @Reference(name = "querydslSupport", bind = "setQdsl")
    private QuerydslSupport qdsl;

    @Reference(name = "transactionHelper", bind = "setTh")
    private TransactionHelper th;

    @Activate
    public void activate(BundleContext bundleContext) {
        ClassLoader classLoader = resolveClassLoader(bundleContext);
        piCacheHolder = cacheFactory.createCache(permissionInheritanceCacheConfiguration, classLoader);
    }

    @Override
    public void addPermission(long authorizedResourceId, long targetResourceId, String action) {
        th.required(() -> qdsl.execute((connection, configuration) -> {
            QPermission p = QPermission.permission;
            SQLInsertClause insert = new SQLInsertClause(connection, configuration, p);
            insert.set(p.authorizedResourceId, authorizedResourceId).set(p.targetResourceId, targetResourceId)
                    .set(p.action, action).execute();

            return null;
        }));
    }

    @Override
    public void addPermissionInheritance(long parentResourceId, long childResourceId) {
        th.required(() -> qdsl.execute((connection, configuration) -> {

            return null;
        }));
    }

    @Override
    public void clearCache() {
        // TODO Auto-generated method stub

    }

    @Deactivate
    public void deactivate() {
        if (piCacheHolder != null) {
            piCacheHolder.close();
        }
    }

    @Override
    public long[] getAuthorizationScope(long resourceId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasPermission(long authorizedResourceId, long targetResourceId, String action) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void removePermission(long authorizedResourceId, long targetResourceId, String action) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removePermissionInheritance(long parentResourceId, long childResourceId) {
        // TODO Auto-generated method stub

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
            CacheConfiguration<Long, Long[]> permissionInheritanceCacheConfiguration) {
        this.permissionInheritanceCacheConfiguration = permissionInheritanceCacheConfiguration;
    }

    public void setQdsl(QuerydslSupport qdsl) {
        this.qdsl = qdsl;
    }

    public void setTh(TransactionHelper th) {
        this.th = th;
    }

}
