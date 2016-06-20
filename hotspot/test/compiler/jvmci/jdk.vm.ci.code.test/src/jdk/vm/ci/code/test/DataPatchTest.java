/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @requires (vm.simpleArch == "x64" | vm.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library /
 * @modules jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.meta
 *          jdk.vm.ci/jdk.vm.ci.code
 *          jdk.vm.ci/jdk.vm.ci.code.site
 *          jdk.vm.ci/jdk.vm.ci.runtime
 *          jdk.vm.ci/jdk.vm.ci.amd64
 *          jdk.vm.ci/jdk.vm.ci.sparc
 * @compile CodeInstallationTest.java DebugInfoTest.java TestAssembler.java amd64/AMD64TestAssembler.java sparc/SPARCTestAssembler.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI jdk.vm.ci.code.test.DataPatchTest
 */

package jdk.vm.ci.code.test;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.junit.Assume;
import org.junit.Test;

/**
 * Test code installation with data patches.
 */
public class DataPatchTest extends CodeInstallationTest {

    public static Class<?> getConstClass() {
        return DataPatchTest.class;
    }

    private void test(TestCompiler compiler) {
        test(compiler, getMethod("getConstClass"));
    }

    @Test
    public void testInlineObject() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) constantReflection.asJavaClass(type);
            Register ret = asm.emitLoadPointer(c);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testInlineNarrowObject() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedOops);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) constantReflection.asJavaClass(type);
            Register compressed = asm.emitLoadPointer((HotSpotConstant) c.compress());
            Register ret = asm.emitUncompressPointer(compressed, HotSpotVMConfig.config().narrowOopBase, HotSpotVMConfig.config().narrowOopShift);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testDataSectionReference() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) constantReflection.asJavaClass(type);
            DataSectionReference ref = asm.emitDataItem(c);
            Register ret = asm.emitLoadPointer(ref);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testNarrowDataSectionReference() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedOops);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) constantReflection.asJavaClass(type);
            HotSpotConstant cCompressed = (HotSpotConstant) c.compress();
            DataSectionReference ref = asm.emitDataItem(cCompressed);
            Register compressed = asm.emitLoadNarrowPointer(ref);
            Register ret = asm.emitUncompressPointer(compressed, HotSpotVMConfig.config().narrowOopBase, HotSpotVMConfig.config().narrowOopShift);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testInlineMetadata() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            Register klass = asm.emitLoadPointer((HotSpotConstant) constantReflection.asObjectHub(type));
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testInlineNarrowMetadata() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedClassPointers);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant hub = (HotSpotConstant) constantReflection.asObjectHub(type);
            Register narrowKlass = asm.emitLoadPointer((HotSpotConstant) hub.compress());
            Register klass = asm.emitUncompressPointer(narrowKlass, HotSpotVMConfig.config().narrowKlassBase, HotSpotVMConfig.config().narrowKlassShift);
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testMetadataInDataSection() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant hub = (HotSpotConstant) constantReflection.asObjectHub(type);
            DataSectionReference ref = asm.emitDataItem(hub);
            Register klass = asm.emitLoadPointer(ref);
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testNarrowMetadataInDataSection() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedClassPointers);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant hub = (HotSpotConstant) constantReflection.asObjectHub(type);
            HotSpotConstant narrowHub = (HotSpotConstant) hub.compress();
            DataSectionReference ref = asm.emitDataItem(narrowHub);
            Register narrowKlass = asm.emitLoadNarrowPointer(ref);
            Register klass = asm.emitUncompressPointer(narrowKlass, HotSpotVMConfig.config().narrowKlassBase, HotSpotVMConfig.config().narrowKlassShift);
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }
}
