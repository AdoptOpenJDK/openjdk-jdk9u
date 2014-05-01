/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestDynShrinkHeap
 * @bug 8016479
 * @summary Verify that the heap shrinks after full GC according to the current values of the Min/MaxHeapFreeRatio flags
 * @library /testlibrary
 * @run main/othervm -XX:+UseAdaptiveSizePolicyWithSystemGC -XX:+UseParallelGC -XX:MinHeapFreeRatio=0 -XX:MaxHeapFreeRatio=100 -verbose:gc TestDynShrinkHeap
 */

import com.oracle.java.testlibrary.TestDynamicVMOption;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import sun.management.ManagementFactoryHelper;
import static com.oracle.java.testlibrary.Asserts.*;

public class TestDynShrinkHeap {

    public static final String MIN_FREE_RATIO_FLAG_NAME = "MinHeapFreeRatio";
    public static final String MAX_FREE_RATIO_FLAG_NAME = "MaxHeapFreeRatio";

    private static ArrayList<byte[]> list = new ArrayList<>(0);
    private static final int M = 1024 * 1024; // to make heap more manageable by test code

    private final TestDynamicVMOption maxRatioOption;
    private final TestDynamicVMOption minRatioOption;

    public TestDynShrinkHeap() {
        minRatioOption = new TestDynamicVMOption(MIN_FREE_RATIO_FLAG_NAME);
        maxRatioOption = new TestDynamicVMOption(MAX_FREE_RATIO_FLAG_NAME);
    }

    private final void test() {
        System.gc();
        MemoryUsagePrinter.printMemoryUsage("init");

        eat();
        MemoryUsagePrinter.printMemoryUsage("eaten");
        MemoryUsage muFull = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        free();
        MemoryUsagePrinter.printMemoryUsage("free");
        MemoryUsage muFree = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        assertLessThan(muFree.getCommitted(), muFull.getCommitted(), String.format(
                "committed free heap size is not less than committed full heap size, heap hasn't been shrunk?%n"
                + "%s = %s%n%s = %s",
                MIN_FREE_RATIO_FLAG_NAME,
                ManagementFactoryHelper.getDiagnosticMXBean().getVMOption(MIN_FREE_RATIO_FLAG_NAME).getValue(),
                MAX_FREE_RATIO_FLAG_NAME,
                ManagementFactoryHelper.getDiagnosticMXBean().getVMOption(MAX_FREE_RATIO_FLAG_NAME).getValue()
        ));
    }

    private void eat() {
        for (int i = 0; i < M; i++) {
            list.add(new byte[1024]);
        }
        MemoryUsagePrinter.printMemoryUsage("allocated " + M + " arrays");

        list.subList(0, M / 2).clear();
        System.gc();
        MemoryUsagePrinter.printMemoryUsage("array halved");
    }

    private void free() {
        maxRatioOption.setIntValue(minRatioOption.getIntValue() + 1);
        System.gc();
        MemoryUsagePrinter.printMemoryUsage("under pressure");
    }

    public static void main(String[] args) {
        new TestDynShrinkHeap().test();
    }
}

/**
 * Prints memory usage to standard output
 */
class MemoryUsagePrinter {

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static void printMemoryUsage(String label) {
        MemoryUsage memusage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        float freeratio = 1f - (float) memusage.getUsed() / memusage.getCommitted();
        System.out.format("[%-24s] init: %-7s, used: %-7s, comm: %-7s, freeRatio ~= %.1f%%%n",
                label,
                humanReadableByteCount(memusage.getInit(), true),
                humanReadableByteCount(memusage.getUsed(), true),
                humanReadableByteCount(memusage.getCommitted(), true),
                freeratio * 100
        );
    }
}
