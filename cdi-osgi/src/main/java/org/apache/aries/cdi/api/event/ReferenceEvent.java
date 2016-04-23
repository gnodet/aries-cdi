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
package org.apache.aries.cdi.api.event;

import org.osgi.framework.ServiceReference;

/**
 * The event sent by the CDI extender whenever a service that matches
 * an injection point is registered or unregistered from the OSGi
 * registry.
 *
 * @see ReferenceAdded
 * @see ReferenceRemoved
 * @see javax.enterprise.event.Observes
 */
public class ReferenceEvent<T> {

    private final ServiceReference<T> reference;
    private final T service;

    public ReferenceEvent(ServiceReference<T> reference, T service) {
        this.reference = reference;
        this.service = service;
    }

    public ServiceReference<T> getReference() {
        return reference;
    }

    public T getService() {
        return service;
    }

}
