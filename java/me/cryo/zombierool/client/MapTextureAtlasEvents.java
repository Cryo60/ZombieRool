package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MapTextureAtlasEvents {
    @SubscribeEvent public static void stitched(TextureStitchEvent.Post event){
        if(event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)){
            Minecraft.getInstance().tell(()->{MapTextureClient.invalidate();MapTextureClient.rebuildIfChanged();});
        }
    }
}
