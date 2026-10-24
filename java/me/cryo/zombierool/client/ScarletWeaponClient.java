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
        if(off&&mc.player.getMainHandItem().getItem() instanceof ScarletWeaponItem main&&main.twoHanded()){e.setCanceled(true);return;}
        if(off?!(stack.getItem() instanceof ScarletWeaponItem.Guard):!(stack.getItem() instanceof ScarletWeaponItem))return;
        e.setCanceled(true);var pose=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        int side=mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?1:-1;if(off)side=-side;
        float progress=e.getSwingProgress();var phases=ScarletWeaponPose.phases(progress);float ready=phases[0],stroke=phases[1];
        float idle=(float)Math.sin((mc.player.tickCount+e.getPartialTick())*.08)*.008f;
        pose.pushPose();pose.translate(side*.47,-.4+idle-e.getEquipProgress()*.4,-.95);
        if(off){boolean blocking=mc.player.isUsingItem()&&mc.player.getUsedItemHand()==InteractionHand.OFF_HAND;
            pose.translate(-side*(blocking?.18:0),blocking?.12:-.03,blocking?-.04:.06);
            pose.mulPose(Axis.YP.rotationDegrees(180+side*(blocking?0:15)));pose.scale(1.15f,1.15f,1.15f);
        }else{var weapon=(ScarletWeaponItem)stack.getItem();
            if(MapWeaponAnimation.authored(weapon.getDefinition())){
                var def=weapon.getDefinition();
                pose.translate(side*MapWeaponAnimation.value(def,"fpX",ready,stroke,0),MapWeaponAnimation.value(def,"fpY",ready,stroke,0),MapWeaponAnimation.value(def,"fpZ",ready,stroke,0));
                pose.mulPose(Axis.YP.rotationDegrees(side*MapWeaponAnimation.value(def,"fpRy",ready,stroke,0)));
                pose.mulPose(Axis.ZP.rotationDegrees(side*MapWeaponAnimation.value(def,"fpRz",ready,stroke,0)));
                pose.mulPose(Axis.XP.rotationDegrees(MapWeaponAnimation.value(def,"fpRx",ready,stroke,0)));
                float scale=net.minecraft.util.Mth.clamp(MapWeaponAnimation.value(def,"fpScale",ready,stroke,1),.1f,3);pose.scale(scale,scale,scale);
                if(weapon.kind().equals("shield"))stack=new ItemStack(me.cryo.zombierool.init.ZombieroolModItems.SCARLET_SWORD.get());
            }else if(weapon.twoHanded()){
                pose.translate(side*(-.04*ready-.12*stroke),.07*ready-.08*stroke,-.17*stroke);
                pose.mulPose(Axis.YP.rotationDegrees(side*(-12+6*ready-12*stroke)));
                pose.mulPose(Axis.ZP.rotationDegrees(side*(12+8*ready-25*stroke)));
                pose.mulPose(Axis.XP.rotationDegrees(-35-30*ready+65*stroke));pose.scale(.8f,.8f,.8f);
            }else if(weapon.kind().equals("axe")){
                pose.translate(side*(-.08*ready-.2*stroke),.18*ready-.12*stroke,-.15*stroke);
                pose.mulPose(Axis.YP.rotationDegrees(side*-25));
                pose.mulPose(Axis.ZP.rotationDegrees(side*(-18+22*ready-25*stroke)));
                pose.mulPose(Axis.XP.rotationDegrees(-8-50*ready+72*stroke));pose.scale(.85f,.85f,.85f);
            }else{
                pose.translate(side*(.07*ready-.35*stroke),.08*ready-.08*stroke,-.12*stroke);
                pose.mulPose(Axis.YP.rotationDegrees(side*-20));
                pose.mulPose(Axis.ZP.rotationDegrees(side*(-12-25*ready+70*stroke)));
                pose.mulPose(Axis.XP.rotationDegrees(-12-20*ready+35*stroke));pose.scale(.85f,.85f,.85f);
                if(weapon.kind().equals("shield"))stack=new ItemStack(me.cryo.zombierool.init.ZombieroolModItems.SCARLET_SWORD.get());
            }
        }
        if(!off&&stack.getItem() instanceof ScarletWeaponItem){int combo=stack.getOrCreateTag().getInt("zr_combo");float charge=mc.player.isUsingItem()&&mc.player.getUsedItemHand()==InteractionHand.MAIN_HAND?Math.min(1,mc.player.getTicksUsingItem()/16f):0;pose.mulPose(Axis.ZP.rotationDegrees(side*((combo==1?-38:combo==2?22:0)*stroke-25*charge)));pose.mulPose(Axis.XP.rotationDegrees(-35*charge));pose.translate(0,.12*charge,.05*charge);}
        mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,e.getPackedLight(),OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);
        // Grip follows the weapon, below the crossguard, without a second wrist rotation.
        pose.pushPose();pose.translate(0,!off&&stack.getItem() instanceof ScarletWeaponItem weapon&&weapon.kind().equals("greatsword")?-.38:-.23,off?.18:0);
        grip().render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation())),e.getPackedLight(),OverlayTexture.NO_OVERLAY);pose.popPose();
        if(!off&&stack.getItem() instanceof ScarletWeaponItem weapon&&weapon.twoHanded()){
            pose.translate(0,weapon.kind().equals("greatsword")?-.18:.18,.02);pose.mulPose(Axis.XP.rotationDegrees(-20));pose.mulPose(Axis.ZP.rotationDegrees(-side*65));
            grip().render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation())),e.getPackedLight(),OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();buffers.endBatch();
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Models {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(new net.minecraft.resources.ResourceLocation("zombierool","scarlet_weapon"),1100,ScarletWeaponPose::new);net.minecraft.client.renderer.item.ItemProperties.register(me.cryo.zombierool.init.ZombieroolModItems.SCARLET_SHIELD.get(),new net.minecraft.resources.ResourceLocation("zombierool","held_kit"),(stack,level,entity,seed)->entity!=null&&entity.getMainHandItem()==stack?1:0);});}
    }
}
