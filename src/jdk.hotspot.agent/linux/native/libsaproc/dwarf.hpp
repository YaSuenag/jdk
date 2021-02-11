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

#ifndef _DWARF_HPP_
#define _DWARF_HPP_

#include <elfutils/libdw.h>

#include "libproc_impl.h"

/*
 * from System V Application Binary Interface
 *        AMD64 Architecture Processor Supplement
 *          Figure 3.38: DWARF Register Number Mapping
 * https://software.intel.com/sites/default/files/article/402129/mpx-linux64-abi.pdf
 */
enum DWARF_Register {
  RAX,
  RDX,
  RCX,
  RBX,
  RSI,
  RDI,
  RBP,
  RSP,
  R8,
  R9,
  R10,
  R11,
  R12,
  R13,
  R14,
  R15,
  RA,
  MAX_VALUE
};

class DwarfParser {
  private:
    struct ps_prochandle *_ph;
    const lib_info *_lib;
    uintptr_t _bp;
    uintptr_t _sp;
    uintptr_t _ra;
    const char *_error;

    void process_dwarf(Elf *elf, uintptr_t ip_offset, uintptr_t bp, uintptr_t sp);
    uintptr_t get_cfa_address(Dwarf_Op *cfa_op, uintptr_t bp, uintptr_t sp);
    uintptr_t get_register_value(Dwarf_Frame *frame, uintptr_t cfa, DWARF_Register dwarf_reg, uintptr_t bp, uintptr_t sp);

  public:
    DwarfParser(struct ps_prochandle *ph, const lib_info *lib, uintptr_t ip, uintptr_t bp, uintptr_t sp);
    ~DwarfParser() {}

    uintptr_t get_bp() {
      return _bp;
    }

    uintptr_t get_sp() {
      return _sp;
    }

    uintptr_t get_ra() {
      return _ra;
    }

    bool is_error() {
      return _error != NULL;
    }

    const char *error_message() {
      return _error;
    }
};

#endif //_DWARF_HPP_
