package net.mcreator.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.mcreator.zombierool.init.ZombieroolModEntities;
import net.minecraft.world.effect.MobEffects;
import org.joml.Vector3f;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.mcreator.zombierool.WaveManager;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.mcreator.zombierool.bonuses.BonusManager;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;

// Imports for DataWatcher
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

// Imports pour les sons
import net.mcreator.zombierool.init.ZombieroolModSounds; // Assure-toi que ce chemin est correct
import java.util.Random; // Make sure Random is imported

public class ZombieEntity extends Monster {

    public static final EntityType<ZombieEntity> TYPE = ZombieroolModEntities.ZOMBIE.get();
    private boolean headshotDeath = false;
    private int headshotDeathTicks = 0;
    private int ambientSoundCooldown = 0;
    private double baseSpeed = 0.18; // Valeur par défaut
    private DamageSource headshotSource;
    private boolean hasTriggeredHeadshotKill = false;
    private static final Random STATIC_RANDOM = new Random(); // Static Random for general use

    // NOUVEAU : DataAccessor pour synchroniser l'état de super sprinter
    private static final EntityDataAccessor<Boolean> IS_SUPER_SPRINTER = SynchedEntityData.defineId(ZombieEntity.class, EntityDataSerializers.BOOLEAN);

    // --- Variables pour le son "behind_you" ---
    // Un cooldown par zombie pour éviter le spam d'un zombie en particulier
    private int behindYouSoundCooldown = 0;
    // Cooldown global pour le son "behind_you" pour tous les zombies, stocké statiquement
    // Permet d'éviter que tous les zombies déclenchent le son en même temps
    private static int globalBehindYouSoundCooldown = 0;
    private static final int BEHIND_YOU_MIN_COOLDOWN_TICKS = 20 * 5; // Minimum 5 secondes entre les sons (pour éviter le spam abusif)
    private static final int BEHIND_YOU_MAX_COOLDOWN_TICKS = 20 * 15; // Max 15 secondes pour le cooldown aléatoire
    private static final double BEHIND_YOU_CHECK_RADIUS = 8.0; // Rayon de vérification réduit
    private static final float BEHIND_YOU_CHANCE = 0.005f; // 0.5% de chance par tick (pour le son aléatoire)

    // --- NOUVELLES VARIABLES pour le son "behind_you" instantané ---
    private static final double INSTANT_BEHIND_YOU_DISTANCE = 1.5; // Distance max réduite pour le déclenchement instantané
    private static final double INSTANT_BEHIND_YOU_DOT_PRODUCT_THRESHOLD = -0.7; // Seuil pour "derrière soi" (134 degrés)
    // --- Fin variables pour le son "behind_you" ---


    public ZombieEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.ZOMBIE.get(), world);
        // La vitesse de base sera synchronisée via applySpeedScaling si c'est un super sprinter
        // Ou via les attributs si ce n'est pas un super sprinter.
    }

    public ZombieEntity(EntityType<ZombieEntity> type, Level world) {
        super(type, world);
        setMaxUpStep(0.6f);
        xpReward = 0;
        setNoAi(false);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        // Définir la valeur initiale pour IS_SUPER_SPRINTER
        this.entityData.define(IS_SUPER_SPRINTER, false);
    }

    // Getter pour l'état de super sprinter
    public boolean isSuperSprinter() {
        return this.entityData.get(IS_SUPER_SPRINTER);
    }

    // Setter pour l'état de super sprinter (utilisé par WaveManager)
    public void setSuperSprinter(boolean value) {
        this.entityData.set(IS_SUPER_SPRINTER, value);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
        return size.height * 0.92F;
    }

    public void setBaseSpeed(double speed) {
        this.baseSpeed = speed;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            WaveManager.onMobDeath(this, serverLevel);

            // Award points to player
            Entity direct = source.getDirectEntity();
            Entity src = source.getEntity();
            Player player = null; // Initialize player as null
            int pts = 50;

            // Variables pour vérifier si la mort est causée par la dernière balle du Whisper
            boolean killedByWhisperLastBullet = false;

            if (src instanceof Player p1) {
                player = p1;
                if (headshotDeath) pts += 50;
                // Vérifie si le projectile a le tag "zombierool:last_whisper_bullet"
                if (direct instanceof net.minecraft.world.entity.projectile.AbstractArrow proj && proj.getOwner() == player) {
                    if (proj.getPersistentData().getBoolean("zombierool:last_whisper_bullet")) {
                        killedByWhisperLastBullet = true;
                    }
                }
            } else if (direct instanceof Projectile proj && proj.getOwner() instanceof Player p2) {
                player = p2;
                if (headshotDeath) pts += 50;
                // Vérifie si la source directe est une balle et si c'est la dernière balle du Whisper
                if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
                    if (proj.getPersistentData().getBoolean("zombierool:last_whisper_bullet")) {
                        killedByWhisperLastBullet = true;
                    }
                }
            }

            if (player != null) {
                net.mcreator.zombierool.PointManager.modifyScore(player, pts);
                //player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a+ " + pts + " points"));

                // 🎁 Special zombie loot
                boolean hasIngot = player.getInventory().items.stream()
                    .anyMatch(st -> st.getItem() instanceof net.mcreator.zombierool.item.IngotSaleItem);
                if (!hasIngot && player.level().random.nextFloat() < 0.0015f) {
                    player.getInventory().add(new net.minecraft.world.item.ItemStack(
                        net.mcreator.zombierool.init.ZombieroolModItems.INGOT_SALE.get()));
                    net.mcreator.zombierool.FireSaleHandler.startFireSale(player);
                }
            }

            // ⭐ LOGIQUE POUR FAIRE APPARAÎTRE UN BONUS ⭐
            // All mobs killed by a player contribute to the bonus chance,
            // but the Vulture perk check needs the player context.
            // It's important to ensure a player is available for getRandomBonus.
            if (player != null) { // Ensure a player killed the mob before checking for bonus logic
                int zombiesKilledSinceLastBonus = WaveManager.getZombiesKilledSinceLastBonus(serverLevel);
                boolean shouldSpawnBonus = false;

                if (zombiesKilledSinceLastBonus >= 150) {
                    shouldSpawnBonus = true;
                    WaveManager.resetZombiesKilledSinceLastBonus(serverLevel); // Reset the counter
                } else if (this.random.nextFloat() < 0.005f) { // 5% chance a bonus will appear
                    shouldSpawnBonus = true;
                }

                if (shouldSpawnBonus) {
                    // IMPORTANT: Pass the 'player' who caused the death to getRandomBonus
                    BonusManager.BonusType randomBonus = BonusManager.BonusType.getRandomBonus(player);
                    if (randomBonus != null) {
                        // Bonus position: slightly above the ground where the zombie died
                        Vec3 bonusPos = new Vec3(this.getX(), this.getY() + 0.5, this.getZ());
                        BonusManager.spawnBonus(randomBonus, serverLevel, bonusPos);
                        //serverLevel.sendSystemMessage(net.minecraft.network.chat.Component.literal("Un bonus de type " + randomBonus.name() + " est apparu !")); // Debug
                    }
                }
            }
            // END BONUS LOGIC

            // --- NOUVELLE LOGIQUE POUR L'EXPLOSION DE PARTICULES NOIRES ET LE SON CROW_WAVE ---
            if (killedByWhisperLastBullet) {
                // Spawn des particules noires (fumée et encre de calamar)
                serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(),
                    50,
                    0.3D, 0.5D, 0.3D,
                    0.1D
                );
                serverLevel.sendParticles(
                    ParticleTypes.SQUID_INK,
                    this.getX(),
                    this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(),
                    30,
                    0.2D, 0.4D, 0.2D,
                    0.05D
                );
                 // Joue le son "crow_wave"
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                       ZombieroolModSounds.CROW_WAVE.get(),
                                       net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.8f + this.random.nextFloat() * 0.4f);
            }
            // --- FIN DE LA NOUVELLE LOGIQUE ---
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, Player.class, false, true));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            protected double getAttackReachSqr(LivingEntity entity) {
                return this.mob.getBbWidth() * this.mob.getBbWidth() + entity.getBbWidth();
            }
        });
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(3, new BreakDoorGoal(this, enumDifficulty -> true) {
            @Override
            public boolean canUse() {
                return super.canUse() && this.mob.getTarget() != null;
            }
        });
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public double getMyRidingOffset() {
        return -0.35D;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return null; // Nous gérons les sons d'ambiance via playAmbientBasedOnContext()
    }

    private void playAmbientBasedOnContext() {
        // Ajout d'une vérification pour ne pas jouer le son si le zombie est trop loin des joueurs
        // Permet de réduire le spam sonore dans des zones vides
        if (this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 32.0, false) == null) {
            return; // Pas de joueur à proximité, ne joue pas de son d'ambiance
        }

        double currentSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED).getValue();

        SoundEvent sound = null;
        if (currentSpeed > 0.22) { // Seuil à adapter selon ton design
            int idx = 1 + this.random.nextInt(9); // sprint1–9
            sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "sprint" + idx));
        } else {
            int idx = 1 + this.random.nextInt(10); // ambiant1–10
            sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "ambiant" + idx));
        }

        if (sound != null) {
            this.level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, 1f, 1f);
        }
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean flag = super.doHurtTarget(entity);
        if (flag && !this.level().isClientSide) {
            // Original attack sound logic
            int idx = 1 + this.random.nextInt(16); // 1 à 16
            SoundEvent attackSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "attack" + idx));
            if (attackSound != null) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), attackSound, SoundSource.HOSTILE, 1.0F, 1.0F);
            }

            // --- NEW EASTER EGG: Play "punch" sound if the target is a player ---
            if (entity instanceof Player) {
                // 1 in 20 chance for the "punch" sound to play (5% chance)
                if (STATIC_RANDOM.nextInt(20) == 0) { // <--- Changed from 4 to 20
                    SoundEvent punchSound = ZombieroolModSounds.PUNCH.get(); // Get your custom "punch" sound
                    if (punchSound != null) {
                        this.level().playSound(
                            null,
                            entity.getX(), entity.getY(), entity.getZ(), // Play at the player's location
                            punchSound,
                            SoundSource.PLAYERS, // Play through the player's sound channel
                            1.0F,
                            1.0F + (STATIC_RANDOM.nextFloat() * 0.2F - 0.1F) // Slight pitch variation
                        );
                    } else {
                        System.err.println("[ZombieEntity] Easter Egg: Sound 'punch' not found.");
                    }
                }
            }
            // --- END NEW EASTER EGG ---
        }
        return flag;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource source) {
        int idx = 1 + this.random.nextInt(2); // 1 ou 2
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "hurt" + idx));
    }

    @Override
    public SoundEvent getDeathSound() {
        int idx = 1 + this.random.nextInt(10); // 1 à 10
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "death" + idx));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // --- NOUVELLE LOGIQUE : Particules de fumée grise quand le zombie est touché ---
        if (!this.level().isClientSide) {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(),
                    5,
                    0.1D, 0.1D, 0.1D,
                    0.01D
                );
            }
        }

        if (source.is(DamageTypes.FALL) || source.is(DamageTypes.DROWN))
            return false;

        /*
        if (source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.HOT_FLOOR)) {
            return false;
        }
        */

        if (source.getDirectEntity() instanceof Player player && BonusManager.isInstaKillActive(player)) {
            return super.hurt(source, Float.MAX_VALUE);
        }

        boolean headshot = false;
        if (source.getDirectEntity() instanceof Projectile p) {
            headshot = p.getY() >= this.getY() + this.getBbHeight() * 0.85;
        } else if (source.getSourcePosition() != null) {
            headshot = source.getSourcePosition().y >= this.getY() + this.getBbHeight() * 0.85;
        }

        if (headshot) {
            boolean lethal = amount >= this.getHealth();
            if (lethal && !this.headshotDeath) {
                this.headshotSource = source;
                this.headshotDeath = true;
                this.headshotDeathTicks = 0;
                this.hasTriggeredHeadshotKill = false;

                if (!this.level().isClientSide) {
                    this.level().broadcastEntityEvent(this, (byte) 99);
                }
            }
        }

        return super.hurt(source, amount);
    }


    @Override
    public void handleEntityEvent(byte id) {
        super.handleEntityEvent(id);
        if (id == 99 && this.level().isClientSide) {
            this.setHeadshotDeath(true);
            double x = this.getX(), y = this.getY() + this.getBbHeight() * 0.9, z = this.getZ();
            for (int i = 0; i < 30; i++) {
                double dx = (this.random.nextDouble() - 0.5) * this.getBbWidth();
                double dy = this.random.nextDouble() * 0.5;
                double dz = (this.random.nextDouble() - 0.5) * this.getBbWidth();
                this.level().addParticle(
                    new DustParticleOptions(new Vector3f(1f, 0f, 0f), 1f),
                    x + dx, y + dy, z + dz,
                    dx * 0.2, dy * 0.2, dz * 0.2
                );
            }
            int idx = 1 + this.random.nextInt(3);
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation("zombierool", "death_beurk" + idx)
            );
            if (sound != null) {
                this.level().playLocalSound(x, y, z, sound, SoundSource.HOSTILE, 1f, 1f, false);
            }
        }
    }

    public void setHeadshotDeath(boolean value) {
        this.headshotDeath = value;
        if (value) {
            this.headshotDeathTicks = 0;
        }
    }

    public boolean isHeadshotDeath() {
        return this.headshotDeath;
    }


    public static void init() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.18);
        builder = builder.add(Attributes.MAX_HEALTH, 4);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 2);
        builder = builder.add(Attributes.FOLLOW_RANGE, 124);
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }

    @Override
    public void tick() {
        super.tick();

        // ---------- Logique de headshot ----------
        if (this.headshotDeath && !hasTriggeredHeadshotKill) {
            headshotDeathTicks++;
            if (headshotDeathTicks >= 5 && !this.level().isClientSide) {
                hasTriggeredHeadshotKill = true;
                DamageSource ds = headshotSource != null ? headshotSource : this.damageSources().generic();
                // Peut-être infliger les dégâts ici ?
            }
        }

        // ---------- Logique serveur uniquement ----------
        if (!this.level().isClientSide) {

            // --- Sons ambiants zombies ---
            if (ambientSoundCooldown > 0) {
                ambientSoundCooldown--;
            } else {
                playAmbientBasedOnContext();
                ambientSoundCooldown = 60 + this.random.nextInt(130);
            }

            // --- Cooldown pour sons "behind_you" ---
            if (globalBehindYouSoundCooldown > 0) globalBehindYouSoundCooldown--;
            if (this.behindYouSoundCooldown > 0) {
                this.behindYouSoundCooldown--;
            } else if (globalBehindYouSoundCooldown == 0) {
                List<Player> players = this.level().getEntitiesOfClass(
                    Player.class,
                    this.getBoundingBox().inflate(BEHIND_YOU_CHECK_RADIUS)
                );

                for (Player player : players) {
                    if (!player.isAlive() || player.isSpectator()) continue;
                    if (player.hasEffect(MobEffects.BLINDNESS)) continue;

                    Vec3 playerLook = player.getLookAngle().normalize();
                    Vec3 toZombie = this.position().subtract(player.position()).normalize();
                    double dot = playerLook.dot(toZombie);
                    double distSqr = this.distanceToSqr(player);
                    boolean isBehind = dot <= INSTANT_BEHIND_YOU_DOT_PRODUCT_THRESHOLD
                        || (distSqr <= INSTANT_BEHIND_YOU_DISTANCE * INSTANT_BEHIND_YOU_DISTANCE && dot <= INSTANT_BEHIND_YOU_DOT_PRODUCT_THRESHOLD); // Check both instant and random conditions

                    if (isBehind && this.level().random.nextFloat() < BEHIND_YOU_CHANCE) {

                        player.level().playSound(
                            null,
                            player.getX(), player.getY(), player.getZ(),
                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "behind_you")),
                            SoundSource.HOSTILE,
                            1.0F, 1.0F
                        );

                        int cooldown = this.random.nextInt(
                            BEHIND_YOU_MAX_COOLDOWN_TICKS - BEHIND_YOU_MIN_COOLDOWN_TICKS
                        ) + BEHIND_YOU_MIN_COOLDOWN_TICKS;

                        this.behindYouSoundCooldown = cooldown;
                        globalBehindYouSoundCooldown = cooldown;
                        break;
                    }
                }
            }

            // --- Pathfinding dynamique ---
            if (this.getTarget() instanceof Player) {
                double dist = this.distanceTo(this.getTarget());
                if (dist < 10.0 && this.isPathFinding()) {
                    if (this.navigation.isStuck() || this.tickCount % 20 == 0) {
                        this.navigation.recomputePath();
                    }
                }
            }

            // --- Poussée anti-coincement (proche de portes ouvertes) ---
            List<ZombieEntity> nearbyZombies = this.level().getEntitiesOfClass(
                ZombieEntity.class,
                this.getBoundingBox().inflate(0.3, 0.1, 0.3)
            );

            for (ZombieEntity other : nearbyZombies) {
                if (other == this) continue;

                double distSqr = this.distanceToSqr(other);
                if (distSqr < 0.25) {
                    Vec3 direction = this.position().subtract(other.position()).normalize();
                    float push = 0.025f;

                    // Poussée douce dans la direction opposée
                    other.push(direction.x * push, 0, direction.z * push);
                    this.push(-direction.x * push, 0, -direction.z * push);

                    // Ajustement latéral pour éviter blocage en ligne (utile dans encadrement de porte)
                    if (Math.abs(direction.x) < 0.1) {
                        this.push((this.random.nextBoolean() ? 1 : -1) * 0.02, 0, 0);
                    }
                    if (Math.abs(direction.z) < 0.1) {
                        this.push(0, 0, (this.random.nextBoolean() ? 1 : -1) * 0.02);
                    }
                }
            }
        }
    }

}