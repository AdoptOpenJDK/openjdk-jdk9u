package compiler.jvmci.compilerToVM;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.Pair;
import sun.hotspot.code.NMethod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * @test
 * @bug 8136421
 * @requires (vm.simpleArch == "x64" | vm.simpleArch == "sparcv9" | vm.simpleArch == "aarch64")
 * @library /testlibrary /test/lib /
 * @library ../common/patches
 * @ignore 8139383
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.code
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build compiler.jvmci.compilerToVM.ExecuteInstalledCodeTest
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.ExecuteInstalledCodeTest
 */

public class ExecuteInstalledCodeTest {

    public static void main(String[] args) {
        ExecuteInstalledCodeTest test = new ExecuteInstalledCodeTest();
        List<CompileCodeTestCase> testCases = new ArrayList<>();
        testCases.addAll(CompileCodeTestCase.generate(/* bci = */ -1));
        testCases .stream()
                // ignore <init> of abstract class -- 8138793
                .filter(e -> !(e.executable instanceof Constructor
                        && Modifier.isAbstract(
                                e.executable.getDeclaringClass()
                                        .getModifiers())))
                .forEach(test::checkSanity);
    }

    private void checkSanity(CompileCodeTestCase testCase) {
        System.out.println(testCase);
        // to have a clean state
        testCase.deoptimize();
        Pair<Object, ? extends Throwable> reflectionResult;
        Object[] args = Utils.getNullValues(
                testCase.executable.getParameterTypes());
        reflectionResult = testCase.invoke(args);
        NMethod nMethod = testCase.compile();
        if (nMethod == null) {
            throw new Error(testCase + " : nmethod is null");
        }
        InstalledCode installedCode = testCase.toInstalledCode();
        Object result = null;
        Throwable expectedException = reflectionResult.second;
        boolean gotException = true;
        try {
            args = addReceiver(testCase, args);
            result = CompilerToVMHelper.executeInstalledCode(
                    args, installedCode);
            if (testCase.executable instanceof Constructor) {
                // <init> doesn't have return value, it changes receiver
                result = args[0];
            }
            gotException = false;
        } catch (InvalidInstalledCodeException e) {
            throw new AssertionError(
                    testCase + " : unexpected InvalidInstalledCodeException", e);
        } catch (Throwable t) {
            if (expectedException == null) {
                throw new AssertionError(testCase
                        + " : got unexpected execption : " + t.getMessage(), t);
            }

            if (expectedException.getClass() != t.getClass()) {
                System.err.println("exception from CompilerToVM:");
                t.printStackTrace();
                System.err.println("exception from reflection:");
                expectedException.printStackTrace();
                throw new AssertionError(String.format(
                        "%s : got unexpected different exceptions : %s != %s",
                        testCase, expectedException.getClass(), t.getClass()));
            }
        }

        Asserts.assertEQ(reflectionResult.first, result, testCase
                + " : different return value");
        if (!gotException) {
            Asserts.assertNull(expectedException, testCase
                    + " : expected exception hasn't been thrown");
        }
    }

    private Object[] addReceiver(CompileCodeTestCase testCase, Object[] args) {
        if (!Modifier.isStatic(testCase.executable.getModifiers())) {
            // add instance as 0th arg
            Object[] newArgs = new Object[args.length + 1];
            newArgs[0] = testCase.receiver;
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;
        }
        return args;
    }
}
