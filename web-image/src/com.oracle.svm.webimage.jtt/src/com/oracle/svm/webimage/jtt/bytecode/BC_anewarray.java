/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.webimage.jtt.bytecode;

import com.oracle.svm.core.NeverInline;

public class BC_anewarray {
    @SuppressWarnings("unused")
    @NeverInline(value = "Test")
    public static int test(int a) {
        final BC_anewarray[] v = new BC_anewarray[3];
        if (v != null) {
            return a;
        }
        return -1;
    }

    @NeverInline(value = "Test")
    public void run0() throws Throwable {
        System.out.println(test(0));
    }

    @NeverInline(value = "Test")
    public void run1() throws Throwable {
        System.out.println(test(1));
    }

    @NeverInline(value = "Test")
    public static void s0() {
        try {
            new BC_anewarray().run0();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s1() {
        try {
            new BC_anewarray().run1();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    public static void main(String[] args) {
        s0();
        s1();
    }
}
