/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aries.cdi.impl.extender;

import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.CDIProvider;

import org.apache.aries.cdi.spi.CdiContainerFactory;
import org.apache.felix.utils.extender.AbstractExtender;
import org.apache.felix.utils.extender.Extension;
import org.apache.karaf.util.tracker.SingleServiceTracker;
import org.apache.karaf.util.tracker.SingleServiceTracker.SingleServiceListener;
import org.apache.karaf.util.tracker.annotation.RequireService;
import org.apache.karaf.util.tracker.annotation.Services;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

@Services(
        requires = {@RequireService(CdiContainerFactory.class)}
)
public class CdiExtender extends AbstractExtender implements CDIProvider, SingleServiceListener {

    private SingleServiceTracker<CdiContainerFactory> containerFactoryTracker;

    public CdiExtender() {
        CDI.setCDIProvider(this);
    }

    @Override
    protected void doStart() throws Exception {
        containerFactoryTracker = new SingleServiceTracker<>(
                getBundleContext(), CdiContainerFactory.class, this
        );
    }

    @Override
    protected void doStop() throws Exception {
        containerFactoryTracker.close();
    }

    @Override
    protected Extension doCreateExtension(Bundle bundle) throws Exception {
        Bundle extenderBundle = getBundleContext().getBundle();
        boolean hasWire = false;
        for (BundleWire wire : bundle.adapt(BundleWiring.class).getRequiredWires(null)) {
            hasWire |= "osgi.extender".equals(wire.getCapability().getNamespace())
                    && wire.getProviderWiring().getBundle().equals(extenderBundle);
        }
        if (hasWire) {
            return new CdiExtension(containerFactoryTracker.getService(), bundle);
        } else {
            return null;
        }
    }

    @Override
    protected void debug(Bundle bundle, String msg) {

    }

    @Override
    protected void warn(Bundle bundle, String msg, Throwable t) {

    }

    @Override
    protected void error(String msg, Throwable t) {

    }

    @Override
    public CDI<Object> getCDI() {
        return CdiExtension.current();
    }

    @Override
    public void serviceFound() {
        startTracking();
    }

    @Override
    public void serviceLost() {
        stopTracking();
    }

    @Override
    public void serviceReplaced() {
        stopTracking();
        startTracking();
    }
}
