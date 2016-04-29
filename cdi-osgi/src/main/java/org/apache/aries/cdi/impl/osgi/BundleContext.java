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
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import java.lang.annotation.Annotation;
import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.aries.cdi.api.Bundle;

public class BundleContext implements AlterableContext {

    private final Map<Object, CreationalContext<?>> instanceMap = new IdentityHashMap<>();

    private final ComponentRegistry registry;

    public BundleContext(ComponentRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return Bundle.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        T instance = contextual.create(creationalContext);
        instanceMap.put(instance, creationalContext);
        return instance;
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        return null;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void destroy(Contextual<?> contextual) {
        ComponentDescriptor desc = registry.getDescriptor((Bean) contextual);
        Object instance = desc.getComponentContext().getComponentInstance().getInstance();
        CreationalContext<Object> cc = (CreationalContext<Object>) instanceMap.get(instance);
        if (cc != null) {
            ((Contextual<Object>) contextual).destroy(instance, cc);
        }
    }

}
