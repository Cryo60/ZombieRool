package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.manager.BallisticManager;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CWeaponVfxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** TaCZ keeps Mark II's real burst/piercing bullets; ZR supplies the energy visuals. */
public final class RayGunShots {
    private RayGunShots() {}
    public static void markIIFired(ServerPlayer player,WeaponSystem.Definition definition,boolean pap){
        Vec3 eye=player.getEyePosition(),direction=player.getLookAngle();
        Vec3 end=player.level().clip(new ClipContext(eye,eye.add(direction.scale(definition.stats.range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getLocation();
        var target=BallisticManager.getRayTraceTarget(player,definition.stats.range);
        if(target!=null)end=target.getBoundingBox().clip(eye,end).orElse(end);
        Vec3 right=direction.cross(new Vec3(0,1,0)).normalize();
        send(player, "RAYGUN_MK2",eye.add(direction.scale(.55)).add(right.scale(.20)).add(0,-.16,0),end,pap);
    }
    public static void impact(ServerPlayer player,Vec3 at,boolean pap){send(player,"RAYGUN_MK2_IMPACT",at,at,pap);}
    private static void send(ServerPlayer player,String kind,Vec3 from,Vec3 to,boolean pap){
        NetworkHandler.INSTANCE.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(from.x,from.y,from.z,128,player.level().dimension())),new S2CWeaponVfxPacket(kind,from,to,pap,false));
    }
}
