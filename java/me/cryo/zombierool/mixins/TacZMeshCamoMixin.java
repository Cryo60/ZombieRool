package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run before the loader restores its scope stencil, lighting and gun pose. */
@Pseudo
@Mixin(targets="com.example.taczmeshloader.tacz.TaczPolyMeshGunModel",remap=false)
public class TacZMeshCamoMixin {
    @Inject(method={"renderPolyMeshNormal","renderPolyMeshWithStencil"},at=@At("RETURN"),require=2)
    private void zr$polygonCamo(PoseStack pose,MultiBufferSource.BufferSource buffers,ResourceLocation texture,
        int light,int overlay,boolean world,CallbackInfo info){
        me.cryo.zombierool.client.TacZCamoOverlay.renderMeshes(this,pose,light,overlay);
    }
}
