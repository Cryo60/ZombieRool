package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RecoilPacket {
    private final float pitchRecoil;
    private final float yawRecoil;

    public RecoilPacket(float pitchRecoil, float yawRecoil) {
        this.pitchRecoil = pitchRecoil;
        this.yawRecoil = yawRecoil;
    }

    public static void encode(RecoilPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.pitchRecoil);
        buf.writeFloat(msg.yawRecoil);
    }

    public static RecoilPacket decode(FriendlyByteBuf buf) {
        return new RecoilPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(RecoilPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Côté client
            if (Minecraft.getInstance().player != null) {
                Player player = Minecraft.getInstance().player;
                // Appliquer le recul en modifiant la rotation du joueur
                // Ceci fait bouger la caméra du joueur
                player.setXRot(player.getXRot() - msg.pitchRecoil); // Diminue XRot pour regarder vers le haut (recul)
                player.setYRot(player.getYRot() + msg.yawRecoil);   // Ajoute YRot pour regarder à gauche/droite (aléatoire)
            }
        });
        context.setPacketHandled(true);
    }
}