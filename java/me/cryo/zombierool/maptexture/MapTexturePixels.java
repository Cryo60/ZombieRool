package me.cryo.zombierool.maptexture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

/** Normalizes source atlases without rewriting the creator's original PNG. */
public final class MapTexturePixels {
    public static final String[] FACES={"side","top","bottom","lower","upper","north","south","east","west"};
    public static final int MAX_FACE_BYTES=196608;
    private MapTexturePixels(){}
    public static byte[][] readAnimated(Path directory,String name)throws IOException{
        if(name==null||name.isEmpty())return read(directory,name);
        Path base=directory.resolve(name+".png");
        byte[][] result=null;
        var animations=new java.util.HashMap<Path,byte[]>();
        // Metadata distinguishes a frame strip from a static door or cube atlas.
        for(int i=0;i<FACES.length;i++){
            Path override=directory.resolve(name+"_"+FACES[i]+".png");
            Path source=Files.isRegularFile(override)?override:base;
            if(MapTextureAnimation.exists(source)){
                if(result==null)result=new byte[FACES.length][];
                byte[] encoded=animations.get(source);
                if(encoded==null){encoded=MapTextureAnimation.read(source).encode();animations.put(source,encoded);}
                result[i]=encoded;
            }
        }
        if(result==null)return read(directory,name);
        byte[][] staticFaces=null;
        for(int i=0;i<result.length;i++)if(result[i]==null){
            if(staticFaces==null)staticFaces=read(directory,name);
            result[i]=staticFaces[i];
        }
        return result;
    }
    public static byte[][] read(Path directory,String name)throws IOException{
        byte[][] result=new byte[FACES.length][];
        if(name==null||name.isEmpty()){for(int i=0;i<result.length;i++)result[i]=new byte[0];return result;}
        Map<String,BufferedImage> atlas=convert(directory.resolve(name+".png"));
        for(int i=0;i<FACES.length;i++){
            String face=FACES[i];String suffix=face.equals("side")||face.equals("north")?"":"_"+face;
            BufferedImage image=atlas.getOrDefault(suffix,atlas.get(""));
            Path override=directory.resolve(name+"_"+face+".png");
            if(Files.isRegularFile(override))image=convert(override).get("");
            var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);result[i]=bytes.toByteArray();
            if(result[i].length>MAX_FACE_BYTES)throw new IOException("Normalized PNG too large");
        }
        return result;
    }
    private static Map<String,BufferedImage> convert(Path path)throws IOException{
        if(MapTextureAnimation.exists(path)){
            var animation=MapTextureAnimation.read(path);
            var sheet=ImageIO.read(new java.io.ByteArrayInputStream(animation.png()));
            return Map.of("",sheet.getSubimage(0,animation.frames()[0]*32,32,32));
        }
        if(Files.size(path)>1048576)throw new IOException("Source PNG too large");
        return TextureAtlasImport.convert(Files.readAllBytes(path),32);
    }
}
