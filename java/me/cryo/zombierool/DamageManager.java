package me.cryo.zombierool.core.manager;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.CrawlerCorpse;
import me.cryo.zombierool.entity.DummyEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.util.PlayerVoiceManager;
import me.cryo.zombierool.career.CareerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.api.IHeadshotWeapon;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID)
public class DamageManager {

    public static final String HEADSHOT_TAG = "zombierool:is_headshot_damage";
    public static final String GUN_DAMAGE_TAG = "zombierool:damage_by_gun";
    public static final String HIT_ZONE_TAG = "zombierool:hit_zone";

    public static boolean applyDamage(LivingEntity target, DamageSource source, float damage) {
        target.invulnerableTime = 0; 
        return target.hurt(source, damage);
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity().getPersistentData().getBoolean("zr_prevent_knockback")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player targetPlayer) {
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof Player && attacker != targetPlayer) {
                event.setCanceled(true);
                return;
            }
            if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                if (proj.getOwner() instanceof Player && proj.getOwner() != targetPlayer) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    public static String computeHitZone(LivingEntity entity, Vec3 hitPos) {
        boolean isCrawler = entity instanceof CrawlerEntity || entity instanceof CrawlerCorpse;
        Vec3 entityCenter = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        Vec3 toHit = hitPos.subtract(entityCenter);

        Vec3 forward = Vec3.directionFromRotation(0, entity.getYRot()).normalize();
        Vec3 up;
        double lengthReference;

        if (isCrawler) {
            up = forward;
            lengthReference = entity.getBbWidth(); 
        } else {
            up = new Vec3(0, 1, 0); 
            lengthReference = entity.getBbHeight();
        }

        double alongBody = toHit.dot(up);
        double ratio = (alongBody + lengthReference / 2.0) / lengthReference;
        ratio = Math.max(0, Math.min(1, ratio));

        Vec3 right = isCrawler ? forward.cross(new Vec3(0, 1, 0)).normalize() : forward.cross(up).normalize();
        double lateral = toHit.dot(right);

        double armThreshold = entity.getBbWidth() * 0.22;

        if (ratio > 0.70) return "head"; 
        if (ratio > 0.35 && Math.abs(lateral) > armThreshold)
            return lateral > 0 ? "right_arm" : "left_arm";
        if (ratio > 0.35) return "torso";
        
        return "legs";
    }

    public static float calculateDamage(ServerPlayer attacker, LivingEntity target, float baseGunDamage, boolean isHeadshot, ItemStack weapon) {
        float damage = baseGunDamage;

        if (isHeadshot) {
            float globalMultiplier = 2.0f; 
            float flatBonus = 0.0f; 

            if (weapon != null) {
                WeaponSystem.Definition def = WeaponFacade.getDefinition(weapon);
                if (def != null && "SNIPER".equalsIgnoreCase(def.type)) {
                    globalMultiplier = 3.0f;
                }

                if (weapon.getItem() instanceof IHeadshotWeapon headshotWeapon) {
                    flatBonus += headshotWeapon.getHeadshotBaseDamage(weapon); 
                    if (WeaponFacade.isPackAPunched(weapon)) {
                        flatBonus += headshotWeapon.getHeadshotPapBonusDamage(weapon); 
                    }
                } else if (WeaponFacade.isTaczWeapon(weapon) && def != null) {
                    flatBonus += def.headshot.base_bonus_damage;
                    if (WeaponFacade.isPackAPunched(weapon)) {
                        flatBonus += def.headshot.pap_bonus_damage;
                    }
                }
            }
            damage = (baseGunDamage + flatBonus) * globalMultiplier;
        }

        if (BonusManager.isInstaKillActive(attacker)) {
            damage = 100000f;
        }

        return damage;
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (target instanceof Player player && source.getEntity() instanceof Player && target != source.getEntity()) {
            event.setCanceled(true);
            return;
        }

        if (target instanceof ZombieEntity zombie && !zombie.isCrawler()) {
            boolean isExplosive = target.getPersistentData().getBoolean("zombierool:explosive_damage") || source.is(DamageTypes.EXPLOSION);
            if (isExplosive) {
                if (target.getRandom().nextFloat() < 0.40f) {
                    boolean lethal = (target.getHealth() - event.getAmount()) <= 0;
                    zombie.makeCrawler();
                    if (lethal) {
                        event.setAmount(Math.max(0, target.getHealth() - 1.0f)); 
                    }
                }
            }
        }

        if (source.getEntity() instanceof ServerPlayer attackerPlayer && isValidTarget(target)) {
            if (me.cryo.zombierool.WaveManager.isGameRunning() && !attackerPlayer.isCreative() && !attackerPlayer.isSpectator()) {
                PlayerStatsManager.recordDamage(target, attackerPlayer);
            }
        }

        if (target instanceof Player playerTarget && !playerTarget.level().isClientSide) {
            Entity sourceEntity = source.getEntity();
            if (sourceEntity instanceof ZombieEntity) {
                PlayerVoiceManager.playZombieHit(playerTarget, playerTarget.level());
            } else if (sourceEntity instanceof CrawlerEntity) {
                PlayerVoiceManager.playCrawlerHit(playerTarget, playerTarget.level());
            } else if (sourceEntity instanceof HellhoundEntity) {
                PlayerVoiceManager.playHellhoundHit(playerTarget, playerTarget.level());
            } else if (sourceEntity instanceof LivingEntity) {
                PlayerVoiceManager.playGeneralHit(playerTarget, playerTarget.level());
            }
        }

        if (!isValidTarget(target)) return;
        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof ServerPlayer player)) return;

        if (target.getPersistentData().getBoolean("SkipFlamePoints")) return;

        PointManager.modifyScore(player, 10);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity target = event.getEntity();
        
        if (target.getPersistentData().getBoolean("zr_death_processed")) return;
        target.getPersistentData().putBoolean("zr_death_processed", true);

        DamageSource source = event.getSource();

        if (!isValidTarget(target)) return;

        Entity attackerEntity = source.getEntity();

        if (attackerEntity instanceof ServerPlayer player) {
            boolean isGunDamage = target.getPersistentData().getBoolean(GUN_DAMAGE_TAG);
            boolean wasHeadshotHit = target.getPersistentData().getBoolean(HEADSHOT_TAG);
            boolean isExplosive = target.getPersistentData().getBoolean("zombierool:explosive_damage") || source.is(DamageTypes.EXPLOSION) || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.ThrowableProjectile;
            String mobType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();

            if (me.cryo.zombierool.WaveManager.isGameRunning() && !player.isCreative() && !player.isSpectator()) {
                PlayerStatsManager.recordKill(target, player);
                
                if (!me.cryo.zombierool.WaveManager.areCheatsUsed()) {
                    CareerManager.progressChallenge(player, CareerManager.ChallengeType.KILLS, 1);
                }

                if (wasHeadshotHit) {
                    PlayerStatsManager.recordHeadshot(player);
                    if (!me.cryo.zombierool.WaveManager.areCheatsUsed()) {
                        CareerManager.progressChallenge(player, CareerManager.ChallengeType.HEADSHOTS, 1);
                    }
                }

                if (isExplosive && !me.cryo.zombierool.WaveManager.areCheatsUsed()) {
                    CareerManager.progressChallenge(player, CareerManager.ChallengeType.GRENADE_KILLS, 1);
                }

                if (isGunDamage && !me.cryo.zombierool.WaveManager.areCheatsUsed()) {
                    String wId = WeaponFacade.getWeaponId(player.getMainHandItem());
                    if (wId != null && !wId.isEmpty()) {
                        wId = wId.replace("zombierool:", "");
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new me.cryo.zombierool.network.packet.S2CProgressWeaponStatPacket(wId, 1, wasHeadshotHit ? 1 : 0, 0));
                    }
                }
            }

            if (isGunDamage) {
                if (!target.getPersistentData().getBoolean("zombierool:no_gore")) {
                    if (!wasHeadshotHit) {
                        String zone = target.getPersistentData().getString(HIT_ZONE_TAG);
                        switch (zone) {
                            case "left_arm"  -> GoreManager.triggerArmExplosion(target, GoreManager.Limb.LEFT_ARM);
                            case "right_arm" -> GoreManager.triggerArmExplosion(target, GoreManager.Limb.RIGHT_ARM);
                            default          -> GoreManager.tryDismemberLimb(target, 100f);
                        }
                    }
                }
            }

            LuaScriptManager.callEvent("OnZombieKill", player.getUUID().toString(), mobType, wasHeadshotHit);
            LuaScriptManager.callEvent("OnEntityKilled", player.getUUID().toString(), target.getUUID().toString(), wasHeadshotHit);

            if (wasHeadshotHit) {
                PlayerVoiceManager.playKillHeadshot(player, player.level());
            }

            if (target instanceof HellhoundEntity) {
                PlayerVoiceManager.playKillHellhound(player, player.level());
            } else if (target instanceof CrawlerEntity || (target instanceof ZombieEntity z && z.isCrawler())) {
                PlayerVoiceManager.playKillCrawler(player, player.level());
            }

            int points = 40; 
            if (isGunDamage) {
                points = wasHeadshotHit ? 90 : 40; 
            } else if (isExplosive) {
                points = 40; 
            } else if (source.getDirectEntity() instanceof Player) {
                points = 120; 
            }

            PointManager.modifyScore(player, points);

        } else {
            PlayerStatsManager.cleanupEntity(target.getId());
        }

        target.getPersistentData().remove(HEADSHOT_TAG);
        target.getPersistentData().remove(GUN_DAMAGE_TAG);
        target.getPersistentData().remove(HIT_ZONE_TAG);
        target.getPersistentData().remove("zombierool:explosive_damage");
        target.getPersistentData().remove("zombierool:no_gore");
    }

    private static boolean isValidTarget(LivingEntity entity) {
        return entity instanceof ZombieEntity || 
               entity instanceof CrawlerEntity || 
               entity instanceof HellhoundEntity || 
               entity instanceof DummyEntity;
    }
}