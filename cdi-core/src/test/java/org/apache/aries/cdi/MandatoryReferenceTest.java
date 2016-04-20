package org.apache.aries.cdi;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

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

    @Immediate
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
