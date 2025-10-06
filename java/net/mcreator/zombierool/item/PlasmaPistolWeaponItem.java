package net.mcreator.zombierool.item;

import com.mojang.datafixers.util.Pair;
import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.api.IHeadshotWeapon;
import net.mcreator.zombierool.api.IOverheatable;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.sounds.SoundEvents;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal; // Added missing import for Animal

import net.mcreator.zombierool.client.CherryReloadAnimationHandler;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;

// Added missing imports for NetworkHandler, RecoilPacket, and PacketDistributor
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

public class PlasmaPistolWeaponItem extends Item implements ICustomWeapon, IPackAPunchable, IHeadshotWeapon, IOverheatable { // Removed IHandgunWeapon as it was not defined in the provided code

    // --- Overheat & Firing Parameters ---
    private static final int MAX_HEAT = 100;
    private static final int HEAT_PER_SHOT_NORMAL = 10;
    private static final int HEAT_PER_SHOT_OVERCHARGE = 40;
    private static final int COOL_DOWN_PER_TICK = 5; // Will modify this later if needed, but lets keep it here for now
    private static final int OVERHEAT_THRESHOLD_FOR_DISABLE = 95;
    private static final int UNLOCK_OVERHEAT_THRESHOLD = MAX_HEAT / 5;

    private static final int COOLDOWN_TICKS_NORMAL_SHOT = 3;
    private static final int COOLDOWN_TICKS_OVERCHARGE_SHOT = 40;
    private static final int CHARGE_TIME_TICKS = 20;
    private static final int MIN_CHARGE_FOR_OVERCHARGE_TICKS = 5; // Min ticks to register as a charging attempt (e.g., 0.25s)

    // --- Damage Parameters ---
    private static final float WEAPON_DAMAGE_NORMAL = 4.5f;
    private static final float PAP_BONUS_DAMAGE_NORMAL = 3.5f;
    private static final float WEAPON_DAMAGE_OVERCHARGE_BASE = 12.0f;
    private static final float WEAPON_DAMAGE_OVERCHARGE_FULL = 10.0f;
    private static final float PAP_BONUS_DAMAGE_OVERCHARGE_FULL = 15.0f;
      
    private static final float BASE_HEADSHOT_DAMAGE = 2.0f;
    private static final float PAP_HEADSHOT_BONUS = 3.0f;    

    // --- Projectile Parameters ---
    private static final float BASE_PROJECTILE_VELOCITY_NORMAL = 3.0f;
    private static final float BASE_PROJECTILE_VELOCITY_OVERCHARGE = 4.5f;
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.2f;
    
    private static final float BASE_PROJECTILE_SPREAD_NORMAL = 0.8f;
    private static final float BASE_PROJECTILE_SPREAD_OVERCHARGE = 0.1f;
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.6f;

    // --- Recoil Parameters ---
    private static final float BASE_RECOIL_PITCH_NORMAL = 0.6f;
    private static final float BASE_RECOIL_YAW_NORMAL = 0.3f;
    private static final float BASE_RECOIL_PITCH_OVERCHARGE = 1.2f;
    private static final float BASE_RECOIL_YAW_OVERCHARGE = 0.6f;
    private static final float PAP_RECOIL_MULTIPLIER = 0.5f;

    // --- Durability Parameters ---
    private static final int BASE_MAX_DURABILITY = 200;
    private static final int PAP_DURABILITY_BONUS = 200;
    private static final int DURABILITY_DRAIN_NORMAL_SHOT = 1;
    private static final int DURABILITY_DRAIN_OVERCHARGE_SHOT = 5;

    // --- Sounds ---
    private static final SoundEvent FIRE_SOUND_NORMAL          = ZombieroolModSounds.PLASMA_PISTOL_FIRE.get();
    private static final SoundEvent CHARGE_LOOP_SOUND          = ZombieroolModSounds.PLASMA_PISTOL_CHARGE_LOOP.get();
    private static final SoundEvent OVERHEAT_SOUND             = ZombieroolModSounds.PLASMA_PISTOL_OVERHEAT.get();
    private static final SoundEvent WEAPON_IN_HAND_SOUND       = ZombieroolModSounds.PLASMA_PISTOL_READY.get();
    private static final SoundEvent DRY_FIRE_SOUND             = ZombieroolModSounds.RIFLE_DRY.get();

    // --- NBT Tags ---
    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_OVERHEAT = "Overheat";
    private static final String TAG_CHARGE_TIMER = "ChargeTimer";
    private static final String TAG_IS_CHARGING = "IsCharging";
    private static final String TAG_OVERHEAT_LOCKED = "OverheatLocked";
    private static final String TAG_WAS_OVERHEATED_FLAG = "WasOverheatedFlag";
    private static final String TAG_DURABILITY = "Durability";
    // *** NEW/MODIFIED NBT TAG for consistency with ArrowImpactHandler ***
    public static final String TAG_ARROW_DAMAGE = "zombierool:arrow_damage";
    public static final String TAG_IS_OVERCHARGED = "zombierool:is_overcharged";


    // --- Internal class for Cherry Cola Effects (consistent) ---
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    private static AbstractTickableSoundInstance currentChargeLoopSoundInstance = null;

    public static class LoopingPlasmaChargeSound extends AbstractTickableSoundInstance {
        private final Player player;

        public LoopingPlasmaChargeSound(SoundEvent soundEvent, Player player) {
            super(soundEvent, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.volume = 0.7f;
            this.pitch = 1.0f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof PlasmaPistolWeaponItem) || !((PlasmaPistolWeaponItem)player.getUseItem().getItem()).isCharging(player.getUseItem())) {
                this.stop();
            }
        }
    }

    public PlasmaPistolWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    /**
     * Helper method to check if the client's language is English.
     * This is crucial for dynamic translation of item names and tooltips.
     * @return true if the client's language code starts with "en", false otherwise.
     */
    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    /**
     * Helper method for dynamic translation based on the client's language.
     * @param frenchMessage The message to display if the client's language is French or not English.
     * @param englishMessage The message to display if the client's language is English.
     * @return The appropriate translated message.
     */
    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    // --- Pack-a-Punch Implementation ---
    @Override
    public void applyPackAPunch(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_PAP, true);
        setOverheat(stack, 0);
        setOverheatLocked(stack, false);
        setWasOverheatedFlag(stack, false);
        setDurability(stack, getMaxDurability(stack));
    }

    @Override
    public boolean isPackAPunched(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_PAP);
    }

    // --- Dynamic Damage & Stats based on shot type and PaP status ---
    public float getNormalShotDamage(ItemStack stack) {
        return isPackAPunched(stack) ? WEAPON_DAMAGE_NORMAL + PAP_BONUS_DAMAGE_NORMAL : WEAPON_DAMAGE_NORMAL;
    }

    public float getOverchargeShotDamage(ItemStack stack, float chargeProgress) {
        float baseDamage = Mth.lerp(chargeProgress, WEAPON_DAMAGE_OVERCHARGE_BASE, WEAPON_DAMAGE_OVERCHARGE_FULL);
        float papBonus = Mth.lerp(chargeProgress, 0.0f, PAP_BONUS_DAMAGE_OVERCHARGE_FULL);
        return isPackAPunched(stack) ? baseDamage + papBonus : baseDamage;
    }

    @Override
    public float getWeaponDamage(ItemStack stack) {
        // This method should return the base damage for the item,
        // but for Plasma Pistol, the actual damage depends on charge,
        // so it's better to get it directly from the projectile data in ArrowImpactHandler
        return getNormalShotDamage(stack); // Defaulting to normal shot damage for general purpose
    }
      
    @Override
    public float getHeadshotBaseDamage(ItemStack stack) {
        return BASE_HEADSHOT_DAMAGE;
    }

    @Override
    public float getHeadshotPapBonusDamage(ItemStack stack) {
        return PAP_HEADSHOT_BONUS;   
    }

    public float getProjectileVelocity(ItemStack stack, boolean isOvercharged) {
        float base = isOvercharged ? BASE_PROJECTILE_VELOCITY_OVERCHARGE : BASE_PROJECTILE_VELOCITY_NORMAL;
        return isPackAPunched(stack) ? base * PAP_PROJECTILE_VELOCITY_MULTIPLIER : base;
    }

    public float getProjectileSpread(ItemStack stack, boolean isOvercharged) {
        float base = isOvercharged ? BASE_PROJECTILE_SPREAD_OVERCHARGE : BASE_PROJECTILE_SPREAD_NORMAL;
        return isPackAPunched(stack) ? base * PAP_PROJECTILE_SPREAD_MULTIPLIER : base;
    }

    public float getRecoilPitch(ItemStack stack, boolean isOvercharged) {
        float base = isOvercharged ? BASE_RECOIL_PITCH_OVERCHARGE : BASE_RECOIL_PITCH_NORMAL;
        return isPackAPunched(stack) ? base * PAP_RECOIL_MULTIPLIER : base;
    }

    public float getRecoilYaw(ItemStack stack, boolean isOvercharged) {
        float base = isOvercharged ? BASE_RECOIL_YAW_OVERCHARGE : BASE_RECOIL_YAW_NORMAL;
        return isPackAPunched(stack) ? base * PAP_RECOIL_MULTIPLIER : base;
    }

    // --- NBT Management ---
    private CompoundTag getOrCreateTag(ItemStack s) {
        if (!s.hasTag()) s.setTag(new CompoundTag());
        return s.getTag();
    }

    @Override
    public int getOverheat(ItemStack stack) {
        return getOrCreateTag(stack).getInt(TAG_OVERHEAT);
    }

    @Override
    public void setOverheat(ItemStack stack, int overheat) {
        getOrCreateTag(stack).putInt(TAG_OVERHEAT, Mth.clamp(overheat, 0, MAX_HEAT));
    }

    @Override
    public int getMaxOverheat() {
        return MAX_HEAT;
    }

    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    public int getChargeTimer(ItemStack stack) { return getOrCreateTag(stack).getInt(TAG_CHARGE_TIMER); }
    public void setChargeTimer(ItemStack stack, int timer) { getOrCreateTag(stack).putInt(TAG_CHARGE_TIMER, timer); }

    public boolean isCharging(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_IS_CHARGING); }
    public void setIsCharging(ItemStack stack, boolean charging) { getOrCreateTag(stack).putBoolean(TAG_IS_CHARGING, charging); }

    public boolean isOverheatLocked(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_OVERHEAT_LOCKED); }
    public void setOverheatLocked(ItemStack stack, boolean locked) { getOrCreateTag(stack).putBoolean(TAG_OVERHEAT_LOCKED, locked); }

    public boolean getWasOverheatedFlag(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_WAS_OVERHEATED_FLAG); }
    public void setWasOverheatedFlag(ItemStack stack, boolean flag) { getOrCreateTag(stack).putBoolean(TAG_WAS_OVERHEATED_FLAG, flag); }

    // --- Durability Management ---
    public int getMaxDurability(ItemStack stack) {
        int max = BASE_MAX_DURABILITY;
        if (isPackAPunched(stack)) {
            max += PAP_DURABILITY_BONUS;
        }
        return max;
    }

    public int getDurability(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        if (!tag.contains(TAG_DURABILITY)) {
            tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
        }
        return tag.getInt(TAG_DURABILITY);
    }

    public void setDurability(ItemStack stack, int durability) {
        CompoundTag tag = getOrCreateTag(stack);
        int max = getMaxDurability(stack);
        if (durability > max) durability = max;
        if (durability < 0) durability = 0;
        tag.putInt(TAG_DURABILITY, durability);
    }

    public void damageItem(ItemStack stack, int amount, Player player) {
        int current = getDurability(stack);
        setDurability(stack, current - amount);
        if (getDurability(stack) <= 0) {
            if (!player.level().isClientSide) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            stack.shrink(1);
        }
    }

    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_OVERHEAT)) tag.putInt(TAG_OVERHEAT, 0);
        if (!tag.contains(TAG_LAST_FIRE_TICK)) tag.putLong(TAG_LAST_FIRE_TICK, 0);
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        if (!tag.contains(TAG_CHARGE_TIMER)) tag.putInt(TAG_CHARGE_TIMER, 0);
        if (!tag.contains(TAG_IS_CHARGING)) tag.putBoolean(TAG_IS_CHARGING, false);
        if (!tag.contains(TAG_OVERHEAT_LOCKED)) tag.putBoolean(TAG_OVERHEAT_LOCKED, false);
        if (!tag.contains(TAG_WAS_OVERHEATED_FLAG)) tag.putBoolean(TAG_WAS_OVERHEATED_FLAG, false);
        if (!tag.contains(TAG_DURABILITY)) {
            tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
        }
    }

    @Override public UseAnim getUseAnimation(ItemStack s) {
        if (isOverheatLocked(s)) return UseAnim.NONE;
        if (isCharging(s) && getChargeTimer(s) > 0) return UseAnim.BOW;
        return UseAnim.NONE;
    }
    @Override public int     getUseDuration(ItemStack s)  { return 72000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        initializeIfNeeded(stack);

        if (isOverheatLocked(stack)) {
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            }
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        
        if (level.isClientSide()) {
            if (currentChargeLoopSoundInstance == null || !Minecraft.getInstance().getSoundManager().isActive(currentChargeLoopSoundInstance)) {
                currentChargeLoopSoundInstance = new LoopingPlasmaChargeSound(CHARGE_LOOP_SOUND, player);
                Minecraft.getInstance().getSoundManager().play(currentChargeLoopSoundInstance);
            }
        }
        
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity ent, ItemStack stack, int count) {
        if (!(ent instanceof Player player)) return;
        initializeIfNeeded(stack);

        if (isOverheatLocked(stack)) {
            player.stopUsingItem();
            setIsCharging(stack, false);
            setChargeTimer(stack, 0);
            if (level.isClientSide() && currentChargeLoopSoundInstance != null) {
                Minecraft.getInstance().getSoundManager().stop(currentChargeLoopSoundInstance);
                currentChargeLoopSoundInstance = null;
            }
            return;
        }

        int elapsedUseTicks = 72000 - count;
        setChargeTimer(stack, elapsedUseTicks);

        if (elapsedUseTicks >= MIN_CHARGE_FOR_OVERCHARGE_TICKS) {
            setIsCharging(stack, true);
        } else {
            setIsCharging(stack, false);
        }
        
        if (level.isClientSide()) {
            if (isCharging(stack)) {
                if (currentChargeLoopSoundInstance == null || !Minecraft.getInstance().getSoundManager().isActive(currentChargeLoopSoundInstance)) {
                    currentChargeLoopSoundInstance = new LoopingPlasmaChargeSound(CHARGE_LOOP_SOUND, player);
                    Minecraft.getInstance().getSoundManager().play(currentChargeLoopSoundInstance);
                }
            } else {
                if (currentChargeLoopSoundInstance != null) {
                    Minecraft.getInstance().getSoundManager().stop(currentChargeLoopSoundInstance);
                    currentChargeLoopSoundInstance = null;
                }
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof Player player) || level.isClientSide) return;
        initializeIfNeeded(stack);

        int currentOverheat = getOverheat(stack);
        long currentTick = level.getGameTime();
        long lastFireTick = getLastFireTick(stack);
        int chargeTimer = getChargeTimer(stack);

        if (level.isClientSide() && currentChargeLoopSoundInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(currentChargeLoopSoundInstance);
            currentChargeLoopSoundInstance = null;
        }

        setIsCharging(stack, false);
        setChargeTimer(stack, 0);
        player.stopUsingItem();

        boolean isOverchargedShot = chargeTimer >= MIN_CHARGE_FOR_OVERCHARGE_TICKS;

        float actualDamage;
        int actualHeatCost;
        int actualDurabilityDrain;
        int cooldownForThisShot;

        if (isOverchargedShot) {
            float chargeProgress = Mth.clamp((float) chargeTimer / CHARGE_TIME_TICKS, 0.0f, 1.0f);
            actualDamage = getOverchargeShotDamage(stack, chargeProgress);
            actualHeatCost = (int) (HEAT_PER_SHOT_NORMAL + (HEAT_PER_SHOT_OVERCHARGE - HEAT_PER_SHOT_NORMAL) * chargeProgress);
            actualDurabilityDrain = (int) (DURABILITY_DRAIN_NORMAL_SHOT + (DURABILITY_DRAIN_OVERCHARGE_SHOT - DURABILITY_DRAIN_NORMAL_SHOT) * chargeProgress);
            cooldownForThisShot = COOLDOWN_TICKS_OVERCHARGE_SHOT;

            if (isPackAPunched(stack)) {
                actualHeatCost = (int)(actualHeatCost * 0.75);
            }
        } else {
            actualDamage = getNormalShotDamage(stack);
            actualHeatCost = HEAT_PER_SHOT_NORMAL;
            actualDurabilityDrain = DURABILITY_DRAIN_NORMAL_SHOT;
            cooldownForThisShot = COOLDOWN_TICKS_NORMAL_SHOT;

            if (isPackAPunched(stack)) {
                actualHeatCost = (int)(actualHeatCost * 0.75);
            }
        }
        
        if (currentTick - lastFireTick < cooldownForThisShot) {
            return;
        }

        if (currentOverheat + actualHeatCost > MAX_HEAT || getDurability(stack) < actualDurabilityDrain) {
            if (currentOverheat + actualHeatCost > MAX_HEAT) {
                startOverheat(stack, player, level);
            } else if (getDurability(stack) < actualDurabilityDrain) {
                damageItem(stack, actualDurabilityDrain, player);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            }
            return;
        }

        fireProjectile(stack, player, level, isOverchargedShot, actualDamage);
        setOverheat(stack, currentOverheat + actualHeatCost);
        damageItem(stack, actualDurabilityDrain, player);
        setLastFireTick(stack, currentTick);

        if (getOverheat(stack) >= MAX_HEAT) {
            startOverheat(stack, player, level);
        }
    }


    private void fireProjectile(ItemStack stack, Player player, Level level, boolean isOvercharged, float damageToDeal) {
        if (level.isClientSide) return;

        Vec3 start = player.getEyePosition(1F);
        Vec3 dir   = player.getViewVector(1F);

        float projectileVelocity = getProjectileVelocity(stack, isOvercharged);
        float projectileSpread = getProjectileSpread(stack, isOvercharged);
        float recoilPitch = getRecoilPitch(stack, isOvercharged);
        float recoilYaw = getRecoilYaw(stack, isOvercharged);

        Arrow arrow = new Arrow(level, player);
        arrow.setOwner(player);
        arrow.getPersistentData().putUUID("shooterUUID", player.getUUID());
        arrow.setPos(start.x, start.y, start.z);
        arrow.setSilent(true);
        arrow.setNoGravity(true);
        arrow.pickup = Pickup.DISALLOWED;
        arrow.getPersistentData().putBoolean("zombierool:invisible", true);
        arrow.getPersistentData().putBoolean("zombierool:small", !isOvercharged);
        arrow.setInvisible(true);
        arrow.setBaseDamage(0.0D); // Damage handled by ArrowImpactHandler
        // *** MODIFIED NBT TAG HERE ***
        arrow.getPersistentData().putFloat(TAG_ARROW_DAMAGE, damageToDeal); // Pass actual damage to handler
        arrow.getPersistentData().putBoolean(TAG_IS_OVERCHARGED, isOvercharged);

        arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread);
          
        level.addFreshEntity(arrow);

        if (player instanceof ServerPlayer serverPlayer) {
            float actualPitchRecoil = recoilPitch;
            float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
        }

        level.playSound(null,
            player.getX(), player.getY(), player.getZ(),
            FIRE_SOUND_NORMAL,
            SoundSource.PLAYERS, 0.7f, 1f
        );
    }

    private void startOverheat(ItemStack stack, Player player, Level level) {
        if (!isOverheatLocked(stack)) {
            setOverheatLocked(stack, true);
            setOverheat(stack, MAX_HEAT);
            setIsCharging(stack, false);
            player.stopUsingItem();
            setWasOverheatedFlag(stack, true);
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), OVERHEAT_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            if (level.isClientSide() && currentChargeLoopSoundInstance != null) {
                Minecraft.getInstance().getSoundManager().stop(currentChargeLoopSoundInstance);
                currentChargeLoopSoundInstance = null;
            }
        }
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);

        int currentOverheat = getOverheat(stack);
        boolean isLocked = isOverheatLocked(stack);
        boolean wasFlaggedOverheated = getWasOverheatedFlag(stack);

        // --- Overheat Management ---
        if (!level.isClientSide()) {
            boolean isPlayerCurrentlyUsing = sel && p.isUsingItem() && p.getUseItem().equals(stack);
            
            // Only cool down if not currently being used, OR if it's locked (and needs to cool down to unlock)
            if (!isPlayerCurrentlyUsing || isLocked) {
                if (currentOverheat > 0) {
                    // *** MODIFIED COOLING RATE ***
                    int newOverheat = currentOverheat - 2; // Reduced from 5 to 2 per tick for slower cooldown
                    setOverheat(stack, newOverheat);

                    if (isLocked && newOverheat <= UNLOCK_OVERHEAT_THRESHOLD) {
                        setOverheatLocked(stack, false);
                        setWasOverheatedFlag(stack, false);
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                    } else if (!isLocked && wasFlaggedOverheated && newOverheat < MAX_HEAT / 2) {
                        // This else if is for when it's not locked but was previously overheated, and is now cooling down
                        // Play sound when it cools below half, indicating it's "ready" again after an overheat scare.
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                        setWasOverheatedFlag(stack, false);
                    }
                }
            }
        }
        // Client-side: manage the charge loop sound
        if (level.isClientSide() && currentChargeLoopSoundInstance != null) {
            if (!sel || !p.isUsingItem() || !p.getUseItem().equals(stack) || !isCharging(stack)) {
                Minecraft.getInstance().getSoundManager().stop(currentChargeLoopSoundInstance);
                currentChargeLoopSoundInstance = null;
            }
        }

        CompoundTag tag = stack.getOrCreateTag();
        boolean wasEquippedPreviously = tag.getBoolean(TAG_EQUIPPED_PREVIOUSLY);

        if (sel && !wasEquippedPreviously) {
            if (p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                 level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, true);
        } else if (!sel && wasEquippedPreviously) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        String name = upgraded
            ? getTranslatedMessage("§dPew Pew", "§dPew Pew") // PaP name, remains same as it's a proper noun-like name
            : getTranslatedMessage("§2Pistolet à Plasma", "§2Plasma Pistol"); // Base name (proper noun, remains same)
        
        int currentOverheat = getOverheat(stack);
        if (isOverheatLocked(stack)) {
            name += getTranslatedMessage(" §4(Surchauffé !)", " §4(Overheated!)");
        } else if (currentOverheat > OVERHEAT_THRESHOLD_FOR_DISABLE * 0.75) {
            name += getTranslatedMessage(" §e(Chauffe !)", " §e(Heating Up!)");
        }
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);

        tips.add(Component.literal(getTranslatedMessage("§ePistolet à plasma Covenant", "§eCovenant Plasma Pistol")));
        tips.add(Component.literal(getTranslatedMessage("§eClic-droit : Tir normal (Full-auto)", "§eRight-click: Normal Shot (Full-auto)")));
        tips.add(Component.literal(getTranslatedMessage("§eMaintenir Clic-droit : Charger le tir", "§eHold Right-click: Charge Shot")));
        tips.add(Component.literal(getTranslatedMessage("§eRelâcher (chargé) : Tir Surchargé (dégâts et chaleur augmentent avec la charge)", "§eRelease (charged): Overcharge Shot (damage and heat increase with charge)")));
        
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts normaux PaP : " + String.format("%.1f", PAP_BONUS_DAMAGE_NORMAL),
                "§dPaP Normal Shot Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE_NORMAL)
            )));
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts surchargés max PaP : " + String.format("%.1f", PAP_BONUS_DAMAGE_OVERCHARGE_FULL),
                "§dPaP Max Overcharge Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE_OVERCHARGE_FULL)
            )));
            tips.add(Component.literal(getTranslatedMessage("§dSurchauffe réduite lors du tir.", "§dReduced overheat on firing.")));
        } else {
            tips.add(Component.literal(getTranslatedMessage("§cAttention à la surchauffe !", "§cBeware of overheating!")));
        }

        int currentOverheat = getOverheat(stack);
        tips.add(Component.literal(getTranslatedMessage("§7Surchauffe : ", "§7Overheat: ") + currentOverheat + " / " + MAX_HEAT));
        if (isOverheatLocked(stack)) {
            tips.add(Component.literal(getTranslatedMessage("§cVerrouillé par Surchauffe ! Refroidir à ", "§cOverheat Locked! Cool down to ") + UNLOCK_OVERHEAT_THRESHOLD + getTranslatedMessage(" pour déverrouiller.", " to unlock.")));
        } else if (isCharging(stack) && getChargeTimer(stack) > 0) {
            float chargeProgress = Mth.clamp((float) getChargeTimer(stack) / CHARGE_TIME_TICKS, 0.0f, 1.0f);
            String chargeStatus = getTranslatedMessage("§bChargement : ", "§bCharging: ") + String.format("%.1f", chargeProgress * 100) + "%";
            if (chargeProgress >= 1.0f) {
                 chargeStatus += getTranslatedMessage(" (Max)", " (Max)");
            }
            tips.add(Component.literal(chargeStatus));
        }

        int durability = getDurability(stack);
        int maxDurability = getMaxDurability(stack);
        tips.add(Component.literal(getTranslatedMessage("§7Durabilité : ", "§7Durability: ") + durability + " / " + maxDurability));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
