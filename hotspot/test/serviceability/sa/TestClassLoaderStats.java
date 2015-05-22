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

import jdk.test.lib.Platform;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.OutputAnalyzer;

/*
 * @test
 * @library /testlibrary
 * @build jdk.test.lib.*
 * @run main TestClassLoaderStats
 */
public class TestClassLoaderStats {

    public static void main(String[] args) throws Exception {
        if (!Platform.shouldSAAttach()) {
            System.out.println("SA attach not expected to work - test skipped.");
            return;
        }

        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                "-XX:+UsePerfData",
                "sun.jvm.hotspot.tools.ClassLoaderStats",
                Integer.toString(ProcessTools.getProcessId()));
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        System.out.println(output.getOutput());

        output.shouldHaveExitValue(0);
        output.shouldContain("Debugger attached successfully.");
        // The class loader stats header needs to be presented in the output:
        output.shouldMatch("class_loader\\W+classes\\W+bytes\\W+parent_loader\\W+alive?\\W+type");
        output.stderrShouldNotMatch("[E|e]xception");
        output.stderrShouldNotMatch("[E|e]rror");
    }

}
