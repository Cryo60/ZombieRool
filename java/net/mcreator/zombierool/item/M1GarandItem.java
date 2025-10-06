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

public class M1GarandItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO = 8; // Caractéristique du M1 Garand : chargeur de 8 coups
    private static final int MAX_RESERVE = MAX_AMMO * 8; // Réserve généreuse
    private static final int COOLDOWN_TICKS = 6; // Cadence de tir semi-automatique équilibrée
    private static final int RELOAD_TIME = 60; // Rechargement relativement long (clip entier)
    private static final int RELOAD_CANCEL_THRESHOLD = 50; // Temps à partir duquel l'animation de rechargement "ping" devrait se jouer si interrompue

    private static final float WEAPON_DAMAGE = 6.8f; // Bons dégâts par coup
    private static final float PAP_BONUS_DAMAGE = 6.0f; // Très bon bonus Pack-a-Punch
    
    private static final float BASE_HEADSHOT_DAMAGE = 2.0f; // Bon multiplicateur headshot de base
    private static final float PAP_HEADSHOT_BONUS = 2.5f; // Excellent bonus headshot si PaP
    
    private static final float BASE_PROJECTILE_VELOCITY = 3.0f; // Très rapide
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.1f; 
    private static final float BASE_PROJECTILE_SPREAD = 0.8f; // Bonne précision, légèrement moins que les pistolets dédiés
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.6f; 

    private static final float BASE_RECOIL_PITCH = 1.8f; // Recul notable
    private static final float BASE_RECOIL_YAW = 0.7f; 
    private static final float PAP_RECOIL_MULTIPLIER = 0.7f; 

    private static final SoundEvent FIRE_SOUND = ZombieroolModSounds.M1_GARAND_FIRE.get(); // À définir
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get(); 
    private static final SoundEvent RELOAD_SOUND = ZombieroolModSounds.M1_GARAND_RELOADING.get(); // À définir
    private static final SoundEvent DRY_FIRE_SOUND = ZombieroolModSounds.RIFLE_DRY.get(); 
    private static final SoundEvent PING_SOUND = ZombieroolModSounds.M1_GARAND_PING.get(); // Le fameux son de "ping"
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    // Nouveau tag pour suivre si la dernière balle a été tirée et le rechargement est attendu
    private static final String TAG_LAST_BULLET_FIRED_WAITING_RELOAD = "LastBulletFiredWaitingReload";


    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }

    public M1GarandItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); // Rareté UNCOMMON pour une arme de milieu de partie
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

    @Override public int  getAmmo(ItemStack s)           { return getOrCreateTag(s).getInt("Ammo"); }
    @Override public void setAmmo(ItemStack s, int a)    { getOrCreateTag(s).putInt("Ammo", a); }
    @Override public int  getReserve(ItemStack s)        { return getOrCreateTag(s).getInt("Reserve"); }
    @Override public void setReserve(ItemStack s, int r) { getOrCreateTag(s).putInt("Reserve", r); }
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }

    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    // Méthodes pour le nouveau tag
    public boolean getLastBulletFiredWaitingReload(ItemStack stack) {
        return getOrCreateTag(stack).getBoolean(TAG_LAST_BULLET_FIRED_WAITING_RELOAD);
    }
    public void setLastBulletFiredWaitingReload(ItemStack stack, boolean value) {
        getOrCreateTag(stack).putBoolean(TAG_LAST_BULLET_FIRED_WAITING_RELOAD, value);
    }


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
        // Initialiser le nouveau tag
        if (!tag.contains(TAG_LAST_BULLET_FIRED_WAITING_RELOAD)) {
            tag.putBoolean(TAG_LAST_BULLET_FIRED_WAITING_RELOAD, false);
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

                // Joue le son PING ici si la dernière balle a été tirée et que cela a déclenché ce rechargement
                if (getLastBulletFiredWaitingReload(stack)) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), PING_SOUND, SoundSource.PLAYERS, 1f, 1f);
                    setLastBulletFiredWaitingReload(stack, false); // Réinitialise le drapeau après avoir joué le son
                }

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

        // Si l'inventaire est déjà à 0, on ne peut pas tirer. On tente de recharger ou de jouer le son de "clic à vide".
        if (getAmmo(stack) == 0) {
            // Si la dernière balle vient d'être tirée (et aucun rechargement n'a encore commencé), initie le rechargement.
            // Le "ping" sera joué quand startReload est appelé.
            if (getLastBulletFiredWaitingReload(stack) && getReserve(stack) > 0) {
                startReload(stack, player);
                // Le flag sera réinitialisé dans startReload après le ping
            } else if (getReserve(stack) > 0) {
                // Si l'inventaire est à 0 et qu'on n'a pas encore marqué la dernière balle, mais qu'il y a des réserves, commence le rechargement.
                // Cela gère les cas où le joueur ne maintient pas le tir après la dernière balle mais initie un rechargement manuel.
                startReload(stack, player);
            } else {
                // Pas de munitions et pas de réserves
                level.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    DRY_FIRE_SOUND,
                    SoundSource.PLAYERS, 0.7f, 1f
                );
            }
            return;
        }

        // Ce bloc s'exécute s'il y a des munitions pour tirer
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
            setLastFireTick(stack, currentTick);

            // Si c'était la dernière balle, définissez le drapeau. Le "ping" sera joué lorsque le rechargement commencera.
            if (getAmmo(stack) == 0) {
                setLastBulletFiredWaitingReload(stack, true);
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
                // Si le rechargement est interrompu (par exemple, changement d'arme), joue le "ping" si le rechargement était presque terminé
                if (RELOAD_TIME - t >= RELOAD_CANCEL_THRESHOLD) { // Si suffisamment de temps s'est écoulé pendant le rechargement pour justifier un "ping"
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), PING_SOUND, SoundSource.PLAYERS, 1f, 1f);
                }
                setReloadTimer(stack, 0); // Réinitialise le timer de rechargement si l'arme est déséquipée ou changée
                setLastBulletFiredWaitingReload(stack, false); // Réinitialise également ce drapeau
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
        String name = upgraded
            ? getTranslatedMessage("§aLe Garand Vert", "§aThe Green Garand") // Translated PaP name
            : getTranslatedMessage("§7M1 Garand", "§7M1 Garand"); // M1 Garand name likely same
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§eFusil semi-automatique historique, célèbre pour son 'ping'.", "§eHistoric semi-automatic rifle, famous for its 'ping'.")));
        tips.add(Component.literal(getTranslatedMessage("§eIdéal pour les manches intermédiaires.", "§eIdeal for mid-rounds.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dDégâts PaP : " + String.format("%.1f", WEAPON_DAMAGE + PAP_BONUS_DAMAGE),
                "§dPaP Damage: " + String.format("%.1f", WEAPON_DAMAGE + PAP_BONUS_DAMAGE)
            ))); 
            tips.add(Component.literal(getTranslatedMessage(
                "§dMultiplicateur Headshot PaP : x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE * PAP_HEADSHOT_BONUS),
                "§dPaP Headshot Multiplier: x" + String.format("%.1f", BASE_HEADSHOT_DAMAGE * PAP_HEADSHOT_BONUS)
            )));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
