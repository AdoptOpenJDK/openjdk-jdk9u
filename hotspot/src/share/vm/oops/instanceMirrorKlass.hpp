/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_INSTANCEMIRRORKLASS_HPP
#define SHARE_VM_OOPS_INSTANCEMIRRORKLASS_HPP

#include "classfile/systemDictionary.hpp"
#include "memory/specialized_oop_closures.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/handles.hpp"
#include "utilities/macros.hpp"

// An InstanceMirrorKlass is a specialized InstanceKlass for
// java.lang.Class instances.  These instances are special because
// they contain the static fields of the class in addition to the
// normal fields of Class.  This means they are variable sized
// instances and need special logic for computing their size and for
// iteration of their oops.


class InstanceMirrorKlass: public InstanceKlass {
  friend class VMStructs;
  friend class InstanceKlass;

 private:
  static int _offset_of_static_fields;

  // Constructor
  InstanceMirrorKlass(int vtable_len, int itable_len, int static_field_size, int nonstatic_oop_map_size, ReferenceType rt, AccessFlags access_flags,  bool is_anonymous)
    : InstanceKlass(vtable_len, itable_len, static_field_size, nonstatic_oop_map_size, rt, access_flags, is_anonymous) {}

 public:
  InstanceMirrorKlass() { assert(DumpSharedSpaces || UseSharedSpaces, "only for CDS"); }
  // Type testing
  bool oop_is_instanceMirror() const             { return true; }

  // Casting from Klass*
  static InstanceMirrorKlass* cast(Klass* k) {
    assert(k->oop_is_instanceMirror(), "cast to InstanceMirrorKlass");
    return (InstanceMirrorKlass*) k;
  }

  // Returns the size of the instance including the extra static fields.
  virtual int oop_size(oop obj) const;

  // Static field offset is an offset into the Heap, should be converted by
  // based on UseCompressedOop for traversal
  static HeapWord* start_of_static_fields(oop obj) {
    return (HeapWord*)(cast_from_oop<intptr_t>(obj) + offset_of_static_fields());
  }

  static void init_offset_of_static_fields() {
    // Cache the offset of the static fields in the Class instance
    assert(_offset_of_static_fields == 0, "once");
    _offset_of_static_fields = InstanceMirrorKlass::cast(SystemDictionary::Class_klass())->size_helper() << LogHeapWordSize;
  }

  static int offset_of_static_fields() {
    return _offset_of_static_fields;
  }

  int compute_static_oop_field_count(oop obj);

  // Given a Klass return the size of the instance
  int instance_size(KlassHandle k);

  // allocation
  instanceOop allocate_instance(KlassHandle k, TRAPS);

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
  // The InstanceMirrorKlass iterators also visit the hidden Klass pointer.

 public:
  // Iterate over the static fields.
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_statics(oop obj, OopClosureType* closure);

 private:
  // Iterate over the static fields.
  // Specialized for [T = oop] or [T = narrowOop].
  template <bool nv, typename T, class OopClosureType>
  inline void oop_oop_iterate_statics_specialized(oop obj, OopClosureType* closure);

  // Forward iteration
  // Iterate over the oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline int oop_oop_iterate(oop obj, OopClosureType* closure);


  // Reverse iteration
#if INCLUDE_ALL_GCS
  // Iterate over the oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline int oop_oop_iterate_reverse(oop obj, OopClosureType* closure);
#endif


  // Bounded range iteration
  // Iterate over the oop fields and metadata.
  template <bool nv, class OopClosureType>
  inline int oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr);

  // Iterate over the static fields.
  template <bool nv, class OopClosureType>
  inline void oop_oop_iterate_statics_bounded(oop obj, OopClosureType* closure, MemRegion mr);

  // Iterate over the static fields.
  // Specialized for [T = oop] or [T = narrowOop].
  template <bool nv, typename T, class OopClosureType>
  inline void oop_oop_iterate_statics_specialized_bounded(oop obj, OopClosureType* closure, MemRegion mr);


 public:

#define InstanceMirrorKlass_OOP_OOP_ITERATE_DECL(OopClosureType, nv_suffix)           \
  int oop_oop_iterate##nv_suffix(oop obj, OopClosureType* blk);                       \
  int oop_oop_iterate##nv_suffix##_m(oop obj, OopClosureType* blk, MemRegion mr);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_DECL)

#if INCLUDE_ALL_GCS
#define InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DECL(OopClosureType, nv_suffix) \
  int oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* blk);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceMirrorKlass_OOP_OOP_ITERATE_BACKWARDS_DECL)
#endif // INCLUDE_ALL_GCS
};

#endif // SHARE_VM_OOPS_INSTANCEMIRRORKLASS_HPP
