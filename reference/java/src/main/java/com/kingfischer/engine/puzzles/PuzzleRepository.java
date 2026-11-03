// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.puzzles;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** Disk-backed database. Call import/search/load on a worker thread, never JavaFX. */
public final class PuzzleRepository {
    public record Matches(long[] offsets) { public int count() { return offsets.length; } }
    private final Path directory;
    private final Set<String> themes;
    private final long count;
    private PuzzleRepository(Path directory, Properties meta) {
        this.directory=directory;
        this.themes=Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(meta.getProperty("themes", "").split(" "))));
        this.count=Long.parseLong(meta.getProperty("count"));
    }
    public Set<String> themes() { return themes; }
    public long count() { return count; }
    public static Path storageDirectory() {
        return Path.of(System.getProperty("user.home"), ".kingfischer", "puzzles");
    }
    private static void check(BooleanSupplier cancel) {
        if (cancel.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    public static PuzzleRepository open(Path csv, BooleanSupplier cancel,
            BiConsumer<Double,String> progress) throws IOException {
        csv=csv.toRealPath();
        long size=Files.size(csv), modified=Files.getLastModifiedTime(csv).toMillis();
        String signature=csv.toString()+"|"+size+"|"+modified+"|format-1";
        String key;
        try { key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IOException(e); }
        Path parent=storageDirectory().resolve("indexes"); Files.createDirectories(parent);
        Path target=parent.resolve(key);
        if(Files.isRegularFile(target.resolve("metadata.properties"))) {
            Properties meta=new Properties();
            try(InputStream in=Files.newInputStream(target.resolve("metadata.properties"))) { meta.load(in); }
            if(signature.equals(meta.getProperty("signature")) && Files.isRegularFile(target.resolve("data.bin"))) {
                progress.accept(1.0,"Using saved puzzle index");
                return new PuzzleRepository(target,meta);
            }
            throw new IOException("Damaged puzzle index. Delete this folder and import again: " + target);
        }
        Path temp=Files.createTempDirectory(parent,"import-");
        boolean complete=false;
        Map<String,DataOutputStream> indexes=new HashMap<>();
        try {
            long rows=0, offset=0;
            Set<String> tags=new TreeSet<>();
            try(PuzzleCsvReader reader=new PuzzleCsvReader(csv);
                DataOutputStream data=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp.resolve("data.bin")),65536))) {
                Puzzle p;
                while((p=reader.next())!=null) {
                    check(cancel);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream(256);
                    try(DataOutputStream out=new DataOutputStream(bytes)) {
                        out.writeUTF(p.id()); out.writeUTF(p.fen());
                        out.writeUTF(String.join(" ",p.moves())); out.writeInt(p.rating());
                        out.writeUTF(String.join(" ",p.themes())); out.writeUTF(p.gameUrl());
                    }
                    data.writeInt(bytes.size()); bytes.writeTo(data);
                    Set<String> keys=new HashSet<>(p.themes()); keys.add("_all");
                    for(String tag:keys) {
                        if(!tag.matches("[A-Za-z0-9_]+") || tag.length()>100)
                            throw new IOException("Invalid theme tag: "+tag);
                        if(indexes.size()>512) throw new IOException("Unexpected number of themes");
                        DataOutputStream idx=indexes.get(tag);
                        if(idx==null) {
                            idx=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp.resolve(tag+".idx")),16384));
                            indexes.put(tag,idx);
                        }
                        idx.writeInt(p.rating()); idx.writeLong(offset);
                    }
                    tags.addAll(p.themes()); offset+=4L+bytes.size(); rows++;
                    if(rows%4096==0) progress.accept(size==0?0.0:(double)reader.bytesRead()/size,
                        String.format("Importing %,d puzzles…",rows));
                }
            }
            for(DataOutputStream stream:indexes.values()) stream.close();
            indexes.clear();
            check(cancel);
            if(rows==0) throw new IOException("No puzzles found in this CSV");
            if(Files.size(csv)!=size || Files.getLastModifiedTime(csv).toMillis()!=modified)
                throw new IOException("The CSV changed during import. Please try again.");
            Properties meta=new Properties();
            meta.setProperty("signature",signature); meta.setProperty("count",Long.toString(rows));
            meta.setProperty("themes",String.join(" ",tags));
            try(OutputStream out=Files.newOutputStream(temp.resolve("metadata.properties"))) { meta.store(out,"KingFischer puzzle index v1"); }
            try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,target); }
            complete=true;
            progress.accept(1.0,String.format("Ready: %,d puzzles",rows));
            return new PuzzleRepository(target,meta);
        } finally {
            for(DataOutputStream stream:indexes.values()) try { stream.close(); } catch(IOException ignored) { }
            if(!complete && Files.exists(temp)) {
                try(var files=Files.walk(temp)) {
                    for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                }
            }
        }
    }
    public Matches search(PuzzleFilter filter, BooleanSupplier cancel) throws IOException {
        String tag=filter.theme().isEmpty()?"_all":filter.theme();
        if(!tag.equals("_all") && !themes.contains(tag)) return new Matches(new long[0]);
        long[] matches=new long[4096]; int count=0;
        Path index=directory.resolve(tag+".idx");
        long length=Files.size(index);
        if(length%12!=0) throw new IOException("Damaged theme index");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(index),65536))) {
            for(long i=0;i<length/12;i++) {
                if(i%4096==0) check(cancel);
                int rating=in.readInt(); long offset=in.readLong();
                if(rating>=filter.minimum() && rating<=filter.maximum()) {
                    if(count==matches.length) matches=Arrays.copyOf(matches,matches.length+matches.length/2);
                    matches[count++]=offset;
                }
            }
        }
        check(cancel);
        return new Matches(Arrays.copyOf(matches,count));
    }
    public Puzzle load(long offset) throws IOException {
        try(RandomAccessFile file=new RandomAccessFile(directory.resolve("data.bin").toFile(),"r")) {
            if(offset<0 || offset>=file.length()) throw new IOException("Invalid puzzle offset");
            file.seek(offset); int size=file.readInt();
            if(size<0 || size>1_000_000) throw new IOException("Damaged puzzle record");
            byte[] bytes=new byte[size]; file.readFully(bytes);
            try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
                String id=in.readUTF(),fen=in.readUTF(),moves=in.readUTF(); int rating=in.readInt();
                String tags=in.readUTF(),url=in.readUTF();
                return new Puzzle(id,fen,Arrays.asList(moves.split(" ")),rating,
                    tags.isBlank()?Set.of():new HashSet<>(Arrays.asList(tags.split(" "))),url);
            }
        }
    }
}
