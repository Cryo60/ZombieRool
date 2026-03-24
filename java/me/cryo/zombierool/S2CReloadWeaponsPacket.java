package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.integration.TacZIntegration;

import java.util.function.Supplier;

public class S2CReloadWeaponsPacket {

    public S2CReloadWeaponsPacket() {}

    public S2CReloadWeaponsPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static S2CReloadWeaponsPacket decode(FriendlyByteBuf buf) {
        return new S2CReloadWeaponsPacket();
    }

    public static void handle(S2CReloadWeaponsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                WeaponSystem.Loader.loadWeapons();
                TacZIntegration.syncTaczGunData();

                me.cryo.zombierool.core.manager.DynamicResourceManager.clearClientResources();
                me.cryo.zombierool.client.DynamicSoundLoader.clearLoadedSounds();
                
                System.out.println("[ZombieRool] Client-side JSON weapons and dynamic resources reloaded via network packet.");
            });
        });
        ctx.get().setPacketHandled(true);
    }
}