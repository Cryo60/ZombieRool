package me.cryo.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;
import me.cryo.zombierool.init.ZombieroolModEntities;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

public class DummyEntity extends Monster {
	private final Map<UUID, DamageAccumulator> damageAccumulatorMap = new ConcurrentHashMap<>();
	private static class DamageAccumulator {
	    float totalDamage;
	    boolean headshot;
	    String playerName;
	
	    DamageAccumulator(float damage, boolean headshot, String playerName) {
	        this.totalDamage = damage;
	        this.headshot = headshot;
	        this.playerName = playerName;
	    }
	
	    void add(float damage, boolean isHeadshot) {
	        this.totalDamage += damage;
	        if (isHeadshot) this.headshot = true;
	    }
	}
	
	private static boolean isEnglishClient() {
	    return Minecraft.getInstance().options.languageCode.startsWith("en");
	}
	
	private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
	    if (isEnglishClient()) {
	        return Component.literal(englishMessage);
	    }
	    return Component.literal(frenchMessage);
	}
	
	public DummyEntity(PlayMessages.SpawnEntity packet, Level world) {
	    this(ZombieroolModEntities.DUMMY.get(), world);
	}
	
	public DummyEntity(EntityType<DummyEntity> type, Level world) {
	    super(type, world);
	    setMaxUpStep(0.6f);
	    xpReward = 0;
	    setNoAi(true);
	    setCustomName(getTranslatedComponent("Mannequin", "Dummy"));
	    setCustomNameVisible(true);
	    setPersistenceRequired();
	}
	
	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket() {
	    return NetworkHooks.getEntitySpawningPacket(this);
	}
	
	@Override
	public MobType getMobType() {
	    return MobType.UNDEFINED;
	}
	
	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
	    return false;
	}
	
	@Override
	public SoundEvent getHurtSound(DamageSource ds) {
	    return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.armor_stand.hit"));
	}
	
	@Override
	public void tick() {
	    super.tick();
	    if (!this.level().isClientSide && !damageAccumulatorMap.isEmpty()) {
	        damageAccumulatorMap.forEach((uuid, acc) -> {
	            float heartsLost = acc.totalDamage / 2.0F;
	            MutableComponent message = Component.literal("[" + acc.playerName + "] ").withStyle(ChatFormatting.BLUE);
	            message.append(getTranslatedComponent("Le Mannequin a reçu ", "The Dummy took "));
	            message.append(Component.literal(String.format("%.1f", acc.totalDamage)).withStyle(ChatFormatting.RED));
	            message.append(getTranslatedComponent(" dégâts (", " damage ("));
	            message.append(Component.literal(String.format("%.1f", heartsLost)).withStyle(ChatFormatting.DARK_RED));
	            message.append(getTranslatedComponent(" cœurs)", " hearts)"));
	            if (acc.headshot) {
	                message.append(getTranslatedComponent(" (Tir à la tête !)", " (Headshot!)").withStyle(ChatFormatting.GOLD));
	            }
	            message.append(Component.literal(" !"));
	            this.level().players().forEach(p -> p.sendSystemMessage(message));
	        });
	        damageAccumulatorMap.clear();
	    }
	}
	
	@Override
	public boolean hurt(DamageSource source, float amount) {
	    if (!this.level().isClientSide) {
	        boolean isHeadshotCalc = false;
	        boolean isProjectile = source.getDirectEntity() instanceof Projectile;
	        if (isProjectile) {
	            Projectile projectile = (Projectile) source.getDirectEntity();
	            if (projectile.getY() >= this.getY() + this.getBbHeight() * 0.85) {
	                isHeadshotCalc = true;
	            }
	        }
	        final boolean finalIsHeadshot = isHeadshotCalc;
	
	        if (source.getEntity() instanceof Player player) {
	            UUID playerId = player.getUUID();
	            String playerName = player.getName().getString();
	            damageAccumulatorMap.compute(playerId, (key, acc) -> {
	                if (acc == null) {
	                    return new DamageAccumulator(amount, finalIsHeadshot, playerName);
	                } else {
	                    acc.add(amount, finalIsHeadshot);
	                    return acc;
	                }
	            });
	        }
	    }
	
	    if (source.getEntity() instanceof Player player) {
	        if (player.getMainHandItem().getItem() == Items.DIAMOND_PICKAXE) {
	            return super.hurt(source, amount); 
	        }
	    }
	    this.setHealth(this.getMaxHealth());
	    return true; 
	}
	
	public static void init() {}
	
	public static AttributeSupplier.Builder createAttributes() {
	    AttributeSupplier.Builder builder = Mob.createMobAttributes();
	    builder = builder.add(Attributes.MOVEMENT_SPEED, 0.0);
	    builder = builder.add(Attributes.MAX_HEALTH, 100); 
	    builder = builder.add(Attributes.ARMOR, 0);
	    builder = builder.add(Attributes.ATTACK_DAMAGE, 0);
	    builder = builder.add(Attributes.FOLLOW_RANGE, 16);
	    builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1.0); 
	    return builder;
	}
}