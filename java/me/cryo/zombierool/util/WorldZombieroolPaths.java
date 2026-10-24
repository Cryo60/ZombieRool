package me.cryo.zombierool.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;

public final class WorldZombieroolPaths {
    private WorldZombieroolPaths() {}

    public static File getWorldRoot(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toFile();
    }

    public static File getZombieroolDir(ServerLevel level) {
        return new File(getWorldRoot(level), "zombierool");
    }

    public static File getConfigJson(ServerLevel level) {
        var file=resolve(getZombieroolDir(level),"config.json");file.getParentFile().mkdirs();return file;
    }

    public static File resolve(File root,String legacy){
        String canonical=legacy;
        for(var entry:java.util.Map.of("config.json","config/settings.json","entities","gameplay/entities","weapons","gameplay/weapons","weapon_packs","gameplay/weapon_packs","custom_blocks","assets/blocks","audio","assets/audio","skins","assets/textures","particles/custom","assets/particles","assets/chalks","assets/textures/chalks","assets/skyboxes","assets/textures/skyboxes").entrySet())if(legacy.equals(entry.getKey())||legacy.startsWith(entry.getKey()+"/")){canonical=entry.getValue()+legacy.substring(entry.getKey().length());break;}
        var file=new File(root,canonical);var old=new File(root,legacy);return file.exists()||!old.exists()?file:old;
    }
    public static java.nio.file.Path path(java.nio.file.Path world,String legacy){return resolve(world.resolve("zombierool").toFile(),legacy).toPath();}
    public static File getScriptsDir(ServerLevel level){return new File(getZombieroolDir(level),"scripts");}
    /** Canonical files take precedence over matching legacy names, so callbacks never load twice. */
    public static File[] scripts(ServerLevel level){
        var files=new java.util.TreeMap<String,File>();
        for(var folder:new File[]{new File(getWorldRoot(level),"zr_scripts"),getScriptsDir(level)}){
            var rows=folder.listFiles((dir,name)->name.endsWith(".lua"));if(rows!=null)for(var file:rows)if(!java.nio.file.Files.isSymbolicLink(file.toPath()))files.put(file.getName(),file);
        }
        return me.cryo.zombierool.gameplay.CompatibilityFixes.sortScripts(files.values().toArray(File[]::new));
    }

    public static File ensureZombieroolDir(ServerLevel level) {
        File dir = getZombieroolDir(level);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
