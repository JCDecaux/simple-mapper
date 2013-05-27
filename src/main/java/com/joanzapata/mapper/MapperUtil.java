/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.joanzapata.mapper;

import java.lang.reflect.Method;
import java.util.*;

final class MapperUtil {

    public static final Collection createCollectionLike(Collection source) {
        return new ArrayList();
    }

    public static final Map createMapLike(Map source) {
        return new HashMap();
    }

    /**
     * Find a getter on the source object for the given setter name.
     * @param source The source object.
     * @param setter The setter method.
     * @return the corresponding getter method given the setter method, or null if nothing found.
     */
    public static Method findGetter(Object source, Method setter, List<String> knownSuffixes) {

        // A setter must have 1 parameter
        if (setter.getParameterTypes().length != 1) {
            return null;
        }

        // The name of the setter should be "set..."
        if (setter.getName().length() <= 3 || !setter.getName().startsWith("set")) {
            return null;
        }

        String expectedGetterName = "get" + setter.getName().substring(3);
        String expectedGetterNameForBooleans = "is" + setter.getName().substring(3);

        Class loopClass = source.getClass();
        while (loopClass != Object.class) {
            for (Method method : loopClass.getMethods()) {
                if (method.getParameterTypes().length != 0) continue;

                String methodName = method.getName();
                if (knownSuffixes != null) {
                    expectedGetterName = removeSuffix(expectedGetterName, knownSuffixes);
                    methodName = removeSuffix(methodName, knownSuffixes);
                }

                if (expectedGetterName.equals(method.getName()) ||
                        expectedGetterNameForBooleans.equals(method.getName())) {
                    return method;
                }
            }
            loopClass = loopClass.getSuperclass();
        }
        return null;
    }

    public static String removeSuffix(String expectedGetterName, List<String> knownSuffixes) {
        for (String suffix : knownSuffixes) {
            if (expectedGetterName.endsWith(suffix)) {
                return expectedGetterName.substring(0,
                        expectedGetterName.length() - suffix.length());
            }
        }
        return expectedGetterName;
    }

    public static <D> D mapNativeTypeOrNull(Object source, Class<D> destinationClass) {
        if (source instanceof Byte ||
                source instanceof Short ||
                source instanceof Integer ||
                source instanceof Long ||
                source instanceof Float ||
                source instanceof Double ||
                source instanceof Boolean ||
                source instanceof String ||
                source instanceof Character) {
            return (D) source;
        }
        return null;
    }

    /**
     * Try to find a user defined mapping for the source class that is more precise than
     * the destination type retrieved from target object.
     */
    public static <D> Class<D> findBestDestinationType(
            Class<?> sourceClass, Class<D> destinationClass, MappingContext context) {
        Class explicitMapping = context.getMapping(sourceClass);
        return (explicitMapping == null ||
                explicitMapping.isAssignableFrom(destinationClass)) ?
                destinationClass : explicitMapping;
    }
}