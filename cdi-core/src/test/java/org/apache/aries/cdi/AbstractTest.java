package org.apache.aries.cdi;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.aries.cdi.impl.osgi.BundleContextHolder;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public abstract class AbstractTest {

    @Rule
    public TemporaryFolder cache;

    Framework framework;
    WeldContainer weld;

    AbstractTest() {
        File root = new File("target/osgi");
        root.mkdirs();
        cache = new TemporaryFolder(root);
    }

    @Before
    public void setUp() throws BundleException {
        // Boot an empty osgi framework for testing
        Map<String, String> config = new HashMap<>();
        config.put(Constants.FRAMEWORK_STORAGE, cache.getRoot().toString());
        framework = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config);
        framework.start();
        BundleContextHolder.setBundleContext(framework.getBundleContext());
    }

    @After
    public void tearDown() throws BundleException {
        framework.stop();
        if (weld != null) {
            weld.close();
            weld = null;
        }
    }

}
