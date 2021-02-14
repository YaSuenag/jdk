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

#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <sys/ptrace.h>
#include <libunwind.h>
#include <libunwind-coredump.h>
#include <libunwind-ptrace.h>

#include "libproc.h"


#define CHECK_EXCEPTION if (env->ExceptionOccurred()) { return; }

static jfieldID unwAddrSpace_ID = 0;
static jfieldID unwArgs_ID = 0;
static jfieldID unwCursor_ID = 0;
static jfieldID debugger_ID = 0;

static jclass debuggerClass = NULL;
static jmethodID debugger_isCore_ID = 0;
static jmethodID debugger_getCoreName_ID = 0;
static jmethodID debugger_getProcHandle_ID = 0;

static jclass debuggerExceptionClass = NULL;

thread_local static jlong local_ip;
thread_local static jlong local_sp;
thread_local static jlong local_bp;
static unw_accessors_t accessor_for_sa;

static int access_reg(unw_addr_space_t as, unw_regnum_t reg, unw_word_t *val, int write, void *arg) {
  int ret = 0;
  if (write) {
    return -UNW_EREADONLYREG;
  }
  switch (reg) {
    case UNW_REG_IP:
      *val = local_ip;
      break;
    case UNW_REG_SP:
      *val = local_sp;
      break;
    case UNW_TDEP_BP:
      *val = local_bp;
      break;
    default:
      ret = -UNW_EBADREG;
  }
  return ret;
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder
 * Method:    init0
 * Signature: ()V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder_init0
  (JNIEnv *env, jclass cls) {
  unwAddrSpace_ID = env->GetFieldID(cls, "unwAddrSpace", "J");
  CHECK_EXCEPTION
  unwArgs_ID = env->GetFieldID(cls, "unwArgs", "J");
  CHECK_EXCEPTION
  unwCursor_ID = env->GetFieldID(cls, "unwCursor", "J");
  CHECK_EXCEPTION
  debugger_ID = env->GetFieldID(cls, "debugger", "Lsun/jvm/hotspot/debugger/linux/LinuxDebuggerLocal;");
  CHECK_EXCEPTION

  debuggerClass = env->FindClass("sun/jvm/hotspot/debugger/linux/LinuxDebuggerLocal");
  CHECK_EXCEPTION
  debuggerClass = (jclass)env->NewGlobalRef(debuggerClass);
  CHECK_EXCEPTION
  debugger_isCore_ID = env->GetMethodID(debuggerClass, "isCore", "()Z");
  CHECK_EXCEPTION
  debugger_getCoreName_ID = env->GetMethodID(debuggerClass, "getCoreName", "()Ljava/lang/String;");
  CHECK_EXCEPTION
  debugger_getProcHandle_ID = env->GetMethodID(debuggerClass, "getProcHandle", "()J");
  CHECK_EXCEPTION

  debuggerExceptionClass = env->FindClass("sun/jvm/hotspot/debugger/DebuggerException");
  CHECK_EXCEPTION
  debuggerExceptionClass = (jclass)env->NewGlobalRef(debuggerExceptionClass);
}

static bool select_thread(struct UCD_info *ui, int lwp_id) {
  for (int i = 0; i < _UCD_get_num_threads(ui); i++) {
    _UCD_select_thread(ui, i);
    if (_UCD_get_pid(ui) == lwp_id) {
      return true;
    }
  }
  return false;
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder
 * Method:    initCursor
 * Signature: (IJJJ)V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder_initCursor
  (JNIEnv *env, jobject this_obj, jint lwp_id, jlong ip, jlong sp, jlong bp) {
  jobject debugger_obj = env->GetObjectField(this_obj, debugger_ID);
  jboolean is_core = env->CallBooleanMethod(debugger_obj, debugger_isCore_ID);
  void *args;
  if (is_core) {
    jstring coreName = static_cast<jstring>(env->CallObjectMethod(debugger_obj, debugger_getCoreName_ID));
    const char *corename_utf = env->GetStringUTFChars(coreName, NULL);
    args = _UCD_create(corename_utf);
    env->ReleaseStringUTFChars(coreName, corename_utf);
    if (!select_thread(reinterpret_cast<struct UCD_info *>(args), lwp_id)) {
      env->ThrowNew(debuggerExceptionClass, "Could not find thread in unwinder");
      return;
    }
    memcpy(&accessor_for_sa, &_UCD_accessors, sizeof(unw_accessors_t));
  } else {
    args = _UPT_create(lwp_id);
    memcpy(&accessor_for_sa, &_UPT_accessors, sizeof(unw_accessors_t));
  }

  accessor_for_sa.access_reg = &access_reg;
  unw_addr_space_t as = unw_create_addr_space(&accessor_for_sa, 0);
  if (as == NULL) {
    env->ThrowNew(debuggerExceptionClass, "Could not create address space for unwinder");
    return;
  }

  unw_cursor_t *cursor = reinterpret_cast<unw_cursor_t *>(malloc(sizeof(unw_cursor_t)));
  if (cursor == NULL) {
    env->ThrowNew(debuggerExceptionClass, "Could not allocate memory for cursor of unwinder");
    return;
  }

  local_ip = ip;
  local_sp = sp;
  local_bp = bp;

  int result = unw_init_remote(cursor, as, args);
  if (result != 0) {
    const char *reason;
    switch (result) {
      case -UNW_EINVAL:
        reason = "initialize unwinder: UNW_EINVAL";
        break;
      case -UNW_EUNSPEC:
        reason = "initialize unwinder: UNW_EUNSPEC";
        break;
      case -UNW_EBADREG:
        reason = "initialize unwinder: UNW_EBADREG";
        break;
      default:
        reason = "initialize unwinder: Unknown";
    }
    env->ThrowNew(debuggerExceptionClass, reason);
    return;
  }

  if (is_core) {
    jlong prochandle = env->CallLongMethod(debugger_obj, debugger_getProcHandle_ID);
    struct ps_prochandle *ph = reinterpret_cast<struct ps_prochandle *>(prochandle);
    for (int i = 0; i < get_num_libs(ph); i++) {
      uintptr_t base = get_lib_base(ph, i);
      const char *libname = get_lib_name(ph, i);
      if (_UCD_add_backing_file_at_vaddr(reinterpret_cast<struct UCD_info *>(args), base, libname) < 0) {
        env->ThrowNew(debuggerExceptionClass, "_UCD_add_backing_file_at_vaddr");
        return;
      }
    }
  }

  env->SetLongField(this_obj, unwAddrSpace_ID, reinterpret_cast<jlong>(as));
  env->SetLongField(this_obj, unwArgs_ID, reinterpret_cast<jlong>(args));
  env->SetLongField(this_obj, unwCursor_ID, reinterpret_cast<jlong>(cursor));
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder
 * Method:    disposeInner
 * Signature: (Lsun/jvm/hotspot/debugger/linux/LinuxDebuggerLocal;JJJ)V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder_disposeInner
  (JNIEnv *env, jclass cls, jobject debugger, jlong unwAddrSpace, jlong unwArgs, jlong unwCursor) {
  jboolean is_core = env->CallBooleanMethod(debugger, debugger_isCore_ID);
  if (is_core) {
    _UCD_destroy(reinterpret_cast<struct UCD_info *>(unwArgs));
  } else {
    _UPT_destroy(reinterpret_cast<void *>(unwArgs));
  }
  unw_destroy_addr_space(reinterpret_cast<unw_addr_space_t>(unwAddrSpace));
  free(reinterpret_cast<unw_cursor_t *>(unwCursor));
}

static jlong get_reg_inner(JNIEnv *env, jobject this_obj, unw_regnum_t reg) {
  unw_cursor_t *cursor = reinterpret_cast<unw_cursor_t *>(env->GetLongField(this_obj, unwCursor_ID));
  unw_word_t val;
  int ret = unw_get_reg(cursor, reg, &val);
  if (ret != 0) {
    env->ThrowNew(debuggerExceptionClass, "Could not get register value from unwinder");
    return -1L;
  }
  return static_cast<jlong>(val);
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder
 * Method:    getBP0
 * Signature: ()J
 */
extern "C"
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder_getBP0
  (JNIEnv *env, jobject this_obj) {
  return get_reg_inner(env, this_obj, UNW_TDEP_BP);
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder
 * Method:    getSP0
 * Signature: ()J
 */
extern "C"
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder_getSP0
  (JNIEnv *env, jobject this_obj) {
  return get_reg_inner(env, this_obj, UNW_REG_SP);
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder
 * Method:    getIP0
 * Signature: ()J
 */
extern "C"
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder_getIP0
  (JNIEnv *env, jobject this_obj) {
  return get_reg_inner(env, this_obj, UNW_REG_IP);
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder
 * Method:    step
 * Signature: ()Z
 */
extern "C"
JNIEXPORT jboolean JNICALL Java_sun_jvm_hotspot_debugger_linux_amd64_NativeUnwinder_step
  (JNIEnv *env, jobject this_obj) {
  unw_cursor_t *cursor = reinterpret_cast<unw_cursor_t *>(env->GetLongField(this_obj, unwCursor_ID));
  int result = unw_step(cursor);
  if (result < 0) { // Error
    const char *reason;
    switch (result) {
      case -UNW_EUNSPEC:
        reason = "unwind: An unspecified error occurred.";
        break;
      case -UNW_ENOINFO:
        reason = "unwind: Libunwind was unable to locate the unwind-info needed to complete the operation.";
        break;
      case -UNW_EBADVERSION:
        reason = "unwind: The unwind-info needed to complete the operation has a version or a format that is not understood by libunwind.";
        break;
      case -UNW_EINVALIDIP:
        reason = "unwind: The instruction-pointer (``program-counter'') of the next stack frame is invalid (e.g., not properly aligned).";
        break;
      case -UNW_EBADFRAME:
        reason = "unwind: The next stack frame is invalid.";
        break;
      case -UNW_ESTOPUNWIND:
        reason = "unwind: Returned if a call to find_proc_info() returned -UNW_ESTOPUNWIND.";
        break;
      case -UNW_EINVAL:
        reason = "unwind: unsupported operation or bad value";
        break;
      default:
        reason = "unwind: Unknown";
    }
    env->ThrowNew(debuggerExceptionClass, reason);
    return JNI_FALSE;
  } else if (result == 0) { // Last frame
    return JNI_FALSE;
  } else {
    return JNI_TRUE;
  }
}
