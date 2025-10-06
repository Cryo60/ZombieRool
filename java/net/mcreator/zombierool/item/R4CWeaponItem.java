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
import net.minecraft.world.entity.animal.Animal; // Added missing import for Animal

import net.mcreator.zombierool.client.CherryReloadAnimationHandler;
import java.util.List;
import java.util.Random; // Import pour Random

// Import for RecoilPacket (YOU NEED TO CREATE THIS PACKET IN MCreator FIRST)
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.RecoilPacket;
import net.minecraftforge.network.PacketDistributor;

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class R4CWeaponItem extends Item implements IReloadable, ICustomWeapon, IPackAPunchable, IHeadshotWeapon {

    private static final int MAX_AMMO       = 30;
    private static final int MAX_RESERVE    = MAX_AMMO * 8; // Très bonne réserve
    private static final int COOLDOWN_TICKS = 4; // Bonne cadence de tir
    private static final int RELOAD_TIME    = 60; // Rechargement rapide

    private static final float WEAPON_DAMAGE = 11.0f; // Très bons dégâts de base
    private static final float PAP_BONUS_DAMAGE = 12.0f; // Excellent bonus PaP

    private static final float BASE_HEADSHOT_DAMAGE = 20.0f; // Très bon multiplicateur de headshot
    private static final float PAP_HEADSHOT_BONUS = 12.0f;    // Excellent bonus headshot PaP

    // Paramètres de tir
    private static final float BASE_PROJECTILE_VELOCITY = 3.5f; // Très bonne vitesse
    private static final float PAP_PROJECTILE_VELOCITY_MULTIPLIER = 1.2f; // Meilleure vitesse PaP
    private static final float BASE_PROJECTILE_SPREAD = 0.5f; // Très bonne précision de base
    private static final float PAP_PROJECTILE_SPREAD_MULTIPLIER = 0.5f; // Excellente précision PaP

    // Paramètres de Recul
    private static final float BASE_RECOIL_PITCH = 0.9f; // Recul vertical présent mais gérable
    private static final float BASE_RECOIL_YAW = 0.5f;   // Recul horizontal modéré
    private static final float PAP_RECOIL_MULTIPLIER = 0.6f; // Réduction significative du recul PaP


    private static final SoundEvent FIRE_SOUND           = ZombieroolModSounds.R4C_FIRE.get(); // Utilise R4C_FIRE
    private static final SoundEvent FIRE_SOUND_UPGRADED = ZombieroolModSounds.GUN_FIRE_UPGRADED.get();
    private static final SoundEvent RELOAD_SOUND         = ZombieroolModSounds.R4C_RELOADING.get(); // Utilise R4C_RELOADING
    private static final SoundEvent DRY_FIRE_SOUND       = ZombieroolModSounds.RIFLE_DRY.get();
    private static final SoundEvent WEAPON_IN_HAND_SOUND = ZombieroolModSounds.WEAPON_IN_HAND.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    private static final String TAG_BLACK_ICE = "BlackIce"; // Nouveau tag pour la texture Black Ice

    private static final double BLACK_ICE_CHANCE = 0.05; // 5% de chance d'avoir la texture Black Ice

    // --- Classe interne pour Cherry Cola Effects (Identique) ---
    public static class CherryColaEffects {
        public static final double RADIUS = 3.0;
        public static final float PERCENTAGE_DAMAGE = 0.10f;
        public static final float MAX_DISTANCE_DAMAGE = 1.5f;
        public static final int STUN_DURATION_TICKS = 80;
    }
    // --- Fin de la classe interne ---

    public R4CWeaponItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE)); // Rareté RARE pour une arme 7.5/10
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

    // Nouveau getter pour la propriété Black Ice
    public boolean isBlackIce(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_BLACK_ICE);
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

    /**
     * Initialise les tags NBT de l'ItemStack si nécessaire.
     * Cette méthode est appelée lors de l'inventoryTick, ce qui en fait un bon endroit
     * pour s'assurer que l'arme a ses propriétés de base et que le tag Black Ice
     * est défini UNE SEULE FOIS lors de la création de l'itemStack.
     *
     * @param stack L'ItemStack de l'arme.
     */
    @Override
    public void initializeIfNeeded(ItemStack stack) {
        // S'assure que l'ItemStack a toujours un tag NBT. Crée un nouveau tag s'il n'existe pas.
        CompoundTag tag = stack.getOrCreateTag();

        // Initialise les statistiques de base de l'arme si elles ne sont pas déjà présentes.
        // Ceci est important pour les nouvelles instances d'item (ex: /give, loot).
        if (!tag.contains("Ammo")) {
            tag.putInt("Ammo", MAX_AMMO);
        }
        if (!tag.contains("Reserve")) {
            tag.putInt("Reserve", MAX_RESERVE);
        }
        if (!tag.contains("ReloadTimer")) {
            tag.putInt("ReloadTimer", 0);
        }
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_LAST_FIRE_TICK)) {
            tag.putLong(TAG_LAST_FIRE_TICK, 0);
        }

        // Initialise la propriété Black Ice UNIQUEMENT SI ELLE N'EST PAS DÉJÀ PRÉSENTE.
        // Cela garantit que le tirage au sort (5% de chance) ne se produit qu'une seule fois
        // pour la durée de vie de cet ItemStack spécifique.
        if (!tag.contains(TAG_BLACK_ICE)) {
            // Effectue le tirage au sort pour déterminer si l'arme est "Black Ice".
            // Utilise une nouvelle instance de Random. Pour une gestion de la graine plus avancée,
            // on pourrait utiliser level.getRandom() si disponible et pertinent.
            boolean isBlackIceRolled = new Random().nextDouble() < BLACK_ICE_CHANCE;
            tag.putBoolean(TAG_BLACK_ICE, isBlackIceRolled);
            // Ajout d'un message de débogage pour voir quand cela se produit
            System.out.println("DEBUG: R4C initialisée. Variante Black Ice tirée : " + isBlackIceRolled);
        }
    }

    @Override
    public void startReload(ItemStack stack, Player player) {
        initializeIfNeeded(stack); // S'assurer que les tags sont init.

        if (getReloadTimer(stack) == 0 && getAmmo(stack) < MAX_AMMO && getReserve(stack) > 0) {
            int reloadTime = RELOAD_TIME;
            float pitch = 1.0f;

            if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
                reloadTime /= 2;
                pitch = 1.6f;
            }

            if (!player.level().isClientSide) { // Ce bloc s'exécute sur le serveur
                setReloadTimer(stack, reloadTime);
                System.out.println("DEBUG: Tentative de jouer le son de rechargement pour " + player.getName().getString() + " avec un pitch de " + pitch);
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
                        if (target instanceof Monster || (target instanceof TamableAnimal && !((TamableAnimal)target).isTame()) || (target instanceof Animal && !(target instanceof TamableAnimal))) { // Added Animal check
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
        initializeIfNeeded(stack); // S'assurer que les tags sont init.

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

            if (player instanceof ServerPlayer serverPlayer) {
                float actualPitchRecoil = recoilPitch;
                float actualYawRecoil = (player.getRandom().nextBoolean() ? 1 : -1) * recoilYaw;
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new RecoilPacket(actualPitchRecoil, actualYawRecoil));
            }

            level.addFreshEntity(arrow);
            level.playSound(null,
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
        initializeIfNeeded(stack); // S'assurer que les tags sont init.

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

        // Logique côté serveur pour le timer de rechargement et l'interruption si déséquipé
        if (t > 0) {
            if (!sel || !p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                setReloadTimer(stack, 0);
                System.out.println("DEBUG: Rechargement interrompu pour " + stack.getDisplayName().getString() + " (non sélectionné ou mauvais item). Timer réinitialisé à 0.");
                return;
            }
            setReloadTimer(stack, --t);
            if (t <= 0) {
                finishReload(stack, p, level);
                System.out.println("DEBUG: Rechargement terminé pour " + stack.getDisplayName().getString() + ".");
            }
        }

        CompoundTag tag = stack.getOrCreateTag(); // Utiliser getOrCreateTag pour s'assurer d'avoir un tag valide
        boolean wasEquippedPreviously = tag.getBoolean(TAG_EQUIPPED_PREVIOUSLY);

        if (sel && !wasEquippedPreviously) {
            // Vérifier que c'est bien l'arme tenue en main principale
            if (p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), WEAPON_IN_HAND_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                System.out.println("DEBUG: Joue WEAPON_IN_HAND_SOUND pour " + stack.getDisplayName().getString());
            }
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, true);
        } else if (!sel && wasEquippedPreviously) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
            System.out.println("DEBUG: " + stack.getDisplayName().getString() + " déséquipée. Réinitialisation de TAG_EQUIPPED_PREVIOUSLY.");
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        initializeIfNeeded(stack); // S'assurer que les tags sont init.
        boolean upgraded = isPackAPunched(stack);
        boolean blackIce = isBlackIce(stack); // Vérifie si c'est une Black Ice

        String nameColor = upgraded ? "§6" : "§2"; // Oranged pour PaP, vert pour normal
        String baseName = "R4C";
        String papName = getTranslatedMessage("Le Dragon Tactique", "The Tactical Dragon"); // Traduction du nom PaP

        if (blackIce) {
            nameColor = "§b"; // Bleu cyan pour Black Ice
            baseName = getTranslatedMessage("R4C ", "R4C "); // R4C reste R4C, mais on peut le traduire si besoin
            papName = getTranslatedMessage("Le Glacier Nocturne", "The Night Glacier"); // Traduction du nom PaP spécifique pour Black Ice
        }

        return Component.literal(nameColor + (upgraded ? papName : baseName + (blackIce ? getTranslatedMessage(" Black Ice", " Black Ice") : "")));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack); // S'assurer que les tags sont init.
        boolean upgraded = isPackAPunched(stack);
        boolean blackIce = isBlackIce(stack);

        tips.add(Component.literal(getTranslatedMessage("§eFusil d'assaut d'élite", "§eElite Assault Rifle")));
        if (blackIce) {
            tips.add(Component.literal(getTranslatedMessage("§bUne finition rare et éclatante !", "§bA rare and dazzling finish!")));
        }
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§dAméliorée via Pack-a-Punch", "§dUpgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§dBonus dégâts PaP : " + String.format("%.1f", PAP_BONUS_DAMAGE), // Utilise PAP_BONUS_DAMAGE pour le texte
                "§dPaP Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE)
            )));
            tips.add(Component.literal(getTranslatedMessage("§dDispersion et recul grandement réduits.", "§dGreatly reduced spread and recoil."))); // Ajouté pour être cohérent avec G36C
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // La R4C Pack-a-Punchée ou Black Ice a un effet brillant (foil)
        return isPackAPunched(stack) || isBlackIce(stack) || super.isFoil(stack);
    }
}
