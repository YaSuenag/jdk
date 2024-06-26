/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <stdio.h>

extern "C" {

/*
 * A C function that takes a reference to java Object (a circular Linked list)
 * and utilizes this reference to do a java method callback to determine the
 * number of elements in the linked list
 */
JNIEXPORT jint JNICALL
Java_gc_gctests_nativeGC01_nativeGC01_nativeMethod01
(JNIEnv *env, jobject obj, jobject linked_list) {
        jclass cls, clss;
        jmethodID mid, mid2;
        int elementCount;

        /* Before doing anything force a GC by
           invoking a callback where System.gc() is called
           */
        cls  = env->GetObjectClass(obj);
        mid = env->GetMethodID(cls, "callbackGC", "()V");
        if (mid == nullptr) {
                printf("couldnt locate method callbackGC()");
                return -1;
        }
        env->CallVoidMethod(obj,mid);

        /* Now that a GC has been done, invoke the callback
           that counts the number of elements in the
           circular linked list
           */

        clss = env->GetObjectClass(linked_list);
        mid2 = env->GetMethodID(clss, "getLength", "()I");
        if (mid2 == nullptr) {
                printf("couldnt locate method getLength()");
                return -1;
        }
        elementCount  = env->CallIntMethod(linked_list, mid2);
        return elementCount;
}

}
