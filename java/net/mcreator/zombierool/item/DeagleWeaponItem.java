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
import net.minecraft.sounds.SoundEvents; // Added for quest completion sound
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

import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
// Removed redundant MobCategory import, as it was causing issues and won't be used directly for specific checks
// import net.minecraft.world.entity.MobCategory; 
import net.minecraft.world.entity.monster.Zombie; // Added for specific zombie checks
import net.minecraft.world.entity.monster.Skeleton; // Added for specific skeleton checks
import net.minecraft.world.entity.monster.Husk;     // Added for specific husk checks
import net.minecraft.world.entity.monster.Stray;    // Added for specific stray checks
import net.minecraft.world.entity.monster.Drowned;  // Added for specific drowned checks


import net.mcreator.zombierool.client.CherryReloadAnimationHandler;
import java.util.List;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class DeagleWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon, IHandgunWeapon {

    private static final int MAX_AMMO = 7; // Très faible capacité de chargeur
    private static final int MAX_RESERVE = MAX_AMMO * 9; // Réserve faible
    private static final int COOLDOWN_TICKS = 10; // Cadence de tir très lente (semi-auto)
    private static final int RELOAD_TIME = 50; // Rechargement lent
    
    private static final float WEAPON_DAMAGE = 16.5f; // Très gros dégâts par balle
    private static final float PAP_BONUS_DAMAGE = 18.0f; // Excellent bonus Pack-a-Punch pour des dégâts massifs
    
    private static final float BASE_HEADSHOT_DAMAGE = 3.0f; // Très haut multiplicateur headshot de base
    private static final float PAP_HEADSHOT_BONUS = 3.5f; // Bonus headshot si PaP encore plus fou
    
    private static final float BASE_PROJECTILE_VELOCITY = 3.5f; // Très rapide
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.05f;
    private static final float BASE_PROJECTILE_SPREAD = 0.7f; // Précis, mais pas parfait pour équilibrer
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.8f;

    private static final float BASE_RECOIL_PITCH = 9.5f; // Énorme recul vertical
    private static final float BASE_RECOIL_YAW = 1.0f; // Gros recul horizontal
    private static final float PAP_RECOIL_MULTIPLIER = 0.8f;

    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.DEAGLE_FIRE.get(); // À définir
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.DEAGLE_FIRE.get();
    private static final SoundEvent RELOAD_SOUND = ZombieroolModSounds.DEAGLE_RELOADING.get(); // À définir
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get();
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";

    // --- New Quest Tags ---
    private static final String TAG_ZOMBIE_KILLS = "DeagleZombieKills";
    private static final String TAG_GOLDEN_DEAGLE = "IsGoldenDeagle";
    private static final int QUEST_KILL_THRESHOLD = 100; // 100 kills for the golden Deagle
    // --- End New Quest Tags ---

    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public DeagleWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE)); // Rareté Rare pour une arme si puissante
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

    @Override public int  getAmmo(ItemStack s)        { return getOrCreateTag(s).getInt("Ammo"); }
    @Override public void setAmmo(ItemStack s, int a)    { getOrCreateTag(s).putInt("Ammo", a); }
    @Override public int  getReserve(ItemStack s)        { return getOrCreateTag(s).getInt("Reserve"); }
    @Override public void setReserve(ItemStack s, int r) { getOrCreateTag(s).putInt("Reserve", r); }
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }

    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    // --- New Quest Getters/Setters ---
    public int getZombieKills(ItemStack s) { return getOrCreateTag(s).getInt(TAG_ZOMBIE_KILLS); }
    public void setZombieKills(ItemStack s, int kills) { getOrCreateTag(s).putInt(TAG_ZOMBIE_KILLS, kills); }
    public boolean isGoldenDeagle(ItemStack s) { return getOrCreateTag(s).getBoolean(TAG_GOLDEN_DEAGLE); }
    public void setGoldenDeagle(ItemStack s, boolean isGolden) { getOrCreateTag(s).putBoolean(TAG_GOLDEN_DEAGLE, isGolden); }
    // --- End New Quest Getters/Setters ---

    @Override public int getMaxAmmo()    { return MAX_AMMO; }
    @Override public int getMaxReserve() { return MAX_RESERVE; }

    @Override
    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag(); // Use getOrCreateTag for consistency
        
        // Initialize existing weapon stats tags
        if (!tag.contains("Ammo")) {
            setAmmo(stack, MAX_AMMO);
        }
        if (!tag.contains("Reserve")) {
            setReserve(stack, MAX_RESERVE);
        }
        if (!tag.contains("ReloadTimer")) {
            setReloadTimer(stack, 0);
        }
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_LAST_FIRE_TICK)) {
            tag.putLong(TAG_LAST_FIRE_TICK, 0);
        }
        if (!tag.contains(TAG_PAP)) { // Ensure PAP tag is initialized
            tag.putBoolean(TAG_PAP, false);
        }

        // --- New Quest Initialization ---
        if (!tag.contains(TAG_ZOMBIE_KILLS)) {
            tag.putInt(TAG_ZOMBIE_KILLS, 0);
        }
        if (!tag.contains(TAG_GOLDEN_DEAGLE)) {
            tag.putBoolean(TAG_GOLDEN_DEAGLE, false);
        }
        // --- End New Quest Initialization ---
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
                        // Cherry Cola effect applies to hostile mobs or untamed animals
                        // Explicitly check for common zombie-like mobs and general Monsters
                        if (target instanceof Zombie || target instanceof Skeleton || target instanceof Husk || target instanceof Stray || target instanceof Drowned || target instanceof Monster) {
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
    @Override public int      getUseDuration(ItemStack s)  { return 72000; }

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

            Arrow arrow = new Arrow(level, player);
            arrow.setOwner(player);
            // Store a reference to the Deagle's NBT on the arrow for kill tracking
            // This assumes your custom damage logic or a LivingDeathEvent handler can read this.
            arrow.getPersistentData().putUUID("shooterUUID", player.getUUID()); 
            arrow.getPersistentData().put("DeagleWeaponTag", stack.copy().save(new CompoundTag())); // Save a copy of the stack's NBT
            
            arrow.setPos(start.x, start.y, start.z);
            arrow.setSilent(true);
            arrow.setNoGravity(true);
            arrow.pickup = Pickup.DISALLOWED;
            arrow.getPersistentData().putBoolean("zombierool:invisible", true);
            arrow.getPersistentData().putBoolean("zombierool:small", true);
            arrow.setInvisible(true);
            arrow.setBaseDamage(0.0D); // Damage is applied by a custom handler, not the arrow itself
            
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
            setLastFireTick(stack, currentTick);

        }
    }

    /**
     * This method should be called from your global Forge event handler (e.g., LivingDeathEvent).
     * It tracks zombie kills and handles the golden Deagle quest completion.
     * @param deagleStack The Deagle ItemStack that killed the entity.
     * @param killer The player who used the Deagle.
     * @param killedEntity The entity that was killed.
     */
    public void onZombieKilled(ItemStack deagleStack, Player killer, LivingEntity killedEntity) {
        // Only proceed if the Deagle is not already golden
        if (isGoldenDeagle(deagleStack)) {
            return;
        }

        // Check if the killed entity is a zombie-like monster
        // Using specific instanceof checks instead of MobCategory.UNDEAD to avoid compilation issues.
        if (killedEntity instanceof Zombie || killedEntity instanceof Skeleton || killedEntity instanceof Husk || killedEntity instanceof Stray || killedEntity instanceof Drowned || killedEntity instanceof Monster) {
            int currentKills = getZombieKills(deagleStack);
            setZombieKills(deagleStack, currentKills + 1);

            // Check for quest completion
            if (getZombieKills(deagleStack) >= QUEST_KILL_THRESHOLD) {
                setGoldenDeagle(deagleStack, true);
                setZombieKills(deagleStack, 0); // Reset kill count after achieving golden status

                if (!killer.level().isClientSide) {
                    // Play a celebratory sound
                    killer.level().playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                    // Send a message to the player
                    killer.sendSystemMessage(Component.literal(getTranslatedMessage("§6Votre Desert Eagle est devenu §eL'Aigle Doré§6 !", "§6Your Desert Eagle has become §eThe Golden Eagle§6!")));
                    
                    // Force client to update the item model for visual change
                    if (killer instanceof ServerPlayer sp) {
                        sp.connection.send(new ClientboundSetEquipmentPacket(
                            sp.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, deagleStack))));
                    }
                }
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
                return;
            }
            setReloadTimer(stack, --t);
            if (t <= 0) {
                finishReload(stack, p, level);
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
        boolean isGolden = isGoldenDeagle(stack); // Check for golden state

        String name;
        if (isGolden) {
            name = getTranslatedMessage("§6L'Aigle Doré", "§6The Golden Eagle"); // Golden Name: Gold color code
        } else if (upgraded) {
            name = getTranslatedMessage("§dDeagle Amélioré", "§dUpgraded Deagle"); // Pack-a-Punch name: Purple color code
        } else {
            name = getTranslatedMessage("§7Desert Eagle", "§7Desert Eagle"); // Base name: Grey color code
        }
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        boolean isGolden = isGoldenDeagle(stack);

        tips.add(Component.literal(getTranslatedMessage("§ePistolet lourd à fort impact, parfait pour les cibles isolées.", "§eHeavy, high-impact pistol, perfect for single targets.")));

        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dDégâts PaP : " + String.format("%.1f", WEAPON_DAMAGE + PAP_BONUS_DAMAGE),
                "§dPaP Damage: " + String.format("%.1f", WEAPON_DAMAGE + PAP_BONUS_DAMAGE)
            )));
            // Corrected PAP Headshot calculation based on the new structure
            tips.add(Component.literal(getTranslatedMessage(
                "§dMultiplicateur Headshot PaP : x" + String.format("%.1f", PAP_HEADSHOT_BONUS),
                "§dPaP Headshot Multiplier: x" + String.format("%.1f", PAP_HEADSHOT_BONUS)
            )));
        }

        if (!isGolden) {
            // Show quest progress only if not yet golden
            tips.add(Component.literal(getTranslatedMessage("§aProgression Quête : " + getZombieKills(stack) + "/" + QUEST_KILL_THRESHOLD + " zombies tués", "§aQuest Progress: " + getZombieKills(stack) + "/" + QUEST_KILL_THRESHOLD + " zombies killed")));
        } else {
            tips.add(Component.literal(getTranslatedMessage("§6Débloqué : Aspect Doré !", "§6Unlocked: Golden Aspect!")));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // If it's golden or Pack-a-Punched, it should always be shiny
        return isGoldenDeagle(stack) || isPackAPunched(stack) || super.isFoil(stack);
    }
}
