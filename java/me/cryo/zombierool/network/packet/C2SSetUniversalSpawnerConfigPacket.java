package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSetUniversalSpawnerConfigPacket {
    private final BlockPos pos;
    private final UniversalSpawnerSystem.SpawnerMobType mobType;
    private final String zone;
    private final String startChannels;
    private final String stopChannels;
    private final boolean requirePower;
    private final int spawnWeight;
    private net.minecraft.nbt.CompoundTag specialSettings;
    public C2SSetUniversalSpawnerConfigPacket(BlockPos pos,UniversalSpawnerSystem.SpawnerMobType mobType,String zone,String startChannels,String stopChannels,boolean requirePower,int spawnWeight,net.minecraft.nbt.CompoundTag settings){this(pos,mobType,zone,startChannels,stopChannels,requirePower,spawnWeight);this.specialSettings=settings.copy();}

    public C2SSetUniversalSpawnerConfigPacket(BlockPos pos, UniversalSpawnerSystem.SpawnerMobType mobType, String zone, String startChannels, String stopChannels, boolean requirePower, int spawnWeight) {
        this.pos = pos;
        this.mobType = mobType;
        this.zone = zone;
        this.startChannels = startChannels;
        this.stopChannels = stopChannels;
        this.requirePower = requirePower;
        this.spawnWeight = spawnWeight;
    }

    public static void encode(C2SSetUniversalSpawnerConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.mobType);
        buf.writeUtf(msg.zone);
        buf.writeUtf(msg.startChannels);
        buf.writeUtf(msg.stopChannels);
        buf.writeBoolean(msg.requirePower);
        buf.writeInt(msg.spawnWeight);
        buf.writeNbt(msg.specialSettings);
    }

    public static C2SSetUniversalSpawnerConfigPacket decode(FriendlyByteBuf buf) {
        return new C2SSetUniversalSpawnerConfigPacket(
                buf.readBlockPos(),
                buf.readEnum(UniversalSpawnerSystem.SpawnerMobType.class),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readInt(),
                readSettings(buf)
        );
    }
    private static net.minecraft.nbt.CompoundTag readSettings(FriendlyByteBuf buf){var tag=buf.readNbt();return tag==null?new net.minecraft.nbt.CompoundTag():tag;}

    public static void handle(C2SSetUniversalSpawnerConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.isCreative() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(msg.pos))<=64 && player.level().hasChunkAt(msg.pos)) {
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity spawner) {
                    var type=(spawner.getMobType().boss()||msg.mobType.boss())?me.cryo.zombierool.config.SpecialEnemyConfig.type(player.serverLevel()):msg.mobType;
                    spawner.setConfig(type, msg.zone, msg.startChannels, msg.stopChannels, msg.requirePower, msg.spawnWeight);
                    if(msg.specialSettings!=null&&msg.specialSettings.contains("PowerMode",8)){
                        try{me.cryo.zombierool.gameplay.BlockPowerSettings.setMode(player.serverLevel(),msg.pos,me.cryo.zombierool.gameplay.BlockPowerSettings.Mode.valueOf(msg.specialSettings.getString("PowerMode")));}
                        catch(IllegalArgumentException ignored){}
                    }
                    // Advanced special-enemy rules are edited only in the map menu.
                    if(!type.special())spawner.setSpecialConfig(msg.specialSettings);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
