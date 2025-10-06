package net.mcreator.zombierool.item;

import com.mojang.datafixers.util.Pair;
import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.api.IHeadshotWeapon;
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
import net.minecraft.world.entity.animal.Animal;

import net.mcreator.zombierool.client.CherryReloadAnimationHandler; // Si tu l'utilises, sinon à retirer
import java.util.List;

import net.minecraft.util.RandomSource;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class DoubleBarrelWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO = 2; // Inchangé (deux coups, c'est l'essence du double barrel)
    private static final int MAX_RESERVE = 24; // 12 rechargements complets (24 balles) (Réduit de 32 à 24)
    private static final int COOLDOWN_TICKS = 12; // Cadence de tir légèrement réduite (1 tir/0.6 sec) (Augmenté de 10 à 12)

    // Temps de rechargement en ticks (Augmenté pour les trois phases)
    private static final int RELOAD_TIME_OPEN = 12;   // Temps pour "ouvrir" (Augmenté de 10 à 12)
    private static final int RELOAD_TIME_INJECT = 20; // Temps pour "injecter" les deux balles (Augmenté de 15 à 20)
    private static final int RELOAD_TIME_CLOSE = 12;  // Temps pour "fermer" (Augmenté de 10 à 12)
    private static final int TOTAL_RELOAD_TICKS = RELOAD_TIME_OPEN + RELOAD_TIME_INJECT + RELOAD_TIME_CLOSE; // Total : 44 ticks (2.2 sec) (Ancien : 35 ticks)

    private static final float WEAPON_DAMAGE_PER_PELLET = 12.0f; // Dégâts par "plomb" (Réduit de 15.0f à 12.0f)
    private static final int NUM_PELLETS = 8; // Nombre de "plombs" par tir (Réduit de 10 à 8)
    private static final float TOTAL_BASE_DAMAGE = WEAPON_DAMAGE_PER_PELLET * NUM_PELLETS; // Total : 96 (Ancien : 150)

    private static final float PAP_BONUS_DAMAGE_PER_PELLET = 8.0f; // Bonus PaP par "plomb" (Réduit de 10.0f à 8.0f)
    private static final float TOTAL_PAP_BONUS_DAMAGE = PAP_BONUS_DAMAGE_PER_PELLET * NUM_PELLETS; // Total : 64 (Ancien : 100)

    private static final float BASE_HEADSHOT_DAMAGE = 1.4f; // Multiplicateur headshot (Réduit de 1.6f à 1.4f)
    private static final float PAP_HEADSHOT_BONUS = 0.1f; // Petit bonus headshot si PaP (Réduit de 0.2f à 0.1f)

    // Paramètres de tir (projectile invisible)
    private static final float BASE_PROJECTILE_VELOCITY = 2.0f; // Vitesse des plombs (Légèrement réduite de 2.2f à 2.0f)
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.05f; // Moins rapide après PaP (Réduit de 1.1f à 1.05f)
    private static final float BASE_PROJECTILE_SPREAD = 4.5f; // Dispersion TRÈS ÉLEVÉE (Augmenté de 4.0f à 4.5f)
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.6f; // Moins bonne réduction de dispersion avec PaP (Augmenté de 0.5f à 0.6f)

    // Paramètres de Recul
    private static final float BASE_RECOIL_PITCH = 4.5f; // Recul vertical IMMENSE (Augmenté de 4.0f à 4.5f)
    private static final float BASE_RECOIL_YAW = 1.0f; // Recul latéral très marqué (Augmenté de 0.8f à 1.0f)
    private static final float PAP_RECOIL_MULTIPLIER = 0.5f; // Recul fortement réduit avec PaP (Moins efficace, augmenté de 0.4f à 0.5f)

    // Paramètres de réduction de dégâts à distance (pour un plomb)
    private static final float EFFECTIVE_RANGE_SHORT = 2.5f; // Distance où les dégâts sont max (Réduit de 3.0f à 2.5f)
    private static final float EFFECTIVE_RANGE_MEDIUM = 6.0f; // Distance où les dégâts commencent à être très faibles (Réduit de 7.0f à 6.0f)
    private static final float MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE = 0.15f; // 15% des dégâts à moyenne portée (Réduit de 0.2f à 0.15f)
    private static final float EFFECTIVE_RANGE_LONG = 8.0f; // Au-delà de cette distance, dégâts quasi nuls (Réduit de 10.0f à 8.0f)

    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.DOUBLE_BARREL_FIRE.get(); 
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get(); 
    private static final SoundEvent INJECT_SOUND = ZombieroolModSounds.DOUBLE_BARREL_INJECT.get();
    private static final SoundEvent CLOSE_SOUND = ZombieroolModSounds.DOUBLE_BARREL_CLOSE.get();
    private static final SoundEvent OPEN_SOUND = ZombieroolModSounds.DOUBLE_BARREL_OPEN.get();
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get(); 
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_RELOAD_STATE = "ReloadState"; // 0: Idle, 1: Opening, 2: Injecting, 3: Closing
    private static final String TAG_RELOAD_TIMER = "ReloadTimer";

    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public DoubleBarrelWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    /**
     * Helper method to check if the client's language is English.
     * This is crucial for dynamic translation of item names and tooltips.
     * @return true if the client's language code starts with "en", false otherwise.
     */
    private static boolean isEnglishClient() {
        // Minecraft.getInstance() can only be called on the client side.
        // This method should primarily be used for client-side rendering like tooltips.
        if (Minecraft.getInstance() == null) {
            // If called on the server, or before Minecraft client is fully initialized,
            // default to a language or handle appropriately. For tooltips, it's always client-side.
            return false; // Or throw an error, depending on desired behavior
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

    @Override
    public void applyPackAPunch(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG_PAP, true);
    }

    @Override
    public boolean isPackAPunched(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_PAP);
    }

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

    @Override
    public void tickReload(ItemStack stack, Player player, Level level) {
        // Logic handled in inventoryTick, can remain empty
    }

    private CompoundTag getOrCreateTag(ItemStack s) {
        if (!s.hasTag()) s.setTag(new CompoundTag());
        return s.getTag();
    }

    @Override public int  getAmmo(ItemStack s)           { return getOrCreateTag(s).getInt("Ammo"); }
    @Override public void setAmmo(ItemStack s, int a)    { getOrCreateTag(s).putInt("Ammo", a); }
    @Override public int  getReserve(ItemStack s)        { return getOrCreateTag(s).getInt("Reserve"); }
    @Override public void setReserve(ItemStack s, int r) { getOrCreateTag(s).putInt("Reserve", r); }
    
    // Gestion du rechargement à étapes
    public int getReloadState(ItemStack s) { return getOrCreateTag(s).getInt(TAG_RELOAD_STATE); }
    public void setReloadState(ItemStack s, int state) { getOrCreateTag(s).putInt(TAG_RELOAD_STATE, state); }
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt(TAG_RELOAD_TIMER); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt(TAG_RELOAD_TIMER, t); }
    
    public boolean isReloading(ItemStack s) { return getReloadState(s) != 0; }


    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    @Override public int getMaxAmmo()    { return MAX_AMMO; }
    @Override public int getMaxReserve() { return MAX_RESERVE; }

    @Override
    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag(); // Use getOrCreateTag for consistency
        if (!tag.contains("Ammo")) {
            setAmmo(stack, MAX_AMMO);
        }
        if (!tag.contains("Reserve")) {
            setReserve(stack, MAX_RESERVE);
        }
        if (!tag.contains("ReloadTimer")) {
            setReloadTimer(stack, 0);
        }
        if (!tag.contains(TAG_RELOAD_STATE)) {
            setReloadState(stack, 0); // État initial: Idle
        }
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_LAST_FIRE_TICK)) {
            tag.putLong(TAG_LAST_FIRE_TICK, 0);
        }
    }

    @Override
    public void startReload(ItemStack stack, Player player) {
        initializeIfNeeded(stack);

        if (isReloading(stack) || getReserve(stack) < MAX_AMMO || getAmmo(stack) == MAX_AMMO) {
            return; // Déjà en rechargement, pas assez de munitions pour un rechargement complet, ou déjà plein
        }

        if (!player.level().isClientSide) {
            setReloadState(stack, 1); // Démarre le rechargement: Opening
            setReloadTimer(stack, RELOAD_TIME_OPEN);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), OPEN_SOUND, SoundSource.PLAYERS, 1f, 1f);

            if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) {
                double radius = CherryColaEffects.RADIUS;
                float percentageDamage = CherryColaEffects.PERCENTAGE_DAMAGE;
                float maxDistanceDamage = CherryColaEffects.MAX_DISTANCE_DAMAGE;
                int stunDurationTicks = CherryColaEffects.STUN_DURATION_TICKS;
            
                AABB boundingBox = new AABB(player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                                            player.getX() + radius, player.getY() + radius, player.getZ() + radius);
                List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, boundingBox,
                    entity -> {
                        return entity != player && entity.isAlive()
                            && !(entity instanceof Player)
                            && !((entity instanceof TamableAnimal tamable) && tamable.isTame());
                    }
                );
            
                for (LivingEntity target : entities) {
                    if (target instanceof Monster || (target instanceof TamableAnimal && !((TamableAnimal)target).isTame()) || !(target instanceof TamableAnimal || target instanceof Animal)) {
                        float damage = target.getMaxHealth() * percentageDamage;
                        double distance = player.distanceTo(target);
                        float distanceFactor = (float) (1.0 - (distance / radius));
                        if (distanceFactor < 0) {
                            distanceFactor = 0;
                        }
                        float flatDamageBonus = maxDistanceDamage * distanceFactor;
                        damage += flatDamageBonus;

                        target.hurt(player.level().damageSources().playerAttack(player), damage);
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stunDurationTicks, 4));
                    }
                }
            }
        }
    }
    
    private SoundEvent getFireSound(ItemStack stack) {
        return isPackAPunched(stack) ? FIRE_SOUND_UPGRADED : FIRE_SOUND;
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
        int reloadState = getReloadState(stack);

        // Si en rechargement, le joueur ne peut pas tirer
        if (reloadState != 0) {
            return;
        }

        // Gestion du cooldown de tir
        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return;
        }

        // Si pas de munitions, mais de la réserve, tente de recharger
        if (getAmmo(stack) == 0 && getReserve(stack) > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            startReload(stack, player);
            return;
        }
        
        // Si plus de munitions et plus de réserve
        if (getAmmo(stack) == 0 && getReserve(stack) == 0) {
            level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                DRY_FIRE_SOUND,
                SoundSource.PLAYERS, 0.7f, 1f
            );
            return;
        }

        // Tir de l'arme
        if (getAmmo(stack) > 0) {
            Vec3 start = player.getEyePosition(1F);
            Vec3 dir   = player.getViewVector(1F);

            float projectileVelocity = getProjectileVelocity(stack);
            float projectileSpread = getProjectileSpread(stack);
            float recoilPitch = getRecoilPitch(stack);
            float recoilYaw = getRecoilYaw(stack);
            float damagePerPellet = getDamagePerPellet(stack);

            for (int i = 0; i < NUM_PELLETS; i++) {
                Arrow arrow = new Arrow(level, player);
                arrow.setOwner(player);
                arrow.getPersistentData().putUUID("shooterUUID", player.getUUID());
                arrow.setPos(start.x, start.y, start.z);
                arrow.setSilent(true);
                arrow.setNoGravity(true);
                arrow.pickup = Arrow.Pickup.DISALLOWED;
                arrow.getPersistentData().putBoolean("zombierool:invisible", true);
                arrow.getPersistentData().putBoolean("zombierool:small", true);
                arrow.setInvisible(true);
                arrow.setBaseDamage(damagePerPellet); 
                arrow.getPersistentData().putBoolean("zombierool:shotgun_pellet", true); // Marque comme plomb de shotgun
                
                arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread + player.getRandom().nextFloat() * 0.5f);
                level.addFreshEntity(arrow);
            }
            
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                getFireSound(stack),
                SoundSource.PLAYERS, 1.2f, 1f
            );
            setAmmo(stack, getAmmo(stack) - 1);
            setLastFireTick(stack, currentTick);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);
        
        int reloadState = getReloadState(stack);
        int reloadTimer = getReloadTimer(stack);
        
        if (level.isClientSide) {
            // Pas de gestion spécifique pour le rechargement Cherry Cola côté client, sauf l'animation
            int expectedReloadTime = TOTAL_RELOAD_TICKS; // Pour Speed Cola, il faudra ajuster ici aussi
            if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                expectedReloadTime /= 2; // Exemple, adapte le calcul si ton Speed Cola est plus complexe
            }
            if (reloadState == 1 && reloadTimer == RELOAD_TIME_OPEN && p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) {
                // CherryReloadAnimationHandler.startCherryAnimation(null); // Si tu as une animation pour ça
            }
            return;
        }
    
        // Gestion du rechargement par états
        if (reloadState != 0) {
            if (!sel || !p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                // Si l'arme n'est plus sélectionnée ou si le joueur change d'arme, annule le rechargement
                setReloadState(stack, 0);
                setReloadTimer(stack, 0);
                return;
            }
            
            if (reloadTimer > 0) {
                // Diminue le timer
                setReloadTimer(stack, --reloadTimer);
            }
            
            if (reloadTimer <= 0) {
                switch (reloadState) {
                    case 1: // Étape d'ouverture terminée, passe à l'injection
                        setReloadState(stack, 2);
                        int injectTime = RELOAD_TIME_INJECT;
                        if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                            injectTime /= 2;
                        }
                        setReloadTimer(stack, injectTime);
                        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), INJECT_SOUND, SoundSource.PLAYERS, 1f, 1f);
                        break;
                    case 2: // Étape d'injection terminée, passe à la fermeture et remplit les munitions
                        // Remplit les munitions si possible
                        int shellsToLoad = Math.min(MAX_AMMO - getAmmo(stack), getReserve(stack));
                        setAmmo(stack, getAmmo(stack) + shellsToLoad);
                        setReserve(stack, getReserve(stack) - shellsToLoad);
                        
                        setReloadState(stack, 3);
                        int closeTime = RELOAD_TIME_CLOSE;
                        if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                            closeTime /= 2;
                        }
                        setReloadTimer(stack, closeTime);
                        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), CLOSE_SOUND, SoundSource.PLAYERS, 1f, 1f);
                        break;
                    case 3: // Étape de fermeture terminée, rechargement complet
                        setReloadState(stack, 0); // Retour à l'état Idle
                        setReloadTimer(stack, 0);
                        break;
                }
            }
        }

        // Gestion de l'équipement initial de l'arme
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
            ? getTranslatedMessage("§6Le Doigt du Diable", "§6The Devil's Finger") // Translated PaP name
            : getTranslatedMessage("§fDouble-Barreled Shotgun", "§fDouble-Barreled Shotgun"); // Double-Barreled Shotgun name likely same
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§l§eFusil à Pompe", "§l§eShotgun")));
        tips.add(Component.literal(getTranslatedMessage("§aDeux coups massifs avant rechargement.", "§aTwo massive shots before reloading.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage("§dNom PaP : Le Doigt du Diable", "§dPaP Name: The Devil's Finger"))); // Specific PaP name - now translated
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts par plomb : " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET),
                "§dBonus damage per pellet: " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET)
            )));
            tips.add(Component.literal(getTranslatedMessage("§dDispersion et recul grandement réduits.", "§dGreatly reduced spread and recoil.")));
        }
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
            return Math.max(0.1f, interpolatedDamage); 
        } else if (distance <= EFFECTIVE_RANGE_LONG) {
            float damageReductionFactor = (distance - EFFECTIVE_RANGE_MEDIUM) / (EFFECTIVE_RANGE_LONG - EFFECTIVE_RANGE_MEDIUM);
            float interpolatedDamage = baseDamage * MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE * (1.0f - damageReductionFactor);
            return Math.max(0.1f, interpolatedDamage); 
        } else {
            return 0.1f; 
        }
    }
}
