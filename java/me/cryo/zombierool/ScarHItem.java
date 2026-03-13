package me.cryo.zombierool.item;

import me.cryo.zombierool.core.manager.BallisticManager;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.DummyEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.network.DisplayHitmarkerPacket;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.WeaponVfxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScarHItem extends WeaponImplementations.HitscanGunItem {

	public ScarHItem(WeaponSystem.Definition def) {
	    super(def);
	}

	@Override
	protected void performShooting(ItemStack stack, Player player, float charge) {
	    if (isPackAPunched(stack)) {
	        if (player.level().isClientSide) return;

	        float damage = getWeaponDamage(stack);
	        int maxChains = def.stats.penetration + def.pap.penetration_bonus;
	        if (maxChains <= 0) maxChains = 3;
	        double chainRange = 6.0;

            Entity firstHit = BallisticManager.getRayTraceTarget((ServerPlayer) player, def.stats.range);

            if (firstHit == null) {
                Vec3 eyePos = player.getEyePosition(1.0f);
                Vec3 lookVec = player.getViewVector(1.0f);
                Vec3 visualStart = getVisualMuzzlePos(player);
                Vec3 endPos = eyePos.add(lookVec.scale(def.stats.range));
                
                sendLightningVfx((ServerPlayer) player, visualStart, endPos, false);
                return;
            }

            if (firstHit instanceof LivingEntity initialTarget) {
                Set<LivingEntity> hitTargets = new HashSet<>();
                LivingEntity currentTarget = initialTarget;
                Vec3 lastPos = getVisualMuzzlePos(player);

                for (int i = 0; i < maxChains; i++) {
                    if (currentTarget == null || !currentTarget.isAlive()) break;
                    hitTargets.add(currentTarget);

                    Vec3 targetCenter = currentTarget.position().add(0, currentTarget.getBbHeight() / 2.0, 0);
                    
                    sendLightningVfx((ServerPlayer) player, lastPos, targetCenter, false);

                    lastPos = targetCenter;

                    boolean isHeadshot = false;
                    if (i == 0) {
                        Vec3 eyePos = player.getEyePosition(1.0f);
                        Vec3 lookVec = player.getViewVector(1.0f);
                        Vec3 start = eyePos;
                        Vec3 end = start.add(lookVec.scale(def.stats.range));
                        
                        var hitResult = currentTarget.getBoundingBox().clip(start, end);
                        if (hitResult.isPresent()) {
                            if (hitResult.get().y >= currentTarget.getY() + currentTarget.getBbHeight() * 0.85) {
                                isHeadshot = true;
                            }
                        }
                    }

                    float finalDamage = DamageManager.calculateDamage((ServerPlayer) player, currentTarget, damage, isHeadshot, stack);
                    
                    currentTarget.getPersistentData().putBoolean(DamageManager.GUN_DAMAGE_TAG, true);
                    if (isHeadshot) {
                        currentTarget.getPersistentData().putBoolean(DamageManager.HEADSHOT_TAG, true);
                    } else {
                        currentTarget.getPersistentData().remove(DamageManager.HEADSHOT_TAG);
                    }

                    if (DamageManager.applyDamage(currentTarget, player.damageSources().playerAttack(player), finalDamage)) {
                        currentTarget.level().playSound(null, currentTarget.getX(), currentTarget.getY(), currentTarget.getZ(),
                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:impact_flesh")),
                            SoundSource.PLAYERS, 0.5f, 1.0f + (currentTarget.getRandom().nextFloat() * 0.2f));

                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), new DisplayHitmarkerPacket());
                    }

                    AABB searchBox = currentTarget.getBoundingBox().inflate(chainRange);
                    List<LivingEntity> potentialNext = player.level().getEntitiesOfClass(LivingEntity.class, searchBox, e ->
                        e != player && e.isAlive() && !hitTargets.contains(e) &&
                        (e instanceof ZombieEntity || e instanceof CrawlerEntity || e instanceof HellhoundEntity || e instanceof DummyEntity)
                    );

                    if (potentialNext.isEmpty()) break;

                    final LivingEntity refTarget = currentTarget;
                    potentialNext.sort(Comparator.comparingDouble(e -> e.distanceToSqr(refTarget)));
                    currentTarget = potentialNext.get(0);
                }
            }

	    } else {
	        super.performShooting(stack, player, charge);
	    }
	}

	private void sendLightningVfx(ServerPlayer player, Vec3 start, Vec3 end, boolean isPap) {
	    WeaponVfxPacket packet = new WeaponVfxPacket("WUNDERWAFFE", start, end, isPap, false);
	    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> player.level().getChunkAt(player.blockPosition())), packet);
	    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
	}
}