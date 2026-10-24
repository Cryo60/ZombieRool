package me.cryo.zombierool.client.renderer;
import me.cryo.zombierool.entity.WhiteKnightEntity;
import me.cryo.zombierool.client.model.SpectralKnightModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
public class WhiteKnightRenderer extends HumanoidMobRenderer<WhiteKnightEntity,SpectralKnightModel> {
    private static final ResourceLocation SKIN=new ResourceLocation("zombierool:textures/entities/spectral_knight.png");
    public WhiteKnightRenderer(EntityRendererProvider.Context context){
        super(context,new SpectralKnightModel(),.25f);
        addLayer(new EyesLayer<WhiteKnightEntity,SpectralKnightModel>(this){
            public RenderType renderType(){return RenderType.eyes(new ResourceLocation("zombierool:textures/entities/spectral_knight_glow.png"));}
        });
    }
    public ResourceLocation getTextureLocation(WhiteKnightEntity entity){return SKIN;}
    protected RenderType getRenderType(WhiteKnightEntity entity,boolean visible,boolean translucent,boolean glowing){return RenderType.entityTranslucent(SKIN);}
    protected void scale(WhiteKnightEntity entity,com.mojang.blaze3d.vertex.PoseStack pose,float partial){pose.translate(0,.045+Math.sin((entity.tickCount+partial)*.08)*.025,0);}
}
