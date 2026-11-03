// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.puzzles;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Append-only attempt history. Distinct successful puzzles are counted once. */
public final class PuzzleProgressStore {
    private final Path file;
    private final Set<String> solved=new HashSet<>();
    private long attempts;
    public PuzzleProgressStore() throws IOException {
        this(PuzzleRepository.storageDirectory().resolve("progress.tsv"));
    }
    public PuzzleProgressStore(Path file) throws IOException {
        this.file=file;
        Files.createDirectories(file.toAbsolutePath().getParent());
        if(Files.exists(file)) try(BufferedReader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            String line;
            while((line=in.readLine())!=null) {
                String[] parts=line.split("\t");
                if(parts.length==3) {
                    attempts++;
                    if(parts[2].equals("SOLVED")) solved.add(parts[1]);
                }
            }
        }
    }
    public synchronized void record(String id, String outcome) throws IOException {
        if(!id.matches("[A-Za-z0-9_-]+") || !Set.of("SOLVED","ASSISTED","FAILED","REVEALED").contains(outcome))
            throw new IllegalArgumentException("Invalid progress entry");
        Files.writeString(file,System.currentTimeMillis()+"\t"+id+"\t"+outcome+"\n",
            StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        attempts++; if(outcome.equals("SOLVED")) solved.add(id);
    }
    public synchronized int solvedCount() { return solved.size(); }
    public synchronized long attempts() { return attempts; }
}
