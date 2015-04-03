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

import com.oracle.java.testlibrary.Asserts;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.List;
import sun.hotspot.code.BlobType;

/*
 * @test InitialAndMaxUsageTest
 * @library /testlibrary /../../test/lib
 * @modules java.base/sun.misc
 *          java.management
 * @build InitialAndMaxUsageTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:-UseCodeCacheFlushing
 *     -XX:-MethodFlushing -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:+SegmentedCodeCache -XX:CompileCommand=compileonly,null::*
 *     -XX:-UseLargePages InitialAndMaxUsageTest
 * @summary testing of initial and max usage
 */
public class InitialAndMaxUsageTest {

    private static final double CACHE_USAGE_COEF = 0.95d;
    private final BlobType btype;
    private final boolean lowerBoundIsZero;
    private final long maxSize;

    public InitialAndMaxUsageTest(BlobType btype) {
        this.btype = btype;
        this.maxSize = btype.getSize();
        /* Only profiled code cache initial size should be 0, because of
         -XX:CompileCommand=compileonly,null::* non-methods might be not empty,
         as well as non-profiled methods, because it's used as fallback in
         case non-methods is full */
        lowerBoundIsZero = btype == BlobType.MethodProfiled;
    }

    public static void main(String[] args) {
        for (BlobType btype : BlobType.getAvailable()) {
            new InitialAndMaxUsageTest(btype).runTest();
        }
    }

    private void fillWithSize(long size, List<Long> blobs) {
        long blob;
        while ((blob = CodeCacheUtils.WB.allocateCodeBlob(size, btype.id))
                != 0L) {
            blobs.add(blob);
        }
    }

    protected void runTest() {
        long headerSize = CodeCacheUtils.getHeaderSize(btype);
        MemoryPoolMXBean bean = btype.getMemoryPool();
        long initialUsage = btype.getMemoryPool().getUsage().getUsed();
        System.out.printf("INFO: trying to test %s of max size %d and initial"
                + " usage %d%n", bean.getName(), maxSize, initialUsage);
        Asserts.assertLT(initialUsage + headerSize + 1L, maxSize,
                "Initial usage is close to total size for " + bean.getName());
        if (lowerBoundIsZero) {
            Asserts.assertEQ(initialUsage, 0L, "Unexpected initial usage");
        }
        ArrayList<Long> blobs = new ArrayList<>();
        long minAllocationUnit = CodeCacheUtils.MIN_ALLOCATION - headerSize;
        /* now filling code cache with large-sized allocation first, since
         lots of small allocations takes too much time, so, just a small
         optimization */
        try {
            for (int coef = 1000000; coef > 0; coef /= 10) {
                fillWithSize(coef * minAllocationUnit, blobs);
            }
            Asserts.assertGT((double) bean.getUsage().getUsed(),
                    CACHE_USAGE_COEF * maxSize, String.format("Unable to fill "
                            + "more than %f of %s. Reported usage is %d ",
                            CACHE_USAGE_COEF, bean.getName(),
                            bean.getUsage().getUsed()));
        } finally {
            for (long entry : blobs) {
                CodeCacheUtils.WB.freeCodeBlob(entry);
            }
        }
        System.out.printf("INFO: Scenario finished successfully for %s%n",
                bean.getName());
    }

}
