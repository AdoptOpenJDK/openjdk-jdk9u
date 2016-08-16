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
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 * @run main PatchModuleCDS
 */

import java.io.File;
import jdk.test.lib.*;

public class PatchModuleCDS {

    public static void main(String args[]) throws Throwable {
        System.out.println("Test that --patch-module and -Xshare:dump are incompatibable");
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("--patch-module=java.naming=mods/java.naming", "-Xshare:dump");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Cannot use the following option when dumping the shared archive: --patch-module");

        System.out.println("Test that --patch-module and -Xshare:on are incompatibable");
        String filename = "patch_module.jsa";
        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:dump");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("ro space:"); // Make sure archive got created.

        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:on",
            "--patch-module=java.naming=mods/java.naming",
            "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("The shared archive file cannot be used with --patch-module");

        output.shouldHaveExitValue(1);
    }
}
