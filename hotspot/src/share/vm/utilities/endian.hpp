/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_ENDIAN_HPP
#define SHARE_VM_UTILITIES_ENDIAN_HPP

#include "utilities/globalDefinitions.hpp"

// Selectable endian handling. Endian handlers are used when accessing values
// that are of unknown (until runtime) endian.  The only requirement of the values
// accessed are that they are aligned to proper size boundaries (no misalignment.)
// To select an endian handler, one should call Endian::get_handler(big_endian);
// Where big_endian is true if big endian is required and false otherwise.  The
// native endian handler can be fetched with Endian::get_native_handler();
// To retrieve a value using the approprate endian, use one of the overloaded
// calls to get. To set a value, then use one of the overloaded set calls.
// Ex.
//      s4 value; // Imported value;
//      ...
//      Endian* endian = Endian::get_handler(true);  // Use big endian
//      s4 corrected = endian->get(value);
//      endian->set(value, 1);
//
class Endian {
public:
  virtual u2 get(u2 x) = 0;
  virtual u4 get(u4 x) = 0;
  virtual u8 get(u8 x) = 0;
  virtual s2 get(s2 x) = 0;
  virtual s4 get(s4 x) = 0;
  virtual s8 get(s8 x) = 0;

  virtual void set(u2& x, u2 y) = 0;
  virtual void set(u4& x, u4 y) = 0;
  virtual void set(u8& x, u8 y) = 0;
  virtual void set(s2& x, s2 y) = 0;
  virtual void set(s4& x, s4 y) = 0;
  virtual void set(s8& x, s8 y) = 0;

  // Quick little endian test.
  static bool is_little_endian() {  u4 x = 1; return *(u1 *)&x != 0; }

  // Quick big endian test.
  static bool is_big_endian() { return !is_little_endian(); }

  // Select an appropriate endian handler.
  static Endian* get_handler(bool big_endian);

  // Return the native endian handler.
  static Endian* get_native_handler();
};

// Normal endian handling.
class NativeEndian : public Endian {
private:
  static NativeEndian _native;

public:
  u2 get(u2 x);
  u4 get(u4 x);
  u8 get(u8 x);
  s2 get(s2 x);
  s4 get(s4 x);
  s8 get(s8 x);

  void set(u2& x, u2 y);
  void set(u4& x, u4 y);
  void set(u8& x, u8 y);
  void set(s2& x, s2 y);
  void set(s4& x, s4 y);
  void set(s8& x, s8 y);

  static Endian* get_native() { return &_native; }
};

// Swapping endian handling.
class SwappingEndian : public Endian {
private:
  static SwappingEndian _swapping;

public:
  u2 get(u2 x);
  u4 get(u4 x);
  u8 get(u8 x);
  s2 get(s2 x);
  s4 get(s4 x);
  s8 get(s8 x);

  void set(u2& x, u2 y);
  void set(u4& x, u4 y);
  void set(u8& x, u8 y);
  void set(s2& x, s2 y);
  void set(s4& x, s4 y);
  void set(s8& x, s8 y);

  static Endian* get_swapping() { return &_swapping; }
};
#endif // SHARE_VM_UTILITIES_ENDIAN_HPP
