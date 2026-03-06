package me.cryo.zombierool.item;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.DummyEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.WeaponVfxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import java.util.List;

public class ThundergunItem extends WeaponSystem.BaseGunItem {

    public ThundergunItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override
    protected void performShooting(ItemStack stack, Player player, float charge) {
        if (player.level().isClientSide) return;
        boolean isPap = isPackAPunched(stack);
        float damage = getWeaponDamage(stack);
        
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f);
        double range = def.stats.range > 0 ? def.stats.range : 15.0;
        
        Vec3 visualStart = getVisualMuzzlePos(player);
        WeaponVfxPacket packet = new WeaponVfxPacket("THUNDERGUN", visualStart, eyePos.add(lookVec.scale(range)), isPap, false);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> player.level().getChunkAt(player.blockPosition())), packet);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer)player), packet);

        AABB box = player.getBoundingBox().inflate(range);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, box, e ->
            e != player && e.isAlive() &&
            (e instanceof ZombieEntity || e instanceof CrawlerEntity || e instanceof HellhoundEntity || e instanceof DummyEntity)
        );

        for (LivingEntity target : targets) {
            Vec3 toTarget = target.position().subtract(player.position());
            double dist = toTarget.length();
            if (dist > range) continue;
            
            Vec3 toTargetNorm = toTarget.normalize();
            double dot = lookVec.dot(toTargetNorm);
            
            if (dot > 0.5) {
                if (Math.abs(target.getY() - player.getY()) <= 3.0) {
                    DamageManager.applyDamage(target, player.damageSources().playerAttack(player), damage);
                    double kbStrength = isPap ? 3.5 : 2.5;
                    target.setDeltaMovement(lookVec.x * kbStrength, isPap ? 1.5 : 1.0, lookVec.z * kbStrength);
                    target.hurtMarked = true;
                }
            }
        }
    }
}