package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.WeaponVfxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

public class StormItem extends WeaponSystem.BaseGunItem {

    public static final String TAG_CONSECUTIVE_SHOTS = "ConsecutiveShots";
    public static final String TAG_OVERHEAT_LOCKED = "OverheatLocked";

    public StormItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override
    public boolean hasOverheat() {
        return true;
    }

    @Override
    public int getMaxOverheat() {
        return 120;
    }

    @Override
    protected int getOverheatPerShot(ItemStack stack) {
        return isPackAPunched(stack) ? 1 : 3;
    }

    @Override
    protected int getCooldownPerTick(ItemStack stack) {
        return 3;
    }

    @Override
    public int getFireRate(ItemStack stack, @Nullable Player player) {
        int consecutive = getOrCreateTag(stack).getInt(TAG_CONSECUTIVE_SHOTS);
        int baseRate = def.stats.fire_rate; 
        
        int reduction = consecutive / 5; 
        int rate = Math.max(1, baseRate - reduction);
        
        if (player != null && player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAPE.get())) {
            rate = Math.max(1, (int)(rate * 0.75f));
        }
        
        return rate;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        
        if (!level.isClientSide && entity instanceof Player player) {
            long now = level.getGameTime();
            long lastFire = getOrCreateTag(stack).getLong(TAG_LAST_FIRE);
            
            if (now - lastFire > 10) {
                getOrCreateTag(stack).putInt(TAG_CONSECUTIVE_SHOTS, 0); 
            }

            int heat = getOverheat(stack);
            boolean locked = isOverheatLocked(stack);

            if (heat >= getMaxOverheat() - 5 && !locked) {
                setOverheatLocked(stack, true);
                playSound(level, player, "zombierool:storm_overheat");
            } else if (locked && heat <= getMaxOverheat() / 3) {
                setOverheatLocked(stack, false);
                playSound(level, player, def.sounds.equip);
            }
        }
    }

    public boolean isOverheatLocked(ItemStack stack) {
        return getOrCreateTag(stack).getBoolean(TAG_OVERHEAT_LOCKED);
    }

    public void setOverheatLocked(ItemStack stack, boolean locked) {
        getOrCreateTag(stack).putBoolean(TAG_OVERHEAT_LOCKED, locked);
    }

    @Override
    protected boolean executeShot(ItemStack stack, Player player, float charge, boolean isLeft) {
        if (isOverheatLocked(stack) || getDurability(stack) <= 0) {
            playSound(player.level(), player, def.sounds.dry);
            return false;
        }

        boolean success = super.executeShot(stack, player, charge, isLeft);
        if (success) {
            int consecutive = getOrCreateTag(stack).getInt(TAG_CONSECUTIVE_SHOTS);
            getOrCreateTag(stack).putInt(TAG_CONSECUTIVE_SHOTS, consecutive + 1);
        }
        return success;
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge) {
        if (player.level().isClientSide) return;
        ServerLevel level = (ServerLevel) player.level();

        boolean isPap = isPackAPunched(stack);
        float damage = getWeaponDamage(stack);
        float spread = isPap ? def.ballistics.spread * def.pap.spread_mult : def.ballistics.spread;

        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f);
        
        double factor = 0.045;
        lookVec = lookVec.add(
            player.getRandom().nextGaussian() * factor * spread,
            player.getRandom().nextGaussian() * factor * spread,
            player.getRandom().nextGaussian() * factor * spread
        ).normalize();

        Vec3 endPos = eyePos.add(lookVec.scale(def.stats.range));

        net.minecraft.world.phys.HitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
            eyePos, endPos, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));
        
        Vec3 finalEndPos = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? endPos : hit.getLocation();

        AABB searchBox = new AABB(eyePos, finalEndPos).inflate(1.0);
        java.util.List<Entity> hitEntities = level.getEntities(player, searchBox, e -> 
            e instanceof LivingEntity && e.isAlive() && 
            (e instanceof me.cryo.zombierool.entity.ZombieEntity || 
             e instanceof me.cryo.zombierool.entity.CrawlerEntity || 
             e instanceof me.cryo.zombierool.entity.HellhoundEntity || 
             e instanceof me.cryo.zombierool.entity.DummyEntity)
        );

        LivingEntity closestTarget = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : hitEntities) {
            AABB bbox = e.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> clip = bbox.clip(eyePos, finalEndPos);
            if (clip.isPresent() || bbox.contains(eyePos)) {
                double dist = eyePos.distanceToSqr(e.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    closestTarget = (LivingEntity) e;
                }
            }
        }

        boolean hitHeadshot = false;

        if (closestTarget != null) {
            finalEndPos = closestTarget.position().add(0, closestTarget.getBbHeight() / 2, 0);
            hitHeadshot = finalEndPos.y >= closestTarget.getY() + closestTarget.getBbHeight() * 0.85;

            float finalDamage = me.cryo.zombierool.core.manager.DamageManager.calculateDamage((ServerPlayer) player, closestTarget, damage, hitHeadshot, stack);
            
            closestTarget.getPersistentData().putBoolean(me.cryo.zombierool.core.manager.DamageManager.GUN_DAMAGE_TAG, true);
            if (hitHeadshot) closestTarget.getPersistentData().putBoolean(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG, true);

            if (me.cryo.zombierool.core.manager.DamageManager.applyDamage(closestTarget, player.damageSources().playerAttack(player), finalDamage)) {
                closestTarget.level().playSound(null, closestTarget.getX(), closestTarget.getY(), closestTarget.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:impact_flesh")),
                    SoundSource.PLAYERS, 0.5f, 1.0f + (closestTarget.getRandom().nextFloat() * 0.2f));

                me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), new me.cryo.zombierool.network.DisplayHitmarkerPacket());
            }

            if (isPap) {
                closestTarget.setSecondsOnFire(4);
            }
        }

        Vec3 visualStartPos = getVisualMuzzlePos(player);
        WeaponVfxPacket packet = new WeaponVfxPacket("STORM", visualStartPos, finalEndPos, isPap, hitHeadshot);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(player.blockPosition())), packet);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), packet);
    }
}