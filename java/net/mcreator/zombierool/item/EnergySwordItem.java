package net.mcreator.zombierool.item;

import net.mcreator.zombierool.api.ICustomWeapon;
import net.mcreator.zombierool.api.IPackAPunchable;

import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.sounds.SoundEvents;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
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

import net.mcreator.zombierool.PointManager;
import net.minecraft.client.Minecraft; // Import manquant ajouté pour la traduction

public class EnergySwordItem extends Item implements ICustomWeapon, IPackAPunchable {

    private static final int COOLDOWN_TICKS = 10;
    private static final int DASH_COOLDOWN_TICKS = 30;

    private static final float WEAPON_DAMAGE = 10.0f;
    private static final float PAP_BONUS_DAMAGE = 15.0f;

    private static final float CRITICAL_CHANCE = 0.15f;
    private static final float CRITICAL_MULTIPLIER = 1.5f;

    private static final double CLEAVE_RADIUS = 2.5D;
    private static final float CLEAVE_DAMAGE_PERCENTAGE = 0.75f;

    private static final float DASH_DISTANCE = 5.0f;

    private static final SoundEvent LOOP_SOUND = ZombieroolModSounds.ENERGY_SWORD_LOOP.get();
    private static final SoundEvent SWING_SOUND = ZombieroolModSounds.ENERGY_SWORD_SWING.get();
    private static final SoundEvent SWING_SOUND_UPGRADED = ZombieroolModSounds.ENERGY_SWORD_SWING_UPGRADED.get();
    private static final SoundEvent READY_SOUND = ZombieroolModSounds.ENERGY_SWORD_READY.get();

    private static final String TAG_PAP = "PackAPunch";
    private static final String TAG_EQUIPPED_PREVIOUSLY = "EquippedPreviously";
    private static final String TAG_LAST_ATTACK_TICK = "LastAttackTick";
    private static final String TAG_LAST_DASH_TICK = "LastDashTick";
    private static final String TAG_DASHED_TARGET_ID = "DashedTargetId";

    // Durability tags
    private static final String TAG_DURABILITY = "Durability";
    // TAG_MAX_DURABILITY est obsolète car la MAX_DURABILITY est calculée dynamiquement maintenant

    // Base durability value
    private static final int BASE_MAX_DURABILITY = 100;
    // Bonus durability when Pack-a-Punched
    private static final int PAP_DURABILITY_BONUS = 150; // Adds 150 durability, for a total of 250 when PaP

    private static final java.util.UUID ATTACK_DAMAGE_MODIFIER_UUID = java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final java.util.UUID ATTACK_SPEED_MODIFIER_UUID = java.util.UUID.fromString("FA233EE9-2F64-44F4-52E5-2792376F8AE5");


    public EnergySwordItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }
    
    /**
     * Helper method to check if the client's language is English.
     */
    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().options.languageCode == null) {
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
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_MODIFIER_UUID, "Weapon speed", -2.4D, AttributeModifier.Operation.ADDITION));
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
    // La durabilité max est maintenant dynamique en fonction du statut PaP
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
            // Initialisation de la durabilité au max initial
            tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
        }
        return tag.getInt(TAG_DURABILITY);
    }

    public void setDurability(ItemStack stack, int durability) {
        CompoundTag tag = getOrCreateTag(stack);
        int max = getMaxDurability(stack); // Utilise la durabilité max dynamique
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
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_LAST_ATTACK_TICK)) tag.putLong(TAG_LAST_ATTACK_TICK, 0);
        if (!tag.contains(TAG_LAST_DASH_TICK)) tag.putLong(TAG_LAST_DASH_TICK, 0);
        if (!tag.contains(TAG_EQUIPPED_PREVIOUSLY)) {
            tag.putBoolean(TAG_EQUIPPED_PREVIOUSLY, false);
        }
        if (!tag.contains(TAG_DASHED_TARGET_ID)) {
            tag.putInt(TAG_DASHED_TARGET_ID, -1);
        }
        // Initialise la durabilité au max approprié (base ou PaP) si non présente
        if (!tag.contains(TAG_DURABILITY)) {
            tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
        }
    }

    private SoundEvent getSwingSound(ItemStack stack) {
        return isPackAPunched(stack) ? SWING_SOUND_UPGRADED : SWING_SOUND;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) { return UseAnim.SPEAR; }
    @Override
    public int getUseDuration(ItemStack s) { return 72000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
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
    
                    // Empêche le super jump : garde la vélocité verticale actuelle du joueur
                    player.setDeltaMovement(dash.x, player.getDeltaMovement().y, dash.z);
                    player.hurtMarked = true;
                    player.fallDistance = 0;
    
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.0f);
    
                    setLastDashTick(stack, currentTick);
                    setDashedTargetId(stack, target.getId());
    
                    // System.out.println("Dash vers : " + target.getName().getString()); // Commenté pour un code plus propre
                    return;
                } else {
                    // System.out.println("Dash bloqué par un bloc."); // Commenté pour un code plus propre
                }
            } else {
                // System.out.println("Aucune cible valide pour le dash."); // Commenté pour un code plus propre
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
                     level.playSound(null, p.getX(), p.getY(), p.getZ(), READY_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
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
        
        String frenchName = upgraded ? "§dLame d'Élite" : "§2Épée à Énergie";
        String englishName = upgraded ? "§dElite Blade" : "§2Energy Sword";
        
        String name = getTranslatedMessage(frenchName, englishName);
        return Component.literal(name);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
        initializeIfNeeded(stack);
        boolean upgraded = isPackAPunched(stack);
        
        // Traduction des descriptions
        String desc1Fr = "§eArme de mêlée ultime";
        String desc1En = "§eUltimate melee weapon";
        tips.add(Component.literal(getTranslatedMessage(desc1Fr, desc1En)));
        
        String desc2Fr = "§eClic-gauche : Attaque de zone (Cleave)";
        String desc2En = "§eLeft-click: Area Attack (Cleave)";
        tips.add(Component.literal(getTranslatedMessage(desc2Fr, desc2En)));
        
        String desc3Fr = "§eClic-droit : Dash sur l'ennemi le plus proche (suivi d'une attaque)";
        String desc3En = "§eRight-click: Dash to nearest enemy (followed by an attack)";
        tips.add(Component.literal(getTranslatedMessage(desc3Fr, desc3En)));
        
        if (upgraded) {
            String papDescFr = "§dAméliorée via Pack-a-Punch";
            String papDescEn = "§dUpgraded via Pack-a-Punch";
            tips.add(Component.literal(getTranslatedMessage(papDescFr, papDescEn)));

            String damagePrefixFr = "§dBonus dégâts PaP : ";
            String damagePrefixEn = "§dPaP Damage Bonus: ";
            tips.add(Component.literal(
                getTranslatedMessage(damagePrefixFr, damagePrefixEn) + String.format("%.1f", PAP_BONUS_DAMAGE)
            ));
        }

        // Traduction de l'affichage de la durabilité
        int durability = getDurability(stack);
        int maxDurability = getMaxDurability(stack);
        String durabilityPrefixFr = "§7Durabilité : ";
        String durabilityPrefixEn = "§7Durability: ";

        tips.add(Component.literal(
            getTranslatedMessage(durabilityPrefixFr, durabilityPrefixEn) + durability + " / " + maxDurability
        ));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // La couleur du foil est fixe dans Minecraft. On peut juste l'activer ou la désactiver.
        return isPackAPunched(stack) || super.isFoil(stack);
    }
}
