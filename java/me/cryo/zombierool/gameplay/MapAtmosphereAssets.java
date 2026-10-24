package me.cryo.zombierool.gameplay;

import java.nio.file.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Map-local sky images, transported through the dynamic loader without a resource-pack reload. */
public final class MapAtmosphereAssets {
    private static final Map<String,byte[]> textures=new TreeMap<>();
    public static void load(java.io.File root){
        textures.clear();Path folder=me.cryo.zombierool.util.WorldZombieroolPaths.resolve(root,"assets/skyboxes").toPath();
        try{Files.createDirectories(folder);try(var paths=Files.walk(folder,4)){long total=0;for(Path p:paths.filter(Files::isRegularFile).sorted().toList()){
            if(textures.size()>=16||total>=32*1024*1024)break;if(!p.toString().toLowerCase(Locale.ROOT).endsWith(".png"))continue;long size=Files.size(p);if(size<=0||size>8*1024*1024||total+size>32*1024*1024)continue;
            String name=folder.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT);name=name.substring(0,name.length()-4);if(!name.matches("[a-z0-9_./-]+"))continue;
            textures.put("zombierool:world_sky/"+name,Files.readAllBytes(p));total+=size;
        }}}catch(java.io.IOException e){me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Cannot read map skyboxes",e);}
    }
    public static java.util.Set<String> skyIds(){return java.util.Set.copyOf(textures.keySet());}
    public static void sync(ServerPlayer p){textures.forEach((id,data)->MapAtmospherePackets.texture(p,id,data));}
}
