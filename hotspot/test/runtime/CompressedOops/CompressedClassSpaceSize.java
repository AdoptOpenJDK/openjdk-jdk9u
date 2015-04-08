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

/*
 * @test
 * @bug 8022865
 * @summary Tests for the -XX:CompressedClassSpaceSize command line option
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @run main CompressedClassSpaceSize
 */
import com.oracle.java.testlibrary.*;

public class CompressedClassSpaceSize {

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer output;
        if (Platform.is64bit()) {
            // Minimum size is 1MB
            pb = ProcessTools.createJavaProcessBuilder("-XX:CompressedClassSpaceSize=0",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("CompressedClassSpaceSize of 0 is invalid")
                  .shouldHaveExitValue(1);

            // Invalid size of -1 should be handled correctly
            pb = ProcessTools.createJavaProcessBuilder("-XX:CompressedClassSpaceSize=-1",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("Improperly specified VM option 'CompressedClassSpaceSize=-1'")
                  .shouldHaveExitValue(1);


            // Maximum size is 3GB
            pb = ProcessTools.createJavaProcessBuilder("-XX:CompressedClassSpaceSize=4g",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("CompressedClassSpaceSize of 4294967296 is invalid")
                  .shouldHaveExitValue(1);


            // Make sure the minimum size is set correctly and printed
            pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                                                       "-XX:CompressedClassSpaceSize=1m",
                                                       "-XX:+PrintCompressedOopsMode",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("Compressed class space size: 1048576")
                  .shouldHaveExitValue(0);


            // Make sure the maximum size is set correctly and printed
            pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                                                       "-XX:CompressedClassSpaceSize=3g",
                                                       "-XX:+PrintCompressedOopsMode",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("Compressed class space size: 3221225472")
                  .shouldHaveExitValue(0);


            pb = ProcessTools.createJavaProcessBuilder("-XX:-UseCompressedOops",
                                                       "-XX:CompressedClassSpaceSize=1m",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("Setting CompressedClassSpaceSize has no effect when compressed class pointers are not used")
                  .shouldHaveExitValue(0);


            pb = ProcessTools.createJavaProcessBuilder("-XX:-UseCompressedClassPointers",
                                                       "-XX:CompressedClassSpaceSize=1m",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("Setting CompressedClassSpaceSize has no effect when compressed class pointers are not used")
                  .shouldHaveExitValue(0);
        } else {
            // 32bit platforms doesn't have compressed oops
            pb = ProcessTools.createJavaProcessBuilder("-XX:CompressedClassSpaceSize=1m",
                                                       "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("Setting CompressedClassSpaceSize has no effect when compressed class pointers are not used")
                  .shouldHaveExitValue(0);
        }
    }
}
