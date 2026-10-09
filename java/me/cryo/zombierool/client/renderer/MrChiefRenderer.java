package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.entity.MrChiefEntity;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cat;

public final class MrChiefRenderer extends CatRenderer {
    private static final ResourceLocation TEXTURE=new ResourceLocation("zombierool","textures/entity/mr_chief.png");
    public MrChiefRenderer(EntityRendererProvider.Context context){
        super(context);
        model=new EyeModel(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.CAT));
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<Cat,net.minecraft.client.model.CatModel<Cat>>(this) {
            @Override public void render(PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,Cat cat,float swing,float amount,float partial,float age,float yaw,float pitch) {
                if(cat.isInvisible())return;
                pose.pushPose();((EyeModel)getParentModel()).eyePose(pose);
                var vertex=buffers.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
                for(float x:new float[]{-2.5f,1.5f}) {
                    var matrix=pose.last().pose();float left=x/16,right=(x+1)/16,top=-1.5f/16,bottom=-.5f/16,z=-3.01f/16;
                    vertex.vertex(matrix,left,top,z).color(230,18,18,255).endVertex();
                    vertex.vertex(matrix,left,bottom,z).color(230,18,18,255).endVertex();
                    vertex.vertex(matrix,right,bottom,z).color(230,18,18,255).endVertex();
                    vertex.vertex(matrix,right,top,z).color(230,18,18,255).endVertex();
                }
                pose.popPose();
            }
        });
    }
    /** Align the two eye pixels with the vanilla head, including its look animation. */
    private static final class EyeModel extends net.minecraft.client.model.CatModel<Cat> {
        EyeModel(net.minecraft.client.model.geom.ModelPart root){super(root);}
        void eyePose(PoseStack pose){head.translateAndRotate(pose);}
    }
    @Override public ResourceLocation getTextureLocation(Cat cat){return TEXTURE;}
    @Override public void render(Cat cat,float yaw,float partial,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light) {
        int death=cat.deathTime;
        try {
            // A fiesta is rendered as a dancing cat, without vanilla's red death overlay.
            if(cat instanceof MrChiefEntity egg && egg.isPartying())cat.deathTime=0;
            super.render(cat,yaw,partial,pose,buffers,light);
        } finally {cat.deathTime=death;}
    }
    @Override protected void setupRotations(Cat cat,PoseStack stack,float age,float yaw,float partial){
        if(cat instanceof MrChiefEntity egg && egg.isPartying()){
            stack.translate(0,.15+Math.sin(age*.35)*.05,0);
            stack.mulPose(Axis.YP.rotationDegrees(180-yaw));
        }else super.setupRotations(cat,stack,age,yaw,partial);
    }
}
