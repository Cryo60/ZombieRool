package me.cryo.zombierool.client.render;

import java.util.*;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import me.cryo.zombierool.client.CamoAppearance;
import me.cryo.zombierool.client.CamoPatterns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Composes a coating into the original sprite; geometry remains the ordinary item geometry. */
public final class SpriteCamoTextures {
 private static final Map<ResourceLocation,Source> SOURCES=new HashMap<>();
 private static final LinkedHashMap<String,Finish> FINISHES=new LinkedHashMap<>(16,.75f,true);
 private static Map<String,SpriteCamoMasks.Mask> masks;private static final Set<String> FAILED=new HashSet<>();
 private record Source(NativeImage image,byte[] mask,int width,int height,double highlight,int x0,int y0,int x1,int y1){}
 private static final class Finish {
  final Source source;final DynamicTexture texture;final ResourceLocation location;long frame=Long.MIN_VALUE,tick=Long.MIN_VALUE;int profile=Integer.MIN_VALUE;
  Finish(Source source){this.source=source;try(var state=new me.cryo.zombierool.client.TextureUploadState()){var image=new NativeImage(source.image.getWidth(),source.image.getHeight(),true);image.copyFrom(source.image);texture=new DynamicTexture(image);location=Minecraft.getInstance().getTextureManager().register("zombierool_sprite_camo",texture);texture.setFilter(false,false);}}
  void close(){Minecraft.getInstance().getTextureManager().release(location);}
 }
 private SpriteCamoTextures(){}
 public static void clear(){if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()){com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(SpriteCamoTextures::clear);return;}FINISHES.values().forEach(Finish::close);FINISHES.clear();SOURCES.values().forEach(s->s.image.close());SOURCES.clear();FAILED.clear();masks=null;}
 private static Source source(ResourceLocation sprite)throws Exception{
  if(SOURCES.containsKey(sprite))return SOURCES.get(sprite);
  var manager=Minecraft.getInstance().getResourceManager();
  if(masks==null)masks=SpriteCamoMasks.load();
  NativeImage image;try(var in=manager.open(new ResourceLocation(sprite.getNamespace(),"textures/"+sprite.getPath()+".png"))){image=NativeImage.read(in);}
  var row=masks.get(sprite.getPath());int w=image.getWidth(),h=image.getHeight();byte[] mask;double highlight=180;
  if(sprite.getNamespace().equals("zombierool")&&row!=null&&row.width()==w&&row.height()==h){mask=row.bits();highlight=row.highlight();}
  else { // Resource-pack replacements use their own silhouette and protected muzzle.
   mask=new byte[(w*h+7)/8];for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int pixel=image.getPixelRGBA(x,y);if((pixel>>>24)>=192&&x>w*.18&&(image.getPixelRGBA(x-1,y)>>>24)>=192&&(image.getPixelRGBA(x+1,y)>>>24)>=192&&(image.getPixelRGBA(x,y-1)>>>24)>=192&&(image.getPixelRGBA(x,y+1)>>>24)>=192)mask[(y*w+x)>>>3]|=(byte)(1<<((y*w+x)&7));}
  }
  if(mask.length!=(w*h+7)/8){image.close();throw new IllegalArgumentException("Invalid sprite mask size");}
  int x0=w,y0=h,x1=0,y1=0;for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((image.getPixelRGBA(x,y)>>>24)>0){x0=Math.min(x0,x);x1=Math.max(x1,x+1);y0=Math.min(y0,y);y1=Math.max(y1,y+1);}
  var result=new Source(image,mask,w,h,Math.max(1,highlight),x0,y0,x1,y1);SOURCES.put(sprite,result);return result;
 }
 public static ResourceLocation texture(ResourceLocation sprite,String camo){
  String id=camo.replace("camo_",""),key=sprite+"|"+id;if(FAILED.contains(key))return null;
  try{
   var pattern=CamoPatterns.spritePattern(id);if(pattern==null)return null;
   var profile=CamoAppearance.get(id);int hash=Objects.hash(profile.repeats,profile.rotation,profile.offsetU,profile.offsetV,profile.opacity,profile.fitted,profile.artworkScale,profile.backgroundColor);
   var finish=FINISHES.get(key);if(finish==null){finish=new Finish(source(sprite));FINISHES.put(key,finish);if(FINISHES.size()>64){var old=FINISHES.entrySet().iterator();var entry=old.next();mcFlush();entry.getValue().close();old.remove();}}
   var mc=Minecraft.getInstance();long tick=mc.level==null?net.minecraft.Util.getMillis()/50:mc.level.getGameTime();
   if(finish.profile!=hash||finish.frame==Long.MIN_VALUE||finish.frame!=pattern.version()&&finish.tick!=tick){compose(finish,pattern.pixels(),profile);finish.profile=hash;finish.frame=pattern.version();finish.tick=tick;}
   return finish.location;
  }catch(Exception error){FAILED.add(key);org.slf4j.LoggerFactory.getLogger(SpriteCamoTextures.class).warn("Sprite camo unavailable for {}",key,error);return null;}
 }
 private static void mcFlush(){Minecraft.getInstance().renderBuffers().bufferSource().endBatch();}
 private static void compose(Finish finish,NativeImage pattern,CamoAppearance.Profile profile){
  var s=finish.source;var output=finish.texture.getPixels();int w=s.width,h=s.height,size=pattern.getWidth();
  double radians=Math.toRadians(profile.rotation),cos=Math.cos(radians),sin=Math.sin(radians),span=Math.max(1,Math.max(s.x1-s.x0,s.y1-s.y0)),cx=(s.x0+s.x1)*.5,cy=(s.y0+s.y1)*.5;
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int original=s.image.getPixelRGBA(x,y);double amount=((s.mask[(y*w+x)>>>3]&(1<<((y*w+x)&7)))!=0?profile.opacity:0);
   if(amount<=0){output.setPixelRGBA(x,y,original);continue;}
   double u=(x+.5-cx)/span,v=(y+.5-cy)/span,ru=cos*u-sin*v,rv=sin*u+cos*v;
   if(profile.fitted){u=ru/profile.artworkScale+.5-profile.offsetU;v=rv/profile.artworkScale+.5-profile.offsetV;}else {u=ru*(profile.repeats/3)+.5+profile.offsetU;v=rv*(profile.repeats/3)+.5+profile.offsetV;}
   int paint;
   if(profile.fitted&&(u<0||v<0||u>=1||v>=1)){int rgb=profile.backgroundColor;paint=rgb<0?pattern.getPixelRGBA(0,0):0xFF000000|((rgb&255)<<16)|(rgb&0xFF00)|((rgb>>>16)&255);}
   else {u-=Math.floor(u);v-=Math.floor(v);paint=pattern.getPixelRGBA(Math.min(size-1,(int)(u*size)),Math.min(pattern.getHeight()-1,(int)(v*pattern.getHeight())));}
   amount*=((paint>>>24)&255)/255.0;
   double luma=.2126*(original&255)+.7152*((original>>>8)&255)+.0722*((original>>>16)&255);
   double shade=Math.max(.18,Math.min(1.15,Math.pow(luma/s.highlight,.65)));
   int result=original&0xFF000000;for(int shift=0;shift<24;shift+=8){int channel=(original>>>shift)&255;int coat=Math.min(255,(int)Math.round(((paint>>>shift)&255)*shade));result|=(int)Math.round(channel*(1-amount)+coat*amount)<<shift;}
   output.setPixelRGBA(x,y,result);
  }
  try(var state=new me.cryo.zombierool.client.TextureUploadState()){finish.texture.upload();finish.texture.setFilter(false,false);}
 }
}
