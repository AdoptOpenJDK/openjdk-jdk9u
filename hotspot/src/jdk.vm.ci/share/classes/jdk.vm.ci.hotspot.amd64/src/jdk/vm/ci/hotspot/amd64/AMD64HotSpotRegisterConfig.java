/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class AMD64HotSpotRegisterConfig implements RegisterConfig {

    private final TargetDescription target;

    private final Register[] allocatable;

    private final int maxFrameSize;

    /**
     * The caller saved registers always include all parameter registers.
     */
    private final Register[] callerSaved;

    private final boolean allAllocatableAreCallerSaved;

    private final RegisterAttributes[] attributesMap;

    public int getMaximumFrameSize() {
        return maxFrameSize;
    }

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable.clone();
    }

    @Override
    public Register[] filterAllocatableRegisters(PlatformKind kind, Register[] registers) {
        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : registers) {
            if (target.arch.canStoreValue(reg.getRegisterCategory(), kind)) {
                list.add(reg);
            }
        }

        Register[] ret = list.toArray(new Register[list.size()]);
        return ret;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap.clone();
    }

    private final Register[] javaGeneralParameterRegisters;
    private final Register[] nativeGeneralParameterRegisters;
    private final Register[] xmmParameterRegisters = {xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7};

    /*
     * Some ABIs (e.g. Windows) require a so-called "home space", that is a save area on the stack
     * to store the argument registers
     */
    private final boolean needsNativeStackHomeSpace;

    private static final Register[] reservedRegisters = {rsp, r15};

    private static Register[] initAllocatable(Architecture arch, boolean reserveForHeapBase) {
        Register[] allRegisters = arch.getAvailableValueRegisters();
        Register[] registers = new Register[allRegisters.length - reservedRegisters.length - (reserveForHeapBase ? 1 : 0)];
        List<Register> reservedRegistersList = Arrays.asList(reservedRegisters);

        int idx = 0;
        for (Register reg : allRegisters) {
            if (reservedRegistersList.contains(reg)) {
                // skip reserved registers
                continue;
            }
            if (reserveForHeapBase && reg.equals(r12)) {
                // skip heap base register
                continue;
            }

            registers[idx++] = reg;
        }

        assert idx == registers.length;
        return registers;
    }

    public AMD64HotSpotRegisterConfig(TargetDescription target, HotSpotVMConfig config) {
        this(target, config, initAllocatable(target.arch, config.useCompressedOops));
        assert callerSaved.length >= allocatable.length;
    }

    public AMD64HotSpotRegisterConfig(TargetDescription target, HotSpotVMConfig config, Register[] allocatable) {
        this.target = target;
        this.maxFrameSize = config.maxFrameSize;

        if (config.windowsOs) {
            javaGeneralParameterRegisters = new Register[]{rdx, r8, r9, rdi, rsi, rcx};
            nativeGeneralParameterRegisters = new Register[]{rcx, rdx, r8, r9};
            this.needsNativeStackHomeSpace = true;
        } else {
            javaGeneralParameterRegisters = new Register[]{rsi, rdx, rcx, r8, r9, rdi};
            nativeGeneralParameterRegisters = new Register[]{rdi, rsi, rdx, rcx, r8, r9};
            this.needsNativeStackHomeSpace = false;
        }

        this.allocatable = allocatable;
        Set<Register> callerSaveSet = new HashSet<>();
        Collections.addAll(callerSaveSet, allocatable);
        Collections.addAll(callerSaveSet, xmmParameterRegisters);
        Collections.addAll(callerSaveSet, javaGeneralParameterRegisters);
        Collections.addAll(callerSaveSet, nativeGeneralParameterRegisters);
        callerSaved = callerSaveSet.toArray(new Register[callerSaveSet.size()]);

        allAllocatableAreCallerSaved = true;
        attributesMap = RegisterAttributes.createMap(this, target.arch.getRegisters());
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        return callerSaved;
    }

    @Override
    public Register[] getCalleeSaveRegisters() {
        return null;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return allAllocatableAreCallerSaved;
    }

    @Override
    public Register getRegisterForRole(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        HotSpotCallingConventionType hotspotType = (HotSpotCallingConventionType) type;
        if (type == HotSpotCallingConventionType.NativeCall) {
            return callingConvention(nativeGeneralParameterRegisters, returnType, parameterTypes, hotspotType, valueKindFactory);
        }
        // On x64, parameter locations are the same whether viewed
        // from the caller or callee perspective
        return callingConvention(javaGeneralParameterRegisters, returnType, parameterTypes, hotspotType, valueKindFactory);
    }

    @Override
    public Register[] getCallingConventionRegisters(Type type, JavaKind kind) {
        HotSpotCallingConventionType hotspotType = (HotSpotCallingConventionType) type;
        switch (kind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Object:
                return hotspotType == HotSpotCallingConventionType.NativeCall ? nativeGeneralParameterRegisters : javaGeneralParameterRegisters;
            case Float:
            case Double:
                return xmmParameterRegisters;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, HotSpotCallingConventionType type,
                    ValueKindFactory<?> valueKindFactory) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentXMM = 0;
        int currentStackOffset = type == HotSpotCallingConventionType.NativeCall && needsNativeStackHomeSpace ? generalParameterRegisters.length * target.wordSize : 0;

        for (int i = 0; i < parameterTypes.length; i++) {
            final JavaKind kind = parameterTypes[i].getJavaKind().getStackKind();

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    if (currentGeneral < generalParameterRegisters.length) {
                        Register register = generalParameterRegisters[currentGeneral++];
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    }
                    break;
                case Float:
                case Double:
                    if (currentXMM < xmmParameterRegisters.length) {
                        Register register = xmmParameterRegisters[currentXMM++];
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                ValueKind<?> valueKind = valueKindFactory.getValueKind(kind);
                locations[i] = StackSlot.get(valueKind, currentStackOffset, !type.out);
                currentStackOffset += Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
            }
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
        AllocatableValue returnLocation = returnKind == JavaKind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(valueKindFactory.getValueKind(returnKind.getStackKind()));
        return new CallingConvention(currentStackOffset, returnLocation, locations);
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
            case Object:
                return rax;
            case Float:
            case Double:
                return xmm0;
            case Void:
            case Illegal:
                return null;
            default:
                throw new UnsupportedOperationException("no return register for type " + kind);
        }
    }

    @Override
    public Register getFrameRegister() {
        return rsp;
    }

    @Override
    public String toString() {
        return String.format("Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" + "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n");
    }
}
