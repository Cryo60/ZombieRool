package me.cryo.zombierool.core.system;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import me.cryo.zombierool.core.manager.BallisticManager;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class WeaponImplementations {

    public static class HitscanGunItem extends WeaponSystem.BaseGunItem {
        public HitscanGunItem(WeaponSystem.Definition def) { super(def); }

        @Override
        protected void performShooting(ItemStack stack, Player player, float charge, boolean isLeft) {
            if (player.level().isClientSide) return;
            float damage = getWeaponDamage(stack);
            boolean isPap = isPackAPunched(stack);
            float spread = isPap ? def.ballistics.spread * def.pap.spread_mult : def.ballistics.spread;
            int penetration = def.stats.penetration;
            int count = (isPap && def.pap.pellet_count_override > 0) ? def.pap.pellet_count_override : def.ballistics.count;

            if (count == 3 && isPap && def.pap.pellet_count_override == 3) {
                float angleOffset = 10.0f;
                BallisticManager.fireBullet((ServerPlayer) player, (float) def.stats.range, damage, 0.0f, penetration, stack, 0.0f);
                BallisticManager.fireBullet((ServerPlayer) player, (float) def.stats.range, damage, 0.0f, penetration, stack, -angleOffset);
                BallisticManager.fireBullet((ServerPlayer) player, (float) def.stats.range, damage, 0.0f, penetration, stack, angleOffset);
            } else {
                for (int i = 0; i < count; i++) {
                    BallisticManager.fireBullet((ServerPlayer) player, (float) def.stats.range, damage, spread, penetration, stack, 0.0f);
                }
            }
        }

        @Override
        protected void performShooting(ItemStack stack, Player player, float charge) {
            performShooting(stack, player, charge, false);
        }
    }

    public static class PistolGunItem extends HitscanGunItem implements me.cryo.zombierool.item.IHandgunWeapon {
        public PistolGunItem(WeaponSystem.Definition def) { super(def); }
    }

    public static class SniperGunItem extends HitscanGunItem {
        public SniperGunItem(WeaponSystem.Definition def) { super(def); }
    }

    public static class ShellReloadGunItem extends HitscanGunItem {
        public ShellReloadGunItem(WeaponSystem.Definition def) { super(def); }

        @Override
        public void startReload(ItemStack stack, Player player) {
            if (player.getCooldowns().isOnCooldown(this)) return;
            if (getAmmo(stack) >= getMaxAmmo(stack)) return;
            if (getReserve(stack) <= 0 && !player.isCreative()) return;
            getOrCreateTag(stack).putInt(TAG_BURST_SHOTS_LEFT, 0);

            float baseTime = def.ammo.reload_time;
            if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) baseTime *= 0.5f;

            setReloadTimer(stack, (int)baseTime);
            getOrCreateTag(stack).putBoolean(TAG_IS_RELOADING, true);
            player.getCooldowns().addCooldown(this, (int)baseTime);

            if (!player.level().isClientSide) {
                if (def.sounds.reload_start != null && !def.sounds.reload_start.isEmpty()) {
                    playSound(player.level(), player, def.sounds.reload_start);
                } else {
                    playSound(player.level(), player, def.sounds.reload);
                }
                triggerCherryEffect(player);
            }
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            super.inventoryTick(stack, level, entity, slot, selected);
            if (!(entity instanceof Player player)) return;
            if (!getOrCreateTag(stack).getBoolean(TAG_IS_RELOADING)) return;

            int timer = getReloadTimer(stack);
            if (timer > 0) {
                setReloadTimer(stack, timer - 1);
                return;
            }

            int current = getAmmo(stack);
            int max = getMaxAmmo(stack);

            if (current < max && (getReserve(stack) > 0 || player.isCreative())) {
                setAmmo(stack, current + 1);
                if (!player.isCreative()) setReserve(stack, getReserve(stack) - 1);

                if (!level.isClientSide) {
                    playSound(level, player, def.sounds.reload); 
                }

                float time = 15; 
                if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) time *= 0.5f;
                setReloadTimer(stack, (int) time);
                player.getCooldowns().addCooldown(this, (int)time);
            } else {
                getOrCreateTag(stack).putBoolean(TAG_IS_RELOADING, false);
                if (!level.isClientSide) {
                    if (def.sounds.reload_end != null && !def.sounds.reload_end.isEmpty()) {
                        playSound(level, player, def.sounds.reload_end); 
                    } else if (def.sounds.pump != null && !def.sounds.pump.isEmpty()) {
                        playSound(level, player, def.sounds.pump); 
                    }
                }
            }
        }
    }

    public static class ShotgunItem extends ShellReloadGunItem {
        public ShotgunItem(WeaponSystem.Definition def) { super(def); }
    }

    public static class BoltActionRifleItem extends ShellReloadGunItem {
        public BoltActionRifleItem(WeaponSystem.Definition def) { super(def); }
    }

    public static class ProjectileGunItem extends WeaponSystem.BaseGunItem {
        public ProjectileGunItem(WeaponSystem.Definition def) { super(def); }

        @Override
        protected void performShooting(ItemStack stack, Player player, float charge, boolean isLeft) {
            if (player.level().isClientSide) return;
            boolean isPap = isPackAPunched(stack);
            float damage = getWeaponDamage(stack);
            float spread = isPap ? def.ballistics.spread * def.pap.spread_mult : def.ballistics.spread;
            float velocity = isPap ? def.ballistics.velocity * 1.25f : def.ballistics.velocity; 
            int count = (isPap && def.pap.pellet_count_override > 0) ? def.pap.pellet_count_override : def.ballistics.count;
            int penetration = def.stats.penetration;
            if (isPap) penetration += def.pap.penetration_bonus;

            for (int i = 0; i < count; i++) {
                Arrow projectile = new Arrow(player.level(), player);
                projectile.setBaseDamage(0); 
                Vec3 startPos = getVisualMuzzlePos(player, isLeft);
                projectile.setPos(startPos.x, startPos.y, startPos.z);
                
                float currentYaw = player.getYRot();
                if (count == 3 && isPap) {
                    if (i == 0) currentYaw -= 10.0f;
                    else if (i == 2) currentYaw += 10.0f;
                }

                float yawOffset = isLeft ? -3.0f : 3.0f;
                projectile.shootFromRotation(player, player.getXRot(), currentYaw + yawOffset, 0.0F, velocity, spread);
                projectile.setSilent(true);
                projectile.pickup = AbstractArrow.Pickup.DISALLOWED;

                if (penetration > 0) {
                    projectile.setPierceLevel((byte) Math.min(127, penetration));
                }

                CompoundTag nbt = projectile.getPersistentData();
                nbt.putBoolean("zombierool:custom_projectile", true);
                nbt.putFloat("zombierool:damage", damage);
                nbt.putBoolean("zombierool:invisible", true); 
                nbt.putBoolean("zombierool:pap", isPap);
                nbt.putString("zombierool:trail_vfx", def.ballistics.trail_vfx);

                if (def.explosion != null && (!def.explosion.pap_only || isPap)) {
                    nbt.putBoolean("zombierool:explosive", true);
                    nbt.putFloat("zr_exp_radius", def.explosion.radius + (isPap ? def.pap.explosion_radius_bonus : 0));
                    nbt.putFloat("zr_exp_dmg_mult", def.explosion.damage_multiplier);
                    nbt.putFloat("zr_exp_self_mult", def.explosion.self_damage_multiplier);
                    nbt.putFloat("zr_exp_self_cap", def.explosion.self_damage_cap);
                    nbt.putFloat("zr_exp_kb", def.explosion.knockback);
                    nbt.putString("zr_exp_vfx", def.explosion.vfx_type);
                    nbt.putString("zr_exp_sound", def.explosion.sound);
                }

                if (!def.ballistics.gravity) projectile.setNoGravity(true);
                player.level().addFreshEntity(projectile);
            }
        }

        @Override
        protected void performShooting(ItemStack stack, Player player, float charge) {
            performShooting(stack, player, charge, false);
        }
    }

    public static class MeleeWeaponItem extends WeaponSystem.BaseGunItem {
        public static final java.util.UUID BASE_ATTACK_DAMAGE_UUID = java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
        public static final java.util.UUID BASE_ATTACK_SPEED_UUID = java.util.UUID.fromString("FA233EE9-2F64-44F4-52E5-2792376F8AE5");
        protected static final String TAG_LAST_DASH_TICK = "LastDashTick";

        public MeleeWeaponItem(WeaponSystem.Definition def) { super(def); }

        @Override
        public void initializeIfNeeded(ItemStack stack) {
            super.initializeIfNeeded(stack);
            stack.getOrCreateTag().putInt("HideFlags", 2); 
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
            Multimap<Attribute, AttributeModifier> map = super.getDefaultAttributeModifiers(slot);
            if (slot == EquipmentSlot.MAINHAND) {
                ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
                builder.putAll(map);
                builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", def.stats.damage, AttributeModifier.Operation.ADDITION));
                builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon speed", -2.4, AttributeModifier.Operation.ADDITION));
                return builder.build();
            }
            return map;
        }

        protected float getCriticalChance() { return 0.10f; }
        protected float getCriticalMultiplier() { return 1.5f; }
        protected double getCleaveRadius() { return 1.5D; }
        protected float getCleaveDamagePercentage() { return 0.50f; }
        protected float getDashDistance() { return 3.0f; }
        protected int getDashCooldownTicks() { return 40; }
        protected boolean canDash(ItemStack stack) { return true; }

        protected SoundEvent getSwingSound(ItemStack stack) {
            String soundId = isPackAPunched(stack) ? def.sounds.fire_pap : def.sounds.fire;
            if (soundId != null && !soundId.isEmpty()) {
                return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
            }
            return null;
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            if (!(attacker instanceof Player player) || player.level().isClientSide) return true;

            float baseDamage = getWeaponDamage(stack);
            boolean isCritical = player.level().random.nextFloat() < getCriticalChance();
            float actualDamage = isCritical ? baseDamage * getCriticalMultiplier() : baseDamage;

            java.util.concurrent.atomic.AtomicInteger entitiesHit = new java.util.concurrent.atomic.AtomicInteger(0);

            if (isCritical) {
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.5f);
            }

            // Enlever les balises Gun et Headshot pour éviter le GoreManager sur les kills de mêlée (Épées etc)
            target.getPersistentData().remove(me.cryo.zombierool.core.manager.DamageManager.GUN_DAMAGE_TAG);
            target.getPersistentData().remove(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG);
            target.getPersistentData().remove("zombierool:explosive_damage");

            me.cryo.zombierool.core.manager.DamageManager.applyDamage(target, player.damageSources().playerAttack(player), actualDamage);
            entitiesHit.incrementAndGet();

            SoundEvent swing = getSwingSound(stack);
            if (swing != null) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), swing, SoundSource.PLAYERS, 1.0f, 0.9f + player.level().random.nextFloat() * 0.2f);
            }

            AABB cleaveBox = target.getBoundingBox().inflate(getCleaveRadius());
            player.level().getEntitiesOfClass(LivingEntity.class, cleaveBox, entity ->
                entity != player && entity.isAlive() && entity != target &&
                (entity instanceof me.cryo.zombierool.entity.ZombieEntity || entity instanceof me.cryo.zombierool.entity.CrawlerEntity || entity instanceof me.cryo.zombierool.entity.HellhoundEntity || entity instanceof me.cryo.zombierool.entity.DummyEntity)
            ).forEach(entity -> {
                entity.getPersistentData().remove(me.cryo.zombierool.core.manager.DamageManager.GUN_DAMAGE_TAG);
                entity.getPersistentData().remove(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG);
                entity.getPersistentData().remove("zombierool:explosive_damage");

                me.cryo.zombierool.core.manager.DamageManager.applyDamage(entity, player.damageSources().playerAttack(player), actualDamage * getCleaveDamagePercentage());
                entitiesHit.incrementAndGet();
            });

            if (hasDurability() && !player.isCreative()) {
                int dur = getDurability(stack) - entitiesHit.get();
                setDurability(stack, Math.max(0, dur));
                if (dur <= 0) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
                    stack.shrink(1);
                }
            }

            getOrCreateTag(stack).putLong(TAG_LAST_FIRE, player.level().getGameTime());
            return true;
        }

        @Override
        public int getUseDuration(ItemStack s) { return 72000; }

        @Override
        public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack s) { return canDash(s) ? net.minecraft.world.item.UseAnim.SPEAR : net.minecraft.world.item.UseAnim.NONE; }

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (canDash(stack)) {
                player.startUsingItem(hand);
                return net.minecraft.world.InteractionResultHolder.consume(stack);
            } else {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("§cCette arme doit être améliorée pour utiliser le dash !"), true);
                }
                return net.minecraft.world.InteractionResultHolder.fail(stack);
            }
        }

        @Override
        public void onUseTick(Level level, LivingEntity ent, ItemStack stack, int count) {
            if (!(ent instanceof Player player) || level.isClientSide) return;
            long currentTick = level.getGameTime();
            long lastDash = getOrCreateTag(stack).getLong(TAG_LAST_DASH_TICK);

            if (currentTick - lastDash >= getDashCooldownTicks()) {
                Vec3 lookVec = player.getViewVector(1.0f).normalize();
                float dashDist = getDashDistance();
                
                java.util.Optional<LivingEntity> targetOpt = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(dashDist), target -> {
                        if (!(target instanceof me.cryo.zombierool.entity.ZombieEntity || target instanceof me.cryo.zombierool.entity.CrawlerEntity || target instanceof me.cryo.zombierool.entity.HellhoundEntity || target instanceof me.cryo.zombierool.entity.DummyEntity)) return false;
                        if (!target.isAlive() || target.equals(player)) return false;
                        if (player.distanceTo(target) > dashDist) return false;
                        if (!player.hasLineOfSight(target)) return false; 
                        Vec3 dirToTarget = target.getEyePosition().subtract(player.getEyePosition()).normalize();
                        return lookVec.dot(dirToTarget) > 0.8; 
                    }
                ).stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr));

                if (targetOpt.isPresent()) {
                    LivingEntity target = targetOpt.get();
                    Vec3 dir = target.position().subtract(player.position()).normalize();
                    Vec3 dash = dir.scale(dashDist * 0.8);
                    
                    player.setDeltaMovement(dash.x, player.getDeltaMovement().y, dash.z);
                    player.hurtMarked = true;
                    player.fallDistance = 0;
                    
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);
                    getOrCreateTag(stack).putLong(TAG_LAST_DASH_TICK, currentTick);
                    
                    this.hurtEnemy(stack, target, player);
                    
                    player.getCooldowns().addCooldown(this, getDashCooldownTicks());
                }
            }
        }

        @Override
        public void tryShoot(ItemStack stack, Player player, float charge, boolean isLeft) {}

        @Override
        protected void performShooting(ItemStack stack, Player player, float charge) {}
    }
}