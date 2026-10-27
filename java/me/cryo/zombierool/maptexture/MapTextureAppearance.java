package me.cryo.zombierool.maptexture;
import java.nio.file.*;
import java.util.*;
/** Per-slot appearance belongs to the world and is synchronized to remote clients. */
public final class MapTextureAppearance {
 private static final float[] opacity=new float[MapTextures.SLOTS];
 private static final int[] colors=new int[MapTextures.SLOTS];
 private static Path file;
 static{Arrays.fill(opacity,1);Arrays.fill(colors,0x4A83A0);}
 public static float opacity(int slot){return slot>=0&&slot<opacity.length?opacity[slot]:1;}
 public static int color(int slot){return slot>=0&&slot<colors.length?colors[slot]:0x4A83A0;}
 public static void color(int slot,int rgb){if(slot>=0&&slot<colors.length)colors[slot]=rgb&0xffffff;}
 public static void client(int slot,float value){if(slot>=0&&slot<opacity.length&&Float.isFinite(value))opacity[slot]=Math.max(0,Math.min(1,value));}
 public static void load(Path root){file=me.cryo.zombierool.util.WorldZombieroolPaths.path(root,"custom_blocks").resolve("appearance.json");Arrays.fill(opacity,1);try{if(Files.isRegularFile(file)){var obj=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();for(int i=0;i<opacity.length;i++)if(obj.has(""+i))client(i,obj.get(""+i).getAsFloat());}}catch(Exception e){me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Cannot load map opacity",e);}}
 public static void set(int slot,float value)throws java.io.IOException{client(slot,value);if(file!=null){var obj=new com.google.gson.JsonObject();for(int i=0;i<opacity.length;i++)obj.addProperty(""+i,opacity[i]);Files.createDirectories(file.getParent());Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj));}}
 private MapTextureAppearance(){}
}
