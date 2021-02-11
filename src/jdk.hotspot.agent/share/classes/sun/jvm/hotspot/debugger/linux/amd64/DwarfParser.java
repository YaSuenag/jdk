/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, NTT DATA.
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

package sun.jvm.hotspot.debugger.linux.amd64;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.linux.LinuxDebuggerLocal;

public class DwarfParser {

    // They will be set from processDwarf()
    private long bp;
    private long sp;
    private long ra;

    private final LinuxDebuggerLocal debugger;

    private static native void init0();
    private native void processDwarf(long lib, long ip, long bp, long sp);

    static {
        init0();
    }

    public DwarfParser(Address lib, Address ip, Address bp, Address sp, LinuxDebuggerLocal debugger) {
        this.debugger = debugger;
        processDwarf(lib.asLongValue(),
                     ip.asLongValue(),
                     bp == null ? 0L : bp.asLongValue(),
                     sp.asLongValue());
    }

    public Address getBasePointer() {
        return debugger.newAddress(bp);
    }

    public Address getStackPointer() {
        return debugger.newAddress(sp);
    }

    public Address getReturnAddress() {
        return debugger.newAddress(ra);
    }

}
