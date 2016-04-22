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

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;

import org.apache.aries.cdi.api.Config;
import org.apache.aries.cdi.api.Dynamic;
import org.apache.aries.cdi.api.Greedy;
import org.apache.aries.cdi.api.Immediate;
import org.apache.aries.cdi.api.Optional;
import org.apache.aries.cdi.impl.dm.AbstractDependency;
import org.apache.aries.cdi.impl.dm.ComponentImpl;
import org.apache.aries.cdi.impl.dm.ComponentState;
import org.apache.aries.cdi.impl.dm.Configurable;
import org.apache.aries.cdi.impl.dm.ConfigurationDependencyImpl;
import org.apache.aries.cdi.impl.dm.Event;
import org.apache.aries.cdi.impl.dm.ServiceDependencyImpl;
import org.apache.aries.cdi.impl.dm.ServiceEventImpl;
import org.osgi.framework.ServiceReference;

public class ComponentDescriptor<C> {

    private final Bean<C> bean;
    private final ComponentRegistry registry;
    private final ComponentImpl component;
    private final List<Dependency> dependencies = new ArrayList<>();

    public ComponentDescriptor(Bean<C> bean, ComponentRegistry registry) {
        this.bean = bean;
        this.registry = registry;
        this.component = new ComponentImpl(registry.getBundleContext(), registry.getDm()) {
            protected <D extends AbstractDependency<D, S, E>, S, E extends Event<S>>
            void updateInstance(D dc, E event, boolean update, boolean add) {
                registry.deactivate(ComponentDescriptor.this);
                registry.activate(ComponentDescriptor.this);
                super.updateInstance(dc, event, update, add);
            }
        };
    }

    public Bean<C> getBean() {
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
        dependencies.add(new ReferenceDependency(ip));
    }

    public void addDependency(InjectionPoint ip) {
        dependencies.add(new ComponentDependency(ip));
    }

    public void addConfig(InjectionPoint ip) {
        dependencies.add(new ConfigDependency(ip));
    }

    public void preStart(AfterBeanDiscovery event) {
        dependencies.forEach(s -> s.preStart(event));
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

    public <T> void inject(T instance, CreationalContext<T> ctx, InjectionPoint injectionPoint) {
        for (Dependency dependency : dependencies) {
            if (dependency.getClass() == ReferenceDependency.class) {
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
    }

    public interface Dependency {

        void preStart(AfterBeanDiscovery event);

    }
    public class ReferenceDependency implements Dependency {

        protected final InjectionPoint injectionPoint;
        protected final Class<?> clazz;
        protected final ServiceDependencyImpl<Object> sd;
        protected final boolean isInstance;

        public ReferenceDependency(InjectionPoint injectionPoint) {
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
            boolean optional = injectionPoint.getAnnotated().isAnnotationPresent(Optional.class);
            boolean greedy = injectionPoint.getAnnotated().isAnnotationPresent(Greedy.class);
            boolean dynamic = injectionPoint.getAnnotated().isAnnotationPresent(Dynamic.class);
            boolean multiple = isInstance;
            sd = getRegistry().getDm().createServiceDependency()
                    .setRequired(!optional)
                    .setGreedy(greedy)
                    .setDynamic(dynamic)
                    .setMultiple(multiple)
                    .setService(clazz);
            component.add(sd);
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            event.addBean(new SimpleBean<>(clazz, injectionPoint, this::getService));
        }

        protected Object getService() {
            if (isInstance) {
                return new ReferenceInstance<Object>() {
                    @Override
                    protected Collection<ServiceReference<Object>> getServiceReferences() {
                        SortedSet<? extends ServiceEventImpl<Object>> col = component.getDependencyEvents(sd);
                        return new AbstractCollection<ServiceReference<Object>>() {
                            @Override
                            public Iterator<ServiceReference<Object>> iterator() {
                                Iterator<? extends ServiceEventImpl<Object>> it = col.iterator();
                                return new Iterator<ServiceReference<Object>>() {
                                    @Override
                                    public boolean hasNext() {
                                        return it.hasNext();
                                    }
                                    @Override
                                    public ServiceReference<Object> next() {
                                        return it.next().getReference();
                                    }
                                };
                            }
                            @Override
                            public int size() {
                                return col.size();
                            }
                        };
                    }
                };
            }
            return sd.getService();
        }
    }

    public class ComponentDependency implements Dependency {

        protected final InjectionPoint injectionPoint;

        public ComponentDependency(InjectionPoint injectionPoint) {
            this.injectionPoint = injectionPoint;
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            Set<Annotation> qualifiersSet = injectionPoint.getQualifiers();
            Annotation[] qualifiers = qualifiersSet.toArray(new Annotation[qualifiersSet.size()]);
            ComponentDescriptor resolved = getRegistry().resolve(injectionPoint.getType(), qualifiers);
            component.add(getRegistry().getDm().createComponentDependency().setComponent(resolved.component));
        }

    }

    public class ConfigDependency implements Dependency {

        protected final InjectionPoint injectionPoint;
        protected final Class<?> clazz;
        protected final ConfigurationDependencyImpl cd;

        public ConfigDependency(InjectionPoint injectionPoint) {
            this.injectionPoint = injectionPoint;
            Type type = injectionPoint.getType();
            if (type instanceof ParameterizedType) {
                clazz = (Class) ((ParameterizedType) type).getRawType();
            } else {
                clazz = (Class) type;
            }
            if (!clazz.isAnnotation()) {
                throw new IllegalArgumentException("Configuration class should be an annotation: " + clazz.getName());
            }

            Config config = injectionPoint.getAnnotated().getAnnotation(Config.class);
            String pid = config.pid().isEmpty() ? clazz.getName() : config.pid();
            boolean optional = injectionPoint.getAnnotated().isAnnotationPresent(Optional.class);
            boolean dynamic = injectionPoint.getAnnotated().isAnnotationPresent(Dynamic.class);

            cd = getRegistry().getDm().createConfigurationDependency()
                    .setPid(pid)
                    .setRequired(!optional)
                    .setDynamic(dynamic);
            component.add(cd);
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            event.addBean(new SimpleBean<>(clazz, injectionPoint, this::createConfig));
        }

        @SuppressWarnings("unchecked")
        protected Object createConfig() {
            return Configurable.create(clazz, cd.getService());
        }
    }

    static class SimpleBean<T> implements Bean<T> {
        private final Class clazz;
        private final InjectionPoint injectionPoint;
        private final Supplier<T> supplier;

        public SimpleBean(Class clazz, InjectionPoint injectionPoint, Supplier<T> supplier) {
            this.clazz = clazz;
            this.injectionPoint = injectionPoint;
            this.supplier = supplier;
        }

        @Override
        public Class<?> getBeanClass() {
            return clazz;
        }
        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }
        @Override
        public boolean isNullable() {
            return false;
        }
        @Override
        public Set<Type> getTypes() {
            return Collections.singleton(injectionPoint.getType());
        }
        @Override
        public Set<Annotation> getQualifiers() {
            return new HashSet<>(injectionPoint.getQualifiers());
        }
        @Override
        public Class<? extends Annotation> getScope() {
            return org.apache.aries.cdi.api.Component.class;
        }
        @Override
        public String getName() {
            return null;
        }
        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }
        @Override
        public boolean isAlternative() {
            return false;
        }
        @Override
        public T create(CreationalContext<T> creationalContext) {
            return supplier.get();
        }
        @Override
        public void destroy(T instance, CreationalContext<T> creationalContext) {
        }
    }

    abstract class ReferenceInstance<T> implements Instance<T> {

        @Override
        public Instance<T> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return getServiceReferences().isEmpty();
        }

        @Override
        public boolean isAmbiguous() {
            return getServiceReferences().size() > 0;
        }

        @Override
        public void destroy(T instance) {

        }

        @Override
        public Iterator<T> iterator() {
            Iterator<ServiceReference<T>> iterator = getServiceReferences().iterator();
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public T next() {
                    ServiceReference<T> ref = iterator.next();
                    return getRegistry().getBundleContext().getService(ref);
                }
            };
        }

        @Override
        public T get() {
            if (isUnsatisfied()) {
                throw new UnsatisfiedResolutionException();
            }
            if (isAmbiguous()) {
                throw new AmbiguousResolutionException();
            }
            return iterator().next();
        }

        protected abstract Collection<ServiceReference<T>> getServiceReferences();
    }

}
