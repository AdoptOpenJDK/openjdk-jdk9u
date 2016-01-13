/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/templateInterpreterGenerator.hpp"
#include "interpreter/templateTable.hpp"
#include "oops/arrayOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/debug.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

#define __ _masm->

#ifdef _WIN64
address AbstractInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();

  // rbx: method
  // r14: pointer to locals
  // c_rarg3: first stack arg - wordSize
  __ mov(c_rarg3, rsp);
  // adjust rsp
  __ subptr(rsp, 4 * wordSize);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::slow_signature_handler),
             rbx, r14, c_rarg3);

  // rax: result handler

  // Stack layout:
  // rsp: 3 integer or float args (if static first is unused)
  //      1 float/double identifiers
  //        return address
  //        stack args
  //        garbage
  //        expression stack bottom
  //        bcp (NULL)
  //        ...

  // Do FP first so we can use c_rarg3 as temp
  __ movl(c_rarg3, Address(rsp, 3 * wordSize)); // float/double identifiers

  for ( int i= 0; i < Argument::n_int_register_parameters_c-1; i++ ) {
    XMMRegister floatreg = as_XMMRegister(i+1);
    Label isfloatordouble, isdouble, next;

    __ testl(c_rarg3, 1 << (i*2));      // Float or Double?
    __ jcc(Assembler::notZero, isfloatordouble);

    // Do Int register here
    switch ( i ) {
      case 0:
        __ movl(rscratch1, Address(rbx, Method::access_flags_offset()));
        __ testl(rscratch1, JVM_ACC_STATIC);
        __ cmovptr(Assembler::zero, c_rarg1, Address(rsp, 0));
        break;
      case 1:
        __ movptr(c_rarg2, Address(rsp, wordSize));
        break;
      case 2:
        __ movptr(c_rarg3, Address(rsp, 2 * wordSize));
        break;
      default:
        break;
    }

    __ jmp (next);

    __ bind(isfloatordouble);
    __ testl(c_rarg3, 1 << ((i*2)+1));     // Double?
    __ jcc(Assembler::notZero, isdouble);

// Do Float Here
    __ movflt(floatreg, Address(rsp, i * wordSize));
    __ jmp(next);

// Do Double here
    __ bind(isdouble);
    __ movdbl(floatreg, Address(rsp, i * wordSize));

    __ bind(next);
  }


  // restore rsp
  __ addptr(rsp, 4 * wordSize);

  __ ret(0);

  return entry;
}
#else
address AbstractInterpreterGenerator::generate_slow_signature_handler() {
  address entry = __ pc();

  // rbx: method
  // r14: pointer to locals
  // c_rarg3: first stack arg - wordSize
  __ mov(c_rarg3, rsp);
  // adjust rsp
  __ subptr(rsp, 14 * wordSize);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::slow_signature_handler),
             rbx, r14, c_rarg3);

  // rax: result handler

  // Stack layout:
  // rsp: 5 integer args (if static first is unused)
  //      1 float/double identifiers
  //      8 double args
  //        return address
  //        stack args
  //        garbage
  //        expression stack bottom
  //        bcp (NULL)
  //        ...

  // Do FP first so we can use c_rarg3 as temp
  __ movl(c_rarg3, Address(rsp, 5 * wordSize)); // float/double identifiers

  for (int i = 0; i < Argument::n_float_register_parameters_c; i++) {
    const XMMRegister r = as_XMMRegister(i);

    Label d, done;

    __ testl(c_rarg3, 1 << i);
    __ jcc(Assembler::notZero, d);
    __ movflt(r, Address(rsp, (6 + i) * wordSize));
    __ jmp(done);
    __ bind(d);
    __ movdbl(r, Address(rsp, (6 + i) * wordSize));
    __ bind(done);
  }

  // Now handle integrals.  Only do c_rarg1 if not static.
  __ movl(c_rarg3, Address(rbx, Method::access_flags_offset()));
  __ testl(c_rarg3, JVM_ACC_STATIC);
  __ cmovptr(Assembler::zero, c_rarg1, Address(rsp, 0));

  __ movptr(c_rarg2, Address(rsp, wordSize));
  __ movptr(c_rarg3, Address(rsp, 2 * wordSize));
  __ movptr(c_rarg4, Address(rsp, 3 * wordSize));
  __ movptr(c_rarg5, Address(rsp, 4 * wordSize));

  // restore rsp
  __ addptr(rsp, 14 * wordSize);

  __ ret(0);

  return entry;
}
#endif


//
// Various method entries
//

address TemplateInterpreterGenerator::generate_math_entry(AbstractInterpreter::MethodKind kind) {

  // rbx,: Method*
  // rcx: scratrch
  // r13: sender sp

  if (!InlineIntrinsics) return NULL; // Generate a vanilla entry

  address entry_point = __ pc();

  // These don't need a safepoint check because they aren't virtually
  // callable. We won't enter these intrinsics from compiled code.
  // If in the future we added an intrinsic which was virtually callable
  // we'd have to worry about how to safepoint so that this code is used.

  // mathematical functions inlined by compiler
  // (interpreter must provide identical implementation
  // in order to avoid monotonicity bugs when switching
  // from interpreter to compiler in the middle of some
  // computation)
  //
  // stack: [ ret adr ] <-- rsp
  //        [ lo(arg) ]
  //        [ hi(arg) ]
  //

  // Note: For JDK 1.2 StrictMath doesn't exist and Math.sin/cos/sqrt are
  //       native methods. Interpreter::method_kind(...) does a check for
  //       native methods first before checking for intrinsic methods and
  //       thus will never select this entry point. Make sure it is not
  //       called accidentally since the SharedRuntime entry points will
  //       not work for JDK 1.2.
  //
  // We no longer need to check for JDK 1.2 since it's EOL'ed.
  // The following check existed in pre 1.6 implementation,
  //    if (Universe::is_jdk12x_version()) {
  //      __ should_not_reach_here();
  //    }
  // Universe::is_jdk12x_version() always returns false since
  // the JDK version is not yet determined when this method is called.
  // This method is called during interpreter_init() whereas
  // JDK version is only determined when universe2_init() is called.

  // Note: For JDK 1.3 StrictMath exists and Math.sin/cos/sqrt are
  //       java methods.  Interpreter::method_kind(...) will select
  //       this entry point for the corresponding methods in JDK 1.3.
  // get argument

  if (kind == Interpreter::java_lang_math_sqrt) {
    __ sqrtsd(xmm0, Address(rsp, wordSize));
  } else if (kind == Interpreter::java_lang_math_exp) {
    __ movdbl(xmm0, Address(rsp, wordSize));
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dexp())));
  } else if (kind == Interpreter::java_lang_math_log) {
    __ movdbl(xmm0, Address(rsp, wordSize));
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dlog())));
  } else if (kind == Interpreter::java_lang_math_pow) {
    __ movdbl(xmm1, Address(rsp, wordSize));
    __ movdbl(xmm0, Address(rsp, 3 * wordSize));
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dpow())));
  } else if (kind == Interpreter::java_lang_math_sin && StubRoutines::dsin() != NULL) {
    __ movdbl(xmm0, Address(rsp, wordSize));
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dsin())));
  } else if (kind == Interpreter::java_lang_math_cos && StubRoutines::dcos() != NULL) {
    __ movdbl(xmm0, Address(rsp, wordSize));
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::dcos())));
  } else {
    __ fld_d(Address(rsp, wordSize));
    switch (kind) {
      case Interpreter::java_lang_math_sin :
          __ trigfunc('s');
          break;
      case Interpreter::java_lang_math_cos :
          __ trigfunc('c');
          break;
      case Interpreter::java_lang_math_tan :
          __ trigfunc('t');
          break;
      case Interpreter::java_lang_math_abs:
          __ fabs();
          break;
      case Interpreter::java_lang_math_log10:
          __ flog10();
          break;
      default                              :
          ShouldNotReachHere();
    }

    // return double result in xmm0 for interpreter and compilers.
    __ subptr(rsp, 2*wordSize);
    // Round to 64bit precision
    __ fstp_d(Address(rsp, 0));
    __ movdbl(xmm0, Address(rsp, 0));
    __ addptr(rsp, 2*wordSize);
  }


  __ pop(rax);
  __ mov(rsp, r13);
  __ jmp(rax);

  return entry_point;
}
