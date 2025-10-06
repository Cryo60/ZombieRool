package net.mcreator.zombierool.item;

import com.mojang.datafixers.util.Pair;
import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.api.IHeadshotWeapon;
import net.mcreator.zombierool.init.ZombieroolModSounds; // Assure-toi que ces sons existent !
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

import net.mcreator.zombierool.client.CherryReloadAnimationHandler; // Si tu l'utilises

import java.util.List;
import java.util.UUID;

import net.minecraft.util.RandomSource;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class SPAS12WeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO = 10;
    private static final int MAX_RESERVE = 80;
    private static final int COOLDOWN_TICKS = 10;
    private static final int RELOAD_TIME_PER_SHELL = 12;
    private static final int PUMP_SOUND_DURATION = 8;
    private static final int MIN_PUMP_FIRE_DELAY_TICKS = PUMP_SOUND_DURATION;

    private static final float WEAPON_DAMAGE_PER_PELLET = 10.5f; // Nerfé : Dégâts par plomb légèrement réduits
    private static final int NUM_PELLETS = 8;
    private static final float TOTAL_BASE_DAMAGE = WEAPON_DAMAGE_PER_PELLET * NUM_PELLETS; // Sera 108.0f maintenant

    private static final float PAP_BONUS_DAMAGE_PER_PELLET = 10.0f;
    private static final float TOTAL_PAP_BONUS_DAMAGE = PAP_BONUS_DAMAGE_PER_PELLET * NUM_PELLETS;

    private static final float BASE_HEADSHOT_DAMAGE = 2.0f;
    private static final float PAP_HEADSHOT_BONUS = 0.3f;

    private static final float BASE_PROJECTILE_VELOCITY = 2.2f;
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.2f;
    private static final float BASE_PROJECTILE_SPREAD = 2.7f; // Nerfé : Très légère augmentation de la dispersion de base
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.5f;

    private static final float BASE_RECOIL_PITCH = 2.5f;
    private static final float BASE_RECOIL_YAW = 0.5f;
    private static final float PAP_RECOIL_MULTIPLIER = 0.4f;

    private static final float EFFECTIVE_RANGE_SHORT = 5.0f;
    private static final float EFFECTIVE_RANGE_MEDIUM = 10.0f;
    private static final float MIN_DAMAGE_PERCENTAGE_AT_MEDIUM_RANGE = 0.4f;
    private static final float EFFECTIVE_RANGE_LONG = 15.0f;

    // --- UPDATED SOUND REFERENCES ---
    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.SPAS12_FIRE.get();
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get(); // Re-using generic upgraded sound
    private static final SoundEvent PUMP_SOUND = ZombieroolModSounds.SPAS12_RELOADING.get(); // Using the new spas12_reloading sound for pump
    private static final SoundEvent INSERT_SHELL_SOUND = ZombieroolModSounds.TRENCH_GUN_INSERT.get(); // Re-using Trench Gun insert sound
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get();
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();
    // --- END UPDATED SOUND REFERENCES ---

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_PUMP_TIMER = "PumpTimer";
    private static final String TAG_IS_RELOADING = "IsReloading";
    private static final String TAG_RELOAD_START_AMMO = "ReloadStartAmmo";

    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public SPAS12WeaponItem() {
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
        CompoundTag tag = stack.getOrCreateTag(); // Use getOrCreateTag for consistency
        if (!tag.contains("Ammo")) { // Check for "Ammo" as a primary indicator for initialization
            setAmmo(stack, MAX_AMMO);
            setReserve(stack, MAX_RESERVE);
            setReloadTimer(stack, 0);
            setReloading(stack, false);
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
            if (getAmmo(stack) == 0) {
                setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS);
            } else {
                setReloadTimer(stack, RELOAD_TIME_PER_SHELL);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), INSERT_SHELL_SOUND, SoundSource.PLAYERS, 1f, 1f);
            }
            setReloadStartAmmo(stack, getAmmo(stack));

            // Cherry Cola effect (copied from Trench Gun)
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
                    // Corrected type check for Animal
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

        if (isReloading(stack) && getAmmo(stack) > 0 && pumpTimer <= 0) {
            setReloading(stack, false);
            setReloadTimer(stack, 0);
        }

        if (pumpTimer > 0) {
            return;
        }

        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return;
        }

        if (getAmmo(stack) == 0 && getReserve(stack) > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
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
            float damagePerPellet = getDamagePerPellet(stack);

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
                arrow.setBaseDamage(damagePerPellet);
                arrow.getPersistentData().putBoolean("zombierool:shotgun_pellet", true);

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
            setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS);
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

        if (pumpTimer > 0) {
            setPumpTimer(stack, --pumpTimer);
            if (pumpTimer == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), PUMP_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }

        if (isReloading(stack)) {
            if (!sel || !p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                setReloading(stack, false);
                setReloadTimer(stack, 0);
                return;
            }

            if (t > 0) {
                setReloadTimer(stack, --t);
            }

            if (t <= 0) {
                if (getAmmo(stack) < MAX_AMMO && getReserve(stack) > 0) {
                    setAmmo(stack, getAmmo(stack) + 1);
                    setReserve(stack, getReserve(stack) - 1);

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
                        setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS);
                    }
                } else {
                    setReloading(stack, false);
                    setReloadTimer(stack, 0);
                    setPumpTimer(stack, MIN_PUMP_FIRE_DELAY_TICKS);
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
            ? getTranslatedMessage("§6Le Maître-Chasseur", "§6The Master Hunter") // Translated PaP name
            : getTranslatedMessage("§fSPAS-12", "§fSPAS-12"); // Base name (proper noun, remains same)
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§l§eFusil à Pompe Automatique", "§l§eAutomatic Shotgun")));
        tips.add(Component.literal(getTranslatedMessage("§aForce de frappe incroyable, rechargement rapide.", "§aIncredible stopping power, fast reload.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage("§dNom PaP : Le Maître-Chasseur", "§dPaP Name: The Master Hunter")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts par plomb : " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET),
                "§dPellet Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE_PER_PELLET)
            )));
            tips.add(Component.literal(getTranslatedMessage("§dDispersion et recul grandement réduits.", "§dGreatly reduced spread and recoil.")));
            tips.add(Component.literal(getTranslatedMessage("§dAugmentation de la vélocité des plombs.", "§dIncreased pellet velocity.")));
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
