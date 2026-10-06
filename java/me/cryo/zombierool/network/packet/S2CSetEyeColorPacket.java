package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;

public class S2CSetEyeColorPacket {
    private final String eyeColorPreset;

    public S2CSetEyeColorPacket(String eyeColorPreset) {
        this.eyeColorPreset = eyeColorPreset;
    }

    // Encoder: Écrit les données du paquet dans le buffer
    public static void encode(S2CSetEyeColorPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.eyeColorPreset);
    }

    // Decoder: Lit les données du buffer pour reconstruire un objet S2CSetEyeColorPacket
    public static S2CSetEyeColorPacket decode(FriendlyByteBuf buf) {
        return new S2CSetEyeColorPacket(buf.readUtf());
    }

    // Getter pour accéder au preset de couleur des yeux
    public String getEyeColorPreset() {
        return eyeColorPreset;
    }
}