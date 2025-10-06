package net.mcreator.zombierool.item;

import com.mojang.datafixers.util.Pair;
import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable; // Keep this import even if not used for PaP logic in Whisper
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

import net.mcreator.zombierool.client.CherryReloadAnimationHandler;
import java.util.List;
import java.util.Random;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;
// NEW IMPORT for the custom packet
import net.mcreator.zombierool.network.StopFourIsReadySoundPacket; 

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class WhisperWeaponItem extends Item implements IReloadable, ICustomWeapon, IHeadshotWeapon, IHandgunWeapon {

    private static final int MAX_AMMO = 4; // Chargeur de 4 balles
    private static final int MAX_RESERVE = 0; // Munitions infinies, gérées par le code
    private static final int COOLDOWN_TICKS = 10; // Cadence de tir (ajustez si besoin pour 10/10)
    private static final int RELOAD_TIME = 40; // temps de rechargement (2s, ajustez pour 10/10)

    private static final float WEAPON_DAMAGE = 18.0f; // Dégâts de base par balle (les 3 premières)
    
    // Dégâts de la dernière balle
    private static final float LAST_BULLET_MIN_DAMAGE = 44.0f; // Minimum des dégâts de la dernière balle (multiple de 4)
    private static final float LAST_BULLET_MAX_DAMAGE = 84.0f; // Maximum des dégâts de la dernière balle (multiple de 4)
    private static final float LAST_BULLET_MAX_HP_PERCENT_DAMAGE = 0.44f; // 44% des PV max pour la dernière balle


    private static final float BASE_HEADSHOT_DAMAGE = 20.0f; // Dégâts de base du headshot

    // Paramètres de tir
    private static final float BASE_PROJECTILE_VELOCITY = 4.0f; // Vitesse du projectile
    private static final float BASE_PROJECTILE_SPREAD = 0.1f; // Dispersion (très précis)

    // Paramètres de Recul
    private static final float BASE_RECOIL_PITCH = 1.0f; // Recul en haut/bas
    private static final float BASE_RECOIL_YAW = 0.5f;   // Recul gauche/droite

    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.WHISPER_FIRE.get(); // Son de tir (à définir dans ZombieroolModSounds)
    private static final SoundEvent RELOAD_SOUND = ZombieroolModSounds.WHISPER_RELOADING.get(); // Son de rechargement (à définir)
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get(); // Son de tir à vide
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get(); // Son de prise en main
    private static final SoundEvent FOUR_IS_READY_SOUND = ZombieroolModSounds.FOUR_IS_READY.get(); // Son "four_is_ready"

    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_FOUR_IS_READY_PLAYING = "FourIsReadyPlaying"; // Pour gérer l'état du son

    private Random random = new Random();

    // --- Classe interne pour Cherry Cola Effects ---
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f; // 10% of max health
        public static final float MAX_DISTANCE_DAMAGE = 1.5f; // Max flat damage bonus when very close
        public static final int STUN_DURATION_TICKS = 80;
    }
    // --- Fin de la classe interne ---

    public WhisperWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE)); // Rareté RARE pour une arme 10/10
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
    
    // Renvoie le dégât de base. Le calcul des dégâts de la dernière balle se fera au moment du tir.
    @Override
    public float getWeaponDamage(ItemStack stack) {
        return WEAPON_DAMAGE;
    }
      
    // Implémentation de IHeadshotWeapon
    @Override
    public float getHeadshotBaseDamage(ItemStack stack) {
        return BASE_HEADSHOT_DAMAGE;
    }

    @Override
    public float getHeadshotPapBonusDamage(ItemStack stack) {
        return 0.0f; // Pas de bonus PaP pour le Whisper
    }

    public float getProjectileVelocity(ItemStack stack) {
        return BASE_PROJECTILE_VELOCITY;
    }

    public float getProjectileSpread(ItemStack stack) {
        return BASE_PROJECTILE_SPREAD;
    }

    public float getRecoilPitch(ItemStack stack) {
        return BASE_RECOIL_PITCH;
    }

    public float getRecoilYaw(ItemStack stack) {
        return BASE_RECOIL_YAW;
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
    // Pour le Whisper, les munitions en réserve sont toujours "infinies", donc on ne les stocke pas réellement.
    @Override public int getReserve(ItemStack s) { return getMaxReserve(); } // Toujours le max pour simuler l'infini
    @Override public void setReserve(ItemStack s, int r) { /* Do nothing, infinite ammo */ }
    @Override public int getReloadTimer(ItemStack s) { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }

    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    public boolean isFourIsReadyPlaying(ItemStack stack) { return getOrCreateTag(stack).getBoolean(TAG_FOUR_IS_READY_PLAYING); }
    public void setFourIsReadyPlaying(ItemStack stack, boolean playing) { getOrCreateTag(stack).putBoolean(TAG_FOUR_IS_READY_PLAYING, playing); }

    // --- NEW HELPER METHOD TO STOP THE SOUND ---
    private void stopFourIsReadySound(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new StopFourIsReadySoundPacket());
        }
    }
    // --- END NEW HELPER METHOD ---

    @Override public int getMaxAmmo() { return MAX_AMMO; }
    @Override public int getMaxReserve() { return MAX_RESERVE; } // 0 car infini est géré différemment

    @Override
    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Ammo")) {
            stack.setTag(new CompoundTag());
            setAmmo(stack, MAX_AMMO);
            // La réserve n'a pas besoin d'être initialisée car elle est "infinie"
            setReloadTimer(stack, 0);
            tag = stack.getTag();
        }
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_LAST_FIRE_TICK)) {
            tag.putLong(TAG_LAST_FIRE_TICK, 0);
        }
        if (!tag.contains(TAG_FOUR_IS_READY_PLAYING)) {
            tag.putBoolean(TAG_FOUR_IS_READY_PLAYING, false);
        }
    }

    @Override
    public void startReload(ItemStack stack, Player player) {
        initializeIfNeeded(stack);

        if (getReloadTimer(stack) == 0 && getAmmo(stack) < MAX_AMMO) {
            int reloadTime = RELOAD_TIME;
            float pitch = 1.0f;

            if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                reloadTime /= 2;
                pitch = 1.6f;
            }

            if (!player.level().isClientSide) {
                setReloadTimer(stack, reloadTime);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), RELOAD_SOUND, SoundSource.PLAYERS, 1f, pitch);

                // --- STOP SOUND ON RELOAD START ---
                if (isFourIsReadyPlaying(stack)) {
                    stopFourIsReadySound(player);
                    setFourIsReadyPlaying(stack, false);
                }
                // --- END STOP SOUND ---

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
        setAmmo(stack, MAX_AMMO); // Recharge toujours au max
        sp.connection.send(new ClientboundSetEquipmentPacket(
            sp.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, stack))));

        // --- STOP SOUND ON RELOAD FINISH ---
        if (isFourIsReadyPlaying(stack)) {
            stopFourIsReadySound(player);
            setFourIsReadyPlaying(stack, false);
        }
        // --- END STOP SOUND ---
    }

    private SoundEvent getFireSound(ItemStack stack) {
        return FIRE_SOUND;
    }

    @Override public UseAnim getUseAnimation(ItemStack s) { return getReloadTimer(s) > 0 ? UseAnim.NONE : UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack s) { return 72000; }

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
            if (isFourIsReadyPlaying(stack)) {
                stopFourIsReadySound(player);
                setFourIsReadyPlaying(stack, false);
            }
            return;
        }
    
        long lastFireTick = getLastFireTick(stack);
        long currentTick = level.getGameTime();
        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return;
        }
    
        if (getAmmo(stack) == 0) {
            startReload(stack, player);
            if (isFourIsReadyPlaying(stack)) {
                stopFourIsReadySound(player);
                setFourIsReadyPlaying(stack, false);
            }
            return;
        }
        
        if (getAmmo(stack) > 0) {
            float actualDamage = WEAPON_DAMAGE;
            boolean isLastBulletShot = (getAmmo(stack) == 1); 
    
            // --- DÉPLACEZ LA DÉCLARATION DE 'arrow' ICI ---
            // Elle doit être déclarée AVANT d'être utilisée pour stocker des données ou tirer.
            Arrow arrow = new Arrow(level, player); // <--- LIGNE À DÉPLACER ICI
            // --- FIN DU DÉPLACEMENT ---
    
            if (isLastBulletShot) {
                float baseRandomDamage = LAST_BULLET_MIN_DAMAGE + random.nextFloat() * (LAST_BULLET_MAX_DAMAGE - LAST_BULLET_MIN_DAMAGE);
                actualDamage = (float) (Math.round(baseRandomDamage / 4.0f) * 4.0f);
                
                // Maintenant 'arrow' est disponible ici car elle a été déclarée plus haut
                arrow.getPersistentData().putFloat("zombierool:last_bullet_max_hp_percent", LAST_BULLET_MAX_HP_PERCENT_DAMAGE);
            }
    
            Vec3 start = player.getEyePosition(1F);
            Vec3 dir = player.getViewVector(1F);
    
            float projectileVelocity = getProjectileVelocity(stack);
            float projectileSpread = getProjectileSpread(stack);
            float recoilPitch = getRecoilPitch(stack);
            float recoilYaw = getRecoilYaw(stack);
    
            // L'initialisation du reste des propriétés de la flèche peut rester ici
            arrow.setOwner(player);
            arrow.getPersistentData().putUUID("shooterUUID", player.getUUID());
            arrow.setPos(start.x, start.y, start.z);
            arrow.setSilent(true);
            arrow.setNoGravity(true);
            arrow.pickup = Pickup.DISALLOWED;
            arrow.getPersistentData().putBoolean("zombierool:invisible", true);
            arrow.getPersistentData().putBoolean("zombierool:small", true);
            arrow.setInvisible(true);
            arrow.getPersistentData().putFloat("zombierool:arrow_damage", actualDamage);
            arrow.setBaseDamage(0.0D); 
    
            if (isLastBulletShot) {
                arrow.getPersistentData().putBoolean("zombierool:last_whisper_bullet", true);
            }
    
            arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread);
    
            level.addFreshEntity(arrow);

            // Removed the PaP check as this weapon does not have PaP functionality.
            // if (isPackAPunched(stack)) {
            //     arrow.setSecondsOnFire(200);
            //     arrow.getPersistentData().putBoolean("ignoreFireResistance", true);
            //     arrow.getPersistentData().putBoolean("ignoreRain", true);
            //     arrow.getPersistentData().putBoolean("zombierool:whisper_pap", true);
            // }
    
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }
    
            float soundVolume = 0.7f; 
            if (isLastBulletShot) {
                soundVolume = 6.0f;
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), getFireSound(stack), SoundSource.PLAYERS, soundVolume, 1f);
            
            setAmmo(stack, getAmmo(stack) - 1);
            setLastFireTick(stack, currentTick);
    
            if (isLastBulletShot) { 
                 if (isFourIsReadyPlaying(stack)) {
                    stopFourIsReadySound(player);
                    setFourIsReadyPlaying(stack, false);
                }
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return; // Use 'p' for the Player instance
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
                // --- STOP SOUND: RELOAD INTERRUPTED / WEAPON UNEQUIPPED DURING RELOAD ---
                if (isFourIsReadyPlaying(stack)) {
                    stopFourIsReadySound(p); 
                    setFourIsReadyPlaying(stack, false);
                }
                return; // Stop processing this tick for reload
            }
            setReloadTimer(stack, --t); // Decrement timer
            if (t <= 0) { // If timer finishes
                finishReload(stack, p, level);
                // After reload, if the sound "four_is_ready" was playing, stop it (because we now have 4 bullets)
                // --- STOP SOUND: RELOAD FINISHED (SOUND SHOULD STOP AS AMMO IS FULL) ---
                if (isFourIsReadyPlaying(stack)) {
                    stopFourIsReadySound(p); 
                    setFourIsReadyPlaying(stack, false);
                }
            }
        }

        CompoundTag tag = stack.getOrCreateTag();
        boolean wasEquippedPreviously = tag.getBoolean(TAG_EQUIPPED_PREVIOUSLY);

        // Logic for playing the "weapon in hand" sound
        if (sel && !wasEquippedPreviously) {
            if (p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, true);
        } else if (!sel && wasEquippedPreviously) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
            // --- STOP SOUND: WEAPON UNEQUIPPED ---
            if (isFourIsReadyPlaying(stack)) {
                stopFourIsReadySound(p); 
                setFourIsReadyPlaying(stack, false);
            }
        }

        // Vérifier l'état du son four_is_ready en permanence quand l'arme est sélectionnée
        if (sel) {
            // If ammo is 1 and sound is not playing, start it
            if (getAmmo(stack) == 1 && !isFourIsReadyPlaying(stack)) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), FOUR_IS_READY_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                setFourIsReadyPlaying(stack, true);
            } 
            // If ammo is NOT 1 and sound IS playing, stop it
            else if (getAmmo(stack) != 1 && isFourIsReadyPlaying(stack)) {
                stopFourIsReadySound(p); 
                setFourIsReadyPlaying(stack, false);
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        initializeIfNeeded(stack);
        // The Whisper does not have a Pack-a-Punch version, so no need for PaP check here
        return Component.literal(getTranslatedMessage("§6Le Murmure", "§6The Whisper"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        // The Whisper does not have a Pack-a-Punch version, so no need for PaP check here
        tips.add(Component.literal(getTranslatedMessage("§7Théâtralement mystérieux", "§7Theatrically mysterious")));
        tips.add(Component.literal(getTranslatedMessage("§7Munitions : §bInfinies", "§7Ammo: §bInfinite")));
        tips.add(Component.literal(getTranslatedMessage("§cLa dernière balle fait des dégâts §lÉNORMES et prend en compte les PV max !", "§cThe last bullet deals §lHUGE damage and scales with max HP!")));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // The Whisper is always special, so it should always be foil
        return true;
    }
}
