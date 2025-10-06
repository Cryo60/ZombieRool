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
import net.minecraft.world.entity.animal.Animal; // Added missing import for Animal

import net.mcreator.zombierool.client.CherryReloadAnimationHandler;
import java.util.List;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class VectorWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO = 25; // Taille de chargeur modérée, mais la cadence de tir est la clé
    private static final int MAX_RESERVE = MAX_AMMO * 6; // Bonne réserve pour compenser la consommation rapide
    private static final int BURST_BULLETS = 3; // Tir en rafale de 3 balles
    private static final int BURST_INTERVAL_TICKS = 1; // **TRÈS HAUTE CADENCE DE TIR** (0.05s entre balles dans la rafale)
    private static final int BURST_COOLDOWN_TICKS = 8; // Cooldown court entre les rafales pour un DPS soutenu
    private static final int RELOAD_TIME = 40; // Rechargement assez rapide (2s)
    
    private static final float WEAPON_DAMAGE = 4.0f; // Dégâts par balle plus faibles, compensés par la rafale ultra-rapide
    private static final float PAP_BONUS_DAMAGE = 6.0f; // Bon bonus Pack-a-Punch pour monter rapidement le DPS
    
    private static final float BASE_HEADSHOT_DAMAGE = 1.9f; // Multiplicateur headshot élevé
    private static final float PAP_HEADSHOT_BONUS = 6.0f; 
    // Dégâts de base du headshot : 4.0 * 1.9 = 7.6
    // Dégâts PaP du headshot : (4.0 + 6.0) * 1.9 + 6.0 = 10.0 * 1.9 + 6.0 = 19.0 + 6.0 = 25.0

    private static final float BASE_PROJECTILE_VELOCITY = 3.2f; // Bonne vitesse de projectile
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.15f; 
    private static final float BASE_PROJECTILE_SPREAD = 0.9f; // Dispersion contrôlée pour une rafale rapide
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.6f; // Amélioration de la précision PaP

    private static final float BASE_RECOIL_PITCH = 0.5f; // Recul vertical très modéré
    private static final float BASE_RECOIL_YAW = 0.3f; // Très faible recul horizontal
    private static final float PAP_RECOIL_MULTIPLIER = 0.5f; // Très bon gain sur le recul PaP

    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.VECTOR_FIRE.get(); // À définir
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get(); 
    private static final SoundEvent RELOAD_SOUND = ZombieroolModSounds.VECTOR_RELOADING.get(); // À définir
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get(); 
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_SHOT_TICK = "LastShotTick"; // Pour le cooldown global de la rafale
    private static final String TAG_BURST_COUNTER = "BurstCounter"; // Pour suivre les balles tirées dans une rafale
    private static final String TAG_NEXT_BURST_TICK = "NextBurstTick"; // Pour le délai entre les balles de la rafale

    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public VectorWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
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
            ? WEAPON_DAMAGE + PAP_BONUS_DAMAGE
            : WEAPON_DAMAGE;
    }
    
    @Override
    public float getHeadshotBaseDamage(ItemStack stack) {
        // La méthode IHeadshotWeapon.getHeadshotBaseDamage doit retourner un MULTIPLICATEUR
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

    public long getLastShotTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_SHOT_TICK); }
    public void setLastShotTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_SHOT_TICK, tick); }

    public int getBurstCounter(ItemStack stack) { return getOrCreateTag(stack).getInt(TAG_BURST_COUNTER); }
    public void setBurstCounter(ItemStack stack, int count) { getOrCreateTag(stack).putInt(TAG_BURST_COUNTER, count); }

    public long getNextBurstTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_NEXT_BURST_TICK); }
    public void setNextBurstTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_NEXT_BURST_TICK, tick); }


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
        // Start burst if not already in one or on cooldown
        if (getReloadTimer(player.getItemInHand(hand)) == 0 && getBurstCounter(player.getItemInHand(hand)) == 0 && level.getGameTime() >= getLastShotTick(player.getItemInHand(hand)) + BURST_COOLDOWN_TICKS) {
            setBurstCounter(player.getItemInHand(hand), BURST_BULLETS);
            setNextBurstTick(player.getItemInHand(hand), level.getGameTime());
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity ent, ItemStack stack, int count) {
        if (!(ent instanceof Player player) || level.isClientSide) return;
        initializeIfNeeded(stack);

        if (getReloadTimer(stack) > 0) {
            setBurstCounter(stack, 0); // Stop burst if reloading
            return;
        }

        long currentTick = level.getGameTime();
        
        // Handle global cooldown after a full burst
        if (currentTick < getLastShotTick(stack) + BURST_COOLDOWN_TICKS && getBurstCounter(stack) == 0) {
            return;
        }

        // If a burst is active and it's time for the next bullet
        if (getBurstCounter(stack) > 0 && currentTick >= getNextBurstTick(stack)) {
            if (getAmmo(stack) == 0 && getReserve(stack) > 0) {
                startReload(stack, player);
                setBurstCounter(stack, 0); // Stop burst if reloading
                return;
            }
            
            if (getAmmo(stack) == 0 && getReserve(stack) == 0) {
                level.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    DRY_FIRE_SOUND,
                    SoundSource.PLAYERS, 0.7f, 1f
                );
                setBurstCounter(stack, 0); // Stop burst on dry fire
                return;
            }

            // Shoot a bullet
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
            
            arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread);
            
            level.addFreshEntity(arrow);

            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                getFireSound(stack),
                SoundSource.PLAYERS, 0.7f, 1f
            );
            setAmmo(stack, getAmmo(stack) - 1);
            
            setBurstCounter(stack, getBurstCounter(stack) - 1);
            setNextBurstTick(stack, currentTick + BURST_INTERVAL_TICKS);

            if (getBurstCounter(stack) == 0) { // If burst finished
                setLastShotTick(stack, currentTick); // Set global cooldown
                player.stopUsingItem(); // Stop using item after burst
            }
        } else if (getBurstCounter(stack) == 0 && player.isUsingItem() && currentTick >= getLastShotTick(stack) + BURST_COOLDOWN_TICKS) {
             // If player holds down and not in burst/on cooldown, start a new burst
             if (getAmmo(stack) > 0) {
                 setBurstCounter(stack, BURST_BULLETS);
                 setNextBurstTick(stack, currentTick); // Fire first bullet immediately
             } else if (getReserve(stack) > 0) {
                 startReload(stack, player);
             } else {
                 level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
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
    
        if (t > 0) {
            if (!sel || !p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                setReloadTimer(stack, 0);
                setBurstCounter(stack, 0); // Important: Stop burst if unequipping
                setNextBurstTick(stack, 0);
                return; 
            }
            setReloadTimer(stack, --t);
            if (t <= 0) {
                finishReload(stack, p, level);
            }
        }

        // If the player stops using the item (e.g. releases right click), reset the burst
        if (!p.isUsingItem() && getBurstCounter(stack) > 0) {
            setBurstCounter(stack, 0);
            setNextBurstTick(stack, 0);
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
            ? getTranslatedMessage("§dBlaze Striker", "§dBlaze Striker") // PaP name, remains same as it's a proper noun-like name
            : getTranslatedMessage("§2Vector", "§2Vector"); // Base name, remains same
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§ePistolet-mitrailleur tirant en rafale ultra-rapide.", "§eUltra-fast burst-fire submachine gun.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dDégâts PaP (par balle) : " + String.format("%.1f", WEAPON_DAMAGE + PAP_BONUS_DAMAGE),
                "§dPaP Damage (per bullet): " + String.format("%.1f", WEAPON_DAMAGE + PAP_BONUS_DAMAGE)
            )));
            tips.add(Component.literal(getTranslatedMessage(
                "§dMultiplicateur Headshot PaP : x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE), // Note: Le bonus PaP s'ajoute, ce n'est pas un multiplicateur du multiplicateur
                "§dPaP Headshot Multiplier: x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE)
            )));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}