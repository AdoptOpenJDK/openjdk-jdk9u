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

/**
 * @test
 * @bug 8057967
 * @run main/bootclasspath -Xbatch java.lang.invoke.CallSiteDepContextTest
 */
package java.lang.invoke;

import java.lang.ref.*;
import jdk.internal.org.objectweb.asm.*;
import sun.misc.Unsafe;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class CallSiteDepContextTest {
    static final Unsafe               UNSAFE = Unsafe.getUnsafe();
    static final MethodHandles.Lookup LOOKUP = MethodHandles.Lookup.IMPL_LOOKUP;
    static final String           CLASS_NAME = "java/lang/invoke/Test";
    static final String          METHOD_NAME = "m";
    static final MethodType             TYPE = MethodType.methodType(int.class);

    static MutableCallSite mcs;
    static MethodHandle bsmMH;

    static {
        try {
            bsmMH = LOOKUP.findStatic(
                    CallSiteDepContextTest.class, "bootstrap",
                    MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));
        } catch(Throwable e) {
            throw new InternalError(e);
        }
    }

    public static CallSite bootstrap(MethodHandles.Lookup caller,
                                     String invokedName,
                                     MethodType invokedType) {
        return mcs;
    }

    static class T {
        static int f1() { return 1; }
        static int f2() { return 2; }
    }

    static byte[] getClassFile(String suffix) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        MethodVisitor mv;
        cw.visit(52, ACC_PUBLIC | ACC_SUPER, CLASS_NAME + suffix, null, "java/lang/Object", null);
        {
            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, METHOD_NAME, TYPE.toMethodDescriptorString(), null, null);
            mv.visitCode();
            Handle bsm = new Handle(H_INVOKESTATIC,
                    "java/lang/invoke/CallSiteDepContextTest", "bootstrap",
                    bsmMH.type().toMethodDescriptorString());
            mv.visitInvokeDynamicInsn("methodName", TYPE.toMethodDescriptorString(), bsm);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void execute(int expected, MethodHandle... mhs) throws Throwable {
        for (int i = 0; i < 20_000; i++) {
            for (MethodHandle mh : mhs) {
                int r = (int) mh.invokeExact();
                if (r != expected) {
                    throw new Error(r + " != " + expected);
                }
            }
        }
    }

    public static void testSharedCallSite() throws Throwable {
        Class<?> cls1 = UNSAFE.defineAnonymousClass(Object.class, getClassFile("CS_1"), null);
        Class<?> cls2 = UNSAFE.defineAnonymousClass(Object.class, getClassFile("CS_2"), null);

        MethodHandle[] mhs = new MethodHandle[] {
            LOOKUP.findStatic(cls1, METHOD_NAME, TYPE),
            LOOKUP.findStatic(cls2, METHOD_NAME, TYPE)
        };

        mcs = new MutableCallSite(LOOKUP.findStatic(T.class, "f1", TYPE));
        execute(1, mhs);
        mcs.setTarget(LOOKUP.findStatic(T.class, "f2", TYPE));
        execute(2, mhs);
    }

    public static void testNonBoundCallSite() throws Throwable {
        mcs = new MutableCallSite(LOOKUP.findStatic(T.class, "f1", TYPE));

        // mcs.context == null
        MethodHandle mh = mcs.dynamicInvoker();
        execute(1, mh);

        // mcs.context == cls1
        Class<?> cls1 = UNSAFE.defineAnonymousClass(Object.class, getClassFile("NonBound_1"), null);
        MethodHandle mh1 = LOOKUP.findStatic(cls1, METHOD_NAME, TYPE);

        execute(1, mh1);

        mcs.setTarget(LOOKUP.findStatic(T.class, "f2", TYPE));

        execute(2, mh, mh1);
    }

    static ReferenceQueue rq = new ReferenceQueue();
    static PhantomReference ref;

    public static void testGC() throws Throwable {
        mcs = new MutableCallSite(LOOKUP.findStatic(T.class, "f1", TYPE));

        Class<?>[] cls = new Class[] {
                UNSAFE.defineAnonymousClass(Object.class, getClassFile("GC_1"), null),
                UNSAFE.defineAnonymousClass(Object.class, getClassFile("GC_2"), null),
        };

        MethodHandle[] mhs = new MethodHandle[] {
                LOOKUP.findStatic(cls[0], METHOD_NAME, TYPE),
                LOOKUP.findStatic(cls[1], METHOD_NAME, TYPE),
        };

        // mcs.context == cls[0]
        int r = (int) mhs[0].invokeExact();

        execute(1, mhs);

        ref = new PhantomReference<>(cls[0], rq);
        cls[0] = UNSAFE.defineAnonymousClass(Object.class, getClassFile("GC_3"), null);
        mhs[0] = LOOKUP.findStatic(cls[0], METHOD_NAME, TYPE);

        do {
            System.gc();
            try {
                Reference ref1 = rq.remove(1000);
                if (ref1 == ref) {
                    ref1.clear();
                    System.gc(); // Ensure that the stale context is cleared
                    break;
                }
            } catch(InterruptedException e) { /* ignore */ }
        } while (true);

        execute(1, mhs);
        mcs.setTarget(LOOKUP.findStatic(T.class, "f2", TYPE));
        execute(2, mhs);
    }

    public static void main(String[] args) throws Throwable {
        testSharedCallSite();
        testNonBoundCallSite();
        testGC();
        System.out.println("TEST PASSED");
    }
}
