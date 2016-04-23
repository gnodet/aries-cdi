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
package org.apache.aries.cdi.impl.osgi;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.aries.cdi.api.Attribute;
import org.apache.aries.cdi.api.Component;
import org.apache.aries.cdi.api.Config;
import org.apache.aries.cdi.api.Contract;
import org.apache.aries.cdi.api.Contracts;
import org.apache.aries.cdi.api.Dynamic;
import org.apache.aries.cdi.api.Greedy;
import org.apache.aries.cdi.api.Immediate;
import org.apache.aries.cdi.api.Optional;
import org.apache.aries.cdi.api.Properties;
import org.apache.aries.cdi.api.Property;
import org.apache.aries.cdi.api.Service;
import org.apache.aries.cdi.impl.dm.AbstractDependency;
import org.apache.aries.cdi.impl.dm.ComponentDependencyImpl;
import org.apache.aries.cdi.impl.dm.ComponentImpl;
import org.apache.aries.cdi.impl.dm.ComponentState;
import org.apache.aries.cdi.impl.dm.Configurable;
import org.apache.aries.cdi.impl.dm.ConfigurationDependencyImpl;
import org.apache.aries.cdi.impl.dm.Event;
import org.apache.aries.cdi.impl.dm.ServiceDependencyImpl;
import org.apache.aries.cdi.impl.dm.ServiceEventImpl;
import org.apache.aries.cdi.impl.osgi.support.Filters;
import org.apache.aries.cdi.impl.osgi.support.IterableInstance;
import org.apache.aries.cdi.impl.osgi.support.MappingIterator;
import org.apache.aries.cdi.impl.osgi.support.SimpleBean;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

public class ComponentDescriptor {

    private final Bean<Object> bean;
    private final ComponentRegistry registry;
    private final ComponentImpl component;
    private final Map<InjectionPoint, Dependency> dependencies = new HashMap<>();

    public ComponentDescriptor(Bean<Object> bean, ComponentRegistry registry) {
        this.bean = bean;
        this.registry = registry;
        this.component = new ComponentImpl(registry.getBundleContext(), registry.getDm()) {
            protected <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
            void updateInstance(D dc, E event, boolean update, boolean add) {
                registry.deactivate(ComponentDescriptor.this);
                registry.activate(ComponentDescriptor.this);
                super.updateInstance(dc, event, update, add);
            }

            @Override
            protected Object doInstantiateComponent() {
                return new ServiceFactory<Object>() {
                    @Override
                    public Object getService(Bundle bundle, ServiceRegistration registration) {
                        BeanManager beanManager = registry.getBeanManager();
                        Context context = beanManager.getContext(Component.class);
                        return context.get(bean, beanManager.createCreationalContext(bean));
                    }
                    @Override
                    public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
                        // TODO ?
                    }
                };
            }
        };

        boolean hasService = false;
        List<String> names = new ArrayList<>();
        Dictionary<String, Object> properties = new Hashtable<>();
        for (Annotation annotation : bean.getQualifiers()) {
            if (annotation instanceof Service) {
                hasService = true;
            } else if (annotation instanceof Contract) {
                names.add(((Contract) annotation).value().getName());
            } else if (annotation instanceof Contracts) {
                for (Contract ctr : ((Contracts) annotation).value()) {
                    names.add(ctr.value().getName());
                }
            } else if (annotation instanceof Properties) {
                for (Property prop : ((Properties) annotation).value()) {
                    properties.put(prop.name(), prop.value());
                }
            } else {
                Class<? extends Annotation> annClass = annotation.annotationType();
                Attribute attr = annClass.getAnnotation(Attribute.class);
                if (attr != null) {
                    String name = attr.value();
                    Object value;
                    try {
                        Method[] methods = annClass.getDeclaredMethods();
                        if (methods != null && methods.length == 1) {
                            value = methods[0].invoke(annotation);
                        } else {
                            throw new IllegalArgumentException("Bad attribute " + annClass);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                    properties.put(name, value);
                }
            }
        }
        if (hasService) {
            if (names.isEmpty()) {
                for (Class cl : bean.getBeanClass().getInterfaces()) {
                    names.add(cl.getName());
                }
            }
            if (names.isEmpty()) {
                names.add(bean.getBeanClass().getName());
            }
            this.component.setInterface(names.toArray(new String[names.size()]), properties);
        }
    }

    public Bean<Object> getBean() {
        return bean;
    }

    public ComponentRegistry getRegistry() {
        return registry;
    }

    public boolean isImmediate() {
        for (Annotation anno : bean.getQualifiers()) {
            if (anno instanceof Immediate) {
                return true;
            }
        }
        return false;
    }

    public void addReference(InjectionPoint ip) {
        dependencies.put(ip, new ReferenceDependency(ip));
    }

    public void addDependency(InjectionPoint ip) {
        dependencies.put(ip, new ComponentDependency(ip));
    }

    public void addConfig(InjectionPoint ip) {
        dependencies.put(ip, new ConfigDependency(ip));
    }

    public void preStart(AfterBeanDiscovery event) {
        dependencies.values().forEach(s -> s.preStart(event));
    }

    public void start() {
        component.add((c, state) -> {
            if (state == ComponentState.TRACKING_OPTIONAL) {
                registry.activate(ComponentDescriptor.this);
            } else {
                registry.deactivate(ComponentDescriptor.this);
            }
        });
        getRegistry().getDm().add(component);
    }

    @Override
    public String toString() {
        return "Component[" +
                "bean=" + bean +
                ", component=" + component +
                ']';
    }

    public void inject(Object instance, InjectionPoint injectionPoint) {
        Dependency dependency = dependencies.get(injectionPoint);
        if (dependency instanceof ReferenceDependency) {
            ReferenceDependency ref = (ReferenceDependency) dependency;
            if (ref.injectionPoint == injectionPoint && ref.isInstance) {
                Field field = ((AnnotatedField) injectionPoint.getAnnotated()).getJavaMember();
                field.setAccessible(true);
                try {
                    field.set(instance, ref.getService());
                }
                catch (IllegalAccessException exc) {
                    throw new RuntimeException(exc);
                }
            }
        }
    }

    public abstract class Dependency {

        protected final InjectionPoint injectionPoint;
        protected final Class<?> clazz;
        protected final boolean isInstance;

        public Dependency(InjectionPoint injectionPoint) {
            this.injectionPoint = injectionPoint;
            Type type = injectionPoint.getType();
            if (type instanceof ParameterizedType) {
                Type raw = ((ParameterizedType) type).getRawType();
                if (raw == Instance.class) {
                    isInstance = true;
                    Type tp = ((ParameterizedType) type).getActualTypeArguments()[0];
                    clazz = (Class) ((ParameterizedType) type).getActualTypeArguments()[0];
                } else {
                    isInstance = false;
                    clazz = (Class) ((ParameterizedType) type).getRawType();
                }
            } else {
                if (type == Instance.class) {
                    throw new IllegalArgumentException();
                }
                isInstance = false;
                clazz = (Class) type;
            }
        }

        abstract void preStart(AfterBeanDiscovery event);

    }

    public class ReferenceDependency extends Dependency {

        protected final ServiceDependencyImpl<Object> sd;

        public ReferenceDependency(InjectionPoint injectionPoint) {
            super(injectionPoint);

            String filter = Filters.getFilter(injectionPoint.getAnnotated().getAnnotations());

            boolean optional = injectionPoint.getAnnotated().isAnnotationPresent(Optional.class);
            boolean greedy = injectionPoint.getAnnotated().isAnnotationPresent(Greedy.class);
            boolean dynamic = injectionPoint.getAnnotated().isAnnotationPresent(Dynamic.class);
            boolean multiple = isInstance;
            sd = new ServiceDependencyImpl<>()
                    .setRequired(!optional)
                    .setGreedy(greedy)
                    .setDynamic(dynamic)
                    .setMultiple(multiple)
                    .setService(clazz, filter);
            component.add(sd);
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            event.addBean(new SimpleBean<>(clazz, Dependent.class, injectionPoint, this::getService));
        }

        protected Object getService() {
            if (isInstance) {
                Iterable<Object> iterable = () -> new MappingIterator<>(
                        component.getDependencyEvents(sd).iterator(),
                        this::getService
                );
                return new IterableInstance<>(iterable);
            }
            return sd.getService();
        }

        protected <T> T getService(ServiceEventImpl<T> event) {
            return getRegistry().getBundleContext().getService(event.getReference());
        }
    }

    public class ComponentDependency extends Dependency {

        public ComponentDependency(InjectionPoint injectionPoint) {
            super(injectionPoint);
            if (isInstance) {
                throw new IllegalArgumentException("Illegal use of Instance<?> on component: " + clazz.getName());
            }
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            Set<Annotation> qualifiersSet = injectionPoint.getQualifiers();
            Annotation[] qualifiers = qualifiersSet.toArray(new Annotation[qualifiersSet.size()]);
            ComponentDescriptor resolved = getRegistry().resolve(injectionPoint.getType(), qualifiers);

            ComponentDependencyImpl cd = new ComponentDependencyImpl()
                    .setComponent(resolved.component);
            component.add(cd);
        }

    }

    public class ConfigDependency extends Dependency {

        protected final ConfigurationDependencyImpl cd;

        public ConfigDependency(InjectionPoint injectionPoint) {
            super(injectionPoint);
            if (isInstance) {
                throw new IllegalArgumentException("Illegal use of Instance<?> on configuration: " + clazz.getName());
            }
            if (!clazz.isAnnotation()) {
                throw new IllegalArgumentException("Configuration class should be an annotation: " + clazz.getName());
            }

            Config config = injectionPoint.getAnnotated().getAnnotation(Config.class);
            String pid = config.pid().isEmpty() ? clazz.getName() : config.pid();
            boolean optional = injectionPoint.getAnnotated().isAnnotationPresent(Optional.class);
            boolean greedy = injectionPoint.getAnnotated().isAnnotationPresent(Greedy.class);
            boolean dynamic = injectionPoint.getAnnotated().isAnnotationPresent(Dynamic.class);

            cd = new ConfigurationDependencyImpl()
                    .setPid(pid)
                    .setRequired(!optional)
                    .setGreedy(greedy)
                    .setDynamic(dynamic);
            component.add(cd);
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            event.addBean(new SimpleBean<>(clazz, Dependent.class, injectionPoint, this::createConfig));
        }

        protected Object createConfig() {
            Dictionary<String, Object> cfg = cd.getService();
            return Configurable.create(clazz, cfg != null ? cfg : new Hashtable<>());
        }
    }

}
