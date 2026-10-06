package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.integration.TacZIntegration;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class S2CReloadWeaponsPacket {
    private final String payload;

    public S2CReloadWeaponsPacket() {
        this("");
    }

    public S2CReloadWeaponsPacket(String payload) {
        this.payload = payload == null ? "" : payload;
    }

    public void encode(FriendlyByteBuf buf) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        buf.writeByteArray(data);
    }

    public static S2CReloadWeaponsPacket decode(FriendlyByteBuf buf) {
        byte[] data = buf.readByteArray();
        return new S2CReloadWeaponsPacket(new String(data, StandardCharsets.UTF_8));
    }

    public static void handle(S2CReloadWeaponsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                WeaponSystem.Loader.loadWeapons();
                WeaponSystem.Loader.applyWeaponPayload(msg.payload);
                TacZIntegration.syncTaczGunData();

                me.cryo.zombierool.core.manager.DynamicResourceManager.clearClientResources();
                me.cryo.zombierool.client.DynamicSoundLoader.clearLoadedSounds();
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
