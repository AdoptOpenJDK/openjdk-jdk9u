/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import java.lang.management.MemoryPoolMXBean;
import sun.hotspot.code.BlobType;

/*
 * @test PeakUsageTest
 * @library /testlibrary /../../test/lib
 * @modules java.base/sun.misc
 *          java.management
 * @build PeakUsageTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *     -XX:+WhiteBoxAPI -XX:+SegmentedCodeCache
 *     -XX:CompileCommand=compileonly,null::* PeakUsageTest
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *     -XX:+WhiteBoxAPI -XX:-SegmentedCodeCache
 *     -XX:CompileCommand=compileonly,null::* PeakUsageTest
 * @summary testing of getPeakUsage() and resetPeakUsage for
 *     segmented code cache
 */
public class PeakUsageTest {

    private final BlobType btype;

    public PeakUsageTest(BlobType btype) {
        this.btype = btype;
    }

    public static void main(String[] args) {
        for (BlobType btype : BlobType.getAvailable()) {
            if (CodeCacheUtils.isCodeHeapPredictable(btype)) {
                new PeakUsageTest(btype).runTest();
            }
        }
    }

    protected void runTest() {
        MemoryPoolMXBean bean = btype.getMemoryPool();
        bean.resetPeakUsage();
        long addr = CodeCacheUtils.WB.allocateCodeBlob(
                CodeCacheUtils.ALLOCATION_SIZE, btype.id);
        long newPeakUsage = bean.getPeakUsage().getUsed();
        try {
            Asserts.assertEQ(newPeakUsage, bean.getUsage().getUsed(),
                    "Peak usage does not match usage after allocation for "
                    + bean.getName());
        } finally {
            if (addr != 0) {
                CodeCacheUtils.WB.freeCodeBlob(addr);
            }
        }
        Asserts.assertEQ(newPeakUsage, bean.getPeakUsage().getUsed(),
                "Code cache peak usage has changed after usage decreased for "
                + bean.getName());
        bean.resetPeakUsage();
        Asserts.assertEQ(bean.getPeakUsage().getUsed(),
                bean.getUsage().getUsed(),
                "Code cache peak usage is not equal to usage after reset for "
                + bean.getName());
        long addr2 = CodeCacheUtils.WB.allocateCodeBlob(
                CodeCacheUtils.ALLOCATION_SIZE, btype.id);
        try {
            Asserts.assertEQ(bean.getPeakUsage().getUsed(),
                    bean.getUsage().getUsed(),
                    "Code cache peak usage is not equal to usage after fresh "
                    + "allocation for " + bean.getName());
        } finally {
            if (addr2 != 0) {
                CodeCacheUtils.WB.freeCodeBlob(addr2);
            }
        }
        System.out.printf("INFO: Scenario finished successfully for %s%n",
                bean.getName());
    }
}
