/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "prims/jvm.h"
#include "utilities/quickSort.hpp"
#include "unittest.hpp"

static int int_comparator(int a, int b) {
  if (a == b) {
    return 0;
  } else if (a < b) {
    return -1;
  }

  // a > b
  return 1;
}

TEST(utilities, quicksort) {
  int test_array[] = {3,2,1};
  QuickSort::sort(test_array, 3, int_comparator, false);

  ASSERT_EQ(1, test_array[0]);
  ASSERT_EQ(2, test_array[1]);
  ASSERT_EQ(3, test_array[2]);
}
