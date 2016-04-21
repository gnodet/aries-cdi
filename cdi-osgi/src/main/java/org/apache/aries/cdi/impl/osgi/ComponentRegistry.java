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

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.aries.cdi.api.Component;
import org.apache.aries.cdi.impl.dm.DependencyManager;
import org.osgi.framework.BundleContext;

public class ComponentRegistry {

    private final BeanManager beanManager;
    private final BundleContext bundleContext;
    private final Map<Bean<?>, ComponentDescriptor<?>> descriptors = new HashMap<>();

    private final DependencyManager dm;

    public ComponentRegistry(BeanManager beanManager, BundleContext bundleContext) {
        this.beanManager = beanManager;
        this.bundleContext = bundleContext;
        this.dm = new DependencyManager(bundleContext);
    }

    public DependencyManager getDm() {
        return dm;
    }

    public void start(AfterBeanDiscovery event) {
        descriptors.values().forEach(d -> d.preStart(event));
        descriptors.values().forEach(ComponentDescriptor::start);
    }

    public <S> ComponentDescriptor<S> addComponent(Bean<S> component) {
        ComponentDescriptor<S> descriptor = new ComponentDescriptor<S>(component, this);
        descriptors.put(component, descriptor);
        return descriptor;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public Set<Bean<?>> getComponents() {
        return descriptors.keySet();
    }

    public ComponentDescriptor<?> getDescriptor(Bean<?> component) {
        return descriptors.get(component);
    }

    public <S> void activate(ComponentDescriptor<S> descriptor) {
        if (descriptor.isImmediate()) {
            Context context = beanManager.getContext(Component.class);
            CreationalContext<S> creationalContext = beanManager.createCreationalContext(descriptor.getBean());
            context.get(descriptor.getBean(), creationalContext);
        }
    }

    public <S> void deactivate(ComponentDescriptor<S> descriptor) {
        AlterableContext context = (AlterableContext) beanManager.getContext(Component.class);
        context.destroy(descriptor.getBean());
    }

    public ComponentDescriptor<?> resolve(Type type, Annotation[] qualifiers) {
        Bean<?> resolved = beanManager.resolve(beanManager.getBeans(type, qualifiers));
        ComponentDescriptor<?> desc =  descriptors.get(resolved);
        if (desc == null) {
            throw new IllegalStateException("Unable to find component descriptor for " + resolved);
        }
        return desc;
    }

}
