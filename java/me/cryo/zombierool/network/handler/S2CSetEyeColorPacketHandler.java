package me.cryo.zombierool.network.handler;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.LogicalSide;
import me.cryo.zombierool.network.packet.S2CSetEyeColorPacket;
import me.cryo.zombierool.client.renderer.ZombieRenderer; 

import java.util.function.Supplier;

public class S2CSetEyeColorPacketHandler {
    public static void handle(S2CSetEyeColorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
            context.enqueueWork(() -> {
                ZombieRenderer.setEyeTexture(msg.getEyeColorPreset());
            });
            context.setPacketHandled(true);
        } else {
            context.setPacketHandled(false); 
        }
    }
}