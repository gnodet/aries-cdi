/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.cdi.impl.dm;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.osgi.service.cm.ConfigurationException;

/**
 * Utility methods for invoking callbacks.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class InvocationUtil {
    /**
     * Interface internally used to handle a ConfigurationAdmin update synchronously, in a component executor queue.
     */
    @FunctionalInterface
    public interface ConfigurationHandler {
        void handle() throws Exception;
    }

    /**
     * Max time to wait until a configuration update callback has returned.
     */
    private final static int UPDATED_MAXWAIT = 30000; // max time to wait until a CM update has completed

    /**
     * Invokes a configuration update callback synchronously, but through the component executor queue.
     */
    public static void invokeUpdated(Executor queue, ConfigurationHandler handler) throws ConfigurationException {
        Callable<Exception> result = () -> {
            try {
                handler.handle();
            } catch (Exception e) {
                return e;
            }
            return null;
        };
        
        FutureTask<Exception> ft = new FutureTask<>(result);
        queue.execute(ft);
                
        try {
            Exception err = ft.get(UPDATED_MAXWAIT, TimeUnit.MILLISECONDS);
            if (err != null) {
                throw err;
            }
        }
        
        catch (ConfigurationException e) {
            throw e;
        }

        catch (Throwable error) {
            Throwable rootCause = error.getCause();
            if (rootCause != null) {
                if (rootCause instanceof ConfigurationException) {
                    throw (ConfigurationException) rootCause;
                }
                throw new ConfigurationException("", "Configuration update error, unexpected exception.", rootCause);
            } else {
                throw new ConfigurationException("", "Configuration update error, unexpected exception.", error);
            }
        }
    }
}
