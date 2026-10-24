package me.cryo.zombierool.util;

import java.nio.file.*;
import java.util.*;

/** One-time, verified migration before a world's resources or scripts are read. */
public final class WorldLayoutMigration {
    private static final Set<Path> checked=new HashSet<>();
    private record Entry(Path source,Path target,String hash){}
    private static final Map<String,String> LAYOUT=Map.ofEntries(Map.entry("config.json","config/settings.json"),Map.entry("entities","gameplay/entities"),Map.entry("weapons","gameplay/weapons"),Map.entry("weapon_packs","gameplay/weapon_packs"),Map.entry("custom_blocks","assets/blocks"),Map.entry("audio","assets/audio"),Map.entry("skins","assets/textures"),Map.entry("particles/custom","assets/particles"),Map.entry("assets/chalks","assets/textures/chalks"),Map.entry("assets/skyboxes","assets/textures/skyboxes"));
    private static String hash(Path path)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    public static synchronized void migrate(Path world){
        world=world.toAbsolutePath().normalize();if(!checked.add(world))return;Path root=world.resolve("zombierool");var rows=new ArrayList<Entry>();
        try{
            for(var entry:LAYOUT.entrySet())collect(root.resolve(entry.getKey()),root.resolve(entry.getValue()),rows);
            collect(world.resolve("zr_scripts"),root.resolve("scripts"),rows);
            if(rows.isEmpty())return;var targets=new HashMap<Path,String>();for(var row:rows){var previous=targets.putIfAbsent(row.target,row.hash);if(previous!=null&&!previous.equals(row.hash))throw new java.io.IOException("Conflicting legacy files target: "+row.target);}
            Path backup=world.getParent().resolve(".zombierool-backups").resolve(world.getFileName().toString()).resolve("layout-"+System.currentTimeMillis());
            // Back up and verify every source before removing anything.
            for(var row:rows){Path saved=backup.resolve(world.relativize(row.source));Files.createDirectories(saved.getParent());Files.copy(row.source,saved);if(!hash(saved).equals(row.hash))throw new java.io.IOException("Backup verification failed: "+row.source);}
            for(var row:rows){Files.createDirectories(row.target.getParent());if(!Files.exists(row.target))Files.copy(row.source,row.target);if(!hash(row.target).equals(row.hash))throw new java.io.IOException("Migration verification failed: "+row.target);}
            for(var row:rows){if(!hash(row.source).equals(row.hash)||!hash(row.target).equals(row.hash))throw new java.io.IOException("File changed during migration: "+row.source);Files.delete(row.source);}
            for(var name:LAYOUT.keySet())empty(root.resolve(name));empty(world.resolve("zr_scripts"));empty(root.resolve("particles"));
            me.cryo.zombierool.ZombieroolMod.LOGGER.info("Migrated {} map authoring files; verified backup: {}",rows.size(),backup);
        }catch(Exception e){checked.remove(world);me.cryo.zombierool.ZombieroolMod.LOGGER.error("Map folder migration stopped; original or verified backup files are preserved",e);}
    }
    private static void collect(Path source,Path target,List<Entry> out)throws Exception{
        if(!Files.exists(source))return;if(Files.isSymbolicLink(source))throw new java.io.IOException("Linked migration source: "+source);
        if(Files.isDirectory(source)){try(var paths=Files.list(source)){for(var child:paths.sorted().toList())collect(child,target.resolve(child.getFileName()),out);}return;}
        if(!Files.isRegularFile(source))return;String digest=hash(source);
        if(Files.exists(target)&&!hash(target).equals(digest))throw new java.io.IOException("Conflicting old/new map files: "+source+" and "+target);
        out.add(new Entry(source,target,digest));
    }
    private static void empty(Path folder)throws java.io.IOException{if(!Files.isDirectory(folder)||Files.isSymbolicLink(folder))return;try(var paths=Files.list(folder)){for(var path:paths.toList())if(Files.isDirectory(path))empty(path);}try{Files.delete(folder);}catch(DirectoryNotEmptyException ignored){}}
    private WorldLayoutMigration(){}
}
