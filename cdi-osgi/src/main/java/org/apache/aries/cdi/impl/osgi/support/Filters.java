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
package org.apache.aries.cdi.impl.osgi.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.aries.cdi.api.Attribute;
import org.apache.aries.cdi.api.Filter;

public class Filters {

    public static String getFilter(Collection<Annotation> annotations) {
        return and(getSubFilters(annotations));
    }

    public static String and(Collection<String> filters) {
        return combineFilter("&", filters);
    }

    public static String or(Collection<String> filters) {
        return combineFilter("|", filters);
    }

    public static String combineFilter(String op, Collection<String> filters) {
        String filter;
        switch (filters.size()) {
            case 0:
                filter = null;
                break;
            case 1:
                filter = filters.iterator().next();
                break;
            default:
                StringBuilder sb = new StringBuilder();
                sb.append("(");
                sb.append(op);
                filters.forEach(sb::append);
                sb.append(")");
                filter = sb.toString();
                break;
        }
        return filter;
    }

    public static List<String> getSubFilters(Collection<Annotation> annotations) {
        List<String> filters = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Filter) {
                String filter = ((Filter) annotation).value();
                if (!filter.startsWith("(") || !filter.endsWith(")")) {
                    filter = "(" + filter + ")";
                }
                filters.add(filter);
            } else {
                Class<? extends Annotation> annClass = annotation.annotationType();
                Attribute attr = annClass.getAnnotation(Attribute.class);
                if (attr != null) {
                    String name = attr.value();
                    Object value;
                    try {
                        Method[] methods = annClass.getDeclaredMethods();
                        if (methods != null && methods.length == 1) {
                            value = methods[0].invoke(annotation);
                        } else {
                            throw new IllegalArgumentException("Bad attribute " + annClass);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                    filters.add("(" + name + "=" + value + ")");
                }
            }
        }
        Collections.sort(filters);
        return filters;
    }
}
