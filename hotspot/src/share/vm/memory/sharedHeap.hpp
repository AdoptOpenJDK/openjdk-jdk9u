/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_SHAREDHEAP_HPP
#define SHARE_VM_MEMORY_SHAREDHEAP_HPP

#include "gc_interface/collectedHeap.hpp"

// A "SharedHeap" is an implementation of a java heap for HotSpot.  This
// is an abstract class: there may be many different kinds of heaps.  This
// class defines the functions that a heap must implement, and contains
// infrastructure common to all heaps.

// Note on use of FlexibleWorkGang's for GC.
// There are three places where task completion is determined.
// In
//    1) ParallelTaskTerminator::offer_termination() where _n_threads
//    must be set to the correct value so that count of workers that
//    have offered termination will exactly match the number
//    working on the task.  Tasks such as those derived from GCTask
//    use ParallelTaskTerminator's.  Tasks that want load balancing
//    by work stealing use this method to gauge completion.
//    2) SubTasksDone has a variable _n_threads that is used in
//    all_tasks_completed() to determine completion.  all_tasks_complete()
//    counts the number of tasks that have been done and then reset
//    the SubTasksDone so that it can be used again.  When the number of
//    tasks is set to the number of GC workers, then _n_threads must
//    be set to the number of active GC workers. G1RootProcessor and
//    GenCollectedHeap have SubTasksDone.
//    3) SequentialSubTasksDone has an _n_threads that is used in
//    a way similar to SubTasksDone and has the same dependency on the
//    number of active GC workers.  CompactibleFreeListSpace and Space
//    have SequentialSubTasksDone's.
//
// Examples of using SubTasksDone and SequentialSubTasksDone:
//  G1RootProcessor and GenCollectedHeap::process_roots() use
//  SubTasksDone* _process_strong_tasks to claim tasks for workers
//
//  GenCollectedHeap::gen_process_roots() calls
//      rem_set()->younger_refs_iterate()
//  to scan the card table and which eventually calls down into
//  CardTableModRefBS::par_non_clean_card_iterate_work().  This method
//  uses SequentialSubTasksDone* _pst to claim tasks.
//  Both SubTasksDone and SequentialSubTasksDone call their method
//  all_tasks_completed() to count the number of GC workers that have
//  finished their work.  That logic is "when all the workers are
//  finished the tasks are finished".
//
//  The pattern that appears  in the code is to set _n_threads
//  to a value > 1 before a task that you would like executed in parallel
//  and then to set it to 0 after that task has completed.  A value of
//  0 is a "special" value in set_n_threads() which translates to
//  setting _n_threads to 1.
//
//  Some code uses _n_termination to decide if work should be done in
//  parallel.  The notorious possibly_parallel_oops_do() in threads.cpp
//  is an example of such code.  Look for variable "is_par" for other
//  examples.
//
//  The active_workers is not reset to 0 after a parallel phase.  It's
//  value may be used in later phases and in one instance at least
//  (the parallel remark) it has to be used (the parallel remark depends
//  on the partitioning done in the previous parallel scavenge).

class SharedHeap : public CollectedHeap {
  friend class VMStructs;

protected:
  // Full initialization is done in a concrete subtype's "initialize"
  // function.
  SharedHeap();

public:
  // Note, the below comment needs to be updated to reflect the changes
  // introduced by JDK-8076225. This should be done as part of JDK-8076289.
  //
  //Some collectors will perform "process_strong_roots" in parallel.
  // Such a call will involve claiming some fine-grained tasks, such as
  // scanning of threads.  To make this process simpler, we provide the
  // "strong_roots_parity()" method.  Collectors that start parallel tasks
  // whose threads invoke "process_strong_roots" must
  // call "change_strong_roots_parity" in sequential code starting such a
  // task.  (This also means that a parallel thread may only call
  // process_strong_roots once.)
  //
  // For calls to process_roots by sequential code, the parity is
  // updated automatically.
  //
  // The idea is that objects representing fine-grained tasks, such as
  // threads, will contain a "parity" field.  A task will is claimed in the
  // current "process_roots" call only if its parity field is the
  // same as the "strong_roots_parity"; task claiming is accomplished by
  // updating the parity field to the strong_roots_parity with a CAS.
  //
  // If the client meats this spec, then strong_roots_parity() will have
  // the following properties:
  //   a) to return a different value than was returned before the last
  //      call to change_strong_roots_parity, and
  //   c) to never return a distinguished value (zero) with which such
  //      task-claiming variables may be initialized, to indicate "never
  //      claimed".
 public:

  // Call these in sequential code around process_roots.
  // strong_roots_prologue calls change_strong_roots_parity, if
  // parallel tasks are enabled.
  class StrongRootsScope : public MarkingCodeBlobClosure::MarkScope {
    SharedHeap*   _sh;

   public:
    StrongRootsScope(SharedHeap* heap, bool activate = true);
    ~StrongRootsScope();
  };
 };

#endif // SHARE_VM_MEMORY_SHAREDHEAP_HPP
