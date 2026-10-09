package me.cryo.zombierool.mixins;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderDispatcher.class)
public abstract class FiestaEntityMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void zombierool_begin(Entity entity,double x,double y,double z,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci){
        if(!(entity instanceof LivingEntity)){pose.pushPose();if(me.cryo.zombierool.client.MatchAtmosphere.active())pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw-me.cryo.zombierool.client.MatchAtmosphere.yaw(entity,yaw,partial)));}
    }
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",shift=At.Shift.AFTER))
    private void zombierool_end(Entity entity,double x,double y,double z,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light,CallbackInfo ci){if(!(entity instanceof LivingEntity))pose.popPose();}
}
