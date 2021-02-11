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

#include <cstdlib>
#include <dwarf.h>
#include <fcntl.h>
#include <unistd.h>
#include <elfutils/libdw.h>
#include <elfutils/known-dwarf.h>

#include "dwarf.hpp"
#include "libproc_impl.h"

static const char *op_name(int code) {
#define DWARF_ONE_KNOWN_DW_OP(NAME, CODE) \
  if (code == CODE) {                     \
    return #NAME;                         \
  }

  DWARF_ALL_KNOWN_DW_OP

#undef DWARF_ONE_KNOWN_DW_OP

  return "UNKNOWN";
}

DwarfParser::DwarfParser(struct ps_prochandle *ph, const lib_info *lib, uintptr_t ip, uintptr_t bp, uintptr_t sp)
    : _ph(ph), _lib(lib), _bp(0L), _sp(0L), _ra(0L), _error(NULL) {
  elf_version(EV_CURRENT);
  int fd = (lib->fd != -1) ? lib->fd : open(lib->name, O_RDONLY);
  Elf *elf = elf_begin(fd, ELF_C_READ, NULL);
  if (elf == NULL) {
    _error = elf_errmsg(elf_errno());
    return;
  }

  uintptr_t ip_offset = ip - lib->base;
  process_dwarf(elf, ip_offset, bp, sp);

  elf_end(elf);
  if (lib->fd == -1) {
    close(fd);
  }
}

#define ERROR_CHECK(cond)                 \
  if (cond) {                             \
    _error = dwarf_errmsg(dwarf_errno()); \
    free(frame);                          \
    dwarf_cfi_end(cfi);                   \
    return;                               \
  }

void DwarfParser::process_dwarf(Elf *elf, uintptr_t ip_offset, uintptr_t bp, uintptr_t sp) {
  Dwarf_CFI *cfi = dwarf_getcfi_elf(elf);
  if (cfi == NULL) {
    _error = dwarf_errmsg(dwarf_errno());
    return;
  }

  int result;
  Dwarf_Frame *frame = NULL;
  result = dwarf_cfi_addrframe(cfi, ip_offset, &frame);
  if (result != 0) { // fallback to use RBP
    print_debug("dwarf_cfi_addrframe() error: %s\n", dwarf_errmsg(dwarf_errno()));
    uintptr_t retval;
    bool read_result;

    read_result = _ph->ops->p_pread(_ph, bp, reinterpret_cast<char *>(&retval), sizeof(uintptr_t));
    _bp = read_result ? retval : 0L;
    read_result = _ph->ops->p_pread(_ph, bp + sizeof(uintptr_t), reinterpret_cast<char *>(&retval), sizeof(uintptr_t));
    _ra = read_result ? retval : 0L;
    read_result = _ph->ops->p_pread(_ph, bp + (sizeof(uintptr_t) * 2), reinterpret_cast<char *>(&retval), sizeof(uintptr_t));
    _sp = read_result ? retval : 0L;
    return;
  }

  int ra_reg = dwarf_frame_info(frame, NULL, NULL, NULL);
  ERROR_CHECK(ra_reg < 0)

  Dwarf_Op dummy;
  Dwarf_Op *cfa_ops = &dummy;
  size_t cfa_nops;
  result = dwarf_frame_cfa(frame, &cfa_ops, &cfa_nops);
  ERROR_CHECK((result == -1) || (cfa_nops == 0))
  uintptr_t cfa = get_cfa_address(cfa_ops, bp, sp);
  ERROR_CHECK(cfa == -1)

  _bp = get_register_value(frame, cfa, RBP, bp, sp);
  _sp = get_register_value(frame, cfa, RSP, bp, sp);
  _ra = get_register_value(frame, cfa, static_cast<DWARF_Register>(ra_reg), bp, sp);

  free(frame);
  dwarf_cfi_end(cfi);
}

uintptr_t DwarfParser::get_cfa_address(Dwarf_Op *cfa_op, uintptr_t bp, uintptr_t sp) {

  if (cfa_op->atom == DW_OP_bregx) {
    switch (cfa_op->number) {
      case RBP:
        return bp + cfa_op->number2;
      case RSP:
        return sp + cfa_op->number2;
    }
    print_debug("Unsupported dwarf register in DwarfParser::get_cfa_address(): %d\n", cfa_op->number);
    _error = "Unsupported dwarf operation in DwarfParser::get_cfa_address()";
    return -1L;
  } else if (cfa_op->atom == DW_OP_breg6) {
    return bp + cfa_op->number;
  } else if (cfa_op->atom == DW_OP_breg7) {
    return sp + cfa_op->number;
  }

  print_debug("Unsupported operation in DwarfParser::get_cfa_address(): %s\n", op_name(cfa_op->atom));
  _error = "Unsupported operation in DwarfParser::get_cfa_address()";
  return -1L;
}

uintptr_t DwarfParser::get_register_value(Dwarf_Frame *frame, uintptr_t cfa, DWARF_Register dwarf_reg, uintptr_t bp, uintptr_t sp) {
  Dwarf_Op ops_mem[3];
  Dwarf_Op *ops;
  size_t nops;
  int result = dwarf_frame_register(frame, dwarf_reg, ops_mem, &ops, &nops);
  if (result < 0) {
    print_debug("Error in DwarfParser::get_register_value(): %s\n", dwarf_errmsg(dwarf_errno()));
    _error = dwarf_errmsg(dwarf_errno());
    return 0L;
  } else if (result != 0) {
    print_debug("DwarfParser::get_register_value() supports location expression only (%d)\n", result);
    _error = "DwarfParser::get_register_value() supports location expression only";
    return 0L;
  } else if (nops == 0) {
    if (ops == NULL) { // same value
      if (dwarf_reg == RBP) {
        return bp;
      } else if (dwarf_reg == RSP) {
        return sp;
      } else {
        print_debug("DwarfParser::get_register_value() does not support same value (DWARF register: %d)\n", dwarf_reg);
        _error = "DwarfParser::get_register_value() does not support same value";
        return 0L;
      }
    } else { // undefined
      return 0L;
    }
  }

  if ((ops[0].atom == DW_OP_call_frame_cfa) && (nops == 2)) {
    uintptr_t ofs_from_cfa;
    if (ops[1].atom == DW_OP_stack_value) {
      return cfa;
    } else if (ops[1].atom == DW_OP_plus_uconst) {
      uintptr_t retval;
      uintptr_t ofs = cfa + ops[1].number;
      bool read_result = _ph->ops->p_pread(_ph, ofs, reinterpret_cast<char *>(&retval), sizeof(uintptr_t));
      if (read_result) {
        return retval;
      } else {
        print_debug("DwarfParser::get_register_value(): could not read register value: 0x%lx\n", ofs);
        _error = "DwarfParser::get_register_value(): could not read register value";
        return 0L;
      }
    } else if (ops[1].atom == DW_OP_breg6) {
      uintptr_t retval;
      bool read_result = _ph->ops->p_pread(_ph, bp + ops[1].number, reinterpret_cast<char *>(&retval), sizeof(uintptr_t));
      return read_result ? retval : 0L;
    } else if (ops[1].atom == DW_OP_breg7) {
      uintptr_t retval;
      bool read_result = _ph->ops->p_pread(_ph, sp + ops[1].number, reinterpret_cast<char *>(&retval), sizeof(uintptr_t));
      return read_result ? retval : 0L;
    } else {
      print_debug("DwarfParser::get_register_value() does not support %s in second operation\n", op_name(ops[1].atom));
      _error = "Unsupported second operation";
      return 0L;
    }
  }

  print_debug("Unknown dwarf operations:\n");
  for (int i = 0; i < nops; i++) {
    print_debug("    %s\n", op_name(ops[i].atom));
  }
  _error = "Unknown dwarf operations";

  return 0L;
}
