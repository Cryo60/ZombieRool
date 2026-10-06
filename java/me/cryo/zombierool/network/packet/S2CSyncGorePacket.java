package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.core.manager.GoreManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncGorePacket {

    private final int entityId;
    private final CompoundTag goreData;

    public S2CSyncGorePacket(int entityId, CompoundTag goreData) {
        this.entityId = entityId;
        this.goreData = goreData;
    }

    public static void encode(S2CSyncGorePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeNbt(msg.goreData);
    }

    public static S2CSyncGorePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncGorePacket(buf.readInt(), buf.readNbt());
    }

    public static void handle(S2CSyncGorePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handle(S2CSyncGorePacket msg) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Entity entity = mc.level.getEntity(msg.entityId);
                if (entity != null) {
                    CompoundTag data = entity.getPersistentData();
                    apply(entity, data, msg.goreData, "h", GoreManager.TAG_NO_HEAD, GoreManager.Limb.HEAD);
                    apply(entity, data, msg.goreData, "la", GoreManager.TAG_NO_LEFT_ARM, GoreManager.Limb.LEFT_ARM);
                    apply(entity, data, msg.goreData, "ra", GoreManager.TAG_NO_RIGHT_ARM, GoreManager.Limb.RIGHT_ARM);
                    apply(entity, data, msg.goreData, "ll", GoreManager.TAG_NO_LEFT_LEG, GoreManager.Limb.LEFT_LEG);
                    apply(entity, data, msg.goreData, "rl", GoreManager.TAG_NO_RIGHT_LEG, GoreManager.Limb.RIGHT_LEG);
                    apply(entity, data, msg.goreData, "lf", GoreManager.TAG_NO_LEFT_FOREARM, GoreManager.Limb.LEFT_FOREARM);
                    apply(entity, data, msg.goreData, "rf", GoreManager.TAG_NO_RIGHT_FOREARM, GoreManager.Limb.RIGHT_FOREARM);
                    apply(entity, data, msg.goreData, "lh", GoreManager.TAG_NO_LEFT_HAND, GoreManager.Limb.LEFT_HAND);
                    apply(entity, data, msg.goreData, "rh", GoreManager.TAG_NO_RIGHT_HAND, GoreManager.Limb.RIGHT_HAND);
                    apply(entity, data, msg.goreData, "to", GoreManager.TAG_TORSO, GoreManager.Limb.TORSO);
                    apply(entity, data, msg.goreData, "sk", GoreManager.TAG_SKULL, GoreManager.Limb.SKULL);

                    if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                        living.refreshDimensions();
                    }
                }
            }
        }

        private static void apply(Entity entity, CompoundTag data, CompoundTag gore, String key, String tag, GoreManager.Limb limb) {
            if (!gore.contains(key)) return;
            boolean now = gore.getBoolean(key);
            boolean was = data.getBoolean(tag);
            data.putBoolean(tag, now);
            if (now && !was && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                me.cryo.zombierool.client.BloodJets.onLimb(living, limb);
            }
        }
    }
}