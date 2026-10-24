package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CStopFourIsReadySoundPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

public class WhisperItem extends WeaponImplementations.PistolGunItem {
    private static final String TAG_FOUR_IS_READY_PLAYING = "FourIsReadyPlaying";

    public WhisperItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override
    public boolean isInfinite(ItemStack stack) {
        return true;
    }

    public boolean isFourIsReadyPlaying(ItemStack stack) {
        return getOrCreateTag(stack).getBoolean(TAG_FOUR_IS_READY_PLAYING);
    }

    public void setFourIsReadyPlaying(ItemStack stack, boolean playing) {
        getOrCreateTag(stack).putBoolean(TAG_FOUR_IS_READY_PLAYING, playing);
    }

    private void stopFourIsReadySound(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new S2CStopFourIsReadySoundPacket());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean selected) {
        super.inventoryTick(stack, level, ent, slot, selected);
        if (!(ent instanceof Player player)) return;

        // Readiness audio is owned by WhisperSounds on each client, attached to its player.
        if (!level.isClientSide && isFourIsReadyPlaying(stack)) setFourIsReadyPlaying(stack, false);
    }

    @Override
    public void startReload(ItemStack stack, Player player) {
        super.startReload(stack, player);
        if (isFourIsReadyPlaying(stack)) {
            stopFourIsReadySound(player);
            setFourIsReadyPlaying(stack, false);
        }
    }

    @Override
    protected void finishReload(ItemStack stack, Player player) {
        super.finishReload(stack, player);
        if (isFourIsReadyPlaying(stack)) {
            stopFourIsReadySound(player);
            setFourIsReadyPlaying(stack, false);
        }
    }

    @Override
    protected boolean executeShot(ItemStack stack, Player player, float charge, boolean isLeft) {
        boolean isLast = (isLeft ? getAmmoLeft(stack) : getAmmo(stack)) == 1;
        boolean success = super.executeShot(stack, player, charge, isLeft);

        if (success && isLast) {
            if (isFourIsReadyPlaying(stack)) {
                stopFourIsReadySound(player);
                setFourIsReadyPlaying(stack, false);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ZombieroolModSounds.WHISPER_FIRE.get(), SoundSource.PLAYERS, 6.0f, 1.0f);
        }

        return success;
    }

    /**
     * Méthode centralisée pour faire apparaître les particules du Whisper
     * Appelé depuis BallisticManager quand la cible meurt de la dernière balle.
     */
    public static void spawnCrowParticles(Entity target) {
        if (target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getEyeY();
            double z = target.getZ();

            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(.06f, .04f, .08f), .65f), x, y, z, 8, .12, .10, .12, .02);
            for (int i = 0; i < 9; i++) {
                double vx = (serverLevel.random.nextFloat() - 0.5) * .38;
                double vy = serverLevel.random.nextFloat() * .16 + .12;
                double vz = (serverLevel.random.nextFloat() - 0.5) * .38;
                serverLevel.sendParticles(ZombieroolModParticleTypes.BLACK_CROW.get(), x, y, z, 0, vx, vy, vz, 1.0);
            }
            serverLevel.playSound(null, x, y, z, ZombieroolModSounds.CROW_WAVE.get(), SoundSource.HOSTILE, 1.5f, 0.7f + serverLevel.random.nextFloat() * 0.3f);
        }
    }
}
