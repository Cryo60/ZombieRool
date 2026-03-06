package me.cryo.zombierool.item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.network.SyncBlueFirePacket;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.WeaponVfxPacket;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

public class FlamethrowerItem extends WeaponSystem.BaseGunItem {
    public static final String TAG_WAS_OVERHEATED = "WasOverheated";
    public static final String TAG_OVERHEAT_LOCKED = "OverheatLocked";

    public FlamethrowerItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override
    public boolean hasOverheat() {
        return true;
    }

    @Override
    public int getMaxOverheat() {
        return 1000;
    }

    @Override
    public int getOverheatPerShot(ItemStack stack) {
        return isPackAPunched(stack) ? 15 : 20;
    }

    @Override
    public int getCooldownPerTick(ItemStack stack) {
        return 10;
    }

    public boolean wasOverheated(ItemStack stack) {
        return getOrCreateTag(stack).getBoolean(TAG_WAS_OVERHEATED);
    }

    public void setWasOverheated(ItemStack stack, boolean wasOverheated) {
        getOrCreateTag(stack).putBoolean(TAG_WAS_OVERHEATED, wasOverheated);
    }

    public boolean isOverheatLocked(ItemStack stack) {
        return getOrCreateTag(stack).getBoolean(TAG_OVERHEAT_LOCKED);
    }

    public void setOverheatLocked(ItemStack stack, boolean locked) {
        getOrCreateTag(stack).putBoolean(TAG_OVERHEAT_LOCKED, locked);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        super.inventoryTick(stack, level, ent, slot, sel); 
        if (!(ent instanceof Player p)) return;

        int currentOverheat = getOverheat(stack);
        boolean wasLocked = isOverheatLocked(stack);

        if (!level.isClientSide) {
            if (currentOverheat >= 990 && !wasLocked) {
                setOverheatLocked(stack, true);
                setWasOverheated(stack, true);
                playSound(level, p, "zombierool:flamethrower_overheat");
                playSound(level, p, "zombierool:flamethrower_off");
            }
            if (wasLocked && currentOverheat <= (getMaxOverheat() / 8)) {
                setOverheatLocked(stack, false);
                setWasOverheated(stack, false);
                playSound(level, p, "zombierool:flamethrower_on");
            }
        }
    }

    @Override
    protected boolean executeShot(ItemStack stack, Player player, float charge, boolean isLeft) {
        if (isOverheatLocked(stack) || getOverheat(stack) >= 990) {
            playSound(player.level(), player, def.sounds.dry);
            return false;
        }

        performShooting(stack, player, charge, isLeft);

        if (!player.isCreative()) {
            long now = player.level().getGameTime();
            long lastFire = getOrCreateTag(stack).getLong(isLeft ? TAG_LAST_FIRE_LEFT : TAG_LAST_FIRE);
            int heatAdd = getOverheatPerShot(stack);
            if (now - lastFire > def.stats.fire_rate * 2) {
                heatAdd *= 2; 
            }
            setOverheat(stack, Math.min(getMaxOverheat(), getOverheat(stack) + heatAdd));
        }

        float pitchRecoil = isPackAPunched(stack) ? def.recoil.pitch * def.pap.recoil_mult : def.recoil.pitch;
        float yawRecoil = isPackAPunched(stack) ? def.recoil.yaw * def.pap.recoil_mult : def.recoil.yaw;
        if (!player.level().isClientSide) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                    new me.cryo.zombierool.network.RecoilPacket(pitchRecoil, (player.getRandom().nextBoolean() ? 1 : -1) * yawRecoil));
        }

        return true;
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        boolean isPap = isPackAPunched(stack);
        float flatDamage = def.stats.damage;
        float percentageDamage = 0.015f;

        if (isPap) {
            flatDamage += def.pap.damage_bonus;
            percentageDamage += 0.008f;
        }

        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 visualStartPos = getVisualMuzzlePos(player);

        Vec3 maxEndPos = eyePos.add(lookVec.scale(7.5));
        net.minecraft.world.phys.HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
            eyePos, maxEndPos, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));

        Vec3 finalEndPos = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? maxEndPos : hit.getLocation();

        AABB flameArea = new AABB(eyePos, finalEndPos).inflate(1.75);

        Vec3 stepVec = lookVec.scale(1.5); 
        Vec3 groundCheckPos = eyePos;

        for (int j = 0; j < 5; j++) {
            groundCheckPos = groundCheckPos.add(stepVec);
            if (groundCheckPos.distanceToSqr(eyePos) > eyePos.distanceToSqr(finalEndPos)) break; 
            net.minecraft.world.phys.BlockHitResult groundHit = player.level().clip(new net.minecraft.world.level.ClipContext(
                groundCheckPos, groundCheckPos.subtract(0, 4.0, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, 
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
            ));
            if (groundHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                Vec3 firePos = groundHit.getLocation().add(0, 0.05, 0); 
                VirtualFireManager.addFirePatch(serverLevel, firePos, player.getUUID(), flatDamage, 60, isPap);
            }
        }

        List<LivingEntity> hitEntities = player.level().getEntitiesOfClass(LivingEntity.class, flameArea,
            target -> target != player && target.isAlive() && !(target instanceof Player)
                      && !(target instanceof net.minecraft.world.entity.TamableAnimal && ((net.minecraft.world.entity.TamableAnimal)target).isTame())
                      && target.getBoundingBox().intersects(flameArea)
                      && player.hasLineOfSight(target)
        );

        long currentTick = player.level().getGameTime();

        for (LivingEntity target : hitEntities) {
            long lastDmg = target.getPersistentData().getLong("LastFlameDmgTick");
            if (currentTick - lastDmg >= 5) {
                target.getPersistentData().putLong("LastFlameDmgTick", currentTick);
                float totalDamage = flatDamage + (target.getMaxHealth() * percentageDamage);
                
                target.getPersistentData().putBoolean(me.cryo.zombierool.core.manager.DamageManager.GUN_DAMAGE_TAG, true);
                me.cryo.zombierool.core.manager.DamageManager.applyDamage(target, player.level().damageSources().playerAttack(player), totalDamage);
                
                if (target instanceof Monster) {
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 10, 0));
                    me.cryo.zombierool.PointManager.modifyScore(player, 10);
                }
            }
            target.setSecondsOnFire(8);

            if (isPap) {
                if (!target.getPersistentData().getBoolean("BlueFire")) {
                    target.getPersistentData().putBoolean("BlueFire", true);
                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> target), new SyncBlueFirePacket(target.getId(), true));
                }
            } else {
                if (target.getPersistentData().getBoolean("BlueFire")) {
                    target.getPersistentData().remove("BlueFire");
                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> target), new SyncBlueFirePacket(target.getId(), false));
                }
            }
        }

        WeaponVfxPacket packet = new WeaponVfxPacket("FLAMETHROWER", visualStartPos, finalEndPos, isPap, false);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(player.blockPosition())), packet);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), packet);
    }

    @Override
    public Component getName(ItemStack stack) {
        boolean upgraded = isPackAPunched(stack);
        String baseName = upgraded ? (def.pap.name != null ? def.pap.name : "Le Carbonisateur") : def.name;
        net.minecraft.network.chat.MutableComponent nameComponent = Component.literal((upgraded ? "§6" : "§4") + baseName);

        int currentOverheat = getOverheat(stack);
        if (isOverheatLocked(stack)) {
            nameComponent.append(Component.literal(" §8(Surchauffé !)"));
        } else if (currentOverheat > 990 * 0.75) {
            nameComponent.append(Component.literal(" §e(Chauffe !)"));
        }
        
        return nameComponent;
    }

    @OnlyIn(Dist.CLIENT)
    public static class FlamethrowerClient {
        private static net.minecraft.client.resources.sounds.AbstractTickableSoundInstance currentLoopSoundInstance = null;
        private static long lastKeepAliveTick = 0;

        public static void keepAlive(Player player) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            lastKeepAliveTick = mc.level.getGameTime();

            if (currentLoopSoundInstance == null || !mc.getSoundManager().isActive(currentLoopSoundInstance)) {
                currentLoopSoundInstance = new LoopingFlamethrowerSound(
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:flamethrower_loop")), 
                    player);
                mc.getSoundManager().play(currentLoopSoundInstance);
            }
        }

        public static class LoopingFlamethrowerSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
            private final Player player;

            public LoopingFlamethrowerSound(SoundEvent soundEvent, Player player) {
                super(soundEvent, SoundSource.PLAYERS, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
                this.player = player;
                this.looping = true;
                this.x = player.getX();
                this.y = player.getY();
                this.z = player.getZ();
                this.volume = 0.7f;
                this.pitch = 1.0f;
                this.relative = true;
                this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
            }

            @Override
            public void tick() {
                long now = player.level().getGameTime();
                if (player.isRemoved() || !(player.getMainHandItem().getItem() instanceof FlamethrowerItem) || (now - lastKeepAliveTick > 10)) {
                    this.stop();
                    currentLoopSoundInstance = null;
                } else {
                    this.x = player.getX();
                    this.y = player.getY();
                    this.z = player.getZ();
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class VirtualFireManager {
        private static class FirePatch {
            ServerLevel level;
            Vec3 pos;
            UUID owner;
            float damage;
            int ticksRemaining;
            boolean isBlue;

            FirePatch(ServerLevel level, Vec3 pos, UUID owner, float damage, int duration, boolean isBlue) {
                this.level = level;
                this.pos = pos;
                this.owner = owner;
                this.damage = damage;
                this.ticksRemaining = duration;
                this.isBlue = isBlue;
            }
        }

        private static final List<FirePatch> activePatches = new ArrayList<>();
        private static final int MAX_FIRE_PATCHES = 40; 

        public static void addFirePatch(ServerLevel level, Vec3 pos, UUID owner, float damage, int duration, boolean isBlue) {
            for (FirePatch patch : activePatches) {
                if (patch.level == level && patch.pos.distanceToSqr(pos) < 1.0) {
                    patch.ticksRemaining = Math.max(patch.ticksRemaining, duration);
                    patch.isBlue = isBlue; 
                    return;
                }
            }
            if (activePatches.size() >= MAX_FIRE_PATCHES) {
                activePatches.remove(0); 
            }
            activePatches.add(new FirePatch(level, pos, owner, damage, duration, isBlue));
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Iterator<FirePatch> iterator = activePatches.iterator();
            while (iterator.hasNext()) {
                FirePatch patch = iterator.next();
                patch.ticksRemaining--;

                if (patch.ticksRemaining <= 0) {
                    iterator.remove();
                    continue;
                }

                if (patch.ticksRemaining % 2 == 0) {
                    patch.level.sendParticles(patch.isBlue ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                            patch.pos.x + (Math.random() - 0.5),
                            patch.pos.y + Math.random() * 0.4,
                            patch.pos.z + (Math.random() - 0.5),
                            3, 0.1, 0.1, 0.1, 0.01);
                    if (Math.random() < 0.2) {
                        patch.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                patch.pos.x, patch.pos.y + 0.2, patch.pos.z,
                                1, 0.1, 0.1, 0.1, 0.02);
                    }
                }

                if (patch.ticksRemaining % 10 == 0) {
                    AABB damageBox = new AABB(
                            patch.pos.x - 1.0, patch.pos.y - 0.2, patch.pos.z - 1.0,
                            patch.pos.x + 1.0, patch.pos.y + 1.5, patch.pos.z + 1.0
                    );
                    List<LivingEntity> entities = patch.level.getEntitiesOfClass(LivingEntity.class, damageBox);

                    for (LivingEntity target : entities) {
                        if (!target.isAlive() || target.isSpectator()) continue;
                        if (target instanceof me.cryo.zombierool.entity.WhiteKnightEntity) continue;

                        float finalDamage = patch.damage;
                        if (target instanceof Player p) {
                            if (p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
                                continue;
                            }
                            if (p.getUUID().equals(patch.owner)) {
                                finalDamage = 0.5f; 
                            } else {
                                continue; 
                            }
                        } else if (target instanceof Monster) {
                            target.setSecondsOnFire(4);
                            if (patch.isBlue) {
                                if (!target.getPersistentData().getBoolean("BlueFire")) {
                                    target.getPersistentData().putBoolean("BlueFire", true);
                                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> target), new SyncBlueFirePacket(target.getId(), true));
                                }
                            } else {
                                if (target.getPersistentData().getBoolean("BlueFire")) {
                                    target.getPersistentData().remove("BlueFire");
                                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> target), new SyncBlueFirePacket(target.getId(), false));
                                }
                            }
                        }

                        me.cryo.zombierool.core.manager.DamageManager.applyDamage(target, patch.level.damageSources().onFire(), finalDamage);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onLivingTick(LivingEvent.LivingTickEvent event) {
            LivingEntity entity = event.getEntity();
            if (!entity.level().isClientSide && entity.getRemainingFireTicks() <= 0 && entity.getPersistentData().getBoolean("BlueFire")) {
                entity.getPersistentData().remove("BlueFire");
                NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), new SyncBlueFirePacket(entity.getId(), false));
            }
        }

        @SubscribeEvent
        public static void onStartTracking(PlayerEvent.StartTracking event) {
            if (event.getTarget() instanceof LivingEntity entity && !event.getEntity().level().isClientSide) {
                if (entity.getPersistentData().getBoolean("BlueFire")) {
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.getEntity()), new SyncBlueFirePacket(entity.getId(), true));
                }
            }
        }
    }
}