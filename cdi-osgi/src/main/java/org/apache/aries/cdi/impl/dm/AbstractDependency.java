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

import java.util.Collection;
import java.util.Dictionary;
import java.util.Map;
import java.util.Set;

/**
 * Abstract class for implementing Dependencies.
 * You can extends this class in order to supply your own custom dependencies to any Dependency Manager Component.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public abstract class AbstractDependency<D extends AbstractDependency, S, E extends Event<S>> {

    /**
     * The Component implementation is exposed to Dependencies through this interface.
     */
    protected ComponentImpl m_component;

    /**
     * Is this Dependency available ?
     */
    protected volatile boolean m_available;

    /**
     * Is this Dependency "instance bound" ? A dependency is "instance bound" if it is defined within the component's 
     * init method, meaning that it won't deactivate the component if it is not currently available when being added
     * from the component's init method.
     */
    protected boolean m_instanceBound;

    /**
     * Is this dependency required (false by default) ?
     */
    protected volatile boolean m_required;

    /**
     * Is this dependency greedy (false by default) ?
     */
    protected volatile boolean m_greedy;

    /**
     * Has this Dependency been started by the Component implementation ? .
     */
    protected volatile boolean m_isStarted;

    /**
     * Tells if the dependency service properties have to be propagated to the Component service properties.
     */
    protected volatile boolean m_propagate;

    /**
     * Creates a new Dependency. By default, the dependency is optional and autoconfig.
     */
    public AbstractDependency() {
    }

    @Override
    public String toString() {
        return getType() + " dependency [" + getName() + "]";
    }

    // ----------------------- Dependency interface -----------------------------

    /**
     * Is this Dependency required (false by default) ?
     */
    public boolean isRequired() {
        return m_required;
    }

    public boolean isOptional() {
        return !m_required;
    }

    public boolean isGreedy() {
        return m_greedy;
    }

    /**
     * Is this Dependency satisfied and available ?
     */
    public boolean isAvailable() {
        return m_available;
    }

    /**
     * Returns the propagate callback method that is invoked in order to supply dynamically some dependency service properties.
     */
    public boolean isPropagated() {
        return m_propagate;
    }

    /**
     * Returns the dependency service properties (empty by default).
     */
    public Dictionary<String, Object> getProperties() {
        return EmptyDictionary.emptyDictionary();
    }

    // -------------- DependencyContext interface -----------------------------------------------

    /**
     * Called by the Component implementation before the Dependency can be started.
     */
    public void setComponentContext(ComponentImpl component) {
        m_component = component;
    }

    /**
     * A Component callback must be invoked with dependency event(s).
     * @param type the dependency event type
     * @param event the dependency service event to inject in the component.
     */
    public void invokeCallback(EventType type, E event) {
    }

    /**
     * Starts this dependency. Subclasses can override this method but must then call super.start().
     */
    public void start() {
        m_isStarted = true;
    }

    /**
     * Starts this dependency. Subclasses can override this method but must then call super.stop().
     */
    public void stop() {
        m_isStarted = false;
    }

    /**
     * Indicates if this dependency has been started by the Component implementation.
     */
    public boolean isStarted() {
        return m_isStarted;
    }

    /**
     * Called by the Component implementation when the dependency is considered to be available.
     */
    public void setAvailable(boolean available) {
        m_available = available;
    }

    /**
     * Is this Dependency "instance bound" (has been defined within the component's init method) ?
     */
    public boolean isInstanceBound() {
        return m_instanceBound;
    }

    /**
     * Called by the Component implementation when the dependency is declared within the Component's init method.
     */
    public void setInstanceBound(boolean instanceBound) {
        m_instanceBound = instanceBound;
    }

    /**
     * Tells if the Component must be first instantiated before starting this dependency (false by default).
     */
    public boolean needsInstance() {
        return false;
    }

    /**
     * Get the highest ranked available dependency service, or null.
     */
    public S getService() {
        E event = m_component.getBoundDependencyEvent(this);
        if (event == null) {
            return getDefaultService(true);
        }
        return event.getEvent();
    }

    /**
     * Copy all dependency service instances to the given collection.
     */
    public void copyToCollection(Collection<S> services) {
        Set<E> events = m_component.getDependencyEvents(this);
        if (events.size() > 0) {
            for (E e : events) {
                services.add(e.getEvent());
            }
        } else {
            S defaultService = getDefaultService(false);
            if (defaultService != null) {
                services.add(defaultService);
            }
        }
    }

    /**
     * Copy all dependency service instances to the given map (key = dependency service, value = dependency service properties.
     */
    public void copyToMap(Map<S, Dictionary<String, Object>> map) {
        Set<E> events = m_component.getDependencyEvents(this);
        if (events.size() > 0) {
            for (E e : events) {
                map.put(e.getEvent(), e.getProperties());
            }
        } else {
            S defaultService = getDefaultService(false);
            if (defaultService != null) {
                map.put(defaultService, EmptyDictionary.emptyDictionary());
            }
        }
    }

    // -------------- ComponentDependencyDeclaration -----------------------------------------------

    /**
     * Returns a description of this dependency (like the dependency service class name with associated filters)
     */
    public String getName() {
        return getSimpleName();
    }

    /**
     * Returns a simple name for this dependency (like the dependency service class name).
     */
    public abstract String getSimpleName();

    /**
     * Returns the dependency symbolic type.
     */
    public abstract String getType();

    /**
     * Returns the dependency filter, if any.
     */
    public String getFilter() {
        return null;
    }

    /**
     * Returns this dependency state.
     */
//    public int getState() { // Can be called from any threads, but our class attributes are volatile
//        if (m_isStarted) {
//            return (isAvailable() ? 1 : 0) + (isRequired() ? 2 : 0);
//        } else {
//            return isRequired() ? ComponentDependencyDeclaration.STATE_REQUIRED
//                : ComponentDependencyDeclaration.STATE_OPTIONAL;
//        }
//    }

    // -------------- Methods common to sub interfaces of Dependendency

    /**
     * Activates Dependency service properties propagation (to the service properties of the component to which this
     * dependency is added).
     * 
     * @param propagate true if the dependency service properties must be propagated to the service properties of 
     * the component to which this dependency is added. 
     * @return this dependency instance
     */
    @SuppressWarnings("unchecked")
    public D setPropagate(boolean propagate) {
        ensureNotActive();
        m_propagate = propagate;
        return (D) this;
    }

    @SuppressWarnings("unchecked")
    public D setRequired(boolean required) {
        ensureNotActive();
        m_required = required;
        return (D) this;
    }

    @SuppressWarnings("unchecked")
    public D setGreedy(boolean greedy) {
        ensureNotActive();
        m_greedy = greedy;
        return (D) this;
    }

    /**
     * Returns the component implementation context
     * @return the component implementation context
     */
    public ComponentImpl getComponentContext() {
        return m_component;
    }

    /**
     * Returns the default service, or null.
     * @param nullObject if true, a null object may be returned.
     * @return the default service
     */
    protected S getDefaultService(boolean nullObject) {
        return null;
    }

    /**
     * Checks if the component dependency is not started.
     */
    protected void ensureNotActive() {
        if (isStarted()) {
            throw new IllegalStateException("Cannot modify state while active.");
        }
    }

}
