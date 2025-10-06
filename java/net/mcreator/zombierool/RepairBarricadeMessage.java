package net.mcreator.zombierool.network;

import net.mcreator.zombierool.block.DefenseDoorBlock;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

public class RepairBarricadeMessage {
    private final BlockPos blockPos;
    private final int action;
    private static final long BASE_COOLDOWN = 1250;
    private static final double SPEED_COLA_MULTIPLIER = 0.25;
    private static final Map<UUID, Long> lastRepairTimes = new HashMap<>();

    public RepairBarricadeMessage(BlockPos pos, int action) {
        this.blockPos = pos;
        this.action = action;
    }

    public RepairBarricadeMessage(FriendlyByteBuf buffer) {
        this.blockPos = buffer.readBlockPos();
        this.action = buffer.readInt();
    }

    public static void encode(RepairBarricadeMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.blockPos);
        buffer.writeInt(message.action);
    }

    public static RepairBarricadeMessage decode(FriendlyByteBuf buffer) {
        return new RepairBarricadeMessage(buffer.readBlockPos(), buffer.readInt());
    }

    public static void handler(RepairBarricadeMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.level().isClientSide) return;

            UUID playerId = player.getUUID();
            long now = System.currentTimeMillis();

            boolean hasSpeedCola = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
            long effectiveCooldown = hasSpeedCola ? (long) (BASE_COOLDOWN * SPEED_COLA_MULTIPLIER) : BASE_COOLDOWN;

            long lastTime = lastRepairTimes.getOrDefault(playerId, 0L);
            if (now - lastTime < effectiveCooldown) return;

            lastRepairTimes.put(playerId, now);

            BlockPos pos = message.getBlockPos();
            BlockState state = player.level().getBlockState(pos);

            if (state.getBlock() instanceof DefenseDoorBlock door) {
                int stage = state.getValue(DefenseDoorBlock.STAGE);
                if (stage < 5) {
                    door.updateStage(player.level(), pos, stage + 1);

                    Random rand = new Random();
                    int soundIndex = rand.nextInt(3);
                    String soundFile = "board_slam_0" + soundIndex;

                    player.level().playSound(
                        null,
                        pos,
                        SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", soundFile)),
                        SoundSource.BLOCKS,
                        1.0f,
                        1.0f
                    );
                }
            }
        });
        context.setPacketHandled(true);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public int getAction() {
        return action;
    }
}
