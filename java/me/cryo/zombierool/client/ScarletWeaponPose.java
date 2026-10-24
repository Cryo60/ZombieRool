package me.cryo.zombierool.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import me.cryo.zombierool.item.ScarletWeaponItem;

/** Shared attack phases. In third person the support hand follows the actual shaft. */
public final class ScarletWeaponPose implements IAnimation {
    private final AbstractClientPlayer player;
    public ScarletWeaponPose(AbstractClientPlayer player){this.player=player;}
    public static float smooth(float t){t=net.minecraft.util.Mth.clamp(t,0,1);return t*t*(3-2*t);}
    public static float[] phases(float progress){return new float[]{smooth(progress/.3f)-smooth((progress-.3f)/.3f),smooth((progress-.3f)/.3f)-smooth((progress-.6f)/.4f)};}
    @Override public boolean isActive(){return player.getMainHandItem().getItem() instanceof ScarletWeaponItem&&!ThirdPersonAnimHandler.isAnimationPlaying(player.getUUID())&&!me.cryo.zombierool.gameplay.PortableEquipment.inHands(player);}
    @Override public void setupAnim(float delta){}
    @Override public Vec3f get3DTransform(String bone,TransformType type,float delta,Vec3f original){
        if(!(player.getMainHandItem().getItem() instanceof ScarletWeaponItem weapon))return original;
        boolean right=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT;
        String main=right?"rightArm":"leftArm",support=right?"leftArm":"rightArm";float side=right?1:-1;
        var phase=phases(player.getAttackAnim(delta));float wind=phase[0],strike=phase[1];
        float rx=weapon.twoHanded()?-.95f-.6f*wind+1.3f*strike:weapon.kind().equals("axe")?-1f-.7f*wind+1.2f*strike:-1.1f-.45f*wind+1f*strike;
        float ry=side*(-.15f+.3f*strike),rz=side*(-.1f-.3f*strike);
        rx=MapWeaponAnimation.value(weapon.getDefinition(),"tpX",wind,strike,rx);
        ry=side*MapWeaponAnimation.value(weapon.getDefinition(),"tpY",wind,strike,ry/side);
        rz=side*MapWeaponAnimation.value(weapon.getDefinition(),"tpZ",wind,strike,rz/side);
        int combo=player.getMainHandItem().getOrCreateTag().getInt("zr_combo");float charge=player.isUsingItem()&&player.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND?Math.min(1,player.getTicksUsingItem()/16f):0;rx-=.6f*charge;rz+=side*((combo==1?-.65f:combo==2?.4f:0)*strike-.4f*charge);
        if(bone.equals(main)&&type==TransformType.ROTATION)return new Vec3f(rx,ry,rz);
        if(bone.equals(support)&&weapon.twoHanded()){
            if(type==TransformType.ROTATION)return new Vec3f(rx-.12f,ry+side*.65f,rz+side*.25f);
            return original;
        }
        return original;
    }
}
