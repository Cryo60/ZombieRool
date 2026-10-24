package me.cryo.zombierool.client;
import java.util.*;
public final class CamoAppearance {
 public static class Profile {public double repeats=2.5,rotation=0,offsetU=0,offsetV=0,opacity=1,animationSpeed=1,artworkScale=1;public int backgroundColor=-1;public boolean fitted=false;public Profile(){}public Profile(double repeats){this.repeats=repeats;}public Profile copy(){var p=new Profile(repeats);p.rotation=rotation;p.offsetU=offsetU;p.offsetV=offsetV;p.opacity=opacity;p.animationSpeed=animationSpeed;p.artworkScale=artworkScale;p.backgroundColor=backgroundColor;p.fitted=fitted;return p;}public void clamp(){artworkScale=clamp(artworkScale,.05,3);backgroundColor=Math.max(-1,Math.min(0xFFFFFF,backgroundColor));repeats=clamp(repeats,.25,12);rotation=clamp(rotation,-180,180);offsetU=clamp(offsetU,-1,1);offsetV=clamp(offsetV,-1,1);opacity=clamp(opacity,0,1);animationSpeed=clamp(animationSpeed,.1,4);}private static double clamp(double v,double a,double b){return Double.isFinite(v)?Math.max(a,Math.min(b,v)):a;}}
 // All shipped camo densities are defined in CamoPatterns.patternScale.
 // Remaining defaults live in Profile, shared by TACZ and native 2D weapons.
 private static final Map<String,Profile> VALUES=new HashMap<>();
 public static Profile get(String id){return VALUES.computeIfAbsent(id,key->new Profile(CamoPatterns.patternScale(key)));}
 public static void reload(){VALUES.clear();}
}
