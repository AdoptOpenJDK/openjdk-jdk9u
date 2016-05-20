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

package jdk.test.lib.jittester.functions;

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.visitors.Visitor;

public class StaticConstructorDefinition extends IRNode {
    public StaticConstructorDefinition(IRNode body) {
        super(body.getResultType());
        this.owner = body.getOwner();
        addChild(body);
    }

    @Override
    public long complexity() {
        IRNode body = getChild(0);
        return body != null ? body.complexity() : 0;
    }

    @Override
    public<T> T accept(Visitor<T> v) {
        return v.visit(this);
    }
}
