/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8152341
 * @requires (vm.simpleArch == "x64" | vm.simpleArch == "sparcv9" | vm.simpleArch == "aarch64")
 * @library /test/lib /compiler/jvmci/jdk.vm.ci.hotspot.test/src
 * @modules jdk.vm.ci/jdk.vm.ci.meta
 *          jdk.vm.ci/jdk.vm.ci.common
 *          jdk.vm.ci/jdk.vm.ci.runtime
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          java.base/jdk.internal.misc
 * @run testng/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      jdk.vm.ci.hotspot.test.MemoryAccessProviderTest
 */

package jdk.vm.ci.hotspot.test;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.runtime.JVMCI;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MemoryAccessProviderTest {
    private static final MemoryAccessProvider PROVIDER = JVMCI.getRuntime().getHostJVMCIBackend().getConstantReflection().getMemoryAccessProvider();

    @Test(dataProvider = "positivePrimitive", dataProviderClass = MemoryAccessProviderData.class)
    public void testPositiveReadPrimitiveConstant(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        Assert.assertEquals(PROVIDER.readPrimitiveConstant(kind, base, offset, bitsCount), expected, "Failed to read constant");
    }

    @Test(dataProvider = "positivePrimitive", dataProviderClass = MemoryAccessProviderData.class, expectedExceptions = {IllegalArgumentException.class})
    public void testReadPrimitiveConstantNullBase(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        Assert.assertNull(PROVIDER.readPrimitiveConstant(kind, null, offset, bitsCount), "Unexpected value for null base");
    }

    @Test(dataProvider = "negative", dataProviderClass = MemoryAccessProviderData.class, expectedExceptions = {IllegalArgumentException.class})
    public void testNegativeReadPrimitiveConstant(JavaKind kind, Constant base) {
        PROVIDER.readPrimitiveConstant(kind, base, 0L, kind == null ? 0 : kind.getBitCount());
    }

    @Test(dataProvider = "positiveObject", dataProviderClass = MemoryAccessProviderData.class, expectedExceptions = {IllegalArgumentException.class})
    public void testObjectReadPrimitiveConstant(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        PROVIDER.readPrimitiveConstant(kind, base, 0L, bitsCount);
    }

    @Test(dataProvider = "positivePrimitive", dataProviderClass = MemoryAccessProviderData.class, expectedExceptions = {IllegalArgumentException.class})
    public void testReadPrimitiveConstantLessBits(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        PROVIDER.readPrimitiveConstant(kind, base, offset, bitsCount - 1);
    }

    @Test(dataProvider = "positiveObject", dataProviderClass = MemoryAccessProviderData.class)
    public void testPositiveReadObjectConstant(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        Assert.assertEquals(PROVIDER.readObjectConstant(base, offset), expected, "Unexpected result");
    }

    @Test(dataProvider = "positiveObject", dataProviderClass = MemoryAccessProviderData.class)
    public void testNegativeReadObjectConstantNullBase(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        Assert.assertNull(PROVIDER.readObjectConstant(null, offset), "Unexpected return");
    }

    @Test(dataProvider = "positiveObject", dataProviderClass = MemoryAccessProviderData.class)
    public void testNegativeReadObjectConstantWrongOffset(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        Assert.assertNull(PROVIDER.readObjectConstant(base, offset + 1), "Expected null");
    }

    @Test(dataProvider = "positivePrimitive", dataProviderClass = MemoryAccessProviderData.class)
    public void testNegativeReadObjectConstantPrimitiveBase(JavaKind kind, Constant base, Long offset, Object expected, int bitsCount) {
        Assert.assertNull(PROVIDER.readObjectConstant(base, offset), "Expected null");
    }
}
