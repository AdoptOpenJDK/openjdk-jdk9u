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

/*
 * @test
 * @summary Test checks output displayed with jstat -gccause.
 *          Test scenario:
 *          tests forces debuggee application eat ~70% of heap and runs jstat.
 *          jstat should show that ~70% of heap (OC/OU ~= 70%).
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @library ../share
 * @ignore 8168396
 * @run main/othervm -XX:+UsePerfData -Xmx128M -XX:MaxMetaspaceSize=128M GcCauseTest02
 */
import utils.*;

public class GcCauseTest02 {

    private final static float targetMemoryUsagePercent = 0.7f;

    public static void main(String[] args) throws Exception {

        // We will be running "jstat -gc" tool
        JstatGcCauseTool jstatGcTool = new JstatGcCauseTool(ProcessHandle.current().getPid());

        // Run once and get the  results asserting that they are reasonable
        JstatGcCauseResults measurement1 = jstatGcTool.measure();
        measurement1.assertConsistency();

        GcProvoker gcProvoker = GcProvoker.createGcProvoker();

        // Eat metaspace and heap then run the tool again and get the results  asserting that they are reasonable
        gcProvoker.eatMetaspaceAndHeap(targetMemoryUsagePercent);
        JstatGcCauseResults measurement2 = jstatGcTool.measure();
        measurement2.assertConsistency();

        // Assert that space has been utilized acordingly
        JstatResults.assertSpaceUtilization(measurement2, targetMemoryUsagePercent);
    }
}
