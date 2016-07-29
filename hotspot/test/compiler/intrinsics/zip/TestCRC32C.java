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
 */

/**
 * @test
 * @bug 8073583
 * @summary C2 support for CRC32C on SPARC
 *
 * @run main/othervm/timeout=600 -Xbatch compiler.intrinsics.zip.TestCRC32C -m
 */

package compiler.intrinsics.zip;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

public class TestCRC32C {
    public static void main(String[] args) {
        int offset = Integer.getInteger("offset", 0);
        int msgSize = Integer.getInteger("msgSize", 512);
        boolean multi = false;
        int iters = 20000;
        int warmupIters = 20000;

        if (args.length > 0) {
            if (args[0].equals("-m")) {
                multi = true;
            } else {
                iters = Integer.valueOf(args[0]);
            }
            if (args.length > 1) {
                warmupIters = Integer.valueOf(args[1]);
            }
        }

        if (multi) {
            test_multi(warmupIters);
            return;
        }

        System.out.println(" offset = " + offset);
        System.out.println("msgSize = " + msgSize + " bytes");
        System.out.println("  iters = " + iters);

        byte[] b = initializedBytes(msgSize, offset);

        CRC32C crc0 = new CRC32C();
        CRC32C crc1 = new CRC32C();
        CRC32C crc2 = new CRC32C();

        crc0.update(b, offset, msgSize);

        System.out.println("-------------------------------------------------------");

        /* warm up */
        for (int i = 0; i < warmupIters; i++) {
            crc1.reset();
            crc1.update(b, offset, msgSize);
        }

        /* measure performance */
        long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            crc1.reset();
            crc1.update(b, offset, msgSize);
        }
        long end = System.nanoTime();
        double total = (double)(end - start)/1e9;         // in seconds
        double thruput = (double)msgSize*iters/1e6/total; // in MB/s
        System.out.println("CRC32C.update(byte[]) runtime = " + total + " seconds");
        System.out.println("CRC32C.update(byte[]) throughput = " + thruput + " MB/s");

        /* check correctness */
        for (int i = 0; i < iters; i++) {
            crc1.reset();
            crc1.update(b, offset, msgSize);
            if (!check(crc0, crc1)) break;
        }
        report("CRCs", crc0, crc1);

        System.out.println("-------------------------------------------------------");

        ByteBuffer buf = ByteBuffer.allocateDirect(msgSize);
        buf.put(b, offset, msgSize);
        buf.flip();

        /* warm up */
        for (int i = 0; i < warmupIters; i++) {
            crc2.reset();
            crc2.update(buf);
            buf.rewind();
        }

        /* measure performance */
        start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            crc2.reset();
            crc2.update(buf);
            buf.rewind();
        }
        end = System.nanoTime();
        total = (double)(end - start)/1e9;         // in seconds
        thruput = (double)msgSize*iters/1e6/total; // in MB/s
        System.out.println("CRC32C.update(ByteBuffer) runtime = " + total + " seconds");
        System.out.println("CRC32C.update(ByteBuffer) throughput = " + thruput + " MB/s");

        /* check correctness */
        for (int i = 0; i < iters; i++) {
            crc2.reset();
            crc2.update(buf);
            buf.rewind();
            if (!check(crc0, crc2)) break;
        }
        report("CRCs", crc0, crc2);

        System.out.println("-------------------------------------------------------");
    }

    private static void report(String s, Checksum crc0, Checksum crc1) {
        System.out.printf("%s: crc0 = %08x, crc1 = %08x\n",
                          s, crc0.getValue(), crc1.getValue());
    }

    private static boolean check(Checksum crc0, Checksum crc1) {
        if (crc0.getValue() != crc1.getValue()) {
            System.err.printf("ERROR: crc0 = %08x, crc1 = %08x\n",
                              crc0.getValue(), crc1.getValue());
            return false;
        }
        return true;
    }

    private static byte[] initializedBytes(int M, int offset) {
        byte[] bytes = new byte[M + offset];
        for (int i = 0; i < offset; i++) {
            bytes[i] = (byte) i;
        }
        for (int i = offset; i < bytes.length; i++) {
            bytes[i] = (byte) (i - offset);
        }
        return bytes;
    }

    private static void test_multi(int iters) {
        int len1 = 8;    // the  8B/iteration loop
        int len2 = 32;   // the 32B/iteration loop
        int len3 = 4096; // the 4KB/iteration loop

        byte[] b = initializedBytes(len3*16, 0);
        int[] offsets = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 16, 32, 64, 128, 256, 512 };
        int[] sizes = { 0, 1, 2, 3, 4, 5, 6, 7,
                        len1, len1+1, len1+2, len1+3, len1+4, len1+5, len1+6, len1+7,
                        len1*2, len1*2+1, len1*2+3, len1*2+5, len1*2+7,
                        len2, len2+1, len2+3, len2+5, len2+7,
                        len2*2, len2*4, len2*8, len2*16, len2*32, len2*64,
                        len3, len3+1, len3+3, len3+5, len3+7,
                        len3*2, len3*4, len3*8,
                        len1+len2, len1+len2+1, len1+len2+3, len1+len2+5, len1+len2+7,
                        len1+len3, len1+len3+1, len1+len3+3, len1+len3+5, len1+len3+7,
                        len2+len3, len2+len3+1, len2+len3+3, len2+len3+5, len2+len3+7,
                        len1+len2+len3, len1+len2+len3+1, len1+len2+len3+3,
                        len1+len2+len3+5, len1+len2+len3+7,
                        (len1+len2+len3)*2, (len1+len2+len3)*2+1, (len1+len2+len3)*2+3,
                        (len1+len2+len3)*2+5, (len1+len2+len3)*2+7,
                        (len1+len2+len3)*3, (len1+len2+len3)*3-1, (len1+len2+len3)*3-3,
                        (len1+len2+len3)*3-5, (len1+len2+len3)*3-7 };
        CRC32C[] crc0 = new CRC32C[offsets.length*sizes.length];
        CRC32C[] crc1 = new CRC32C[offsets.length*sizes.length];
        int i, j, k;

        System.out.printf("testing %d cases ...\n", offsets.length*sizes.length);

        /* set the result from interpreter as reference */
        for (i = 0; i < offsets.length; i++) {
            for (j = 0; j < sizes.length; j++) {
                crc0[i*sizes.length + j] = new CRC32C();
                crc1[i*sizes.length + j] = new CRC32C();
                crc0[i*sizes.length + j].update(b, offsets[i], sizes[j]);
            }
        }

        /* warm up the JIT compiler and get result */
        for (k = 0; k < iters; k++) {
            for (i = 0; i < offsets.length; i++) {
                for (j = 0; j < sizes.length; j++) {
                    crc1[i*sizes.length + j].reset();
                    crc1[i*sizes.length + j].update(b, offsets[i], sizes[j]);
                }
            }
        }

        /* check correctness */
        for (i = 0; i < offsets.length; i++) {
            for (j = 0; j < sizes.length; j++) {
                if (!check(crc0[i*sizes.length + j], crc1[i*sizes.length + j])) {
                    System.out.printf("offsets[%d] = %d", i, offsets[i]);
                    System.out.printf("\tsizes[%d] = %d\n", j, sizes[j]);
                }
            }
        }
    }
}
