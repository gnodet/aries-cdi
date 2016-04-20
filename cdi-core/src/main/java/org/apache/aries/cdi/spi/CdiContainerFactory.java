package org.apache.aries.cdi.spi;

import java.util.Collection;

import org.osgi.framework.Bundle;

public interface CdiContainerFactory {

    CdiContainer createContainer(Bundle bundle, Collection<Bundle> extensions);

    CdiContainer getContainer(Bundle bundle);

}
