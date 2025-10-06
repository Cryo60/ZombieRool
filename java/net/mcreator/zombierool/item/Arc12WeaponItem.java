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

import net.minecraft.util.RandomSource;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class Arc12WeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO = 12; // Un bon chargeur pour un fusil à pompe rapide
    private static final int MAX_RESERVE = 96; // 8 chargeurs de réserve
    private static final int COOLDOWN_TICKS = 6; // Tir rapide
    private static final int RELOAD_TIME = 40; // Temps de rechargement "chargeur"
    
    private static final float WEAPON_DAMAGE_PER_PELLET = 6.0f; // Dégâts par plomb modérés
    private static final int NUM_PELLETS = 7; // Moins de plombs que le SPAS, pour compenser la cadence
    private static final float TOTAL_BASE_DAMAGE = WEAPON_DAMAGE_PER_PELLET * NUM_PELLETS; // Total : 42.0f
    
    private static final float PAP_BONUS_DAMAGE_PER_PELLET = 8.0f; // Bon bonus PaP pour rester compétitif
    private static final float TOTAL_PAP_BONUS_DAMAGE = PAP_BONUS_DAMAGE_PER_PELLET * NUM_PELLETS;

    private static final float BASE_HEADSHOT_DAMAGE = 1.8f; // Bonus de tête décent
    private static final float PAP_HEADSHOT_BONUS = 0.5f;    // Bonus de tête PaP pour scale

    // Bullet Parameters
    private static final float BASE_PROJECTILE_VELOCITY = 2.0f; // Vélocité standard
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.1f; // Légère amélioration PaP
    private static final float BASE_PROJECTILE_SPREAD = 3.2f; // Bonne dispersion de base pour un pompe rapide
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.6f; // Réduction de dispersion avec PaP

    // Recoil Parameters
    private static final float BASE_RECOIL_PITCH = 1.8f; // Recul vertical modéré
    private static final float BASE_RECOIL_YAW = 0.4f;   // Recul horizontal faible
    private static final float PAP_RECOIL_MULTIPLIER = 0.6f; // Recul réduit avec PaP

    // Sound References
    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.AR_FIRE.get(); // Peut-être un son de fusil généraliste ou à trouver
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get();
    private static final SoundEvent RELOAD_SOUND = ZombieroolModSounds.AR_RELOADING.get(); // Son de rechargement de chargeur
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get();
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";

    // Cherry Cola Effects (Copied from previous examples)
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public Arc12WeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.COMMON)); // Rareté commune
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
        // Pour les fusils à pompe, c'est le total des dégâts de tous les plombs qui compte.
        return isPackAPunched(stack)
            ? TOTAL_BASE_DAMAGE + TOTAL_PAP_BONUS_DAMAGE
            : TOTAL_BASE_DAMAGE;
    }
    
    // Ajout d'une méthode pour récupérer les dégâts par plomb, essentielle pour les calculs individuels
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
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }

    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    @Override public int getMaxAmmo()    { return MAX_AMMO; }
    @Override public int getMaxReserve() { return MAX_RESERVE; }

    @Override
    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag(); // Use getOrCreateTag for consistency
        if (!tag.contains("Ammo")) { // Check for "Ammo" as a primary indicator for initialization
            setAmmo(stack, MAX_AMMO);
            setReserve(stack, MAX_RESERVE);
            setReloadTimer(stack, 0);
            tag.putBoolean(TAG_PAP, false); // Ensure PAP tag is initialized
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

            if (!player.level().isClientSide) {
                setReloadTimer(stack, reloadTime);
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
                        if (target instanceof Monster || (target instanceof TamableAnimal && !((TamableAnimal)target).isTame()) || (target instanceof Animal && !(target instanceof TamableAnimal))) {
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

        level.playSound(null, player.getX(), player.getY(), player.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
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

            float projectileVelocity = getProjectileVelocity(stack);
            float projectileSpread = getProjectileSpread(stack);
            float recoilPitch = getRecoilPitch(stack);
            float recoilYaw = getRecoilYaw(stack);
            float damagePerPellet = getDamagePerPellet(stack); // Utilise la nouvelle méthode pour les dégâts par plomb

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
                arrow.setBaseDamage(damagePerPellet); // Applique les dégâts par plomb
                arrow.getPersistentData().putBoolean("zombierool:shotgun_pellet", true); // Marqueur pour le handler d'impact

                arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread + player.getRandom().nextFloat() * 0.5f);
                level.addFreshEntity(arrow);
            }

            // SEND RECOIL PACKET TO CLIENT
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            level.playSound(null, 
                player.getX(), player.getY(), player.getZ(),
                getFireSound(stack),
                SoundSource.PLAYERS, 0.8f, 1f // Volume légèrement réduit pour la cadence
            );
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
                return; 
            }
            setReloadTimer(stack, --t); 
            if (t <= 0) { 
                finishReload(stack, p, level);
            }
        }

        CompoundTag tag = stack.getOrCreateTag();
        boolean wasEquippedPreviously = tag.getBoolean(TAG_EQUIPPED_PREVIOUSLY);

        // Logic for playing the "weapon in hand" sound (magnum_ready)
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
            ? getTranslatedMessage("§6Le Rayon Arc", "§6The Arc Ray") // Translated PaP name
            : getTranslatedMessage("§fARC-12", "§fARC-12"); // Base name (proper noun, remains same)
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§l§eFusil à Pompe Compact et Rapide", "§l§eCompact and Fast Shotgun")));
        tips.add(Component.literal(getTranslatedMessage("§aIdéal pour nettoyer les hordes rapidement.", "§aIdeal for clearing hordes quickly.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage("§dNom PaP : Le Rayon Arc", "§dPaP Name: The Arc Ray")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts par plomb : " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET),
                "§dPellet Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET)
            )));
            tips.add(Component.literal(getTranslatedMessage("§dDispersion et recul réduits.", "§dReduced spread and recoil.")));
            tips.add(Component.literal(getTranslatedMessage("§dAugmentation de la vélocité des plombs.", "§dIncreased pellet velocity.")));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }

    // Pas de calculateAdjustedDamage pour l'instant si tu ne l'as pas mis en place sur tes flèches générales
    // Si tu veux l'ajouter, il faudra que tes flèches aient des propriétés pour cela.
}
