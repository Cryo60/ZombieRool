package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.StopFourIsReadySoundPacket;
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
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new StopFourIsReadySoundPacket());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean selected) {
        super.inventoryTick(stack, level, ent, slot, selected);
        if (!(ent instanceof Player player)) return;

        int ammo = getAmmo(stack);
        boolean isPlaying = isFourIsReadyPlaying(stack);

        if (selected) {
            if (ammo == 1 && !isPlaying) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ZombieroolModSounds.FOUR_IS_READY.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                setFourIsReadyPlaying(stack, true);
            } else if (ammo != 1 && isPlaying) {
                stopFourIsReadySound(player);
                setFourIsReadyPlaying(stack, false);
            }
        } else {
            if (isPlaying) {
                stopFourIsReadySound(player);
                setFourIsReadyPlaying(stack, false);
            }
        }
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
            double y = target.getY() + target.getBbHeight() / 2.0D;
            double z = target.getZ();

            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0f, 0f, 0f), 3.0f), x, y, z, 50, 0.4, 0.4, 0.4, 0.15);
            for (int i = 0; i < 40; i++) {
                double vx = (serverLevel.random.nextFloat() - 0.5) * 2.0;
                double vy = serverLevel.random.nextFloat() * 1.5 + 0.5;
                double vz = (serverLevel.random.nextFloat() - 0.5) * 2.0;
                serverLevel.sendParticles(ZombieroolModParticleTypes.BLACK_CROW.get(), x, y, z, 0, vx, vy, vz, 1.0);
            }
            serverLevel.playSound(null, x, y, z, ZombieroolModSounds.CROW_WAVE.get(), SoundSource.HOSTILE, 1.5f, 0.7f + serverLevel.random.nextFloat() * 0.3f);
        }
    }
}