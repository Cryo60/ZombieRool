package me.cryo.zombierool.maptexture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Pure PNG conversion, shared by tooling and the in-game editor. */
public final class TextureAtlasImport {
    private TextureAtlasImport() {}
    public static Map<String, BufferedImage> convert(byte[] png,int size)throws IOException {
        if(png.length>1024*1024 || (size!=16&&size!=32))throw new IOException("Texture size limit");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(png))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid PNG");
            var reader=readers.next();
            try{
                reader.setInput(input);
                if(!reader.getFormatName().equalsIgnoreCase("png"))throw new IOException("PNG required");
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>512||h>512)throw new IOException("Atlas size limit");
                var source=reader.read(0);Map<String,BufferedImage> faces=new LinkedHashMap<>();
                if(w==h*2&&w%64==0){faces.putAll(headFaces(source,size));}
                else if(w==h)faces.put("",tile(source,0,0,w,size));
                else if(h==w*2){faces.put("_upper",tile(source,0,0,w,size));faces.put("_lower",tile(source,0,w,w,size));faces.put("",faces.get("_upper"));}
                else if(w%4==0&&h==w/4*3){int t=w/4;String[] names={"_west","","_east","_south","_top","_bottom"};int[] x={0,1,2,3,1,1},y={1,1,1,1,0,2};for(int i=0;i<6;i++)faces.put(names[i],tile(source,x[i]*t,y[i]*t,t,size));}
                else if(w==h*6||h==w*6){int t=Math.min(w,h);String[] names={"","_south","_east","_west","_top","_bottom"};for(int i=0;i<6;i++)faces.put(names[i],tile(source,w>h?i*t:0,h>w?i*t:0,t,size));}
                else throw new IOException("Unsupported atlas layout");
                return faces;
            }finally{reader.dispose();}
        }
    }
    public static Map<String,BufferedImage> convertHead(byte[] png,int size)throws IOException{
        if(png.length>1024*1024||(size!=16&&size!=32))throw new IOException("Texture size limit");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(png))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid PNG");var reader=readers.next();try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(!reader.getFormatName().equalsIgnoreCase("png")||w<64||w%64!=0||w>512||(h!=w&&h*2!=w))throw new IOException("Skin must be 64x32 or 64x64");return headFaces(reader.read(0),size);}finally{reader.dispose();}}
    }
    private static Map<String,BufferedImage> headFaces(BufferedImage source,int size){
        int scale=source.getWidth()/64;Map<String,BufferedImage> faces=new LinkedHashMap<>();
        String[] names={"","_south","_east","_west","_top","_bottom"};int[] xs={8,24,0,16,8,16},ys={8,8,8,8,0,0};
        for(int i=0;i<names.length;i++){var image=tile(source,xs[i]*scale,ys[i]*scale,8*scale,size);var hat=tile(source,(xs[i]+32)*scale,ys[i]*scale,8*scale,size);var graphics=image.createGraphics();graphics.drawImage(hat,0,0,null);graphics.dispose();faces.put(names[i],image);}return faces;
    }
    private static BufferedImage tile(BufferedImage source,int ox,int oy,int tile,int size){
        var out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)out.setRGB(x,y,source.getRGB(ox+x*tile/size,oy+y*tile/size));
        return out;
    }
}
