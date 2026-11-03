// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.puzzles;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Streaming UTF-8 CSV parser supporting quoting, escaped quotes and CRLF. */
public final class PuzzleCsvReader implements AutoCloseable {
    private final FileInputStream source;
    private final PushbackReader reader;
    private final Map<String,Integer> columns = new HashMap<>();
    public PuzzleCsvReader(Path file) throws IOException {
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zst"))
            throw new IOException("Extract the .csv.zst archive first, then select the resulting .csv file.");
        source = new FileInputStream(file.toFile());
        reader = new PushbackReader(new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8), 65536), 1);
        try {
            List<String> header = row();
            if (header == null) throw new IOException("The CSV is empty.");
            for (int i=0;i<header.size();i++) columns.put(header.get(i).replace("\uFEFF", "").trim(), i);
            for (String key : List.of("PuzzleId", "FEN", "Moves", "Rating", "Themes"))
                if (!columns.containsKey(key)) throw new IOException("Missing CSV column: " + key);
        } catch (IOException | RuntimeException e) { reader.close(); throw e; }
    }
    public long bytesRead() throws IOException { return source.getChannel().position(); }
    public Puzzle next() throws IOException {
        List<String> r;
        do { r = row(); } while (r != null && r.size()==1 && r.get(0).isBlank());
        if (r == null) return null;
        try {
            String tags = value(r,"Themes").trim();
            return new Puzzle(value(r,"PuzzleId"), value(r,"FEN"),
                Arrays.asList(value(r,"Moves").trim().split("\\s+")),
                Integer.parseInt(value(r,"Rating")),
                tags.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(tags.split("\\s+"))),
                columns.containsKey("GameUrl") ? value(r,"GameUrl") : "");
        } catch (RuntimeException e) { throw new IOException("Invalid puzzle row: " + e.getMessage(), e); }
    }
    private String value(List<String> row, String key) throws IOException {
        int i = columns.get(key);
        if (i >= row.size()) throw new IOException("Incomplete CSV row (" + key + ")");
        return row.get(i);
    }
    private List<String> row() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted=false, afterQuote=false, seen=false;
        for (;;) {
            int ch=reader.read();
            if (ch<0) {
                if (quoted) throw new IOException("Unclosed CSV quote");
                if (!seen && fields.isEmpty() && field.length()==0) return null;
                fields.add(field.toString()); return fields;
            }
            seen=true;
            if (quoted) {
                if (ch=='"') {
                    int next=reader.read();
                    if (next=='"') field.append('"');
                    else { quoted=false; afterQuote=true; if(next>=0) reader.unread(next); }
                } else field.append((char)ch);
            } else if (ch==',') {
                fields.add(field.toString()); field.setLength(0); afterQuote=false;
            } else if (ch=='\n' || ch=='\r') {
                if(ch=='\r') { int n=reader.read(); if(n>=0 && n!='\n') reader.unread(n); }
                fields.add(field.toString()); return fields;
            } else if(ch=='"' && field.length()==0 && !afterQuote) quoted=true;
            else {
                if (afterQuote || ch=='"') throw new IOException("Unexpected character after CSV quote");
                field.append((char)ch);
            }
            if (field.length()>1_000_000 || fields.size()>100)
                throw new IOException("CSV record is unexpectedly large");
        }
    }
    @Override public void close() throws IOException { reader.close(); }
}
