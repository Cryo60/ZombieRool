package net.mcreator.zombierool.item;

import com.mojang.datafixers.util.Pair;
import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.api.IHeadshotWeapon;
import net.mcreator.zombierool.init.ZombieroolModSounds; // Assurez-vous que ces sons existent !
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import java.util.List;

// Import for RecoilPacket (YOU NEED TO HAVE THIS PACKET IN MCreator)
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class SniperWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    // --- Ammo & Reloading Parameters ---
    private static final int MAX_AMMO = 8; // Chargeur standard de sniper Halo
    private static final int MAX_RESERVE = MAX_AMMO * 6; // 6 chargeurs en réserve
    private static final int COOLDOWN_TICKS = 30; // Sniper : 1.5 seconde par tir (plus lent que l'Intervention)
    private static final int RELOAD_TIME = 60; // ticks (3s), rapide pour un sniper, idéal pour Halo

    // --- Damage Parameters ---
    private static final float WEAPON_DAMAGE = 20.0f; // Dégâts de base élevés (moins que l'Intervention)
    private static final float PAP_BONUS_DAMAGE = 20.0f; // Bonus PaP significatif

    private static final float BASE_HEADSHOT_DAMAGE = 50.0f; // Multiplicateur headshot très élevé
    private static final float PAP_HEADSHOT_BONUS = 30.0f; // Bonus headshot PaP pour un one-shot plus long

    // Paramètres de tir
    private static final float BASE_PROJECTILE_VELOCITY = 6.0f; // Projectile très très rapide
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.05f; // Légèrement plus rapide PaP
    private static final float BASE_PROJECTILE_SPREAD = 0.01f; // Précision quasi parfaite
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.0f; // Dispersion nulle après PaP

    // Paramètres de Recul
    private static final float BASE_RECOIL_PITCH = 1.0f; // Recul vertical notable
    private static final float BASE_RECOIL_YAW = 0.05f; // Très très peu de recul horizontal
    private static final float PAP_RECOIL_MULTIPLIER = 0.1f; // Recul quasi inexistant après PaP

    // --- Sounds ---
    private static final SoundEvent FIRE_SOUND          = ZombieroolModSounds.SNIPER_FIRE.get(); 
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.SNIPER_FIRE.get(); // Peut être le même ou un son différent
    private static final SoundEvent RELOAD_SOUND        = ZombieroolModSounds.SNIPER_RELOADING.get(); 
    private static final SoundEvent DRY_FIRE_SOUND      = ZombieroolModSounds.RIFLE_DRY.get(); 
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    // --- NBT Tags ---
    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";

    // --- Cherry Cola Effects (Reuse from Intervention) ---
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f; // 10% of max health
        public static final float MAX_DISTANCE_DAMAGE = 1.5f; // Max flat damage bonus when very close
        public static final int STUN_DURATION_TICKS = 80;
    }
    // --- End Cherry Cola Effects ---

    public SniperWeaponItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)); // Rareté Rare pour un sniper puissant
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

    // --- Pack-a-Punch Implementation ---
    @Override
    public void applyPackAPunch(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG_PAP, true);
        setAmmo(stack, MAX_AMMO); // Recharge les munitions au PaP
        setReserve(stack, getMaxReserve()); // Recharge la réserve au PaP
    }

    @Override
    public boolean isPackAPunched(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_PAP);
    }

    // --- Dynamic Damage & Stats based on PaP status ---
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

    // --- NBT Management ---
    private CompoundTag getOrCreateTag(ItemStack s) {
        if (!s.hasTag()) s.setTag(new CompoundTag());
        return s.getTag();
    }

    @Override public int  getAmmo(ItemStack s)           { return getOrCreateTag(s).getInt("Ammo"); }
    @Override public void setAmmo(ItemStack s, int a)    { getOrCreateTag(s).putInt("Ammo", Mth.clamp(a, 0, MAX_AMMO)); } 
    @Override public int  getReserve(ItemStack s)        { return getOrCreateTag(s).getInt("Reserve"); }
    @Override public void setReserve(ItemStack s, int r) { getOrCreateTag(s).putInt("Reserve", Mth.clamp(r, 0, MAX_RESERVE)); } 
    
    @Override public int  getReloadTimer(ItemStack s)    { return getOrCreateTag(s).getInt("ReloadTimer"); }
    @Override public void setReloadTimer(ItemStack s, int t) { getOrCreateTag(s).putInt("ReloadTimer", t); }

    public long getLastFireTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_FIRE_TICK); }
    public void setLastFireTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_FIRE_TICK, tick); }

    @Override public int getMaxAmmo()    { return MAX_AMMO; }
    @Override public int getMaxReserve() { return MAX_RESERVE; }

    @Override
    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("Ammo")) { 
            setAmmo(stack, MAX_AMMO);
            setReserve(stack, MAX_RESERVE);
            setReloadTimer(stack, 0);
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
                        if (target instanceof Monster || (target instanceof TamableAnimal && !((TamableAnimal)target).isTame()) || (target instanceof net.minecraft.world.entity.animal.Animal && !(target instanceof TamableAnimal))) { // Added Animal check
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
        int currentAmmo = getAmmo(stack);
        int reserve = getReserve(stack) + currentAmmo; // Récupère les balles restantes dans le chargeur
        int newAmmo = Math.min(MAX_AMMO, reserve);
        reserve -= newAmmo;
        setAmmo(stack, newAmmo);
        setReserve(stack, reserve);
        sp.connection.send(new ClientboundSetEquipmentPacket(
            sp.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, stack))));

        // Play the "ready" sound after reload finishes
        level.playSound(null, player.getX(), player.getY(), player.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private SoundEvent getFireSound(ItemStack stack) {
        return isPackAPunched(stack) ? FIRE_SOUND_UPGRADED : FIRE_SOUND;
    }

    @Override public UseAnim getUseAnimation(ItemStack s) { return getReloadTimer(s) > 0 ? UseAnim.NONE : UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack s) { return 72000; } // Permet le clic maintenu

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand); 
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity ent, ItemStack stack, int count) {
        if (!(ent instanceof Player player) || level.isClientSide) return; 
        initializeIfNeeded(stack);

        // Si l'arme est en cours de rechargement, ne peut pas tirer
        if (getReloadTimer(stack) > 0) {
            return;
        }

        long lastFireTick = getLastFireTick(stack);
        long currentTick = level.getGameTime();
        // Gestion du cooldown de tir
        if (currentTick - lastFireTick < COOLDOWN_TICKS) {
            return;
        }

        // Si pas de munitions, mais de la réserve, tente de recharger
        if (getAmmo(stack) == 0 && getReserve(stack) > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
            startReload(stack, player);
            return;
        }
        
        // Si plus de munitions et plus de réserve
        if (getAmmo(stack) == 0 && getReserve(stack) == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), DRY_FIRE_SOUND, SoundSource.PLAYERS, 0.7f, 1f);
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
            float weaponDamage = getWeaponDamage(stack); // Dégâts de l'arme

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
            arrow.setBaseDamage(weaponDamage); // Le sniper inflige son plein dégât via le projectile
            arrow.getPersistentData().putBoolean("zombierool:sniper_bullet", true); // Marque comme balle de sniper
            
            arrow.shoot(dir.x, dir.y, dir.z, projectileVelocity, projectileSpread);
            level.addFreshEntity(arrow);

            // SEND RECOIL PACKET TO CLIENT
            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            // Joue le son de tir
            level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                getFireSound(stack),
                SoundSource.PLAYERS, 0.8f, 1f + (player.getRandom().nextFloat() * 0.1f - 0.05f) // Volume adjusted from 1.2f to 0.8f
            );

            setAmmo(stack, getAmmo(stack) - 1);
            setLastFireTick(stack, currentTick);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (livingEntity instanceof Player player) {
            player.stopUsingItem(); 
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);

        int t = getReloadTimer(stack);

        if (level.isClientSide) {
            // CherryReloadAnimationHandler pour l'Intervention, pas le Sniper (à moins que tu veuilles ça)
            // int expectedReloadTime = RELOAD_TIME;
            // if (p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
            //     expectedReloadTime /= 2;
            // }
            // if (t == expectedReloadTime && p.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) {
            //     CherryReloadAnimationHandler.startCherryAnimation(null);
            // }
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

        // Logic for playing the "weapon in hand" sound
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
            ? getTranslatedMessage("§bNornfang", "§bNornfang") // Translated PaP name
            : getTranslatedMessage("§9Sniper", "§9Sniper"); // Base name (proper noun, remains same)
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        tips.add(Component.literal(getTranslatedMessage("§l§3Fusil de Précision de l'UNSC", "§l§3UNSC Sniper Rifle")));
        tips.add(Component.literal(getTranslatedMessage("§9Arme de précision à longue portée.", "§9Long-range precision weapon.")));
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§bAméliorée via Pack-a-Punch", "§bUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage("§bNom PaP : Nornfang", "§bPaP Name: Nornfang"))); 
            tips.add(Component.literal(getTranslatedMessage(
                "§bBonus dégâts : +", "§bDamage Bonus: +"
            ) + String.format("%.1f", PAP_BONUS_DAMAGE)));
            tips.add(Component.literal(getTranslatedMessage("§bPrécision et vitesse de tir accrues, recul quasi nul.", "§bIncreased precision and fire rate, virtually no recoil.")));
        } else {
            tips.add(Component.literal(getTranslatedMessage("§7Idéal pour les cibles prioritaires ou le contrôle de zone.", "§7Ideal for priority targets or area control.")));
        }

        tips.add(Component.literal(getTranslatedMessage("§7Munitions : ", "§7Ammo: ") + getAmmo(stack) + " / " + MAX_AMMO));
        tips.add(Component.literal(getTranslatedMessage("§7Réserve : ", "§7Reserve: ") + getReserve(stack) + " / " + MAX_RESERVE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }

    @Override
    public void tickReload(ItemStack stack, Player player, Level level) {
        // La logique de rechargement est gérée dans inventoryTick, cette méthode peut rester vide.
    }
}
