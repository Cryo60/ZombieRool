package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSyncAimPacket {
    private final boolean aiming;

    public C2SSyncAimPacket(boolean aiming) {
        this.aiming = aiming;
    }

    public C2SSyncAimPacket(FriendlyByteBuf buf) {
        this.aiming = buf.readBoolean();
    }

    public static void encode(C2SSyncAimPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.aiming);
    }

    public static C2SSyncAimPacket decode(FriendlyByteBuf buf) {
        return new C2SSyncAimPacket(buf);
    }

    public static void handle(C2SSyncAimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                boolean was=player.getPersistentData().getBoolean("zr_ads");player.getPersistentData().putBoolean("zr_ads", msg.aiming);
                if(msg.aiming&&!was&&player.getMainHandItem().getItem() instanceof me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem gun&&me.cryo.zombierool.gameplay.MapWeaponPacks.available(gun.getDefinition().id.replace("zombierool:",""))){String id=gun.getDefinition().sounds.draw;if(id!=null&&!id.isBlank()){var sound=net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation(id));if(sound!=null)player.level().playSound(null,player.blockPosition(),sound,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1);}}

            }
        });
        ctx.get().setPacketHandled(true);
    }
}
