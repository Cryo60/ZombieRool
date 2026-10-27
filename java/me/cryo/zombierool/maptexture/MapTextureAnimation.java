package me.cryo.zombierool.maptexture;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Bounded animation sheets shared by the map loader and the client. */
public record MapTextureAnimation(byte[] png, int[] frames, int[] times, boolean interpolate) {
    private static final int MAGIC = 0x5A524131;
    public static final int MAX_FRAMES = 64, MAX_STEPS = 256;

    public static boolean exists(Path png) { return Files.isRegularFile(metadata(png)); }
    public static Path metadata(Path png) { return png.resolveSibling(png.getFileName()+".mcmeta"); }

    public static MapTextureAnimation read(Path path) throws IOException {
        try {
            Path meta=metadata(path);
            if(Files.size(path)>1048576 || Files.size(meta)>32768)throw new IOException("Animation file too large");
            var root=JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
            var settings=root.getAsJsonObject("animation");
            if(settings==null)throw new IOException("Missing animation metadata");
            BufferedImage source;
            try(var input=ImageIO.createImageInputStream(path.toFile())) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())throw new IOException("Invalid animation PNG");
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    int w=reader.getWidth(0),h=reader.getHeight(0);
                    if(!reader.getFormatName().equalsIgnoreCase("png") || w<1 || h<1 || w>8192 || h>8192 || (long)w*h>4194304)throw new IOException("Animation dimensions too large");
                    source=reader.read(0);
                } finally {reader.dispose();}
            }
            int w=source.getWidth(),h=source.getHeight();
            int fw=settings.has("width")?settings.get("width").getAsInt():settings.has("height")?w:Math.min(w,h);
            int fh=settings.has("height")?settings.get("height").getAsInt():settings.has("width")?h:Math.min(w,h);
            if(fw<1 || fh<1 || w%fw!=0 || h%fh!=0)throw new IOException("Invalid animation frame dimensions");
            int count=(w/fw)*(h/fh);
            if(count<1 || count>MAX_FRAMES)throw new IOException("Animation supports at most 64 frames");
            int defaultTime=settings.has("frametime")?settings.get("frametime").getAsInt():1;
            if(defaultTime<1 || defaultTime>1200)throw new IOException("Invalid animation frame time");
            var sequence=settings.has("frames")?settings.getAsJsonArray("frames"):null;
            int length=sequence==null || sequence.isEmpty()?count:sequence.size();
            if(length>MAX_STEPS)throw new IOException("Animation sequence too long");
            int[] frames=new int[length],times=new int[length];
            for(int i=0;i<length;i++) {
                var entry=sequence==null || sequence.isEmpty()?null:sequence.get(i);
                frames[i]=entry==null?i:entry.isJsonObject()?entry.getAsJsonObject().get("index").getAsInt():entry.getAsInt();
                times[i]=entry!=null && entry.isJsonObject() && entry.getAsJsonObject().has("time")?entry.getAsJsonObject().get("time").getAsInt():defaultTime;
                if(frames[i]<0 || frames[i]>=count || times[i]<1 || times[i]>1200)throw new IOException("Invalid animation sequence entry");
            }
            var sheet=new BufferedImage(32,32*count,BufferedImage.TYPE_INT_ARGB);
            for(int i=0;i<count;i++)for(int y=0;y<32;y++)for(int x=0;x<32;x++)
                sheet.setRGB(x,i*32+y,source.getRGB((i%(w/fw))*fw+x*fw/32,(i/(w/fw))*fh+y*fh/32));
            var bytes=new ByteArrayOutputStream();ImageIO.write(sheet,"png",bytes);
            return new MapTextureAnimation(bytes.toByteArray(),frames,times,settings.has("interpolate") && settings.get("interpolate").getAsBoolean());
        } catch(RuntimeException e) {throw new IOException("Invalid animation metadata",e);}
    }

    public byte[] encode() throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);out.writeBoolean(interpolate);out.writeInt(frames.length);
            for(int i=0;i<frames.length;i++){out.writeInt(frames[i]);out.writeInt(times[i]);}
            out.writeInt(png.length);out.write(png);
        }
        if(bytes.size()>MapTexturePixels.MAX_FACE_BYTES)throw new IOException("Normalized animation too large");
        return bytes.toByteArray();
    }

    public static MapTextureAnimation decode(byte[] bytes) throws IOException {
        if(bytes.length>MapTexturePixels.MAX_FACE_BYTES)throw new IOException("Texture payload too large");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(bytes.length<4 || in.readInt()!=MAGIC)return new MapTextureAnimation(bytes,new int[]{0},new int[]{1},false);
            boolean interpolate=in.readBoolean();int length=in.readInt();
            if(length<1 || length>MAX_STEPS)throw new IOException("Invalid animation sequence length");
            int[] frames=new int[length],times=new int[length];
            for(int i=0;i<length;i++) {
                frames[i]=in.readInt();times[i]=in.readInt();
                if(frames[i]<0 || frames[i]>=MAX_FRAMES || times[i]<1 || times[i]>1200)throw new IOException("Invalid animation sequence");
            }
            int size=in.readInt();if(size<24 || size!=in.available())throw new IOException("Invalid animation PNG size");
            byte[] png=in.readNBytes(size);
            var header=java.nio.ByteBuffer.wrap(png);
            int height=header.getInt(20);
            if(header.getLong(0)!=0x89504e470d0a1a0aL || header.getInt(16)!=32 || height<32 || height>32*MAX_FRAMES || height%32!=0)throw new IOException("Invalid animation PNG dimensions");
            for(int frame:frames)if(frame>=height/32)throw new IOException("Animation frame outside sheet");
            return new MapTextureAnimation(png,frames,times,interpolate);
        }
    }

    public int step(long tick) {
        int total=0;for(int time:times)total+=time;
        int elapsed=(int)Math.floorMod(tick,total);
        for(int i=0;i<times.length;i++){if(elapsed<times[i])return i;elapsed-=times[i];}
        return 0;
    }
    public float blend(long tick,int step) {
        int total=0,before=0;for(int i=0;i<times.length;i++){total+=times[i];if(i<step)before+=times[i];}
        return interpolate?(Math.floorMod(tick,total)-before)/(float)times[step]:0;
    }
}
