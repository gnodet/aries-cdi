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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

/**
 * Dependency Manager Component implementation.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ComponentImpl {

    /**
     * Default Component Executor, which is by default single threaded. The first thread which schedules a task
     * is the master thread and will execute all tasks that are scheduled by other threads at the time the master
     * thread is executing. Internal tasks scheduled by the master thread are executed immediately (inline execution).
     * 
     * If a ComponentExecutorFactory is provided in the OSGI registry, then this executor will be replaced by the
     * executor returned by the ComponentExecutorFactory (however, the same semantic of the default executor is used: 
     * all tasks are serially executed).
     */
	private volatile Executor m_executor = new SerialExecutor(new Logger(null));
	
	/**
	 * The current state of the component state machine.
	 */
	private ComponentState m_state = ComponentState.INACTIVE;
	
    /**
     * Indicates that the handleChange method is currently being executed.
     */
    private boolean m_handlingChange;
    
    /**
     * List of dependencies. We use a COW list in order to avoid ConcurrentModificationException while iterating on the 
     * list and while a component synchronously add more dependencies from one of its callback method.
     */
	private final CopyOnWriteArrayList<AbstractDependency<?, ?, ?>> m_dependencies = new CopyOnWriteArrayList<>();
	
	/**
	 * List of Component state listeners. We use a COW list in order to avoid ConcurrentModificationException while iterating on the 
     * list and while a component synchronously add more listeners from one of its callback method.
	 */
	private final List<ComponentStateListener> m_listeners = new CopyOnWriteArrayList<>();
	
	/**
	 * Is the component active ?
	 */
	private boolean m_isStarted;
	
	/**
	 * The Component logger.
	 */
    private final Logger m_logger;
    
    /**
     * The Component bundle context.
     */
    private final BundleContext m_context;
    
    /**
     * The DependencyManager object that has created this component.
     */
    private final DependencyManager m_manager;
    
    /**
     * The object used to create the component. Can be a class name, or the component implementation instance.
     */
    private volatile Object m_componentDefinition;
    
    /**
     * The component instance.
     */
	private Object m_componentInstance;
	
	/**
	 * The service(s) provided by this component. Can be a String, or a String array.
	 */
    private volatile Object m_serviceName;
    
    /**
     * The service properties, if this component is providing a service.
     */
    private volatile Dictionary<String, Object> m_serviceProperties;
    
    /**
     * The component service registration. Can be a NullObject in case the component does not provide a service.
     */
    private volatile ServiceRegistration<?> m_registration;
    
    /**
     * Data structure used to record the elapsed time used by component lifecycle callbacks.
     * Key = callback name ("init", "start", "stop", "destroy").
     * Value = elapsed time in nanos.
     */
    private final Map<String, Long> m_stopwatch = new ConcurrentHashMap<>();
    
    /**
     * Unique component id.
     */
    private final long m_id;
    
    /**
     * Unique ID generator.
     */
    private final static AtomicLong m_idGenerator = new AtomicLong();
    
    /**
     * Holds all the services of a given dependency context. Caution: the last entry in the skiplist is the highest 
     * ranked service.
     */
    private final Map<AbstractDependency, ConcurrentSkipListSet<Event>> m_dependencyEvents = new HashMap<>();
    private final Map<AbstractDependency, AtomicReference<Event>> m_dependencySelectedEvent = new HashMap<>();

    /**
     * Flag used to check if this component has been added in a DependencyManager object.
     */
    private final AtomicBoolean m_active = new AtomicBoolean(false);

    /**
     * Cache of callback invocation used to avoid calling the same callback twice.
     * This situation may sometimes happen when the state machine triggers a lifecycle callback ("bind" call), and
     * when the bind method registers a service which is tracked by another optional component dependency.
     */
    private final Map<Event, Event> m_invokeCallbackCache = new IdentityHashMap<>();

	/**
	 * The Component bundle.
	 */
    private final Bundle m_bundle;

    /**
     * Flag used to check if the start callback has been invoked.
     * We use this flag to ensure that we only inject optional dependencies after the start callback has been called. 
     */
	private boolean m_startCalled;

    /**
     * Constructor. Only used for tests.
     */
    public ComponentImpl() {
	    this(null, null, new Logger(null));
	}

    public ComponentImpl(BundleContext context, DependencyManager manager) {
        this(context, manager, manager.getLogger());
    }

    /**
     * Constructor
     * @param context the component bundle context 
     * @param manager the manager used to create the component
     * @param logger the logger to use
     */
    public ComponentImpl(BundleContext context, DependencyManager manager, Logger logger) {
        m_context = context;
        m_bundle = context != null ? context.getBundle() : null;
        m_manager = manager;
        m_logger = logger;
        m_id = m_idGenerator.getAndIncrement();
    }

    public Executor getExecutor() {
        return m_executor;
    }

    public ComponentImpl setDebug(String debugKey) {
        // Force debug level in our logger
        m_logger.setEnabledLevel(LogService.LOG_DEBUG);
        m_logger.setDebugKey(debugKey);
        return this;
    }

	public ComponentImpl add(final AbstractDependency... dependencies) {
		getExecutor().execute(() -> {
            List<AbstractDependency<?, ?, ?>> instanceBoundDeps = new ArrayList<>();
            for (AbstractDependency<?, ?, ?> dc : dependencies) {
                if (dc.getComponentContext() != null) {
                    m_logger.err("%s can't be added to %s (dependency already added to another component).", dc, ComponentImpl.this);
                    continue;
                }
                m_dependencyEvents.put(dc, new ConcurrentSkipListSet<>());
                m_dependencies.add(dc);
                dc.setComponentContext(ComponentImpl.this);
                if (!(m_state == ComponentState.INACTIVE)) {
                    dc.setInstanceBound(true);
                    instanceBoundDeps.add(dc);
                }
            }
            startDependencies(instanceBoundDeps);
            handleChange();
		});
		return this;
	}

	public ComponentImpl remove(final AbstractDependency d) {
		getExecutor().execute(() -> {
		    // First remove this dependency from the dependency list
		    m_dependencies.remove(d);
		    // Now we can stop the dependency (our component won't be deactivated, it will only be unbound with
		    // the removed dependency).
		    if (!(m_state == ComponentState.INACTIVE)) {
		        d.stop();
		    }
		    // Finally, cleanup the dependency events.
		    m_dependencyEvents.remove(d);
		    handleChange();
		});
		return this;
	}

	public void start() {
	    if (m_active.compareAndSet(false, true)) {
            getExecutor().execute(() -> {
                m_isStarted = true;
                handleChange();
            });
	    }
	}
	
	public void stop() {
	    if (m_active.compareAndSet(true, false)) {
	        Executor executor = getExecutor();

	        // First, declare the task that will stop our component in our executor.
	        final Runnable stopTask = () -> {
	            m_isStarted = false;
	            handleChange();
	        };
            
            // Now, we have to schedule our stopTask in our component executor. But we have to handle a special case:
            // if the component bundle is stopping *AND* if the executor is a parallel dispatcher, then we want 
            // to invoke our stopTask synchronously, in order to make sure that the bundle context is valid while our 
            // component is being deactivated (if we stop the component asynchronously, the bundle context may be invalidated
            // before our component is stopped, and we don't want to be in this situation).
            
            boolean stopping = m_bundle != null /* null only in tests env */ && m_bundle.getState() == Bundle.STOPPING;
            if (stopping && executor instanceof DispatchExecutor) {
            	((DispatchExecutor) executor).execute(stopTask, false /* try to  execute synchronously, not using threadpool */);
            } else {
            	executor.execute(stopTask);
            }
	    }
	}

	public ComponentImpl setInterface(String serviceName, Dictionary<String, Object> properties) {
		ensureNotActive();
	    m_serviceName = serviceName;
	    m_serviceProperties = properties;
	    return this;
	}

	public ComponentImpl setInterface(String[] serviceName, Dictionary<String, Object> properties) {
	    ensureNotActive();
	    m_serviceName = serviceName;
	    m_serviceProperties = properties;
	    return this;
	}
	
    public <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
    void handleEvent(final D dc, final EventType type, final E event) {
        // since this method can be invoked by anyone from any thread, we need to
        // pass on the event to a runnable that we execute using the component's
        // executor
        getExecutor().execute(() -> {
            try {
                switch (type) {
                    case ADDED:
                        handleAdded(dc, event);
                        break;
                    case CHANGED:
                        handleChanged(dc, event);
                        break;
                    case REMOVED:
                        handleRemoved(dc, event);
                        break;
                }
            } finally {
                // Clear cache of component callbacks invocation, except if we are currently called from handleChange().
                clearInvokeCallbackCache();
            }
            });
	}

    public <D extends AbstractDependency, S, E extends Event<S>>
    E getDependencyEvent(AbstractDependency<D, S, E> dc) {
        SortedSet<E> events = getDependencyEvents(dc);
        return events.isEmpty() ? null : events.last();
    }

    @SuppressWarnings("unchecked")
    public <D extends AbstractDependency, S, E extends Event<S>>
    SortedSet<E> getDependencyEvents(AbstractDependency<D, S, E> dc) {
        return (SortedSet) m_dependencyEvents.get(dc);
    }

    public <D extends AbstractDependency, S, E extends Event<S>>
    AtomicReference<E> getBoundReference(AbstractDependency<D, S, E> dc) {
        @SuppressWarnings("unchecked")
        AtomicReference<E> ref = (AtomicReference) m_dependencySelectedEvent.computeIfAbsent(dc, d -> new AtomicReference<>());
        return ref;
    }

    public <D extends AbstractDependency, S, E extends Event<S>>
    E getBoundDependencyEvent(AbstractDependency<D, S, E> dc) {
        AtomicReference<E> ref = getBoundReference(dc);
        E bound = ref.get();
        if (bound == null) {
            SortedSet<E> events = getDependencyEvents(dc);
            bound = events.isEmpty() ? null : events.last();
            ref.set(bound);
        }
        return bound;
    }

    public boolean isAvailable() {
        return m_state == ComponentState.TRACKING_OPTIONAL;
    }
    
    public boolean isActive() {
        return m_active.get();
    }
    
    public ComponentImpl add(final ComponentStateListener l) {
        m_listeners.add(l);
        return this;
    }

    public ComponentImpl remove(ComponentStateListener l) {
        m_listeners.remove(l);
        return this;
    }

    @SuppressWarnings("unchecked")
    public List<AbstractDependency<?, ?, ?>> getDependencies() {
        return (List<AbstractDependency<?, ?, ?>>) m_dependencies.clone();
    }

    public ComponentImpl setImplementation(Object implementation) {
        m_componentDefinition = implementation;
        return this;
    }
    
    public ServiceRegistration<?> getServiceRegistration() {
        return m_registration;
    }

    @SuppressWarnings("unchecked")
    public Dictionary<String, Object> getServiceProperties() {
        if (m_serviceProperties != null) {
            // Applied patch from FELIX-4304
            Hashtable<String, Object> serviceProperties = new Hashtable<>();
            addTo(serviceProperties, m_serviceProperties);
            return serviceProperties;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public ComponentImpl setServiceProperties(final Dictionary<String, Object> serviceProperties) {
        getExecutor().execute(() -> {
            Dictionary<String, Object> properties = null;
            m_serviceProperties = serviceProperties;
            if ((m_registration != null) && (m_serviceName != null)) {
                properties = calculateServiceProperties();
                m_registration.setProperties(properties);
            }
        });
        return this;
    }
    
     public DependencyManager getDependencyManager() {
        return m_manager;
    }
    
    public String getName() {
        StringBuffer sb = new StringBuffer();
        Object serviceName = m_serviceName;
        // If the component provides service(s), return the services as the component name.
        if (serviceName instanceof String[]) {
            String[] names = (String[]) serviceName;
            for (int i = 0; i < names.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(names[i]);
            }
            appendProperties(sb);
        } else if (serviceName instanceof String) {
            sb.append(serviceName.toString());
            appendProperties(sb);
        } else {
            // The component does not provide a service, use the component definition as the name.
            Object componentDefinition = m_componentDefinition;
            if (componentDefinition != null) {
                sb.append(toString(componentDefinition));
            } else { 
                // No component definition means we are using a factory. If the component instance is available use it as the component name,
                // alse use teh factory object as the component name.
                Object componentInstance = m_componentInstance;
                if (componentInstance != null) {
                    sb.append(componentInstance.getClass().getName());
                } else {
                    // Check if a factory is set.
                    sb.append(super.toString());
                }
            }
        }
        return sb.toString();
    }
    
    private String toString(Object implementation) {
        if (implementation instanceof Class) {
            return (((Class<?>) implementation).getName());
        } else {
            // If the implementation instance does not override "toString", just display
            // the class name, else display the component using its toString method
            try {
            Method m = implementation.getClass().getMethod("toString", new Class[0]);
                if (m.getDeclaringClass().equals(Object.class)) {
                    return implementation.getClass().getName();
                } else {
                    return implementation.toString();
                }
            }  catch (NoSuchMethodException e) {
                // Just display the class name
                return implementation.getClass().getName();
            }
        }
    }
    
    public BundleContext getBundleContext() {
        return m_context;
    }
    
    public Bundle getBundle() {
        return m_bundle;
    }

    public long getId() {
        return m_id;
    }
    
    public String getClassName() {
        Object serviceInstance = m_componentInstance;
        if (serviceInstance != null) {
            return serviceInstance.getClass().getName();
        } 
        
        Object implementation = m_componentDefinition;
        if (implementation != null) {
            if (implementation instanceof Class) {
                return ((Class<?>) implementation).getName();
            }
            return implementation.getClass().getName();
        } 
        
        return ComponentImpl.class.getName();
    }
    
    public String[] getServices() {
        if (m_serviceName instanceof String[]) {
            return (String[]) m_serviceName;
        } else if (m_serviceName instanceof String) {
            return new String[] { (String) m_serviceName };
        } else {
            return null;
        }
    }
    
    public void ensureNotActive() {
        if (m_active.get()) {
            throw new IllegalStateException("Can't modify an already started component.");
        }
    }
    
    @Override
    public String toString() {
        if (m_logger.getDebugKey() != null) {
            return m_logger.getDebugKey();
        }
        return getClassName();
    }
    
    public void setThreadPool(Executor threadPool) {
        ensureNotActive();
        m_executor = new DispatchExecutor(threadPool, m_logger);
    }
    
    public Logger getLogger() {
        return m_logger;
    }

    public Map<String, Long> getCallbacksTime() {
        return m_stopwatch;
    }
    
    // ---------------------- Package/Private methods ---------------------------
    
    void instantiateComponent() {
        // TODO add more complex factory instantiations of one or more components in a composition here
        if (m_componentInstance == null) {
            m_logger.debug("instantiating component.");
            m_componentInstance = m_componentDefinition;
        }
    }    
    
    /**
     * Runs the state machine, to see if a change event has to trigger some component state transition.
     */
    private void handleChange() {
        m_logger.debug("handleChanged");
    	handlingChange(true);
        try {
            ComponentState oldState;
            ComponentState newState;
            do {
                oldState = m_state;
                newState = calculateNewState(oldState);
                m_logger.debug("%s -> %s", oldState, newState);
                m_state = newState;
            } while (performTransition(oldState, newState));
        } finally {
        	handlingChange(false);
            m_logger.debug("end handling change.");
        }
    }
    
    /** 
     * Based on the current state, calculate the new state. 
     */
    private ComponentState calculateNewState(ComponentState currentState) {
        if (currentState == ComponentState.INACTIVE) {
            if (m_isStarted) {
                return ComponentState.WAITING_FOR_REQUIRED;
            }
        }
        if (currentState == ComponentState.WAITING_FOR_REQUIRED) {
            if (!m_isStarted) {
                return ComponentState.INACTIVE;
            }
            if (allRequiredAvailable()) {
                return ComponentState.INSTANTIATED_AND_WAITING_FOR_REQUIRED;
            }
        }
        if (currentState == ComponentState.INSTANTIATED_AND_WAITING_FOR_REQUIRED) {
            if (m_isStarted && allRequiredAvailable()) {
                if (allInstanceBoundAvailable()) {
                    return ComponentState.TRACKING_OPTIONAL;
                }
                return currentState;
            }
            return ComponentState.WAITING_FOR_REQUIRED;
        }
        if (currentState == ComponentState.TRACKING_OPTIONAL) {
            if (m_isStarted && allRequiredAvailable() && allInstanceBoundAvailable()) {
                return currentState;
            }
            return ComponentState.INSTANTIATED_AND_WAITING_FOR_REQUIRED;
        }
        return currentState;
    }

    /** 
     * Perform all the actions associated with state transitions. 
     * @returns true if a transition was performed.
     **/
    private boolean performTransition(ComponentState oldState, ComponentState newState) {
        if (oldState == ComponentState.INACTIVE && newState == ComponentState.WAITING_FOR_REQUIRED) {
            startDependencies(m_dependencies);
            notifyListeners(newState);
            return true;
        }
        if (oldState == ComponentState.WAITING_FOR_REQUIRED && newState == ComponentState.INSTANTIATED_AND_WAITING_FOR_REQUIRED) {
            instantiateComponent();
            invokeAddRequiredDependencies();
			ComponentState stateBeforeCallingInit = m_state;
            invokeInit();
	        if (stateBeforeCallingInit == m_state) {
	            notifyListeners(newState); // init did not change current state, we can notify about this new state
	        }
            return true;
        }
        if (oldState == ComponentState.INSTANTIATED_AND_WAITING_FOR_REQUIRED && newState == ComponentState.TRACKING_OPTIONAL) {
            invokeAddRequiredInstanceBoundDependencies();
            invokeStart();
            invokeAddOptionalDependencies();
            registerService();
            notifyListeners(newState);
            return true;
        }
        if (oldState == ComponentState.TRACKING_OPTIONAL && newState == ComponentState.INSTANTIATED_AND_WAITING_FOR_REQUIRED) {
            unregisterService();
            invokeRemoveOptionalDependencies();
            invokeStop();
            invokeRemoveInstanceBoundDependencies();
            notifyListeners(newState);
            return true;
        }
        if (oldState == ComponentState.INSTANTIATED_AND_WAITING_FOR_REQUIRED && newState == ComponentState.WAITING_FOR_REQUIRED) {
            invokeDestroy();
            removeInstanceBoundDependencies();
            invokeRemoveRequiredDependencies();
            notifyListeners(newState);
            if (! someDependenciesNeedInstance()) {
                destroyComponent();
            }
            return true;
        }
        if (oldState == ComponentState.WAITING_FOR_REQUIRED && newState == ComponentState.INACTIVE) {
            stopDependencies();
            destroyComponent();
            notifyListeners(newState);
            return true;
        }
        return false;
    }
    
	private void invokeStart() {
        m_startCalled = true;
	}

    private void invokeStop() {
        m_startCalled = false;
	}

	/**
     * Sets the m_handlingChange flag that indicates if the state machine is currently running the handleChange method.
     */
    private void handlingChange(boolean transiting) {
        m_handlingChange = transiting;
    }
    
    /**
     * Are we currently running the handleChange method ?
     */
    private boolean isHandlingChange() {
    	return m_handlingChange;
    }

    /**
     * Then handleEvent calls this method when a dependency service is being added.
     */
    private <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
    void handleAdded(D dc, E e) {
        if (! m_isStarted) {
            return;
        }
        m_logger.debug("handleAdded %s", e);
        
        SortedSet<E> dependencyEvents = getDependencyEvents(dc);
        dependencyEvents.add(e);
        dc.setAvailable(true);

        // In the following switch block, we sometimes only recalculate state changes
        // if the dependency is fully started. If the dependency is not started,
        // it means it is actually starting (the service tracker is executing the open method). 
        // And in this case, depending on the state, we don't recalculate state changes now. 
        // 
        // All this is done for two reasons:
        // 1- optimization: it is preferable to recalculate state changes once we know about all currently 
        //    available dependency services (after the tracker has returned from its open method).
        // 2- This also allows to determine the list of currently available dependency services before calling
        //    the component start() callback.
        
        switch (m_state) {
        case WAITING_FOR_REQUIRED:            
            if (dc.isStarted() && dc.isRequired()) {
                handleChange();
            }
            break;
        case INSTANTIATED_AND_WAITING_FOR_REQUIRED:
            if (!dc.isInstanceBound()) {
                if (dc.isRequired()) {
                    invokeCallbackSafe(dc, EventType.ADDED, e);
                }
                updateInstance(dc, e, false, true);
            } else {
                if (dc.isStarted() && dc.isRequired()) {
                    handleChange();
                }
            }
            break;
        case TRACKING_OPTIONAL:
            invokeCallbackSafe(dc, EventType.ADDED, e);
            if (dc.isGreedy() && dependencyEvents.last() == e) {
                updateInstance(dc, e, false, true);
            }
            break;
        default:
        }
    }       
    
    /**
     * Then handleEvent calls this method when a dependency service is being changed.
     */
    private <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
    void handleChanged(final D dc, final E e) {
        if (! m_isStarted) {
            return;
        }
        Set<E> dependencyEvents = getDependencyEvents(dc);
        dependencyEvents.remove(e);
        dependencyEvents.add(e);
                
        switch (m_state) {
        case TRACKING_OPTIONAL:
            invokeCallbackSafe(dc, EventType.CHANGED, e);
            updateInstance(dc, e, true, false);
            break;

        case INSTANTIATED_AND_WAITING_FOR_REQUIRED:
            if (!dc.isInstanceBound()) {
                invokeCallbackSafe(dc, EventType.CHANGED, e);
                updateInstance(dc, e, true, false);
            }
            break;
        default:
            // noop
        }
    }
    
    /**
     * Then handleEvent calls this method when a dependency service is being removed.
     */
    private <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
    void handleRemoved(D dc, E e) {
        if (! m_isStarted) {
            return;
        }
        // Check if the dependency is still available.
        SortedSet<E> dependencyEvents = getDependencyEvents(dc);
        int size = dependencyEvents.size();
        if (dependencyEvents.contains(e)) {
            size--; // the dependency is currently registered and is about to be removed.
        }
        dc.setAvailable(size > 0);
        
        // If the dependency is now unavailable, we have to recalculate state change. This will result in invoking the
        // "removed" callback with the removed dependency (which we have not yet removed from our dependency events list.).
        // But we don't recalculate the state if the dependency is not started (if not started, it means that it is currently starting,
        // and the tracker is detecting a removed service).
        if (size == 0 && dc.isStarted()) {
            handleChange();
        }
        
        // Now, really remove the dependency event.
        AtomicReference<E> ref = getBoundReference(dc);
        boolean bound = e.equals(ref.get());
        if (bound) {
            ref.set(null);
        }

        dependencyEvents.remove(e);
        
        // Depending on the state, we possible have to invoke the callbacks and update the component instance.        
        switch (m_state) {
        case INSTANTIATED_AND_WAITING_FOR_REQUIRED:
            if (!dc.isInstanceBound()) {
                if (dc.isRequired()) {
                    invokeCallbackSafe(dc, EventType.REMOVED, e);
                }
                updateInstance(dc, e, false, false);
            }
            break;
        case TRACKING_OPTIONAL:
            invokeCallbackSafe(dc, EventType.REMOVED, e);
            if (bound) {
                updateInstance(dc, e, false, false);
            }
            break;
        default:
        }
    }

    private boolean allRequiredAvailable() {
        return !m_dependencies
                .stream()
                .filter(d -> d.isRequired() && !d.isInstanceBound() && !d.isAvailable())
                .findAny().isPresent();
    }

    private boolean allInstanceBoundAvailable() {
        return !m_dependencies
                .stream()
                .filter(d -> d.isRequired() && d.isInstanceBound() && !d.isAvailable())
                .findAny().isPresent();
    }

    private boolean someDependenciesNeedInstance() {
        return m_dependencies
                .stream()
                .filter(AbstractDependency::needsInstance)
                .findAny().isPresent();
    }

    /**
     * Updates the component instance(s).
     * @param dc the dependency context for the updating dependency service
     * @param event the event holding the updating service (service + properties)
     * @param update true if dependency service properties are updating, false if not. If false, it means
     *        that a dependency service is being added or removed. (see the "add" flag).
     * @param add true if the dependency service has been added, false if it has been removed. This flag is 
     *        ignored if the "update" flag is true (because the dependency properties are just being updated).
     */
    protected <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
    void updateInstance(D dc, E event, boolean update, boolean add) {
        if (dc.isPropagated() && m_registration != null) {
            m_registration.setProperties(calculateServiceProperties());
        }
        m_dependencySelectedEvent.computeIfAbsent(dc, d -> new AtomicReference<>()).set(event);
    }
    
    private void startDependencies(List<AbstractDependency<?, ?, ?>> dependencies) {
        // Start first optional dependencies first.
        m_logger.debug("startDependencies.");
        dependencies.stream()
                .filter(AbstractDependency::isOptional)
                .forEach(this::startDependency);
        dependencies.stream()
                .filter(AbstractDependency::isRequired)
                .forEach(this::startDependency);
    }

    private void startDependency(AbstractDependency<?, ?, ?> d) {
        if (d.needsInstance()) {
            instantiateComponent();
        }
        d.start();
    }
    
    private void stopDependencies() {
        m_dependencies.forEach(AbstractDependency::stop);
    }

    private void registerService() {
        if (m_context != null && m_serviceName != null) {
            ServiceRegistrationImpl wrapper = new ServiceRegistrationImpl();
            m_registration = wrapper;

            // service name can either be a string or an array of strings
            ServiceRegistration registration;

            // determine service properties
            Dictionary<String, Object> properties = calculateServiceProperties();

            // register the service
            try {
                if (m_serviceName instanceof String) {
                    registration = m_context.registerService((String) m_serviceName, m_componentInstance, properties);
                }
                else {
                    registration = m_context.registerService((String[]) m_serviceName, m_componentInstance, properties);
                }
                wrapper.setServiceRegistration(registration);
            }
            catch (IllegalArgumentException iae) {
                m_logger.log(Logger.LOG_ERROR, "Could not register service " + m_componentInstance, iae);
                // set the registration to an illegal state object, which will make all invocations on this
                // wrapper fail with an ISE (which also occurs when the SR becomes invalid)
                wrapper.setIllegalState();
            }
        }
    }

    private void unregisterService() {
        if (m_serviceName != null && m_registration != null) {
            try {
                if (m_bundle != null && (m_bundle.getState() == Bundle.ACTIVE || m_bundle.getState() == Bundle.STOPPING)) {
                    m_registration.unregister();
                }
            } catch (IllegalStateException e) { /* Should we really log this ? */}
            m_registration = null;
        }
    }
    
    private Dictionary<String, Object> calculateServiceProperties() {
		Dictionary<String, Object> properties = new Hashtable<>();
        for (AbstractDependency<?, ?, ?> d : m_dependencies) {
            if (d.isPropagated() && d.isAvailable()) {
                Dictionary<String, Object> dict = d.getProperties();
                addTo(properties, dict);
            }
        }
		// our service properties must not be overriden by propagated dependency properties, so we add our service
		// properties after having added propagated dependency properties.
		addTo(properties, m_serviceProperties);
		if (properties.size() == 0) {
			properties = null;
		}
		return properties;
	}

	private void addTo(Dictionary<String, Object> properties, Dictionary<String, Object> additional) {
		if (properties == null) {
			throw new IllegalArgumentException("Dictionary to add to cannot be null.");
		}
		if (additional != null) {
			Enumeration<String> e = additional.keys();
			while (e.hasMoreElements()) {
				String key = e.nextElement();
				properties.put(key, additional.get(key));
			}
		}
	}
	
	private void destroyComponent() {
		m_componentInstance = null;
	}

    protected void invokeInit() {
    }

    protected void invokeDestroy() {
    }

	private void invokeAddRequiredDependencies() {
        invokeCallbacksFilter(EventType.ADDED, d -> d.isRequired() && !d.isInstanceBound());
	}

	private void invokeAddRequiredInstanceBoundDependencies() {
        invokeCallbacksFilter(EventType.ADDED, d -> d.isRequired() && d.isInstanceBound());
	}

    private void invokeAddOptionalDependencies() {
        invokeCallbacksFilter(EventType.ADDED, d -> !d.isRequired());
    }

    private void invokeRemoveRequiredDependencies() {
        invokeCallbacksFilter(EventType.REMOVED, d -> !d.isInstanceBound() && d.isRequired());
	}

    private void invokeRemoveOptionalDependencies() {
        invokeCallbacksFilter(EventType.REMOVED, d -> !d.isRequired());
    }

	private void invokeRemoveInstanceBoundDependencies() {
        invokeCallbacksFilter(EventType.REMOVED, d -> d.isInstanceBound());
	}

    private void invokeCallbacksFilter(EventType type, Predicate<? super AbstractDependency<?, ?, ?>> pred) {
        m_dependencies
                .stream()
                .filter(pred)
                .forEach(d -> invokeCallbacks(type, (AbstractDependency) d));
    }

    private <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
    void invokeCallbacks(EventType type, D dependency) {
        for (E e : getDependencyEvents(dependency)) {
            invokeCallbackSafe(dependency, type, e);
        }
    }

    /**
	 * This method ensures that a dependency callback is invoked only one time;
	 * It also ensures that if the dependency callback is optional, then we only
	 * invoke the bind method if the component start callback has already been called.
	 */
	private <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
    void invokeCallbackSafe(D dc, EventType type, E event) {
		if (!dc.isRequired() && !m_startCalled) {
			return;
		}
		if (m_invokeCallbackCache.put(event, event) == null) {
			dc.invokeCallback(type, event);
		}
	}

	/**
	 * Removes and closes all instance bound dependencies.
	 * This method is called when a component is destroyed.
	 */
    private void removeInstanceBoundDependencies() {
    	for (AbstractDependency dep : m_dependencies) {
    		if (dep.isInstanceBound()) {
    			m_dependencies.remove(dep);
    			dep.stop();
    		}
    	}
    }

    /**
     * Clears the cache of invoked components callbacks.
     * We only clear the cache when the state machine is not running.
     * The cache is used to avoid calling the same bind callback twice.
     * See FELIX-4913.
     */
    private void clearInvokeCallbackCache() {
        if (! isHandlingChange()) {
            m_invokeCallbackCache.clear();
        }
    }

    private void notifyListeners(ComponentState state) {
		for (ComponentStateListener l : m_listeners) {
			l.changed(this, state);
		}
	}
	
    private void appendProperties(StringBuffer result) {
        Dictionary<String, Object> properties = calculateServiceProperties();
        if (properties != null) {
            result.append("(");
            Enumeration<?> enumeration = properties.keys();
            while (enumeration.hasMoreElements()) {
                Object key = enumeration.nextElement();
                result.append(key.toString());
                result.append('=');
                Object value = properties.get(key);
                if (value instanceof String[]) {
                    String[] values = (String[]) value;
                    result.append('{');
                    for (int i = 0; i < values.length; i++) {
                        if (i > 0) {
                            result.append(',');
                        }
                        result.append(values[i]);
                    }
                    result.append('}');
                }
                else {
                    result.append(value.toString());
                }
                if (enumeration.hasMoreElements()) {
                    result.append(',');
                }
            }
            result.append(")");
        }
    }
}
