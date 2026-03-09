package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.integration.TacZIntegration;

import java.util.function.Supplier;

public class ReloadWeaponsPacket {

    public ReloadWeaponsPacket() {}

    public ReloadWeaponsPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static ReloadWeaponsPacket decode(FriendlyByteBuf buf) {
        return new ReloadWeaponsPacket();
    }

    public static void handle(ReloadWeaponsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                WeaponSystem.Loader.loadWeapons();
                TacZIntegration.syncTaczGunData();
                System.out.println("[ZombieRool] Client-side JSON weapons reloaded via network packet.");
            });
        });
        ctx.get().setPacketHandled(true);
    }
}