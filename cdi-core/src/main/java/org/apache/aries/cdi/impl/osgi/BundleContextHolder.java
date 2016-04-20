package org.apache.aries.cdi.impl.osgi;

import org.osgi.framework.BundleContext;

public class BundleContextHolder {

    private static final ThreadLocal<BundleContext> threadLocal = new ThreadLocal<>();

    public static BundleContext getBundleContext() {
        return threadLocal.get();
    }

    public static void setBundleContext(BundleContext bundleContext) {
        threadLocal.set(bundleContext);
    }

}
