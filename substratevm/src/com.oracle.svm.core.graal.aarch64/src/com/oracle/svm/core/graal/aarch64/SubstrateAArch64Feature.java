/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.graal.code.SubstrateVectorArchitectureFactory;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig.ConfigKind;
import com.oracle.svm.core.heap.ReferenceAccess;

import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.graal.compiler.replacements.aarch64.AArch64GraphBuilderPlugins;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.aarch64.VectorAArch64;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;

@AutomaticallyRegisteredFeature
@Platforms(Platform.AARCH64.class)
class SubstrateAArch64Feature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {

        ImageSingletons.add(SubstrateRegisterConfigFactory.class, new SubstrateRegisterConfigFactory() {
            @Override
            public RegisterConfig newRegisterFactory(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, Boolean preserveFramePointer) {
                return new SubstrateAArch64RegisterConfig(config, metaAccess, target, preserveFramePointer);
            }
        });

        ImageSingletons.add(ReservedRegisters.class, new AArch64ReservedRegisters());

        if (!SubstrateOptions.useLLVMBackend()) {

            ImageSingletons.add(SubstrateBackendFactory.class, new SubstrateBackendFactory() {
                @Override
                public SubstrateBackend newBackend(Providers newProviders) {
                    return new SubstrateAArch64Backend(newProviders);
                }
            });

            ImageSingletons.add(SubstrateLoweringProviderFactory.class, new SubstrateAArch64LoweringProviderFactory());

            ImageSingletons.add(TargetGraphBuilderPlugins.class, new AArch64GraphBuilderPlugins());
            ImageSingletons.add(SubstrateSuitesCreatorProvider.class, new SubstrateAArch64SuitesCreatorProvider());
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!SubstrateOptions.useLLVMBackend()) {
            AArch64CalleeSavedRegisters.createAndRegister();
        }
    }
}

class SubstrateAArch64LoweringProviderFactory extends SubstrateVectorArchitectureFactory implements SubstrateLoweringProviderFactory {

    @Override
    public DefaultJavaLoweringProvider newLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        VectorArchitecture vectorArchitecture = getSingletonVectorArchitecture(VectorAArch64::new, (AArch64) ConfigurationValues.getTarget().arch, !SubstrateOptions.useLLVMBackend(),
                        ConfigurationValues.getObjectLayout().getReferenceSize(), ReferenceAccess.singleton().haveCompressedReferences(),
                        ConfigurationValues.getObjectLayout().getAlignment());
        return new SubstrateAArch64LoweringProvider(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target, vectorArchitecture);
    }
}
