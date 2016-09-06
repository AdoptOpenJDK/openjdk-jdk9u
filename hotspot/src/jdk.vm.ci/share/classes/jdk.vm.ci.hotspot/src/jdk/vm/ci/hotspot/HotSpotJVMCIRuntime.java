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

import static jdk.vm.ci.common.InitTimer.timer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;

import jdk.internal.misc.VM;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.services.HotSpotJVMCICompilerFactory;
import jdk.vm.ci.hotspot.services.HotSpotJVMCICompilerFactory.CompilationLevel;
import jdk.vm.ci.hotspot.services.HotSpotVMEventListener;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.runtime.services.JVMCICompilerFactory;
import jdk.vm.ci.services.Services;

/**
 * HotSpot implementation of a JVMCI runtime.
 *
 * The initialization of this class is very fragile since it's initialized both through
 * {@link JVMCI#initialize()} or through calling {@link HotSpotJVMCIRuntime#runtime()} and
 * {@link HotSpotJVMCIRuntime#runtime()} is also called by {@link JVMCI#initialize()}. So this class
 * can't have a static initializer and any required initialization must be done as part of
 * {@link #runtime()}. This allows the initialization to funnel back through
 * {@link JVMCI#initialize()} without deadlocking.
 */
public final class HotSpotJVMCIRuntime implements HotSpotJVMCIRuntimeProvider {

    @SuppressWarnings("try")
    static class DelayedInit {
        private static final HotSpotJVMCIRuntime instance;

        static {
            try (InitTimer t = timer("HotSpotJVMCIRuntime.<init>")) {
                instance = new HotSpotJVMCIRuntime();
            }
        }
    }

    /**
     * Gets the singleton {@link HotSpotJVMCIRuntime} object.
     */
    public static HotSpotJVMCIRuntime runtime() {
        JVMCI.initialize();
        return DelayedInit.instance;
    }

    /**
     * A list of all supported JVMCI options.
     */
    public enum Option {
        Compiler(String.class, null, "Selects the system compiler."),
        // Note: The following one is not used (see InitTimer.ENABLED). It is added here
        // so that -Djvmci.PrintFlags=true shows the option.
        InitTimer(boolean.class, false, "Specifies if initialization timing is enabled."),
        PrintConfig(boolean.class, false, "Prints VM configuration available via JVMCI and exits."),
        PrintFlags(boolean.class, false, "Prints all JVMCI flags and exits."),
        ShowFlags(boolean.class, false, "Prints all JVMCI flags and continues."),
        TraceMethodDataFilter(String.class, null, "");

        /**
         * The prefix for system properties that are JVMCI options.
         */
        private static final String JVMCI_OPTION_PROPERTY_PREFIX = "jvmci.";

        /**
         * Marker for uninitialized flags.
         */
        private static final String UNINITIALIZED = "UNINITIALIZED";

        private final Class<?> type;
        private Object value;
        private final Object defaultValue;
        private boolean isDefault;
        private final String help;

        Option(Class<?> type, Object defaultValue, String help) {
            assert Character.isUpperCase(name().charAt(0)) : "Option name must start with upper-case letter: " + name();
            this.type = type;
            this.value = UNINITIALIZED;
            this.defaultValue = defaultValue;
            this.help = help;
        }

        @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "sentinel must be String since it's a static final in an enum")
        private Object getValue() {
            if (value == UNINITIALIZED) {
                String propertyValue = VM.getSavedProperty(JVMCI_OPTION_PROPERTY_PREFIX + name());
                if (propertyValue == null) {
                    this.value = defaultValue;
                    this.isDefault = true;
                } else {
                    if (type == boolean.class) {
                        this.value = Boolean.parseBoolean(propertyValue);
                    } else if (type == String.class) {
                        this.value = propertyValue;
                    } else {
                        throw new JVMCIError("Unexpected option type " + type);
                    }
                    this.isDefault = false;
                }
                // Saved properties should not be interned - let's be sure
                assert value != UNINITIALIZED;
            }
            return value;
        }

        /**
         * Returns the option's value as boolean.
         *
         * @return option's value
         */
        public boolean getBoolean() {
            return (boolean) getValue();
        }

        /**
         * Returns the option's value as String.
         *
         * @return option's value
         */
        public String getString() {
            return (String) getValue();
        }

        /**
         * Prints all option flags to {@code out}.
         *
         * @param out stream to print to
         */
        public static void printFlags(PrintStream out) {
            out.println("[List of JVMCI options]");
            for (Option option : values()) {
                Object value = option.getValue();
                String assign = option.isDefault ? ":=" : " =";
                out.printf("%9s %-40s %s %-14s %s%n", option.type.getSimpleName(), option, assign, value, option.help);
            }
        }
    }

    public static HotSpotJVMCIBackendFactory findFactory(String architecture) {
        for (HotSpotJVMCIBackendFactory factory : Services.load(HotSpotJVMCIBackendFactory.class)) {
            if (factory.getArchitecture().equalsIgnoreCase(architecture)) {
                return factory;
            }
        }

        throw new JVMCIError("No JVMCI runtime available for the %s architecture", architecture);
    }

    /**
     * Gets the kind of a word value on the {@linkplain #getHostJVMCIBackend() host} backend.
     */
    public static JavaKind getHostWordKind() {
        return runtime().getHostJVMCIBackend().getCodeCache().getTarget().wordJavaKind;
    }

    protected final CompilerToVM compilerToVm;

    protected final HotSpotVMConfigStore configStore;
    protected final HotSpotVMConfig config;
    private final JVMCIBackend hostBackend;

    private final JVMCICompilerFactory compilerFactory;
    private final HotSpotJVMCICompilerFactory hsCompilerFactory;
    private volatile JVMCICompiler compiler;
    protected final HotSpotJVMCIMetaAccessContext metaAccessContext;

    /**
     * Stores the result of {@link HotSpotJVMCICompilerFactory#getCompilationLevelAdjustment} so
     * that it can be read from the VM.
     */
    @SuppressWarnings("unused") private final int compilationLevelAdjustment;

    private final Map<Class<? extends Architecture>, JVMCIBackend> backends = new HashMap<>();

    private volatile List<HotSpotVMEventListener> vmEventListeners;

    private Iterable<HotSpotVMEventListener> getVmEventListeners() {
        if (vmEventListeners == null) {
            synchronized (this) {
                if (vmEventListeners == null) {
                    List<HotSpotVMEventListener> listeners = new ArrayList<>();
                    for (HotSpotVMEventListener vmEventListener : ServiceLoader.load(HotSpotVMEventListener.class)) {
                        listeners.add(vmEventListener);
                    }
                    vmEventListeners = listeners;
                }
            }
        }
        return vmEventListeners;
    }

    /**
     * Stores the result of {@link HotSpotJVMCICompilerFactory#getTrivialPrefixes()} so that it can
     * be read from the VM.
     */
    @SuppressWarnings("unused") private final String[] trivialPrefixes;

    @SuppressWarnings("try")
    @SuppressFBWarnings(value = "DM_EXIT", justification = "PrintFlags is meant to exit the VM")
    private HotSpotJVMCIRuntime() {
        compilerToVm = new CompilerToVM();

        try (InitTimer t = timer("HotSpotVMConfig<init>")) {
            configStore = new HotSpotVMConfigStore(compilerToVm);
            config = new HotSpotVMConfig(configStore);
        }

        String hostArchitecture = config.getHostArchitectureName();

        HotSpotJVMCIBackendFactory factory;
        try (InitTimer t = timer("find factory:", hostArchitecture)) {
            factory = findFactory(hostArchitecture);
        }

        try (InitTimer t = timer("create JVMCI backend:", hostArchitecture)) {
            hostBackend = registerBackend(factory.createJVMCIBackend(this, null));
        }

        metaAccessContext = new HotSpotJVMCIMetaAccessContext();

        boolean printFlags = Option.PrintFlags.getBoolean();
        boolean showFlags = Option.ShowFlags.getBoolean();
        if (printFlags || showFlags) {
            Option.printFlags(System.out);
            if (printFlags) {
                System.exit(0);
            }
        }

        if (Option.PrintConfig.getBoolean()) {
            printConfig(configStore, compilerToVm);
            System.exit(0);
        }

        compilerFactory = HotSpotJVMCICompilerConfig.getCompilerFactory();
        if (compilerFactory instanceof HotSpotJVMCICompilerFactory) {
            hsCompilerFactory = (HotSpotJVMCICompilerFactory) compilerFactory;
            trivialPrefixes = hsCompilerFactory.getTrivialPrefixes();
            switch (hsCompilerFactory.getCompilationLevelAdjustment()) {
                case None:
                    compilationLevelAdjustment = config.compLevelAdjustmentNone;
                    break;
                case ByHolder:
                    compilationLevelAdjustment = config.compLevelAdjustmentByHolder;
                    break;
                case ByFullSignature:
                    compilationLevelAdjustment = config.compLevelAdjustmentByFullSignature;
                    break;
                default:
                    compilationLevelAdjustment = config.compLevelAdjustmentNone;
                    break;
            }
        } else {
            hsCompilerFactory = null;
            trivialPrefixes = null;
            compilationLevelAdjustment = config.compLevelAdjustmentNone;
        }
    }

    private JVMCIBackend registerBackend(JVMCIBackend backend) {
        Class<? extends Architecture> arch = backend.getCodeCache().getTarget().arch.getClass();
        JVMCIBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    public ResolvedJavaType fromClass(Class<?> javaClass) {
        return metaAccessContext.fromClass(javaClass);
    }

    public HotSpotVMConfigStore getConfigStore() {
        return configStore;
    }

    public HotSpotVMConfig getConfig() {
        return config;
    }

    public CompilerToVM getCompilerToVM() {
        return compilerToVm;
    }

    public JVMCICompiler getCompiler() {
        if (compiler == null) {
            synchronized (this) {
                if (compiler == null) {
                    compiler = compilerFactory.createCompiler(this);
                }
            }
        }
        return compiler;
    }

    public JavaType lookupType(String name, HotSpotResolvedObjectType accessingType, boolean resolve) {
        Objects.requireNonNull(accessingType, "cannot resolve type without an accessing class");
        // If the name represents a primitive type we can short-circuit the lookup.
        if (name.length() == 1) {
            JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
            return fromClass(kind.toJavaClass());
        }

        // Resolve non-primitive types in the VM.
        HotSpotResolvedObjectTypeImpl hsAccessingType = (HotSpotResolvedObjectTypeImpl) accessingType;
        final HotSpotResolvedObjectTypeImpl klass = compilerToVm.lookupType(name, hsAccessingType.mirror(), resolve);

        if (klass == null) {
            assert resolve == false;
            return HotSpotUnresolvedJavaType.create(this, name);
        }
        return klass;
    }

    public JVMCIBackend getHostJVMCIBackend() {
        return hostBackend;
    }

    public <T extends Architecture> JVMCIBackend getJVMCIBackend(Class<T> arch) {
        assert arch != Architecture.class;
        return backends.get(arch);
    }

    public Map<Class<? extends Architecture>, JVMCIBackend> getJVMCIBackends() {
        return Collections.unmodifiableMap(backends);
    }

    /**
     * Called from the VM.
     */
    @SuppressWarnings({"unused"})
    private int adjustCompilationLevel(Class<?> declaringClass, String name, String signature, boolean isOsr, int level) {
        CompilationLevel curLevel;
        if (level == config.compilationLevelNone) {
            curLevel = CompilationLevel.None;
        } else if (level == config.compilationLevelSimple) {
            curLevel = CompilationLevel.Simple;
        } else if (level == config.compilationLevelLimitedProfile) {
            curLevel = CompilationLevel.LimitedProfile;
        } else if (level == config.compilationLevelFullProfile) {
            curLevel = CompilationLevel.FullProfile;
        } else if (level == config.compilationLevelFullOptimization) {
            curLevel = CompilationLevel.FullOptimization;
        } else {
            throw JVMCIError.shouldNotReachHere();
        }

        switch (hsCompilerFactory.adjustCompilationLevel(declaringClass, name, signature, isOsr, curLevel)) {
            case None:
                return config.compilationLevelNone;
            case Simple:
                return config.compilationLevelSimple;
            case LimitedProfile:
                return config.compilationLevelLimitedProfile;
            case FullProfile:
                return config.compilationLevelFullProfile;
            case FullOptimization:
                return config.compilationLevelFullOptimization;
            default:
                return level;
        }
    }

    /**
     * Called from the VM.
     */
    @SuppressWarnings({"unused"})
    private HotSpotCompilationRequestResult compileMethod(HotSpotResolvedJavaMethod method, int entryBCI, long jvmciEnv, int id) {
        CompilationRequestResult result = getCompiler().compileMethod(new HotSpotCompilationRequest(method, entryBCI, jvmciEnv, id));
        assert result != null : "compileMethod must always return something";
        HotSpotCompilationRequestResult hsResult;
        if (result instanceof HotSpotCompilationRequestResult) {
            hsResult = (HotSpotCompilationRequestResult) result;
        } else {
            Object failure = result.getFailure();
            if (failure != null) {
                boolean retry = false; // Be conservative with unknown compiler
                hsResult = HotSpotCompilationRequestResult.failure(failure.toString(), retry);
            } else {
                int inlinedBytecodes = -1;
                hsResult = HotSpotCompilationRequestResult.success(inlinedBytecodes);
            }
        }

        return hsResult;
    }

    /**
     * Shuts down the runtime.
     *
     * Called from the VM.
     */
    @SuppressWarnings({"unused"})
    private void shutdown() throws Exception {
        for (HotSpotVMEventListener vmEventListener : getVmEventListeners()) {
            vmEventListener.notifyShutdown();
        }
    }

    /**
     * Notify on completion of a bootstrap.
     *
     * Called from the VM.
     */
    @SuppressWarnings({"unused"})
    private void bootstrapFinished() throws Exception {
        for (HotSpotVMEventListener vmEventListener : getVmEventListeners()) {
            vmEventListener.notifyBootstrapFinished();
        }
    }

    /**
     * Notify on successful install into the CodeCache.
     *
     * @param hotSpotCodeCacheProvider
     * @param installedCode
     * @param compiledCode
     */
    void notifyInstall(HotSpotCodeCacheProvider hotSpotCodeCacheProvider, InstalledCode installedCode, CompiledCode compiledCode) {
        for (HotSpotVMEventListener vmEventListener : getVmEventListeners()) {
            vmEventListener.notifyInstall(hotSpotCodeCacheProvider, installedCode, compiledCode);
        }
    }

    @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING", justification = "no localization here please!")
    private static void printConfigLine(CompilerToVM vm, String format, Object... args) {
        String line = String.format(format, args);
        byte[] lineBytes = line.getBytes();
        vm.writeDebugOutput(lineBytes, 0, lineBytes.length);
        vm.flushDebugOutput();
    }

    private static void printConfig(HotSpotVMConfigStore store, CompilerToVM vm) {
        TreeMap<String, VMField> fields = new TreeMap<>(store.getFields());
        for (VMField field : fields.values()) {
            if (!field.isStatic()) {
                printConfigLine(vm, "[vmconfig:instance field] %s %s {offset=%d[0x%x]}%n", field.type, field.name, field.offset, field.offset);
            } else {
                String value = field.value == null ? "null" : String.format("%d[0x%x]", field.value, field.value);
                printConfigLine(vm, "[vmconfig:static field] %s %s = %s {address=0x%x}%n", field.type, field.name, value, field.address);
            }
        }
        TreeMap<String, VMFlag> flags = new TreeMap<>(store.getFlags());
        for (VMFlag flag : flags.values()) {
            printConfigLine(vm, "[vmconfig:flag] %s %s = %s%n", flag.type, flag.name, flag.value);
        }
        TreeMap<String, Long> addresses = new TreeMap<>(store.getAddresses());
        for (Map.Entry<String, Long> e : addresses.entrySet()) {
            printConfigLine(vm, "[vmconfig:address] %s = %d[0x%x]%n", e.getKey(), e.getValue(), e.getValue());
        }
        TreeMap<String, Long> constants = new TreeMap<>(store.getConstants());
        for (Map.Entry<String, Long> e : constants.entrySet()) {
            printConfigLine(vm, "[vmconfig:constant] %s = %d[0x%x]%n", e.getKey(), e.getValue(), e.getValue());
        }
        TreeMap<String, Long> typeSizes = new TreeMap<>(store.getTypeSizes());
        for (Map.Entry<String, Long> e : typeSizes.entrySet()) {
            printConfigLine(vm, "[vmconfig:type size] %s = %d%n", e.getKey(), e.getValue());
        }
        for (VMIntrinsicMethod e : store.getIntrinsics()) {
            printConfigLine(vm, "[vmconfig:intrinsic] %d = %s.%s %s%n", e.id, e.declaringClass, e.name, e.descriptor);
        }
    }

    public OutputStream getLogStream() {
        return new OutputStream() {

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (b == null) {
                    throw new NullPointerException();
                } else if (off < 0 || off > b.length || len < 0 || (off + len) > b.length || (off + len) < 0) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return;
                }
                compilerToVm.writeDebugOutput(b, off, len);
            }

            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void flush() throws IOException {
                compilerToVm.flushDebugOutput();
            }
        };
    }

    /**
     * Collects the current values of all JVMCI benchmark counters, summed up over all threads.
     */
    public long[] collectCounters() {
        return compilerToVm.collectCounters();
    }
}
