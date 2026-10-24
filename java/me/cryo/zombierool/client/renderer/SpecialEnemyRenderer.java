package me.cryo.zombierool.client.renderer;

import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Brasero model and the vanilla skeleton geometry/skin, independent of boss meshes. */
public final class SpecialEnemyRenderer extends HumanoidMobRenderer<SpecialEnemyEntity,HumanoidModel<SpecialEnemyEntity>> {
    private final boolean skeleton;
    public SpecialEnemyRenderer(EntityRendererProvider.Context context,String kind){
        super(context,new HumanoidModel<>(kind.equals("skeleton_minion")?SkeletonModel.createBodyLayer().bakeRoot():LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),128,128).bakeRoot()),.35f);
        skeleton=kind.equals("skeleton_minion");
    }
    public ResourceLocation getTextureLocation(SpecialEnemyEntity entity){return skeleton?new ResourceLocation("minecraft","textures/entity/skeleton/skeleton.png"):new ResourceLocation("zombierool","textures/entities/napalm_minion_provisional.png");}
    protected float getFlipDegrees(SpecialEnemyEntity entity){return 0;}
    protected void scale(SpecialEnemyEntity entity,com.mojang.blaze3d.vertex.PoseStack pose,float partial){if(entity.getEmergenceTicks()>0)pose.translate(0,entity.getBbHeight()*(entity.getEmergenceTicks()-partial)/12.0,0);if(entity.deathTime>0){float shrink=Math.max(0,1-(entity.deathTime+partial)/8f);pose.scale(shrink,shrink,shrink);}if(skeleton)pose.scale(.72f,.72f,.72f);}
}
