package net.mcreator.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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
import net.minecraft.core.BlockPos;
import net.mcreator.zombierool.ExplosionControl;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.mcreator.zombierool.WaveManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import net.mcreator.zombierool.bonuses.BonusManager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3; // NOUVEAU IMPORT pour Vec3

import net.mcreator.zombierool.init.ZombieroolModEntities;
import net.mcreator.zombierool.init.ZombieroolModSounds; // NOUVEAU IMPORT pour le son crow_wave

public class HellhoundEntity extends Monster {

    private int breathSoundCooldown = 0;
    private int spawnTimer = 0;
    private boolean hasPlayedPreSpawn = false;
    private boolean isFireVariant = false;
    //private boolean isRevealed = false;
    private boolean spawnInitialized = false;
    public static final EntityType<HellhoundEntity> TYPE = ZombieroolModEntities.HELLHOUND.get();
    private static final EntityDataAccessor<Boolean> REVEALED = SynchedEntityData.defineId(
        HellhoundEntity.class, EntityDataSerializers.BOOLEAN
    );
    
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(REVEALED, false);
    }
    
    public boolean isRevealedClient() {
        return this.entityData.get(REVEALED);
    }
    
    public HellhoundEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.HELLHOUND.get(), world);
    }

    public HellhoundEntity(EntityType<HellhoundEntity> type, Level world) {
        super(type, world);
        setMaxUpStep(1.0f);
        xpReward = 0;
        setNoAi(false);
        setPersistenceRequired();
        
    }

    public void setFireVariant(boolean fire) {
        this.isFireVariant = fire;
        if (fire) this.setSecondsOnFire(15);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(1.5F, 1.2F);
    }
    
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
        return 0.85F * size.height;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(2, new FloatGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Player.class, false, true));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            protected double getAttackReachSqr(LivingEntity entity) {
                return this.mob.getBbWidth() * this.mob.getBbWidth() + entity.getBbWidth();
            }
        });
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_living"));
    }

    @Override
    public void playStepSound(BlockPos pos, BlockState blockIn) {
    }

    @Override
    public SoundEvent getDeathSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_mort"));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean flag = super.doHurtTarget(target);
        if (flag) {
            this.playSound(ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_bite")), 1.0f, 1.0f);
        }
        return flag;
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        // --- NOUVELLE LOGIQUE : Particules de fumée grise quand le Hellhound est touché ---
        if (!this.level().isClientSide) { // Assurez-vous que c'est côté serveur pour envoyer les particules aux clients
            if (this.level() instanceof ServerLevel serverLevel) {
                // Spawn des particules de fumée grises (petites) à l'endroit de l'impact ou au centre de l'entité
                serverLevel.sendParticles(
                    ParticleTypes.SMOKE, // Particules de fumée standard (grises par défaut)
                    this.getX(),        // X de la position
                    this.getY() + this.getBbHeight() / 2.0D, // Y (au centre de l'entité)
                    this.getZ(),        // Z de la position
                    5,                 // Nombre de particules (très peu pour un petit impact)
                    0.1D, 0.1D, 0.1D,   // Très petit décalage aléatoire pour la dispersion
                    0.01D                // Très petite vitesse des particules
                );
            }
        }
        // --- FIN DE LA NOUVELLE LOGIQUE ---

        // Vérifie si la source des dégâts est un joueur et si l'Insta-Kill est actif
        if (source.getDirectEntity() instanceof Player player && BonusManager.isInstaKillActive(player)) {
            return super.hurt(source, Float.MAX_VALUE); // Tue instantanément
        }
    
        // Ignorer certains types de dégâts
        if (source.is(DamageTypes.IN_FIRE) ||
            source.is(DamageTypes.FALL) ||
            source.is(DamageTypes.DROWN) ||
            source.is(DamageTypes.LIGHTNING_BOLT) ||
            source.is(DamageTypes.EXPLOSION)) {
            return false;
        }
    
        boolean headshot = false;
        double headLevel = this.getY() + this.getEyeHeight();
    
        // Projectile ?
        if (source.getDirectEntity() instanceof Projectile p) {
            double arrowY = p.getY();
            headshot = arrowY >= headLevel - 0.1;
            // System.out.println("[HELLHOUND HEADSHOT] arrowY=" + arrowY + " | headLevel=" + headLevel + " -> " + headshot); // Commenté car c'est du debug
        }
        // Source positionnelle ?
        else if (source.getSourcePosition() != null) {
            double srcY = source.getSourcePosition().y;
            headshot = srcY >= headLevel - 0.1;
            // System.out.println("[HELLHOUND HEADSHOT] srcY=" + srcY + " | headLevel=" + headLevel + " -> " + headshot); // Commenté car c'est du debug
        }
    
        if (headshot) {
            // Double dégâts, plafonnés à 20 points (10 cœurs)
            float rawDmg = amount * 2;
            float cap    = 20f;
            float dmg    = Math.min(rawDmg, cap);
    
            // (Pas d'effet visuel particulier ici, mais vous pouvez en ajouter)
            return super.hurt(source, dmg);
        }
    
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
    
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            WaveManager.onMobDeath(this, serverLevel); 

            // Variables pour vérifier si la mort est causée par la dernière balle du Whisper
            boolean killedByWhisperLastBullet = false;
            Entity directSource = cause.getDirectEntity();

            if (directSource instanceof net.minecraft.world.entity.projectile.AbstractArrow proj) {
                // Vérifie si le projectile a le tag "zombierool:last_whisper_bullet"
                if (proj.getPersistentData().getBoolean("zombierool:last_whisper_bullet")) {
                    killedByWhisperLastBullet = true;
                }
            }

            // --- NOUVELLE LOGIQUE POUR L'EXPLOSION DE PARTICULES NOIRES ET LE SON CROW_WAVE ---
            if (killedByWhisperLastBullet) {
                // Spawn des particules noires (fumée et encre de calamar)
                serverLevel.sendParticles(
                    ParticleTypes.SMOKE, // Type de particule (fumée noire)
                    this.getX(),        // X de la position
                    this.getY() + this.getBbHeight() / 2.0D, // Y (au centre de l'entité)
                    this.getZ(),        // Z de la position
                    50,                 // Nombre de particules
                    0.3D, 0.5D, 0.3D,   // Décalage aléatoire (x, y, z) pour la dispersion
                    0.1D                // Vitesse des particules
                );
                serverLevel.sendParticles(
                    ParticleTypes.SQUID_INK, // Un autre type pour des particules plus "noires"
                    this.getX(),
                    this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(),
                    30,
                    0.2D, 0.4D, 0.2D,
                    0.05D
                );
                 // Joue le son "crow_wave"
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                                       ZombieroolModSounds.CROW_WAVE.get(), // Utilise votre son personnalisé
                                       net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.8f + this.random.nextFloat() * 0.4f);
            } else if (isFireVariant) { // Comportement par défaut si n'est pas la dernière balle du Whisper MAIS est une variante de feu
                new ExplosionControl(level(), this, this.getX(), this.getY(), this.getZ(), 2.5f);
                this.level().playSound(null, this.blockPosition(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_explosion")),
                    this.getSoundSource(), 1.0f, 1.0f);
            }
            // --- FIN DE LA NOUVELLE LOGIQUE ---
        }
    }


    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        return super.finalizeSpawn(world, difficulty, reason, spawnData, dataTag);
    }

    public static void init() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.27);
        builder = builder.add(Attributes.MAX_HEALTH, 6);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 1);
        builder = builder.add(Attributes.FOLLOW_RANGE, 16);
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }

    @Override
    public void tick() {
        super.tick();
    
        // 1) Initialisation unique au premier tick (serveur)
        if (!spawnInitialized && !this.level().isClientSide()) {
            spawnInitialized = true;
            if (this.random.nextInt(4) == 0) {
                isFireVariant = true;
                this.setSecondsOnFire(15);
            }
            this.setInvisible(true);
            this.entityData.set(REVEALED, false);
            spawnTimer = 20;
            this.level().playSound(
                null, this.blockPosition(),
                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_prespawn")),
                this.getSoundSource(), 1.0f, 1.0f
            );
        }
    
        // 2) Gestion du timer & révélation (serveur)
        if (!this.level().isClientSide() && spawnInitialized && !this.entityData.get(REVEALED)) {
            if (spawnTimer-- <= 0) {
                this.setInvisible(false);
                this.entityData.set(REVEALED, true);
                this.level().playSound(
                    null, this.blockPosition(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_strike")),
                    this.getSoundSource(), 1.0f, 1.0f
                );
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 50; i++) {
                        double dx = (this.random.nextDouble() - 0.5) * 1.5;
                        double dy = this.random.nextDouble() * this.getBbHeight();
                        double dz = (this.random.nextDouble() - 0.5) * 1.5;
                        serverLevel.sendParticles(
                            ParticleTypes.FLAME,
                            this.getX() + dx, this.getY() + dy, this.getZ() + dz,
                            1, 0, 0, 0, 0.01
                        );
                        if (this.random.nextDouble() < 0.3) {
                            serverLevel.sendParticles(
                                ParticleTypes.LAVA,
                                this.getX() + dx, this.getY() + dy, this.getZ() + dz,
                                1, 0, 0.05, 0, 0.02
                            );
                        }
                    }
                }
            }
        }
    
        // 3) Maintien du feu (serveur + client)
        if (isFireVariant && this.entityData.get(REVEALED)) {
            this.setSecondsOnFire(15);
            this.setRemainingFireTicks(15);
            this.setSharedFlag(0, true);
        }
    
        // 4) Sons de respiration en boucle (client)
        if (this.level().isClientSide() && this.isAlive()) {
            if (breathSoundCooldown-- <= 0) {
                this.playSound(
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_breath_loop")),
                    1.0f, 1.0f
                );
                if (isFireVariant && this.entityData.get(REVEALED)) {
                    this.playSound(
                        ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_fire_loop")),
                        1.0f, 1.0f
                    );
                }
                breathSoundCooldown = 80;
            }
        }
    }
}