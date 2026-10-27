package me.cryo.zombierool.core.system;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

import me.cryo.zombierool.core.manager.BallisticManager;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.ZombieroolKeys;
import me.cryo.zombierool.item.PackAPunchProjectiles;
import net.minecraft.ChatFormatting;

public class WeaponImplementations {

    public static class HitscanGunItem extends WeaponSystem.BaseGunItem {
        public HitscanGunItem(WeaponSystem.Definition def) { super(def); }

        @Override
        protected void performShooting(ItemStack stack, Player player, float charge, boolean isLeft) {
            if (player.level().isClientSide) return;
            
            float damage = getWeaponDamage(stack);
            boolean isPap = isPackAPunched(stack);
            float spread = getDynamicSpread(stack, player);
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

        @Override
        protected void triggerCherryEffect(Player player) {
            me.cryo.zombierool.gameplay.CherryDischarge.trigger(player);
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
            float spread = getDynamicSpread(stack, player);
            float velocity = isPap ? def.ballistics.velocity * 1.25f : def.ballistics.velocity; 
            int count = (isPap && def.pap.pellet_count_override > 0) ? def.pap.pellet_count_override : def.ballistics.count;
            int penetration = def.stats.penetration;
            if (isPap) penetration += def.pap.penetration_bonus;

            for (int i = 0; i < count; i++) {
                Entity projectile;
                Vec3 startPos = getVisualMuzzlePos(player, isLeft);

                float currentYaw = player.getYRot();
                if (count == 3 && isPap) {
                    if (i == 0) currentYaw -= 10.0f;
                    else if (i == 2) currentYaw += 10.0f;
                }
                
                float yawOffset = isLeft ? -3.0f : 3.0f;

                if ("GRENADE".equalsIgnoreCase(def.ballistics.type)) {
                    me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity grenade = new me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity(player.level(), player, 0);
                    grenade.setPos(startPos.x, startPos.y, startPos.z);
                    grenade.shootFromRotation(player, player.getXRot(), currentYaw + yawOffset, 0.0F, velocity, spread);
                    
                    CompoundTag nbt = grenade.getPersistentData();
                    PackAPunchProjectiles.applyCustom(nbt, damage, isPap);
                    nbt.putString(ZombieroolKeys.TRAIL_VFX, def.ballistics.trail_vfx);
                    PackAPunchProjectiles.applyExplosive(nbt, def, isPap);

                    if (!def.ballistics.gravity) grenade.setNoGravity(true);
                    projectile = grenade;
                } else {
                    Arrow arrow = new Arrow(player.level(), player);
                    arrow.setBaseDamage(0); 
                    arrow.setPos(startPos.x, startPos.y, startPos.z);
                    arrow.shootFromRotation(player, player.getXRot(), currentYaw + yawOffset, 0.0F, velocity, spread);
                    arrow.setSilent(true);
                    arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
                    
                    if (penetration > 0) {
                        arrow.setPierceLevel((byte) Math.min(127, penetration));
                    }

                    CompoundTag nbt = arrow.getPersistentData();
                    PackAPunchProjectiles.applyCustom(nbt, damage, isPap);
                    PackAPunchProjectiles.applyInvisibleTrail(nbt, def.ballistics.trail_vfx);
                    PackAPunchProjectiles.applyExplosive(nbt, def, isPap);

                    if (!def.ballistics.gravity) arrow.setNoGravity(true);
                    projectile = arrow;
                }
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
                builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon speed", 20.0/Math.max(6,Math.min(20,Math.round(getDefinition().stats.fire_rate*.85f)))-4, AttributeModifier.Operation.ADDITION));
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

        /** Every held melee weapon shares server timing and single-hit ownership. */
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            return attacker instanceof net.minecraft.server.level.ServerPlayer player && me.cryo.zombierool.gameplay.MeleeCombat.request(player,false);
        }
        public void playMeleeSwing(Player player,ItemStack stack,boolean heavy) {
            SoundEvent sound=getSwingSound(stack);
            player.level().playSound(null,player.blockPosition(),sound==null?SoundEvents.PLAYER_ATTACK_SWEEP:sound,SoundSource.PLAYERS,.65f,heavy?.78f:1.08f);
        }
        @Override public int getUseDuration(ItemStack stack){return 72000;}
        @Override public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack){return net.minecraft.world.item.UseAnim.NONE;}
        @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            if(hand!=InteractionHand.MAIN_HAND||!player.getOffhandItem().isEmpty())return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
            if(player instanceof net.minecraft.server.level.ServerPlayer server&&!me.cryo.zombierool.gameplay.MeleeCombat.canCharge(server))return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
            player.startUsingItem(hand);return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining){}
        @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
            if(!(entity instanceof net.minecraft.server.level.ServerPlayer player))return;
            int held=getUseDuration(stack)-remaining;
            if(held<me.cryo.zombierool.gameplay.MeleeMoves.move(stack,player,true).charge())return;
            // Vanilla invokes releaseUsing before clearing the active-use flag.
            player.stopUsingItem();
            if(!me.cryo.zombierool.gameplay.MeleeCombat.request(player,true))return;
            long now=level.getGameTime();var tag=stack.getOrCreateTag();
            if(canDash(stack)&&(!tag.contains(TAG_LAST_DASH_TICK)||now-tag.getLong(TAG_LAST_DASH_TICK)>=getDashCooldownTicks())){
                // A short collision-resolved lunge. Damage still waits for actual melee reach.
                var forward=player.getLookAngle().multiply(1,0,1).normalize();
                double speed=Math.min(.85,getDashDistance()*.15);
                player.setDeltaMovement(forward.x*speed,player.getDeltaMovement().y,forward.z*speed);player.hurtMarked=true;
                tag.putLong(TAG_LAST_DASH_TICK,now);
            }
        }
        @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> tooltip,net.minecraft.world.item.TooltipFlag flag){
            super.appendHoverText(stack,level,tooltip,flag);
            tooltip.add(Component.translatable("tooltip.zombierool.melee.combo").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(me.cryo.zombierool.gameplay.MeleeMoves.kind(stack).equals("shield")?"tooltip.zombierool.melee.guard":"tooltip.zombierool.melee.heavy").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public void tryShoot(ItemStack stack, Player player, float charge, boolean isLeft) {}

        @Override
        protected void performShooting(ItemStack stack, Player player, float charge) {}
    }
}