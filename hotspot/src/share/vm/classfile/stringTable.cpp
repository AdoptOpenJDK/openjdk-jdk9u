/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/altHashing.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/filemap.hpp"
#include "memory/gcLocker.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.inline2.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/hashtable.inline.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1StringDedup.hpp"
#endif

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

// the number of buckets a thread claims
const int ClaimChunkSize = 32;

#ifdef ASSERT
class StableMemoryChecker : public StackObj {
  enum { _bufsize = wordSize*4 };

  address _region;
  jint    _size;
  u1      _save_buf[_bufsize];

  int sample(u1* save_buf) {
    if (_size <= _bufsize) {
      memcpy(save_buf, _region, _size);
      return _size;
    } else {
      // copy head and tail
      memcpy(&save_buf[0],          _region,                      _bufsize/2);
      memcpy(&save_buf[_bufsize/2], _region + _size - _bufsize/2, _bufsize/2);
      return (_bufsize/2)*2;
    }
  }

 public:
  StableMemoryChecker(const void* region, jint size) {
    _region = (address) region;
    _size   = size;
    sample(_save_buf);
  }

  bool verify() {
    u1 check_buf[sizeof(_save_buf)];
    int check_size = sample(check_buf);
    return (0 == memcmp(_save_buf, check_buf, check_size));
  }

  void set_region(const void* region) { _region = (address) region; }
};
#endif


// --------------------------------------------------------------------------
StringTable* StringTable::_the_table = NULL;

bool StringTable::_needs_rehashing = false;

volatile int StringTable::_parallel_claimed_idx = 0;

// Pick hashing algorithm
unsigned int StringTable::hash_string(const jchar* s, int len) {
  return use_alternate_hashcode() ? AltHashing::murmur3_32(seed(), s, len) :
                                    java_lang_String::hash_code(s, len);
}

oop StringTable::lookup(int index, jchar* name,
                        int len, unsigned int hash) {
  int count = 0;
  for (HashtableEntry<oop, mtSymbol>* l = bucket(index); l != NULL; l = l->next()) {
    count++;
    if (l->hash() == hash) {
      if (java_lang_String::equals(l->literal(), name, len)) {
        return l->literal();
      }
    }
  }
  // If the bucket size is too deep check if this hash code is insufficient.
  if (count >= BasicHashtable<mtSymbol>::rehash_count && !needs_rehashing()) {
    _needs_rehashing = check_rehash_table(count);
  }
  return NULL;
}


oop StringTable::basic_add(int index_arg, Handle string, jchar* name,
                           int len, unsigned int hashValue_arg, TRAPS) {

  assert(java_lang_String::equals(string(), name, len),
         "string must be properly initialized");
  // Cannot hit a safepoint in this function because the "this" pointer can move.
  No_Safepoint_Verifier nsv;

  // Check if the symbol table has been rehashed, if so, need to recalculate
  // the hash value and index before second lookup.
  unsigned int hashValue;
  int index;
  if (use_alternate_hashcode()) {
    hashValue = hash_string(name, len);
    index = hash_to_index(hashValue);
  } else {
    hashValue = hashValue_arg;
    index = index_arg;
  }

  // Since look-up was done lock-free, we need to check if another
  // thread beat us in the race to insert the symbol.

  oop test = lookup(index, name, len, hashValue); // calls lookup(u1*, int)
  if (test != NULL) {
    // Entry already added
    return test;
  }

  HashtableEntry<oop, mtSymbol>* entry = new_entry(hashValue, string());
  add_entry(index, entry);
  return string();
}


oop StringTable::lookup(Symbol* symbol) {
  ResourceMark rm;
  int length;
  jchar* chars = symbol->as_unicode(length);
  return lookup(chars, length);
}


oop StringTable::lookup(jchar* name, int len) {
  unsigned int hash = hash_string(name, len);
  int index = the_table()->hash_to_index(hash);
  return the_table()->lookup(index, name, len, hash);
}


oop StringTable::intern(Handle string_or_null, jchar* name,
                        int len, TRAPS) {
  unsigned int hashValue = hash_string(name, len);
  int index = the_table()->hash_to_index(hashValue);
  oop found_string = the_table()->lookup(index, name, len, hashValue);

  // Found
  if (found_string != NULL) return found_string;

  debug_only(StableMemoryChecker smc(name, len * sizeof(name[0])));
  assert(!Universe::heap()->is_in_reserved(name),
         "proposed name of symbol must be stable");

  Handle string;
  // try to reuse the string if possible
  if (!string_or_null.is_null()) {
    string = string_or_null;
  } else {
    string = java_lang_String::create_from_unicode(name, len, CHECK_NULL);
  }

#if INCLUDE_ALL_GCS
  if (G1StringDedup::is_enabled()) {
    // Deduplicate the string before it is interned. Note that we should never
    // deduplicate a string after it has been interned. Doing so will counteract
    // compiler optimizations done on e.g. interned string literals.
    G1StringDedup::deduplicate(string());
  }
#endif

  // Grab the StringTable_lock before getting the_table() because it could
  // change at safepoint.
  MutexLocker ml(StringTable_lock, THREAD);

  // Otherwise, add to symbol to table
  return the_table()->basic_add(index, string, name, len,
                                hashValue, CHECK_NULL);
}

oop StringTable::intern(Symbol* symbol, TRAPS) {
  if (symbol == NULL) return NULL;
  ResourceMark rm(THREAD);
  int length;
  jchar* chars = symbol->as_unicode(length);
  Handle string;
  oop result = intern(string, chars, length, CHECK_NULL);
  return result;
}


oop StringTable::intern(oop string, TRAPS)
{
  if (string == NULL) return NULL;
  ResourceMark rm(THREAD);
  int length;
  Handle h_string (THREAD, string);
  jchar* chars = java_lang_String::as_unicode_string(string, length, CHECK_NULL);
  oop result = intern(h_string, chars, length, CHECK_NULL);
  return result;
}


oop StringTable::intern(const char* utf8_string, TRAPS) {
  if (utf8_string == NULL) return NULL;
  ResourceMark rm(THREAD);
  int length = UTF8::unicode_length(utf8_string);
  jchar* chars = NEW_RESOURCE_ARRAY(jchar, length);
  UTF8::convert_to_unicode(utf8_string, chars, length);
  Handle string;
  oop result = intern(string, chars, length, CHECK_NULL);
  return result;
}

void StringTable::unlink_or_oops_do(BoolObjectClosure* is_alive, OopClosure* f, int* processed, int* removed) {
  buckets_unlink_or_oops_do(is_alive, f, 0, the_table()->table_size(), processed, removed);
}

void StringTable::possibly_parallel_unlink_or_oops_do(BoolObjectClosure* is_alive, OopClosure* f, int* processed, int* removed) {
  // Readers of the table are unlocked, so we should only be removing
  // entries at a safepoint.
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  const int limit = the_table()->table_size();

  for (;;) {
    // Grab next set of buckets to scan
    int start_idx = Atomic::add(ClaimChunkSize, &_parallel_claimed_idx) - ClaimChunkSize;
    if (start_idx >= limit) {
      // End of table
      break;
    }

    int end_idx = MIN2(limit, start_idx + ClaimChunkSize);
    buckets_unlink_or_oops_do(is_alive, f, start_idx, end_idx, processed, removed);
  }
}

void StringTable::buckets_oops_do(OopClosure* f, int start_idx, int end_idx) {
  const int limit = the_table()->table_size();

  assert(0 <= start_idx && start_idx <= limit,
         err_msg("start_idx (%d) is out of bounds", start_idx));
  assert(0 <= end_idx && end_idx <= limit,
         err_msg("end_idx (%d) is out of bounds", end_idx));
  assert(start_idx <= end_idx,
         err_msg("Index ordering: start_idx=%d, end_idx=%d",
                 start_idx, end_idx));

  for (int i = start_idx; i < end_idx; i += 1) {
    HashtableEntry<oop, mtSymbol>* entry = the_table()->bucket(i);
    while (entry != NULL) {
      assert(!entry->is_shared(), "CDS not used for the StringTable");

      f->do_oop((oop*)entry->literal_addr());

      entry = entry->next();
    }
  }
}

void StringTable::buckets_unlink_or_oops_do(BoolObjectClosure* is_alive, OopClosure* f, int start_idx, int end_idx, int* processed, int* removed) {
  const int limit = the_table()->table_size();

  assert(0 <= start_idx && start_idx <= limit,
         err_msg("start_idx (%d) is out of bounds", start_idx));
  assert(0 <= end_idx && end_idx <= limit,
         err_msg("end_idx (%d) is out of bounds", end_idx));
  assert(start_idx <= end_idx,
         err_msg("Index ordering: start_idx=%d, end_idx=%d",
                 start_idx, end_idx));

  for (int i = start_idx; i < end_idx; ++i) {
    HashtableEntry<oop, mtSymbol>** p = the_table()->bucket_addr(i);
    HashtableEntry<oop, mtSymbol>* entry = the_table()->bucket(i);
    while (entry != NULL) {
      assert(!entry->is_shared(), "CDS not used for the StringTable");

      if (is_alive->do_object_b(entry->literal())) {
        if (f != NULL) {
          f->do_oop((oop*)entry->literal_addr());
        }
        p = entry->next_addr();
      } else {
        *p = entry->next();
        the_table()->free_entry(entry);
        (*removed)++;
      }
      (*processed)++;
      entry = *p;
    }
  }
}

void StringTable::oops_do(OopClosure* f) {
  buckets_oops_do(f, 0, the_table()->table_size());
}

void StringTable::possibly_parallel_oops_do(OopClosure* f) {
  const int limit = the_table()->table_size();

  for (;;) {
    // Grab next set of buckets to scan
    int start_idx = Atomic::add(ClaimChunkSize, &_parallel_claimed_idx) - ClaimChunkSize;
    if (start_idx >= limit) {
      // End of table
      break;
    }

    int end_idx = MIN2(limit, start_idx + ClaimChunkSize);
    buckets_oops_do(f, start_idx, end_idx);
  }
}

// This verification is part of Universe::verify() and needs to be quick.
// See StringTable::verify_and_compare() below for exhaustive verification.
void StringTable::verify() {
  for (int i = 0; i < the_table()->table_size(); ++i) {
    HashtableEntry<oop, mtSymbol>* p = the_table()->bucket(i);
    for ( ; p != NULL; p = p->next()) {
      oop s = p->literal();
      guarantee(s != NULL, "interned string is NULL");
      unsigned int h = java_lang_String::hash_string(s);
      guarantee(p->hash() == h, "broken hash in string table entry");
      guarantee(the_table()->hash_to_index(h) == i,
                "wrong index in string table");
    }
  }
}

void StringTable::dump(outputStream* st) {
  the_table()->dump_table(st, "StringTable");
}

StringTable::VerifyRetTypes StringTable::compare_entries(
                                      int bkt1, int e_cnt1,
                                      HashtableEntry<oop, mtSymbol>* e_ptr1,
                                      int bkt2, int e_cnt2,
                                      HashtableEntry<oop, mtSymbol>* e_ptr2) {
  // These entries are sanity checked by verify_and_compare_entries()
  // before this function is called.
  oop str1 = e_ptr1->literal();
  oop str2 = e_ptr2->literal();

  if (str1 == str2) {
    tty->print_cr("ERROR: identical oop values (0x" PTR_FORMAT ") "
                  "in entry @ bucket[%d][%d] and entry @ bucket[%d][%d]",
                  (void *)str1, bkt1, e_cnt1, bkt2, e_cnt2);
    return _verify_fail_continue;
  }

  if (java_lang_String::equals(str1, str2)) {
    tty->print_cr("ERROR: identical String values in entry @ "
                  "bucket[%d][%d] and entry @ bucket[%d][%d]",
                  bkt1, e_cnt1, bkt2, e_cnt2);
    return _verify_fail_continue;
  }

  return _verify_pass;
}

StringTable::VerifyRetTypes StringTable::verify_entry(int bkt, int e_cnt,
                                      HashtableEntry<oop, mtSymbol>* e_ptr,
                                      StringTable::VerifyMesgModes mesg_mode) {

  VerifyRetTypes ret = _verify_pass;  // be optimistic

  oop str = e_ptr->literal();
  if (str == NULL) {
    if (mesg_mode == _verify_with_mesgs) {
      tty->print_cr("ERROR: NULL oop value in entry @ bucket[%d][%d]", bkt,
                    e_cnt);
    }
    // NULL oop means no more verifications are possible
    return _verify_fail_done;
  }

  if (str->klass() != SystemDictionary::String_klass()) {
    if (mesg_mode == _verify_with_mesgs) {
      tty->print_cr("ERROR: oop is not a String in entry @ bucket[%d][%d]",
                    bkt, e_cnt);
    }
    // not a String means no more verifications are possible
    return _verify_fail_done;
  }

  unsigned int h = java_lang_String::hash_string(str);
  if (e_ptr->hash() != h) {
    if (mesg_mode == _verify_with_mesgs) {
      tty->print_cr("ERROR: broken hash value in entry @ bucket[%d][%d], "
                    "bkt_hash=%d, str_hash=%d", bkt, e_cnt, e_ptr->hash(), h);
    }
    ret = _verify_fail_continue;
  }

  if (the_table()->hash_to_index(h) != bkt) {
    if (mesg_mode == _verify_with_mesgs) {
      tty->print_cr("ERROR: wrong index value for entry @ bucket[%d][%d], "
                    "str_hash=%d, hash_to_index=%d", bkt, e_cnt, h,
                    the_table()->hash_to_index(h));
    }
    ret = _verify_fail_continue;
  }

  return ret;
}

// See StringTable::verify() above for the quick verification that is
// part of Universe::verify(). This verification is exhaustive and
// reports on every issue that is found. StringTable::verify() only
// reports on the first issue that is found.
//
// StringTable::verify_entry() checks:
// - oop value != NULL (same as verify())
// - oop value is a String
// - hash(String) == hash in entry (same as verify())
// - index for hash == index of entry (same as verify())
//
// StringTable::compare_entries() checks:
// - oops are unique across all entries
// - String values are unique across all entries
//
int StringTable::verify_and_compare_entries() {
  assert(StringTable_lock->is_locked(), "sanity check");

  int  fail_cnt = 0;

  // first, verify all the entries individually:
  for (int bkt = 0; bkt < the_table()->table_size(); bkt++) {
    HashtableEntry<oop, mtSymbol>* e_ptr = the_table()->bucket(bkt);
    for (int e_cnt = 0; e_ptr != NULL; e_ptr = e_ptr->next(), e_cnt++) {
      VerifyRetTypes ret = verify_entry(bkt, e_cnt, e_ptr, _verify_with_mesgs);
      if (ret != _verify_pass) {
        fail_cnt++;
      }
    }
  }

  // Optimization: if the above check did not find any failures, then
  // the comparison loop below does not need to call verify_entry()
  // before calling compare_entries(). If there were failures, then we
  // have to call verify_entry() to see if the entry can be passed to
  // compare_entries() safely. When we call verify_entry() in the loop
  // below, we do so quietly to void duplicate messages and we don't
  // increment fail_cnt because the failures have already been counted.
  bool need_entry_verify = (fail_cnt != 0);

  // second, verify all entries relative to each other:
  for (int bkt1 = 0; bkt1 < the_table()->table_size(); bkt1++) {
    HashtableEntry<oop, mtSymbol>* e_ptr1 = the_table()->bucket(bkt1);
    for (int e_cnt1 = 0; e_ptr1 != NULL; e_ptr1 = e_ptr1->next(), e_cnt1++) {
      if (need_entry_verify) {
        VerifyRetTypes ret = verify_entry(bkt1, e_cnt1, e_ptr1,
                                          _verify_quietly);
        if (ret == _verify_fail_done) {
          // cannot use the current entry to compare against other entries
          continue;
        }
      }

      for (int bkt2 = bkt1; bkt2 < the_table()->table_size(); bkt2++) {
        HashtableEntry<oop, mtSymbol>* e_ptr2 = the_table()->bucket(bkt2);
        int e_cnt2;
        for (e_cnt2 = 0; e_ptr2 != NULL; e_ptr2 = e_ptr2->next(), e_cnt2++) {
          if (bkt1 == bkt2 && e_cnt2 <= e_cnt1) {
            // skip the entries up to and including the one that
            // we're comparing against
            continue;
          }

          if (need_entry_verify) {
            VerifyRetTypes ret = verify_entry(bkt2, e_cnt2, e_ptr2,
                                              _verify_quietly);
            if (ret == _verify_fail_done) {
              // cannot compare against this entry
              continue;
            }
          }

          // compare two entries, report and count any failures:
          if (compare_entries(bkt1, e_cnt1, e_ptr1, bkt2, e_cnt2, e_ptr2)
              != _verify_pass) {
            fail_cnt++;
          }
        }
      }
    }
  }
  return fail_cnt;
}

// Create a new table and using alternate hash code, populate the new table
// with the existing strings.   Set flag to use the alternate hash code afterwards.
void StringTable::rehash_table() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  // This should never happen with -Xshare:dump but it might in testing mode.
  if (DumpSharedSpaces) return;
  StringTable* new_table = new StringTable();

  // Rehash the table
  the_table()->move_to(new_table);

  // Delete the table and buckets (entries are reused in new table).
  delete _the_table;
  // Don't check if we need rehashing until the table gets unbalanced again.
  // Then rehash with a new global seed.
  _needs_rehashing = false;
  _the_table = new_table;
}
