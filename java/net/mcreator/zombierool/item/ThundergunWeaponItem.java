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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.Arrow; // Used as base for custom projectile properties
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
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
import net.minecraft.world.entity.animal.Animal; // Also consider preventing damage to tamed animals

import net.mcreator.zombierool.client.CherryReloadAnimationHandler;
import java.util.List;
import java.util.Random; // For spread randomness

// Import for RecoilPacket (YOU NEED TO CREATE THIS PACKET IN MCreator FIRST)
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class ThundergunWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO       = 2;    // Seulement 2 coups par chargeur
    private static final int MAX_RESERVE    = MAX_AMMO * 4; // 8 coups en réserve
    private static final int COOLDOWN_TICKS = 20;   // 1 seconde de cadence de tir (arme lourde)
    private static final int RELOAD_TIME    = 80;   // ticks (4s)
       
    private static final float WEAPON_DAMAGE        = 100.0f; // Dégâts de base très élevés pour les zombies normaux
    private static final float PAP_BONUS_DAMAGE     = 100.0f; // Bonus de dégâts PaP (pour les plus gros sacs à PV)
       
    private static final float BASE_HEADSHOT_DAMAGE = 200.0f; // Les headshots devraient être dévastateurs
    private static final float PAP_HEADSHOT_BONUS   = 100.0f; // Bonus de dégâts du headshot si PaP
    
    // Paramètres de tir du Thundergun (pas un projectile classique, mais une "force")
    private static final float BASE_PROJECTILE_VELOCITY = 0.0f; // Pas de projectile visible, c'est un effet de zone
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.0f; // Pas de changement de vitesse
    private static final float BASE_PROJECTILE_SPREAD = 0.0f; // Pas de dispersion, c'est une ligne droite
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 1.0f; // Pas de changement de dispersion

    // Paramètres de Recul (lourd, mais contrôlable)
    private static final float BASE_RECOIL_PITCH = 5.0f; // Gros recul vers le haut
    private static final float BASE_RECOIL_YAW = 0.5f;   // Léger recul latéral
    private static final float PAP_RECOIL_MULTIPLIER = 0.4f; // Beaucoup moins de recul avec PaP

    // Paramètres spécifiques au Thundergun
    private static final float KNOCKBACK_STRENGTH = 2.5f; // Force de repoussement des entités
    private static final float EXPLOSION_RADIUS   = 5.0f; // Rayon de l'effet de zone (l'air "poussé")
    private static final int STUN_DURATION_TICKS = 40; // Durée de stun après être repoussé (2 secondes)


    private static final SoundEvent FIRE_SOUND           = ZombieroolModSounds.THUNDERGUN_FIRE.get(); // Créer un son unique
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.THUNDERGUN_FIRE.get(); // Son PaP
    private static final SoundEvent RELOAD_SOUND         = ZombieroolModSounds.THUNDERGUN_RELOADING.get(); // Créer un son unique
    private static final SoundEvent DRY_FIRE_SOUND       = ZombieroolModSounds.RIFLE_DRY.get(); // Son générique si pas de son Thundergun spécifique
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";

    // --- Classe interne pour Cherry Cola Effects ---
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f; // 10% of max health
        public static final float MAX_DISTANCE_DAMAGE = 1.5f; // Max flat damage bonus when very close
        public static final int STUN_DURATION_TICKS = 80;
    }
    // --- Fin de la classe interne ---

    public ThundergunWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC)); // Rareté épique ou légendaire
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
            ? WEAPON_DAMAGE + PAP_BONUS_DAMAGE
            : WEAPON_DAMAGE;
    }
       
    // Implémentation de IHeadshotWeapon
    @Override
    public float getHeadshotBaseDamage(ItemStack stack) {
        // Le Thundergun ne fait pas de headshots à proprement parler car c'est une poussée de zone.
        // On peut laisser une valeur élevée pour simuler l'impact sur des cibles proches,
        // mais le mécanisme de détection de headshot n'aura pas le même sens.
        return BASE_HEADSHOT_DAMAGE;
    }

    @Override
    public float getHeadshotPapBonusDamage(ItemStack stack) {
        return PAP_HEADSHOT_BONUS;    
    }

    public float getProjectileVelocity(ItemStack stack) {
        return isPackAPunched(stack)
            ? BASE_PROJECTILE_VELOCITY * PAP_PROJECTILE_VELOCITY_MULTIPLIER   // Inchangé pour le Thundergun
            : BASE_PROJECTILE_VELOCITY;
    }

    public float getProjectileSpread(ItemStack stack) {
        return isPackAPunched(stack)
            ? BASE_PROJECTILE_SPREAD * PAP_PROJECTILE_SPREAD_MULTIPLIER     // Inchangé pour le Thundergun
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
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }

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
        if (!tag.contains(TAG_PAP)) { // Ensure PAP tag is initialized
            tag.putBoolean(TAG_PAP, false);
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

        if (getReloadTimer(stack) == 0 && getAmmo(stack) < MAX_AMMO && getReserve(stack) > 0) {
            int reloadTime = RELOAD_TIME;
            float pitch = 1.0f;

            if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                reloadTime /= 2;
                pitch = 1.6f;
            }

            if (!player.level().isClientSide) { // This block runs on the server
                setReloadTimer(stack, reloadTime);
                System.out.println("DEBUG: Attempting to play reload sound for " + player.getName().getString() + " at pitch " + pitch);
                // Use player.playSound to attach sound to the player
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), RELOAD_SOUND, SoundSource.PLAYERS, 1f, pitch);    

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
                        if (target instanceof Monster || (target instanceof TamableAnimal && !((TamableAnimal)target).isTame()) || !(target instanceof TamableAnimal || target instanceof Animal)) { // Added check for Animal to avoid hurting passive mobs
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

                if (player instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundSetEquipmentPacket(
                        sp.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, stack))));
                }
            }
        }
    }

    private void finishReload(ItemStack stack, Player player, Level level) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return;
        int leftover = getAmmo(stack);
        int reserve  = getReserve(stack) + leftover;
        int newAmmo  = Math.min(MAX_AMMO, reserve);
        reserve -= newAmmo;
        setAmmo(stack, newAmmo);
        setReserve(stack, reserve);
        sp.connection.send(new ClientboundSetEquipmentPacket(
            sp.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, stack))));
    }

    private SoundEvent getFireSound(ItemStack stack) {
        return isPackAPunched(stack) ? FIRE_SOUND_UPGRADED : FIRE_SOUND;
    }

    @Override public UseAnim getUseAnimation(ItemStack s) { return getReloadTimer(s) > 0 ? UseAnim.NONE : UseAnim.BOW; }
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

        if (getReloadTimer(stack) > 0) {
            return;
        }

        long lastFireTick = getLastFireTick(stack);
        long currentTick = level.getGameTime();
        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return;
        }

        if (getAmmo(stack) == 0 && getReserve(stack) > 0) {
            startReload(stack, player);
            return;
        }
           
        if (getAmmo(stack) == 0 && getReserve(stack) == 0) {
            level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                DRY_FIRE_SOUND,
                SoundSource.PLAYERS, 0.7f, 1f
            );
            return;
        }

        if (getAmmo(stack) > 0) {
            Vec3 start = player.getEyePosition(1F);
            Vec3 dir   = player.getViewVector(1F);

            // Pas de projectile, mais un effet de zone en ligne droite
            level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                getFireSound(stack),
                SoundSource.PLAYERS, 0.7f, 1f
            );

            // Appliquer les dégâts et la poussée aux entités dans le rayon
            // On calcule la fin du rayon d'effet pour la détection
            Vec3 endOfEffect = start.add(dir.scale(EXPLOSION_RADIUS));
            AABB effectBox = new AABB(start.x, start.y, start.z, endOfEffect.x, endOfEffect.y, endOfEffect.z)
                                .inflate(EXPLOSION_RADIUS / 2); // Inflate to create a volume around the line

            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, effectBox,
                target -> target != player && target.isAlive() && !(target instanceof Player) && !(target instanceof Animal) && !(target instanceof TamableAnimal && ((TamableAnimal)target).isTame())
            );

            float damage = getWeaponDamage(stack);

            for (LivingEntity target : entities) {
                // Calcul de la direction pour la poussée
                Vec3 knockbackDirection = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(knockbackDirection.x * KNOCKBACK_STRENGTH, 1.0, knockbackDirection.z * KNOCKBACK_STRENGTH); // Poussée vers le haut et en arrière
                
                target.hurt(player.level().damageSources().playerAttack(player), damage);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_DURATION_TICKS, 4)); // Ralentissement après la poussée
            }

            // ENVOYER LE PAQUET DE RECUL AU CLIENT
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = getRecoilPitch(stack);
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * getRecoilYaw(stack);
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            setAmmo(stack, getAmmo(stack) - 1);
            setLastFireTick(stack, currentTick);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);
           
        int t = getReloadTimer(stack);
           
        if (level.isClientSide) {
            int expectedReloadTime = RELOAD_TIME;
            if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                expectedReloadTime /= 2;
            }
            if (t == expectedReloadTime && p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) {
                CherryReloadAnimationHandler.startCherryAnimation(null);
            }
            return;
        }
           
        // Server-side logic for reload timer and unequipped interruption
        if (t > 0) {
            if (!sel || !p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                setReloadTimer(stack, 0);
                System.out.println("DEBUG: Reload interrupted for " + stack.getDisplayName().getString() + " (not selected or wrong item). Timer reset to 0.");
                return;
            }
            setReloadTimer(stack, --t);
            if (t <= 0) {
                finishReload(stack, p, level);
                System.out.println("DEBUG: Reload finished for " + stack.getDisplayName().getString() + ".");
            }
        }

        CompoundTag tag = stack.getOrCreateTag();
        boolean wasEquippedPreviously = tag.getBoolean(TAG_EQUIPPED_PREVIOUSLY);

        if (sel && !wasEquippedPreviously) {
            if (p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                System.out.println("DEBUG: Playing WEAPON_IN_HAND_SOUND for " + stack.getDisplayName().getString());
            }
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, true);
        } else if (!sel && wasEquippedPreviously) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
            System.out.println("DEBUG: " + stack.getDisplayName().getString() + " unequipped. Resetting TAG_EQUIPPED_PREVIOUSLY.");
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        String name = upgraded
            ? getTranslatedMessage("§5Canon Zeus", "§5Zeus Cannon") // Translated PaP name
            : getTranslatedMessage("§9Thundergun", "§9Thundergun"); // Thundergun name likely same
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§l§cArme Spéciale", "§l§cSpecial Weapon"))); // Special Weapon
        tips.add(Component.literal(getTranslatedMessage("§eProjette et étourdit les morts-vivants.", "§eKnocks back and stuns the undead.")));
        tips.add(Component.literal(getTranslatedMessage("§6Dégâts de base : " + String.format("%.1f", WEAPON_DAMAGE), "§6Base Damage: " + String.format("%.1f", WEAPON_DAMAGE))));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage("§dBonus dégâts PaP : " + String.format("%.1f", PAP_BONUS_DAMAGE), "§dPaP Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE))));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
