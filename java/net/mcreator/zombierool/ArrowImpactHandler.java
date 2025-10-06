package net.mcreator.zombierool;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraft.sounds.SoundEvent;

import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IHeadshotWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;
import net.mcreator.zombierool.entity.MannequinEntity;
import net.mcreator.zombierool.bonuses.BonusManager;
import net.mcreator.zombierool.PointManager; // Import du PointManager

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

import net.mcreator.zombierool.init.ZombieroolModParticleTypes;
import net.mcreator.zombierool.item.M1911WeaponItem;
import net.mcreator.zombierool.item.RaygunWeaponItem;
import net.mcreator.zombierool.item.RaygunMarkiiItem;
import net.mcreator.zombierool.item.TrenchGunWeaponItem;
import net.mcreator.zombierool.item.BarretWeaponItem;
import net.mcreator.zombierool.item.InterventionWeaponItem;
import net.mcreator.zombierool.item.NeedlerWeaponItem;
import net.mcreator.zombierool.item.PlasmaPistolWeaponItem;
import net.mcreator.zombierool.item.HydraWeaponItem;
import net.mcreator.zombierool.item.RPGWeaponItem;
import net.mcreator.zombierool.item.ChinaLakeWeaponItem;
import net.mcreator.zombierool.item.PercepteurWeaponItem;

import net.minecraft.world.effect.MobEffectInstance;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;

import net.minecraft.server.level.ServerPlayer;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.DisplayHitmarkerPacket;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import javax.annotation.Nullable;

import net.minecraft.util.RandomSource;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

// Imports for blood particles
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;



@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArrowImpactHandler {

    private static final float INSTA_KILL_MULTIPLIER = 1000.0f;
    private static final String TAG_LASER_UPGRADED = "IsLaserUpgraded";
    private static final float GENERIC_HEADSHOT_MULTIPLIER = 2.0f;
    private static final float WHISPER_LAST_BULLET_HEADSHOT_MULTIPLIER = 1.5f;

    private static final RandomSource RANDOM = RandomSource.create();
    // --- Needler Specific Constants ---
    private static final String TAG_NEEDLER_PROJECTILE = "zombierool:needler_projectile";
    private static final String TAG_NEEDLER_DATA = "NeedlerData";
    private static final String TAG_NEEDLE_COUNT = "NeedleCount";
    private static final String TAG_LAST_NEEDLE_HIT_TICK = "LastNeedleHitTick";
    private static final String TAG_ATTACHED_NEEDLE_UUIDS = "AttachedNeedleUUIDs";
    private static final int SUPERCOMBINE_THRESHOLD = 7;
    private static final int NEEDLE_DECAY_TICKS = 40;
    private static final float SUPERCOMBINE_RADIUS = 2.5f;
    private static final float SUPERCOMBINE_DAMAGE = 30.0f;
    private static final float NEEDLE_IMPACT_SOUND_VOLUME = 0.7f;
    private static final float NEEDLE_IMPACT_SOUND_PITCH_VARIATION = 0.05F;
    private static final float SUPERCOMBINE_SOUND_VOLUME = 3.0f;
    private static final float SUPERCOMBINE_SOUND_PITCH = 1.0f;
    // --- End Needler Specific Constants ---

    // --- Particle Constants ---
    private static final int LASER_PARTICLE_COUNT = 20;
    private static final double LASER_PARTICLE_SPREAD = 0.4D; // Augmenté pour plus de dispersion
    private static final double LASER_PARTICLE_VELOCITY = 0.1D; // Augmenté pour plus de volatilité
    private static final int MUSTANG_SALLY_PARTICLE_COUNT = 1;
    private static final double MUSTANG_SALLY_PARTICLE_SPREAD = 0.0D;
    private static final double MUSTANG_SALLY_PARTICLE_VELOCITY = 0.0D;
    private static final int NEEDLER_PARTICLE_COUNT = 5;
    private static final double NEEDLER_PARTICLE_SPREAD = 0.1D;
    private static final double NEEDLER_PARTICLE_VELOCITY = 0.02D;
    private static final int GENERIC_BULLET_PARTICLE_COUNT = 1;
    private static final double GENERIC_BULLET_PARTICLE_SPREAD = 0.0D;
    private static final double GENERIC_BULLET_PARTICLE_VELOCITY = 0.0D;
    // --- End Particle Constants ---

    // --- Damage and Hitbox Constants ---
    private static final double HEADSHOT_THRESHOLD_PERCENTAGE = 0.85;
    private static final float LASER_AOE_RADIUS = 2.0f;
    // R.A.S. - Ces constantes ne sont plus utilisées directement pour le calcul des dégâts AoE du laser,
    // car les dégâts AoE sont maintenant basés sur les dégâts de l'arme.
    // private static final float UPGRADED_LASER_AOE_DAMAGE_PERCENTAGE = 0.20f;
    // private static final float BASE_LASER_AOE_DAMAGE_PERCENTAGE = 0.10f;
    private static final float MUSTANG_SALLY_EXPLOSION_RADIUS = 1.0f;
    private static final float MUSTANG_SALLY_AOE_DAMAGE = 10.0f;
    private static final float FLESH_IMPACT_SOUND_VOLUME = 2.0f;
    private static final float FLESH_IMPACT_SOUND_PITCH = 1.0f;
    // --- End Damage and Hitbox Constants ---

    // --- MLG Easter Egg Constants ---
    private static final int MLG_EASTER_EGG_CHANCE = 100;
    private static final float MLG_SOUND_VOLUME = 2.0F;
    private static final float MLG_SOUND_PITCH_VARIATION = 0.1F;
    // --- End MLG Easter Egg Constants ---

    @SubscribeEvent
    public static void onArrowImpact(ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!(arrow.getOwner() instanceof Player player)) {
            return;
        }

        // --- NOUVELLE LOGIQUE POUR LES POINTS SUR IMPACT DIRECT ---
        // On vérifie l'impact sur une entité pour attribuer les points.
        if (event.getRayTraceResult() instanceof EntityHitResult ehr) {
            if (ehr.getEntity() instanceof LivingEntity target) {
                 ItemStack weaponStackForPoints = player.getMainHandItem();
                // On ne donne pas de points ici si l'arme a sa propre logique (ex: Percepteur)
                if (!(weaponStackForPoints.getItem() instanceof PercepteurWeaponItem)) {
                    boolean isOurCustomMob = target instanceof ZombieEntity ||
                                             target instanceof CrawlerEntity ||
                                             target instanceof MannequinEntity ||
                                             target instanceof HellhoundEntity;

                    // Si c'est un de nos mobs, on donne les points
                    if (isOurCustomMob) {
                        PointManager.modifyScore(player, 10);
                    }
                }
            }
        }
        // --- FIN DE LA NOUVELLE LOGIQUE POUR LES POINTS SUR IMPACT DIRECT ---

        HitResult result = event.getRayTraceResult();
        event.setCanceled(true); // Always cancel default arrow behavior

        ItemStack weaponStack = player.getMainHandItem();
        boolean isLaserProjectile = arrow.getPersistentData().contains(TAG_LASER_UPGRADED);
        boolean isUpgradedLaser = isLaserProjectile && arrow.getPersistentData().getBoolean(TAG_LASER_UPGRADED);
        boolean isTrenchGunPellet = arrow.getPersistentData().getBoolean("zombierool:shotgun_pellet");

        boolean isBarretBullet = weaponStack.getItem() instanceof BarretWeaponItem;
        boolean isArmorPiercing = arrow.getPersistentData().getBoolean(BarretWeaponItem.TAG_ARMOR_PIERCING);
        int targetsToPierce = arrow.getPersistentData().getInt(BarretWeaponItem.TAG_MULTI_TARGET_PIERCING);

        boolean isMustangAndSally = weaponStack.getItem() instanceof M1911WeaponItem
                                    && ((M1911WeaponItem) weaponStack.getItem()).isPackAPunched(weaponStack);
        boolean isIntervention = weaponStack.getItem() instanceof InterventionWeaponItem;
        boolean isNeedlerProjectile = arrow.getPersistentData().getBoolean(TAG_NEEDLER_PROJECTILE);

        boolean isPlasmaPistolProjectile = weaponStack.getItem() instanceof PlasmaPistolWeaponItem;
        boolean isPlasmaPistolOvercharged = false;
        if (isPlasmaPistolProjectile) {
            isPlasmaPistolOvercharged = arrow.getPersistentData().getBoolean(PlasmaPistolWeaponItem.TAG_IS_OVERCHARGED);
        }
        // Check if it's an Hydra projectile
        boolean isHydraProjectile = arrow.getPersistentData().getBoolean(HydraWeaponItem.TAG_IS_HYDRA_PROJECTILE);
        // Check if it's an RPG projectile
        boolean isRPGProjectile = arrow.getPersistentData().getBoolean(RPGWeaponItem.TAG_IS_RPG_PROJECTILE);
        // Placeholder for China Lake projectile
        boolean isChinaLakeProjectile = arrow.getPersistentData().getBoolean(ChinaLakeWeaponItem.TAG_IS_CHINALAKE_PROJECTILE);
        // Vérifier si c'est un projectile du Percepteur
        boolean isPercepteurProjectile = arrow.getPersistentData().getBoolean(PercepteurWeaponItem.TAG_PERCEPTEUR_PROJECTILE);

        // Vérifier si le propriétaire a PHD Flopper en utilisant les MobEffects
        boolean hasPHDFlopper = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get());

        float baseWeaponDamage = 0;
        if (weaponStack.getItem() instanceof ICustomWeapon w) {
            baseWeaponDamage = w.getWeaponDamage(weaponStack);
        } else {
            baseWeaponDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        }

        SimpleParticleType particleToSpawn = null;
        int particleCount = 0;
        double particleSpreadX = 0.0D;
        double particleSpreadY = 0.0D;
        double particleSpreadZ = 0.0D;
        double particleVelocity = 0.0D;
        if (isLaserProjectile) {
            // Particles for laser impact, using END_ROD for a more "energy spark" look
            particleToSpawn = ParticleTypes.END_ROD; // Changé de ELECTRIC_SPARK/CRIT à END_ROD
            particleCount = LASER_PARTICLE_COUNT;
            particleSpreadX = LASER_PARTICLE_SPREAD;
            particleSpreadY = LASER_PARTICLE_SPREAD;
            particleSpreadZ = LASER_PARTICLE_SPREAD;
            particleVelocity = LASER_PARTICLE_VELOCITY;
        } else if (isMustangAndSally) {
            particleToSpawn = ParticleTypes.EXPLOSION;
            particleCount = MUSTANG_SALLY_PARTICLE_COUNT;
            particleSpreadX = MUSTANG_SALLY_PARTICLE_SPREAD;
            particleSpreadY = MUSTANG_SALLY_PARTICLE_SPREAD;
            particleSpreadZ = MUSTANG_SALLY_PARTICLE_SPREAD;
            particleVelocity = MUSTANG_SALLY_PARTICLE_VELOCITY;
        } else if (isNeedlerProjectile) {
            particleToSpawn = ParticleTypes.CRIT;
            particleCount = NEEDLER_PARTICLE_COUNT;
            particleSpreadX = NEEDLER_PARTICLE_SPREAD;
            particleSpreadY = NEEDLER_PARTICLE_SPREAD;
            particleSpreadZ = NEEDLER_PARTICLE_SPREAD;
            particleVelocity = NEEDLER_PARTICLE_VELOCITY;
        } else if (isHydraProjectile) { // Particle for Hydra impact
            particleToSpawn = ParticleTypes.SMOKE;
            particleCount = 5;
            particleSpreadX = 0.5D;
            particleSpreadY = 0.5D;
            particleSpreadZ = 0.5D;
            particleVelocity = 0.1D;
        } else if (isRPGProjectile) { // Particle for RPG impact
            particleToSpawn = ParticleTypes.LARGE_SMOKE; // Plus de fumée pour le RPG
            particleCount = 10;
            particleSpreadX = 0.8D;
            particleSpreadY = 0.8D;
            particleSpreadZ = 0.8D;
            particleVelocity = 0.2D;
        }
        else if (isChinaLakeProjectile) {
            particleToSpawn = ParticleTypes.SMOKE;
            particleSpreadX = 0.7D;
            particleSpreadY = 0.7D;
            particleSpreadZ = 0.7D;
            particleVelocity = 0.15D;
        }
        // Ajout des particules pour le Percepteur (peut-être des particules d'âme ou d'énergie subtiles)
        else if (isPercepteurProjectile) {
            particleToSpawn = ParticleTypes.SOUL; // Ou un autre type de particule qui correspond à l'idée d'âme/exécution
            particleCount = 3;
            particleSpreadX = 0.05D;
            particleSpreadY = 0.05D;
            particleSpreadZ = 0.05D;
            particleVelocity = 0.01D;
        }
        else {
            particleToSpawn = ZombieroolModParticleTypes.BULLET_IMPACT.get();
            particleCount = GENERIC_BULLET_PARTICLE_COUNT;
            particleSpreadX = GENERIC_BULLET_PARTICLE_SPREAD;
            particleSpreadY = GENERIC_BULLET_PARTICLE_SPREAD;
            particleSpreadZ = GENERIC_BULLET_PARTICLE_SPREAD;
            particleVelocity = GENERIC_BULLET_PARTICLE_VELOCITY;
        }

        if (event.getEntity().level() instanceof ServerLevel serverLevel) {
            if (particleToSpawn != null) {
                serverLevel.sendParticles(particleToSpawn,
                    result.getLocation().x, result.getLocation().y, result.getLocation().z,
                    particleCount,
                    particleSpreadX, particleSpreadY, particleSpreadZ,
                    particleVelocity
                );
            }
        }

        // --- GESTION DE L'IMPACT SUR UNE ENTITÉ ---
        if (result instanceof EntityHitResult ehr) {
            LivingEntity target = (LivingEntity) ehr.getEntity();
            boolean isOurCustomMob = target instanceof ZombieEntity
                                  ||
            target instanceof CrawlerEntity
                                  ||
            target instanceof MannequinEntity
                                  ||
            target instanceof HellhoundEntity;

            List<UUID> hitEntities = new ArrayList<>();
            if (arrow.getPersistentData().contains("hitEntities")) {
                long[] uuidsLong = arrow.getPersistentData().getLongArray("hitEntities");
                for (int i = 0; i < uuidsLong.length; i += 2) {
                    hitEntities.add(new UUID(uuidsLong[i], uuidsLong[i + 1]));
                }
            }

            if (isOurCustomMob || target == player) { // Permettre le ciblage du joueur pour l'auto-dommage
                // Le Percepteur est un projectile non explosif, mais il ne perce pas.
                // Le Needler peut toucher plusieurs fois, Hydra et RPG causent une explosion.
                if (hitEntities.contains(target.getUUID()) && !(isNeedlerProjectile) && !(isHydraProjectile) && !(isRPGProjectile) && !(isPercepteurProjectile) && !(isLaserProjectile)) {
                     if (isBarretBullet && targetsToPierce > 0) {
                         // Ne pas défausser, permettre le piercing
                     } else {
                         arrow.discard();
                         return;
                     }
                }

                double headshotYThreshold = target.getY() + target.getBbHeight() * HEADSHOT_THRESHOLD_PERCENTAGE;
                boolean headshot = target.getBbHeight() > 0 && ehr.getLocation().y >= headshotYThreshold;
                float finalDamage = 0;

                boolean isWhisperLastBullet = arrow.getPersistentData().getBoolean("zombierool:last_whisper_bullet");
                if (isTrenchGunPellet) {
                    float distance = player.distanceTo(target);
                    finalDamage = TrenchGunWeaponItem.calculateAdjustedDamage(arrow, target, distance);

                    if (headshot && weaponStack.getItem() instanceof IHeadshotWeapon headshotWeapon) {
                        float headshotMultiplier = 1.0f;
                        if (headshotWeapon instanceof IPackAPunchable papWeapon && papWeapon.isPackAPunched(weaponStack)) {
                            headshotMultiplier += headshotWeapon.getHeadshotPapBonusDamage(weaponStack);
                        } else {
                            headshotMultiplier += headshotWeapon.getHeadshotBaseDamage(weaponStack);
                        }
                        finalDamage *= headshotMultiplier;
                    }
                }
                else if (isWhisperLastBullet) {
                    finalDamage = arrow.getPersistentData().getFloat("zombierool:arrow_damage");
                    if (headshot) {
                        finalDamage *= WHISPER_LAST_BULLET_HEADSHOT_MULTIPLIER;
                    }
                }
                else if (isPlasmaPistolProjectile) {
                    finalDamage = arrow.getPersistentData().getFloat(PlasmaPistolWeaponItem.TAG_ARROW_DAMAGE);
                    if (headshot) {
                        PlasmaPistolWeaponItem plasmaPistol = (PlasmaPistolWeaponItem) weaponStack.getItem();
                        float headshotBonus = plasmaPistol.getHeadshotBaseDamage(weaponStack);
                        if (plasmaPistol.isPackAPunched(weaponStack)) {
                            headshotBonus = plasmaPistol.getHeadshotPapBonusDamage(weaponStack);
                        }
                        finalDamage += headshotBonus;
                    }
                }
                // Hydra direct impact damage (less relevant than AoE, but still present)
                else if (isHydraProjectile) {
                    finalDamage = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");
                    if (headshot && weaponStack.getItem() instanceof IHeadshotWeapon headshotWeapon) {
                        float headshotDamage = 0;
                        if (headshotWeapon instanceof IPackAPunchable papWeapon && papWeapon.isPackAPunched(weaponStack)) {
                            headshotDamage = headshotWeapon.getHeadshotPapBonusDamage(weaponStack);
                        } else {
                            headshotDamage = headshotWeapon.getHeadshotBaseDamage(weaponStack);
                        }
                        finalDamage = headshotDamage;
                    }
                }
                // RPG direct impact damage (less relevant than AoE, but still present)
                else if (isRPGProjectile) {
                    finalDamage = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");
                    if (headshot && weaponStack.getItem() instanceof IHeadshotWeapon headshotWeapon) {
                        float headshotDamage = 0;
                        if (headshotWeapon instanceof IPackAPunchable papWeapon && papWeapon.isPackAPunched(weaponStack)) {
                            headshotDamage = headshotWeapon.getHeadshotPapBonusDamage(weaponStack);
                        } else {
                            headshotDamage = headshotWeapon.getHeadshotBaseDamage(weaponStack);
                        }
                        finalDamage = headshotDamage;
                    }
                }
                else if (isChinaLakeProjectile) {
                    finalDamage = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");
                    if (headshot && weaponStack.getItem() instanceof IHeadshotWeapon headshotWeapon) {
                        float headshotDamage = 0;
                        if (headshotWeapon instanceof IPackAPunchable papWeapon && papWeapon.isPackAPunched(weaponStack)) {
                            headshotDamage = headshotWeapon.getHeadshotPapBonusDamage(weaponStack);
                        } else {
                            headshotDamage = headshotWeapon.getHeadshotBaseDamage(weaponStack);
                        }
                        finalDamage = headshotDamage;
                    }
                }
                // Logique de dégâts pour le Percepteur
                else if (isPercepteurProjectile) {
                    PercepteurWeaponItem percepteurWeapon = (PercepteurWeaponItem) weaponStack.getItem();
                    finalDamage = percepteurWeapon.getWeaponDamage(weaponStack); // Dégâts de base du Percepteur

                    // Vérifier si la cible est éligible pour l'exécution
                    // Suppression du bloc try-catch car le getter est maintenant attendu.
                    float executionHealthThreshold = percepteurWeapon.getExecutionHealthPercentage() * target.getMaxHealth();

                    if (target.getHealth() <= executionHealthThreshold) {
                        // Exécution : infliger des dégâts massifs
                        finalDamage = target.getMaxHealth() * 100f; // Dégâts suffisants pour tuer
                        if (player instanceof ServerPlayer serverPlayer) {
                            int executionPoints = arrow.getPersistentData().getInt("PercepteurExecutionPoints");
                            PointManager.modifyScore(serverPlayer, executionPoints); // Ajouter les points d'exécution
                            // Afficher un message ou une particule pour l'exécution réussie
                            serverPlayer.sendSystemMessage(Component.literal("§6Exécution! +" + executionPoints + " points!"));
                        }
                    } else if (headshot) {
                        float headshotDamage = 0;
                        if (percepteurWeapon.isPackAPunched(weaponStack)) {
                            headshotDamage = percepteurWeapon.getHeadshotPapBonusDamage(weaponStack);
                        } else {
                            headshotDamage = percepteurWeapon.getHeadshotBaseDamage(weaponStack);
                        }
                        finalDamage += headshotDamage; // Ajouter le bonus de tir à la tête
                    }
                }
                else if (weaponStack.getItem() instanceof ICustomWeapon customWeapon) {
                    finalDamage = customWeapon.getWeaponDamage(weaponStack);
                    if (headshot && weaponStack.getItem() instanceof IHeadshotWeapon headshotWeapon) {
                        float headshotDamage = 0;
                        if (headshotWeapon instanceof IPackAPunchable papWeapon && papWeapon.isPackAPunched(weaponStack)) {
                            headshotDamage = headshotWeapon.getHeadshotPapBonusDamage(weaponStack);
                        } else {
                            headshotDamage = headshotWeapon.getHeadshotBaseDamage(weaponStack);
                        }
                        finalDamage = headshotDamage;
                    }
                }
                else if (arrow.getPersistentData().contains("zombierool:arrow_damage")) {
                    finalDamage = arrow.getPersistentData().getFloat("zombierool:arrow_damage");
                    if (headshot) {
                        finalDamage *= GENERIC_HEADSHOT_MULTIPLIER;
                    }
                }
                else {
                    finalDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    if (headshot) {
                        finalDamage *= GENERIC_HEADSHOT_MULTIPLIER;
                    }
                }

                // Insta-Kill applied to monster damage (restored)
                if (isOurCustomMob && BonusManager.isInstaKillActive(player)) {
                   finalDamage *= INSTA_KILL_MULTIPLIER;
                }

                DamageSource src = arrow.level().damageSources().arrow(arrow, player);
                // --- Needler Supercombine Logic ---
                if (isNeedlerProjectile) {
                    CompoundTag needlerData = target.getPersistentData().getCompound(TAG_NEEDLER_DATA);
                    long currentTick = target.level().getGameTime();

                    int needleCount = needlerData.getInt(TAG_NEEDLE_COUNT);
                    long lastHitTick = needlerData.getLong(TAG_LAST_NEEDLE_HIT_TICK);
                    long[] attachedUuidsLong = needlerData.getLongArray(TAG_ATTACHED_NEEDLE_UUIDS);
                    List<UUID> attachedUuids = new ArrayList<>();
                    for (int i = 0; i < attachedUuidsLong.length; i += 2) {
                        // Correction: Utiliser attachedUuidsLong ici, comme déclaré ci-dessus.
                        attachedUuids.add(new UUID(attachedUuidsLong[i], attachedUuidsLong[i + 1]));
                    }

                    // Reset count if too much time has passed
                    if (currentTick - lastHitTick > NEEDLE_DECAY_TICKS) {
                        needleCount = 0;
                        attachedUuids.clear();
                        for (UUID uuid : attachedUuids) {
                            Entity attachedArrow = ((ServerLevel)target.level()).getEntity(uuid);
                            if (attachedArrow != null) {
                                attachedArrow.discard();
                            }
                        }
                    }

                    if (!attachedUuids.contains(arrow.getUUID())) {
                         needleCount++;
                         attachedUuids.add(arrow.getUUID());
                        lastHitTick = currentTick;

                        // Appliquer les dégâts d'une aiguille individuelle uniquement aux monstres
                        if (isOurCustomMob) {
                            target.hurt(src, finalDamage);
                            if (player instanceof ServerPlayer serverPlayer) {
                                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new DisplayHitmarkerPacket());
                            }
                        }
                    } else {
                         arrow.discard();
                         return;
                    }

                    needlerData.putInt(TAG_NEEDLE_COUNT, needleCount);
                    needlerData.putLong(TAG_LAST_NEEDLE_HIT_TICK, lastHitTick);
                    long[] newUuidsLong = new long[attachedUuids.size() * 2];
                    for (int i = 0; i < attachedUuids.size(); i++) {
                        UUID uuid = attachedUuids.get(i);
                        newUuidsLong[i * 2] = uuid.getMostSignificantBits();
                        newUuidsLong[i * 2 + 1] = uuid.getLeastSignificantBits();
                    }
                    needlerData.putLongArray(TAG_ATTACHED_NEEDLE_UUIDS, newUuidsLong);
                    target.getPersistentData().put(TAG_NEEDLER_DATA, needlerData);

                    if (needleCount >= SUPERCOMBINE_THRESHOLD) {
                        float explosionDamage = SUPERCOMBINE_DAMAGE;
                        // Insta-Kill applied to explosion damage (restored)
                        if (BonusManager.isInstaKillActive(player)) {
                           explosionDamage *= INSTA_KILL_MULTIPLIER;
                        }

                        // Manually spawn explosion particles and play sound
                        if (arrow.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + target.getBbHeight() / 2.0D, target.getZ(), 1, 0, 0, 0, 0);
                            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.getX(), target.getY() + target.getBbHeight() / 2.0D, target.getZ(), 1, 0, 0, 0, 0);
                        }
                        SoundEvent supercombineSound = ZombieroolModSounds.NEEDLER_SUPERCOMBINE.get();
                        if (supercombineSound != null) {
                            arrow.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                                supercombineSound, SoundSource.PLAYERS, SUPERCOMBINE_SOUND_VOLUME, SUPERCOMBINE_SOUND_PITCH);
                        } else {
                            System.err.println("[ArrowImpactHandler] Needler Supercombine: Sound 'needler_supercombine' not found.");
                        }


                        AABB aoeBox = new AABB(
                            target.getX() - SUPERCOMBINE_RADIUS, target.getY() - SUPERCOMBINE_RADIUS, target.getZ() - SUPERCOMBINE_RADIUS,
                            target.getX() + SUPERCOMBINE_RADIUS, target.getY() +
                            SUPERCOMBINE_RADIUS, target.getZ() + SUPERCOMBINE_RADIUS
                        );
                        List<LivingEntity> affectedEntities = arrow.level().getEntitiesOfClass(LivingEntity.class, aoeBox);

                        for (LivingEntity affected : affectedEntities) {
                            // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                            boolean isAffectedCustomMob = affected instanceof ZombieEntity
                                                      ||
                            affected instanceof CrawlerEntity
                                                      ||
                            affected instanceof MannequinEntity
                                                      ||
                            affected instanceof HellhoundEntity;

                            if (isAffectedCustomMob) {
                                affected.hurt(arrow.level().damageSources().indirectMagic(arrow, player), explosionDamage);
                                if (player instanceof ServerPlayer sp) {
                                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new DisplayHitmarkerPacket());
                                }
                                // Attribution des points pour les dégâts de zone du Needler
                                PointManager.modifyScore(player, 10);
                            } else if (affected == player && !hasPHDFlopper) {
                                // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                                float selfDamage = player.getMaxHealth() * 0.30f;
                                player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                                System.out.println("DEBUG: Player took self-damage (Needler)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                            }
                            // Important : Les autres joueurs (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                        }

                        needlerData.putInt(TAG_NEEDLE_COUNT, 0);
                        needlerData.putLong(TAG_LAST_NEEDLE_HIT_TICK, 0);
                        needlerData.putLongArray(TAG_ATTACHED_NEEDLE_UUIDS, new long[0]);
                        target.getPersistentData().put(TAG_NEEDLER_DATA, needlerData);

                        for (UUID uuid : attachedUuids) {
                            Entity attachedArrow = ((ServerLevel)target.level()).getEntity(uuid);
                            if (attachedArrow != null) {
                                attachedArrow.discard();
                            }
                        }
                    }
                    arrow.discard();
                    return;
                }
                // --- End Needler Supercombine Logic ---

                // --- Hydra Explosion Logic (on Entity) ---
                if (isHydraProjectile) {
                    float explosionRadius = arrow.getPersistentData().getFloat("zombierool:explosion_radius");
                    float explosionDamageMultiplier = arrow.getPersistentData().getFloat("zombierool:explosion_damage_multiplier");
                    float baseWeaponDamageForAoE = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");

                    float explosionDamage = baseWeaponDamageForAoE * explosionDamageMultiplier;
                    // Insta-Kill applied to explosion damage (restored)
                    if (BonusManager.isInstaKillActive(player)) {
                       explosionDamage *= INSTA_KILL_MULTIPLIER;
                    }

                    // Manually spawn explosion particles and play sound
                    if (arrow.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z, 1, 0, 0, 0, 0);
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z, 1, 0, 0, 0, 0);
                    }
                    arrow.level().playSound(null, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.0f);


                    // Deal AoE damage to other entities
                    AABB aoeBox = new AABB(
                        ehr.getLocation().x - explosionRadius,
                        ehr.getLocation().y - explosionRadius,
                        ehr.getLocation().z - explosionRadius,
                        ehr.getLocation().x + explosionRadius,
                        ehr.getLocation().y + explosionRadius,
                        ehr.getLocation().z + explosionRadius
                    );
                    List<LivingEntity> affectedEntities = arrow.level().getEntitiesOfClass(LivingEntity.class, aoeBox);
                    for (LivingEntity affected : affectedEntities) {
                        // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                        boolean isAffectedCustomMob = affected instanceof ZombieEntity
                                                  ||
                        affected instanceof CrawlerEntity
                                                  ||
                        affected instanceof MannequinEntity
                                                  ||
                        affected instanceof HellhoundEntity;
                        if (isAffectedCustomMob) {
                            float currentAoeDamage = explosionDamage;
                            DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                            affected.hurt(aoeSrc, currentAoeDamage);
                            if (player instanceof ServerPlayer sp) {
                                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new DisplayHitmarkerPacket());
                            }
                            // Attribution des points pour les dégâts de zone de l'Hydra
                            PointManager.modifyScore(player, 10);
                        } else if (affected == player && !hasPHDFlopper) {
                            // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                            float selfDamage = player.getMaxHealth() * 0.30f;
                            player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                            System.out.println("DEBUG: Player took self-damage (Hydra/Entity)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                        }
                        // Important : Les autres joueurs (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                    }

                    arrow.discard();
                    return; // Stop further processing for Hydra projectile
                }
                // --- End Hydra Explosion Logic ---

                // --- RPG Explosion Logic (on Entity) ---
                if (isRPGProjectile) {
                    float explosionRadius = arrow.getPersistentData().getFloat("zombierool:explosion_radius");
                    float explosionDamageMultiplier = arrow.getPersistentData().getFloat("zombierool:explosion_damage_multiplier");
                    float baseWeaponDamageForAoE = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");

                    float explosionDamage = baseWeaponDamageForAoE * explosionDamageMultiplier;
                    // Insta-Kill applied to explosion damage (restored)
                    if (BonusManager.isInstaKillActive(player)) {
                       explosionDamage *= INSTA_KILL_MULTIPLIER;
                    }

                    // Manually spawn explosion particles and play sound
                    if (arrow.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z, 1, 0, 0, 0, 0);
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z, 1, 0, 0, 0, 0);
                    }
                    arrow.level().playSound(null, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.0f);


                    // Deal AoE damage to other entities
                    AABB aoeBox = new AABB(
                        ehr.getLocation().x - explosionRadius,
                        ehr.getLocation().y - explosionRadius,
                        ehr.getLocation().z - explosionRadius,
                        ehr.getLocation().x + explosionRadius,
                        ehr.getLocation().y + explosionRadius,
                        ehr.getLocation().z + explosionRadius
                    );
                    List<LivingEntity> affectedEntities = arrow.level().getEntitiesOfClass(LivingEntity.class, aoeBox);
                    for (LivingEntity affected : affectedEntities) {
                        // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                        boolean isAffectedCustomMob = affected instanceof ZombieEntity
                                                  || affected instanceof CrawlerEntity
                                                  || affected instanceof MannequinEntity
                                                  || affected instanceof HellhoundEntity;
                        if (isAffectedCustomMob) {
                            float currentAoeDamage = explosionDamage;
                            DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                            affected.hurt(aoeSrc, currentAoeDamage);
                            if (player instanceof ServerPlayer sp) {
                                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new DisplayHitmarkerPacket());
                            }
                            // Attribution des points pour les dégâts de zone du RPG
                            PointManager.modifyScore(player, 10);
                        } else if (affected == player && !hasPHDFlopper) {
                            // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                            float selfDamage = player.getMaxHealth() * 0.30f;
                            player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                            System.out.println("DEBUG: Player took self-damage (RPG/Entity)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                        }
                        // Important : Les autres joueurs (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                    }

                    arrow.discard();
                    return; // Stop further processing for RPG projectile
                }
                // --- End RPG Explosion Logic ---

                // Placeholder for China Lake Explosion Logic (on Entity)
                if (isChinaLakeProjectile) {
                    float explosionRadius = arrow.getPersistentData().getFloat("zombierool:explosion_radius");
                    float explosionDamageMultiplier = arrow.getPersistentData().getFloat("zombierool:explosion_damage_multiplier");
                    float baseWeaponDamageForAoE = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");

                    float explosionDamage = baseWeaponDamageForAoE * explosionDamageMultiplier;
                    // Insta-Kill applied to explosion damage (restored)
                    if (BonusManager.isInstaKillActive(player)) {
                       explosionDamage *= INSTA_KILL_MULTIPLIER;
                    }

                    // Manually spawn explosion particles and play sound
                    if (arrow.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z, 1, 0, 0, 0, 0);
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z, 1, 0, 0, 0, 0);
                    }
                    arrow.level().playSound(null, ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5f, 1.0f); // Slightly weaker sound than RPG


                    AABB aoeBox = new AABB(
                        ehr.getLocation().x - explosionRadius,
                        ehr.getLocation().y - explosionRadius,
                        ehr.getLocation().z - explosionRadius,
                        ehr.getLocation().x + explosionRadius,
                        ehr.getLocation().y + explosionRadius,
                        ehr.getLocation().z + explosionRadius
                    );
                    List<LivingEntity> affectedEntities = arrow.level().getEntitiesOfClass(LivingEntity.class, aoeBox);
                    for (LivingEntity affected : affectedEntities) {
                        // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                        boolean isAffectedCustomMob = affected instanceof ZombieEntity
                                                  || affected instanceof CrawlerEntity
                                                  || affected instanceof MannequinEntity
                                                  || affected instanceof HellhoundEntity;
                        if (isAffectedCustomMob) {
                            float currentAoeDamage = explosionDamage;
                            DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                            affected.hurt(aoeSrc, currentAoeDamage);
                            if (player instanceof ServerPlayer sp) {
                                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new DisplayHitmarkerPacket());
                            }
                            // Attribution des points pour les dégâts de zone du China Lake
                            PointManager.modifyScore(player, 10);
                        } else if (affected == player && !hasPHDFlopper) {
                            // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                            float selfDamage = player.getMaxHealth() * 0.30f;
                            player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                            System.out.println("DEBUG: Player took self-damage (ChinaLake/Entity)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                        }
                        // Important : Les autres autres (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                    }

                    arrow.discard();
                    return;
                }

                // Standard damage application for non-Needler, non-Hydra, non-RPG, non-Percepteur projectiles
                // Appliquer les dégâts directs uniquement aux monstres.
                if (isOurCustomMob) {
                    target.hurt(src, finalDamage);

                    // Si c'est un impact de balle générique (non-laser, non-explosif, non-Needler, non-Percepteur)
                    if (!isNeedlerProjectile && !isHydraProjectile && !isRPGProjectile && !isPercepteurProjectile && !isLaserProjectile && arrow.level() instanceof ServerLevel serverLevel) {
                        // Particules d'impact de sang au milieu de l'entité
                        serverLevel.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.0F),
                            target.getX(), target.getY() + target.getBbHeight() / 2.0D, target.getZ(), // Ajustement pour le milieu de l'entité
                            10, 0.2, 0.2, 0.2, 0.1);
                    }
                }
                // --- MLG Easter Egg Logic ---
                if (isIntervention && headshot) {
                    if (RANDOM.nextInt(MLG_EASTER_EGG_CHANCE) == 0) { // 1 in 100 chance
                        SoundEvent mlgSound = ZombieroolModSounds.MLG.get();
                        if (mlgSound != null) {
                            arrow.level().playSound(
                                null,
                                ehr.getLocation().x, ehr.getLocation().y, ehr.getLocation().z,
                                mlgSound,
                                SoundSource.PLAYERS,
                                MLG_SOUND_VOLUME,
                                1.0F + (RANDOM.nextFloat() * MLG_SOUND_PITCH_VARIATION * 2 - MLG_SOUND_PITCH_VARIATION)
                            );
                        } else {
                            System.err.println("[ArrowImpactHandler] MLG Easter Egg: Sound 'mlg' not found.");
                        }
                    }
                }
                // --- End MLG Easter Egg Logic ---

                // Generic impact sound for non-Needler, non-Hydra, non-RPG, non-Percepteur, non-Laser flesh hits
                // Removed sound for laser projectiles here
                if (!isNeedlerProjectile && !isHydraProjectile && !isRPGProjectile && !isPercepteurProjectile && !isLaserProjectile)
                {
                    arrow.level().playSound(
                        null,
                        target.getX(), target.getY(), target.getZ(),
                        ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:impact_flesh")),
                        SoundSource.NEUTRAL,
                        FLESH_IMPACT_SOUND_VOLUME,
                        FLESH_IMPACT_SOUND_PITCH
                    );
                }
                 // Son spécifique pour le Percepteur (si désiré, sinon utilise le son générique)
                if (isPercepteurProjectile) {
                    arrow.level().playSound(
                        null,
                        target.getX(), target.getY(), target.getZ(),
                        ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:impact_flesh")), // Ou un son d'impact spécifique au Percepteur
                        SoundSource.NEUTRAL,
                        FLESH_IMPACT_SOUND_VOLUME,
                        FLESH_IMPACT_SOUND_PITCH
                    );
                }


                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new DisplayHitmarkerPacket());
                }

                if (isBarretBullet && targetsToPierce > 0) {
                    hitEntities.add(target.getUUID());
                    long[] newUuidsLong = new long[hitEntities.size() * 2];
                    for (int i = 0; i < hitEntities.size(); i++) {
                        UUID uuid = hitEntities.get(i);
                        newUuidsLong[i * 2] = uuid.getMostSignificantBits();
                        newUuidsLong[i * 2 + 1] = uuid.getLeastSignificantBits();
                    }
                    arrow.getPersistentData().putLongArray("hitEntities", newUuidsLong);
                    arrow.getPersistentData().putInt(BarretWeaponItem.TAG_MULTI_TARGET_PIERCING, targetsToPierce - 1);

                    return; // Don't discard arrow if it's piercing
                }

                // Discard non-piercing projectiles after hitting an entity
                arrow.discard();

                // Re-added AoE logic for PistoletLaserWeaponItem and RaygunMarkiiItem
                if (weaponStack.getItem() instanceof RaygunWeaponItem || weaponStack.getItem() instanceof RaygunMarkiiItem) {
                    double aoeRadius = LASER_AOE_RADIUS;
                    // Les dégâts de zone du laser utilisent maintenant la totalité des dégâts de l'arme.
                    float aoeDamage = baseWeaponDamage;
                    if (BonusManager.isInstaKillActive(player)) {
                       aoeDamage *= INSTA_KILL_MULTIPLIER;
                    }

                    // No explosion particles or sound here, only AoE damage
                    AABB aabb = new AABB(
                        result.getLocation().x - aoeRadius,
                        result.getLocation().y - aoeRadius,
                        result.getLocation().z - aoeRadius,
                        result.getLocation().x + aoeRadius,
                        result.getLocation().y + aoeRadius,
                        result.getLocation().z + aoeRadius
                    );
                    List<LivingEntity> entitiesInArea = arrow.level().getEntitiesOfClass(LivingEntity.class, aabb);
                    for (LivingEntity entity : entitiesInArea) {
                        boolean isOurCustomMobAoE = entity instanceof ZombieEntity
                                          || entity instanceof CrawlerEntity
                                          || entity instanceof MannequinEntity
                                          || entity instanceof HellhoundEntity;
                        if (isOurCustomMobAoE) {
                            DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                            entity.hurt(aoeSrc, aoeDamage);
                            arrow.level().playSound(
                                null,
                                entity.getX(), entity.getY(), entity.getZ(),
                                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:impact_flesh")),
                                SoundSource.NEUTRAL,
                                FLESH_IMPACT_SOUND_VOLUME,
                                FLESH_IMPACT_SOUND_PITCH
                            );
                            if (player instanceof ServerPlayer serverPlayer) {
                                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new DisplayHitmarkerPacket());
                            }
                            PointManager.modifyScore(player, 10);
                        } else if (entity == player && !hasPHDFlopper) {
                            float selfDamage = player.getMaxHealth() * 0.30f;
                            player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                            System.out.println("DEBUG: Player took self-damage (Laser/Entity)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                        }
                    }
                }
            }
        }
        // --- GESTION DE L'IMPACT SUR UN BLOC ---
        else if (result instanceof BlockHitResult bhr) {
            BlockPos hitPos = bhr.getBlockPos();
            BlockState blockState = arrow.level().getBlockState(hitPos);
            Block block = blockState.getBlock();

            // Needler projectiles should also discard on block hit and not contribute to supercombine on blocks.
            if (isNeedlerProjectile) {
                arrow.discard();
                return;
            }

            // --- Hydra Explosion Logic (on Block) ---
            if (isHydraProjectile) {
                float explosionRadius = arrow.getPersistentData().getFloat("zombierool:explosion_radius");
                float explosionDamageMultiplier = arrow.getPersistentData().getFloat("zombierool:explosion_damage_multiplier");
                float baseWeaponDamageForAoE = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");

                float explosionDamage = baseWeaponDamageForAoE * explosionDamageMultiplier;
                // Insta-Kill applied to explosion damage (restored)
                if (BonusManager.isInstaKillActive(player)) {
                   explosionDamage *= INSTA_KILL_MULTIPLIER;
                }

                // Manually spawn explosion particles and play sound
                if (arrow.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z, 1, 0, 0, 0, 0);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z, 1, 0, 0, 0, 0);
                }
                arrow.level().playSound(null, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.0f);


                // Deal AoE damage to entities in area
                AABB aoeBox = new AABB(
                    bhr.getLocation().x - explosionRadius,
                    bhr.getLocation().y - explosionRadius,
                    bhr.getLocation().z - explosionRadius,
                    bhr.getLocation().x + explosionRadius,
                    bhr.getLocation().y + explosionRadius,
                    bhr.getLocation().z + explosionRadius
                );
                List<LivingEntity> affectedEntities = arrow.level().getEntitiesOfClass(LivingEntity.class, aoeBox);

                for (LivingEntity affected : affectedEntities) {
                    // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                    boolean isAffectedCustomMob = affected instanceof ZombieEntity
                                              ||
                    affected instanceof CrawlerEntity
                                              ||
                    affected instanceof MannequinEntity
                                              ||
                    affected instanceof HellhoundEntity;
                    if (isAffectedCustomMob) {
                        float currentAoeDamage = explosionDamage;
                        DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                        affected.hurt(aoeSrc, currentAoeDamage);
                        if (player instanceof ServerPlayer sp) {
                            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new DisplayHitmarkerPacket());
                        }
                        // Attribution des points pour les dégâts de zone de l'Hydra (sur bloc)
                        PointManager.modifyScore(player, 10);
                    } else if (affected == player && !hasPHDFlopper) {
                        // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                        float selfDamage = player.getMaxHealth() * 0.30f;
                        player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                        System.out.println("DEBUG: Player took self-damage (Hydra/Block)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                    }
                    // Important : Les autres autres (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                }

                arrow.discard();
                return; // Stop further processing for Hydra projectile
            }
            // --- End Hydra Explosion Logic ---

            // --- RPG Explosion Logic (on Block) ---
            if (isRPGProjectile) {
                float explosionRadius = arrow.getPersistentData().getFloat("zombierool:explosion_radius");
                float explosionDamageMultiplier = arrow.getPersistentData().getFloat("zombierool:explosion_damage_multiplier");
                float baseWeaponDamageForAoE = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");

                float explosionDamage = baseWeaponDamageForAoE * explosionDamageMultiplier;
                // Insta-Kill applied to explosion damage (restored)
                if (BonusManager.isInstaKillActive(player)) {
                   explosionDamage *= INSTA_KILL_MULTIPLIER;
                }

                // Manually spawn explosion particles and play sound
                if (arrow.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z, 1, 0, 0, 0, 0);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z, 1, 0, 0, 0, 0);
                }
                arrow.level().playSound(null, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.0f);


                // Deal AoE damage to entities in area
                AABB aoeBox = new AABB(
                    bhr.getLocation().x - explosionRadius,
                    bhr.getLocation().y - explosionRadius,
                    bhr.getLocation().z - explosionRadius,
                    bhr.getLocation().x + explosionRadius,
                    bhr.getLocation().y + explosionRadius,
                    bhr.getLocation().z + explosionRadius
                );
                List<LivingEntity> affectedEntities = arrow.level().getEntitiesOfClass(LivingEntity.class, aoeBox);

                for (LivingEntity affected : affectedEntities) {
                    // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                    boolean isAffectedCustomMob = affected instanceof ZombieEntity
                                              || affected instanceof CrawlerEntity
                                              || affected instanceof MannequinEntity
                                              || affected instanceof HellhoundEntity;
                    if (isAffectedCustomMob) {
                        float currentAoeDamage = explosionDamage;
                        DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                        affected.hurt(aoeSrc, currentAoeDamage);
                        if (player instanceof ServerPlayer sp) {
                            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new DisplayHitmarkerPacket());
                        }
                        // Attribution des points pour les dégâts de zone du RPG (sur bloc)
                        PointManager.modifyScore(player, 10);
                    } else if (affected == player && !hasPHDFlopper) {
                        // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                        float selfDamage = player.getMaxHealth() * 0.30f;
                        player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                        System.out.println("DEBUG: Player took self-damage (RPG/Block)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                    }
                    // Important : Les autres autres (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                }

                arrow.discard();
                return; // Stop further processing for RPG projectile
            }
            // --- End RPG Explosion Logic ---
            if (isChinaLakeProjectile) {
                float explosionRadius = arrow.getPersistentData().getFloat("zombierool:explosion_radius");
                float explosionDamageMultiplier = arrow.getPersistentData().getFloat("zombierool:explosion_damage_multiplier");
                float baseWeaponDamageForAoE = arrow.getPersistentData().getFloat("zombierool:weapon_damage_base");

                float explosionDamage = baseWeaponDamageForAoE * explosionDamageMultiplier;
                // Insta-Kill applied to explosion damage (restored)
                if (BonusManager.isInstaKillActive(player)) {
                   explosionDamage *= INSTA_KILL_MULTIPLIER;
                }

                // Manually spawn explosion particles and play sound
                if (arrow.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z, 1, 0, 0, 0, 0);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z, 1, 0, 0, 0, 0);
                }
                arrow.level().playSound(null, bhr.getLocation().x, bhr.getLocation().y, bhr.getLocation().z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5f, 1.0f); // Slightly weaker sound than RPG


                AABB aoeBox = new AABB(
                    bhr.getLocation().x - explosionRadius,
                    bhr.getLocation().y - explosionRadius,
                    bhr.getLocation().z - explosionRadius,
                    bhr.getLocation().x + explosionRadius,
                    bhr.getLocation().y + explosionRadius,
                    bhr.getLocation().z + explosionRadius
                );
                List<LivingEntity> affectedEntities = arrow.level().getEntitiesOfClass(LivingEntity.class, aoeBox);

                for (LivingEntity affected : affectedEntities) {
                    // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                    boolean isAffectedCustomMob = affected instanceof ZombieEntity
                                              || affected instanceof CrawlerEntity
                                              || affected instanceof MannequinEntity
                                              || affected instanceof HellhoundEntity;
                    if (isAffectedCustomMob) {
                        float currentAoeDamage = explosionDamage;
                        DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                        affected.hurt(aoeSrc, currentAoeDamage);
                        if (player instanceof ServerPlayer sp) {
                            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new DisplayHitmarkerPacket());
                        }
                        // Attribution des points pour les dégâts de zone du China Lake (sur bloc)
                        PointManager.modifyScore(player, 10);
                    } else if (affected == player && !hasPHDFlopper) {
                        // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                        float selfDamage = player.getMaxHealth() * 0.30f;
                        player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                        System.out.println("DEBUG: Player took self-damage (ChinaLake/Block)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                    }
                    // Important : Les autres autres (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                }

                arrow.discard();
                return;
            }

            if (isMustangAndSally) {
                double explosionRadius = MUSTANG_SALLY_EXPLOSION_RADIUS;
                // Manually spawn explosion particles and play sound
                if (arrow.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
                }
                arrow.level().playSound(null, arrow.getX(), arrow.getY(), arrow.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 1.0f);

                float mustangSallyAoEDamage = MUSTANG_SALLY_AOE_DAMAGE;
                // Insta-Kill applied to AOE damage (restored)
                if (BonusManager.isInstaKillActive(player)) {
                   mustangSallyAoEDamage *= INSTA_KILL_MULTIPLIER;
                }

                AABB aabb = new AABB(
                    result.getLocation().x - explosionRadius,
                    result.getLocation().y - explosionRadius,
                    result.getLocation().z - explosionRadius,
                    result.getLocation().x + explosionRadius,
                    result.getLocation().y + explosionRadius,
                    result.getLocation().z + explosionRadius
                );
                List<LivingEntity> entitiesInAoE = arrow.level().getEntitiesOfClass(LivingEntity.class, aabb);

                for (LivingEntity entity : entitiesInAoE) {
                    // Appliquer les dégâts uniquement aux monstres ou au propriétaire (si pas de PHD Flopper)
                    boolean isOurCustomMobAoE = entity instanceof ZombieEntity
                                          ||
                    entity instanceof CrawlerEntity
                                          ||
                    entity instanceof MannequinEntity
                                          ||
                    entity instanceof HellhoundEntity;
                    if (isOurCustomMobAoE) {
                        float currentAoeDamage = mustangSallyAoEDamage;
                        DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                        entity.hurt(aoeSrc, currentAoeDamage);

                        arrow.level().playSound(
                            null,
                            entity.getX(), entity.getY(), entity.getZ(),
                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:impact_flesh")),
                            SoundSource.NEUTRAL,
                            FLESH_IMPACT_SOUND_VOLUME,
                            FLESH_IMPACT_SOUND_PITCH
                        );
                        if (player instanceof ServerPlayer serverPlayer) {
                            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new DisplayHitmarkerPacket());
                        }
                        // Attribution des points pour les dégâts de zone du Mustang & Sally (sur bloc)
                        PointManager.modifyScore(player, 10);
                    } else if (entity == player && !hasPHDFlopper) {
                        // AUTO-DÉGÂTS : le joueur propriétaire subit 30% de ses PV max si PHD Flopper n'est PAS actif.
                        float selfDamage = player.getMaxHealth() * 0.30f;
                        player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                        System.out.println("DEBUG: Player took self-damage (MustangSally)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                    }
                    // Important : Les autres autres (ni mobs, ni le propriétaire) ne subissent PAS de dégâts ici.
                }
            }
            // Re-added AoE logic for PistoletLaserWeaponItem and RaygunMarkiiItem when hitting blocks
            else if (weaponStack.getItem() instanceof RaygunWeaponItem || weaponStack.getItem() instanceof RaygunMarkiiItem) {
                double aoeRadius = LASER_AOE_RADIUS;
                // Les dégâts de zone du laser utilisent maintenant la totalité des dégâts de l'arme.
                float aoeDamage = baseWeaponDamage;
                if (BonusManager.isInstaKillActive(player)) {
                   aoeDamage *= INSTA_KILL_MULTIPLIER;
                }

                // No explosion particles or sound here, only AoE damage
                AABB aabb = new AABB(
                    result.getLocation().x - aoeRadius,
                    result.getLocation().y - aoeRadius,
                    result.getLocation().z - aoeRadius,
                    result.getLocation().x + aoeRadius,
                    result.getLocation().y + aoeRadius,
                    result.getLocation().z + aoeRadius
                );
                List<LivingEntity> entitiesInArea = arrow.level().getEntitiesOfClass(LivingEntity.class, aabb);
                for (LivingEntity entity : entitiesInArea) {
                    boolean isOurCustomMobAoE = entity instanceof ZombieEntity
                                          || entity instanceof CrawlerEntity
                                          || entity instanceof MannequinEntity
                                          || entity instanceof HellhoundEntity;
                    if (isOurCustomMobAoE) {
                        DamageSource aoeSrc = arrow.level().damageSources().indirectMagic(arrow, player);
                        entity.hurt(aoeSrc, aoeDamage);
                        arrow.level().playSound(
                            null,
                            entity.getX(), entity.getY(), entity.getZ(),
                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:impact_flesh")),
                            SoundSource.NEUTRAL,
                            FLESH_IMPACT_SOUND_VOLUME,
                            FLESH_IMPACT_SOUND_PITCH
                        );
                        if (player instanceof ServerPlayer serverPlayer) {
                            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new DisplayHitmarkerPacket());
                        }
                        PointManager.modifyScore(player, 10);
                    } else if (entity == player && !hasPHDFlopper) {
                        float selfDamage = player.getMaxHealth() * 0.30f;
                        player.hurt(arrow.level().damageSources().indirectMagic(arrow, player), selfDamage);
                        System.out.println("DEBUG: Player took self-damage (Laser/Block)! Amount: " + selfDamage + " Max Health: " + player.getMaxHealth());
                    }
                }
            }

            // Gérer l'interaction des projectiles non explosifs avec les boutons et plaques de pression en bois
            // Le Percepteur et les projectiles laser (Pistolet Laser, Raygun Mark II) ne sont pas explosifs
            boolean isExplosiveProjectile = isHydraProjectile || isRPGProjectile || isChinaLakeProjectile || isMustangAndSally;

            if (!isExplosiveProjectile) {
                if (block instanceof ButtonBlock buttonBlock) { // Use pattern matching for direct cast
                    // Directly call the block's projectile hit method. This ensures all default behaviors
                    // (state change, sound, redstone update, timing) are handled by the block itself.
                    // This is usually the most robust way to interact with blocks via projectiles.
                    buttonBlock.onProjectileHit(arrow.level(), blockState, bhr, arrow);
                } else if (block instanceof PressurePlateBlock pressurePlateBlock) { // Use pattern matching
                    // Pressure plates don't have a direct onProjectileHit method to activate them like buttons do.
                    // They usually rely on entity presence or specific redstone updates.
                    // Our current approach is to directly set the 'powered' state. This should work for basic activation.
                    if (blockState.hasProperty(PressurePlateBlock.POWERED)) {
                        BooleanProperty poweredProperty = PressurePlateBlock.POWERED;
                        if (!blockState.getValue(poweredProperty)) { // If it's not already activated
                            BlockState newState = blockState.setValue(poweredProperty, true);
                            arrow.level().setBlock(hitPos, newState, 3);
                            arrow.level().scheduleTick(hitPos, block, 20); // Stay activated for 1 second (20 ticks)
                            arrow.level().updateNeighborsAt(hitPos, block); // Notify neighbors
                            arrow.level().playSound(null, hitPos, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.3F, 0.6F); // Click sound
                        }
                    }
                }
                // Vous pouvez ajouter d'autres interactions de blocs ici si nécessaire
            }
            arrow.discard();
        } else {
            arrow.discard();
        }
    }
}
