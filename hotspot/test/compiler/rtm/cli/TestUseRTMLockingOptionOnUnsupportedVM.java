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
 *
 */

/**
 * @test
 * @bug 8031320
 * @summary Verify UseRTMLocking option processing on CPU with rtm support
 *          in case when VM should not support this option.
 * @library /testlibrary /../../test/lib /compiler/testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @build TestUseRTMLockingOptionOnUnsupportedVM
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestUseRTMLockingOptionOnUnsupportedVM
 */

import com.oracle.java.testlibrary.ExitCode;
import com.oracle.java.testlibrary.cli.*;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import com.oracle.java.testlibrary.cli.predicate.NotPredicate;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

public class TestUseRTMLockingOptionOnUnsupportedVM
        extends CommandLineOptionTest {
    private static final String DEFAULT_VALUE = "false";

    private TestUseRTMLockingOptionOnUnsupportedVM() {
        super(new AndPredicate(new SupportedCPU(),
                new NotPredicate(new SupportedVM())));
    }
    @Override
    public void runTestCases() throws Throwable {
        String errorMessage
                = RTMGenericCommandLineOptionTest.RTM_UNSUPPORTED_VM_ERROR;
        String shouldFailMessage = "JVM startup should fail with option "
                + "-XX:+UseRTMLocking even on unsupported VM. Error message"
                + " should be shown";
        String shouldPassMessage = "JVM startup should pass with option "
                + "-XX:-UseRTMLocking even on unsupported VM";
        // verify that we can't use +UseRTMLocking
        CommandLineOptionTest.verifySameJVMStartup(
                new String[] { errorMessage }, null, shouldFailMessage,
                shouldFailMessage, ExitCode.FAIL,
                 "-XX:+UseRTMLocking");
        // verify that we can turn it off
        CommandLineOptionTest.verifySameJVMStartup(null,
                new String[] { errorMessage }, shouldPassMessage,
                shouldPassMessage + " without any warnings", ExitCode.OK,
                "-XX:-UseRTMLocking");
        // verify that it is off by default
        CommandLineOptionTest.verifyOptionValueForSameVM("UseRTMLocking",
                TestUseRTMLockingOptionOnUnsupportedVM.DEFAULT_VALUE,
                String.format("Default value of option 'UseRTMLocking' should"
                    + " be '%s'", DEFAULT_VALUE));
    }

    public static void main(String args[]) throws Throwable {
        new TestUseRTMLockingOptionOnUnsupportedVM().test();
    }
}
