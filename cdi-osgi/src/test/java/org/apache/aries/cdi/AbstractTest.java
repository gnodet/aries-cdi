/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aries.cdi;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.aries.cdi.impl.osgi.support.BundleContextHolder;
import org.apache.aries.cdi.impl.osgi.OsgiExtension;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

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

    protected BundleContext getBundleContext() {
        return framework.getBundleContext();
    }

    protected void startConfigAdmin() {
        BundleContextHolder.setBundleContext(getBundleContext());
        new org.apache.felix.cm.impl.ConfigurationManager().start(getBundleContext());
    }

    protected <T> T getService(Class<T> clazz) {
        ServiceReference<T> ref = getBundleContext().getServiceReference(clazz);
        if (ref != null) {
            return getBundleContext().getService(ref);
        } else {
            return null;
        }
    }

    protected <T> ServiceRegistration<T> register(Class<T> clazz, T t) {
        return getBundleContext().registerService(clazz, t, null);
    }

    protected <T> ServiceRegistration<T> register(Class<T> clazz, T t, int ranking) {
        return getBundleContext().registerService(clazz, t,
                dictionary(Constants.SERVICE_RANKING, ranking));
    }

    protected WeldContainer createCdi(Class... classes) {
        BundleContextHolder.setBundleContext(getBundleContext());
        weld = new Weld()
                .disableDiscovery()
                .beanClasses(classes)
                .extensions(new OsgiExtension())
                .initialize();
        return weld;
    }

    protected Configuration getConfiguration(Class<? extends Annotation> cfg) throws IOException {
        return getService(ConfigurationAdmin.class).getConfiguration(cfg.getName());
    }

    protected Dictionary<String, Object> dictionary(String key, Object val) {
        Dictionary<String, Object> dictionary = new Hashtable<>();
        dictionary.put(key, val);
        return dictionary;
    }

}
