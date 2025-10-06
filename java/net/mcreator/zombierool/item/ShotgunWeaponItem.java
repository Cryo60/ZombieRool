package net.mcreator.zombierool.item;

import com.mojang.datafixers.util.Pair;
import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.api.IHeadshotWeapon;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth; 

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import net.minecraft.util.RandomSource; 

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class ShotgunWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    // --- Ammo & Reloading Parameters ---
    private static final int MAX_AMMO = 6; 
    private static final int MAX_RESERVE = 48; 
    private static final int COOLDOWN_TICKS = 20; 
    // Changement ici : le temps de rechargement est maintenant par cartouche
    private static final int RELOAD_TIME_PER_SHELL = 10; // 0.5 seconde par cartouche insérée (identique à l'ancien RELOAD_INSERT_SHELL_TICKS)

    private static final int PUMP_SOUND_DURATION = 10; 
    private static final int MIN_PUMP_FIRE_DELAY_TICKS = PUMP_SOUND_DURATION; 


    // --- Damage Parameters ---
    private static final float WEAPON_DAMAGE_PER_PELLET = 18.0f; 
    private static final int NUM_PELLETS = 10; 
    private static final float TOTAL_BASE_DAMAGE = WEAPON_DAMAGE_PER_PELLET * NUM_PELLETS; 
    
    private static final float PAP_BONUS_DAMAGE_PER_PELLET = 12.0f; 
    private static final float TOTAL_PAP_BONUS_DAMAGE = PAP_BONUS_DAMAGE_PER_PELLET * NUM_PELLETS; 
    
    private static final float BASE_HEADSHOT_DAMAGE = 1.5f; 
    private static final float PAP_HEADSHOT_BONUS = 0.5f; 
    
    // Paramètres de tir (projectile invisible)
    private static final float BASE_PROJECTILE_VELOCITY = 1.8f; 
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.2f; 
    private static final float BASE_PROJECTILE_SPREAD = 4.5f; 
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.5f; 

    // Paramètres de Recul
    private static final float BASE_RECOIL_PITCH = 4.0f; 
    private static final float BASE_RECOIL_YAW = 0.8f; 
    private static final float PAP_RECOIL_MULTIPLIER = 0.4f; 

    // Paramètres de réduction de dégâts à distance (pour un plomb)
    private static final float EFFECTIVE_RANGE_SHORT = 2.0f; 
    private static final float EFFECTIVE_RANGE_MEDIUM = 4.0f; 
    private static final float MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE = 0.1f; 
    private static final float EFFECTIVE_RANGE_LONG = 6.0f; 

    // --- Sounds ---
    private static final SoundEvent FIRE_SOUND          = ZombieroolModSounds.SHOTGUN_FIRE.get(); 
    private static final SoundEvent RELOAD_SOUND        = ZombieroolModSounds.SHOTGUN_RELOADING.get(); 
    private static final SoundEvent PUMP_SOUND          = ZombieroolModSounds.SHOTGUN_PUMP.get();
    private static final SoundEvent INSERT_SHELL_SOUND  = ZombieroolModSounds.TRENCH_GUN_INSERT.get(); 
    private static final SoundEvent DRY_FIRE_SOUND      = ZombieroolModSounds.RIFLE_DRY.get(); 
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    // --- NBT Tags ---
    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_PUMP_TIMER = "PumpTimer"; 
    private static final String TAG_IS_RELOADING = "IsReloading";
    private static final String TAG_RELOAD_TIMER = "ReloadTimer"; 
    // Suppression de TAG_RELOAD_INSERT_PHASE car la logique change

    public ShotgunWeaponItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); 
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
        stack.getOrCreateTag().putBoolean(TAG_PAP, true);
        setAmmo(stack, MAX_AMMO); 
        setReserve(stack, getMaxReserve()); 
    }

    @Override
    public boolean isPackAPunched(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_PAP);
    }

    // --- Dynamic Damage & Stats based on PaP status ---
    @Override
    public float getWeaponDamage(ItemStack stack) {
        return isPackAPunched(stack)
            ? TOTAL_BASE_DAMAGE + TOTAL_PAP_BONUS_DAMAGE
            : TOTAL_BASE_DAMAGE;
    }
    
    public float getDamagePerPellet(ItemStack stack) {
        return isPackAPunched(stack)
            ? WEAPON_DAMAGE_PER_PELLET + PAP_BONUS_DAMAGE_PER_PELLET
            : WEAPON_DAMAGE_PER_PELLET;
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
        return isPackAPunched(stack)
            ? BASE_PROJECTILE_VELOCITY * PAP_PROJECTILE_VELOCITY_MULTIPLIER
            : BASE_PROJECTILE_VELOCITY;
    }

    public float getProjectileSpread(ItemStack stack) {
        return isPackAPunched(stack)
            ? BASE_PROJECTILE_SPREAD * PAP_PROJECTILE_SPREAD_MULTIPLIER
            : BASE_PROJECTILE_SPREAD;
    }

    public float getRecoilPitch(ItemStack stack) {
        return isPackAPunched(stack)
            ? BASE_RECOIL_PITCH * PAP_RECOIL_MULTIPLIER
            : BASE_RECOIL_PITCH;
    }

    public float getRecoilYaw(ItemStack stack) {
        return isPackAPunched(stack)
            ? BASE_RECOIL_YAW * PAP_RECOIL_MULTIPLIER
            : BASE_RECOIL_YAW;
    }

    // --- NBT Management ---
    private CompoundTag getOrCreateTag(ItemStack s) {
        if (!s.hasTag()) s.setTag(new CompoundTag());
        return s.getTag();
    }

    @Override public int  getAmmo(ItemStack s)           { return getOrCreateTag(s).getInt("Ammo"); }
    @Override public void setAmmo(ItemStack s, int a)    { getOrCreateTag(s).putInt("Ammo", Mth.clamp(a, 0, MAX_AMMO)); } 
    @Override public int  getReserve(ItemStack s)        { return getOrCreateTag(s).getInt("Reserve"); }
    @Override public void setReserve(ItemStack s, int r) { getOrCreateTag(s).putInt("Reserve", Mth.clamp(r, 0, MAX_RESERVE)); } 
    
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt(TAG_RELOAD_TIMER); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt(TAG_RELOAD_TIMER, t); }
    public boolean isReloading(ItemStack s) { return getOrCreateTag(s).getBoolean(TAG_IS_RELOADING); }
    public void setReloading(ItemStack s, boolean reloading) { getOrCreateTag(s).putBoolean(TAG_IS_RELOADING, reloading); }
    // Anciennement public int getReloadInsertPhase(ItemStack s) { return getOrCreateTag(s).getInt(TAG_RELOAD_INSERT_PHASE); }
    // Anciennement public void setReloadInsertPhase(ItemStack s, int phase) { getOrCreateTag(s).putInt(TAG_RELOAD_INSERT_PHASE, phase); }


    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }
    public int getPumpTimer(ItemStack stack) { return getOrCreateTag(stack).getInt(TAG_PUMP_TIMER); }
    public void setPumpTimer(ItemStack stack, int timer) { getOrCreateTag(stack).putInt(TAG_PUMP_TIMER, timer); }

    @Override public int getMaxAmmo()    { return MAX_AMMO; }
    @Override public int getMaxReserve() { return MAX_RESERVE; }

    @Override
    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("Ammo")) { 
            setAmmo(stack, MAX_AMMO);
            setReserve(stack, MAX_RESERVE);
            setReloadTimer(stack, 0);
            setReloading(stack, false);
            // Suppression de l'initialisation de TAG_RELOAD_INSERT_PHASE
        }
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_LAST_FIRE_TICK)) {
            tag.putLong(TAG_LAST_FIRE_TICK, 0);
        }
        if (!tag.contains(TAG_PUMP_TIMER)) {
            tag.putInt(TAG_PUMP_TIMER, 0);
        }
        if (!tag.contains(TAG_IS_RELOADING)) { 
            tag.putBoolean(TAG_IS_RELOADING, false);
        }
        if (!tag.contains(TAG_RELOAD_TIMER)) {
            tag.putInt(TAG_RELOAD_TIMER, 0);
        }
        // Suppression de l'initialisation de TAG_RELOAD_INSERT_PHASE
    }

    @Override
    public void startReload(ItemStack stack, Player player) {
        initializeIfNeeded(stack);

        if (isReloading(stack) || getReserve(stack) == 0 || getAmmo(stack) == MAX_AMMO) {
            return; 
        }

        if (!player.level().isClientSide) {
            setReloading(stack, true);
            setReloadTimer(stack, RELOAD_TIME_PER_SHELL); // Lance le timer pour la première cartouche
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), RELOAD_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }
    
    @Override
    public void tickReload(ItemStack stack, Player player, Level level) {
        // La logique de rechargement est maintenant principalement dans inventoryTick
        // Mais cette méthode doit exister car l'interface IReloadable l'exige.
    }

    private SoundEvent getFireSound(ItemStack stack) {
        return FIRE_SOUND;
    }

    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.NONE; }
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

        long currentTick = level.getGameTime();
        long lastFireTick = getLastFireTick(stack);
        int pumpTimer = getPumpTimer(stack);

        // Si le joueur est en train de recharger, un clic sur la souris interrompt le rechargement et tire
        if (isReloading(stack) && getAmmo(stack) > 0 && pumpTimer <= 0) { // S'assurer que le pump timer est à 0
            setReloading(stack, false);
            setReloadTimer(stack, 0); 
            // Procède au tir normalement
        }

        if (pumpTimer > 0) {
            return;
        }

        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return;
        }

        // Si pas de munitions, mais de la réserve, tente de recharger une balle
        if (getAmmo(stack) == 0 && getReserve(stack) > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            startReload(stack, player); // Lance le rechargement d'une seule balle
            return;
        }
        
        if (getAmmo(stack) == 0 && getReserve(stack) == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            return;
        }

        if (getAmmo(stack) > 0) {
            Vec3 start = player.getEyePosition(1F);
            Vec3 dir   = player.getViewVector(1F);

            float projectileVelocity = getProjectileVelocity(stack);
            float projectileSpread = getProjectileSpread(stack);
            float recoilPitch = getRecoilPitch(stack);
            float recoilYaw = getRecoilYaw(stack);
            float damagePerPellet = getDamagePerPellet(stack); 

            RandomSource random = player.getRandom();

            for (int i = 0; i < NUM_PELLETS; i++) {
                Arrow arrow = new Arrow(level, player);
                arrow.setOwner(player);
                arrow.getPersistentData().putUUID("shooterUUID", player.getUUID());
                arrow.setPos(start.x, start.y, start.z);
                arrow.setSilent(true);
                arrow.setNoGravity(true);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.getPersistentData().putBoolean("zombierool:invisible", true);
                arrow.getPersistentData().putBoolean("zombierool:small", true); 
                arrow.setInvisible(true);
                arrow.setBaseDamage(damagePerPellet); 
                arrow.getPersistentData().putBoolean("zombierool:shotgun_pellet", true); 
                
                float currentSpread = projectileSpread + random.nextFloat() * 1.0f; 
                arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, currentSpread);
                level.addFreshEntity(arrow);
            }
            
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (random.nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            // Reduced volume to mitigate overlapping sounds for high rate-of-fire weapons.
            // If the sound still "lingers", the sound asset itself might be too long or configured to loop.
            level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                getFireSound(stack),
                SoundSource.PLAYERS, 0.8f, 1f + (random.nextFloat() * 0.1f - 0.05f) // Volume adjusted from 1.2f to 0.8f
            );
            setAmmo(stack, getAmmo(stack) - 1);
            setLastFireTick(stack, currentTick);
            setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS); 
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (livingEntity instanceof Player player) {
            // N'arrête le rechargement que si la souris est relâchée
            // Le rechargement continue si la souris reste enfoncée et qu'il reste des balles
            // Cela permet une interruption "manuelle" si le joueur lâche le clic
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);
        
        int t = getReloadTimer(stack);
        int pumpTimer = getPumpTimer(stack);
        
        if (level.isClientSide) {
            return;
        }
        
        if (pumpTimer > 0) {
            setPumpTimer(stack, --pumpTimer);
            if (pumpTimer == 0) { 
                level.playSound(null, p.getX(), p.getY(), p.getZ(), PUMP_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    
        // Gestion du rechargement balle par balle
        if (isReloading(stack)) {
            if (!sel || !p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                // Si l'arme n'est plus sélectionnée ou si le joueur change d'arme, annule le rechargement
                setReloading(stack, false);
                setReloadTimer(stack, 0);
                return;
            }
            
            // Si le timer est en cours
            if (t > 0) {
                setReloadTimer(stack, --t);
            }
            
            // Si le timer est terminé pour une balle, ajoute une balle et prépare la suivante
            if (t <= 0) {
                if (getAmmo(stack) < MAX_AMMO && getReserve(stack) > 0) {
                    setAmmo(stack, getAmmo(stack) + 1);
                    setReserve(stack, getReserve(stack) - 1);
                    
                    // Relance le timer pour la prochaine balle, ou termine si plus de balles ou plein
                    if (getAmmo(stack) < MAX_AMMO && getReserve(stack) > 0) {
                        int reloadTime = RELOAD_TIME_PER_SHELL;
                        // Si tu as un effet Speed Cola pour le shotgun, tu peux l'ajouter ici
                        // if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                        //     reloadTime /= 2;
                        // }
                        setReloadTimer(stack, reloadTime);
                        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), INSERT_SHELL_SOUND, SoundSource.PLAYERS, 1f, 1f + (p.getRandom().nextFloat() * 0.1f - 0.05f));
                    } else {
                        setReloading(stack, false); 
                        setReloadTimer(stack, 0); 
                        // Pas de setPumpTimer ici, le pump final est géré par la logique de fin de rechargement/lâcher clic
                    }
                } else {
                    setReloading(stack, false); 
                    setReloadTimer(stack, 0); 
                    // Pas de setPumpTimer ici non plus
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
            ? getTranslatedMessage("§5Bulldog", "§5Bulldog") // Translated PaP name
            : getTranslatedMessage("§fFusil à Pompe", "§fShotgun"); // Base name
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§l§eFusil à Pompe de l'UNSC", "§l§eUNSC Shotgun")));
        tips.add(Component.literal(getTranslatedMessage("§cDégâts massifs à très courte portée.", "§cMassive damage at very close range.")));
        tips.add(Component.literal(getTranslatedMessage("§cDispersion énorme, mais efficace de près.", "§cHuge spread, but effective up close.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage("§dNom PaP : Bulldog", "§dPaP Name: Bulldog"))); 
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts par plomb : " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET),
                "§dPaP Damage per Pellet Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET)
            )));
            tips.add(Component.literal(getTranslatedMessage("§dDispersion et recul grandement réduits, portée légèrement améliorée.", "§dGreatly reduced spread and recoil, slightly improved range.")));
        } else {
            tips.add(Component.literal(getTranslatedMessage("§7Un classique pour nettoyer la pièce.", "§7A classic for clearing the room.")));
        }

        tips.add(Component.literal(getTranslatedMessage("§7Munitions : ", "§7Ammo: ") + getAmmo(stack) + " / " + MAX_AMMO));
        tips.add(Component.literal(getTranslatedMessage("§7Réserve : ", "§7Reserve: ") + getReserve(stack) + " / " + MAX_RESERVE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }

    public static float calculateAdjustedDamage(Arrow pellet, LivingEntity target, float distance) {
        float baseDamage = (float) pellet.getBaseDamage(); 
        
        if (distance <= EFFECTIVE_RANGE_SHORT) {
            return baseDamage; 
        } else if (distance <= EFFECTIVE_RANGE_MEDIUM) {
            float damageReductionFactor = (distance - EFFECTIVE_RANGE_SHORT) / (EFFECTIVE_RANGE_MEDIUM - EFFECTIVE_RANGE_SHORT);
            float interpolatedDamage = baseDamage * (1.0f - damageReductionFactor * (1.0f - MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE));
            return Math.max(0.05f, interpolatedDamage); 
        } else if (distance <= EFFECTIVE_RANGE_LONG) {
            float damageReductionFactor = (distance - EFFECTIVE_RANGE_MEDIUM) / (EFFECTIVE_RANGE_LONG - EFFECTIVE_RANGE_MEDIUM);
            float interpolatedDamage = baseDamage * MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE * (1.0f - damageReductionFactor);
            return Math.max(0.05f, interpolatedDamage); 
        } else {
            return 0.05f; 
        }
    }
}
