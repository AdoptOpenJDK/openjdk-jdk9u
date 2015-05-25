/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_INSTANCECLASSLOADERKLASS_HPP
#define SHARE_VM_OOPS_INSTANCECLASSLOADERKLASS_HPP

#include "gc/shared/specialized_oop_closures.hpp"
#include "oops/instanceKlass.hpp"
#include "utilities/macros.hpp"

// An InstanceClassLoaderKlass is a specialization of the InstanceKlass. It does
// not add any field.  It is added to walk the dependencies for the class loader
// key that this class loader points to.  This is how the loader_data graph is
// walked and dependant class loaders are kept alive.  I thought we walked
// the list later?

class InstanceClassLoaderKlass: public InstanceKlass {
  friend class VMStructs;
  friend class InstanceKlass;

  // Constructor
  InstanceClassLoaderKlass(int vtable_len, int itable_len, int static_field_size, int nonstatic_oop_map_size, ReferenceType rt, AccessFlags access_flags, bool is_anonymous)
    : InstanceKlass(vtable_len, itable_len, static_field_size, nonstatic_oop_map_size, rt, access_flags, is_anonymous) {}

public:
  virtual bool oop_is_instanceClassLoader() const { return true; }

  InstanceClassLoaderKlass() { assert(DumpSharedSpaces || UseSharedSpaces, "only for CDS"); }

  // GC specific object visitors
  //
  // Mark Sweep
  void oop_ms_follow_contents(oop obj);
  int  oop_ms_adjust_pointers(oop obj);
#if INCLUDE_ALL_GCS
  // Parallel Scavenge
  void oop_ps_push_contents(  oop obj, PSPromotionManager* pm);
  // Parallel Compact
  void oop_pc_follow_contents(oop obj, ParCompactionManager* cm);
  void oop_pc_update_pointers(oop obj);
#endif

  // Oop fields (and metadata) iterators
  //  [nv = true]  Use non-virtual calls to do_oop_nv.
  //  [nv = false] Use virtual calls to do_oop.
  //
  // The InstanceClassLoaderKlass iterators also visit the CLD pointer (or mirror of anonymous klasses.)

 private:
  // Forward iteration
  // Iterate over the oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline int oop_oop_iterate(oop obj, OopClosureType* closure);

#if INCLUDE_ALL_GCS
  // Reverse iteration
  // Iterate over the oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline int oop_oop_iterate_reverse(oop obj, OopClosureType* closure);
#endif

  // Bounded range iteration
  // Iterate over the oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline int oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr);

 public:

  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL)

#if INCLUDE_ALL_GCS
  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL_BACKWARDS)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL_BACKWARDS)
#endif // INCLUDE_ALL_GCS

};

#endif // SHARE_VM_OOPS_INSTANCECLASSLOADERKLASS_HPP
