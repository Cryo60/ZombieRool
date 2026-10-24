package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MapDeviceRenderer implements BlockEntityRenderer<MapDeviceSystem.Device> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("zombierool","textures/block/turret.png");
    private final ModelPart head;
    public MapDeviceRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh=new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("head",CubeListBuilder.create()
                .texOffs(0,0).addBox(-4,-3,-3,8,6,6)
                .texOffs(0,16).addBox(-3,-1,-12,2,2,8)
                .texOffs(0,16).addBox(1,-1,-12,2,2,8)
                .texOffs(16,0).addBox(4,-2,-2,3,5,5),PartPose.ZERO);
        head=LayerDefinition.create(mesh,32,32).bakeRoot();
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e) {e.registerBlockEntityRenderer(MapDeviceSystem.DEVICE.get(),MapDeviceRenderer::new);}
    @Override public boolean shouldRenderOffScreen(MapDeviceSystem.Device d){return me.cryo.zombierool.gameplay.AmbientScenery.kind(d.kind())||d.kind()==MapDeviceSystem.Kind.ELECTRIC&&d.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE);}
    @Override public int getViewDistance(){return 256;}
    @Override public boolean shouldRender(MapDeviceSystem.Device d,net.minecraft.world.phys.Vec3 camera){if(d.kind()==MapDeviceSystem.Kind.AIRCRAFT&&d.scenery.getString("variant").equals("airship"))return net.minecraft.world.phys.Vec3.atCenterOf(d.getBlockPos()).closerThan(camera,me.cryo.zombierool.gameplay.AmbientScenery.airshipLength(d.scenery)*.5+256);return BlockEntityRenderer.super.shouldRender(d,camera);}
    @Override public void render(MapDeviceSystem.Device d,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(me.cryo.zombierool.gameplay.AmbientScenery.kind(d.kind())){AmbientSceneryRenderer.render(d,partial,pose,buffers,light);return;}
        if(d.kind()==MapDeviceSystem.Kind.ELECTRIC){
            if(d.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE))ElectricTrapLightning.render(d,pose,buffers);
            return;
        }
        if(d.kind()!=MapDeviceSystem.Kind.TURRET)return;
        pose.pushPose();pose.translate(.5,.78,.5);
        pose.mulPose(Axis.YP.rotationDegrees(d.aimYaw));pose.mulPose(Axis.XP.rotationDegrees(d.aimPitch));
        head.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),light,overlay);
        pose.popPose();
    }
}
