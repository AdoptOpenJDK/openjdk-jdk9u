/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/vm_version.hpp"

const char* Abstract_VM_Version::_s_vm_release = Abstract_VM_Version::vm_release();
const char* Abstract_VM_Version::_s_internal_vm_info_string = Abstract_VM_Version::internal_vm_info_string();
bool Abstract_VM_Version::_supports_cx8 = false;
bool Abstract_VM_Version::_supports_atomic_getset4 = false;
bool Abstract_VM_Version::_supports_atomic_getset8 = false;
bool Abstract_VM_Version::_supports_atomic_getadd4 = false;
bool Abstract_VM_Version::_supports_atomic_getadd8 = false;
unsigned int Abstract_VM_Version::_logical_processors_per_package = 1U;
unsigned int Abstract_VM_Version::_L1_data_cache_line_size = 0;
int Abstract_VM_Version::_reserve_for_allocation_prefetch = 0;

#ifndef HOTSPOT_RELEASE_VERSION
  #error HOTSPOT_RELEASE_VERSION must be defined
#endif

#ifndef JDK_MAJOR_VERSION
  #error JDK_MAJOR_VERSION must be defined
#endif
#ifndef JDK_MINOR_VERSION
  #error JDK_MINOR_VERSION must be defined
#endif
#ifndef JDK_MICRO_VERSION
  #error JDK_MICRO_VERSION must be defined
#endif
#ifndef JDK_BUILD_NUMBER
  #error JDK_BUILD_NUMBER must be defined
#endif

#ifndef JRE_RELEASE_VERSION
  #error JRE_RELEASE_VERSION must be defined
#endif

// NOTE: Builds within Visual Studio do not define the build target in
//       HOTSPOT_RELEASE_VERSION, so it must be done here
#if defined(VISUAL_STUDIO_BUILD) && !defined(PRODUCT)
  #ifndef HOTSPOT_BUILD_TARGET
    #error HOTSPOT_BUILD_TARGET must be defined
  #endif
  #define VM_RELEASE HOTSPOT_RELEASE_VERSION "-" HOTSPOT_BUILD_TARGET
#else
  #define VM_RELEASE HOTSPOT_RELEASE_VERSION
#endif

// HOTSPOT_RELEASE_VERSION follows the JDK release version naming convention
// <major_ver>.<minor_ver>.<micro_ver>[-<identifier>][-<debug_target>][-b<nn>]
int Abstract_VM_Version::_vm_major_version = 0;
int Abstract_VM_Version::_vm_minor_version = 0;
int Abstract_VM_Version::_vm_micro_version = 0;
int Abstract_VM_Version::_vm_build_number = 0;
bool Abstract_VM_Version::_initialized = false;
int Abstract_VM_Version::_parallel_worker_threads = 0;
bool Abstract_VM_Version::_parallel_worker_threads_initialized = false;

#ifdef ASSERT
static void assert_digits(const char * s, const char * message) {
  for (int i = 0; s[i] != '\0'; i++) {
    assert(isdigit(s[i]), message);
  }
}
#endif

static void set_version_field(int * version_field, const char * version_str,
                              const char * const assert_msg) {
  if (version_str != NULL && *version_str != '\0') {
    DEBUG_ONLY(assert_digits(version_str, assert_msg));
    *version_field = atoi(version_str);
  }
}

void Abstract_VM_Version::initialize() {
  if (_initialized) {
    return;
  }

  set_version_field(&_vm_major_version, JDK_MAJOR_VERSION, "bad major version");
  set_version_field(&_vm_minor_version, JDK_MINOR_VERSION, "bad minor version");
  set_version_field(&_vm_micro_version, JDK_MICRO_VERSION, "bad micro version");
  int offset = (JDK_BUILD_NUMBER != NULL && JDK_BUILD_NUMBER[0] == 'b') ? 1 : 0;
  set_version_field(&_vm_build_number, &JDK_BUILD_NUMBER[offset],
                    "bad build number");

  _initialized = true;
}

#if defined(_LP64)
  #define VMLP "64-Bit "
#else
  #define VMLP ""
#endif

#ifndef VMTYPE
  #ifdef TIERED
    #define VMTYPE "Server"
  #else // TIERED
  #ifdef ZERO
  #ifdef SHARK
    #define VMTYPE "Shark"
  #else // SHARK
    #define VMTYPE "Zero"
  #endif // SHARK
  #else // ZERO
     #define VMTYPE COMPILER1_PRESENT("Client")   \
                    COMPILER2_PRESENT("Server")
  #endif // ZERO
  #endif // TIERED
#endif

#ifndef HOTSPOT_VM_DISTRO
  #error HOTSPOT_VM_DISTRO must be defined
#endif
#define VMNAME HOTSPOT_VM_DISTRO " " VMLP EMBEDDED_ONLY("Embedded ") VMTYPE " VM"

const char* Abstract_VM_Version::vm_name() {
  return VMNAME;
}


const char* Abstract_VM_Version::vm_vendor() {
#ifdef VENDOR
  return XSTR(VENDOR);
#else
  return "Oracle Corporation";
#endif
}


const char* Abstract_VM_Version::vm_info_string() {
  switch (Arguments::mode()) {
    case Arguments::_int:
      return UseSharedSpaces ? "interpreted mode, sharing" : "interpreted mode";
    case Arguments::_mixed:
      return UseSharedSpaces ? "mixed mode, sharing"       :  "mixed mode";
    case Arguments::_comp:
      return UseSharedSpaces ? "compiled mode, sharing"    : "compiled mode";
  };
  ShouldNotReachHere();
  return "";
}

// NOTE: do *not* use stringStream. this function is called by
//       fatal error handler. if the crash is in native thread,
//       stringStream cannot get resource allocated and will SEGV.
const char* Abstract_VM_Version::vm_release() {
  return VM_RELEASE;
}

// NOTE: do *not* use stringStream. this function is called by
//       fatal error handlers. if the crash is in native thread,
//       stringStream cannot get resource allocated and will SEGV.
const char* Abstract_VM_Version::jre_release_version() {
  return JRE_RELEASE_VERSION;
}

#define OS       LINUX_ONLY("linux")             \
                 WINDOWS_ONLY("windows")         \
                 SOLARIS_ONLY("solaris")         \
                 AIX_ONLY("aix")                 \
                 BSD_ONLY("bsd")

#ifdef ZERO
#define CPU      ZERO_LIBARCH
#else
#define CPU      IA32_ONLY("x86")                \
                 IA64_ONLY("ia64")               \
                 AMD64_ONLY("amd64")             \
                 ARM_ONLY("arm")                 \
                 PPC32_ONLY("ppc")               \
                 PPC64_ONLY("ppc64")             \
                 AARCH64_ONLY("aarch64")         \
                 SPARC_ONLY("sparc")
#endif // ZERO

const char *Abstract_VM_Version::vm_platform_string() {
  return OS "-" CPU;
}

const char* Abstract_VM_Version::internal_vm_info_string() {
  #ifndef HOTSPOT_BUILD_USER
    #define HOTSPOT_BUILD_USER unknown
  #endif

  #ifndef HOTSPOT_BUILD_COMPILER
    #ifdef _MSC_VER
      #if _MSC_VER == 1600
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 10.0 (VS2010)"
      #elif _MSC_VER == 1700
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 11.0 (VS2012)"
      #elif _MSC_VER == 1800
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 12.0 (VS2013)"
      #else
        #define HOTSPOT_BUILD_COMPILER "unknown MS VC++:" XSTR(_MSC_VER)
      #endif
    #elif defined(__SUNPRO_CC)
      #if   __SUNPRO_CC == 0x420
        #define HOTSPOT_BUILD_COMPILER "Workshop 4.2"
      #elif __SUNPRO_CC == 0x500
        #define HOTSPOT_BUILD_COMPILER "Workshop 5.0 compat=" XSTR(__SUNPRO_CC_COMPAT)
      #elif __SUNPRO_CC == 0x520
        #define HOTSPOT_BUILD_COMPILER "Workshop 5.2 compat=" XSTR(__SUNPRO_CC_COMPAT)
      #elif __SUNPRO_CC == 0x580
        #define HOTSPOT_BUILD_COMPILER "Workshop 5.8"
      #elif __SUNPRO_CC == 0x590
        #define HOTSPOT_BUILD_COMPILER "Workshop 5.9"
      #elif __SUNPRO_CC == 0x5100
        #define HOTSPOT_BUILD_COMPILER "Sun Studio 12u1"
      #elif __SUNPRO_CC == 0x5120
        #define HOTSPOT_BUILD_COMPILER "Sun Studio 12u3"
      #else
        #define HOTSPOT_BUILD_COMPILER "unknown Workshop:" XSTR(__SUNPRO_CC)
      #endif
    #elif defined(__GNUC__)
        #define HOTSPOT_BUILD_COMPILER "gcc " __VERSION__
    #elif defined(__IBMCPP__)
        #define HOTSPOT_BUILD_COMPILER "xlC " XSTR(__IBMCPP__)

    #else
      #define HOTSPOT_BUILD_COMPILER "unknown compiler"
    #endif
  #endif

  #ifndef FLOAT_ARCH
    #if defined(__SOFTFP__)
      #define FLOAT_ARCH_STR "-sflt"
    #elif defined(E500V2)
      #define FLOAT_ARCH_STR "-e500v2"
    #elif defined(ARM)
      #define FLOAT_ARCH_STR "-vfp"
    #elif defined(PPC32)
      #define FLOAT_ARCH_STR "-hflt"
    #else
      #define FLOAT_ARCH_STR ""
    #endif
  #else
    #define FLOAT_ARCH_STR XSTR(FLOAT_ARCH)
  #endif

  return VMNAME " (" VM_RELEASE ") for " OS "-" CPU FLOAT_ARCH_STR
         " JRE (" JRE_RELEASE_VERSION "), built on " __DATE__ " " __TIME__
         " by " XSTR(HOTSPOT_BUILD_USER) " with " HOTSPOT_BUILD_COMPILER;
}

const char *Abstract_VM_Version::vm_build_user() {
  return HOTSPOT_BUILD_USER;
}

unsigned int Abstract_VM_Version::jvm_version() {
  return ((Abstract_VM_Version::vm_major_version() & 0xFF) << 24) |
         ((Abstract_VM_Version::vm_minor_version() & 0xFF) << 16) |
         ((Abstract_VM_Version::vm_micro_version() & 0xFF) << 8) |
         (Abstract_VM_Version::vm_build_number() & 0xFF);
}


void VM_Version_init() {
  VM_Version::initialize();

#ifndef PRODUCT
  if (PrintMiscellaneous && Verbose) {
    os::print_cpu_info(tty);
  }
#endif
}

unsigned int Abstract_VM_Version::nof_parallel_worker_threads(
                                                      unsigned int num,
                                                      unsigned int den,
                                                      unsigned int switch_pt) {
  if (FLAG_IS_DEFAULT(ParallelGCThreads)) {
    assert(ParallelGCThreads == 0, "Default ParallelGCThreads is not 0");
    // For very large machines, there are diminishing returns
    // for large numbers of worker threads.  Instead of
    // hogging the whole system, use a fraction of the workers for every
    // processor after the first 8.  For example, on a 72 cpu machine
    // and a chosen fraction of 5/8
    // use 8 + (72 - 8) * (5/8) == 48 worker threads.
    unsigned int ncpus = (unsigned int) os::active_processor_count();
    return (ncpus <= switch_pt) ?
           ncpus :
          (switch_pt + ((ncpus - switch_pt) * num) / den);
  } else {
    return ParallelGCThreads;
  }
}

unsigned int Abstract_VM_Version::calc_parallel_worker_threads() {
  return nof_parallel_worker_threads(5, 8, 8);
}


// Does not set the _initialized flag since it is
// a global flag.
unsigned int Abstract_VM_Version::parallel_worker_threads() {
  if (!_parallel_worker_threads_initialized) {
    if (FLAG_IS_DEFAULT(ParallelGCThreads)) {
      _parallel_worker_threads = VM_Version::calc_parallel_worker_threads();
    } else {
      _parallel_worker_threads = ParallelGCThreads;
    }
    _parallel_worker_threads_initialized = true;
  }
  return _parallel_worker_threads;
}
