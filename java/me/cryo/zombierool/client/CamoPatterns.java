package me.cryo.zombierool.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Shared animated camo textures: native weapons never load optional TaCZ model classes. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class CamoPatterns {
    private static final Map<String,Pattern> PATTERNS=new HashMap<>();
    private static final Set<String> MISSING=new HashSet<>();
    private CamoPatterns(){}
    /** Repeats per model-space block, independent of texture resolution and gun UVs. */
    static float patternScale(String id){
        return switch(id){
            // Larger motifs with fewer repeats across the paintable gun.
            case "grotog", "horror_eye_animated", "halloween_animated" -> 2.5f;
            case "sponsor_animated" -> 3f;
            case "cherry_blossom_animated", "panda", "giraffe", "leopard", "zebra",
                 "chameleon_amethyst_emerald", "chameleon_copper_teal", "chameleon_inferno", "chameleon_jade_gold", "chameleon_ocean_violet", "chameleon_sakura_cyan" -> 4f;
            // Large illustrations, ice cracks and flowing finishes span the receiver.
            case "black_ice", "blood", "celestial", "contributor", "cryo", "dark_matter",
                 "galaxy", "gigachad", "hyper_beast", "lava", "lightning", "magma",
                 "marmot", "nebula", "ocean", "plasma", "supernova", "void", "water" -> 2f;
            // At 3x, crests span narrow gun panels and mostly appear as clipped fragments.
            // Keep them compact: halfway between the oversized 3x and original dense 8x.
            case "lordaeron", "alliance", "horde", "forsaken", "burning_legion",
                 "quel_thalas", "scarlet_crusade", "scourge", "silvermoon",
                 "trollface", "mojang", "mdxu" -> 6f;
            // Repeating symbols and animated particles keep several readable instances.
            case "bubbles", "doge", "glitch_cartridge", "matrix", "prestige",
                 "rain_storm", "retro_8bit", "venom" -> 3f;
            // Broad bands, branches, cells and contrasting geometric patterns.
            case "asiimov", "bloodshot", "blue_tiger", "cherry_blossom", "disruptive_patt",
                 "fast_food", "gold", "hex", "masterchief", "merc_red", "missing_textures",
                 "red_tiger", "tigerstripe", "toxic" -> 4f;
            // Mid-sized patches and textured coatings.
            case "arctic", "brushstroke", "chocolate_chip", "coral", "desert", "jungle",
                 "multicam", "ocp", "red_soda", "sandstorm", "solid_gold", "urban" -> 5f;
            // Fine mottling/digital patterns: retain detail without the old 8x tiling.
            case "anniversary", "auscam", "bedrock", "cadpat", "damascus", "diamond",
                 "digital", "erdl", "first_anniversary", "flecktarn", "marpat",
                 "midnight", "olive", "strichtarn", "surpat", "ucp", "woodland" -> 6f;
            // Fine fabric grain and the upgrade sheen already work at this density.
            case "wesker_coat", "_pap" -> 8f;
            // Compatibility fallback for additional resource-pack camos only.
            default -> 6f;
        };
    }

    private static final class Pattern {
        final NativeImage strip;
        final DynamicTexture texture;
        final ResourceLocation location;
        final int size,time;
        final int[] frames;
        final boolean interpolate;
        boolean fitted;String id="_pap";
        long lastStep=Long.MIN_VALUE; boolean lastFitted; int lastBackground=Integer.MIN_VALUE;
        Pattern(NativeImage source,int time,int[] frames,boolean interpolate){
            strip=source;size=Math.min(source.getWidth(),source.getHeight());this.time=time;this.frames=frames;this.interpolate=interpolate;
            texture=new DynamicTexture(new NativeImage(size,size,true)){
                @Override public void setFilter(boolean blur,boolean mipmap){super.setFilter(true,false);}
            };
            location=Minecraft.getInstance().getTextureManager().register("zombierool_tacz_pattern",texture);
        }
        ResourceLocation current(){
            var mc=Minecraft.getInstance();var appearance=CamoAppearance.get(id);fitted=appearance.fitted;double tick=(mc.level==null?System.currentTimeMillis()/50.0:mc.level.getGameTime()+(mc.isPaused()?0:mc.getFrameTime()))*appearance.animationSpeed;
            long step=frames.length==1?0:(long)Math.floor(interpolate?tick*3:tick/time);if(step==lastStep&&lastFitted==fitted&&lastBackground==appearance.backgroundColor)return location;lastStep=step;lastFitted=fitted;lastBackground=appearance.backgroundColor;
            double clock=interpolate?step/3.0:step*time;int index=(int)(Math.floor(clock/time)%frames.length);
            int aFrame=frames[index],bFrame=interpolate?frames[(index+1)%frames.length]:aFrame;
            float fraction=interpolate?(float)(clock%time/time):0;
            var image=texture.getPixels();
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                int a=strip.getPixelRGBA(x,y+aFrame*size),b=strip.getPixelRGBA(x,y+bFrame*size);
                float wa=(1-fraction)*(a>>>24),wb=fraction*(b>>>24),alpha=wa+wb;
                int pixel=Math.round(alpha)<<24;
                if(alpha>0)for(int shift=0;shift<24;shift+=8)pixel|=Math.round((((a>>>shift)&255)*wa+((b>>>shift)&255)*wb)/alpha)<<shift;
                image.setPixelRGBA(x,y,pixel);
            }
            texture.upload();texture.setFilter(true,false);
            int border=image.getPixelRGBA(0,0);int rgb=appearance.backgroundColor;
            float red=rgb<0?(border&255)/255f:((rgb>>16)&255)/255f,green=rgb<0?((border>>8)&255)/255f:((rgb>>8)&255)/255f,blue=rgb<0?((border>>16)&255)/255f:(rgb&255)/255f;
            org.lwjgl.opengl.GL11.glTexParameterfv(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_BORDER_COLOR,new float[]{red,green,blue,rgb<0?(border>>>24)/255f:1f});
            // Minecraft's dynamic images clamp their edges by default. Surface
            // coordinates cover several tiles, so repeat must be explicit.
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,fitted?org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER:org.lwjgl.opengl.GL11.GL_REPEAT);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,fitted?org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER:org.lwjgl.opengl.GL11.GL_REPEAT);
            return location;
        }
        void close(){strip.close();Minecraft.getInstance().getTextureManager().release(location);}
    }
    public record SpritePattern(NativeImage pixels,long version){}
    public static SpritePattern spritePattern(String id){if(pattern(id)==null)return null;var p=PATTERNS.get(id);return new SpritePattern(p.texture.getPixels(),p.lastStep);}
    public static ResourceLocation pattern(String id){
        if(id.isEmpty()||!id.matches("[a-z0-9_]+")||MISSING.contains(id))return null;
        int unit=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        int binding=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        try{
            if(PATTERNS.containsKey(id))return PATTERNS.get(id).current();
            if(id.equals("_pap")){
                // Draw the upgrade sheen on the actual gun mesh. Vanilla glint
                // uses a different hand pass under shaders and can leak into the sky.
                var strip=new NativeImage(32,32*16,true);
                for(int frame=0;frame<16;frame++)for(int y=0;y<32;y++)for(int x=0;x<32;x++){
                    double wave=(1+Math.cos((x+y-frame*2)*Math.PI/16))/2;
                    int alpha=25+(int)(Math.pow(wave,4)*145),red=115+(int)(wave*100),green=45+(int)(wave*65),blue=195+(int)(wave*60);
                    strip.setPixelRGBA(x,y+frame*32,alpha<<24|blue<<16|green<<8|red);
                }
                var pattern=new Pattern(strip,2,java.util.stream.IntStream.range(0,16).toArray(),true);
                PATTERNS.put(id,pattern);return pattern.current();
            }
            var mc=Minecraft.getInstance();var path=new ResourceLocation("zombierool","textures/item/camos/"+id+".png");
            NativeImage source;try(var input=mc.getResourceManager().open(path)){source=NativeImage.read(input);}
            int count=Math.max(1,source.getHeight()/Math.min(source.getWidth(),source.getHeight())),time=1;boolean interpolate=false;
            int[] frames=java.util.stream.IntStream.range(0,count).toArray();
            var metadata=mc.getResourceManager().getResource(new ResourceLocation(path+".mcmeta"));
            if(metadata.isPresent())try(var reader=metadata.get().openAsReader()){
                var animation=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animation");
                if(animation.has("frametime"))time=Math.max(1,animation.get("frametime").getAsInt());
                if(animation.has("interpolate"))interpolate=animation.get("interpolate").getAsBoolean();
                if(animation.has("frames")){
                    var sequence=new ArrayList<Integer>();for(var frame:animation.getAsJsonArray("frames")){
                        int value=frame.isJsonPrimitive()?frame.getAsInt():frame.getAsJsonObject().get("index").getAsInt();if(value>=0&&value<count)sequence.add(value);
                    }if(!sequence.isEmpty())frames=sequence.stream().mapToInt(Integer::intValue).toArray();
                }
            }catch(java.io.IOException|RuntimeException ignored){}
            var pattern=new Pattern(source,time,frames,interpolate);pattern.id=id;PATTERNS.put(id,pattern);return pattern.current();
        }catch(java.io.IOException failure){MISSING.add(id);org.slf4j.LoggerFactory.getLogger(CamoPatterns.class).warn("Camo {} unavailable: {}",id,failure.getMessage());return null;}
        finally{com.mojang.blaze3d.platform.GlStateManager._bindTexture(binding);com.mojang.blaze3d.systems.RenderSystem.activeTexture(unit);}
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void reload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->{CamoAppearance.reload();PATTERNS.values().forEach(Pattern::close);PATTERNS.clear();MISSING.clear();});
    }
}
