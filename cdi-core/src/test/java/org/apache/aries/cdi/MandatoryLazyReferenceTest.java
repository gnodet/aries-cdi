package org.apache.aries.cdi;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.aries.cdi.api.Immediate;
import org.apache.aries.cdi.api.Reference;
import org.apache.aries.cdi.impl.osgi.OsgiExtension;
import org.jboss.weld.environment.se.Weld;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;

public class MandatoryLazyReferenceTest extends AbstractTest {

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

        Assert.assertEquals(0, Hello.created.get());
        Assert.assertEquals(0, Hello.destroyed.get());
        Assert.assertNull(Hello.instance.get());

        // TODO: how to get the bean correctly ?
        Hello hello = weld.select(Hello.class, AnyLiteral.INSTANCE).get();

        Assert.assertEquals(1, Hello.created.get());
        Assert.assertEquals(0, Hello.destroyed.get());
        Assert.assertNotNull(Hello.instance.get());

        registration.unregister();

        Assert.assertEquals(1, Hello.created.get());
        Assert.assertEquals(1, Hello.destroyed.get());
        Assert.assertNull(Hello.instance.get());
    }

    static class AnyLiteral extends AnnotationLiteral<Any> implements Any {

        public static AnyLiteral INSTANCE = new AnyLiteral();

    }

    public interface Service {

        String hello();

    }

    public static class Hello {

        static final AtomicInteger created = new AtomicInteger();
        static final AtomicInteger destroyed = new AtomicInteger();
        static final AtomicReference<Hello> instance = new AtomicReference<>();

        @Inject
        @Reference
        Service service;

        @PostConstruct
        public void init() {
            created.incrementAndGet();
            instance.compareAndSet(null, this);
            System.err.println("Creating Hello instance");
        }

        @PreDestroy
        public void destroy() {
            destroyed.incrementAndGet();
            instance.compareAndSet(this, null);
            System.err.println("Destroying Hello instance");
        }

        public String sayHelloWorld() {
            return service.hello();
        }
    }

}
