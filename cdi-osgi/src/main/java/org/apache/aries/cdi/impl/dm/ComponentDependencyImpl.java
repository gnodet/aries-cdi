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
package org.apache.aries.cdi.impl.dm;

public class ComponentDependencyImpl extends AbstractDependency<ComponentDependencyImpl> {

    private ComponentImpl component;
    private boolean satisfied;

    public ComponentDependencyImpl() {
        setRequired(true);
    }

    public ComponentDependencyImpl(ComponentDependencyImpl prototype) {
        super(prototype);
        this.component = prototype.component;
    }

    public ComponentDependencyImpl setComponent(ComponentImpl component) {
        ensureNotActive();
        this.component = component;
        return this;
    }

    @Override
    public void start() {
        super.start();
        if (component != null) {
            component.add((c, s) -> {
                onComponentChange(s == ComponentState.TRACKING_OPTIONAL);
            });
        }
    }

    protected void onComponentChange(boolean satisfied) {
        if (this.satisfied != satisfied) {
            this.satisfied = satisfied;
            m_component.handleEvent(
                    this,
                    satisfied ? EventType.ADDED : EventType.REMOVED,
                    new Event(component));
        }
    }

    @Override
    public Class<?> getAutoConfigType() {
        return null;
    }

    @Override
    public ComponentDependencyImpl createCopy() {
        return new ComponentDependencyImpl(this);
    }

    @Override
    public String getSimpleName() {
        return component != null ? component.toString() : null;
    }

    @Override
    public String getType() {
        return "component";
    }
}
