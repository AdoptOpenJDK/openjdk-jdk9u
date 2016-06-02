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
package jdk.vm.ci.hotspot;

import java.lang.reflect.Module;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.Option;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.runtime.services.JVMCICompilerFactory;
import jdk.vm.ci.services.Services;

final class HotSpotJVMCICompilerConfig {

    private static class DummyCompilerFactory extends JVMCICompilerFactory implements JVMCICompiler {

        public HotSpotCompilationRequestResult compileMethod(CompilationRequest request) {
            throw new JVMCIError("no JVMCI compiler selected");
        }

        @Override
        public String getCompilerName() {
            return "<none>";
        }

        @Override
        public JVMCICompiler createCompiler(JVMCIRuntime runtime) {
            return this;
        }
    }

    /**
     * Factory of the selected system compiler.
     */
    private static JVMCICompilerFactory compilerFactory;

    /**
     * Gets the selected system compiler factory.
     *
     * @return the selected system compiler factory
     */
    static JVMCICompilerFactory getCompilerFactory() {
        if (compilerFactory == null) {
            JVMCICompilerFactory factory = null;
            String compilerName = Option.Compiler.getString();
            if (compilerName != null) {
                for (JVMCICompilerFactory f : Services.load(JVMCICompilerFactory.class)) {
                    if (f.getCompilerName().equals(compilerName)) {
                        Module jvmciModule = JVMCICompilerFactory.class.getModule();
                        Services.exportJVMCITo(f.getClass());
                        f.onSelection();
                        factory = f;
                    }
                }
                if (factory == null) {
                    throw new JVMCIError("JVMCI compiler '%s' not found", compilerName);
                }
            } else {
                factory = new DummyCompilerFactory();
            }
            compilerFactory = factory;
        }
        return compilerFactory;
    }
}
