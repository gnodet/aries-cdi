package org.apache.aries.cdi.impl.osgi;

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.BundleContext;

public class ComponentRegistry {

    private final BeanManager beanManager;
    private final BundleContext bundleContext;
    private final Map<Bean<?>, ComponentDescriptor<?>> descriptors = new HashMap<>();

    public ComponentRegistry(BeanManager beanManager, BundleContext bundleContext) {
        this.beanManager = beanManager;
        this.bundleContext = bundleContext;
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

    public void start() {
        descriptors.values().forEach(ComponentDescriptor::start);
    }

    public <S> void activate(ComponentDescriptor<S> descriptor) {
        if (descriptor.isImmediate()) {
            Context context = beanManager.getContext(OsgiScope.class);
            CreationalContext<S> creationalContext = beanManager.createCreationalContext(descriptor.getBean());
            context.get(descriptor.getBean(), creationalContext);
        }
    }

    public <S> void deactivate(ComponentDescriptor<S> descriptor) {
        AlterableContext context = (AlterableContext) beanManager.getContext(OsgiScope.class);
        context.destroy(descriptor.getBean());
    }
}
