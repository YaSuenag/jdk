/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

   public static LinuxAMD64CFrame getTopFrame(LinuxDebuggerLocal dbg, Address rip, ThreadContext context) {
      Address libptr = dbg.findLibPtrByAddress(rip);
      Address rsp = context.getRegisterAsAddress(AMD64ThreadContext.RSP);
      Address rbp = context.getRegisterAsAddress(AMD64ThreadContext.RBP);
      NativeUnwinder unwinder = null;

      if (libptr != null) { // Native frame
        unwinder = NativeUnwinder.newInstance(((LinuxAMD64ThreadContext)context).getLwpId(), rip, rsp, rbp, dbg);
          return new LinuxAMD64CFrame(dbg, rbp, rip, unwinder);
      } else if (rbp == null) { // Java frame, but RBP is NULL
          return null;
      } else { // Valid Java frame
          return new LinuxAMD64CFrame(dbg, rbp, rip, null);
      }
   }

   private LinuxAMD64CFrame(LinuxDebuggerLocal dbg, Address rbp, Address rip, NativeUnwinder unwinder) {
      super(dbg.getCDebugger());
      this.rbp = rbp;
      this.rip = rip;
      this.dbg = dbg;
      this.unwinder = unwinder;
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
     LinuxAMD64ThreadContext context = (LinuxAMD64ThreadContext)thread.getContext();

     Address nextPC;
     Address nextBP;
     Address nextSP;
     if (unwinder != null) { // Current frame is in Native
       if (!dbg.step(unwinder)) {
         return null;
       }
       nextPC = unwinder.getIP();
       nextBP = unwinder.getBP();
       nextSP = unwinder.getSP();
     } else { // Current frame is in Java
       nextPC = rbp.getAddressAt(ADDRESS_SIZE);
       nextBP = rbp.getAddressAt(0);
       nextSP = rbp.addOffsetTo(ADDRESS_SIZE * 2);
     }

     Address libptr = dbg.findLibPtrByAddress(nextPC);
     NativeUnwinder nextUnwinder;
     if (libptr != null) { // Native frame
       nextUnwinder = (unwinder == null) ? NativeUnwinder.newInstance(context.getLwpId(), nextPC, nextSP, nextBP, dbg) : unwinder;
     } else { // Java frame
       nextUnwinder = null;
     }

     return (nextBP == null) ? null : new LinuxAMD64CFrame(dbg, nextBP, nextPC, nextUnwinder);
   }

   // package/class internals only
   private static final int ADDRESS_SIZE = 8;
   private Address rip;
   private Address rbp;
   private LinuxDebuggerLocal dbg;
   private NativeUnwinder unwinder;
}
