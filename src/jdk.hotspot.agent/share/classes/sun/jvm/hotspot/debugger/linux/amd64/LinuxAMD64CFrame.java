/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.amd64.*;
import sun.jvm.hotspot.debugger.linux.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.cdbg.basic.*;

final public class LinuxAMD64CFrame extends BasicCFrame {

    public static LinuxAMD64CFrame getTopFrame(LinuxDebugger dbg, Address rip, ThreadContext context) {
        Address rbp = context.getRegisterAsAddress(AMD64ThreadContext.RBP);
        Address rsp = context.getRegisterAsAddress(AMD64ThreadContext.RSP);
        DwarfParser dwarf = null;
        Address libptr = dbg.findLibPtrByAddress(rip);
        if (libptr == null) { // Java frame
            rbp = context.getRegisterAsAddress(AMD64ThreadContext.RBP);
        } else { // Native frame
            dwarf = ((LinuxDebuggerLocal)dbg).createDwarfParser(libptr, rip, rbp, rsp);
            rbp = dwarf.getBasePointer();
        }
        return new LinuxAMD64CFrame(dbg, rbp, rsp, rip, dwarf);
    }

    private LinuxAMD64CFrame(LinuxDebugger dbg, Address rbp, Address rsp, Address rip, DwarfParser dwarf) {
        super(dbg.getCDebugger());
        this.rbp = rbp;
        this.rsp = rsp;
        this.rip = rip;
        this.dbg = dbg;
        this.dwarf = dwarf;
    }

    // override base class impl to avoid ELF parsing
    public ClosestSymbol closestSymbolToPC() {
        // try native lookup in debugger.
        return dbg.lookup(dbg.getAddressValue(pc()));
    }

    public Address pc() {
        return rip;
    }

    public Address localVariableBase() {
        return rbp;
    }

    @Override
    public CFrame sender(ThreadProxy thread) {
        Address nextRIP;
        if (isNative()) { // current frame is in native
            nextRIP = dwarf.getReturnAddress();
        } else { // current frame is in Java
            if (rbp == null) {
                return null;
            }
            nextRIP = rbp.getAddressAt(ADDRESS_SIZE);
        }
        if (nextRIP == null) {
            return null;
        }

        Address nextRBP, nextRSP;
        Address libptr = dbg.findLibPtrByAddress(nextRIP);
        DwarfParser nextDwarf = null;
        if (libptr == null) { // next frame is in Java
            if (isNative()) { // current frame is in native
                nextRBP = dwarf.getBasePointer();
                nextRSP = dwarf.getStackPointer();
            } else { // current frame is in Java
                nextRBP = rbp.getAddressAt(0);
                nextRSP = rbp.getAddressAt(ADDRESS_SIZE * 2);
            }
        } else { // next frame is in native
            if (isNative()) { // current frame is in native
                nextRSP = dwarf.getStackPointer();
                nextDwarf = ((LinuxDebuggerLocal)dbg).createDwarfParser(libptr, nextRIP, dwarf.getBasePointer(), nextRSP);
                nextRBP = nextDwarf.getBasePointer();
            } else { // current frame is in Java
                nextRBP = rbp.getAddressAt(0);
                nextRSP = rbp.getAddressAt(ADDRESS_SIZE * 2);
            }
        }
        return new LinuxAMD64CFrame(dbg, nextRBP, nextRSP, nextRIP, nextDwarf);
    }

    public boolean isNative() {
        return dwarf != null;
    }

    // package/class internals only
    private static final int ADDRESS_SIZE = 8;
    private Address rip;
    private Address rbp;
    private Address rsp;
    private LinuxDebugger dbg;
    private DwarfParser dwarf;
}
