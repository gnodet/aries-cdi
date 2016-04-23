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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.aries.cdi.api.Component;
import org.apache.aries.cdi.api.Config;
import org.apache.aries.cdi.api.Service;
import org.apache.aries.cdi.api.event.ReferenceEvent;
import org.apache.aries.cdi.impl.osgi.support.BundleContextHolder;
import org.apache.aries.cdi.impl.osgi.support.DelegatingInjectionTarget;
import org.apache.aries.cdi.impl.osgi.support.Filters;
import org.apache.aries.cdi.impl.osgi.support.Types;
import org.osgi.framework.Constants;

@ApplicationScoped
public class OsgiExtension implements Extension {

    private ComponentRegistry componentRegistry;

    public OsgiExtension() {
    }

    public ComponentRegistry getComponentRegistry() {
        return componentRegistry;
    }

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event, BeanManager manager) {
        componentRegistry = new ComponentRegistry(manager, BundleContextHolder.getBundleContext());
        event.addAnnotatedType(manager.createAnnotatedType(EventBridge.class));
        event.addAnnotatedType(manager.createAnnotatedType(BundleContextProducer.class));
        event.addScope(Component.class, false, false);
    }

    public <T> void processBean(@Observes ProcessBean<T> event) {
        @SuppressWarnings("unchecked")
        Bean<Object> bean = (Bean) event.getBean();
        ComponentDescriptor descriptor = null;
        for (InjectionPoint ip : event.getBean().getInjectionPoints()) {
            Service ref = ip.getAnnotated().getAnnotation(Service.class);
            Component cmp = ip.getAnnotated().getAnnotation(Component.class);
            Config    cfg = ip.getAnnotated().getAnnotation(Config.class);
            if (ref != null || cmp != null || cfg != null) {
                if (bean.getScope() != Component.class) {
                    throw new IllegalArgumentException("Beans with @Reference, @Component or @Config injection points should be annotated with @Component");
                }
                if (descriptor == null) {
                    descriptor = componentRegistry.addComponent(bean);
                }
                if ((ref != null ? 1 : 0) + (cmp != null ? 1 : 0) + (cfg != null ? 1 : 0) > 1) {
                    throw new IllegalArgumentException("Only one of @Reference, @Component or @Config can be set on injection point");
                }
                if (ref != null) {
                    descriptor.addReference(ip);
                }
                if (cmp != null) {
                    descriptor.addDependency(ip);
                }
                if (cfg != null) {
                    descriptor.addConfig(ip);
                }
            }
        }
        if (descriptor == null && event.getAnnotated().isAnnotationPresent(Service.class)) {
            descriptor = componentRegistry.addComponent(bean);
        }
    }

    public <T> void processInjectionTarget(@Observes ProcessInjectionTarget<T> event) {
        for (InjectionPoint ip : event.getInjectionTarget().getInjectionPoints()) {
            Annotated annotated = ip.getAnnotated();
            if (annotated.isAnnotationPresent(Service.class)
                    || annotated.isAnnotationPresent(Component.class)
                    || annotated.isAnnotationPresent(Config.class)) {
                event.setInjectionTarget(new DelegatingInjectionTarget<T>(event.getInjectionTarget()) {
                    @Override
                    public void inject(T instance, CreationalContext<T> ctx) {
                        super.inject(instance, ctx);
                        for (InjectionPoint injectionPoint : delegate.getInjectionPoints()) {
                            ComponentDescriptor descriptor = componentRegistry.getDescriptor(injectionPoint.getBean());
                            descriptor.inject(instance, injectionPoint);
                        }
                    }
                });
                return;
            }
        }
    }


    private final Set<String> observedFilters = new HashSet<>();
    private final Set<Annotation> observedQualifiers = new HashSet<>();

    public Set<String> getObservedFilters() {
        return observedFilters;
    }

    public Set<Annotation> getObservedQualifiers() {
        return observedQualifiers;
    }

    public <T, X> void processObserverMethod(@Observes ProcessObserverMethod<T, X> event) {
        Set<Annotation> qualifiers = event.getObserverMethod().getObservedQualifiers();
        if (qualifiers.contains(EventBridge.ADDED_ANNOTATION_LITERAL)
                || qualifiers.contains(EventBridge.REMOVED_ANNOTATION_LITERAL)) {
            List<String> filters = Filters.getSubFilters(qualifiers);
            Type observed = event.getObserverMethod().getObservedType();
            Class service = Types.getRawType(observed);
            if (service == ReferenceEvent.class) {
                service = Types.getRawType(((ParameterizedType) observed).getActualTypeArguments()[0]);
            }
            if (service != Object.class) {
                String subfilter = "(" + Constants.OBJECTCLASS + "=" + service.getName() + ")";
                filters.add(0, subfilter);
            }
            String filter = Filters.and(filters);
            observedFilters.add(filter);
            observedQualifiers.addAll(qualifiers);
        }
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        event.addContext(new ComponentContext());
        componentRegistry.preStart(event);
    }

    public void applicationScopeInitialized(@Observes @Initialized(ApplicationScoped.class) Object init) {
        componentRegistry.start();
    }

}
