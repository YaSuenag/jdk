/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, NTT DATA.
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

import java.lang.ref.Cleaner;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.linux.LinuxDebuggerLocal;

public class NativeUnwinder {

    private static final Cleaner cleaner = Cleaner.create();

    // This value is for libunwind, it will be set in native.
    private long unwAddrSpace;

    // This value is for libunwind, it will be set in native.
    private long unwArgs;

    // This value is for libunwind, it will be set in native.
    private long unwCursor;

    private final LinuxDebuggerLocal debugger;

    static {
      init0();
    }

    private static native void init0();

    public static NativeUnwinder newInstance(int lwp_id, Address ip, Address sp, Address bp, LinuxDebuggerLocal debugger) {
        // Debuggee is already attached from worker thread in LinuxDebuggerLocal.
        // libunwind uses ptrace(), so we need to create instance on worker thread.
        var unwinder = debugger.createUnwinder(lwp_id, ip, sp, bp, debugger);
        long as = unwinder.unwAddrSpace;
        long args = unwinder.unwArgs;
        long cursor = unwinder.unwCursor;
        cleaner.register(unwinder, () -> disposeInner(debugger, as, args, cursor));
        return unwinder;
    }

    private native void initCursor(int lwp_id, long ip, long sp, long bp);

    public NativeUnwinder(int lwp_id, Address ip, Address sp, Address bp, LinuxDebuggerLocal debugger) {
        this.debugger = debugger;
        long bpval = (bp == null) ? 0L : bp.asLongValue(); // RBP might be used as GPR
        initCursor(lwp_id, ip.asLongValue(), sp.asLongValue(), bpval);
    }

    private static native void disposeInner(LinuxDebuggerLocal debugger, long unwAddrSpace, long unwArgs, long unwCursor);

    public void dispose() {
        disposeInner(debugger, unwAddrSpace, unwArgs, unwCursor);
    }

    public native long getBP0();

    public Address getBP() {
        // Debuggee is already attached from worker thread in LinuxDebuggerLocal.
        // libunwind might use ptrace() to get RBP value, so we need to perform
        // on worker thread.
        return debugger.newAddress(debugger.getBPFromUnwinder(this));
    }

    private native long getSP0();

    public Address getSP() {
        return debugger.newAddress(getSP0());
    }

    private native long getIP0();

    public Address getIP() {
        return debugger.newAddress(getIP0());
    }

    /**
     * Debuggee is already attached from worker thread in LinuxDebuggerLocal.
     * libunwind might use ptrace() in unw_step(), so we need to perform
     * on worker thread.
     * See LinuxDebuggerLocal::step
     * @return true if more call frames are available, otherwise return false.
     */
    public native boolean step();

}
