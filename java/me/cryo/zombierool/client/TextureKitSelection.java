package me.cryo.zombierool.client;

import me.cryo.zombierool.ZombieroolMod;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;

/** Remember names rather than slot numbers: imports may reorder the texture catalog. */
final class TextureKitSelection {
    private static final Properties choices=new Properties();
    private static boolean loaded;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/zombierool-texture-kit.properties");}
    static String worldKey() {
        var local=MapTextureClient.worldRoot();
        if(local!=null)return "world:"+local.toAbsolutePath().normalize();
        var server=Minecraft.getInstance().getCurrentServer();
        return "server:"+(server==null?"local":server.ip);
    }
    private static void load(){
        if(loaded)return;loaded=true;
        if(Files.isRegularFile(file()))try(var in=Files.newInputStream(file())){choices.load(in);}
        catch(IOException e){ZombieroolMod.LOGGER.warn("Cannot read texture kit selection",e);}
    }
    static String texture(String world){load();return choices.getProperty(world+".texture","");}
    static int page(String world){load();try{return Math.max(0,Integer.parseInt(choices.getProperty(world+".page","0")));}catch(NumberFormatException e){return 0;}}
    static void save(String world,String name,int page){
        load();choices.setProperty(world+".texture",name);choices.setProperty(world+".page",Integer.toString(page));
        try{Files.createDirectories(file().getParent());try(var out=Files.newOutputStream(file())){choices.store(out,"Texture kit choices by world/server");}}
        catch(IOException e){ZombieroolMod.LOGGER.warn("Cannot save texture kit selection",e);}
    }
}
