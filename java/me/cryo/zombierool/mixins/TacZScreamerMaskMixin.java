package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import me.cryo.zombierool.client.ClientSniperHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw while TacZ's real ocular stencil is still present, including attachment/camera sway. */
@Mixin(value=BedrockAttachmentModel.class,remap=false)
public class TacZScreamerMaskMixin {
    @Inject(method="renderOcularAndDivision",at=@At("RETURN"),remap=false,require=1)
    private void zr$screamer(CallbackInfo ci){
        var mc=Minecraft.getInstance();
        if(ClientSniperHandler.screamerTimer<=0||!ClientSniperHandler.screamerInTaczScope||!mc.options.getCameraType().isFirstPerson())return;
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var shader=RenderSystem.getShader();int texture=RenderSystem.getShaderTexture(0);float[] color=java.util.Arrays.copyOf(RenderSystem.getShaderColor(),4);
        int func=GL11.glGetInteger(GL11.GL_STENCIL_FUNC),ref=GL11.glGetInteger(GL11.GL_STENCIL_REF),mask=GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        var modelView=RenderSystem.getModelViewStack();modelView.pushPose();modelView.setIdentity();RenderSystem.applyModelViewMatrix();
        try{
            float w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),size=h*2;
            float x=(w-size)/2,y=(h-size)/2;
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);
            // TacZ inverts each visible ocular's stencil (1..127 -> 254..128).
            RenderSystem.stencilFunc(GL11.GL_LESS,127,255);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0,new ResourceLocation("zombierool","textures/misc/screamer.png"));
            RenderSystem.setShaderColor(1,1,1,Math.min(1,ClientSniperHandler.screamerTimer/10f));
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            b.vertex(x,y+size,0).uv(0,1).endVertex();b.vertex(x+size,y+size,0).uv(1,1).endVertex();
            b.vertex(x+size,y,0).uv(1,0).endVertex();b.vertex(x,y,0).uv(0,0).endVertex();BufferUploader.drawWithShader(b.end());
        }finally{
            RenderSystem.stencilFunc(func,ref,mask);RenderSystem.depthMask(depthMask);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,texture);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setProjectionMatrix(projection,sorting);modelView.popPose();RenderSystem.applyModelViewMatrix();
        }
    }
}
