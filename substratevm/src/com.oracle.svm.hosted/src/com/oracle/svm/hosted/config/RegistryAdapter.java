/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.config;

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.ReflectionRegistry;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.nativeimage.impl.RuntimeProxyCreationSupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.configure.ConfigurationTypeDescriptor;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.ProxyConfigurationTypeDescriptor;
import com.oracle.svm.configure.ReflectionConfigurationParserDelegate;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.TypeResult;

public class RegistryAdapter implements ReflectionConfigurationParserDelegate<ConfigurationCondition, Class<?>> {
    protected final ReflectionRegistry registry;
    private final ImageClassLoader classLoader;

    public static RegistryAdapter create(ReflectionRegistry registry, RuntimeProxyCreationSupport proxyRegistry, RuntimeSerializationSupport<ConfigurationCondition> serializationSupport,
                    RuntimeJNIAccessSupport jniSupport, ImageClassLoader classLoader) {
        if (registry instanceof RuntimeReflectionSupport) {
            return new ReflectionRegistryAdapter((RuntimeReflectionSupport) registry, proxyRegistry, serializationSupport, jniSupport, classLoader);
        } else if (registry instanceof RuntimeJNIAccessSupport) {
            return new JNIRegistryAdapter(registry, classLoader);
        } else {
            return new RegistryAdapter(registry, classLoader);
        }
    }

    RegistryAdapter(ReflectionRegistry registry, ImageClassLoader classLoader) {
        this.registry = registry;
        this.classLoader = classLoader;
    }

    @Override
    public void registerType(ConfigurationCondition condition, Class<?> type) {
        registry.register(condition, type);
    }

    @Override
    public TypeResult<Class<?>> resolveType(ConfigurationCondition condition, ConfigurationTypeDescriptor typeDescriptor, boolean allowPrimitives, boolean jniAccessible) {
        switch (typeDescriptor.getDescriptorType()) {
            case NAMED -> {
                String reflectionName = ClassNameSupport.typeNameToReflectionName(((NamedConfigurationTypeDescriptor) typeDescriptor).name());
                TypeResult<Class<?>> result = resolveNamedType(reflectionName, allowPrimitives);
                if (!result.isPresent()) {
                    if (throwMissingRegistrationErrors() && result.getException() instanceof ClassNotFoundException) {
                        registry.registerClassLookup(condition, reflectionName);
                    }
                }
                return result;
            }
            case PROXY -> {
                return resolveProxyType((ProxyConfigurationTypeDescriptor) typeDescriptor);
            }
            default -> {
                throw VMError.shouldNotReachHere("Unknown type descriptor kind: %s", typeDescriptor.getDescriptorType());
            }
        }
    }

    private TypeResult<Class<?>> resolveNamedType(String reflectionName, boolean allowPrimitives) {
        TypeResult<Class<?>> result = classLoader.findClass(reflectionName, allowPrimitives);
        if (!result.isPresent() && result.getException() instanceof NoClassDefFoundError) {
            /*
             * In certain cases when the class name is identical to an existing class name except
             * for lettercase, `ClassLoader.findClass` throws a `NoClassDefFoundError` but
             * `Class.forName` throws a `ClassNotFoundException`.
             */
            try {
                Class.forName(reflectionName);
            } catch (ClassNotFoundException notFoundException) {
                result = TypeResult.forException(reflectionName, notFoundException);
            } catch (Throwable t) {
                // ignore
            }
        }
        return result;
    }

    private TypeResult<Class<?>> resolveProxyType(ProxyConfigurationTypeDescriptor typeDescriptor) {
        String typeName = typeDescriptor.toString();
        List<TypeResult<Class<?>>> interfaceResults = typeDescriptor.interfaceNames().stream()
                        .map(interfaceTypeName -> resolveNamedType(ClassNameSupport.typeNameToReflectionName(interfaceTypeName), false)).toList();
        List<Class<?>> interfaces = new ArrayList<>();
        for (TypeResult<Class<?>> intf : interfaceResults) {
            if (!intf.isPresent()) {
                return TypeResult.forException(typeName, intf.getException());
            }
            interfaces.add(intf.get());
        }
        try {
            DynamicProxyRegistry proxyRegistry = ImageSingletons.lookup(DynamicProxyRegistry.class);
            Class<?> proxyClass = proxyRegistry.getProxyClassHosted(interfaces.toArray(Class<?>[]::new));
            return TypeResult.forType(typeName, proxyClass);
        } catch (Throwable t) {
            return TypeResult.forException(typeName, t);
        }
    }

    @Override
    public void registerPublicClasses(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerDeclaredClasses(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerRecordComponents(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerPermittedSubclasses(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerNestMembers(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerSigners(ConfigurationCondition condition, Class<?> type) {
    }

    @Override
    public void registerPublicFields(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (!queriedOnly) {
            registry.register(condition, false, type.getFields());
        }
    }

    @Override
    public void registerDeclaredFields(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        if (!queriedOnly) {
            registry.register(condition, false, type.getDeclaredFields());
        }
    }

    @Override
    public void registerPublicMethods(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getMethods());
    }

    @Override
    public void registerDeclaredMethods(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getDeclaredMethods());
    }

    @Override
    public void registerPublicConstructors(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getConstructors());
    }

    @Override
    public void registerDeclaredConstructors(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        registry.register(condition, queriedOnly, type.getDeclaredConstructors());
    }

    @Override
    @SuppressWarnings("unused")
    public final void registerField(ConfigurationCondition condition, Class<?> type, String fieldName, boolean allowWrite, boolean jniAccessible) throws NoSuchFieldException {
        try {
            registerField(condition, allowWrite, jniAccessible, type.getDeclaredField(fieldName));
        } catch (NoSuchFieldException e) {
            if (throwMissingRegistrationErrors()) {
                registerFieldNegativeQuery(condition, jniAccessible, type, fieldName);
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("unused")
    protected void registerField(ConfigurationCondition condition, boolean allowWrite, boolean jniAccessible, Field field) {
        registry.register(condition, allowWrite, field);
    }

    @SuppressWarnings("unused")
    protected void registerFieldNegativeQuery(ConfigurationCondition condition, boolean jniAccessible, Class<?> type, String fieldName) {
        registry.registerFieldLookup(condition, type, fieldName);
    }

    @Override
    public boolean registerAllMethodsWithName(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type, String methodName) {
        boolean found = false;
        Executable[] methods = type.getDeclaredMethods();
        for (Executable method : methods) {
            if (method.getName().equals(methodName)) {
                registerExecutable(condition, queriedOnly, jniAccessible, method);
                found = true;
            }
        }
        return found;
    }

    @Override
    public boolean registerAllConstructors(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Class<?> type) {
        Executable[] methods = type.getDeclaredConstructors();
        registerExecutable(condition, queriedOnly, jniAccessible, methods);
        return methods.length > 0;
    }

    @Override
    public void registerUnsafeAllocated(ConfigurationCondition condition, Class<?> clazz) {
        if (!clazz.isArray() && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
            registry.register(condition, true, clazz);
            /*
             * Ignore otherwise as the implementation of allocateInstance will anyhow throw an
             * exception.
             */
        }
    }

    @Override
    public final void registerMethod(ConfigurationCondition condition, boolean queriedOnly, Class<?> type, String methodName, List<Class<?>> methodParameterTypes, boolean jniAccessible)
                    throws NoSuchMethodException {
        try {
            Class<?>[] parameterTypesArray = getParameterTypes(methodParameterTypes);
            Method method;
            try {
                method = type.getDeclaredMethod(methodName, parameterTypesArray);
            } catch (NoClassDefFoundError e) {
                /*
                 * getDeclaredMethod() builds a set of all the declared methods, which can fail when
                 * a symbolic reference from another method to a type (via parameters, return value)
                 * cannot be resolved. getMethod() builds a different set of methods and can still
                 * succeed. This case must be handled for predefined classes when, during the run
                 * observed by the agent, a referenced class was not loaded and is not available now
                 * precisely because the application used getMethod() instead of
                 * getDeclaredMethod().
                 */
                try {
                    method = type.getMethod(methodName, parameterTypesArray);
                } catch (Throwable ignored) {
                    throw e;
                }
            }
            registerExecutable(condition, queriedOnly, jniAccessible, method);
        } catch (NoSuchMethodException e) {
            if (throwMissingRegistrationErrors()) {
                registerMethodNegativeQuery(condition, jniAccessible, type, methodName, methodParameterTypes);
            } else {
                throw e;
            }
        }
    }

    @Override
    public final void registerConstructor(ConfigurationCondition condition, boolean queriedOnly, Class<?> type, List<Class<?>> methodParameterTypes, boolean jniAccessible)
                    throws NoSuchMethodException {
        Class<?>[] parameterTypesArray = getParameterTypes(methodParameterTypes);
        try {
            registerExecutable(condition, queriedOnly, jniAccessible, type.getDeclaredConstructor(parameterTypesArray));
        } catch (NoSuchMethodException e) {
            if (throwMissingRegistrationErrors()) {
                registerConstructorNegativeQuery(condition, jniAccessible, type, methodParameterTypes);
            } else {
                throw e;
            }
        }
    }

    static Class<?>[] getParameterTypes(List<Class<?>> methodParameterTypes) {
        return methodParameterTypes.toArray(Class<?>[]::new);
    }

    @SuppressWarnings("unused")
    protected void registerExecutable(ConfigurationCondition condition, boolean queriedOnly, boolean jniAccessible, Executable... executable) {
        registry.register(condition, queriedOnly, executable);
    }

    @SuppressWarnings("unused")
    protected void registerMethodNegativeQuery(ConfigurationCondition condition, boolean jniAccessible, Class<?> type, String methodName, List<Class<?>> methodParameterTypes) {
        registry.registerMethodLookup(condition, type, methodName, getParameterTypes(methodParameterTypes));
    }

    @SuppressWarnings("unused")
    protected void registerConstructorNegativeQuery(ConfigurationCondition condition, boolean jniAccessible, Class<?> type, List<Class<?>> constructorParameterTypes) {
        registry.registerConstructorLookup(condition, type, getParameterTypes(constructorParameterTypes));
    }

    @Override
    public void registerAsSerializable(ConfigurationCondition condition, Class<?> clazz) {
    }

    @Override
    public void registerAsJniAccessed(ConfigurationCondition condition, Class<?> clazz) {
    }

    @Override
    public String getTypeName(Class<?> type) {
        return type.getTypeName();
    }

    @Override
    public String getSimpleName(Class<?> type) {
        return ClassUtil.getUnqualifiedName(type);
    }
}
