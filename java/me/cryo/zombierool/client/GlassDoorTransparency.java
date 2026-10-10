package me.cryo.zombierool.client;

import me.cryo.zombierool.maptexture.GlassDoorAlpha;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public final class GlassDoorTransparency {
    private GlassDoorTransparency(){}
    public static void apply(TextureAtlas atlas){
        atlas.bind();
        for(int stage=0;stage<=7;stage++){
            var id=new ResourceLocation("zombierool","block/glass_door_"+stage);var sprite=atlas.getSprite(id);
            if(!sprite.contents().name().equals(id))continue;
            for(var image:sprite.contents().byMipLevel)for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int color=image.getPixelRGBA(x,y),alpha=GlassDoorAlpha.alpha(color&255,(color>>>8)&255,(color>>>16)&255,x*16/image.getWidth(),y*32/image.getHeight());
                image.setPixelRGBA(x,y,(color&0xFFFFFF)|(alpha<<24));
            }
            sprite.uploadFirstFrame();
        }
    }
}
