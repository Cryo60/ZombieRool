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
    @Override public boolean isActive(){return player.getMainHandItem().getItem() instanceof me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem&&!ThirdPersonAnimHandler.isAnimationPlaying(player.getUUID())&&!me.cryo.zombierool.gameplay.PortableEquipment.inHands(player);}
    @Override public void setupAnim(float delta){}
    @Override public Vec3f get3DTransform(String bone,TransformType type,float delta,Vec3f original){
        if(!(player.getMainHandItem().getItem() instanceof me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem weapon))return original;
        boolean right=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT;
        String main=right?"rightArm":"leftArm",support=right?"leftArm":"rightArm";float side=right?1:-1;
        var stack=player.getMainHandItem();boolean two=me.cryo.zombierool.gameplay.MeleeMoves.twoHanded(stack);
        var motion=MeleeMotionClient.pose(player,delta);float charge=smooth(MeleeMotionClient.charge(player,delta));
        float rx=MapWeaponAnimation.value(weapon.getDefinition(),"tpX",0,0,two?-.95f:-1.1f)+(motion[3]-38*charge)*.0174533f;
        float ry=side*(MapWeaponAnimation.value(weapon.getDefinition(),"tpY",0,0,-.15f)+(motion[4]+12*charge)*.012f);
        float rz=side*(MapWeaponAnimation.value(weapon.getDefinition(),"tpZ",0,0,-.1f)+(motion[5]-16*charge)*.012f);
        if(bone.equals("body")&&type==TransformType.ROTATION)return new Vec3f(original.getX(),original.getY()+side*motion[6],original.getZ());
        if(bone.equals(main)&&type==TransformType.ROTATION)return new Vec3f(rx,ry,rz);
        if(bone.equals(support)&&two&&type==TransformType.ROTATION)return new Vec3f(rx-.12f,ry+side*.65f,rz+side*.25f);
        return original;
    }
}
