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
package org.apache.aries.cdi;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.aries.cdi.api.Component;
import org.apache.aries.cdi.api.Immediate;
import org.apache.aries.cdi.api.Reference;
import org.apache.aries.cdi.impl.osgi.OsgiExtension;
import org.jboss.weld.environment.se.Weld;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;

public class MandatoryReferenceTest extends AbstractTest {

    @Test
    public void test() throws Exception {
        weld = new Weld()
                .disableDiscovery()
                .beanClasses(Hello.class)
                .extensions(new OsgiExtension())
                .initialize();
        BeanManager manager = weld.getBeanManager();

        Assert.assertEquals(0, Hello.created.get());
        Assert.assertEquals(0, Hello.destroyed.get());

        ServiceRegistration registration = framework.getBundleContext()
                .registerService(Service.class, () -> "Hello world !!", null);

        Assert.assertEquals(1, Hello.created.get());
        Assert.assertEquals(0, Hello.destroyed.get());

        registration.unregister();

        Assert.assertEquals(1, Hello.created.get());
        Assert.assertEquals(1, Hello.destroyed.get());
    }

    public interface Service {

        String hello();

    }

    @Immediate @Component
    public static class Hello {

        static final AtomicInteger created = new AtomicInteger();
        static final AtomicInteger destroyed = new AtomicInteger();

        @Inject
        @Reference
        Service service;

        @PostConstruct
        public void init() {
            created.incrementAndGet();
            System.err.println("Creating Hello instance");
        }

        @PreDestroy
        public void destroy() {
            destroyed.incrementAndGet();
            System.err.println("Destroying Hello instance");
        }

        public String sayHelloWorld() {
            return service.hello();
        }
    }

}
