package me.cryo.zombierool.client;

import com.mojang.math.Axis;
import me.cryo.zombierool.item.ScarletWeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Palm-centered grips and a two-handed halberd cut; the shield kit renders both hands. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ScarletWeaponClient {
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void player(net.minecraftforge.client.event.RenderPlayerEvent.Pre e){
        if(!(e.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer player))return;
        var data=dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAssociatedData(player);
        var id=new net.minecraft.resources.ResourceLocation("zombierool","scarlet_weapon");
        if(data.get(id)==null){var pose=new ScarletWeaponPose(player);data.set(id,pose);dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(1100,pose);}
        MapRangedWeaponVisuals.attach(player);
    }
    private static net.minecraft.client.model.geom.ModelPart grip;
    static net.minecraft.client.model.geom.ModelPart grip(){
        if(grip==null){var mesh=new net.minecraft.client.model.geom.builders.MeshDefinition();
            var cubes=net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                .texOffs(40,24).addBox(-1.7f,-2,-1.8f,3.4f,4,3.6f)
                .texOffs(40,16).addBox(-1.7f,-8,-1.8f,3.4f,6,3.6f);
            mesh.getRoot().addOrReplaceChild("hand",cubes,net.minecraft.client.model.geom.PartPose.ZERO);
            grip=net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot();}
        return grip;
    }
    private static float smooth(float t){t=net.minecraft.util.Mth.clamp(t,0,1);return t*t*(3-2*t);}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST,receiveCanceled=true)
    public static void hand(net.minecraftforge.client.event.RenderHandEvent e){
        var mc=Minecraft.getInstance();if(mc.player==null||me.cryo.zombierool.gameplay.PortableEquipment.inHands(mc.player))return;
        boolean off=e.getHand()==InteractionHand.OFF_HAND;var stack=mc.player.getItemInHand(e.getHand());
        if(off&&me.cryo.zombierool.gameplay.MeleeMoves.twoHanded(mc.player.getMainHandItem())){e.setCanceled(true);return;}
        if(off?!(stack.getItem() instanceof ScarletWeaponItem.Guard):!(stack.getItem() instanceof me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem))return;
        e.setCanceled(true);var pose=e.getPoseStack();var buffers=e.getMultiBufferSource();
        int side=mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?1:-1;if(off)side=-side;
        var original=new org.joml.Matrix4f(pose.last().pose());org.joml.Vector3f wrist=null,supportWrist=null;
        float idle=(float)Math.sin((mc.player.tickCount+e.getPartialTick())*.08)*.006f;
        pose.pushPose();pose.translate(side*.47,-.4+idle-e.getEquipProgress()*.4,-.95);
        var held=stack;
        if(off){boolean blocking=mc.player.isUsingItem()&&mc.player.getUsedItemHand()==InteractionHand.OFF_HAND;
            pose.translate(-side*(blocking?.18:0),blocking?.12:-.03,blocking?-.04:.06);
            pose.mulPose(Axis.YP.rotationDegrees(180+side*(blocking?0:15)));pose.scale(1.15f,1.15f,1.15f);
        }else{
            var weapon=(me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem)stack.getItem();
            var def=weapon.getDefinition();var motion=MeleeMotionClient.pose(mc.player,e.getPartialTick());
            float charge=smooth(MeleeMotionClient.charge(mc.player,e.getPartialTick()));
            // Apply the attack to the grip pivot, then the weapon's authored resting alignment.
            pose.translate(side*(motion[0]+.10*charge),motion[1]+.12*charge,motion[2]+.08*charge+MeleeMotionClient.recoil(mc.player,e.getPartialTick()));
            pose.mulPose(Axis.YP.rotationDegrees(side*(motion[4]+12*charge)));
            pose.mulPose(Axis.ZP.rotationDegrees(side*(motion[5]-16*charge)));
            pose.mulPose(Axis.XP.rotationDegrees(motion[3]-38*charge));
            boolean two=me.cryo.zombierool.gameplay.MeleeMoves.twoHanded(stack);
            pose.translate(side*MapWeaponAnimation.value(def,"fpX",0,0,0),MapWeaponAnimation.value(def,"fpY",0,0,0)-(two?.22f:0),MapWeaponAnimation.value(def,"fpZ",0,0,0));
            pose.mulPose(Axis.YP.rotationDegrees(side*MapWeaponAnimation.value(def,"fpRy",0,0,-20)));
            pose.mulPose(Axis.ZP.rotationDegrees(side*MapWeaponAnimation.value(def,"fpRz",0,0,-12)));
            pose.mulPose(Axis.XP.rotationDegrees(MapWeaponAnimation.value(def,"fpRx",0,0,two?-35:-12)));
            float scale=net.minecraft.util.Mth.clamp(MapWeaponAnimation.value(def,"fpScale",0,0,two?.8f:.85f),.1f,3);if(two)scale*=.9f;pose.scale(scale,scale,scale);
            if(me.cryo.zombierool.gameplay.MeleeMoves.kind(stack).equals("shield"))stack=new ItemStack(me.cryo.zombierool.init.ZombieroolModItems.SCARLET_SWORD.get());
        }
        mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,e.getPackedLight(),OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);
        boolean great=!off&&me.cryo.zombierool.gameplay.MeleeMoves.kind(held).equals("greatsword");
        pose.pushPose();pose.translate(0,great?-.18:-.23,off?.18:0);
        wrist=MeleeArms.hand(pose,original,buffers,mc.player.getSkinTextureLocation(),e.getPackedLight(),side);pose.popPose();
        if(!off&&me.cryo.zombierool.gameplay.MeleeMoves.twoHanded(held)){
            pose.translate(0,great?-.38:.18,.02);pose.mulPose(Axis.XP.rotationDegrees(-20));pose.mulPose(Axis.ZP.rotationDegrees(-side*65));
            supportWrist=MeleeArms.hand(pose,original,buffers,mc.player.getSkinTextureLocation(),e.getPackedLight(),-side);
        }
        pose.popPose();
        if(wrist!=null)MeleeArms.connect(pose,buffers,mc.player.getSkinTextureLocation(),e.getPackedLight(),wrist,side);
        if(supportWrist!=null)MeleeArms.connect(pose,buffers,mc.player.getSkinTextureLocation(),e.getPackedLight(),supportWrist,-side);
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Models {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(new net.minecraft.resources.ResourceLocation("zombierool","scarlet_weapon"),1100,ScarletWeaponPose::new);net.minecraft.client.renderer.item.ItemProperties.register(me.cryo.zombierool.init.ZombieroolModItems.SCARLET_SHIELD.get(),new net.minecraft.resources.ResourceLocation("zombierool","held_kit"),(stack,level,entity,seed)->entity!=null&&entity.getMainHandItem()==stack?1:0);});}
    }
}
