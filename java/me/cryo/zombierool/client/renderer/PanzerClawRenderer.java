package me.cryo.zombierool.client.renderer;
import me.cryo.zombierool.entity.PanzerClawEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
public final class PanzerClawRenderer extends EntityRenderer<PanzerClawEntity>{
    private final net.minecraft.client.model.geom.ModelPart claw;
    public PanzerClawRenderer(EntityRendererProvider.Context c){super(c);var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("claw",CubeListBuilder.create().texOffs(192,128).addBox(-2,-2,-2,4,4,4).texOffs(0,192).addBox(-3,-2,-4,1,5,3).addBox(2,-2,-4,1,5,3),PartPose.ZERO);claw=LayerDefinition.create(mesh,256,256).bakeRoot();}
    @Override public ResourceLocation getTextureLocation(PanzerClawEntity e){return new ResourceLocation("zombierool","textures/entities/panzer_minecraft_3.png");}
    @Override public void render(PanzerClawEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.scale(.0625f,.0625f,.0625f);claw.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e))),light,OverlayTexture.NO_OVERLAY);pose.popPose();
        var owner=e.owner();if(owner==null)return;var delta=(owner instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss?boss.grappleOrigin():owner.getEyePosition(partial)).subtract(e.getPosition(partial));var matrix=pose.last();var line=buffers.getBuffer(RenderType.lines());var n=delta.normalize();
        line.vertex(matrix.pose(),0,0,0).color(95,95,85,255).normal(matrix.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
        line.vertex(matrix.pose(),(float)delta.x,(float)delta.y,(float)delta.z).color(95,95,85,255).normal(matrix.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
