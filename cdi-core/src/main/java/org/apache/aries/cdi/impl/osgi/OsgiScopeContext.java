package org.apache.aries.cdi.impl.osgi;

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.lang.annotation.Annotation;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OsgiScopeContext implements AlterableContext {

    private ConcurrentMap<Contextual<?>, Holder<?>> store = new ConcurrentHashMap<>();

    @Override
    public Class<? extends Annotation> getScope() {
        return OsgiScope.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        return (T) store.computeIfAbsent(contextual,
                c -> new Holder(c, creationalContext, c.create((CreationalContext) creationalContext))).instance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Contextual<T> contextual) {
        Holder<?> h = store.get(contextual);
        return h != null ? (T) h.instance : null;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void destroy(Contextual<?> contextual) {
        Holder<?> h = store.remove(contextual);
        if (h != null) {
            h.destroy();
        }
    }

    public void destroy() {
        while (!store.isEmpty()) {
            destroy(store.keySet().iterator().next());
        }
    }

    static class Holder<T> {
        final Contextual<T> contextual;
        final CreationalContext<T> context;
        final T instance;

        public Holder(Contextual<T> contextual, CreationalContext<T> context, T instance) {
            this.contextual = contextual;
            this.context = context;
            this.instance = instance;
        }

        public void destroy() {
            this.contextual.destroy(instance, context);
        }
    }
}
