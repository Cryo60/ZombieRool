package me.cryo.zombierool.client.renderer;

import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Native articulated cube silhouettes, sharing the normal Minecraft skin layout. */
public final class EncounterRenderer extends MobRenderer<SpecialEnemyEntity,EncounterRenderer.Model> {
    private static final ModelPart CHAIN=hookPart(false),HOOK=hookPart(true);
    private static ModelPart hookPart(boolean hook){var mesh=new MeshDefinition();var cubes=CubeListBuilder.create().texOffs(64,32);
        if(hook){cubes.addBox(-.7f,-1,-.7f,1.4f,6,1.4f).addBox(-.7f,4,-.7f,4,1.4f,1.4f).addBox(2,1,-.7f,1.4f,4,1.4f);}
        else{cubes.addBox(-1.2f,-1.5f,-.4f,.6f,3,.8f).addBox(.6f,-1.5f,-.4f,.6f,3,.8f).addBox(-.6f,-1.5f,-.4f,1.2f,.6f,.8f).addBox(-.6f,.9f,-.4f,1.2f,.6f,.8f);}
        mesh.getRoot().addOrReplaceChild("metal",cubes,PartPose.ZERO);return LayerDefinition.create(mesh,256,256).bakeRoot();}
    private final String kind;
    public EncounterRenderer(EntityRendererProvider.Context context,String kind){super(context,new Model(layer(kind).bakeRoot(),kind),.5f);this.kind=kind;}
    @Override protected boolean shouldShowName(SpecialEnemyEntity e){return !kind.equals("abomination")&&super.shouldShowName(e);}
    @Override public ResourceLocation getTextureLocation(SpecialEnemyEntity e){String custom=e.bossProfile().getString("texture");return custom.isBlank()?new ResourceLocation("zombierool","textures/entities/encounters/"+kind+".png"):new ResourceLocation(custom);}
    @Override protected void scale(SpecialEnemyEntity e,com.mojang.blaze3d.vertex.PoseStack pose,float partial){float scale=switch(kind){case "george_romero"->1.35f;case "astronaut"->1.25f;case "abomination"->1.65f;case "geist"->.78f;default->1;};pose.scale(scale,scale,scale);}
    @Override public void render(SpecialEnemyEntity e,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        super.render(e,yaw,partial,pose,buffers,light);renderHook(e,pose,buffers,light);
    }
    public static void renderHook(SpecialEnemyEntity e,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){renderElectric(e,pose,buffers);renderPlague(e,pose,buffers,light);var end=e.hookOffset();if(end.lengthSqr()<.001)return;
        var start=new net.minecraft.world.phys.Vec3(.65,1.55,0).yRot(-e.yBodyRot*(float)Math.PI/180);var direction=end.subtract(start).normalize();
        var rotation=new org.joml.Quaternionf().rotationTo(0,1,0,(float)direction.x,(float)direction.y,(float)direction.z);
        var out=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(new ResourceLocation("zombierool","textures/entities/blocky/abomination.png")));
        int count=Math.min(110,Math.max(1,(int)(start.distanceTo(end)/.2)));
        for(int i=0;i<count;i++){double t=i/(double)count;var point=start.lerp(end,t).add(0,-Math.sin(t*Math.PI)*.12,0);pose.pushPose();pose.translate(point.x,point.y,point.z);pose.mulPose(rotation);if(i%2==0)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));pose.scale(1.75f,1,1.75f);CHAIN.render(pose,out,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);pose.popPose();}
        pose.pushPose();pose.translate(end.x,end.y,end.z);pose.mulPose(rotation);pose.scale(1.4f,1.4f,1.4f);HOOK.render(pose,out,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);pose.popPose();
    }
    private static void renderPlague(SpecialEnemyEntity e,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        var direction=e.plagueDirection();if(!e.isAlive()||!e.bossAnimation().equals("vomit")||direction.lengthSqr()<.5)return;
        float age=e.bossAnimationAge(net.minecraft.client.Minecraft.getInstance().getFrameTime());
        if(age<20||age>=70)return;
        var origin=e.getEyePosition().add(0,-.5,0);var end=origin.add(direction.scale(6));
        var hit=e.level().clip(new net.minecraft.world.level.ClipContext(origin,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,e));
        double reach=hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS?6:origin.distanceTo(hit.getLocation());
        var out=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(new ResourceLocation("zombierool","textures/particle/toxic_smoke.png")));
        // A bounded 24-quad plume: dense feedback without spawning hundreds of
        // ticked particles. Normal world depth keeps it behind walls and hands.
        var camera=net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        float fade=Math.min(1,(70-age)/8);double maximum=Math.min(reach,(age-20)*.6);
        for(int i=23;i>=0;i--){double phase=(i/24.+age*.045)%1,along=phase*6;if(along>maximum)continue;
            double spread=.08+along*.10;var point=origin.subtract(e.position()).add(direction.scale(along)).add(Math.sin(i*2.4+age*.09)*spread,Math.cos(i*1.7+age*.06)*spread,Math.cos(i*2.1)*spread);
            float size=(float)(.22+along*.12),alpha=(int)(190*fade*(1-phase*.45));
            pose.pushPose();pose.translate(point.x,point.y,point.z);pose.mulPose(camera);pose.mulPose(com.mojang.math.Axis.ZP.rotation((float)(i*.8+age*.025)));
            var matrix=pose.last().pose();var normal=pose.last().normal();int packed=Math.max(light,0x00A000A0);
            for(int corner=0;corner<4;corner++){float x=corner==0||corner==3?-size:size,y=corner<2?-size:size;
                out.vertex(matrix,x,y,0).color(190,235,120,(int)alpha).uv(corner==0||corner==3?0:1,corner<2?1:0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(packed).normal(normal,0,0,1).endVertex();}
            pose.popPose();
        }
    }
    private static void renderElectric(SpecialEnemyEntity e,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers){
        if(!me.cryo.zombierool.gameplay.EncounterAbilities.kind(e).equals("george_romero")||!e.bossProfile().getBoolean("enraged")||!e.isAlive())return;
        // Short, deterministic electrical arcs surround the enraged boss and animate
        // at 10 Hz without allocating a particle cloud around every zombie.
        var out=buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());var matrix=pose.last().pose();float age=e.tickCount+net.minecraft.client.Minecraft.getInstance().getFrameTime();
        var random=new java.util.Random(e.getId()*31L+(long)(age/2));
        for(int arc=0;arc<3;arc++){double angle=arc*Math.PI*2/3+age*.04;var previous=new net.minecraft.world.phys.Vec3(Math.cos(angle)*.32,.35,Math.sin(angle)*.32);
            for(int j=1;j<=7;j++){var next=new net.minecraft.world.phys.Vec3(Math.cos(angle)*(.32+random.nextFloat()*.16),.35+j*.19,Math.sin(angle)*(.32+random.nextFloat()*.16));float w=.012f;
                for(int axis=0;axis<2;axis++){float dx=axis==0?w:0,dz=axis==1?w:0;
                    out.vertex(matrix,(float)previous.x-dx,(float)previous.y,(float)previous.z-dz).color(120,205,255,210).endVertex();out.vertex(matrix,(float)previous.x+dx,(float)previous.y,(float)previous.z+dz).color(205,240,255,255).endVertex();out.vertex(matrix,(float)next.x+dx,(float)next.y,(float)next.z+dz).color(205,240,255,255).endVertex();out.vertex(matrix,(float)next.x-dx,(float)next.y,(float)next.z-dz).color(120,205,255,210).endVertex();}
                previous=next;
            }
        }
        String action=e.bossAnimation();float hit=action.equals("slam")?25:action.equals("rage")?71:-100;
        float since=e.bossAnimationAge(net.minecraft.client.Minecraft.getInstance().getFrameTime())-hit;
        if(since>=0&&since<12){double radius=.5+since*.67;int alpha=(int)(220*(1-since/12));for(int i=0;i<32;i++){double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
            out.vertex(matrix,(float)(Math.cos(a)*(radius-.04)),.08f,(float)(Math.sin(a)*(radius-.04))).color(140,210,255,alpha).endVertex();out.vertex(matrix,(float)(Math.cos(a)*(radius+.04)),.08f,(float)(Math.sin(a)*(radius+.04))).color(220,245,255,alpha).endVertex();out.vertex(matrix,(float)(Math.cos(b)*(radius+.04)),.08f,(float)(Math.sin(b)*(radius+.04))).color(220,245,255,alpha).endVertex();out.vertex(matrix,(float)(Math.cos(b)*(radius-.04)),.08f,(float)(Math.sin(b)*(radius-.04))).color(140,210,255,alpha).endVertex();
        }}
    }
    private static void box(PartDefinition parent,String name,int u,int v,float x,float y,float z,float w,float h,float d){parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.ZERO);}
    private static LayerDefinition layer(String kind){
        var mesh=HumanoidModel.createMesh(CubeDeformation.NONE,0);var root=mesh.getRoot();
        root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(40,16).mirror().addBox(-1,-2,-2,4,12,4),PartPose.offset(5,2,0));
        root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,16).mirror().addBox(-2,0,-2,4,12,4),PartPose.offset(1.9f,12,0));
        root.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        if(kind.equals("astronaut")){
            box(root.getChild("head"),"helmet_shell",0,0,-5,-9,-5,10,10,10);
            box(root.getChild("body"),"life_support",16,16,-4,-1,2,8,11,4);
            box(root.getChild("body"),"chest_controls",16,16,-3,2,-3,6,4,1);
        }
        if(kind.equals("george_romero")){
            var arm=root.getChild("right_arm");box(arm,"projector_handle",0,16,-2,9,-2,2,6,2);box(arm,"projector_body",16,16,-4,13,-5,7,6,7);box(arm,"projector_lens",0,0,-2,14,-8,3,3,3);box(arm,"projector_top_reel",0,16,-5,10,-4,9,2,5);
        }
        if(kind.equals("abomination")){
            box(root.getChild("body"),"belly",16,16,-6,1,-4,12,11,9);
            box(root.getChild("right_arm"),"cleaver",0,16,-4,9,-4,6,7,1);
            box(root.getChild("left_arm"),"hook_shank",0,16,0,9,-2,1,6,1);box(root.getChild("left_arm"),"hook_tip",0,16,-2,14,-2,3,1,1);
        }
        if(kind.equals("geist")){
            for(String side:new String[]{"left_arm","right_arm"})for(int i=0;i<3;i++)box(root.getChild(side),"claw_"+i,40,16,-1+i,9,-4,1,4,1);
            box(root.getChild("head"),"hood",0,0,-4.4f,-8.4f,-4.4f,8.8f,8.8f,8.8f);
        }
        return LayerDefinition.create(mesh,64,64);
    }
    public static final class Model extends HumanoidModel<SpecialEnemyEntity>{
        private final String kind;
        Model(ModelPart root,String kind){super(root);this.kind=kind;}
        @Override public void setupAnim(SpecialEnemyEntity e,float walk,float amount,float age,float yaw,float pitch){super.setupAnim(e,walk,amount,age,yaw,pitch);body.xRot=0;head.z=0;body.z=0;leftArm.z=rightArm.z=0;
            if(kind.equals("geist")){body.xRot=.65f;head.z=-4;body.z=-1;leftArm.z=rightArm.z=-4;leftArm.xRot=rightArm.xRot=-.6f;if(e.bossAnimation().equals("leap"))leftArm.xRot=rightArm.xRot=-1.8f;}
            if(kind.equals("abomination")){body.xRot=.12f;head.z=-1;}
            if(kind.equals("astronaut")){leftArm.xRot=rightArm.xRot=-.15f;if(e.bossAnimation().equals("headbutt")){head.xRot+=Mth.sin(e.bossAnimationAge(0)/24f*Mth.PI)*.9f;leftArm.xRot=rightArm.xRot=-1.1f;}}
            if(kind.equals("george_romero")){rightArm.xRot=-.2f;if(e.bossAnimation().equals("slam"))rightArm.xRot=-Mth.sin(e.bossAnimationAge(0)/30f*Mth.PI)*2.5f;}
        }
    }
}
