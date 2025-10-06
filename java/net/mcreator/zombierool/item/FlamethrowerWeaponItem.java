package net.mcreator.zombierool.item;


import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.api.IOverheatable;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.resources.ResourceLocation; 
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft; 
import net.minecraft.client.resources.sounds.SimpleSoundInstance; 
import net.minecraft.client.resources.sounds.SoundInstance; 
import net.minecraft.util.RandomSource; 
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance; 
import net.minecraft.ChatFormatting; // Import for ChatFormatting

import java.util.List;
import java.util.Random; 

import net.mcreator.zombierool.PointManager; // Import de PointManager

public class FlamethrowerWeaponItem extends Item implements ICustomWeapon, IPackAPunchable, IOverheatable {

    // --- Translation Helper Methods ---
    
    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        // This should run on the client side only (e.g., in getName/appendHoverText)
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }
    
    // ------------------------------------

    // Flamethrower Specific Parameters
    private static final int MAX_OVERHEAT = 1000; // Inchangé
    private static final int OVERHEAT_PER_TICK = 15; // Moins de surchauffe par tick (Ancien : 18)
    private static final int COOL_DOWN_PER_TICK = 12; // Refroidissement plus rapide (Ancien : 10)
    private static final int OVERHEAT_THRESHOLD_FOR_DISABLE = 990; // Inchangé
    private static final int COOL_DOWN_THRESHOLD_FOR_READY_SOUND = 200; // Inchangé
    private static final int UNLOCK_OVERHEAT_THRESHOLD = MAX_OVERHEAT / 8; // Surchauffe maximale plus élevée pour le son (Ancien : MAX_OVERHEAT / 10)

    private static final int COOLDOWN_TICKS = 1; // Inchangé (cadence de tir déjà max)

    // Flame Damage Parameters
    private static final float FLAME_BASE_DAMAGE = 3.0f; // Dégâts de base augmentés (Ancien : 2.0f)
    private static final float FLAME_PERCENTAGE_DAMAGE = 0.015f; // Dégâts par pourcentage augmentés (Ancien : 0.01f)
    private static final float PAP_BONUS_FLAT_DAMAGE = 5.0f; // Gros bonus de dégâts PaP (Ancien : 3.0f)
    private static final float PAP_BONUS_PERCENTAGE_DAMAGE = 0.008f; // Gros bonus de pourcentage PaP (Ancien : 0.005f)

    // Flamethrower range and particle parameters
    public static class FlameParticleData {
        public static final double RADIUS = 1.75; // Rayon des flammes augmenté (Ancien : 1.5)
        public static final double DISTANCE = 7.5; // Portée augmentée (Ancien : 6.0)
        public static final int PARTICLE_COUNT = 4; // Plus de particules par tick (Ancien : 3)
        public static final float PARTICLE_VELOCITY = 0.6f; // Vitesse des particules augmentée (Ancien : 0.5f)
        public static final float PARTICLE_SPREAD = 0.3f; // Légèrement plus de dispersion pour couvrir une zone plus large (Ancien : 0.2f)
    }

    // Overheat parameters (replaces recoil for flamethrower)
    private static final float BASE_RECOIL_PITCH = 0.0f; // Inchangé
    private static final float BASE_RECOIL_YAW = 0.0f; // Inchangé

    // Sounds
    private static final SoundEvent FIRE_LOOP_SOUND = ZombieroolModSounds.FLAMETHROWER_LOOP.get();
    private static final SoundEvent FIRE_START_SOUND = ZombieroolModSounds.FLAMETHROWER_START.get();
    private static final SoundEvent FIRE_OFF_SOUND = ZombieroolModSounds.FLAMETHROWER_OFF.get();
    private static final SoundEvent FIRE_ON_SOUND = ZombieroolModSounds.FLAMETHROWER_ON.get();
    private static final SoundEvent OVERHEAT_SOUND = ZombieroolModSounds.FLAMETHROWER_OVERHEAT.get();
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    // NBT Tags
    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_OVERHEAT = "Overheat";
    private static final String TAG_IS_FIRING = "IsFiring";
    private static final String TAG_WAS_OVERHEATED = "WasOverheated";
    private static final String TAG_OVERHEAT_LOCKED = "OverheatLocked"; 

    // --- Cherry Cola Effects (retained from M16A4) ---
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }
    // --- End Cherry Cola Effects ---

    // ** New: Reference to the currently playing loop sound on the client side **
    private static AbstractTickableSoundInstance currentLoopSoundInstance = null;

    /**
     * Custom Looping Sound Instance for the Flamethrower.
     * This class extends AbstractTickableSoundInstance to provide a persistent, looping sound
     * that automatically stops when the player is no longer using the item or the player entity is removed.
     */
    public static class LoopingFlamethrowerSound extends AbstractTickableSoundInstance {
        private final Player player;

        public LoopingFlamethrowerSound(SoundEvent soundEvent, Player player) {
            // Use SoundInstance.createUnseededRandom() for the RandomSource argument
            super(soundEvent, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true; // Mark as looping
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.volume = 0.7f; // Set default volume
            this.pitch = 1.0f;  // Set default pitch
            this.relative = true; // Sound follows the player
            this.attenuation = SoundInstance.Attenuation.NONE; // No attenuation, heard equally regardless of distance
        }

        @Override
        public void tick() {
            // Stop the sound if the player is removed from the world or stops using the item
            if (player.isRemoved() || !player.isUsingItem()) {
                this.stop(); // Stops the sound and sets it to be removed by the sound manager
            }
            // You can add more complex logic here if needed, e.g., adjusting volume based on overheat
        }
    }

    public FlamethrowerWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public void applyPackAPunch(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG_PAP, true);
    }

    @Override
    public boolean isPackAPunched(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_PAP);
    }

    public float getWeaponDamage(ItemStack stack) {
        return 0.0f; 
    }

    public float getProjectileVelocity(ItemStack stack) {
        return 0.0f; 
    }

    public float getProjectileSpread(ItemStack stack) {
        return FlameParticleData.PARTICLE_SPREAD; 
    }

    public float getRecoilPitch(ItemStack stack) {
        return BASE_RECOIL_PITCH;
    }

    public float getRecoilYaw(ItemStack stack) {
        return BASE_RECOIL_YAW;
    }

    @Override
    public int getOverheat(ItemStack stack) {
        return getOrCreateTag(stack).getInt(TAG_OVERHEAT);
    }

    @Override
    public void setOverheat(ItemStack stack, int overheat) {
        getOrCreateTag(stack).putInt(TAG_OVERHEAT, Mth.clamp(overheat, 0, MAX_OVERHEAT));
    }

    @Override
    public int getMaxOverheat() {
        return MAX_OVERHEAT;
    }

    private CompoundTag getOrCreateTag(ItemStack s) {
        if (!s.hasTag()) s.setTag(new CompoundTag());
        return s.getTag();
    }

    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    public boolean isFiring(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_IS_FIRING); }
    public void setFiring(ItemStack stack, boolean firing) { getOrCreateTag(stack).putBoolean(TAG_IS_FIRING, firing); }

    public boolean wasOverheated(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_WAS_OVERHEATED); }
    public void setWasOverheated(ItemStack stack, boolean wasOverheated) { getOrCreateTag(stack).putBoolean(TAG_WAS_OVERHEATED, wasOverheated); } // CORRIGÉ : TAG_WAS_WAS_OVERHEATED -> TAG_WAS_OVERHEATED

    public boolean isOverheatLocked(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_OVERHEAT_LOCKED); }
    public void setOverheatLocked(ItemStack stack, boolean locked) { getOrCreateTag(stack).putBoolean(TAG_OVERHEAT_LOCKED, locked); }


    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_OVERHEAT)) {
            stack.setTag(new CompoundTag());
            setOverheat(stack, 0);
            tag = stack.getTag();
        }
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_LAST_FIRE_TICK)) {
            tag.putLong(TAG_LAST_FIRE_TICK, 0);
        }
        if (!tag.contains(TAG_IS_FIRING)) {
            tag.putBoolean(TAG_IS_FIRING, false);
        }
        if (!tag.contains(TAG_WAS_OVERHEATED)) {
            tag.putBoolean(TAG_WAS_OVERHEATED, false);
        }
        if (!tag.contains(TAG_OVERHEAT_LOCKED)) {
            tag.putBoolean(TAG_OVERHEAT_LOCKED, false);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) { return UseAnim.NONE; }
    @Override
    public int getUseDuration(ItemStack s) { return 72000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        initializeIfNeeded(stack); 

        // Check if the weapon is currently locked due to overheat
        if (isOverheatLocked(stack)) {
            return InteractionResultHolder.fail(stack); // Prevent usage if locked
        }
        
        // Play FIRE_START_SOUND only once when player STARTS using the item (server-side)
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), FIRE_START_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else {
            // Client side: Start the loop sound if it's not already playing
            if (currentLoopSoundInstance == null || !Minecraft.getInstance().getSoundManager().isActive(currentLoopSoundInstance)) {
                currentLoopSoundInstance = new LoopingFlamethrowerSound(FIRE_LOOP_SOUND, player);
                Minecraft.getInstance().getSoundManager().play(currentLoopSoundInstance);
            }
        }
        
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity ent, ItemStack stack, int count) {
        if (!(ent instanceof Player player)) return;

        initializeIfNeeded(stack);

        long lastFireTick = getLastFireTick(stack);
        long currentTick = level.getGameTime();
        int currentOverheat = getOverheat(stack);

        // If the weapon is locked due to overheat, stop using it and return
        if (isOverheatLocked(stack)) {
            player.stopUsingItem();
            setFiring(stack, false);
            // Client side: Stop the loop sound if the player is locked
            if (level.isClientSide() && currentLoopSoundInstance != null) {
                Minecraft.getInstance().getSoundManager().stop(currentLoopSoundInstance);
                currentLoopSoundInstance = null; // Clear the reference
            }
            return; 
        }

        // Set firing state when onUseTick begins (only once when starting use)
        if (!isFiring(stack)) { 
            setFiring(stack, true); 
            // The FIRE_START_SOUND is now played in the use() method.
            // This is for the ready sound when coming out of overheat.
            if (wasOverheated(stack) && currentOverheat < COOL_DOWN_THRESHOLD_FOR_READY_SOUND) {
                if (!level.isClientSide()) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), FIRE_ON_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                setWasOverheated(stack, false);
            }
        }

        // Overheat check: if currentOverheat reaches or exceeds threshold, lock the weapon
        if (currentOverheat >= OVERHEAT_THRESHOLD_FOR_DISABLE) {
            if (!wasOverheated(stack) && !level.isClientSide()) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), OVERHEAT_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), FIRE_OFF_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                setWasOverheated(stack, true);
            }
            setOverheatLocked(stack, true); // Lock the weapon
            player.stopUsingItem(); // Stop using the item
            setFiring(stack, false); // Explicitly set firing to false
            // Client side: Stop the loop sound if the player overheats
            if (level.isClientSide() && currentLoopSoundInstance != null) {
                Minecraft.getInstance().getSoundManager().stop(currentLoopSoundInstance);
                currentLoopSoundInstance = null; // Clear the reference
            }
            return; 
        }

        // --- Damage and Overheat Application (server-side, throttled by COOLDOWN_TICKS) ---
        if (!level.isClientSide()) {
            if (currentTick - lastFireTick >= COOLDOWN_TICKS) { 
                int overheatIncrease = OVERHEAT_PER_TICK;
                if (isPackAPunched(stack)) {
                    overheatIncrease = (int)(overheatIncrease * 0.75f);
                }
                setOverheat(stack, currentOverheat + overheatIncrease);

                float flatDamage = FLAME_BASE_DAMAGE;
                float percentageDamage = FLAME_PERCENTAGE_DAMAGE;
                if (isPackAPunched(stack)) {
                    flatDamage += PAP_BONUS_FLAT_DAMAGE;
                    percentageDamage += PAP_BONUS_PERCENTAGE_DAMAGE;
                }

                Vec3 playerLook = player.getViewVector(1.0F);
                Vec3 flameStart = player.getEyePosition(1.0F).add(playerLook.scale(0.5));
                Vec3 flameEnd = flameStart.add(playerLook.scale(FlameParticleData.DISTANCE));

                AABB flameArea = new AABB(flameStart, flameEnd).inflate(FlameParticleData.RADIUS);

                List<LivingEntity> hitEntities = level.getEntitiesOfClass(LivingEntity.class, flameArea,
                    target -> target != player && target.isAlive() && !(target instanceof Player)
                              && !(target instanceof TamableAnimal && ((TamableAnimal)target).isTame())
                              && target.getBoundingBox().intersects(flameArea)
                              && player.hasLineOfSight(target)
                );

                for (LivingEntity target : hitEntities) {
                    float totalDamage = flatDamage + (target.getMaxHealth() * percentageDamage);
                    target.hurt(level.damageSources().playerAttack(player), totalDamage);
                    target.setSecondsOnFire(8);

                    if (target instanceof Monster) {
                        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 10, 0));
                        // AJOUTÉ : Gagner 10 points si la cible est un monstre (par exemple, un zombie)
                        PointManager.modifyScore(player, 10); 
                    }
                }
                setLastFireTick(stack, currentTick); 
            }
        }

        // --- Particle Generation (client-side, happens every tick while using) ---
        if (level.isClientSide()) {
            Random random = new Random(); 
            for (int i = 0; i < FlameParticleData.PARTICLE_COUNT; i++) {
                Vec3 playerLook = player.getViewVector(1.0F);
                Vec3 startPos = player.getEyePosition(1.0F).add(playerLook.scale(0.5));

                double dX = playerLook.x + (random.nextDouble() - 0.5) * FlameParticleData.PARTICLE_SPREAD;
                double dY = playerLook.y + (random.nextDouble() - 0.5) * FlameParticleData.PARTICLE_SPREAD;
                double dZ = playerLook.z + (random.nextDouble() - 0.5) * FlameParticleData.PARTICLE_SPREAD;

                level.addParticle(ParticleTypes.FLAME,
                    startPos.x, startPos.y, startPos.z,
                    dX * FlameParticleData.PARTICLE_VELOCITY,
                    dY * FlameParticleData.PARTICLE_VELOCITY,
                    dZ * FlameParticleData.PARTICLE_VELOCITY
                );
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        super.releaseUsing(stack, level, livingEntity, timeLeft);
        if (livingEntity instanceof Player player) {
            // Only play OFF sound and reset firing state on server side if not locked
            if (isFiring(stack) && !level.isClientSide() && !isOverheatLocked(stack)) { 
                level.playSound(null, player.getX(), player.getY(), player.getZ(), FIRE_OFF_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            setFiring(stack, false); 
            
            // Client side: Stop the loop sound at the end of use
            if (level.isClientSide() && currentLoopSoundInstance != null) {
                Minecraft.getInstance().getSoundManager().stop(currentLoopSoundInstance);
                currentLoopSoundInstance = null; // Clear the reference
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);

        int currentOverheat = getOverheat(stack);
        boolean isFiringState = isFiring(stack); 
        boolean wasLocked = isOverheatLocked(stack); 

        if (!level.isClientSide()) {
            boolean isPlayerCurrentlyUsing = sel && p.isUsingItem() && p.getUseItem().equals(stack);

            // If the weapon is marked as "firing" but the player stopped using it, or it overheated,
            // set firing to false and potentially play the OFF sound.
            if (isFiringState && (!isPlayerCurrentlyUsing || currentOverheat >= MAX_OVERHEAT)) {
                if (isFiringState && !isPlayerCurrentlyUsing && !wasLocked) {
                     level.playSound(null, p.getX(), p.getY(), p.getZ(), FIRE_OFF_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                setFiring(stack, false);
            }

            // --- Cooling down logic ---
            // Always cool down if not using, or if it's locked.
            if (!isPlayerCurrentlyUsing || currentOverheat >= MAX_OVERHEAT) {
                if (currentOverheat > 0) {
                    int newOverheat = currentOverheat - COOL_DOWN_PER_TICK;
                    setOverheat(stack, newOverheat);

                    // If weapon was locked due to overheat, check if it's cooled enough to unlock
                    if (wasLocked && newOverheat <= UNLOCK_OVERHEAT_THRESHOLD) {
                        setOverheatLocked(stack, false); // Unlock the weapon
                        setWasOverheated(stack, false); // Reset wasOverheated state
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), FIRE_ON_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f); // Play ready sound
                    } else if (!wasLocked && wasOverheated(stack) && newOverheat < COOL_DOWN_THRESHOLD_FOR_READY_SOUND) {
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), FIRE_ON_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                        setWasOverheated(stack, false);
                    }
                }
            }
        }
        // Client side: Stop the loop sound if the weapon is no longer selected or the player is not actively using it
        // Note: `sel` indicates if the item is in the player's hand
        if (level.isClientSide() && currentLoopSoundInstance != null && (!sel || !p.isUsingItem() || !p.getUseItem().equals(stack))) {
             Minecraft.getInstance().getSoundManager().stop(currentLoopSoundInstance);
             currentLoopSoundInstance = null; // Clear the reference
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
        
        // CORRECTION MAJEURE: Nous manipulons des Components/MutableComponents directement.
        // On ne fait plus .getString() pour éviter de casser la mise à jour dynamique.
        MutableComponent nameComponent = upgraded 
            ? getTranslatedComponent("§6Le Carbonisateur", "§6The Carbonizer")
            : getTranslatedComponent("§4Lance-Flammes", "§4Flamethrower");
        
        int currentOverheat = getOverheat(stack);
        
        if (isOverheatLocked(stack)) {
            // Append the locked status component
            nameComponent.append(getTranslatedComponent(" §8(Surchauffé !)", " §8(Overheated!)"));
        } else if (currentOverheat > OVERHEAT_THRESHOLD_FOR_DISABLE * 0.75) { 
            // Append the heating up status component
            nameComponent.append(getTranslatedComponent(" §e(Chauffe !)", " §e(Heating Up!)"));
        }
        
        // Retourne le Component construit
        return nameComponent;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        
        // Tip 1: Weapon Type
        tips.add(getTranslatedComponent("§cArme Spéciale : Lance-Flammes", "§cSpecial Weapon: Flamethrower").withStyle(ChatFormatting.RED));

        // Tip 2: Damage Description
        tips.add(getTranslatedComponent("§7Dégâts: Brûle les hordes avec le feu et les étourdit brièvement.", "§7Damage: Burns hordes with fire and briefly stuns them.").withStyle(ChatFormatting.GRAY));
        
        // Tip 3: DoT Percentage
        MutableComponent dotTip = getTranslatedComponent(
            "§7Applique une brûlure continue et des dégâts de ", 
            "§7Applies continuous burning and damage of "
        ).withStyle(ChatFormatting.GRAY);
        dotTip.append(Component.literal(String.format("%.1f", FLAME_PERCENTAGE_DAMAGE * 100)).withStyle(ChatFormatting.WHITE));
        dotTip.append(getTranslatedComponent("%% PV max.", "%% max HP.").withStyle(ChatFormatting.GRAY));
        tips.add(dotTip);


        if (upgraded) {
            // PaP Tip 1
            tips.add(getTranslatedComponent("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch").withStyle(ChatFormatting.LIGHT_PURPLE));
            
            // PaP Tip 2: Bonus Damage
            MutableComponent damageTip = getTranslatedComponent(
                "§dDégâts supplémentaires : ", 
                "§dBonus Damage: "
            ).withStyle(ChatFormatting.LIGHT_PURPLE);
            damageTip.append(Component.literal(String.format("%.1f", PAP_BONUS_FLAT_DAMAGE)).withStyle(ChatFormatting.WHITE));
            damageTip.append(Component.literal(" + ").withStyle(ChatFormatting.WHITE));
            damageTip.append(Component.literal(String.format("%.1f", PAP_BONUS_PERCENTAGE_DAMAGE * 100)).withStyle(ChatFormatting.WHITE));
            damageTip.append(getTranslatedComponent("%% PV max", "%% max HP").withStyle(ChatFormatting.LIGHT_PURPLE));
            tips.add(damageTip);

            // PaP Tip 3: Overheat reduction
            tips.add(getTranslatedComponent("§dSurchauffe réduite lors du tir.", "§dReduced overheat while firing.").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            // Warning Tip
            tips.add(getTranslatedComponent("§cAttention à la surchauffe !", "§cWatch out for overheating!").withStyle(ChatFormatting.RED));
        }

        // Overheat status
        MutableComponent overheatTip = getTranslatedComponent("§7Surchauffe: ", "§7Overheat: ").withStyle(ChatFormatting.GRAY);
        overheatTip.append(Component.literal(getOverheat(stack) + "/" + MAX_OVERHEAT).withStyle(ChatFormatting.WHITE));
        tips.add(overheatTip);
        
        // Overheat lock status
        if (isOverheatLocked(stack)) {
            MutableComponent lockTip = getTranslatedComponent(
                "§cVerrouillé par Surchauffe ! Refroidir à " + UNLOCK_OVERHEAT_THRESHOLD + " pour déverrouiller.", 
                "§cLocked by Overheat! Cool down to " + UNLOCK_OVERHEAT_THRESHOLD + " to unlock."
            ).withStyle(ChatFormatting.RED);
            tips.add(lockTip);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}

