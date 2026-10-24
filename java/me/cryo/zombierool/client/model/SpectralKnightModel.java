package me.cryo.zombierool.client.model;
import me.cryo.zombierool.entity.WhiteKnightEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
public final class SpectralKnightModel extends HumanoidModel<WhiteKnightEntity> {
    private final ModelPart[] cloak=new ModelPart[3];
    public SpectralKnightModel(){super(layer().bakeRoot());for(int i=0;i<3;i++)cloak[i]=body.getChild("cloak"+i);}
    public static LayerDefinition layer(){
        MeshDefinition mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);var root=mesh.getRoot();
        var head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4.5f,-8.5f,-4.5f,9,9,9),PartPose.ZERO);
        root.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        head.addOrReplaceChild("visor",CubeListBuilder.create().texOffs(96,96).addBox(-3.5f,-5.6f,-4.62f,7,1,1),PartPose.ZERO);
        head.addOrReplaceChild("brow",CubeListBuilder.create().texOffs(64,0).addBox(-4.7f,-7,-5,9.4f,1.4f,1.2f).addBox(-.7f,-8.6f,-5,1.4f,8,1.1f),PartPose.ZERO);
        head.addOrReplaceChild("crest",CubeListBuilder.create().texOffs(64,0).addBox(-1,-11,-3,2,3,7),PartPose.ZERO);
        var body=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,24).addBox(-4.5f,0,-2.7f,9,11,5.4f).texOffs(64,0).addBox(-5,8,-3,10,3,6),PartPose.ZERO);
        body.addOrReplaceChild("heart",CubeListBuilder.create().texOffs(96,96).addBox(-1,3,-2.85f,2,3,.3f),PartPose.ZERO);
        for(int i=0;i<3;i++)body.addOrReplaceChild("cloak"+i,CubeListBuilder.create().texOffs(0,64).addBox(-2,0,0,4,17-i*2,.4f),PartPose.offset((i-1)*4,1,3.25f));
        for(int side=-1;side<=1;side+=2){String name=side<0?"right":"left";
            var arm=root.addOrReplaceChild(name+"_arm",CubeListBuilder.create().texOffs(0,40).addBox(side<0?-3:-1,-2,-2,4,12,4),PartPose.offset(side*5,2,0));
            arm.addOrReplaceChild("pauldron",CubeListBuilder.create().texOffs(64,0).addBox(side<0?-4:-1,-3.2f,-3,5,4,6),PartPose.ZERO);
            arm.addOrReplaceChild("gauntlet",CubeListBuilder.create().texOffs(64,15).addBox(side<0?-3.4f:-1.4f,5,-2.4f,4.8f,5,4.8f),PartPose.ZERO);
            root.addOrReplaceChild(name+"_leg",CubeListBuilder.create().texOffs(0,40).addBox(-2,-.2f,-2,4,10,4).texOffs(64,15).addBox(-2.25f,7,-2.7f,4.5f,5,5.3f),PartPose.offset(side*1.9f,12,0));
        }
        return LayerDefinition.create(mesh,128,128);
    }
    public void setupAnim(WhiteKnightEntity entity,float walk,float speed,float age,float yaw,float pitch){
        super.setupAnim(entity,walk,speed,age,yaw,pitch);
        float swing=entity.spectralSwing(age-entity.tickCount);
        if(swing>0){
            float t=1-swing/18f,blend=Mth.clamp(Math.min(t,1-t)*5,0,1);blend=blend*blend*(3-2*blend);
            rightArm.xRot=Mth.lerp(blend,rightArm.xRot,-1.6f+Mth.sin(t*Mth.PI)*1.8f);
            rightArm.yRot=Mth.lerp(blend,rightArm.yRot,-.7f+t*1.4f);
            body.yRot=Mth.lerp(blend,body.yRot,Mth.sin(t*Mth.PI)*-.22f);
        }
        for(int i=0;i<3;i++){cloak[i].xRot=.14f+speed*.65f+Mth.sin(age*.08f+i)*.055f;cloak[i].zRot=Mth.sin(age*.06f+i)*.035f;}
        head.zRot=Mth.sin(age*.035f)*.018f;
    }
}
