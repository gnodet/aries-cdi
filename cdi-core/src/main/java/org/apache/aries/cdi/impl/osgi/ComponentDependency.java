package org.apache.aries.cdi.impl.osgi;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.aries.cdi.api.Optional;
import org.apache.aries.cdi.api.Reference;
import org.apache.karaf.util.tracker.SingleServiceTracker;
import org.apache.karaf.util.tracker.SingleServiceTracker.SingleServiceListener;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;

public class ComponentDependency<S, T> implements SingleServiceListener, Bean<T> {

    private final ComponentDescriptor<S> descriptor;
    private final InjectionPoint injectionPoint;
    private final SingleServiceTracker<T> tracker;
    private final boolean optional;
    private final Type type;
    private final Class<T> clazz;
    private final Reference qualifier;

    public ComponentDependency(ComponentDescriptor<S> descriptor, InjectionPoint injectionPoint) {
        this.descriptor = descriptor;
        this.injectionPoint = injectionPoint;
        this.qualifier = injectionPoint.getAnnotated().getAnnotation(Reference.class);
        this.optional = injectionPoint.getAnnotated().getAnnotation(Optional.class) != null;
        this.type = injectionPoint.getType();
        if (type instanceof ParameterizedType) {
            clazz = (Class) ((ParameterizedType) type).getRawType();
        } else {
            clazz = (Class) type;
        }
        BundleContext bundleContext = descriptor.getRegistry().getBundleContext();
        try {
            this.tracker = new SingleServiceTracker<>(bundleContext, clazz, this);
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException("Unable to track OSGi dependency", e);
        }
    }

    public boolean isOptional() {
        return optional;
    }

    public void start() {
        tracker.open();
    }

    public void stop() {
        tracker.close();
    }

    @Override
    public void serviceFound() {
        descriptor.satisfaction(this, true);
    }

    @Override
    public void serviceLost() {
        descriptor.satisfaction(this, false);
    }

    @Override
    public void serviceReplaced() {
        serviceLost();
        serviceFound();
    }

    @Override
    public Class<?> getBeanClass() {
        return clazz;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isNullable() {
        return optional;
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> s = new HashSet<>();
        s.add(type);
        s.add(Object.class);
        return s;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> s = new HashSet<>();
        s.add(qualifier);
        return s;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return OsgiScope.class;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        return tracker.getService();
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
    }
}
