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
import net.minecraft.world.entity.animal.Animal; // Added for Cherry Cola

import net.mcreator.zombierool.client.CherryReloadAnimationHandler; 
import java.util.List;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;

public class StormWeaponItem extends Item implements ICustomWeapon, IPackAPunchable, IHeadshotWeapon, IOverheatable {

    // --- Overheat & Firing Parameters ---
    private static final int MAX_HEAT = 120; 
    private static final int HEAT_PER_SHOT_NORMAL = 3; // Réduit de 4 à 3
    private static final int HEAT_PER_SHOT_PAP = 1; // **NOUVEAU** pour PaP : Très peu de chaleur
    private static final int COOL_DOWN_PER_TICK = 3; 
    private static final int OVERHEAT_THRESHOLD_FOR_DISABLE = 110; 
    private static final int UNLOCK_OVERHEAT_THRESHOLD = MAX_HEAT / 3; 
    
    private static final int COOLDOWN_TICKS = 2; 

    // --- Damage Parameters ---
    private static final float WEAPON_DAMAGE = 5.0f; 
    private static final float PAP_BONUS_DAMAGE = 8.0f; 
    
    private static final float BASE_HEADSHOT_DAMAGE = 2.0f; 
    private static final float PAP_HEADSHOT_BONUS = 6.0f; 

    // --- Projectile Parameters ---
    private static final float BASE_PROJECTILE_VELOCITY = 3.5f; 
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.1f; 
    private static final float BASE_PROJECTILE_SPREAD = 1.1f; 
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.7f; 

    // --- Recoil Parameters ---
    private static final float BASE_RECOIL_PITCH = 0.8f; 
    private static final float BASE_RECOIL_YAW = 0.5f; 
    private static final float PAP_RECOIL_MULTIPLIER = 0.6f; 

    // --- Durability Parameters (replaces Ammo) ---
    private static final int BASE_MAX_DURABILITY = 500; 
    private static final int PAP_DURABILITY_BONUS = 300; 
    private static final int DURABILITY_DRAIN_PER_SHOT = 1; 

    // --- Sounds ---
    private static final SoundEvent FIRE_SOUND           = ZombieroolModSounds.STORM_RIFLE_FIRE.get(); 
    private static final SoundEvent OVERHEAT_SOUND       = ZombieroolModSounds.PLASMA_PISTOL_OVERHEAT.get(); 
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get(); 
    private static final SoundEvent DRY_FIRE_SOUND       = ZombieroolModSounds.RIFLE_DRY.get(); 

    // --- NBT Tags ---
    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_OVERHEAT = "Overheat";
    private static final String TAG_OVERHEAT_LOCKED = "OverheatLocked";
    private static final String TAG_WAS_OVERHEATED_FLAG = "WasOverheatedFlag";
    private static final String TAG_DURABILITY = "Durability";
    public static final String TAG_ARROW_DAMAGE = "zombierool:arrow_damage";


    public StormWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE)); 
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

    // --- Dynamic Damage & Stats based on PaP status ---
    @Override
    public float getWeaponDamage(ItemStack stack) {
        return isPackAPunched(stack) ? WEAPON_DAMAGE + PAP_BONUS_DAMAGE : WEAPON_DAMAGE;
    }
      
    @Override
    public float getHeadshotBaseDamage(ItemStack stack) {
        return BASE_HEADSHOT_DAMAGE;
    }

    @Override
    public float getHeadshotPapBonusDamage(ItemStack stack) {
        return PAP_HEADSHOT_BONUS;   
    }

    public float getProjectileVelocity(ItemStack stack) {
        return isPackAPunched(stack) ? BASE_PROJECTILE_VELOCITY * PAP_PROJECTILE_VELOCITY_MULTIPLIER : BASE_PROJECTILE_VELOCITY;
    }

    public float getProjectileSpread(ItemStack stack) {
        return isPackAPunched(stack) ? BASE_PROJECTILE_SPREAD * PAP_PROJECTILE_SPREAD_MULTIPLIER : BASE_PROJECTILE_SPREAD;
    }

    public float getRecoilPitch(ItemStack stack) {
        return isPackAPunched(stack) ? BASE_RECOIL_PITCH * PAP_RECOIL_MULTIPLIER : BASE_RECOIL_PITCH;
    }

    public float getRecoilYaw(ItemStack stack) {
        return isPackAPunched(stack) ? BASE_RECOIL_YAW * PAP_RECOIL_MULTIPLIER : BASE_RECOIL_YAW;
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

    public boolean isOverheatLocked(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_OVERHEAT_LOCKED); }
    public void setOverheatLocked(ItemStack stack, boolean locked) { getOrCreateTag(stack).putBoolean(TAG_OVERHEAT_LOCKED, locked); }

    public boolean getWasOverheatedFlag(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_WAS_OVERHEATED_FLAG); }
    public void setWasOverheatedFlag(ItemStack stack, boolean flag) { getOrCreateTag(stack).putBoolean(TAG_WAS_OVERHEATED_FLAG, flag); }

    // --- Durability Management (replaces Ammo) ---
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

    public void drainDurability(ItemStack stack, int amount, Player player, Level level) {
        int current = getDurability(stack);
        setDurability(stack, current - amount);
        if (getDurability(stack) <= 0) {
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            }
        }
    }

    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_OVERHEAT)) tag.putInt(TAG_OVERHEAT, 0);
        if (!tag.contains(TAG_LAST_FIRE_TICK)) tag.putLong(TAG_LAST_FIRE_TICK, 0);
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        if (!tag.contains(TAG_OVERHEAT_LOCKED)) tag.putBoolean(TAG_OVERHEAT_LOCKED, false);
        if (!tag.contains(TAG_WAS_OVERHEATED_FLAG)) tag.putBoolean(TAG_WAS_OVERHEATED_FLAG, false);
        if (!tag.contains(TAG_DURABILITY)) {
            tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
        }
    }

    @Override public UseAnim getUseAnimation(ItemStack s) { return isOverheatLocked(s) ? UseAnim.NONE : UseAnim.BOW; }
    @Override public int     getUseDuration(ItemStack s)  { return 72000; } 

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity ent, ItemStack stack, int count) {
        if (!(ent instanceof Player player) || level.isClientSide) return;
        initializeIfNeeded(stack);

        if (isOverheatLocked(stack) || getDurability(stack) <= 0) {
            player.stopUsingItem(); 
            if (!level.isClientSide && getDurability(stack) <= 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            }
            return;
        }

        long currentTick = level.getGameTime();
        long lastFireTick = getLastFireTick(stack);

        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return; 
        }
        
        // Détermine le coût en chaleur du tir
        int actualHeatCost = isPackAPunched(stack) ? HEAT_PER_SHOT_PAP : HEAT_PER_SHOT_NORMAL;

        // Vérifie si le tir va provoquer une surchauffe ou manquer de durabilité
        if (getOverheat(stack) + actualHeatCost > MAX_HEAT) { // Utilise actualHeatCost
            startOverheat(stack, player, level);
            return;
        }
        if (getDurability(stack) < DURABILITY_DRAIN_PER_SHOT) {
            drainDurability(stack, DURABILITY_DRAIN_PER_SHOT, player, level); 
            player.stopUsingItem();
            return;
        }

        // Si tout est bon, tire
        fireProjectile(stack, player, level);
        setOverheat(stack, getOverheat(stack) + actualHeatCost); // Utilise actualHeatCost
        drainDurability(stack, DURABILITY_DRAIN_PER_SHOT, player, level);
        setLastFireTick(stack, currentTick);

        if (getOverheat(stack) >= MAX_HEAT) {
            startOverheat(stack, player, level);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (livingEntity instanceof Player player) {
            player.stopUsingItem();
        }
    }

    private void fireProjectile(ItemStack stack, Player player, Level level) {
        if (level.isClientSide) return;

        Vec3 start = player.getEyePosition(1F);
        Vec3 dir   = player.getViewVector(1F);

        float projectileVelocity = getProjectileVelocity(stack);
        float projectileSpread = getProjectileSpread(stack);
        float recoilPitch = getRecoilPitch(stack);
        float recoilYaw = getRecoilYaw(stack);

        Arrow arrow = new Arrow(level, player);
        arrow.setOwner(player);
        arrow.getPersistentData().putUUID("shooterUUID", player.getUUID());
        arrow.setPos(start.x, start.y, start.z);
        arrow.setSilent(true);
        arrow.setNoGravity(true);
        arrow.pickup = Pickup.DISALLOWED;
        arrow.getPersistentData().putBoolean("zombierool:invisible", true);
        arrow.getPersistentData().putBoolean("zombierool:small", true); 
        arrow.setInvisible(true);
        arrow.setBaseDamage(0.0D); 
        arrow.getPersistentData().putFloat(TAG_ARROW_DAMAGE, getWeaponDamage(stack)); 

        arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread);
          
        level.addFreshEntity(arrow);

        if (player instanceof ServerPlayer serverPlayer) {
            float actualPitchRecoil = recoilPitch;
            float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
        }

        // Reduced volume to mitigate overlapping sounds for high rate-of-fire weapons.
        // If the sound still "lingers", the sound asset itself might be too long or configured to loop.
        level.playSound(null,
            player.getX(), player.getY(), player.getZ(),
            FIRE_SOUND,
            SoundSource.PLAYERS, 0.5f, 1f // Volume adjusted from 0.7f to 0.5f
        );
    }

    private void startOverheat(ItemStack stack, Player player, Level level) {
        if (!isOverheatLocked(stack)) {
            setOverheatLocked(stack, true);
            setOverheat(stack, MAX_HEAT); 
            player.stopUsingItem(); 
            setWasOverheatedFlag(stack, true);
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), OVERHEAT_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
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
            
            if (!isPlayerCurrentlyUsing || isLocked) {
                if (currentOverheat > 0) {
                    int newOverheat = currentOverheat - COOL_DOWN_PER_TICK; 
                    setOverheat(stack, newOverheat);

                    if (isLocked && newOverheat <= UNLOCK_OVERHEAT_THRESHOLD) {
                        setOverheatLocked(stack, false);
                        setWasOverheatedFlag(stack, false);
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f); 
                    } else if (!isLocked && wasFlaggedOverheated && newOverheat < MAX_HEAT / 2) {
                        level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                        setWasOverheatedFlag(stack, false);
                    }
                }
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
            ? getTranslatedMessage("§3Rainmaker", "§3Rainmaker") // Translated PaP name
            : getTranslatedMessage("§2Fusil d'Assaut Plasma", "§2Plasma Assault Rifle"); // Base name
        
        int currentOverheat = getOverheat(stack);
        if (isOverheatLocked(stack)) {
            name += getTranslatedMessage(" §4(Surchauffée !)", " §4(Overheated!)");
        } else if (currentOverheat > OVERHEAT_THRESHOLD_FOR_DISABLE * 0.75) {
            name += getTranslatedMessage(" §e(Chauffe !)", " §e(Heating Up!)");
        }
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);

        tips.add(Component.literal(getTranslatedMessage("§eFusil d'assaut Covenant à plasma.", "§eCovenant Plasma Assault Rifle.")));
        tips.add(Component.literal(getTranslatedMessage("§eTir automatique à haute cadence.", "§eHigh rate of fire automatic.")));
        tips.add(Component.literal(getTranslatedMessage("§eUtilise la durabilité comme énergie.", "§eUses durability as energy.")));
        
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts PaP : " + String.format("%.1f", PAP_BONUS_DAMAGE),
                "§dPaP Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE)
            )));
            tips.add(Component.literal(getTranslatedMessage(
                "§dMultiplicateur Headshot PaP : x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE + PAP_HEADSHOT_BONUS), // Summing base and bonus for total multiplier
                "§dPaP Headshot Multiplier: x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE + PAP_HEADSHOT_BONUS)
            )));
            tips.add(Component.literal(getTranslatedMessage("§dDurabilité augmentée et surchauffe très réduite !", "§dIncreased durability and greatly reduced overheat!"))); // Translated text
        } else {
            tips.add(Component.literal(getTranslatedMessage("§cGare à la surchauffe et à la batterie faible !", "§cBeware of overheating and low battery!")));
        }

        int currentOverheat = getOverheat(stack);
        tips.add(Component.literal(getTranslatedMessage("§7Surchauffe : ", "§7Overheat: ") + currentOverheat + " / " + MAX_HEAT));
        if (isOverheatLocked(stack)) {
            tips.add(Component.literal(getTranslatedMessage("§cVerrouillée par Surchauffe ! Refroidir à ", "§cOverheat Locked! Cool down to ") + UNLOCK_OVERHEAT_THRESHOLD + getTranslatedMessage(" pour déverrouiller.", " to unlock.")));
        }

        int durability = getDurability(stack);
        int maxDurability = getMaxDurability(stack);
        tips.add(Component.literal(getTranslatedMessage("§7Énergie (Durabilité) : ", "§7Energy (Durability): ") + durability + " / " + maxDurability));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
