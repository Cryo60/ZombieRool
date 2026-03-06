package me.cryo.zombierool.item;

import me.cryo.zombierool.api.IOverheatable;
import me.cryo.zombierool.core.network.PacketShoot;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.Entity;

public class PlasmaPistolItem extends WeaponSystem.BaseGunItem implements IHandgunWeapon, IOverheatable {

	public static final String TAG_IS_OVERCHARGED = "zombierool:is_overcharged";
	public static final String TAG_OVERHEAT_LOCKED = "OverheatLocked";
	public static final String TAG_WAS_OVERHEATED_FLAG = "WasOverheatedFlag";

	private static final int CHARGE_TIME_TICKS = 20;
	private static final float MIN_CHARGE_FOR_OVERCHARGE = 0.25f; 

	public PlasmaPistolItem(WeaponSystem.Definition def) {
	    super(def);
	}

	@Override
	public boolean hasOverheat() {
	    return true;
	}

	@Override
	public int getMaxOverheat() {
	    return 100;
	}

	@Override
	public int getCooldownPerTick(ItemStack stack) {
	    return 1;
	}

	@Override
	public void onOverheat(ItemStack stack, Player player) {
	    playSound(player.level(), player, def.sounds.dry);
	}

	@Override
	public void onOverheatCooled(ItemStack stack, Player player) {
	    playSound(player.level(), player, def.sounds.equip);
	}

	public boolean isOverheatLocked(ItemStack stack) {
	    return getOrCreateTag(stack).getBoolean(TAG_OVERHEAT_LOCKED);
	}

	public void setOverheatLocked(ItemStack stack, boolean locked) {
	    getOrCreateTag(stack).putBoolean(TAG_OVERHEAT_LOCKED, locked);
	}

	public boolean getWasOverheatedFlag(ItemStack stack) {
	    return getOrCreateTag(stack).getBoolean(TAG_WAS_OVERHEATED_FLAG);
	}

	public void setWasOverheatedFlag(ItemStack stack, boolean flag) {
	    getOrCreateTag(stack).putBoolean(TAG_WAS_OVERHEATED_FLAG, flag);
	}

	@Override
	public void initializeIfNeeded(ItemStack stack) {
	    super.initializeIfNeeded(stack);
	    CompoundTag tag = getOrCreateTag(stack);
	    if (!tag.contains(TAG_DURABILITY)) tag.putInt(TAG_DURABILITY, getMaxDurability(stack));
	    if (!tag.contains(TAG_OVERHEAT)) tag.putInt(TAG_OVERHEAT, 0);
	    if (!tag.contains(TAG_OVERHEAT_LOCKED)) tag.putBoolean(TAG_OVERHEAT_LOCKED, false);
	    if (!tag.contains(TAG_WAS_OVERHEATED_FLAG)) tag.putBoolean(TAG_WAS_OVERHEATED_FLAG, false);
	}

	@Override
	public void applyPackAPunch(ItemStack stack) {
	    super.applyPackAPunch(stack);
	    setOverheat(stack, 0);
	    setOverheatLocked(stack, false);
	    setWasOverheatedFlag(stack, false);
	    setDurability(stack, getMaxDurability(stack));
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity ent, int slot, boolean selected) {
	    super.inventoryTick(stack, level, ent, slot, selected);
	    if (!(ent instanceof Player player)) return;

	    int currentOverheat = getOverheat(stack);
	    boolean wasLocked = isOverheatLocked(stack);

	    if (wasLocked && currentOverheat <= (getMaxOverheat() * 0.15f)) {
	        setOverheatLocked(stack, false);
	        setWasOverheatedFlag(stack, false);
	    }
	}

	@Override
	protected boolean executeShot(ItemStack stack, Player player, float charge, boolean isLeft) {
	    if (!getOrCreateTag(stack).contains(TAG_DURABILITY)) {
	        initializeIfNeeded(stack);
	    }

	    if (isOverheatLocked(stack) || getDurability(stack) <= 0) {
	        playSound(player.level(), player, def.sounds.dry);
	        return false;
	    }

	    boolean isOvercharged = charge >= MIN_CHARGE_FOR_OVERCHARGE;
	    boolean isPap = isPackAPunched(stack);

	    int heatCost = isOvercharged ? (int)(10 + (25 * charge)) : 10;
	    int durabilityDrain = isOvercharged ? (int)(1 + (3 * charge)) : 1;
	    int cooldownTicks = isOvercharged ? 30 : def.stats.fire_rate;

	    if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
	        cooldownTicks = Math.max(1, (int)(cooldownTicks * 0.75f));
	    }

	    if (isPap) {
	        heatCost = (int)(heatCost * 0.75);
	    }

	    if (getOverheat(stack) + heatCost > getMaxOverheat()) {
	        setOverheatLocked(stack, true);
	        setOverheat(stack, getMaxOverheat());
	        setWasOverheatedFlag(stack, true);
	        if (!player.level().isClientSide) {
	            onOverheat(stack, player);
	        }
	        return false;
	    }

	    int multiplier = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get()) ? 2 : 1;
	    for (int m = 0; m < multiplier; m++) {
	        performShooting(stack, player, charge, isLeft);
	    }

	    if (!player.isCreative()) {
	        int newHeat = getOverheat(stack) + heatCost;
	        setOverheat(stack, newHeat);

	        setDurability(stack, getDurability(stack) - durabilityDrain);
	        if (getDurability(stack) <= 0) {
	            if (!player.level().isClientSide) {
	                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
	            }
	            stack.shrink(1);
	        }

	        if (newHeat >= getMaxOverheat()) {
	            setOverheatLocked(stack, true);
	            setWasOverheatedFlag(stack, true);
	            if (!player.level().isClientSide) onOverheat(stack, player);
	        }
	    }

	    getOrCreateTag(stack).putLong(isLeft ? TAG_LAST_FIRE_LEFT : TAG_LAST_FIRE, player.level().getGameTime());
	    player.getCooldowns().addCooldown(this, cooldownTicks);

	    return true;
	}

	@Override
	protected void performShooting(ItemStack stack, Player player, float charge) {
	    performShooting(stack, player, charge, false);
	}

	@Override
	protected void performShooting(ItemStack stack, Player player, float charge, boolean isLeft) {
	    Level level = player.level();
	    if (level.isClientSide) return;

	    boolean isOvercharged = charge >= MIN_CHARGE_FOR_OVERCHARGE;
	    boolean isPap = isPackAPunched(stack);

	    float actualDamage = def.stats.damage;
	    if (isOvercharged) {
	        float overchargeFactor = (charge - MIN_CHARGE_FOR_OVERCHARGE) / (1.0f - MIN_CHARGE_FOR_OVERCHARGE);
	        actualDamage = def.stats.damage * 1.5f + (def.stats.damage * 1.5f * overchargeFactor); 
	    }
	    if (isPap) {
	        actualDamage += isOvercharged ? def.pap.damage_bonus * 0.75f : def.pap.damage_bonus;
	    }

	    Vec3 start = player.getEyePosition(1F);
	    Vec3 dir   = player.getViewVector(1F);

	    float velocity = isOvercharged ? def.ballistics.velocity * 1.5f : def.ballistics.velocity;
	    float spread = isOvercharged ? def.ballistics.spread * 0.125f : def.ballistics.spread;

	    if (isPap) {
	        velocity *= def.pap.reload_speed_mult;
	        spread *= def.pap.spread_mult;
	    }

	    Arrow arrow = new Arrow(level, player);
	    arrow.setOwner(player);
	    arrow.setPos(start.x, start.y, start.z);
	    arrow.setSilent(true);
	    arrow.setNoGravity(true);
	    arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
	    arrow.setBaseDamage(0.0D); 

	    arrow.getPersistentData().putBoolean("zombierool:custom_projectile", true);
	    arrow.getPersistentData().putFloat("zombierool:damage", actualDamage);
	    arrow.getPersistentData().putBoolean(TAG_IS_OVERCHARGED, isOvercharged);
	    arrow.getPersistentData().putBoolean("zombierool:plasma_impact", true);
	    arrow.getPersistentData().putBoolean("zombierool:plasma_charge", true);
	    arrow.getPersistentData().putFloat("zombierool:plasma_charge_level", charge);
	    arrow.getPersistentData().putBoolean("zombierool:plasma_pap", isPap);

	    arrow.setInvisible(false);

	    float yawOffset = isLeft ? -3.0f : 3.0f;
	    arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset, 0.0F, velocity, spread);
	    level.addFreshEntity(arrow);

	    playSound(level, player, def.sounds.fire);
	}

	@Override
	public Component getName(ItemStack stack) {
	    boolean upgraded = isPackAPunched(stack);
	    String baseName = upgraded ? (def.pap.name != null ? def.pap.name : "Pew Pew") : def.name;
	    MutableComponent nameComponent = Component.literal((upgraded ? "§d" : "§2") + baseName);

	    int currentOverheat = getOverheat(stack);
	    if (isOverheatLocked(stack)) {
	        nameComponent.append(Component.literal(" §4(Surchauffé !)"));
	    } else if (currentOverheat > getMaxOverheat() * 0.75) {
	        nameComponent.append(Component.literal(" §e(Chauffe !)"));
	    }

	    return nameComponent;
	}

	@OnlyIn(Dist.CLIENT)
	public static class PlasmaPistolClient {
	    private static AbstractTickableSoundInstance currentChargeLoop = null;
	    private static int chargeTicks = 0;
	    private static boolean wasAttackDownLastTick = false;

	    public static void handleTick(Minecraft mc, Player player, ItemStack stack, boolean attackDown) {
	        PlasmaPistolItem gun = (PlasmaPistolItem) stack.getItem();

	        if (player.getCooldowns().isOnCooldown(gun) || gun.isOverheatLocked(stack) || gun.getDurability(stack) <= 0) {
	            stopSound();
	            chargeTicks = 0;
	            wasAttackDownLastTick = false;
	            return;
	        }

	        if (attackDown) {
	            chargeTicks++;
	            if (chargeTicks >= 5) {
	                keepSoundAlive(player);
	                float chargeRatio = Math.min(1.0f, (float)(chargeTicks - 5) / 15.0f);

	                Vec3 eye = player.getEyePosition();
	                Vec3 look = player.getViewVector(1.0f);
	                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
	                Vec3 up = right.cross(look).normalize();
	                Vec3 barrel = eye.add(look.scale(0.8)).add(right.scale(0.35)).add(up.scale(-0.25));

	                boolean isPap = gun.isPackAPunched(stack);

	                for(int i = 0; i < 2; i++) {
	                    double spread = 0.25 * chargeRatio;
	                    double ox = (mc.level.random.nextDouble() - 0.5) * spread;
	                    double oy = (mc.level.random.nextDouble() - 0.5) * spread;
	                    double oz = (mc.level.random.nextDouble() - 0.5) * spread;
	                    
	                    if (isPap) {
	                        mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, barrel.x + ox, barrel.y + oy, barrel.z + oz, 0, 0, 0);
	                    } else {
	                        mc.level.addParticle(ParticleTypes.GLOW, barrel.x + ox, barrel.y + oy, barrel.z + oz, 0, 0, 0);
	                    }
	                }
	            }
	        } else {
	            if (wasAttackDownLastTick) {
	                float charge = Mth.clamp((float) chargeTicks / (float) CHARGE_TIME_TICKS, 0.0f, 1.0f);
	                
	                NetworkHandler.INSTANCE.sendToServer(new PacketShoot(charge, false));

	                boolean isOvercharged = charge >= MIN_CHARGE_FOR_OVERCHARGE;
	                int heatCost = isOvercharged ? (int)(10 + (25 * charge)) : 10;
	                if (gun.isPackAPunched(stack)) heatCost = (int)(heatCost * 0.75);

	                int newHeat = gun.getOverheat(stack) + heatCost;
	                gun.setOverheat(stack, Math.min(gun.getMaxOverheat(), newHeat));
	                stack.getOrCreateTag().putLong(WeaponSystem.BaseGunItem.TAG_LAST_FIRE, player.level().getGameTime());

	                if (newHeat >= gun.getMaxOverheat()) {
	                    gun.setOverheatLocked(stack, true);
	                    gun.setWasOverheatedFlag(stack, true);
	                }

	                int cooldownTicks = isOvercharged ? 30 : gun.getDefinition().stats.fire_rate;
	                if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
	                    cooldownTicks = Math.max(1, (int)(cooldownTicks * 0.75f));
	                }
	                player.getCooldowns().addCooldown(gun, cooldownTicks);

	                chargeTicks = 0;
	                stopSound();
	            }
	        }

	        wasAttackDownLastTick = attackDown;
	    }

	    public static void stopSound() {
	        if (currentChargeLoop != null) {
	            Minecraft.getInstance().getSoundManager().stop(currentChargeLoop);
	            currentChargeLoop = null;
	        }
	    }

	    private static void keepSoundAlive(Player player) {
	        Minecraft mc = Minecraft.getInstance();
	        if (currentChargeLoop == null || !mc.getSoundManager().isActive(currentChargeLoop)) {
	            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:plasma_pistol_charge_loop"));
	            if (sound != null) {
	                currentChargeLoop = new LoopingPlasmaChargeSound(sound, player);
	                mc.getSoundManager().play(currentChargeLoop);
	            }
	        }
	    }

	    public static class LoopingPlasmaChargeSound extends AbstractTickableSoundInstance {
	        private final Player player;

	        public LoopingPlasmaChargeSound(SoundEvent soundEvent, Player player) {
	            super(soundEvent, SoundSource.PLAYERS, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
	            this.player = player;
	            this.looping = true;
	            this.x = player.getX();
	            this.y = player.getY();
	            this.z = player.getZ();
	            this.volume = 0.7f;
	            this.pitch = 1.0f;
	            this.relative = true;
	            this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
	        }

	        @Override
	        public void tick() {
	            if (player.isRemoved() || !wasAttackDownLastTick || chargeTicks == 0) {
	                this.stop();
	            } else {
	                this.x = player.getX();
	                this.y = player.getY();
	                this.z = player.getZ();
	            }
	        }
	    }
	}
}