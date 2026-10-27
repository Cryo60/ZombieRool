package me.cryo.zombierool.client;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MapTextureWaterClient {
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->{
        for(int slot=0;slot<me.cryo.zombierool.maptexture.MapTextures.SLOTS;slot++){
            for(int shape=0;shape<8;shape++)net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(me.cryo.zombierool.maptexture.MapTextures.BLOCKS[slot].get(shape).get(),net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(me.cryo.zombierool.maptexture.MapTextureWater.SOURCE[slot].get(),net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(me.cryo.zombierool.maptexture.MapTextureWater.FLOW[slot].get(),net.minecraft.client.renderer.RenderType.translucent());
        }
    });}
}
