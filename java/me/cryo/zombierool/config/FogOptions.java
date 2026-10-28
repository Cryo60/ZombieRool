package me.cryo.zombierool.config;
import net.minecraft.nbt.CompoundTag;
/** Optional controls preserve existing maps' appearance until enabled. */
public final class FogOptions {
 private FogOptions(){}
 public static CompoundTag sanitize(CompoundTag input){
  var n=new CompoundTag();n.putString("shape",input.getString("shape").equals("sphere")?"sphere":"cylinder");
  number(n,input,"opacity",1,0,1);number(n,input,"pulseAmount",.25,0,1);number(n,input,"pulseSeconds",20,1,300);
  number(n,input,"height",96,-2048,2048);number(n,input,"heightFade",16,1,512);
  n.putBoolean("heightEnabled",input.getBoolean("heightEnabled"));
  n.putBoolean("pulseOverride",input.getBoolean("pulseOverride"));n.putBoolean("pulse",input.getBoolean("pulse"));return n;
 }
 private static void number(CompoundTag n,CompoundTag in,String key,double fallback,double min,double max){double v=in.contains(key,99)?in.getDouble(key):fallback;n.putDouble(key,Double.isFinite(v)?Math.max(min,Math.min(max,v)):fallback);}
}
