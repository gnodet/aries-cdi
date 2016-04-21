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

import java.util.AbstractMap;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.apache.aries.cdi.impl.dm.tracker.ServiceTracker;
import org.apache.aries.cdi.impl.dm.tracker.ServiceTrackerCustomizer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class ServiceDependencyImpl<S> extends AbstractDependency<ServiceDependencyImpl<S>, S, ServiceEventImpl<S>> implements ServiceTrackerCustomizer<S, S> {
	protected volatile ServiceTracker<S, S> m_tracker;
    protected volatile Class<?> m_trackedServiceName;
    private volatile String m_trackedServiceFilter;
    private volatile String m_trackedServiceFilterUnmodified;
    private volatile ServiceReference<S> m_trackedServiceReference;
    private volatile boolean m_debug = false;
    private volatile String m_debugKey;
    private volatile long m_trackedServiceReferenceId;
    
    public ServiceDependencyImpl<S> setDebug(String debugKey) {
    	m_debugKey = debugKey;
    	m_debug = true;
    	return this;
    }

    /**
     * Entry to wrap service properties behind a Map.
     */
    private static final class ServicePropertiesMapEntry implements Map.Entry<String, Object> {
        private final String m_key;
        private Object m_value;

        public ServicePropertiesMapEntry(String key, Object value) {
            m_key = key;
            m_value = value;
        }

        public String getKey() {
            return m_key;
        }

        public Object getValue() {
            return m_value;
        }

        public String toString() {
            return m_key + "=" + m_value;
        }

        public Object setValue(Object value) {
            Object oldValue = m_value;
            m_value = value;
            return oldValue;
        }

        @SuppressWarnings("unchecked")
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<String, Object> e = (Map.Entry<String, Object>) o;
            return eq(m_key, e.getKey()) && eq(m_value, e.getValue());
        }

        public int hashCode() {
            return ((m_key == null) ? 0 : m_key.hashCode()) ^ ((m_value == null) ? 0 : m_value.hashCode());
        }

        private static boolean eq(Object o1, Object o2) {
            return (o1 == null ? o2 == null : o1.equals(o2));
        }
    }

    /**
     * Wraps service properties behind a Map.
     */
    private final static class ServicePropertiesMap extends AbstractMap<String, Object> {
        private final ServiceReference<?> m_ref;

        public ServicePropertiesMap(ServiceReference<?> ref) {
            m_ref = ref;
        }

        public Object get(Object key) {
            return m_ref.getProperty(key.toString());
        }

        public int size() {
            return m_ref.getPropertyKeys().length;
        }

        public Set<Map.Entry<String, Object>> entrySet() {
            Set<Map.Entry<String, Object>> set = new HashSet<>();
            String[] keys = m_ref.getPropertyKeys();
            for (String key : keys) {
                set.add(new ServicePropertiesMapEntry(key, m_ref.getProperty(key)));
            }
            return set;
        }
    }

	public ServiceDependencyImpl() {
	}
	
    // --- CREATION
    			
   	public ServiceDependencyImpl<S> setService(Class<?> serviceName) {
        setService(serviceName, null, null);
        return this;
    }

    public ServiceDependencyImpl<S> setService(Class<?> serviceName, String serviceFilter) {
        setService(serviceName, null, serviceFilter);
        return this;
    }

    public ServiceDependencyImpl<S> setService(String serviceFilter) {
        if (serviceFilter == null) {
            throw new IllegalArgumentException("Service filter cannot be null.");
        }
        setService(null, null, serviceFilter);
        return this;
    }

    public ServiceDependencyImpl<S> setService(Class<?> serviceName, ServiceReference<S> serviceReference) {
        setService(serviceName, serviceReference, null);
        return this;
    }

	@Override
	public void start() {
        if (m_trackedServiceName != null) {
            BundleContext ctx = m_component.getBundleContext();
            if (m_trackedServiceFilter != null) {
                try {
                    m_tracker = new ServiceTracker<>(ctx, ctx.createFilter(m_trackedServiceFilter), this);
                } catch (InvalidSyntaxException e) {
                    throw new IllegalStateException("Invalid filter definition for dependency: "
                        + m_trackedServiceFilter);
                }
            } else if (m_trackedServiceReference != null) {
                m_tracker = new ServiceTracker<>(ctx, m_trackedServiceReference, this);
            } else {
                m_tracker = new ServiceTracker<>(ctx, m_trackedServiceName.getName(), this);
            }
        } else {
            throw new IllegalStateException("Could not create tracker for dependency, no service name specified.");
        }
        if (m_debug) {
            m_tracker.setDebug(m_debugKey);
        }
        m_tracker.open();
        super.start();
	}
	
	@Override
	public void stop() {
	    m_tracker.close();
	    m_tracker = null;
	    super.stop();
	}

	@Override
	public S addingService(ServiceReference<S> reference) {
		try {
		    return m_component.getBundleContext().getService(reference);
		} catch (IllegalStateException e) {
		    // most likely our bundle is being stopped. Only log an exception if our component is enabled.
		    if (m_component.isActive()) {
                m_component.getLogger().warn("could not handle service dependency for component %s", e,
                        m_component.getClassName());
		    }
		    return null;		    
		}
	}

	@Override
	public void addedService(ServiceReference<S> reference, S service) {
		if (m_debug) {
			System.out.println(m_debugKey + " addedService: ref=" + reference + ", service=" + service);
		}
        m_component.handleEvent(this, EventType.ADDED,
            new ServiceEventImpl<>(m_component.getBundle(), m_component.getBundleContext(), reference, service));
	}

	@Override
	public void modifiedService(ServiceReference<S> reference, S service) {
        m_component.handleEvent(this, EventType.CHANGED,
            new ServiceEventImpl<>(m_component.getBundle(), m_component.getBundleContext(), reference, service));
	}

	@Override
	public void removedService(ServiceReference<S> reference, S service) {
        m_component.handleEvent(this, EventType.REMOVED,
            new ServiceEventImpl<>(m_component.getBundle(), m_component.getBundleContext(), reference, service));
	}
	
    @Override
    public void invokeCallback(EventType type, ServiceEventImpl<S> event) {
        switch (type) {
        case ADDED:
            break;
        case CHANGED:
            break;
        case REMOVED:
            break;
        }
    }

    @Override
    public String getName() {
        StringBuilder sb = new StringBuilder();
        if (m_trackedServiceName != null) {
            sb.append(m_trackedServiceName.getName());
            if (m_trackedServiceFilterUnmodified != null) {
                sb.append(' ');
                sb.append(m_trackedServiceFilterUnmodified);
            }
        }
        if (m_trackedServiceReference != null) {
            sb.append("{service.id=").append(m_trackedServiceReference.getProperty(Constants.SERVICE_ID)).append("}");
        }
        return sb.toString();
    }
    
    @Override
    public String getSimpleName() {
        if (m_trackedServiceName != null) {
            return m_trackedServiceName.getName();
        }
        return null;
    }

    @Override
    public String getFilter() {
        if (m_trackedServiceFilterUnmodified != null) {
            return m_trackedServiceFilterUnmodified;
        } else if (m_trackedServiceReference != null) {
            return "(" + Constants.SERVICE_ID + "=" + String.valueOf(m_trackedServiceReferenceId) + ")";
        } else {
            return null;
        }
    }
    
    @Override
    public String getType() {
        return "service";
    }

	@SuppressWarnings("unchecked")
    @Override
    public Dictionary<String, Object> getProperties() {
        ServiceEventImpl se = (ServiceEventImpl) m_component.getDependencyEvent(this);
        if (se != null) {
            Hashtable<String, Object> props = new Hashtable<>();
            String[] keys = se.getReference().getPropertyKeys();
            for (String key : keys) {
                if (!(key.equals(Constants.SERVICE_ID) || key.equals(Constants.SERVICE_PID))) {
                    props.put(key, se.getReference().getProperty(key));
                }
            }
            return props;
        } else {
            throw new IllegalStateException("cannot find service reference");
        }
    }	
        
    /** Internal method to set the name, service reference and/or filter. */
    private void setService(Class<?> serviceName, ServiceReference<S> serviceReference, String serviceFilter) {
        ensureNotActive();
        if (serviceName == null) {
            m_trackedServiceName = Object.class;
        }
        else {
            m_trackedServiceName = serviceName;
        }
        if (serviceFilter != null) {
            m_trackedServiceFilterUnmodified = serviceFilter;
            if (serviceName == null) {
                m_trackedServiceFilter = serviceFilter;
            }
            else {
                m_trackedServiceFilter = "(&(" + Constants.OBJECTCLASS + "=" + serviceName.getName() + ")"
                    + serviceFilter + ")";
            }
        }
        else {
            m_trackedServiceFilterUnmodified = null;
            m_trackedServiceFilter = null;
        }
        if (serviceReference != null) {
            m_trackedServiceReference = serviceReference;
            if (serviceFilter != null) {
                throw new IllegalArgumentException("Cannot specify both a filter and a service reference.");
            }
            m_trackedServiceReferenceId = (Long) m_trackedServiceReference.getProperty(Constants.SERVICE_ID);
        }
        else {
            m_trackedServiceReference = null;
        }
    }

}
