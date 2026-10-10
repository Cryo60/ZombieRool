package me.cryo.zombierool.gameplay;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Contact uses the victim's volume, not its feet or its eye height. */
public final class TrapContact {
    private static final UUID SLOW_ID=UUID.fromString("e85d5414-5e0d-4cf3-9fd2-132f2209a8d5");
    public static final AttributeModifier SLOWDOWN=new AttributeModifier(SLOW_ID,"Trap contact",-.9,AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final String UNTIL="zr_trap_slow_until";
    public static Vec3 beamEnd(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.core.Direction facing,double range){
        Vec3 origin=Vec3.atCenterOf(pos),end=origin.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(Math.min(range,16)));
        Vec3 clipped=net.minecraft.world.level.BlockGetter.traverseBlocks(origin,end,null,(ctx,p)->{
            if(level.getBlockState(p).getBlock() instanceof me.cryo.zombierool.block.system.MapDeviceSystem.DeviceBlock)return null;
            var shape=level.getBlockState(p).getCollisionShape(level,p,net.minecraft.world.phys.shapes.CollisionContext.empty());
            var hit=shape.clip(origin,end,p);return hit==null?null:hit.getLocation();
        },ctx->null);
        return clipped==null?end:clipped;
    }
    public static AABB area(Vec3 origin,Vec3 end){return new AABB(origin,end).inflate(.65);}
    public static Vec3 closest(Vec3 origin,AABB victim){return new Vec3(
            Math.max(victim.minX,Math.min(victim.maxX,origin.x)),
            Math.max(victim.minY,Math.min(victim.maxY,origin.y)),
            Math.max(victim.minZ,Math.min(victim.maxZ,origin.z)));}
    public static void slow(LivingEntity victim) {
        var speed=victim.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null && speed.getModifier(SLOW_ID)==null)speed.addTransientModifier(SLOWDOWN);
        victim.getPersistentData().putLong(UNTIL,victim.level().getGameTime()+2);
    }
    public static void tick(LivingEntity victim) {
        var data=victim.getPersistentData();
        if(data.contains(UNTIL) && victim.level().getGameTime()>data.getLong(UNTIL)) {
            var speed=victim.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SLOW_ID);
            data.remove(UNTIL);
        }
    }
}
