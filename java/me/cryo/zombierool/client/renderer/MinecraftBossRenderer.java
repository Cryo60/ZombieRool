package me.cryo.zombierool.client.renderer;

import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Individually constructed silhouettes: articulated exosuit and heavy prison guard. */
public final class MinecraftBossRenderer extends MobRenderer<SpecialEnemyEntity,MinecraftBossRenderer.BossModel> {
    @Override protected float getFlipDegrees(SpecialEnemyEntity entity){return 0;}
    private final String kind;
    private final int variant;
    public MinecraftBossRenderer(EntityRendererProvider.Context context,String kind){this(context,kind,2);}
    public MinecraftBossRenderer(EntityRendererProvider.Context context,String kind,int variant){super(context,new BossModel(layer(kind.equals("panzer"),variant).bakeRoot(),kind.equals("panzer"),variant),.55f);this.kind=kind;this.variant=variant;}
    @Override public ResourceLocation getTextureLocation(SpecialEnemyEntity e){var custom=ResourceLocation.tryParse(e.bossProfile().getString("texture"));if(custom!=null&&!e.bossProfile().getString("texture").isBlank())return custom;return new ResourceLocation("zombierool","textures/entities/"+kind+"_minecraft"+(variant==0?"":"_"+(variant+1))+".png");}
    @Override protected void scale(SpecialEnemyEntity e,com.mojang.blaze3d.vertex.PoseStack pose,float partial){float s=e.isPanzer()?1.45f:1.35f;pose.scale(s,e.isBossCrouched()?s*1.95f/(e.isPanzer()?2.8f:2.65f):s,s);if(e.getEmergenceTicks()>0)pose.translate(0,1.95*(e.getEmergenceTicks()-partial)/12.0,0);}
    @Override public void render(SpecialEnemyEntity e,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffer,int light){
        super.render(e,yaw,partial,pose,buffer,light);
        if(!e.isPanzer()||!e.isAlive()||!e.bossAnimation().equals("flame"))return;
        var origin=e.flameOrigin();var direction=e.flameDirection();
        var end=e.level().clip(new net.minecraft.world.level.ClipContext(origin,origin.add(direction.scale(8)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,e)).getLocation();
        var consumer=buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
        var sprite=net.minecraft.client.resources.model.ModelBakery.FIRE_1.sprite();
        float time=e.tickCount+partial;double length=origin.distanceTo(end);
        for(double d=.1;d<length;d+=.24){
            var point=origin.add(direction.scale(d)).subtract(e.getPosition(partial));
            float radius=(float)(.12+d*.065),pulse=.85f+.15f*Mth.sin(time*.8f-(float)d*2);
            pose.pushPose();pose.translate(point.x,point.y,point.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)(Math.atan2(direction.x,direction.z)*180/Math.PI)));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float)(-Math.asin(direction.y)*180/Math.PI)));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(time*9+(float)d*70));
            var matrix=pose.last();float size=radius*pulse;
            consumer.vertex(matrix.pose(),-size,-size,0).color(255,225,155,255).uv(sprite.getU0(),sprite.getV1()).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(15728880).normal(matrix.normal(),0,0,1).endVertex();
            consumer.vertex(matrix.pose(),size,-size,0).color(255,225,155,255).uv(sprite.getU1(),sprite.getV1()).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(15728880).normal(matrix.normal(),0,0,1).endVertex();
            consumer.vertex(matrix.pose(),size,size,0).color(255,255,220,255).uv(sprite.getU1(),sprite.getV0()).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(15728880).normal(matrix.normal(),0,0,1).endVertex();
            consumer.vertex(matrix.pose(),-size,size,0).color(255,255,220,255).uv(sprite.getU0(),sprite.getV0()).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(15728880).normal(matrix.normal(),0,0,1).endVertex();
            pose.popPose();
        }
    }
    private static PartDefinition part(PartDefinition parent,String name,float px,float py,float pz,int u,int v,float x,float y,float z,float w,float h,float d){int tile=material(name);u=tile%4*64;v=tile/4*32;return parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.offset(px,py,pz));}
    private static int material(String n){
        if(n.startsWith("eye"))return 27;if(n.startsWith("lamp"))return 28;if(n.startsWith("glass"))return 29;if(n.startsWith("tie"))return 30;if(n.startsWith("wire")||n.startsWith("spike"))return 31;
        if(n.equals("torso"))return 0;if(n.equals("head")||n.equals("nose"))return 1;if(n.startsWith("brow")||n.equals("neck_guard"))return 12;if(n.endsWith("_arm"))return 2;if(n.equals("forearm"))return 3;
        if(n.endsWith("_leg"))return 4;if(n.equals("shin"))return 5;if(n.equals("boot"))return 6;if(n.equals("knee"))return 7;
        if(n.equals("pelvis"))return 8;if(n.equals("pauldron"))return 9;if(n.startsWith("helmet"))return 10;if(n.equals("visor"))return 11;
        if(n.equals("respirator")||n.startsWith("filter"))return 12;if(n.startsWith("breastplate"))return 13;if(n.startsWith("collar"))return 14;
        if(n.equals("back_frame"))return 15;if(n.startsWith("tank"))return 16;if(n.startsWith("hose")||n.equals("exhaust"))return 17;
        if(n.equals("weapon_body")||n.equals("burner")||n.equals("igniter"))return 18;if(n.startsWith("claw")||n.startsWith("finger"))return 19;
        if(n.startsWith("vest"))return 20;if(n.equals("badge"))return 21;if(n.startsWith("pocket"))return 22;if(n.equals("hand"))return 23;
        if(n.equals("shackle")||n.startsWith("chain"))return 24;if(n.equals("baton"))return 25;if(n.equals("jaw"))return 26;return 0;
    }
    private static void box(PartDefinition p,String name,int u,int v,float x,float y,float z,float w,float h,float d){part(p,name,0,0,0,u,v,x,y,z,w,h,d);}
    public static LayerDefinition layer(boolean panzer){return layer(panzer,0);}
    public static LayerDefinition layer(boolean panzer,int variant){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var torso=part(root,"torso",0,3,1,0,0,-6,-1,-3.5f,12,12,7);
        var head=part(torso,"head",0,-3,-.5f,64,0,-3,-7,-3,6,7,6);
        var left=part(torso,"left_arm",7,0,0,0,0,-2,0,-2.5f,5,6,5);
        var right=part(torso,"right_arm",-7,0,0,0,0,-3,0,-2.5f,5,6,5);
        var lf=part(left,"forearm",0,6,0,panzer?0:64,panzer?64:24,-2,0,-2.5f,5,6,5);
        var rf=part(right,"forearm",0,6,0,panzer?0:64,panzer?64:24,-3,0,-2.5f,5,6,5);
        var ll=part(root,"left_leg",3.3f,14,1,0,0,-2.5f,0,-2.5f,5,5,5);
        var rl=part(root,"right_leg",-3.3f,14,1,0,0,-2.5f,0,-2.5f,5,5,5);
        for(var leg:new PartDefinition[]{ll,rl}){var shin=part(leg,"shin",0,5,0,0,64,-2.5f,0,-2.5f,5,5,5);box(shin,"boot",0,96,-3,3,-4,6,2,7);box(leg,"knee",0,64,-2.8f,3,-3.5f,5.6f,3,2);}
        box(torso,"pelvis",0,96,-5.5f,10,-3,11,2,6);
        box(left,"pauldron",0,64,-3,-1,-3.5f,7,4,7);box(right,"pauldron",0,64,-4,-1,-3.5f,7,4,7);
        if(panzer){
            // Stepped diving shell surrounding an inset corpse face, rather than a Steve helmet.
            float shell=variant==1?4.3f:variant==2?5.4f:4.9f;
            box(head,"helmet_top",0,0,-shell,-8.2f,-4.3f,shell*2,1.8f,8.6f);
            box(head,"helmet_crown",0,0,-shell+1,-9,-3.3f,shell*2-2,1,6.6f);
            box(head,"helmet_back",0,0,-shell,-6.4f,2.8f,shell*2,7,1.6f);
            box(head,"helmet_sides_l",0,0,3.1f,-6.4f,-4.3f,shell-3.1f,7,7.2f);box(head,"helmet_sides_r",0,0,-shell,-6.4f,-4.3f,shell-3.1f,7,7.2f);
            box(head,"helmet_window_top",0,0,-3.2f,-6.5f,-4.7f,6.4f,1,1);
            box(head,"helmet_window_bottom",0,0,-3.2f,-.5f,-4.7f,6.4f,1,1);
            box(head,"glass_left",0,0,-3.2f,-5.5f,-4.8f,.55f,5,1);box(head,"glass_right",0,0,2.65f,-5.5f,-4.8f,.55f,5,1);
            box(head,"eye_l",0,0,-2.3f,-4.5f,-3.2f,1.25f,.6f,.25f);box(head,"eye_r",0,0,1.05f,-4.5f,-3.2f,1.25f,.6f,.25f);
            box(head,"helmet_lamp_mount",0,0,-1.9f,-8.8f,-5,3.8f,3.8f,1.6f);
            box(head,"lamp_top",0,0,-1.3f,-8.2f,-5.3f,2.6f,2.6f,.4f);
            box(head,"respirator",0,0,-2,-1.5f,-5,4,2.5f,2);
            box(head,"filter_l",0,0,-5.6f,-2.8f,-4.8f,2.4f,3.5f,3);box(head,"filter_r",0,0,3.2f,-2.8f,-4.8f,2.4f,3.5f,3);
            var breast=part(torso,"breastplate",0,0,-4,0,64,-6.5f,0,-1,13,5,2);
            breast.addOrReplaceChild("lower_plate",CubeListBuilder.create().texOffs(64,96).addBox(-5.5f,0,-1,11,5,2),PartPose.offsetAndRotation(0,5,0,.13f,0,0));
            box(torso,"breastplate_reactor_frame",0,0,-2.4f,3.6f,-5.9f,4.8f,4.8f,1.2f);
            box(torso,"lamp_reactor",0,0,-1.6f,4.4f,-6.2f,3.2f,3.2f,.7f);
            box(head,"neck_guard",0,96,-3,-.6f,-3.6f,6,1,7);box(torso,"collar",0,64,-5,-2,-4,10,2,7);box(torso,"back_frame",0,96,-5,-1,3.5f,10,12,2);
            if(variant==1){box(torso,"tank_single",0,0,-3,-1,5,6,10,4);box(torso,"tank_strap_single",0,0,-3.2f,3,4.8f,6.4f,1.2f,4.4f);}
            else for(int side:new int[]{-1,1}){box(torso,"tank_"+side,32,96,side<0?-4.5f:.5f,-2,5,4,12,4);box(torso,"tank_cap_"+side,0,64,side<0?-4:1,-3,5.5f,3,1,3);box(torso,"tank_strap_"+side,0,64,side<0?-4.7f:.3f,2,4.8f,4.4f,1,4.4f);}
            box(torso,"exhaust",0,96,-1,-3,5,2,6,2);part(torso,"hose_1",-5,0,6,0,96,-1,-1,-1,2,5,2);
            var hose=part(torso,"hose_2",-5,4,6,0,96,-1,0,-1,2,5,2);hose.addOrReplaceChild("bend",CubeListBuilder.create().texOffs(64,128).addBox(-1,0,-1,2,6,2),PartPose.offsetAndRotation(0,4,0,-1.1f,0,.45f));
            box(rf,"weapon_body",0,64,-4,-1,-3.5f,7,6,7);box(rf,"burner",0,96,-2.5f,1,-9,4,3,7);box(rf,"igniter",32,96,-1.5f,4,-8,2,1,5);
            box(lf,"claw_hub",0,64,-2.5f,3,-3.5f,6,3,7);part(lf,"finger_l",-2.5f,5,-3,0,96,-.5f,0,-.5f,1,4,2);part(lf,"finger_r",3,5,-3,0,96,-.5f,0,-.5f,1,4,2);box(lf,"claw_back",0,96,-1,6,2,3,2,1);
            for(var arm:new PartDefinition[]{left,right})for(int i=0;i<(variant==1?2:3);i++)part(arm,"spike_"+i,arm==left?0:-1,-1,-2+i*2,0,0,-.6f,-2,-.6f,1.2f,2,1.2f);
            if(variant==2){box(torso,"breastplate_abdomen",0,0,-6,6,-5.4f,12,4,2);box(lf,"claw_shield",0,0,-3,-1,-4,7,5,2);box(torso,"collar_ring",0,0,-6,-2.5f,-5,12,3,10);}
            if(variant==1){box(torso,"collar_front",0,0,-6,-2,-5.2f,12,2,2);}
        }else{
            float crown=variant==1?3.7f:4.2f;
            box(head,"helmet_crown",0,64,-crown,-7.7f,-3.9f,crown*2,3,7.8f);box(head,"helmet_top",0,0,-3,-8.6f,-3,6,1,6);box(head,"helmet_brim",0,64,-4.7f,-4.7f,-4.4f,9.4f,1,8.8f);box(head,"jaw",64,24,-2.5f,-2,-3.7f,5,2,1);
            box(head,"brow_l",0,96,-3,-4,-3.25f,2.7f,.4f,.7f);box(head,"brow_r",0,96,.3f,-4,-3.25f,2.7f,.4f,.7f);box(head,"nose",64,0,-.5f,-3.2f,-3.7f,1,1.7f,1);
            box(head,"eye_l",0,0,-2.3f,-3.6f,-3.35f,1.35f,.65f,.3f);box(head,"eye_r",0,0,.95f,-3.6f,-3.35f,1.35f,.65f,.3f);
            box(torso,"vest_front_left",0,64,1,0,-4.7f,5.8f,9.5f,2);box(torso,"vest_front_right",0,64,-6.8f,0,-4.7f,5.8f,9.5f,2);box(torso,"vest_back",0,64,-6.8f,0,3.2f,13.6f,10,2);box(torso,"badge",48,96,2,1,-5.2f,2.5f,2.2f,.5f);
            box(torso,"tie_knot",0,0,-.65f,0,-4.2f,1.3f,1.2f,.6f);box(torso,"tie_tail",0,0,-.5f,1.2f,-4.2f,1,6,.6f);
            box(torso,"shackle_lamp",0,0,-2,4.5f,-6,4,4,1.3f);box(torso,"lamp_chest",0,0,-1.4f,5.1f,-6.25f,2.8f,2.8f,.4f);
            box(torso,"pocket_l",0,0,1,6,-5.5f,4,3,1);box(torso,"pocket_r",0,0,-5,6,-5.5f,4,3,1);
            box(lf,"hand",64,24,-2.4f,5,-2.8f,5.8f,3,5.6f);box(rf,"hand",64,24,-3.4f,5,-2.8f,5.8f,3,5.6f);
            box(lf,"shackle",0,96,-2.5f,3,-3,6,1.8f,6);box(rf,"shackle",0,96,-3.5f,3,-3,6,1.8f,6);
            for(int i=0;i<3;i++)box(lf,"chain_"+i,0,96,3,4+i*1.5f,-1,1,2,1);
            var baton=part(rf,"baton",-1,6,-3,0,96,-1,-1,-7,2,2,14);baton.addOrReplaceChild("end",CubeListBuilder.create().texOffs(64,192).addBox(-1.3f,-1.3f,6,2.6f,2.6f,1),PartPose.ZERO);
            for(int side:new int[]{-1,1})for(int i=0;i<4;i++)box(torso,"chain_neck_"+side+"_"+i,0,0,side*(1.8f+i*.8f)-.5f,-1+i*.65f,-5.1f,1,1.2f,.7f);
            for(var arm:new PartDefinition[]{left,right}){
                for(int i=0;i<(variant==1?2:3);i++)part(arm,"spike_"+i,arm==left?0:-1,-1,-2+i*2,0,0,-.6f,-2.2f,-.6f,1.2f,2.2f,1.2f);
                var fore=arm==left?lf:rf;
                for(int i=0;i<3;i++){box(fore,"wire_front_"+i,0,0,arm==left?-2.5f:-3.5f,i*1.4f,-3.1f,6,.45f,.5f);box(fore,"wire_side_"+i,0,0,arm==left?3:-3.5f,i*1.4f,-3, .5f,.45f,6);}
            }
            for(int i=0;i<3;i++)box(torso,"wire_chest_"+i,0,0,-6.9f,3.2f+i*1.4f,-5,13.8f,.4f,.5f);
            if(variant==2){box(torso,"vest_abdomen",0,0,-6.5f,8,-4.8f,13,3,2);box(left,"pauldron_outer",0,0,-3.8f,-1.5f,-4,8,4.5f,8);box(right,"pauldron_outer",0,0,-4.2f,-1.5f,-4,8,4.5f,8);}
            if(variant==1){box(torso,"vest_belt",0,0,-6,9,-4.4f,12,1.4f,8.8f);box(torso,"vest_coat_left",0,0,1.3f,10,-3.9f,4.5f,4.5f,1.2f);box(torso,"vest_coat_right",0,0,-5.8f,10,-3.9f,4.5f,4.5f,1.2f);}
        }
        return LayerDefinition.create(mesh,256,256);
    }
    public static final class BossModel extends EntityModel<SpecialEnemyEntity>{
        private final ModelPart root,torso,head,left,right,ll,rl,lf,rf,ls,rs;
        private final boolean panzer;
        private final int variant;
        public BossModel(ModelPart root,boolean panzer){this(root,panzer,0);}
        public BossModel(ModelPart root,boolean panzer,int variant){this.root=root;this.panzer=panzer;this.variant=variant;torso=root.getChild("torso");head=torso.getChild("head");left=torso.getChild("left_arm");right=torso.getChild("right_arm");ll=root.getChild("left_leg");rl=root.getChild("right_leg");lf=left.getChild("forearm");rf=right.getChild("forearm");ls=ll.getChild("shin");rs=rl.getChild("shin");}
        @Override public void setupAnim(SpecialEnemyEntity e,float walk,float amount,float age,float yaw,float pitch){
            root.getAllParts().forEach(p->{p.resetPose();p.visible=true;});torso.xRot=panzer?.08f:.17f;torso.y+=Mth.sin(age*.08f)*.12f;
            head.xScale=head.yScale=head.zScale=panzer?1:1.15f;
            if(variant==1){torso.xScale=.86f;torso.zScale=.9f;left.xScale=right.xScale=.85f;left.zScale=right.zScale=.85f;head.xScale*=1.1f;ll.xScale=rl.xScale=.85f;}
            if(variant==2){torso.xScale=1.19f;torso.zScale=1.15f;left.x+=.6f;right.x-=.6f;left.y-=.5f;right.y-=.5f;lf.xScale=rf.xScale=1.18f;lf.zScale=rf.zScale=1.18f;ll.xScale=rl.xScale=1.2f;ll.x+=.3f;rl.x-=.3f;}
            String animation=e.bossAnimation();boolean locomotion=animation.equals("idle");
            String clip=locomotion?(amount>.03f||Math.pow(e.getX()-e.xo,2)+Math.pow(e.getZ()-e.zo,2)>.00001?"run":"idle"):animation;
            float time=locomotion?age:e.bossAnimationAge(age-Mth.floor(age));
            if(e.deathTime==0)T6BossAnimations.apply(panzer,clip,time,locomotion||clip.equals("flame"),e.bossAnimationDuration(),new ModelPart[]{torso,head,left,right,lf,rf,ll,rl,ls,rs});
            torso.yRot=Mth.clamp(Mth.wrapDegrees(torso.yRot*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD,-.2f,.2f);
            if(locomotion){head.yRot+=yaw*Mth.DEG_TO_RAD;head.xRot+=pitch*Mth.DEG_TO_RAD;}
            if(!e.hasBossHelmet()){if(panzer){head.getChild("helmet_window_top").visible=false;head.getChild("helmet_window_bottom").visible=false;head.getChild("glass_left").visible=false;head.getChild("glass_right").visible=false;}else{head.getChild("helmet_crown").visible=false;head.getChild("helmet_top").visible=false;head.getChild("helmet_brim").visible=false;}}
            if(panzer){torso.getChild("breastplate_reactor_frame").visible=e.hasCoreCover();torso.getChild("lamp_reactor").visible=e.hasPowerCore();}
            if(e.deathTime>0){float collapse=Mth.clamp((e.deathTime+age-Mth.floor(age))/20f,0,1);root.y+=collapse*9;torso.xRot=.12f+collapse*.45f;head.xRot=collapse*.35f;left.xRot=right.xRot=-collapse*.2f;ll.xRot=rl.xRot=-collapse*.65f;ls.xRot=rs.xRot=collapse*1.1f;}
            if(panzer&&clip.equals("grab")){lf.getChild("finger_l").zRot=.45f;lf.getChild("finger_r").zRot=-.45f;}

        }
        @Override public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer v,int light,int overlay,float r,float g,float b,float a){root.render(pose,v,light,overlay,r,g,b,a);}
    }
}
