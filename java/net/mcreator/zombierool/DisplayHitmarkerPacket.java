package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraft.client.Minecraft;

import net.mcreator.zombierool.client.screens.HitmarkerOverlay;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.sounds.SoundEvent; // This import is necessary for SoundEvent type

import java.util.function.Supplier;

public class DisplayHitmarkerPacket {

    public DisplayHitmarkerPacket() {
    }

    public static void encode(DisplayHitmarkerPacket msg, FriendlyByteBuf buf) {
        // No data to encode for this simple packet
    }

    public static DisplayHitmarkerPacket decode(FriendlyByteBuf buf) {
        return new DisplayHitmarkerPacket();
    }

    public static class Handler {
        public static void handle(DisplayHitmarkerPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    Minecraft.getInstance().tell(() -> {
                        HitmarkerOverlay.triggerHitmarkerDisplay();

                        if (Minecraft.getInstance().player != null) {
                            // Play the hitmarker sound at a very low volume (0.1f)
                            Minecraft.getInstance().player.level().playSound(
                                Minecraft.getInstance().player,
                                Minecraft.getInstance().player.getX(),
                                Minecraft.getInstance().player.getY(),
                                Minecraft.getInstance().player.getZ(),
                                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:player_hit")),
                                SoundSource.PLAYERS,
                                0.1f, // HITMARKER VOLUME: Very low
                                1.0f
                            );
                        }
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}