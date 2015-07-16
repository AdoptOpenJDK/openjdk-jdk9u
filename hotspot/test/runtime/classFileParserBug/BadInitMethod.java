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
 *
 */

/*
 * @test
 * @bug 8130669
 * @summary VM prohibits <clinit> methods with return values
 * @compile ignoredClinit.jasm
 * @compile badInit.jasm
 * @run main/othervm -Xverify:all BadInitMethod
 */

// Test that a non-void <clinit> method does not cause an exception to be
// thrown.  But that a non-void <init> method causes a ClassFormatError
// exception.
public class BadInitMethod {
    public static void main(String args[]) throws Throwable {

        System.out.println("Regression test for bug 8130669");
        try {
            Class newClass = Class.forName("ignoredClinit");
        } catch (java.lang.Throwable e) {
            throw new RuntimeException("Unexpected exception: " + e.getMessage());
        }

        try {
            Class newClass = Class.forName("badInit");
            throw new RuntimeException("Expected ClassFormatError exception not thrown");
        } catch (java.lang.ClassFormatError e) {
            System.out.println("Test BadInitMethod passed");
        }
    }
}
