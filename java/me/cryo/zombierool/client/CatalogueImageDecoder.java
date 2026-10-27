package me.cryo.zombierool.client;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Bounded source decoding; only the small thumbnail is ever uploaded to the GPU. */
public final class CatalogueImageDecoder {
    public static final int MAX_BYTES=16*1024*1024,MAX_EDGE=8192,MAX_PIXELS=16*1024*1024;
    public static final int WIDTH=640,HEIGHT=360;
    public static BufferedImage decode(byte[] bytes)throws IOException{
        if(bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("image_bytes");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            if(input==null)throw new IOException("image_format");
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("image_format");var reader=readers.next();
            try{
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>MAX_EDGE||height>MAX_EDGE||(long)width*height>MAX_PIXELS)throw new IOException("image_size");
                double scale=Math.min(1,Math.min(WIDTH/(double)width,HEIGHT/(double)height));
                int w=Math.max(1,(int)Math.round(width*scale)),h=Math.max(1,(int)Math.round(height*scale));
                var params=reader.getDefaultReadParam();int sample=Math.max(1,Math.min(width/w,height/h));params.setSourceSubsampling(sample,sample,0,0);
                BufferedImage decoded=reader.read(0,params);if(decoded==null)throw new IOException("image_format");
                try{
                    var thumbnail=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);var graphics=thumbnail.createGraphics();
                    try{graphics.setComposite(java.awt.AlphaComposite.Src);graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);graphics.drawImage(decoded,0,0,w,h,null);}finally{graphics.dispose();}
                    return thumbnail;
                }finally{decoded.flush();}
            }finally{reader.dispose();}
        }
    }
    public static int abgr(int argb){return (argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16);}
    private CatalogueImageDecoder(){}
}
