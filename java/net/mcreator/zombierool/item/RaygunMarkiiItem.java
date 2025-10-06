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
import net.minecraft.world.entity.projectile.Arrow;
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

import net.mcreator.zombierool.client.CherryReloadAnimationHandler;
import java.util.List;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class RaygunMarkiiItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon, IHandgunWeapon {

    private static final int MAX_AMMO = 21; // 93, a nod to its original ammo count (93 bullets * 3 = 279, roughly 279 shots)
    private static final int MAX_RESERVE = 162; // 93 * 2. Reserve is separate, not a multiplier of MAX_AMMO in the traditional sense
    private static final int BURST_BULLETS = 3; // Fires 3 precise laser shots per burst
    private static final int BURST_INTERVAL_TICKS = 1; // Very fast interval between shots in a burst
    private static final int BURST_COOLDOWN_TICKS = 8; // Cooldown after a full burst (slightly longer than base Raygun fire rate)
    private static final int RELOAD_TIME = 70; // Longer reload for a wonder weapon

    // Damage Parameters (High for a 10/10 weapon, especially with burst)
    private static final float WEAPON_DAMAGE = 15.0f; // High base damage per projectile
    private static final float PAP_BONUS_DAMAGE = 25.0f; // Very significant bonus for a 10/10 wonder weapon

    // Headshot Multipliers (Critical for wonder weapons)
    private static final float BASE_HEADSHOT_DAMAGE = 5.0f; // Huge base headshot multiplier
    private static final float PAP_HEADSHOT_BONUS = 8.0f; // Even bigger bonus for PaP headshots

    // Projectile Parameters (Lasers should be extremely fast and precise)
    private static final float BASE_PROJECTILE_VELOCITY = 8.0f; // Extremely fast projectile (near instant hitscan feel)
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.05f; // Slight velocity increase for perfection
    private static final float BASE_PROJECTILE_SPREAD = 0.05f; // Extremely precise base
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 1.0f; // No spread improvement, already perfect

    // Recoil Parameters (Wonder weapons should feel powerful but controllable)
    private static final float BASE_RECOIL_PITCH = 0.8f; // Moderate vertical recoil for a burst weapon
    private static final float BASE_RECOIL_YAW = 0.2f; // Low horizontal recoil
    private static final float PAP_RECOIL_MULTIPLIER = 0.6f; // Significant recoil reduction for PaP

    // Sounds (Unique and impactful for a wonder weapon)
    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.RAY_GUN_MK2_FIRE.get(); // Define this sound
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get(); // Define this sound
    private static final SoundEvent RELOAD_SOUND = ZombieroolModSounds.RAY_GUN_MK2_RELOADING.get(); // Define this sound
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get(); // Reusing laser dry sound
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_SHOT_TICK = "LastShotTick"; // For global burst cooldown
    private static final String TAG_BURST_COUNTER = "BurstCounter"; // To track bullets fired in a burst
    private static final String TAG_NEXT_BURST_TICK = "NextBurstTick"; // For delay between burst bullets
    private static final String TAG_LASER_UPGRADED = "IsLaserUpgraded"; // Used by the projectile logic for visual/damage effects

    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public RaygunMarkiiItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC)); // Epic rarity for a 10/10 wonder weapon
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

    @Override public int getAmmo(ItemStack s) { return getOrCreateTag(s).getInt("Ammo"); }
    @Override public void setAmmo(ItemStack s, int a) { getOrCreateTag(s).putInt("Ammo", a); }
    @Override public int getReserve(ItemStack s) { return getOrCreateTag(s).getInt("Reserve"); }
    @Override public void setReserve(ItemStack s, int r) { getOrCreateTag(s).putInt("Reserve", r); }
    @Override public int getReloadTimer(ItemStack s) { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }

    public long getLastShotTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_SHOT_TICK); }
    public void setLastShotTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_SHOT_TICK, tick); }

    public int getBurstCounter(ItemStack stack) { return getOrCreateTag(stack).getInt(TAG_BURST_COUNTER); }
    public void setBurstCounter(ItemStack stack, int count) { getOrCreateTag(stack).putInt(TAG_BURST_COUNTER, count); }

    public long getNextBurstTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_NEXT_BURST_TICK); }
    public void setNextBurstTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_NEXT_BURST_TICK, tick); }

    @Override public int getMaxAmmo() { return MAX_AMMO; }
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
        if (!tag.contains(TAG_LAST_SHOT_TICK)) {
            tag.putLong(TAG_LAST_SHOT_TICK, 0);
        }
        if (!tag.contains(TAG_BURST_COUNTER)) {
            tag.putInt(TAG_BURST_COUNTER, 0);
        }
        if (!tag.contains(TAG_NEXT_BURST_TICK)) {
            tag.putLong(TAG_NEXT_BURST_TICK, 0);
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
                setBurstCounter(stack, 0); // Reset burst counter on reload
                setNextBurstTick(stack, 0); // Reset burst timer
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
                        if (target instanceof Monster || (target instanceof TamableAnimal && !((TamableAnimal)target).isTame()) || !(target instanceof TamableAnimal)) {
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

        long currentTick = level.getGameTime();

        // Handle reload
        if (getReloadTimer(stack) > 0) {
            return;
        }

        // Check for global burst cooldown or internal burst interval
        long lastShotTick = getLastShotTick(stack);
        long nextBurstTick = getNextBurstTick(stack);
        int burstCounter = getBurstCounter(stack);

        // If a burst is in progress, check burst interval
        if (burstCounter > 0 && currentTick < nextBurstTick) {
            return;
        }

        // If not in burst, check global burst cooldown
        if (burstCounter == 0 && currentTick - lastShotTick < BURST_COOLDOWN_TICKS) {
            return;
        }

        // Handle out of ammo
        if (getAmmo(stack) == 0) {
            if (getReserve(stack) > 0) {
                startReload(stack, player);
            } else {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            }
            return;
        }

        // Fire a projectile
        if (getAmmo(stack) > 0) {
            Vec3 start = player.getEyePosition(1F);
            Vec3 dir = player.getViewVector(1F);

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
            arrow.setBaseDamage(0.0D); // Damage handled by ArrowImpactHandler

            // Add the NBT tag to indicate if the laser projectile is upgraded
            arrow.getPersistentData().putBoolean(TAG_LASER_UPGRADED, isPackAPunched(stack));

            arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread);
            level.addFreshEntity(arrow);

            // Send recoil packet to client
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            // Play fire sound
            level.playSound(null, player.getX(), player.getY(), player.getZ(), getFireSound(stack), SoundSource.PLAYERS, 0.9f, 1.2f);
            setAmmo(stack, getAmmo(stack) - 1); // Consume ammo

            // Update burst state
            setBurstCounter(stack, burstCounter + 1);
            setNextBurstTick(stack, currentTick + BURST_INTERVAL_TICKS);

            if (getBurstCounter(stack) >= BURST_BULLETS) {
                // Burst finished, reset counter and set global cooldown
                setBurstCounter(stack, 0);
                setLastShotTick(stack, currentTick); // This is the start of the full burst cooldown
            }
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
                setReloadTimer(stack, 0); // Reset timer to 0
                setBurstCounter(stack, 0); // Reset burst counter if weapon is unequipped during burst
                setNextBurstTick(stack, 0); // Reset burst interval if weapon is unequipped
                return;
            }
            setReloadTimer(stack, --t);
            if (t <= 0) {
                finishReload(stack, p, level);
            }
        }

        // Ensure burst counter is reset if player stops "using" the item (releases mouse button)
        // This is important to ensure a new burst starts when the player clicks again,
        // rather than continuing a partial burst from a previous hold.
        if (!p.isUsingItem() && getBurstCounter(stack) > 0) {
            setBurstCounter(stack, 0);
            setLastShotTick(stack, level.getGameTime()); // Apply cooldown if burst was interrupted
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
            ? getTranslatedMessage("§bRaygun Mark II (Diodes de la Mort)", "§bRaygun Mark II (Diodes of Death)")
            : getTranslatedMessage("§3Raygun Mark II", "§3Raygun Mark II");
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§9Arme Éclatante : Tire des rafales de 3 lasers.", "§9Blazing Weapon: Fires 3-laser bursts.")));
        tips.add(Component.literal(getTranslatedMessage("§9Excellent pour les vagues tardives.", "§9Excellent for late waves.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§bAméliorée via Pack-a-Punch", "§bUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dDégâts par rafale : " + String.format("%.1f", (WEAPON_DAMAGE + PAP_BONUS_DAMAGE) * BURST_BULLETS),
                "§dDamage per burst: " + String.format("%.1f", (WEAPON_DAMAGE + PAP_BONUS_DAMAGE) * BURST_BULLETS)
            )));
            tips.add(Component.literal(getTranslatedMessage(
                "§dMultiplicateur Headshot PaP : x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE * PAP_HEADSHOT_BONUS),
                "§dPaP Headshot Multiplier: x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE * PAP_HEADSHOT_BONUS)
            )));
        } else {
            tips.add(Component.literal(getTranslatedMessage(
                "§3Dégâts par rafale : " + String.format("%.1f", WEAPON_DAMAGE * BURST_BULLETS),
                "§3Damage per burst: " + String.format("%.1f", WEAPON_DAMAGE * BURST_BULLETS)
            )));
            tips.add(Component.literal(getTranslatedMessage(
                "§3Multiplicateur Headshot : x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE),
                "§3Headshot Multiplier: x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE)
            )));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
