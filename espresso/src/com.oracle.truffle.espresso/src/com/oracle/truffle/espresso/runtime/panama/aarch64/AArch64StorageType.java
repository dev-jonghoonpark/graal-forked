/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.panama.aarch64;

import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.panama.StorageType;

// jdk.internal.foreign.abi.aarch64.AArch64Architecture.StorageType
public enum AArch64StorageType implements StorageType {
    INTEGER,
    VECTOR,
    STACK,
    PLACEHOLDER;

    private static final int STORAGE_INTEGER = 0;
    private static final int STORAGE_VECTOR = 1;
    private static final int STORAGE_STACK = 2;
    private static final int STORAGE_PLACEHOLDER = 3;

    @Override
    public boolean isPlaceholder() {
        return this == PLACEHOLDER;
    }

    @Override
    public boolean isInteger() {
        return this == INTEGER;
    }

    @Override
    public boolean isStack() {
        return this == STACK;
    }

    @Override
    public boolean isVector() {
        return this == VECTOR;
    }

    @Override
    public NativeType asNativeType(short maskOrSize, Klass type) {
        // See ArgumentShuffle::pd_generate around cpu/x86/foreignGlobals_x86_64.cpp:160
        // and jdk.internal.foreign.abi.aarch64.AArch64Architecture

        // sub-word are not needed,
        // done in MethodHandleIntrinsicNode.processReturnValue
        return switch (this) {
            case INTEGER -> {
                assert maskOrSize == AArch64Regs.REG64_MASK;
                yield switch (type.getJavaKind()) {
                    case Int -> NativeType.INT;
                    case Long -> NativeType.LONG;
                    case Object -> NativeType.POINTER;
                    case Char, Short, Byte, Boolean -> throw EspressoError.shouldNotReachHere("Unexpected sub-word in INTEGER: " + type);
                    case Double, Float, Void, ReturnAddress, Illegal -> throw EspressoError.shouldNotReachHere("Unexpected kind in INTEGER: " + type);
                };
            }
            case VECTOR -> {
                assert maskOrSize == AArch64Regs.V128_MASK;
                yield switch (type.getJavaKind()) {
                    case Float -> NativeType.FLOAT;
                    case Double -> NativeType.DOUBLE;
                    case Long, Int, Char, Short, Byte, Boolean, Object, Void, ReturnAddress, Illegal -> throw EspressoError.shouldNotReachHere("Unexpected kind in VECTOR: " + type);
                };
            }
            case STACK -> switch (type.getJavaKind()) {
                case Int -> {
                    assert maskOrSize == 4 : maskOrSize;
                    yield NativeType.INT;
                }
                case Long -> {
                    assert maskOrSize == 8 : maskOrSize;
                    yield NativeType.LONG;
                }
                case Float -> {
                    assert maskOrSize == 4 : maskOrSize;
                    yield NativeType.FLOAT;
                }
                case Double -> {
                    assert maskOrSize == 8 : maskOrSize;
                    yield NativeType.DOUBLE;
                }
                case Char, Short, Byte, Boolean -> throw EspressoError.shouldNotReachHere("Unexpected sub-word in STACK: " + type);
                case Object, Void, ReturnAddress, Illegal -> throw EspressoError.shouldNotReachHere("Unexpected kind in STACK: " + type);
            };
            default -> throw EspressoError.shouldNotReachHere("Unsupported " + this);
        };
    }

    public static StorageType get(byte id) {
        return switch (id) {
            case STORAGE_INTEGER -> INTEGER;
            case STORAGE_VECTOR -> VECTOR;
            case STORAGE_STACK -> STACK;
            case STORAGE_PLACEHOLDER -> PLACEHOLDER;
            default -> throw EspressoError.shouldNotReachHere("Unknown type: " + id);
        };
    }

    public byte getId() {
        return (byte) ordinal();
    }
}
