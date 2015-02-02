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

import org.testng.annotations.Test;

import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.dcmd.CommandExecutor;
import com.oracle.java.testlibrary.dcmd.JMXExecutor;

/*
 * @test
 * @summary Test of diagnostic command VM.system_properties
 * @library /testlibrary
 * @build com.oracle.java.testlibrary.*
 * @build com.oracle.java.testlibrary.dcmd.*
 * @run testng SystemPropertiesTest
 */
public class SystemPropertiesTest {
    private final static String PROPERTY_NAME  = "SystemPropertiesTestPropertyName";
    private final static String PROPERTY_VALUE = "SystemPropertiesTestPropertyValue";

    public void run(CommandExecutor executor) {
        System.setProperty(PROPERTY_NAME, PROPERTY_VALUE);

        OutputAnalyzer output = executor.execute("VM.system_properties");
        output.shouldContain(PROPERTY_NAME + "=" + PROPERTY_VALUE);
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
