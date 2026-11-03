// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.nnue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Actual current-format Stockfish NNUE parameters and scalar quantized
 * inference. Adapted from nnue_architecture.h, network.cpp and NNUE layers.
 * Copyright (C) 2004-2026 The Stockfish developers (see STOCKFISH-AUTHORS).
 */
public final class Network {

    public static final String DEFAULT_FILE = "nn-1a298aa575a0.nnue";
    public static final int WIDTH = 1024;
    public final short[] biases, psqWeights;
    public final byte[] threatWeights, pairWeights;
    public final int[] psqPsqt, threatPsqt, pairPsqt;
    private final Layer[] layers = new Layer[8];
    public final String description;

    public static int transformerHash() {
        int h = 0;
        for (int x : new int[]{0x2e6b9d04, 0x86f2b1dd, 0x7f234cb8}) {
            h = Integer.rotateLeft(h, 1) ^ x;
        }
        return h ^ (WIDTH * 2);
    }

    private static int affineHash(int h, int out) {
        return (0xcc03dae4 + out) ^ Integer.rotateRight(h, 1);
    }

    public static int architectureHash() {
        int h = 0xec42e90d ^ (WIDTH * 2);
        h = affineHash(h, 32) + 0x538d24c7;
        h = affineHash(h, 32) + 0x538d24c7;
        return affineHash(h, 1);
    }

    private Network(InputStream input) throws IOException {
        try (NetworkReader r = new NetworkReader(input)) {
            r.expect(0x6a448afa, "NNUE version");
            r.expect(transformerHash() ^ architectureHash(), "NNUE architecture");
            int length = r.i32();
            if (length < 0 || length > 1_048_576) {
                throw new IOException("Invalid NNUE description length");
            }
            description = new String(r.bytes(length), StandardCharsets.UTF_8);
            r.expect(transformerHash(), "Feature transformer");
            biases = r.shorts(WIDTH);
            threatWeights = r.bytes(Features.THREATS * WIDTH);
            threatPsqt = r.ints(Features.THREATS * 8);
            pairWeights = r.bytes(Features.PAIRS * WIDTH);
            pairPsqt = r.ints(Features.PAIRS * 8);
            psqWeights = r.shorts(Features.PSQ * WIDTH);
            psqPsqt = r.ints(Features.PSQ * 8);
            for (int b = 0; b < 8; b++) {
                r.expect(architectureHash(), "Layer stack " + b);
                layers[b] = new Layer(r);
            }
            r.eof();
        }
    }

    public static Network load(Path p) throws IOException {
        return new Network(Files.newInputStream(p));
    }

    public static Network bundled() throws IOException {
        InputStream in = Network.class.getResourceAsStream("/networks/" + DEFAULT_FILE);
        if (in != null) {
            return new Network(in);
        }
        Path p = Path.of("networks", DEFAULT_FILE);
        if (Files.isRegularFile(p)) {
            return load(p);
        }
        try {
            Path location
                    = Path.of(Network.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                Path besideJar
                        = location.toAbsolutePath().getParent().resolve("networks").resolve(DEFAULT_FILE);
                if (Files.isRegularFile(besideJar)) {
                    return load(besideJar);
                }
            }
        } catch (java.net.URISyntaxException e) {
            throw new IOException("Invalid engine path", e);
        }
        throw new IOException(
                "Required NNUE missing. Keep the networks folder beside JengaFish.jar or set EvalFile to "
                + DEFAULT_FILE);
    }

    /**
     * Add or subtract one feature to an accumulator; signed 16-bit wrap matches
     * C++ SIMD.
     */
    public void update(short[] acc, int[] psqt, int feature, int sign) {
        if (feature < Features.PSQ) {
            int start = feature * WIDTH;
            for (int i = 0; i < WIDTH; i++) {
                acc[i] = (short) (acc[i] + sign * psqWeights[start + i]);
            }
            for (int b = 0; b < 8; b++) {
                psqt[b] += sign * psqPsqt[feature * 8 + b];
            }
        } else {
            int idx = feature - Features.PSQ;
            byte[] w;
            int[] q;
            if (idx < Features.THREATS) {
                w = threatWeights;
                q = threatPsqt;
            } else {
                idx -= Features.THREATS;
                w = pairWeights;
                q = pairPsqt;
            }
            int start = idx * WIDTH;
            for (int i = 0; i < WIDTH; i++) {
                acc[i] = (short) (acc[i] + sign * w[start + i]);
            }
            for (int b = 0; b < 8; b++) {
                psqt[b] += sign * q[idx * 8 + b];
            }
        }
    }

    public int positional(int bucket, int[] transformed, int[] temp, int[] concat) {
        Layer l = layers[bucket];
        for (int o = 0; o < 32; o++) {
            int sum = l.b0[o], off = o * WIDTH;
            for (int i = 0; i < WIDTH; i++) {
                sum += l.w0[off + i] * transformed[i];
            }
            temp[o] = sum;
            concat[o] = (int) Math.min(127, ((long) sum * sum) >> 21);
            concat[32 + o] = Math.max(0, Math.min(127, sum >> 7));
        }
        int skip = temp[30] - temp[31];
        for (int o = 0; o < 32; o++) {
            int sum = l.b1[o], off = o * 64;
            for (int i = 0; i < 64; i++) {
                sum += l.w1[off + i] * concat[i];
            }
            concat[64 + o] = (int) Math.min(127, ((long) sum * sum) >> 19);
            concat[96 + o] = Math.max(0, Math.min(127, sum >> 6));
        }
        int sum = l.b2[0] + skip;
        for (int i = 0; i < 128; i++) {
            sum += l.w2[i] * concat[i];
        }
        return (int) ((long) sum * 9600 / 16384) / 16;
    }

    private static final class Layer {

        final int[] b0, b1, b2;
        final byte[] w0, w1, w2;

        Layer(NetworkReader r) throws IOException {
            b0 = r.rawInts(32);
            w0 = r.bytes(32 * WIDTH);
            b1 = r.rawInts(32);
            w1 = r.bytes(32 * 64);
            b2 = r.rawInts(1);
            w2 = r.bytes(128);
        }
    }
}
