package me.cryo.zombierool.client.render;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import me.cryo.zombierool.util.InternalResources;
/** Packed, lossless one-bit paint masks; no Base64/JSON expansion at runtime. */
public final class SpriteCamoMasks {
 public record Mask(int width,int height,double highlight,byte[] bits){public boolean painted(int x,int y){int i=y*width+x;return (bits[i>>>3]&(1<<(i&7)))!=0;}}
 private SpriteCamoMasks(){}
 public static Map<String,Mask> load()throws IOException{
  try(var raw=InternalResources.open("visuals/sprite-masks.bin.gz");var input=new DataInputStream(new GZIPInputStream(raw))){
   if(input.readInt()!=0x5A52534D||input.readUnsignedByte()!=1)throw new IOException("Invalid sprite mask format");
   int count=input.readUnsignedShort();if(count>1024)throw new IOException("Invalid mask count");var result=new LinkedHashMap<String,Mask>();
   for(int n=0;n<count;n++){
    String name=input.readUTF();int width=input.readUnsignedShort(),height=input.readUnsignedShort();double highlight=input.readDouble();
    if(width<1||height<1||width>4096||height>4096||!Double.isFinite(highlight)||highlight<=0)throw new IOException("Invalid mask dimensions");
    byte[] bits=new byte[(width*height+7)/8];input.readFully(bits);if(result.put(name,new Mask(width,height,highlight,bits))!=null)throw new IOException("Duplicate mask");
   }
   if(input.read()!=-1)throw new IOException("Unexpected trailing mask data");return Collections.unmodifiableMap(result);
  }
 }
}
