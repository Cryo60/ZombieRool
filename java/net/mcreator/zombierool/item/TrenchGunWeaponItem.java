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

public class TrenchGunWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO = 8; // Capacité du tube : 8 cartouches
    private static final int MAX_RESERVE = 64; // 8 tubes complets en réserve
    private static final int COOLDOWN_TICKS = 15; // Cadence de tir lente (1 tir/0.75 sec)
    private static final int RELOAD_TIME_PER_SHELL = 15; // 0.75 seconde par cartouche insérée
    private static final int PUMP_SOUND_DURATION = 10; // Durée approximative du son de pump en ticks (ajuste selon le son)
    private static final int MIN_PUMP_FIRE_DELAY_TICKS = PUMP_SOUND_DURATION; // Délai minimum entre le tir et le prochain pour le pump

    private static final float WEAPON_DAMAGE_PER_PELLET = 9.0f; // Dégâts par "plomb"
    private static final int NUM_PELLETS = 8; // Nombre de "plombs" par tir
    private static final float TOTAL_BASE_DAMAGE = WEAPON_DAMAGE_PER_PELLET * NUM_PELLETS; // Total : 96
    
    private static final float PAP_BONUS_DAMAGE_PER_PELLET = 8.0f; // Bonus PaP par "plomb"
    private static final float TOTAL_PAP_BONUS_DAMAGE = PAP_BONUS_DAMAGE_PER_PELLET * NUM_PELLETS; // Total : 64
    
    private static final float BASE_HEADSHOT_DAMAGE = 1.8f; // Multiplicateur headshot (moins important pour shotgun)
    private static final float PAP_HEADSHOT_BONUS = 0.2f; // Petit bonus headshot si PaP
    
    // Paramètres de tir (projectile invisible)
    private static final float BASE_PROJECTILE_VELOCITY = 2.0f; // Vitesse des plombs faible
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.1f; 
    private static final float BASE_PROJECTILE_SPREAD = 3.5f; // Dispersion ÉLEVÉE (shotgun classique)
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.6f; // Bonne réduction de dispersion avec PaP

    // Paramètres de Recul
    private static final float BASE_RECOIL_PITCH = 3.0f; // Recul vertical ÉNORME
    private static final float BASE_RECOIL_YAW = 0.6f; // Recul latéral très marqué
    private static final float PAP_RECOIL_MULTIPLIER = 0.5f; // Recul fortement réduit avec PaP

    // Paramètres de réduction de dégâts à distance (pour un plomb)
    private static final float EFFECTIVE_RANGE_SHORT = 4.0f; // Distance où les dégâts sont max (4 blocs)
    private static final float EFFECTIVE_RANGE_MEDIUM = 8.0f; // Distance où les dégâts commencent à être très faibles (8 blocs)
    private static final float MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE = 0.3f; // 30% des dégâts à moyenne portée
    private static final float EFFECTIVE_RANGE_LONG = 12.0f; // Au-delà de cette distance, dégâts quasi nuls (12 blocs)

    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.TRENCH_GUN_FIRE.get(); 
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get(); // Utilise le son générique pour les armes PaP
    private static final SoundEvent PUMP_SOUND = ZombieroolModSounds.TRENCH_GUN_PUMP.get();
    private static final SoundEvent INSERT_SHELL_SOUND = ZombieroolModSounds.TRENCH_GUN_INSERT.get();
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get(); 
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_PUMP_TIMER = "PumpTimer"; // Compte à rebours avant que le son de pompe se joue ou que le tir soit à nouveau possible
    private static final String TAG_IS_RELOADING = "IsReloading";
    private static final String TAG_RELOAD_START_AMMO = "ReloadStartAmmo"; 

    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public TrenchGunWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); // Rareté peu commune
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
        // Cette méthode donne le dégât total théorique. Le calcul par plomb est fait lors de l'impact.
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
    
    // Pour le rechargement balle par balle
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }
    public boolean isReloading(ItemStack s) { return getOrCreateTag(s).getBoolean(TAG_IS_RELOADING); }
    public void setReloading(ItemStack s, boolean reloading) { getOrCreateTag(s).putBoolean(TAG_IS_RELOADING, reloading); }
    public int getReloadStartAmmo(ItemStack s) { return getOrCreateTag(s).getInt(TAG_RELOAD_START_AMMO); }
    public void setReloadStartAmmo(ItemStack s, int ammo) { getOrCreateTag(s).putInt(TAG_RELOAD_START_AMMO, ammo); }


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
        }
        if (!tag.contains("Reserve")) {
            setReserve(stack, MAX_RESERVE);
        }
        if (!tag.contains("ReloadTimer")) {
            setReloadTimer(stack, 0);
        }
        if (!tag.contains(TAG_IS_RELOADING)) { // Ensure this tag is initialized
            setReloading(stack, false);
        }
        if (!tag.contains(TAG_RELOAD_START_AMMO)) { // Ensure this tag is initialized
            setReloadStartAmmo(stack, 0);
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
        if (!tag.contains(TAG_RELOAD_START_AMMO)) { 
            tag.putInt(TAG_RELOAD_START_AMMO, 0);
        }
    }

    @Override
    public void startReload(ItemStack stack, Player player) {
        initializeIfNeeded(stack);

        if (isReloading(stack) || getReserve(stack) == 0 || getAmmo(stack) == MAX_AMMO) {
            return; 
        }

        if (!player.level().isClientSide) {
            setReloading(stack, true);
            // Si le joueur n'a pas de balles, on simule un pump avant la première insertion
            if (getAmmo(stack) == 0) {
                // On met un timer court pour le son de pump initial avant l'insertion de la première balle
                setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS); // Empêche de tirer avant le pump
            } else {
                // Si le chargeur n'est pas vide, on peut directement insérer
                setReloadTimer(stack, RELOAD_TIME_PER_SHELL); // Déclenche le timer pour la première balle
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), INSERT_SHELL_SOUND, SoundSource.PLAYERS, 1f, 1f);
            }
            setReloadStartAmmo(stack, getAmmo(stack)); 
            
            // Cherry Cola effect
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
        int pumpTimer = getPumpTimer(stack);

        // Si le joueur est en train de recharger, un clic sur la souris interrompt le rechargement et tire
        if (isReloading(stack) && getAmmo(stack) > 0 && pumpTimer <= 0) { // On s'assure que le pump timer est à 0
            setReloading(stack, false);
            setReloadTimer(stack, 0); 
            // Procède au tir normalement
        }

        // Empêche de tirer tant que le son de pompe n'est pas terminé (pumpTimer > 0)
        if (pumpTimer > 0) {
            return;
        }

        // Gestion du cooldown de tir
        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return;
        }

        // Si pas de munitions, mais de la réserve, tente de recharger une balle
        if (getAmmo(stack) == 0 && getReserve(stack) > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            startReload(stack, player); // Lance le rechargement d'une seule balle
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
            float damagePerPellet = getDamagePerPellet(stack); // Dégâts par plomb

            for (int i = 0; i < NUM_PELLETS; i++) {
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
                arrow.setBaseDamage(damagePerPellet); // Le dégât de base du plomb
                arrow.getPersistentData().putBoolean("zombierool:shotgun_pellet", true); // Marque comme plomb de shotgun
                
                // Applique la dispersion pour chaque plomb
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
            setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS); // Active le timer pour le son de pump et le délai de tir
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);
        
        int t = getReloadTimer(stack);
        int pumpTimer = getPumpTimer(stack);
        
        if (level.isClientSide) {
            int expectedReloadTime = RELOAD_TIME_PER_SHELL;
            if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                expectedReloadTime /= 2;
            }
            if (isReloading(stack) && t == expectedReloadTime && p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) {
                CherryReloadAnimationHandler.startCherryAnimation(null); 
            }
            return;
        }
        
        // Gestion du timer de pump (si le son doit être joué au client, c'est là que le timer se déclenche)
        if (pumpTimer > 0) {
            setPumpTimer(stack, --pumpTimer);
            if (pumpTimer == 0) { // Joue le son de pump quand le timer arrive à 0
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
            
            // Si le joueur est en train de recharger
            if (t > 0) {
                // Diminue le timer
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
                        if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                            reloadTime /= 2;
                        }
                        setReloadTimer(stack, reloadTime);
                        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), INSERT_SHELL_SOUND, SoundSource.PLAYERS, 1f, 1f);
                    } else {
                        setReloading(stack, false); 
                        setReloadTimer(stack, 0); 
                        setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS); // Joue le son de pump final et impose un délai
                    }
                } else {
                    setReloading(stack, false); 
                    setReloadTimer(stack, 0); 
                    setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS); // Joue le son de pump final et impose un délai
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
            ? getTranslatedMessage("§6Le Couteau du Boucher", "§6The Butcher's Knife") // Translated PaP name
            : getTranslatedMessage("§fTrench Gun", "§fTrench Gun"); // Trench Gun name likely same
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§l§eFusil à Pompe", "§l§eShotgun")));
        tips.add(Component.literal(getTranslatedMessage("§aDevastateur à courte portée, rechargement balle par balle.", "§aDevastating at close range, reloads shell by shell.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage("§dNom PaP : Le Couteau du Boucher", "§dPaP Name: The Butcher's Knife"))); // Specific PaP name - now translated
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

    /**
     * Cette méthode doit être appelée quand une flèche (plomb) frappe une entité.
     * Elle doit être implémentée dans un événement ou une surcharge de méthode de la classe Arrow.
     * C'est là que la logique de réduction de dégâts par distance sera appliquée.
     *
     * @param pellet L'entité Arrow qui est le plomb.
     * @param target L'entité que le plomb a frappé.
     * @param distance La distance entre le tireur et la cible.
     * @return Les dégâts ajustés.
     */
    public static float calculateAdjustedDamage(Arrow pellet, LivingEntity target, float distance) {
        float baseDamage = (float) pellet.getBaseDamage(); // Récupère les dégâts de base définis dans l'Arrow
        
        if (distance <= EFFECTIVE_RANGE_SHORT) {
            return baseDamage; // Dégâts maximum à courte portée
        } else if (distance <= EFFECTIVE_RANGE_MEDIUM) {
            // Interpolation linéaire entre le dégât max et le pourcentage min à moyenne portée
            float damageReductionFactor = (distance - EFFECTIVE_RANGE_SHORT) / (EFFECTIVE_RANGE_MEDIUM - EFFECTIVE_RANGE_SHORT);
            float interpolatedDamage = baseDamage * (1.0f - damageReductionFactor * (1.0f - MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE));
            return Math.max(0.1f, interpolatedDamage); // S'assurer que le dégât ne tombe pas en dessous d'une valeur minimale symbolique
        } else if (distance <= EFFECTIVE_RANGE_LONG) {
            // Interpolation linéaire entre le pourcentage min à moyenne portée et un dégât quasi nul
            float damageReductionFactor = (distance - EFFECTIVE_RANGE_MEDIUM) / (EFFECTIVE_RANGE_LONG - EFFECTIVE_RANGE_MEDIUM);
            float interpolatedDamage = baseDamage * MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE * (1.0f - damageReductionFactor);
            return Math.max(0.1f, interpolatedDamage); // Dégât quasi nul
        } else {
            return 0.1f; // Dégâts négligeables au-delà de la longue portée
        }
    }
}
