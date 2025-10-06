package net.mcreator.zombierool.item;

import com.mojang.datafixers.util.Pair;
import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.api.IHeadshotWeapon; // Keep this if OldSword also has headshot mechanics, otherwise remove. Based on the code, it doesn't seem to use it explicitly, but it's an interface.
import net.mcreator.zombierool.init.ZombieroolModSounds; // Assurez-vous que cette importation est correcte pour vos sons custom
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer; // Import pour les messages côté serveur
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.mcreator.zombierool.init.ZombieroolModMobEffects; // Keep this if Cherry Cola effects are still applicable
import net.minecraft.sounds.SoundEvents;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance; // Keep if Cherry Cola effects are still applicable
import net.minecraft.world.effect.MobEffects; // Keep if Cherry Cola effects are still applicable
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster; // Assurez-vous que cette importation est présente
import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.entity.MannequinEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Multimap;
import com.google.common.collect.ImmutableMultimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.mcreator.zombierool.PointManager; // <--- AJOUTÉ : Import de PointManager

// Import Minecraft client for language check
import net.minecraft.client.Minecraft;

public class OldSwordWeaponItem extends Item implements ICustomWeapon, IPackAPunchable { // Removed IHeadshotWeapon and IHandgunWeapon as per context and provided code

    private static final int COOLDOWN_TICKS = 20; // Cooldown légèrement augmenté pour une arme de base
    private static final int DASH_COOLDOWN_TICKS = 60; // Cooldown plus long pour le dash

    private static final float WEAPON_DAMAGE = 3.0f; // Dégâts de base beaucoup plus faibles
    private static final float PAP_BONUS_DAMAGE = 7.0f; // Bonus de dégâts PaP réduit

    private static final float CRITICAL_CHANCE = 0.10f; // Chance de critique légèrement réduite
    private static final float CRITICAL_MULTIPLIER = 1.25f; // Multiplicateur de critique réduit

    private static final double CLEAVE_RADIUS = 1.5D; // Rayon de cleave plus petit
    private static final float CLEAVE_DAMAGE_PERCENTAGE = 0.50f; // Dégâts de cleave réduits

    private static final float DASH_DISTANCE = 3.0f; // Distance de dash réduite

    // --- SONS PERSONNALISÉS POUR L'OLD SWORD ---
    // Assurez-vous que ces SoundEvents existent dans votre ZombieroolModSounds
    private static final SoundEvent OLD_SWORD_EQUIP_SOUND = ZombieroolModSounds.OLD_SWORD_EQUIP.get(); // Nouveau son d'équipement
    private static final SoundEvent OLD_SWORD_SWING_SOUND = ZombieroolModSounds.OLD_SWORD_SWING.get(); // Nouveau son d'attaque
    private static final SoundEvent OLD_SWORD_SWING_UPGRADED_SOUND = SoundEvents.PLAYER_ATTACK_STRONG; // Son plus fort pour l'arme améliorée (gardé car vous n'avez pas spécifié un nouveau pour PaP)


    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_ATTACK_TICK = "LastAttackTick";
    private static final String TAG_LAST_DASH_TICK = "LastDashTick";
    private static final String TAG_DASHED_TARGET_ID = "DashedTargetId";

    // Durability tags
    private static final String TAG_DURABILITY = "Durability";

    // Base durability value
    private static final int BASE_MAX_DURABILITY = 50; // Durabilité de base très faible
    // Bonus durability when Pack-a-Punched
    private static final int PAP_DURABILITY_BONUS = 100; // Bonus de durabilité PaP réduit

    private static final java.util.UUID ATTACK_DAMAGE_MODIFIER_UUID = java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final java.util.UUID ATTACK_SPEED_MODIFIER_UUID = java.util.UUID.fromString("FA233EE9-2F64-44F4-52E5-2792376F8AE5");


    public OldSwordWeaponItem() {
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
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_PAP, true);
        // Quand l'item est PaP, sa durabilité est réinitialisée à sa nouvelle (plus grande) valeur max
        setDurability(stack, getMaxDurability(stack));
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
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        if (slot == EquipmentSlot.MAINHAND) {
            float damage = getWeaponDamage(stack);
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_MODIFIER_UUID, "Weapon damage", damage, AttributeModifier.Operation.ADDITION));
            // Vitesse d'attaque légèrement plus lente pour une arme de base
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_MODIFIER_UUID, "Weapon speed", -2.8D, AttributeModifier.Operation.ADDITION));
        }
        return builder.build();
    }

    private CompoundTag getOrCreateTag(ItemStack s) {
        if (!s.hasTag()) s.setTag(new CompoundTag());
        return s.getTag();
    }

    public long getLastAttackTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_ATTACK_TICK); }
    public void setLastAttackTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_ATTACK_TICK, tick); }

    public long getLastDashTick(ItemStack stack) { return getOrCreateTag(stack).getLong(TAG_LAST_DASH_TICK); }
    public void setLastDashTick(ItemStack stack, long tick) { getOrCreateTag(stack).putLong(TAG_LAST_DASH_TICK, tick); }

    public void setDashedTargetId(ItemStack stack, int entityId) { getOrCreateTag(stack).putInt(TAG_DASHED_TARGET_ID, entityId); }
    public int getDashedTargetId(ItemStack stack) { return getOrCreateTag(stack).getInt(TAG_DASHED_TARGET_ID); }
    public void clearDashedTargetId(ItemStack stack) { getOrCreateTag(stack).remove(TAG_DASHED_TARGET_ID); }

    // --- Durability Management ---
    public int getMaxDurability(ItemStack stack) {
        int max = BASE_MAX_DURABILITY;
        if (isPackAPunched(stack)) {
            max += PAP_DURABILITY_BONUS;
        }
        return max;
    }

    public int getDurability(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        if (!tag.contains(TAG_DURABILITY)) {
            tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
        }
        return tag.getInt(TAG_DURABILITY);
    }

    public void setDurability(ItemStack stack, int durability) {
        CompoundTag tag = getOrCreateTag(stack);
        int max = getMaxDurability(stack);
        if (durability > max) durability = max;
        if (durability < 0) durability = 0;
        tag.putInt(TAG_DURABILITY, durability);
    }

    public void damageItem(ItemStack stack, int amount, Player player) {
        int current = getDurability(stack);
        setDurability(stack, current - amount);
        if (getDurability(stack) <= 0) {
            if (!player.level().isClientSide) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            stack.shrink(1); // Destroys the item
        }
    }

    public void initializeIfNeeded(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        if (!tag.contains(TAG_LAST_ATTACK_TICK)) tag.putLong(TAG_LAST_ATTACK_TICK, 0);
        if (!tag.contains(TAG_LAST_DASH_TICK)) tag.putLong(TAG_LAST_DASH_TICK, 0);
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_DASHED_TARGET_ID)) {
            tag.putInt(TAG_DASHED_TARGET_ID, -1);
        }
        if (!tag.contains(TAG_DURABILITY)) {
            tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
        }
    }

    private SoundEvent getSwingSound(ItemStack stack) {
        return isPackAPunched(stack) ? OLD_SWORD_SWING_UPGRADED_SOUND : OLD_SWORD_SWING_SOUND; // Utilise le nouveau son d'attaque
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) { return UseAnim.SPEAR; } // Peut être laissé ou changé pour SWORD si vous préférez
    @Override
    public int getUseDuration(ItemStack s) { return 72000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Le clic droit est pour le dash, qui n'est dispo qu'après PaP
        if (isPackAPunched(player.getItemInHand(hand))) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        } else {
            if (!level.isClientSide) {
                // Message pour le joueur si l'arme n'est pas PaP
                player.displayClientMessage(Component.literal(getTranslatedMessage("§cCette arme doit être améliorée pour utiliser le dash !", "§cThis weapon must be upgraded to use the dash ability!")), true);
                // AUCUN SON QUAND ON ESSAIE DE DASH SANS PaP (suppression du SoundEvents.EXPERIENCE_ORB_PICKUP)
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
    }

    // --- LEFT-CLICK LOGIC: OVERRIDING DEFAULT MELEE ATTACK ---
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && !player.level().isClientSide) {
            initializeIfNeeded(stack);
    
            float actualDamage = getWeaponDamage(stack);
            if (player.level().random.nextFloat() < CRITICAL_CHANCE) {
                actualDamage *= CRITICAL_MULTIPLIER;
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.5f);
            }
    
            final float finalCalculatedDamage = actualDamage;
            AtomicInteger entitiesHit = new AtomicInteger(0);
            
            // Damage primary target
            target.hurt(player.level().damageSources().playerAttack(player), finalCalculatedDamage);
            entitiesHit.incrementAndGet();

            // AJOUTÉ : Gagner des points pour la cible principale si c'est un monstre
            if (target instanceof Monster) {
                PointManager.modifyScore(player, 10);
            }
    
            // --- Primary attack sound ---
            SoundEvent swing = getSwingSound(stack);
            player.playSound(swing, 1.0f, 0.9f + player.level().random.nextFloat() * 0.2f);
    
            // --- Cleave on surrounding enemies ---
            AABB cleaveBox = target.getBoundingBox().inflate(CLEAVE_RADIUS);
            player.level().getEntitiesOfClass(LivingEntity.class, cleaveBox, entity -> {
                return entity != player && entity.isAlive() && !(entity instanceof Player)
                    && !(entity instanceof TamableAnimal tamable && tamable.isTame())
                    && (entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity || entity instanceof MannequinEntity);
            }).forEach(entity -> {
                if (entity != target) {
                    entity.hurt(player.level().damageSources().playerAttack(player), finalCalculatedDamage * CLEAVE_DAMAGE_PERCENTAGE);
                    player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        swing, SoundSource.PLAYERS, 0.7f, 1.0f);
                    entitiesHit.incrementAndGet();
                    // AJOUTÉ : Gagner des points pour les entités secondaires touchées par le cleave si c'est un monstre
                    if (entity instanceof Monster) {
                        PointManager.modifyScore(player, 10);
                    }
                }
            });
    
            setLastAttackTick(stack, player.level().getGameTime());
            damageItem(stack, entitiesHit.get(), player);
            return true;
        }
        return false;
    }


    // --- RIGHT-CLICK LOGIC: DASH ABILITY ---
    @Override
    public void onUseTick(Level level, LivingEntity ent, ItemStack stack, int count) {
        if (!(ent instanceof Player player) || level.isClientSide) return;
        initializeIfNeeded(stack);
        
        // Le dash n'est disponible que si l'arme est Pack-a-Punched
        if (!isPackAPunched(stack)) {
            // Le joueur continue de "charger" l'arme mais le dash ne se déclenche pas
            // On pourrait arrêter l'utilisation ici si on veut forcer l'arrêt
            player.stopUsingItem();
            return;
        }

        long lastAttackTick = getLastAttackTick(stack);
        long lastDashTick = getLastDashTick(stack);
        long currentTick = level.getGameTime();
    
        if (currentTick - lastDashTick >= DASH_COOLDOWN_TICKS) {
            Optional<LivingEntity> targetOpt = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DASH_DISTANCE * 1.5), target -> {
                    return (target instanceof ZombieEntity || target instanceof CrawlerEntity ||
                            target instanceof HellhoundEntity || target instanceof MannequinEntity)
                        && target.isAlive() && !target.equals(player)
                        && player.distanceTo(target) <= DASH_DISTANCE;
                }).stream().min(Comparator.comparingDouble(player::distanceToSqr));
    
            if (targetOpt.isPresent()) {
                LivingEntity target = targetOpt.get();
                Vec3 from = player.getEyePosition();
                Vec3 to = target.getEyePosition();
    
                HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.MISS) {
                    // Dash possible
                    Vec3 dir = target.position().subtract(player.position()).normalize();
                    Vec3 dash = dir.scale(DASH_DISTANCE * 0.8);
    
                    player.setDeltaMovement(dash.x, player.getDeltaMovement().y, dash.z);
                    player.hurtMarked = true;
                    player.fallDistance = 0;
    
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);
    
                    setLastDashTick(stack, currentTick);
                    setDashedTargetId(stack, target.getId());
    
                    // System.out.println("Dash vers : " + target.getName().getString()); // Retiré pour la version finale
                    return;
                } else {
                    if (player instanceof ServerPlayer) {
                        player.displayClientMessage(Component.literal(getTranslatedMessage("§cDash bloqué par un obstacle !", "§cDash blocked by an obstacle!")), true);
                        // AUCUN SON QUAND LE DASH EST BLOQUÉ
                    }
                }
            } else {
                if (player instanceof ServerPlayer) {
                    player.displayClientMessage(Component.literal(getTranslatedMessage("§cAucune cible à portée pour le dash !", "§cNo target in range for dash!")), true);
                    // AUCUN SON QUAND PAS DE CIBLE POUR LE DASH
                }
            }
        }
    
        // --- Post-dash: perform attack if close ---
        int dashedTargetId = getDashedTargetId(stack);
        if (dashedTargetId != -1) {
            Entity entityTarget = level.getEntity(dashedTargetId);
            if (entityTarget instanceof LivingEntity target && target.isAlive()) {
                if (player.distanceTo(target) <= CLEAVE_RADIUS + 1.0D &&
                    currentTick - lastAttackTick >= COOLDOWN_TICKS) {
    
                    float baseDamage = getWeaponDamage(stack);
                    boolean isCritical = level.random.nextFloat() < CRITICAL_CHANCE;
                    final float actualDamage = isCritical ? baseDamage * CRITICAL_MULTIPLIER : baseDamage;
                    
                    AtomicInteger entitiesHit = new AtomicInteger(0);
                    
                    if (isCritical) {
                        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.5f);
                    }
    
                    target.hurt(level.damageSources().playerAttack(player), actualDamage);
                    entitiesHit.incrementAndGet();
                    level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        getSwingSound(stack), SoundSource.PLAYERS, 1.0f, 1.0f);

                    // AJOUTÉ : Gagner des points pour la cible du dash si c'est un monstre
                    if (target instanceof Monster) {
                        PointManager.modifyScore(player, 10);
                    }
    
                    AABB cleaveBox = target.getBoundingBox().inflate(CLEAVE_RADIUS);
                    level.getEntitiesOfClass(LivingEntity.class, cleaveBox, entity -> {
                        return entity != player && entity.isAlive() && !(entity instanceof Player)
                            && !(entity instanceof TamableAnimal tamable && tamable.isTame())
                            && (entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity || entity instanceof MannequinEntity);
                    }).forEach(entity -> {
                        if (entity != target) {
                            entity.hurt(level.damageSources().playerAttack(player), actualDamage * CLEAVE_DAMAGE_PERCENTAGE);
                            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                getSwingSound(stack), SoundSource.PLAYERS, 0.7f, 1.0f);
                            entitiesHit.incrementAndGet();
                            // AJOUTÉ : Gagner des points pour les entités secondaires touchées par le cleave après le dash si c'est un monstre
                            if (entity instanceof Monster) {
                                PointManager.modifyScore(player, 10);
                            }
                        }
                    });
    
                    setLastAttackTick(stack, currentTick);
                    damageItem(stack, entitiesHit.get(), player);
                    clearDashedTargetId(stack);
                }
            } else {
                clearDashedTargetId(stack);
            }
        }
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean sel) {
        if (!(ent instanceof Player p)) return;
        initializeIfNeeded(stack);

        CompoundTag tag = stack.getOrCreateTag();
        boolean wasEquippedPreviously = tag.getBoolean(TAG_EQUIPPED_PREVIOUSLY);

        if (sel) {
            if (!wasEquippedPreviously) {
                if (p.getItemInHand(InteractionHand.MAIN_HAND).equals(stack)) {
                     // Utilise le nouveau son d'équipement OLD_SWORD_EQUIP_SOUND
                     level.playSound(null, p.getX(), p.getY(), p.getZ(), OLD_SWORD_EQUIP_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, true);
            }
        } else {
            if (wasEquippedPreviously) {
                tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        // Noms et couleurs adaptés pour une arme de base
        String name = upgraded
            ? getTranslatedMessage("§6Vieille Épée Raffinée", "§6Refined Old Sword")
            : getTranslatedMessage("§fVieille Épée", "§fOld Sword");
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);

        tips.add(Component.literal(getTranslatedMessage("§7Clic-gauche : Attaque de zone faible", "§7Left-click: Weak Area Attack")));
        
        if (upgraded) {
            tips.add(Component.literal(getTranslatedMessage("§6Améliorée via Pack-a-Punch", "§6Upgraded via Pack-a-Punch")));
            tips.add(Component.literal(getTranslatedMessage(
                "§6Bonus dégâts PaP : " + String.format("%.1f", PAP_BONUS_DAMAGE),
                "§6PaP Damage Bonus: " + String.format("%.1f", PAP_BONUS_DAMAGE)
            )));
            tips.add(Component.literal(getTranslatedMessage("§eClic-droit : Dash rapide sur l'ennemi le plus proche", "§eRight-click: Quick Dash to nearest enemy")));
        }

        // Add durability display
        int durability = getDurability(stack);
        int maxDurability = getMaxDurability(stack);
        tips.add(Component.literal(getTranslatedMessage("§7Durabilité : ", "§7Durability: ") + durability + " / " + maxDurability));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
