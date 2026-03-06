package me.cryo.zombierool.network;

import me.cryo.zombierool.client.screens.HitmarkerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.function.Supplier;

public class DisplayHitmarkerPacket {
	public DisplayHitmarkerPacket() {
		
	}
	
	public static void encode(DisplayHitmarkerPacket msg, FriendlyByteBuf buf) {
	}
	
	public static DisplayHitmarkerPacket decode(FriendlyByteBuf buf) {
	    return new DisplayHitmarkerPacket();
	}
	
	public static void handle(DisplayHitmarkerPacket msg, Supplier<NetworkEvent.Context> ctx) {
	    ctx.get().enqueueWork(() -> {
	        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle());
	    });
	    ctx.get().setPacketHandled(true);
	}
	
	private static class ClientHandler {
	    public static void handle() {
	        HitmarkerOverlay.triggerHitmarkerDisplay();
	        
	        Minecraft mc = Minecraft.getInstance();
	        if (mc.player != null) {
	            mc.player.level().playSound(
	                mc.player,
	                mc.player.getX(),
	                mc.player.getY(),
	                mc.player.getZ(),
	                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:player_hit")),
	                SoundSource.PLAYERS,
	                0.3f, 
	                1.0f + (mc.player.level().random.nextFloat() * 0.1f)
	            );
	        }
	    }
	}
}
