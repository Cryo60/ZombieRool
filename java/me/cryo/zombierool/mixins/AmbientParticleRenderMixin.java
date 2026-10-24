package me.cryo.zombierool.mixins;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SingleQuadParticle.class)
public abstract class AmbientParticleRenderMixin {
    @Inject(method={"render","m_5744_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void zombierool$deferAmbient(VertexConsumer out,Camera camera,float partial,CallbackInfo ci){if(me.cryo.zombierool.client.AmbientParticles.defer((Particle)(Object)this))ci.cancel();}
}
