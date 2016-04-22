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

import java.util.Collections;
import java.util.List;
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

    private final BundleContext m_context;
    private final Logger m_logger;
    private final ConcurrentHashMap<ComponentImpl, ComponentImpl> m_components = new ConcurrentHashMap<>();

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
        m_context = context;
        m_logger = logger;
    }

    Logger getLogger() {
        return m_logger;
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

}
