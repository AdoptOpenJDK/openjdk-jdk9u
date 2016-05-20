/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary /test/lib
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.code
 *          jdk.vm.ci/jdk.vm.ci.meta
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xmixed -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:CompileCommand=exclude,*::check
 *                   -XX:+DoEscapeAnalysis
 *                   -Xbatch
 *                   -Dcompiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate=false
 *                   compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 * @run main/othervm -Xmixed -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:CompileCommand=exclude,*::check
 *                   -XX:+DoEscapeAnalysis
 *                   -Xbatch
 *                   -Dcompiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate=true
 *                   compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest
 */

package compiler.jvmci.compilerToVM;

import java.lang.reflect.Method;
import jdk.vm.ci.hotspot.HotSpotStackFrameReference;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;

import compiler.jvmci.common.CTVMUtilities;
import compiler.testlibrary.CompilerUtils;
import compiler.whitebox.CompilerWhiteBoxTest;

import sun.hotspot.WhiteBox;

public class MaterializeVirtualObjectTest {
    private static final WhiteBox WB;
    private static final Method METHOD;
    private static final ResolvedJavaMethod RESOLVED_METHOD;
    private static final boolean INVALIDATE;

    static {
        WB = WhiteBox.getWhiteBox();
        try {
            METHOD = MaterializeVirtualObjectTest.class.getDeclaredMethod(
                    "testFrame", String.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new Error("Can't get executable for test method", e);
        }
        RESOLVED_METHOD = CTVMUtilities.getResolvedMethod(METHOD);
        INVALIDATE = Boolean.getBoolean(
            "compiler.jvmci.compilerToVM.MaterializeVirtualObjectTest.invalidate");
    }

    public static void main(String[] args) {
        int levels[] = CompilerUtils.getAvailableCompilationLevels();
        // we need compilation level 4 to use EscapeAnalysis
        if (levels.length < 1 || levels[levels.length - 1] != 4) {
            System.out.println("INFO: Test needs compilation level 4 to"
                    + " be available. Skipping.");
        } else {
            new MaterializeVirtualObjectTest().test();
        }
    }

    private static String getName() {
        return "CASE: invalidate=" + INVALIDATE;
    }

    private void test() {
        System.out.println(getName());
        Asserts.assertFalse(WB.isMethodCompiled(METHOD), getName()
                + " : method unexpectedly compiled");
        /* need to trigger compilation by multiple method invocations
           in order to have method profile data to be gathered */
        boolean isTiered = WB.getBooleanVMFlag("TieredCompilation");
        int COMPILE_THRESHOLD = isTiered ? CompilerWhiteBoxTest.THRESHOLD
                : CompilerWhiteBoxTest.THRESHOLD * 2;
        for (int i = 0; i < COMPILE_THRESHOLD; i++) {
            testFrame("someString", i);
        }
        Asserts.assertTrue(WB.isMethodCompiled(METHOD), getName()
                + "Method unexpectedly not compiled");
        testFrame("someString", CompilerWhiteBoxTest.THRESHOLD);
    }

    private void testFrame(String str, int iteration) {
        Helper helper = new Helper(str);
        check(iteration);
        Asserts.assertTrue((helper.string != null) && (this != null)
                && (helper != null), getName() + " : some locals are null");
    }

    private void check(int iteration) {
        // Materialize virtual objects on last invocation
        if (iteration == CompilerWhiteBoxTest.THRESHOLD) {
            HotSpotStackFrameReference hsFrame = CompilerToVMHelper
                    .getNextStackFrame(/* topmost frame */ null,
                            new ResolvedJavaMethod[]{
                                RESOLVED_METHOD}, /* don't skip any */ 0);
            Asserts.assertNotNull(hsFrame, getName() + " : got null frame");
            Asserts.assertTrue(WB.isMethodCompiled(METHOD), getName()
                    + "Test method should be compiled");
            Asserts.assertTrue(hsFrame.hasVirtualObjects(), getName()
                    + ": has no virtual object before materialization");
            CompilerToVMHelper.materializeVirtualObjects(hsFrame, INVALIDATE);
            Asserts.assertFalse(hsFrame.hasVirtualObjects(), getName()
                    + " : has virtual object after materialization");
            Asserts.assertEQ(WB.isMethodCompiled(METHOD), !INVALIDATE, getName()
                    + " : unexpected compiled status");
        }
    }

    private class Helper {
        public String string;

        public Helper(String s) {
            this.string = s;
        }
    }
}
