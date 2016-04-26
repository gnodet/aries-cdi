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
package org.apache.aries.cdi.impl.osgi.support;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Set;

public class DelegatingInjectionPoint implements InjectionPoint {

    protected final InjectionPoint delegate;

    public DelegatingInjectionPoint(InjectionPoint delegate) {
        this.delegate = delegate;
    }

    @Override
    public Type getType() {
        return delegate.getType();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return delegate.getQualifiers();
    }

    @Override
    public Bean<?> getBean() {
        return delegate.getBean();
    }

    @Override
    public Member getMember() {
        return delegate.getMember();
    }

    @Override
    public Annotated getAnnotated() {
        return delegate.getAnnotated();
    }

    @Override
    public boolean isDelegate() {
        return delegate.isDelegate();
    }

    @Override
    public boolean isTransient() {
        return delegate.isTransient();
    }
}
