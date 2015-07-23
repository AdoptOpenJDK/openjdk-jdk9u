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
 * @test TestLargePageUseForAuxMemory.java
 * @summary Test that auxiliary data structures are allocated using large pages if available.
 * @bug 8058354 8079208
 * @key gc
 * @library /testlibrary /../../test/lib
 * @requires (vm.gc=="G1" | vm.gc=="null")
 * @build jdk.test.lib.* sun.hotspot.WhiteBox
 * @build TestLargePageUseForAuxMemory
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UseG1GC -XX:+WhiteBoxAPI -XX:+IgnoreUnrecognizedVMOptions -XX:+UseLargePages TestLargePageUseForAuxMemory
 */

import java.lang.Math;

import jdk.test.lib.*;
import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

public class TestLargePageUseForAuxMemory {
    static final long HEAP_REGION_SIZE = 1 * 1024 * 1024;
    static long largePageSize;
    static long smallPageSize;
    static long allocGranularity;

    static void checkSmallTables(OutputAnalyzer output, long expectedPageSize) throws Exception {
        output.shouldContain("G1 'Block offset table': pg_sz=" + expectedPageSize);
        output.shouldContain("G1 'Card counts table': pg_sz=" + expectedPageSize);
    }

    static void checkBitmaps(OutputAnalyzer output, long expectedPageSize) throws Exception {
        output.shouldContain("G1 'Prev Bitmap': pg_sz=" + expectedPageSize);
        output.shouldContain("G1 'Next Bitmap': pg_sz=" + expectedPageSize);
    }

    static void testVM(String what, long heapsize, boolean cardsShouldUseLargePages, boolean bitmapShouldUseLargePages) throws Exception {
        System.out.println(what + " heapsize " + heapsize + " card table should use large pages " + cardsShouldUseLargePages + " " +
                           "bitmaps should use large pages " + bitmapShouldUseLargePages);
        ProcessBuilder pb;
        // Test with large page enabled.
        pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                   "-XX:G1HeapRegionSize=" + HEAP_REGION_SIZE,
                                                   "-Xms" + heapsize,
                                                   "-Xmx" + heapsize,
                                                   "-XX:+TracePageSizes",
                                                   "-XX:+UseLargePages",
                                                   "-XX:+IgnoreUnrecognizedVMOptions",  // there is no ObjectAlignmentInBytes in 32 bit builds
                                                   "-XX:ObjectAlignmentInBytes=8",
                                                   "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        checkSmallTables(output, (cardsShouldUseLargePages ? largePageSize : smallPageSize));
        checkBitmaps(output, (bitmapShouldUseLargePages ? largePageSize : smallPageSize));
        output.shouldHaveExitValue(0);

        // Test with large page disabled.
        pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                   "-XX:G1HeapRegionSize=" + HEAP_REGION_SIZE,
                                                   "-Xms" + heapsize,
                                                   "-Xmx" + heapsize,
                                                   "-XX:+TracePageSizes",
                                                   "-XX:-UseLargePages",
                                                   "-XX:+IgnoreUnrecognizedVMOptions",  // there is no ObjectAlignmentInBytes in 32 bit builds
                                                   "-XX:ObjectAlignmentInBytes=8",
                                                   "-version");

        output = new OutputAnalyzer(pb.start());
        checkSmallTables(output, smallPageSize);
        checkBitmaps(output, smallPageSize);
        output.shouldHaveExitValue(0);
    }

    private static long gcd(long x, long y) {
        while (x > 0) {
            long t = x;
            x = y % x;
            y = t;
        }
        return y;
    }

    private static long lcm(long x, long y) {
        return x * (y / gcd(x, y));
    }

    public static void main(String[] args) throws Exception {
        if (!Platform.isDebugBuild()) {
            System.out.println("Skip tests on non-debug builds because the required option TracePageSizes is a debug-only option.");
            return;
        }

        // Size that a single card covers.
        final int cardSize = 512;
        WhiteBox wb = WhiteBox.getWhiteBox();
        smallPageSize = wb.getVMPageSize();
        largePageSize = wb.getVMLargePageSize();
        allocGranularity = wb.getVMAllocationGranularity();
        final long heapAlignment = lcm(cardSize * smallPageSize, largePageSize);

        if (largePageSize == 0) {
            System.out.println("Skip tests because large page support does not seem to be available on this platform.");
            return;
        }
        if (largePageSize == smallPageSize) {
            System.out.println("Skip tests because large page support does not seem to be available on this platform." +
                               "Small and large page size are the same.");
            return;
        }

        // To get large pages for the card table etc. we need at least a 1G heap (with 4k page size).
        // 32 bit systems will have problems reserving such an amount of contiguous space, so skip the
        // test there.
        if (!Platform.is32bit()) {
            final long heapSizeForCardTableUsingLargePages = largePageSize * cardSize;
            final long heapSizeDiffForCardTable = Math.max(Math.max(allocGranularity * cardSize, HEAP_REGION_SIZE), largePageSize);

            Asserts.assertGT(heapSizeForCardTableUsingLargePages, heapSizeDiffForCardTable,
                             "To test we would require to use an invalid heap size");
            testVM("case1: card table and bitmap use large pages (barely)", heapSizeForCardTableUsingLargePages, true, true);
            testVM("case2: card table and bitmap use large pages (extra slack)", heapSizeForCardTableUsingLargePages + heapSizeDiffForCardTable, true, true);
            testVM("case3: only bitmap uses large pages (barely not)", heapSizeForCardTableUsingLargePages - heapSizeDiffForCardTable, false, true);
        }

        // Minimum heap requirement to get large pages for bitmaps is 128M heap. This seems okay to test
        // everywhere.
        final int bitmapTranslationFactor = 8 * 8; // ObjectAlignmentInBytes * BitsPerByte
        final long heapSizeForBitmapUsingLargePages = largePageSize * bitmapTranslationFactor;
        final long heapSizeDiffForBitmap = Math.max(Math.max(allocGranularity * bitmapTranslationFactor, HEAP_REGION_SIZE),
                                                    Math.max(largePageSize, heapAlignment));

        Asserts.assertGT(heapSizeForBitmapUsingLargePages, heapSizeDiffForBitmap,
                         "To test we would require to use an invalid heap size");

        testVM("case4: only bitmap uses large pages (barely)", heapSizeForBitmapUsingLargePages, false, true);
        testVM("case5: only bitmap uses large pages (extra slack)", heapSizeForBitmapUsingLargePages + heapSizeDiffForBitmap, false, true);
        testVM("case6: nothing uses large pages (barely not)", heapSizeForBitmapUsingLargePages - heapSizeDiffForBitmap, false, false);
    }
}
