package me.cryo.zombierool.item;

import me.cryo.zombierool.core.manager.BallisticManager;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.DummyEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CWeaponVfxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WunderwaffeItem extends WeaponSystem.BaseGunItem {

    public WunderwaffeItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge) {
        if (player.level().isClientSide) return;

        boolean isPap = isPackAPunched(stack);
        float damage = getWeaponDamage(stack);

        fire((ServerPlayer)player, def, isPap, damage, getVisualMuzzlePos(player));
    }

    /** Native and TaCZ versions share targeting, damage, stun, chain limits and visuals. */
    public static void fire(ServerPlayer player, WeaponSystem.Definition definition, boolean isPap, float damage, Vec3 visualStart) {
        int maxChains = isPap ? 24 : 10;
        double chainRange = 8.0;

        Entity firstHit = BallisticManager.getRayTraceTarget(player, definition.stats.range);

        if (firstHit == null) {
            Vec3 eyePos = player.getEyePosition(1.0f);
            Vec3 lookVec = player.getViewVector(1.0f);
            Vec3 endPos = eyePos.add(lookVec.scale(definition.stats.range));
            sendLightningVfx(player, visualStart, endPos, isPap);
            return;
        }

        if (firstHit instanceof LivingEntity initialTarget) {
            Set<LivingEntity> hitTargets = new HashSet<>();
            LivingEntity currentTarget = initialTarget;
            Vec3 lastPos = visualStart;

            for (int i = 0; i < maxChains; i++) {
                if (currentTarget == null || !currentTarget.isAlive()) break;

                hitTargets.add(currentTarget);
                Vec3 targetCenter = currentTarget.position().add(0, currentTarget.getBbHeight() / 2.0, 0);

                sendLightningVfx(player, lastPos, targetCenter, isPap);
                lastPos = targetCenter;

                boolean wouldDie = currentTarget.getHealth() <= damage;
                DamageManager.applyDamage(currentTarget, player.damageSources().playerAttack(player), damage);

                // Son électrique
                currentTarget.level().playSound(null, currentTarget.blockPosition(), ZombieroolModSounds.ZOMBIE_ELEC.get(), SoundSource.HOSTILE, 1.0f, 1.0f);

                if (!wouldDie && currentTarget.isAlive()) {
                    me.cryo.zombierool.gameplay.ElectricStun.apply(currentTarget);
                }

                AABB searchBox = currentTarget.getBoundingBox().inflate(chainRange);
                List<LivingEntity> potentialNext = player.level().getEntitiesOfClass(LivingEntity.class, searchBox, e ->
                    e != player && e.isAlive() && !hitTargets.contains(e) &&
                    (e instanceof me.cryo.zombierool.entity.SpecialEnemyEntity || e instanceof ZombieEntity || e instanceof CrawlerEntity || e instanceof HellhoundEntity || e instanceof DummyEntity)
                );

                if (potentialNext.isEmpty()) break;

                final LivingEntity refTarget = currentTarget;
                potentialNext.sort(Comparator.comparingDouble(e -> e.distanceToSqr(refTarget)));
                currentTarget = potentialNext.get(0);
            }
        }
    }

    private static void sendLightningVfx(ServerPlayer player, Vec3 start, Vec3 end, boolean isPap) {
        S2CWeaponVfxPacket packet = new S2CWeaponVfxPacket("WUNDERWAFFE", start, end, isPap, false);
        NetworkHandler.INSTANCE.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(start.x,start.y,start.z,96,player.level().dimension())), packet);
    }
}
