package me.cryo.zombierool.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class MusicVolumeClient {
    public static void receive(String action,BlockPos pos,CompoundTag n){if(n==null)return;if(action.equals("open"))Minecraft.getInstance().setScreen(new me.cryo.zombierool.client.gui.MusicVolumeScreen(pos,n));else if(action.equals("reset"))ZombieSoundHandler.restoreMapMusic(n.getString("preset"));else if(action.equals("play"))ZombieSoundHandler.applyMusicVolume(n);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){ZombieSoundHandler.resetMusicVolume();}
    private MusicVolumeClient(){}
}
