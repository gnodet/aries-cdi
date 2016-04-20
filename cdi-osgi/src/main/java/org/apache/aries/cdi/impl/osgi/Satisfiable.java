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
package org.apache.aries.cdi.impl.osgi;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public abstract class Satisfiable {

    protected final List<Consumer<Satisfiable>> listeners = new ArrayList<>();
    protected final Map<Satisfiable, Boolean> satisfiables = new HashMap<>();
    protected final AtomicBoolean satisfied = new AtomicBoolean();

    public void preStart(AfterBeanDiscovery event) {
        satisfiables.keySet().forEach(s -> s.preStart(event));
    }

    public void start() {
        satisfiables.keySet().forEach(Satisfiable::start);
    }

    public void addSatisfiable(Satisfiable satisfiable) {
        satisfiable.addListener(this::accept);
        satisfiables.put(satisfiable, false);
    }

    public void addListener(Consumer<Satisfiable> listener) {
        listeners.add(listener);
    }

    public boolean satisfied() {
        return satisfied.get()
                && satisfiables.values().stream().allMatch(Boolean::booleanValue);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "satisfied=" + satisfied() +
                ']';
    }

    protected void mayChangeSatisfaction(Runnable r) {
        boolean oldSatisfied = satisfied();
        r.run();
        boolean newSatisfied = satisfied();
        if (oldSatisfied != newSatisfied) {
            listeners.forEach(l -> l.accept(this));
        }
    }

    protected void accept(Satisfiable satisfiable) {
        mayChangeSatisfaction(() -> satisfiables.put(satisfiable, satisfiable.satisfied()));
    }

    protected void satisfied(boolean satisfied) {
        mayChangeSatisfaction(() -> this.satisfied.set(satisfied));
    }

}
