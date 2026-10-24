package me.cryo.zombierool.client;

import me.cryo.zombierool.gameplay.EquipmentEntity;
import me.cryo.zombierool.core.registry.ZRThrowableRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;

/** Draw actual 3D geometry resting on its support rather than a camera-facing sprite. */
public class EquipmentRenderer extends EntityRenderer<EquipmentEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    public EquipmentRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemRenderer();shadowRadius=.12f;}
    @Override public void render(EquipmentEntity device,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-device.getYRot()));pose.mulPose(Axis.XP.rotationDegrees(device.getXRot()));
        pose.scale(.7f,.7f,.7f);pose.translate(0,.05,0);
        items.renderStatic(new ItemStack(ZRThrowableRegistry.loadoutItem(device.kind())),ItemDisplayContext.GROUND,light,OverlayTexture.NO_OVERLAY,pose,buffer,device.level(),device.getId());pose.popPose();
        if(device.kind()==me.cryo.zombierool.gameplay.EquipmentSystem.Kind.CLAYMORE&&device.armed()){
            var lines=buffer.getBuffer(RenderType.lines());
            for(int side:new int[]{-1,1}){
                var forward=net.minecraft.world.phys.Vec3.directionFromRotation(0,device.getYRot());
                var right=new net.minecraft.world.phys.Vec3(forward.z,0,-forward.x);
                var from=device.position().add(right.scale(side*.13)).add(forward.scale(.1)).add(0,.24,0);
                var direction=net.minecraft.world.phys.Vec3.directionFromRotation(0,device.getYRot()+side*12);
                var wanted=from.add(direction.scale(5));
                var hit=device.level().clip(new net.minecraft.world.level.ClipContext(from,wanted,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,device));
                var to=hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS?wanted:hit.getLocation();
                var a=from.subtract(device.position());
                var b=to.subtract(device.position());
                var normal=b.subtract(a).normalize();
                var matrix=pose.last().pose();var normalMatrix=pose.last().normal();
                lines.vertex(matrix,(float)a.x,(float)a.y,(float)a.z).color(255,24,8,210).normal(normalMatrix,(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
                lines.vertex(matrix,(float)b.x,(float)b.y,(float)b.z).color(255,24,8,210).normal(normalMatrix,(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
            }
        }
        super.render(device,yaw,partial,pose,buffer,light);
    }
    @Override public ResourceLocation getTextureLocation(EquipmentEntity device){return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;}
}
