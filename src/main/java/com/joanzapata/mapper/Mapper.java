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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.joanzapata.mapper.MapperUtil.*;
import static java.util.Arrays.asList;

/** Create a new Mapper and map objects using the map() method. */
public final class Mapper {

    private final Logger logger = LoggerFactory.getLogger(Mapper.class);

    private final Map<Class, Class> mappings;

    private final List<String> knownSuffixes = asList("DTO", "BO");

    private final List<HookWrapper> hooks;

    private final List<CustomMapperWrapper> customMappers;

    private boolean strictMode = false;

    public Mapper() {
        mappings = new HashMap<Class, Class>();
        hooks = new ArrayList<HookWrapper>();
        customMappers = new ArrayList<CustomMapperWrapper>();
    }

    /**
     * If set to true and not getter is found for a property,
     * or if types mismatch between getter and setter,
     * a StrictModeException will be thrown. <b>Default is false.</b>
     */
    public Mapper strictMode(boolean strictMode) {
        this.strictMode = strictMode;
        return this;
    }

    /**
     * Set the strict mode to true (false by default).
     * If set to true and not getter is found for a property,
     * or if types mismatch between getter and setter,
     * a StrictModeException will be thrown.
     */
    public Mapper strictMode() {
        return strictMode(true);
    }

    /**
     * Adds an explicit mapping from a source class to a destination class.
     * You shouldn't need this unless you're using inheritance.
     * @param sourceClass      The source class.
     * @param destinationClass The destination class.
     * @return The current mapper for chaining.
     */
    public Mapper mapping(Class<?> sourceClass, Class<?> destinationClass) {
        mappings.put(sourceClass, destinationClass);
        return this;
    }

    /** Same as {@link #mapping(Class, Class)} but adds the mapping in both directions. */
    public Mapper biMapping(Class<?> sourceClass, Class<?> destinationClass) {
        mapping(sourceClass, destinationClass);
        mapping(destinationClass, sourceClass);
        return this;
    }

    /**
     * Add a hook to the mapping process. This hook will be called after the complete mapping of the object.
     * @param hook The hook object.
     * @return The current mapper for chaining.
     */
    public <S, D> Mapper hook(Hook<S, D> hook) {
        hooks.add(new HookWrapper(hook));
        return this;
    }

    /**
     * Add a custom mapper to the mapping process. This custom mapper will be called when
     * the mapper will need to transform an object of type S to an object of type D.
     * @param customMapper Implement this interface to provide a mapping method from S to D.
     * @param <S>          The source type.
     * @param <D>          The destination type.
     * @return The current mapper for chaining.
     */
    public <S, D> Mapper customMapper(CustomMapper<S, D> customMapper) {
        customMappers.add(new CustomMapperWrapper(customMapper));
        return this;
    }
    
    /**
     * Add a custom bi mapper to the mapping process. This custom bi mapper will be called when
     * the mapper will need to transform an object of type S to an object of type D or vice-versa
     * @param customBiMapper Implement this interface to provide a mapping method from S to D and vice-versa.
     * @param <S>          The source type.
     * @param <D>          The destination type.
     * @return The current mapper for chaining.
     */
    public <S, D> Mapper customBiMapper(CustomBiMapper<S, D> customBiMapper) {
        customMappers.add(new CustomMapperWrapper<S, D>(customBiMapper.getStoDMapper()));
        customMappers.add(new CustomMapperWrapper<D, S>(customBiMapper.getDtoSMapper()));
        return this;
    }

    /**
     * Map the source object with the destination class using the getters/setters.
     * This method is thread-safe.
     * @param source           The source object.
     * @param destinationClass The destination class.
     * @return A destination instance filled using its setters and the source getters.
     */
    public <D> D map(Object source, Class<D> destinationClass) {
        return map(source, destinationClass, null);
    }

    /**
     * Map the source object with the destination class using the getters/setters.
     * This method is thread-safe.
     * @param source           The source object.
     * @param destinationClass The destination class.
     * @param mappingContext   An existing mapping context.
     * @return A destination instance filled using its setters and the source getters.
     */
    public <D> D map(Object source, Class<D> destinationClass, MappingContext mappingContext) {
        if (source instanceof Iterable)
            return (D) map((Iterable) source, destinationClass);
        MappingContext context = new MappingContext(mappingContext, mappings);
        return nominalMap(source, destinationClass, context);
    }

    /** Same as {@link #map(Object, Class)}, but applies to collections. */
    public <D, U, CU extends Collection<U>, CD extends Collection<D>>
    CD map(CU source, Class<D> destinationClass) {
        return map(source, destinationClass, null);
    }

    /** Same as {@link #map(Object, Class)}, but applies to collections. */
    public <D, U, CU extends Collection<U>, CD extends Collection<D>> CD map(CU source, Class<D> destinationClass, MappingContext mappingContext) {
        return mapCollection(source, destinationClass, new MappingContext(mappingContext, mappings));
    }

    /** Same as {@link #map(Object, Class)}, but applies to map objects. */
    public <KS, VS, KD, VD> Map<KD, VD> map(Map<KS, VS> source, Class<KD> destinationKeyClass, Class<VD> destinationValueClass) {
        return map(source, destinationKeyClass, destinationValueClass, null);
    }

    /** Same as {@link #map(Object, Class)}, but applies to map objects. */
    public <KS, VS, KD, VD> Map<KD, VD> map(Map<KS, VS> source, Class<KD> destinationKeyClass, Class<VD> destinationValueClass, MappingContext mappingContext) {
        return mapMap(source, destinationKeyClass, destinationValueClass, new MappingContext(mappingContext, mappings));
    }

    private <D, U, CD extends Collection<D>, CU extends Collection<U>>
    CD mapCollection(CU source, Class<D> destinationClass, MappingContext context) {
        if (source == null) return null;
        // Instantiate the same type as the source
        CD out;
        if (source instanceof Set) {
            out = (CD) new HashSet<D>();
        } else if (source instanceof List) {
            out = (CD) new ArrayList<D>();
        } else if (source instanceof Queue) {
            out = (CD) new LinkedList<D>();
        } else {
            if (strictMode) {
                throw new StrictModeException("Unhandler type " + source.getClass().getName());
            } else return null;
        }
        for (Object s : source) {
            final D mappedElement = nominalMap(s, destinationClass, context);
            if (mappedElement != null) out.add(mappedElement);
        }
        return out;
    }

    private <KS, VS, KD, VD> Map<KD, VD> mapMap(Map<KS, VS> source, Class<KD> keyClass, Class<VD> valueClass, MappingContext context) {
        if (source == null) return null;
        Map<KD, VD> out = new HashMap<KD, VD>();
        for (Map.Entry<KS, VS> s : source.entrySet()) {
            KD mappedKey = nominalMap(s.getKey(), keyClass, context);
            VD mappedValue = nominalMap(s.getValue(), valueClass, context);
            out.put(mappedKey, mappedValue);
        }
        return out;
    }

    private <D> D mapEnum(Enum source, Class<D> destinationClass, MappingContext context) {
        if (!destinationClass.isEnum()) {
            if (strictMode)
                throw new StrictModeException("Unable to map "
                        + source.getClass().getCanonicalName()
                        + " -> " + destinationClass.getCanonicalName());
            return null;
        }

        for (D constant : destinationClass.getEnumConstants()) {
            if (((Enum) constant).name().equals(source.name())) {
                return constant;
            }
        }
        if (strictMode)
            throw new StrictModeException("Unable to map "
                    + source.getClass().getCanonicalName()
                    + " -> " + destinationClass.getCanonicalName());
        return null;
    }

    private <D> D nominalMap(Object source, Class<D> destinationClass, MappingContext context) {
        // This is the entry point of the nominal mapping process.
        // Special cases directly provided by the user (lists, etc...) must have been processed before.
        return nominalMap(source, null, destinationClass, context);
    }

    private <D> D nominalMap(Object source, Type field, Class<D> destinationClass, MappingContext context) {
        if (source == null) return null;

        if (source instanceof Collection) {
            ParameterizedType type = (ParameterizedType) field;
            return (D) mapCollection((Collection) source, (Class) type.getActualTypeArguments()[0], context);
        }

        if (source instanceof Map) {
            ParameterizedType type = (ParameterizedType) field;
            return (D) mapMap((Map) source, (Class) type.getActualTypeArguments()[0],
                    (Class) type.getActualTypeArguments()[1], context);
        }

        if (source.getClass().isEnum()) {
            return (D) mapEnum((Enum) source, destinationClass, context);
        }

        // First, use already existing if possible (prevents cyclic mapping)
        D alreadyMapped = context.getAlreadyMapped(source);
        if (alreadyMapped != null) {
            return alreadyMapped;
        }

        // Try to find appropriate customMapper if any
        CustomMapperResult<D> customMapperResult = MapperUtil.applyCustomMappers(customMappers, source, destinationClass, context);
        if (customMapperResult.hasMatched) return customMapperResult.result;

        // Map native types if possible
        D nativeMapped = mapPrimitiveTypeOrNull(source);
        if (nativeMapped != null) {
            if (isCompatiblePrimitiveType(nativeMapped, destinationClass)) {
                applyHooks(hooks, source, destinationClass);
                return nativeMapped;
            } else {
                if (strictMode) {
                    throw new StrictModeException("Unable to map "
                            + nativeMapped.getClass().getCanonicalName()
                            + " -> " + destinationClass.getCanonicalName());
                } else {
                    logger.debug("Incompatible types {} -> {}, ignore...",
                            source.getClass().getSimpleName(),
                            destinationClass.getSimpleName());
                }
            }
        }

        // Otherwise, create appropriate instance and store it in context
        Class<D> bestDestinationClass = findBestDestinationType(source.getClass(), destinationClass, context);
        D destinationInstance = context.createInstanceForDestination(bestDestinationClass);
        context.putAlreadyMapped(source, destinationInstance);

        for (Method setterMethod : findAllSetterMethods(bestDestinationClass)) {

            Method getterMethod = findGetter(source, setterMethod, knownSuffixes);

            if (getterMethod == null) {
                if (strictMode) {
                    throw new StrictModeException("No suitable getter for "
                            + setterMethod.getDeclaringClass().getSimpleName()
                            + "." + setterMethod.getName() + "() method in "
                            + source.getClass().getCanonicalName());
                } else {
                    logger.debug("No getter found for {}.{}() in {}, ignore...",
                            setterMethod.getDeclaringClass().getSimpleName(),
                            setterMethod.getName(),
                            bestDestinationClass.getSimpleName());
                    continue;
                }
            }

            logger.debug("{}.{}() -> {}.{}()",
                    getterMethod.getDeclaringClass().getSimpleName(),
                    getterMethod.getName(),
                    setterMethod.getDeclaringClass().getSimpleName(),
                    setterMethod.getName());

            try {

                Object objectBeingTransferred = getterMethod.invoke(source);

                if (objectBeingTransferred == null) {
                    continue;
                }

                // NOTE This is a recursive call, but the stack is unlikely to explode
                // because the cyclic dependencies are managed, and the depth of a model
                // isn't supposed to get that high.
                Object mappedObjectBeingTransferred = nominalMap(objectBeingTransferred,
                        setterMethod.getGenericParameterTypes()[0],
                        setterMethod.getParameterTypes()[0],
                        context);

                // Apply setter
                setterMethod.invoke(destinationInstance, mappedObjectBeingTransferred);

            } catch (Exception e) {
                if (strictMode) {
                    throw new StrictModeException("Unable to map "
                            + setterMethod.getDeclaringClass().getSimpleName()
                            + "." + setterMethod.getName() + "() method in "
                            + source.getClass().getCanonicalName(), e);
                }
            }
        }

        applyHooks(hooks, source, destinationInstance);
        return destinationInstance;
    }

    public static class CustomMapperResult<T> {
        boolean hasMatched = false;
        T result = null;

        // Success
        public CustomMapperResult(T result) {
            this.result = result;
            this.hasMatched = true;
        }

        // Fail
        public CustomMapperResult() {}
    }
}
