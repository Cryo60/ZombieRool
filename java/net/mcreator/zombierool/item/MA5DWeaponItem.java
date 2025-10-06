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
import net.minecraft.client.Minecraft; // IMPORT ADDED

import net.mcreator.zombierool.client.CherryReloadAnimationHandler;

// Import for RecoilPacket (YOU NEED TO CREATE THIS PACKET IN MCreator FIRST)
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class MA5DWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO       = 32; // Halo 5 MA5D magazine size
    private static final int MAX_RESERVE    = MAX_AMMO * 7; // Generous reserve ammo
    private static final int COOLDOWN_TICKS = 2; // High rate of fire for an automatic rifle
    private static final int RELOAD_TIME    = 50; // ticks (2 seconds)

    private static final float WEAPON_DAMAGE = 8.5f; // Moderate damage per shot
    private static final float PAP_BONUS_DAMAGE = 6.0f; // Significant damage boost

    private static final float BASE_HEADSHOT_DAMAGE = 10.0f; // Direct headshot damage
    private static final float PAP_HEADSHOT_BONUS = 6.0f;    // Bonus headshot damage if PaP

    // Projectile Parameters
    private static final float BASE_PROJECTILE_VELOCITY = 4.5f; // Faster projectile
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.15f; // Speed multiplier for PaP
    private static final float BASE_PROJECTILE_SPREAD = 0.4f; // Moderate spread
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.4f; // Significant spread reduction for PaP

    // Recoil Parameters
    private static final float BASE_RECOIL_PITCH = 0.6f; // Vertical recoil
    private static final float BASE_RECOIL_YAW = 0.2f;   // Horizontal recoil
    private static final float PAP_RECOIL_MULTIPLIER = 0.5f; // Halves recoil for PaP

    private static final SoundEvent FIRE_SOUND           = ZombieroolModSounds.MA5D_FIRE.get(); // Specific MA5D fire sound
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get(); // Generic upgraded sound
    private static final SoundEvent RELOAD_SOUND         = ZombieroolModSounds.MA5D_RELOADING.get(); // Specific MA5D reload sound
    private static final SoundEvent DRY_FIRE_SOUND       = ZombieroolModSounds.RIFLE_DRY.get(); // Generic rifle dry fire
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get(); // Generic weapon equip sound
    private static final SoundEvent READY_SOUND          = ZombieroolModSounds.MA5D_READY.get(); // **NEW SOUND EVENT**

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_READY_SOUND_PLAYED = "ReadySoundPlayed";

    // --- Inner class for Cherry Cola Effects (retained from M16A4) ---
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f; // 10% of max health
        public static final float MAX_DISTANCE_DAMAGE = 1.5f; // Max flat damage bonus when very close
        public static final int STUN_DURATION_TICKS = 80;
    }
    // --- End of inner class ---

    public MA5DWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    // =========================================================================
    // START OF TRANSLATION METHODS (Copied from DefenseDoorBlock)
    // =========================================================================

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

    // =========================================================================
    // END OF TRANSLATION METHODS
    // =========================================================================


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
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Ammo")) {
            stack.setTag(new CompoundTag());
            setAmmo(stack, MAX_AMMO);
            setReserve(stack, MAX_RESERVE);
            setReloadTimer(stack, 0);
            tag = stack.getTag();
        }
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_LAST_FIRE_TICK)) {
            tag.putLong(TAG_LAST_FIRE_TICK, 0);
        }
        if (!tag.contains(TAG_READY_SOUND_PLAYED)) {
            tag.putBoolean(TAG_READY_SOUND_PLAYED, false);
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

                // **Reset Ready Sound Played tag at start of reload**
                stack.getOrCreateTag().putBoolean(TAG_READY_SOUND_PLAYED, false);

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

        // **Play MA5D_READY sound after reload finishes**
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), READY_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
        System.out.println("DEBUG: Playing READY_SOUND after reload for " + stack.getDisplayName().getString());
        stack.getOrCreateTag().putBoolean(TAG_READY_SOUND_PLAYED, true); // Mark as played
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
        if (!(ent instanceof Player player) || level.isClientSide) return; // onUseTick runs client-side too, handle server-side only logic
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
            level.playSound(null, // This sound is not tied to the player, so it will play at the world position.
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
            arrow.setBaseDamage(0.0D); // Base damage is handled by ArrowImpactHandler

            arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread);

            level.addFreshEntity(arrow);

            // SEND RECOIL PACKET TO CLIENT
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            level.playSound(null, // This sound is not tied to the player, so it will play at the world position.
                player.getX(), player.getY(), player.getZ(),
                getFireSound(stack),
                SoundSource.PLAYERS, 0.7f, 1f
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
            // Check if the item is no longer selected OR the item in hand is not this specific stack
            if (!sel || !p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                setReloadTimer(stack, 0); // Reset timer to 0
                System.out.println("DEBUG: Reload interrupted for " + stack.getDisplayName().getString() + " (not selected or wrong item). Timer reset to 0.");
                stack.getOrCreateTag().putBoolean(TAG_READY_SOUND_PLAYED, false); // Reset ready sound tag if interrupted
                return; // Stop processing this tick for reload
            }
            setReloadTimer(stack, --t); // Decrement timer
            if (t <= 0) { // If timer finishes
                finishReload(stack, p, level);
                System.out.println("DEBUG: Reload finished for " + stack.getDisplayName().getString() + ".");
            }
        }

        CompoundTag tag = stack.getOrCreateTag();
        boolean wasEquippedPreviously = tag.getBoolean(TAG_EQUIPPED_PREVIOUSLY);
        boolean readySoundPlayed = tag.getBoolean(TAG_READY_SOUND_PLAYED);

        // Logic for playing the "weapon in hand" sound and "ready" sound when equipped
        if (sel && !wasEquippedPreviously) {
            // Ensure the sound only plays when equipped AND it's the main hand item
            if (p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                System.out.println("DEBUG: Playing WEAPON_IN_HAND_SOUND for " + stack.getDisplayName().getString());

                // Play the READY_SOUND after a short delay or directly after WEAPON_IN_HAND_SOUND
                // A short delay (e.g., 5-10 ticks) might make it sound better if WEAPON_IN_HAND is long
                // For simplicity, we'll play it immediately after, checking it hasn't been played already.
                if (!readySoundPlayed) {
                     level.playSound(null, p.getX(), p.getY(), p.getZ(), READY_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                     System.out.println("DEBUG: Playing READY_SOUND on equip for " + stack.getDisplayName().getString());
                     tag.putBoolean(TAG_READY_SOUND_PLAYED, true); // Mark as played
                }
            }
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, true);
        } else if (!sel && wasEquippedPreviously) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
            tag.putBoolean(TAG_READY_SOUND_PLAYED, false); // Reset ready sound tag when unequipped
            System.out.println("DEBUG: " + stack.getDisplayName().getString() + " unequipped. Resetting TAG_EQUIPPED_PREVIOUSLY and TAG_READY_SOUND_PLAYED.");
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);

        String frenchName = upgraded ? "§3Démon d'Onyx" : "§2Fusil d'Assaut MA5D";
        String englishName = upgraded ? "§3Onyx Demon" : "§2MA5D Assault Rifle";
        
        return Component.literal(getTranslatedMessage(frenchName, englishName));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        
        // Base description (UNSC)
        tips.add(Component.literal(getTranslatedMessage(
            "§eFusil d'assaut automatique de l'UNSC",
            "§eUNSC Automatic Assault Rifle"
        )));

        if (upgraded) {
            // Upgraded status
            tips.add(Component.literal(getTranslatedMessage(
                "§dAméliorée via Pack-a-Punch",
                "§dUpgraded via Pack-a-Punch"
            )));
            
            // PaP damage bonus
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts PaP : ",
                "§dPaP Damage Bonus: "
            ) + String.format("%.1f", getHeadshotPapBonusDamage(stack))));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
