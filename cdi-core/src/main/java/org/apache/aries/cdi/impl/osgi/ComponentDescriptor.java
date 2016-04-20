package org.apache.aries.cdi.impl.osgi;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.aries.cdi.api.Immediate;

public class ComponentDescriptor<S> {

    private final Map<ComponentDependency<S, ?>, Boolean> dependencies = new HashMap<>();
    private final Bean<S> bean;
    private final ComponentRegistry registry;

    public ComponentDescriptor(Bean<S> bean, ComponentRegistry registry) {
        this.bean = bean;
        this.registry = registry;
    }

    public Bean<S> getBean() {
        return bean;
    }

    public ComponentRegistry getRegistry() {
        return registry;
    }

    public boolean isImmediate() {
        for (Annotation anno : bean.getQualifiers()) {
            if (anno instanceof Immediate) {
                return true;
            }
        }
        return false;
    }

    public <T> void addDependency(InjectionPoint ip) {
        ComponentDependency<S, T> componentDependency = new ComponentDependency<>(this, ip);
        dependencies.put(componentDependency, false);
    }

    public void start() {
        dependencies.keySet().forEach(ComponentDependency::start);
        if (satisfied()) {
            registry.activate(this);
        }
    }

    public void stop() {
        dependencies.keySet().forEach(ComponentDependency::stop);
        registry.deactivate(this);
    }

    public boolean satisfied() {
        for (Map.Entry<ComponentDependency<S, ?>, Boolean> entry : dependencies.entrySet()) {
            if (!entry.getKey().isOptional() && !entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public <T> void satisfaction(ComponentDependency<S, T> dependency, boolean satisfied) {
        dependencies.put(dependency, satisfied);
        if (satisfied()) {
            registry.activate(this);
        } else {
            registry.deactivate(this);
        }
    }

    public Set<ComponentDependency<S, ?>> getDependencies() {
        return dependencies.keySet();
    }
}
