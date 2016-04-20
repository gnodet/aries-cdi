package org.apache.aries.cdi.impl.osgi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessBeanAttributes;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import org.apache.aries.cdi.api.Immediate;
import org.apache.aries.cdi.api.Reference;

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
        event.addScope(OsgiScope.class, false, false);
    }

    public <T> void processBean(@Observes ProcessBean<T> event) {
        Bean<T> bean = event.getBean();
        boolean hasReferences = bean.getInjectionPoints().stream()
                .filter(ip -> ip.getAnnotated().getAnnotation(Reference.class) != null)
                .findAny().isPresent();
        if (hasReferences) {
            ComponentDescriptor<T> descriptor = componentRegistry.addComponent(bean);
            for (InjectionPoint ip : bean.getInjectionPoints()) {
                Reference annotation = ip.getAnnotated().getAnnotation(Reference.class);
                if (annotation != null) {
                    descriptor.addDependency(ip);
                }
            }
        }
    }

    public <T> void processBeanAttributes(@Observes ProcessBeanAttributes<T> event) {
        final BeanAttributes<T> attributes = event.getBeanAttributes();
        if (!attributes.getScope().equals(Dependent.class)) {
            return;
        }
        BeanAttributes<T> wrappedAttributes = new BeanAttributes<T>() {
            public Set<Type> getTypes() {
                return attributes.getTypes();
            }
            public Set<Annotation> getQualifiers() {
                return attributes.getQualifiers();
            }
            public String getName() {
                return attributes.getName();
            }
            public Set<Class<? extends Annotation>> getStereotypes() {
                return attributes.getStereotypes();
            }
            public boolean isAlternative() {
                return attributes.isAlternative();
            }
            public Class<? extends Annotation> getScope() {
                return OsgiScope.class;
            }
        };
        event.setBeanAttributes(wrappedAttributes);
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        componentRegistry.getComponents()
                .stream()
                .flatMap(b -> componentRegistry.getDescriptor(b).getDependencies().stream())
                .forEach(event::addBean);
        event.addContext(new OsgiScopeContext());

        componentRegistry.start();
    }

}
