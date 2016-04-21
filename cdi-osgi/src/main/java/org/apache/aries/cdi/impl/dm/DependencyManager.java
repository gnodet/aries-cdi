/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.cdi.impl.dm;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.BundleContext;

/**
 * The dependency manager manages all components and their dependencies. Using
 * this API you can declare all components and their dependencies. Under normal
 * circumstances, you get passed an instance of this class through the
 * <code>DependencyActivatorBase</code> subclass you use as your
 * <code>BundleActivator</code>, but it is also possible to create your
 * own instance.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class DependencyManager {
    /**
     * The DependencyManager Activator will wait for a threadpool before creating any DM components if the following
     * OSGi system property is set to true.
     */
    public final static String PARALLEL = "org.apache.felix.dependencymanager.parallel";

    public static final String ASPECT = "org.apache.felix.dependencymanager.aspect";
    public static final String SERVICEREGISTRY_CACHE_INDICES = "org.apache.felix.dependencymanager.filterindex";
    public static final String METHOD_CACHE_SIZE = "org.apache.felix.dependencymanager.methodcache";

    private final BundleContext m_context;
    private final Logger m_logger;
    private final ConcurrentHashMap<ComponentImpl, ComponentImpl> m_components = new ConcurrentHashMap<>();

    // service registry cache
    private static final Set<WeakReference<DependencyManager>> m_dependencyManagers = new HashSet<>();

    /**
     * Creates a new dependency manager. You need to supply the
     * <code>BundleContext</code> to be used by the dependency
     * manager to register services and communicate with the
     * framework.
     *
     * @param context the bundle context
     */
    public DependencyManager(BundleContext context) {
        this(context, new Logger(context));
    }

    DependencyManager(BundleContext context, Logger logger) {
        m_context = createContext(context);
        m_logger = logger;
        synchronized (m_dependencyManagers) {
            m_dependencyManagers.add(new WeakReference<DependencyManager>(this));
        }
    }

    Logger getLogger() {
        return m_logger;
    }

    /**
     * Returns the list of currently created dependency managers.
     * @return the list of currently created dependency managers
     */
    public static List<DependencyManager> getDependencyManagers() {
        List<DependencyManager> result = new ArrayList<>();
        synchronized (m_dependencyManagers) {
            Iterator<WeakReference<DependencyManager>> iterator = m_dependencyManagers.iterator();
            while (iterator.hasNext()) {
                WeakReference<DependencyManager> reference = iterator.next();
                DependencyManager manager = reference.get();
                if (manager != null) {
                    try {
                        manager.getBundleContext().getBundle();
                        result.add(manager);
                        continue;
                    }
                    catch (IllegalStateException e) {
                    }
                }
                iterator.remove();
            }
        }
        return result;
    }

    /**
     * Returns the bundle context associated with this dependency manager.
     * @return the bundle context associated with this dependency manager.
     */
    public BundleContext getBundleContext() {
        return m_context;
    }

    /**
     * Adds a new component to the dependency manager. After the service is added
     * it will be started immediately.
     *
     * @param c the service to add
     */
    public void add(ComponentImpl c) {
        m_components.put(c, c);
        c.start();
    }

    /**
     * Removes a service from the dependency manager. Before the service is removed
     * it is stopped first.
     *
     * @param c the component to remove
     */
    public void remove(ComponentImpl c) {
        c.stop();
        m_components.remove(c);
    }

    /**
     * Creates a new component.
     *
     * @return the new component
     */
    public ComponentImpl createComponent() {
        return new ComponentImpl(m_context, this);
    }

    /**
     * Creates a new service dependency.
     *
     * @return the service dependency
     */
    public <T> ServiceDependencyImpl<T> createServiceDependency() {
        return new ServiceDependencyImpl<>();
    }

    /**
     * Creates a new configuration dependency.
     *
     * @return the configuration dependency
     */
    public ConfigurationDependencyImpl createConfigurationDependency() {
        return new ConfigurationDependencyImpl();
    }

    /**
     * Creates a new timed required service dependency. A timed dependency blocks the invoker thread is the required dependency
     * is currently unavailable, until it comes up again.
     *
     * @return a new timed service dependency
     */
    public TemporalServiceDependencyImpl createTemporalServiceDependency(long timeout) {
        return new TemporalServiceDependencyImpl(m_context, timeout);
    }

    public ComponentDependencyImpl createComponentDependency() {
        return new ComponentDependencyImpl();
    }

    /**
     * Returns a list of components.
     *
     * @return a list of components
     */
    public List<ComponentImpl> getComponents() {
        return Collections.list(m_components.elements());
    }

    /**
     * Removes all components and their dependencies.
     */
    public void clear() {
        for (ComponentImpl component : m_components.keySet()) {
            remove(component);
        }
        m_components.clear();
    }

    private BundleContext createContext(BundleContext context) {
        return context;
    }

}
