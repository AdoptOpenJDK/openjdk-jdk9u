/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "memory/threadLocalAllocBuffer.hpp"
#include "oops/arrayOop.hpp"
#include "oops/oop.inline.hpp"

size_t ParGCAllocBuffer::min_size() {
  // Make sure that we return something that is larger than AlignmentReserve
  return align_object_size(MAX2(MinTLABSize / HeapWordSize, (uintx)oopDesc::header_size())) + AlignmentReserve;
}

size_t ParGCAllocBuffer::max_size() {
  return ThreadLocalAllocBuffer::max_size();
}

ParGCAllocBuffer::ParGCAllocBuffer(size_t desired_plab_sz_) :
  _word_sz(desired_plab_sz_), _bottom(NULL), _top(NULL),
  _end(NULL), _hard_end(NULL), _allocated(0), _wasted(0)
{
  // ArrayOopDesc::header_size depends on command line initialization.
  AlignmentReserve = oopDesc::header_size() > MinObjAlignment ? align_object_size(arrayOopDesc::header_size(T_INT)) : 0;
  assert(min_size() > AlignmentReserve,
         err_msg("Minimum PLAB size " SIZE_FORMAT" must be larger than alignment reserve " SIZE_FORMAT" "
                 "to be able to contain objects", min_size(), AlignmentReserve));
}

// If the minimum object size is greater than MinObjAlignment, we can
// end up with a shard at the end of the buffer that's smaller than
// the smallest object.  We can't allow that because the buffer must
// look like it's full of objects when we retire it, so we make
// sure we have enough space for a filler int array object.
size_t ParGCAllocBuffer::AlignmentReserve;

void ParGCAllocBuffer::flush_and_retire_stats(PLABStats* stats) {
  // Retire the last allocation buffer.
  size_t unused = retire_internal();

  // Now flush the statistics.
  stats->add_allocated(_allocated);
  stats->add_wasted(_wasted);
  stats->add_unused(unused);

  // Since we have flushed the stats we need to clear  the _allocated and _wasted
  // fields in case somebody retains an instance of this over GCs. Not doing so
  // will artifically inflate the values in the statistics.
  _allocated = 0;
  _wasted = 0;
}

void ParGCAllocBuffer::retire() {
  _wasted += retire_internal();
}

size_t ParGCAllocBuffer::retire_internal() {
  size_t result = 0;
  if (_top < _hard_end) {
    CollectedHeap::fill_with_object(_top, _hard_end);
    result += invalidate();
  }
  return result;
}

// Compute desired plab size and latch result for later
// use. This should be called once at the end of parallel
// scavenge; it clears the sensor accumulators.
void PLABStats::adjust_desired_plab_sz(uint no_of_gc_workers) {
  assert(ResizePLAB, "Not set");

  assert(is_object_aligned(max_size()) && min_size() <= max_size(),
         "PLAB clipping computation may be incorrect");

  if (_allocated == 0) {
    assert(_unused == 0,
           err_msg("Inconsistency in PLAB stats: "
                   "_allocated: "SIZE_FORMAT", "
                   "_wasted: "SIZE_FORMAT", "
                   "_unused: "SIZE_FORMAT,
                   _allocated, _wasted, _unused));

    _allocated = 1;
  }
  double wasted_frac    = (double)_unused / (double)_allocated;
  size_t target_refills = (size_t)((wasted_frac * TargetSurvivorRatio) / TargetPLABWastePct);
  if (target_refills == 0) {
    target_refills = 1;
  }
  size_t used = _allocated - _wasted - _unused;
  size_t recent_plab_sz = used / (target_refills * no_of_gc_workers);
  // Take historical weighted average
  _filter.sample(recent_plab_sz);
  // Clip from above and below, and align to object boundary
  size_t new_plab_sz = MAX2(min_size(), (size_t)_filter.average());
  new_plab_sz = MIN2(max_size(), new_plab_sz);
  new_plab_sz = align_object_size(new_plab_sz);
  // Latch the result
  if (PrintPLAB) {
    gclog_or_tty->print(" (plab_sz = " SIZE_FORMAT" desired_plab_sz = " SIZE_FORMAT") ", recent_plab_sz, new_plab_sz);
  }
  _desired_plab_sz = new_plab_sz;

  reset();
}

#ifndef PRODUCT
void ParGCAllocBuffer::print() {
  gclog_or_tty->print_cr("parGCAllocBuffer: _bottom: " PTR_FORMAT "  _top: " PTR_FORMAT
    "  _end: " PTR_FORMAT "  _hard_end: " PTR_FORMAT ")",
    p2i(_bottom), p2i(_top), p2i(_end), p2i(_hard_end));
}
#endif // !PRODUCT
