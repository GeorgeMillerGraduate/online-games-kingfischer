// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.nnue;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Strict little-endian/signed-LEB128 reader. Rejects truncation, excess bytes
 * and wrong shapes.
 */
public final class NetworkReader implements AutoCloseable {

    private final InputStream in;

    public NetworkReader(InputStream in) {
        this.in = new BufferedInputStream(in, 1 << 20);
    }

    public int u8() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("Truncated NNUE");
        }
        return b;
    }

    public int i32() throws IOException {
        return u8() | u8() << 8 | u8() << 16 | u8() << 24;
    }

    public byte[] bytes(int n) throws IOException {
        byte[] a = in.readNBytes(n);
        if (a.length != n) {
            throw new EOFException("Truncated NNUE");
        }
        return a;
    }

    public void expect(int n, String what) throws IOException {
        int actual = i32();
        if (actual != n) {
            throw new IOException(what + " mismatch: " + Integer.toUnsignedString(actual, 16));
        }
    }

    private long remaining;

    private void startLeb() throws IOException {
        if (!new String(bytes(17), StandardCharsets.US_ASCII).equals("COMPRESSED_LEB128")) {
            throw new IOException("Missing LEB128 header");
        }
        remaining = Integer.toUnsignedLong(i32());
    }

    private int leb() throws IOException {
        long value = 0;
        int shift = 0, b;
        do {
            if (remaining-- <= 0 || shift >= 35) {
                throw new IOException("Malformed LEB128");
            }
            b = u8();
            value |= (long) (b & 127) << shift;
            shift += 7;
        } while ((b & 128) != 0);
        if ((b & 64) != 0) {
            value |= (-1L) << shift;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException("LEB128 integer overflow");
        }
        return (int) value;
    }

    public short[] shorts(int n) throws IOException {
        startLeb();
        short[] a = new short[n];
        for (int i = 0; i < n; i++) {
            int x = leb();
            if (x < Short.MIN_VALUE || x > Short.MAX_VALUE) {
                throw new IOException("NNUE short overflow");
            }
            a[i] = (short) x;
        }
        endLeb();
        return a;
    }

    public int[] ints(int n) throws IOException {
        startLeb();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = leb();
        }
        endLeb();
        return a;
    }

    private void endLeb() throws IOException {
        if (remaining != 0) {
            throw new IOException("NNUE compressed block size mismatch");
        }
    }

    public int[] rawInts(int n) throws IOException {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i32();
        }
        return a;
    }

    public void eof() throws IOException {
        if (in.read() != -1) {
            throw new IOException("Unexpected trailing NNUE data");
        }
    }

    public void close() throws IOException {
        in.close();
    }
}
