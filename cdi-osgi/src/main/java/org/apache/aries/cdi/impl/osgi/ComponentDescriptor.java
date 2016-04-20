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
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.aries.cdi.api.Component;
import org.apache.aries.cdi.api.Immediate;
import org.apache.karaf.util.tracker.SingleServiceTracker;
import org.apache.karaf.util.tracker.SingleServiceTracker.SingleServiceListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;

public class ComponentDescriptor<S> extends Satisfiable {

    private final Bean<S> bean;
    private final ComponentRegistry registry;

    public ComponentDescriptor(Bean<S> bean, ComponentRegistry registry) {
        this.bean = bean;
        this.registry = registry;
        satisfied(true);
    }

    public Bean<S> getBean() {
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
        addSatisfiable(new ReferenceDependency(ip));
    }

    public void addDependency(InjectionPoint ip) {
        addSatisfiable(new ComponentDependency(ip));
    }

    public void preStart(AfterBeanDiscovery event) {
        super.preStart(event);
    }

    @Override
    public void start() {
        super.start();
        if (satisfied()) {
            registry.activate(this);
        }
    }

    @Override
    public void accept(Satisfiable satisfiable) {
        super.accept(satisfiable);
        if (satisfied()) {
            registry.activate(this);
        } else {
            registry.deactivate(this);
        }
    }

    @Override
    public String toString() {
        return "Component[" +
                "bean=" + bean +
                ", satisfied=" + satisfied() +
                ']';
    }

    public class ReferenceDependency extends Satisfiable {

        protected final InjectionPoint injectionPoint;
        protected final SingleServiceTracker<?> tracker;
        protected final Class<?> clazz;

        public ReferenceDependency(InjectionPoint injectionPoint) {
            this.injectionPoint = injectionPoint;
            BundleContext bundleContext = getRegistry().getBundleContext();
            Type type = injectionPoint.getType();
            if (type instanceof ParameterizedType) {
                clazz = (Class) ((ParameterizedType) type).getRawType();
            } else {
                clazz = (Class) type;
            }
            try {
                this.tracker = new SingleServiceTracker<>(bundleContext, clazz, new SingleServiceListener() {
                    @Override
                    public void serviceFound() {
                        satisfied(true);
                    }
                    @Override
                    public void serviceLost() {
                        satisfied(false);
                    }
                    @Override
                    public void serviceReplaced() {
                        serviceLost();
                        serviceFound();
                    }
                });
            } catch (InvalidSyntaxException e) {
                throw new RuntimeException("Unable to track OSGi dependency", e);
            }
        }

        @Override
        public void addSatisfiable(Satisfiable satisfiable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            event.addBean(asBean());
        }

        @Override
        public void start() {
            tracker.open();
        }

        public Bean<?> asBean() {
            return new Bean<Object>() {
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
                    return Component.class;
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
                public Object create(CreationalContext<Object> creationalContext) {
                    return tracker.getService();
                }
                @Override
                public void destroy(Object instance, CreationalContext<Object> creationalContext) {
                }
            };
        }
    }

    public class ComponentDependency extends Satisfiable {

        protected final InjectionPoint injectionPoint;

        public ComponentDependency(InjectionPoint injectionPoint) {
            this.injectionPoint = injectionPoint;
            satisfied(true);
        }

        @Override
        public void preStart(AfterBeanDiscovery event) {
            super.preStart(event);
            Set<Annotation> qualifiersSet = injectionPoint.getQualifiers();
            Annotation[] qualifiers = qualifiersSet.toArray(new Annotation[qualifiersSet.size()]);
            Satisfiable resolved = getRegistry().resolve(injectionPoint.getType(), qualifiers);
            addSatisfiable(resolved);
        }

    }
}
