package org.apache.aries.cdi.spi;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;

public interface CdiContainer {

    <T> Instance<T> getInstance();

    BeanManager getBeanManager();

    void destroy();

}
