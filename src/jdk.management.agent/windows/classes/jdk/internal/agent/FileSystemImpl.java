/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.agent;

import java.io.File;
import java.io.IOException;

/*
 * Windows implementation of sun.management.FileSystem
 */
@SuppressWarnings({"removal", "restricted"})
public class FileSystemImpl extends FileSystem {

    public boolean supportsFileSecurity(File f) throws IOException {
        String path = f.getAbsolutePath();
        if (path.indexOf(0) >= 0) {
            throw new IOException("illegal filename");
        }
        return isSecuritySupported0(f.getAbsolutePath());
    }

    public boolean isAccessUserOnly(File f) throws IOException {
        String path = f.getAbsolutePath();
        if (path.indexOf(0) >= 0) {
            throw new IOException("illegal filename");
        }
        if (!isSecuritySupported0(path)) {
            throw new UnsupportedOperationException("File system does not support file security");
        }
        return isAccessUserOnly0(path);
    }

    // Native methods

    static native void init0();

    static native boolean isSecuritySupported0(String path) throws IOException;

    static native boolean isAccessUserOnly0(String path) throws IOException;

    // Initialization

    static {
        System.loadLibrary("management_agent");
        init0();
    }
}
