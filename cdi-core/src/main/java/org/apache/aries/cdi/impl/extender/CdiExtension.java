package org.apache.aries.cdi.impl.extender;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.aries.cdi.spi.CdiContainer;
import org.apache.aries.cdi.spi.CdiContainerFactory;
import org.apache.aries.cdi.spi.ContainerInitialized;
import org.apache.felix.utils.extender.Extension;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public class CdiExtension extends CDI<Object> implements Extension  {

    private static ThreadLocal<CdiExtension> CURRENT = new ThreadLocal<>();
    
    public static CdiExtension current() {
        return CURRENT.get();
    }

    private final CdiContainerFactory containerFactory;
    private final Bundle bundle;
    private CdiContainer container;
    
    public CdiExtension(CdiContainerFactory containerFactory, Bundle bundle) {
        this.containerFactory = containerFactory;
        this.bundle = bundle;
    }

    @Override
    public void start() throws Exception {
        CURRENT.set(this);
        try {
            Set<Bundle> extensions = new HashSet<>();
            findExtensions(bundle, extensions);
            container = containerFactory.createContainer(bundle, extensions);
            BeanManager beanManager = container.getBeanManager();
            beanManager.fireEvent(new ContainerInitialized());
        } finally {
            CURRENT.set(null);
        }
    }

    @Override
    public void destroy() throws Exception {
        CURRENT.set(this);
        try {
            container.destroy();
        } finally {
            CURRENT.set(null);
        }
    }

    public void findExtensions(Bundle bundle, Set<Bundle> extensions) {
        List<BundleWire> wires = bundle.adapt(BundleWiring.class).getRequiredWires(
                "aries.cdi.extensions");
        if (wires != null) {
            for (BundleWire wire : wires) {
                Bundle b = wire.getProviderWiring().getBundle();
                extensions.add(b);
                findExtensions(b, extensions);
            }
        }
    }

    @Override
    public BeanManager getBeanManager() {
        return container().getBeanManager();
    }

    @Override
    public Instance<Object> select(Annotation... qualifiers) {
        return container().getInstance().select(qualifiers);
    }

    @Override
    public <U extends Object> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return container().getInstance().select(subtype, qualifiers);
    }

    @Override
    public <U extends Object> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return container().getInstance().select(subtype, qualifiers);
    }

    @Override
    public boolean isUnsatisfied() {
        return container().getInstance().isUnsatisfied();
    }

    @Override
    public boolean isAmbiguous() {
        return container().getInstance().isAmbiguous();
    }

    @Override
    public void destroy(Object instance) {
        container().getInstance().destroy(instance);
    }

    @Override
    public Iterator<Object> iterator() {
        return container().getInstance().iterator();
    }

    @Override
    public Object get() {
        return container().getInstance().get();
    }
    
    public CdiContainer container() {
        return container;
    }

    public Bundle getBundle() {
        return bundle;
    }

}
