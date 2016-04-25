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
import javax.enterprise.context.spi.AlterableContext;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
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
import org.apache.aries.cdi.impl.osgi.support.Configurable;
import org.apache.aries.cdi.impl.osgi.support.Filters;
import org.apache.aries.cdi.impl.osgi.support.IterableInstance;
import org.apache.aries.cdi.impl.osgi.support.SimpleBean;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;
import org.apache.felix.scr.impl.metadata.DSVersion;
import org.apache.felix.scr.impl.metadata.ReferenceMetadata;
import org.apache.felix.scr.impl.metadata.ServiceMetadata;
import org.osgi.service.component.ComponentContext;

public class ComponentDescriptor {

    private final Bean<Object> bean;
    private final ComponentRegistry registry;
    private final Map<InjectionPoint, Dependency> dependencies = new HashMap<>();

    private final ThreadLocal<ComponentContext> context = new ThreadLocal<>();

    private ComponentMetadata metadata = new ComponentMetadata(DSVersion.DS13) {

        private boolean m_immediate;

        @Override
        public void setImmediate(boolean immediate) {
            m_immediate = immediate;
        }

        @Override
        public boolean isImmediate() {
            return m_immediate;
        }

    };

    private List<Bean<?>> producers = new ArrayList<>();

    public ComponentDescriptor(Bean<Object> bean, ComponentRegistry registry) {
        this.bean = bean;
        this.registry = registry;

        boolean immediate = false;
        boolean hasService = false;
        Set<String> names = new HashSet<>();
        for (Annotation annotation : bean.getQualifiers()) {
            if (annotation instanceof Immediate) {
                immediate = true;
            } else if (annotation instanceof Service) {
                hasService = true;
            } else if (annotation instanceof Contract) {
                names.add(((Contract) annotation).value().getName());
            } else if (annotation instanceof Contracts) {
                for (Contract ctr : ((Contracts) annotation).value()) {
                    names.add(ctr.value().getName());
                }
            } else if (annotation instanceof Properties) {
                for (Property prop : ((Properties) annotation).value()) {
                    metadata.getProperties().put(prop.name(), prop.value());
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
                    metadata.getProperties().put(name, value);
                }
            }
        }

        ServiceMetadata serviceMetadata = null;
        if (hasService) {
            serviceMetadata = new ServiceMetadata();
            if (names.isEmpty()) {
                for (Class cl : bean.getBeanClass().getInterfaces()) {
                    names.add(cl.getName());
                }
            }
            if (names.isEmpty()) {
                names.add(bean.getBeanClass().getName());
            }
            for (String name : names) {
                serviceMetadata.addProvide(name);
            }
        }

        String name = bean.getName();
        if (name == null) {
            name = bean.getBeanClass().getName();
        }
        metadata.setName(name);
        metadata.setImmediate(immediate);
        metadata.setImplementationClassName(Object.class.getName());
        metadata.setConfigurationPolicy(ComponentMetadata.CONFIGURATION_POLICY_IGNORE);
        metadata.getProperties().put(ComponentDescriptor.class.getName(), this);
        metadata.getProperties().put(ComponentRegistry.class.getName(), registry);
        metadata.setService(serviceMetadata);
    }

    public ComponentMetadata getMetadata() {
        return metadata;
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
        producers.forEach(event::addBean);
        dependencies.values().forEach(s -> s.preStart(event));
    }

    public Object activate(ComponentContext cc) {
        this.context.set(cc);
        try {
            BeanManager beanManager = registry.getBeanManager();
            Context context = beanManager.getContext(Component.class);
            return context.get(bean, beanManager.createCreationalContext(bean));
        } finally {
            this.context.set(null);
        }
    }

    public void deactivate(ComponentContext cc) {
        this.context.set(cc);
        try {
            BeanManager beanManager = registry.getBeanManager();
            AlterableContext context = (AlterableContext) beanManager.getContext(Component.class);
            context.destroy(bean);
        } finally {
            this.context.set(null);
        }
    }

    @Override
    public String toString() {
        return "Component[" + "bean=" + bean + ']';
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

        void preStart(AfterBeanDiscovery event) {
        }

    }

    public class ReferenceDependency extends Dependency {

        public ReferenceDependency(InjectionPoint injectionPoint) {
            super(injectionPoint);

            String filter = Filters.getFilter(injectionPoint.getAnnotated().getAnnotations());

            boolean optional = injectionPoint.getAnnotated().isAnnotationPresent(Optional.class);
            boolean greedy = injectionPoint.getAnnotated().isAnnotationPresent(Greedy.class);
            boolean dynamic = injectionPoint.getAnnotated().isAnnotationPresent(Dynamic.class);
            boolean multiple = isInstance;

            ReferenceMetadata reference = new ReferenceMetadata();
            reference.setName(injectionPoint.toString());
            reference.setInterface(clazz.getName());
            reference.setTarget(filter);
            reference.setCardinality(optional ? multiple ? "0..n" : "0..1" : multiple ? "1..n" : "1..1");
            reference.setPolicy(dynamic ? "dynamic" : "static");
            reference.setPolicyOption(greedy ? "greedy" : "reluctant");
            metadata.addDependency(reference);

            producers.add(new SimpleBean<>(clazz, Dependent.class, injectionPoint, this::getService));
        }

        protected Object getService() {
            ComponentContext cc = context.get();
            if (isInstance) {
                Iterable<Object> iterable = () -> new Iterator<Object>() {
                    final Object[] services = cc.locateServices(injectionPoint.toString());
                    int idx;
                    @Override
                    public boolean hasNext() {
                        return services != null && idx < services.length;
                    }

                    @Override
                    public Object next() {
                        return services[idx++];
                    }
                };
                return new IterableInstance<>(iterable);
            } else {
                return cc.locateService(injectionPoint.toString());
            }
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
            ComponentDescriptor resolved = registry.resolve(injectionPoint.getType(), qualifiers);

//            ComponentDependencyImpl cd = new ComponentDependencyImpl()
//                    .setComponent(resolved.component);
//            component.add(cd);
        }

    }

    public class ConfigDependency extends Dependency {

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

            metadata.setConfigurationPolicy(optional ? ComponentMetadata.CONFIGURATION_POLICY_OPTIONAL : ComponentMetadata.CONFIGURATION_POLICY_REQUIRE);
            metadata.setConfigurationPid(new String[]{ pid });

            producers.add(new SimpleBean<>(clazz, Dependent.class, injectionPoint, this::createConfig));
        }

        @SuppressWarnings("unchecked")
        protected Object createConfig() {
            ComponentContext cc = context.get();
            Map<String, Object> cfg = (Map) cc.getProperties();
            return Configurable.create(clazz, cfg != null ? cfg : new Hashtable<>());
        }
    }

}
