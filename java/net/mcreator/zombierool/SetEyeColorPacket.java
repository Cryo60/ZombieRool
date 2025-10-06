package net.mcreator.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;

public class SetEyeColorPacket {
    private final String eyeColorPreset;

    public SetEyeColorPacket(String eyeColorPreset) {
        this.eyeColorPreset = eyeColorPreset;
    }

    // Encoder: Écrit les données du paquet dans le buffer
    public static void encode(SetEyeColorPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.eyeColorPreset);
    }

    // Decoder: Lit les données du buffer pour reconstruire un objet SetEyeColorPacket
    public static SetEyeColorPacket decode(FriendlyByteBuf buf) {
        return new SetEyeColorPacket(buf.readUtf());
    }

    // Getter pour accéder au preset de couleur des yeux
    public String getEyeColorPreset() {
        return eyeColorPreset;
    }
}