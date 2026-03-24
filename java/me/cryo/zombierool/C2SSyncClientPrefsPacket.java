package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.configuration.ZRClientConfig.HalloweenMode;
import me.cryo.zombierool.HalloweenManager;

import java.util.function.Supplier;

public class C2SSyncClientPrefsPacket {

    private final String halloweenMode;
    private final boolean preferZrWeapons;

    public C2SSyncClientPrefsPacket(String halloweenMode, boolean preferZrWeapons) {
        this.halloweenMode = halloweenMode;
        this.preferZrWeapons = preferZrWeapons;
    }

    public C2SSyncClientPrefsPacket(FriendlyByteBuf buf) {
        this.halloweenMode = buf.readUtf();
        this.preferZrWeapons = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.halloweenMode);
        buf.writeBoolean(this.preferZrWeapons);
    }

    public static C2SSyncClientPrefsPacket decode(FriendlyByteBuf buf) {
        return new C2SSyncClientPrefsPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getPersistentData().putBoolean("zr_prefer_zr_weapons", this.preferZrWeapons);
                
                if (player.hasPermissions(2)) {
                    try {
                        HalloweenMode mode = HalloweenMode.valueOf(this.halloweenMode);
                        me.cryo.zombierool.configuration.ZRClientConfig.setHalloweenMode(mode);
                    } catch (Exception ignored) {}
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
