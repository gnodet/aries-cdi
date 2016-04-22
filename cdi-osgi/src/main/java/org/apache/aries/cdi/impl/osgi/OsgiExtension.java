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

import org.apache.aries.cdi.api.Component;
import org.apache.aries.cdi.api.Config;
import org.apache.aries.cdi.api.Reference;
import org.apache.aries.cdi.impl.osgi.support.DelegatingInjectionTarget;

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
        event.addScope(Component.class, false, false);
    }

    public <T> void processBean(@Observes ProcessBean<T> event) {
        Bean<T> bean = event.getBean();
        ComponentDescriptor<T> descriptor = null;
        for (InjectionPoint ip : event.getBean().getInjectionPoints()) {
            Reference ref = ip.getAnnotated().getAnnotation(Reference.class);
            Component cmp = ip.getAnnotated().getAnnotation(Component.class);
            Config    cfg = ip.getAnnotated().getAnnotation(Config.class);
            if (ref != null || cmp != null || cfg != null) {
                if (bean.getScope() != Component.class) {
                    throw new IllegalArgumentException("Beans with @Reference injection points should be annotated with @Component");
                }
                if (descriptor == null) {
                    descriptor = componentRegistry.addComponent(bean);
                }
                if (ref != null && cmp != null) {
                    throw new IllegalArgumentException("Can not use both @Reference and @Component on injection point");
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
    }

    public <T> void processInjectionTarget(@Observes ProcessInjectionTarget<T> event) {
        for (InjectionPoint ip : event.getInjectionTarget().getInjectionPoints()) {
            Annotated annotated = ip.getAnnotated();
            if (annotated.isAnnotationPresent(Reference.class)
                    || annotated.isAnnotationPresent(Component.class)
                    || annotated.isAnnotationPresent(Config.class)) {
                event.setInjectionTarget(new DelegatingInjectionTarget<T>(event.getInjectionTarget()) {
                    @Override
                    public void inject(T instance, CreationalContext<T> ctx) {
                        super.inject(instance, ctx);
                        for (InjectionPoint injectionPoint : delegate.getInjectionPoints()) {
                            ComponentDescriptor descriptor = componentRegistry.getDescriptor(injectionPoint.getBean());
                            descriptor.inject(instance, ctx, injectionPoint);
                        }
                    }
                });
                return;
            }
        }
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        event.addContext(new ComponentContext());
        componentRegistry.start(event);
    }

}
