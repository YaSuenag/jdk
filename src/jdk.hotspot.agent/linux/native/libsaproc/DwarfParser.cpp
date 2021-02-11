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

#include <jni.h>

#include "dwarf.hpp"
#include "libproc.h"

#define CHECK_EXCEPTION if (env->ExceptionOccurred()) { return; }

static jfieldID bpField;
static jfieldID spField;
static jfieldID raField;
static jfieldID debuggerField;
static jclass ex_class;

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_DwarfParser
 * Method:    init0
 * Signature: ()V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_DwarfParser_init0
  (JNIEnv *env, jclass this_cls) {
  bpField = env->GetFieldID(this_cls, "bp", "J");
  CHECK_EXCEPTION
  spField = env->GetFieldID(this_cls, "sp", "J");
  CHECK_EXCEPTION
  raField = env->GetFieldID(this_cls, "ra", "J");
  CHECK_EXCEPTION
  debuggerField = env->GetFieldID(this_cls, "debugger", "Lsun/jvm/hotspot/debugger/linux/LinuxDebuggerLocal;");
  CHECK_EXCEPTION
  ex_class = env->FindClass("sun/jvm/hotspot/debugger/DebuggerException");
  CHECK_EXCEPTION
  ex_class = static_cast<jclass>(env->NewGlobalRef(ex_class));
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_DwarfParser
 * Method:    processDwarf
 * Signature: (JJJJ)V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_DwarfParser_processDwarf
  (JNIEnv *env, jobject this_obj, jlong libptr, jlong ip, jlong bp, jlong sp) {
  jobject debugger = env->GetObjectField(this_obj, debuggerField);
  struct ps_prochandle *ph = get_proc_handle(env, debugger);
  DwarfParser parser(ph,
                     reinterpret_cast<lib_info *>(libptr),
                     static_cast<uintptr_t>(ip),
                     static_cast<uintptr_t>(bp),
                     static_cast<uintptr_t>(sp));
  if (parser.is_error()) {
    env->ThrowNew(ex_class, parser.error_message());
    return;
  }

  uintptr_t dwarf_bp = parser.get_bp();
  uintptr_t dwarf_sp = parser.get_sp();
  uintptr_t dwarf_ra = parser.get_ra();

  if ((dwarf_ra == 0L) || (dwarf_sp == 0L)) { // final frame
    dwarf_bp = 0L;
    dwarf_sp = 0L;
    dwarf_ra = 0L;
  }

  env->SetLongField(this_obj, bpField, static_cast<jlong>(dwarf_bp));
  env->SetLongField(this_obj, spField, static_cast<jlong>(dwarf_sp));
  env->SetLongField(this_obj, raField, static_cast<jlong>(dwarf_ra));
}
