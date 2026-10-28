package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.entity.MapActorEntity;
import me.cryo.zombierool.gameplay.MapActorAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Map-owned cuboids, textures, bone hierarchy and animation keyframes. */
public final class MapActorRenderer extends MobRenderer<MapActorEntity,MapActorRenderer.Model> {
    private final Map<String,Model> models=new LinkedHashMap<>(96,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,Model> entry){return size()>96;}};
    public MapActorRenderer(EntityRendererProvider.Context context){super(context,new Model(new CompoundTag()),.5f);
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<MapActorEntity,Model>(this){@Override public void render(PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,MapActorEntity actor,float walk,float amount,float partial,float age,float yaw,float pitch){if(actor.isAlive())model.renderEyes(pose,buffers,getTextureLocation(actor),age,actor.profile());}});
    }
    @Override protected boolean shouldShowName(MapActorEntity e){return e.profile().getBoolean("boss")&&super.shouldShowName(e);}
    @Override protected net.minecraft.client.renderer.RenderType getRenderType(MapActorEntity e,boolean visible,boolean translucent,boolean glowing){return visible&&e.profile().contains("renderAlpha")&&e.profile().getDouble("renderAlpha")<1?net.minecraft.client.renderer.RenderType.entityTranslucent(getTextureLocation(e)):super.getRenderType(e,visible,translucent,glowing);}
    @Override public ResourceLocation getTextureLocation(MapActorEntity e){
        String texture=e.profile().getString("texture");
        if(!texture.isEmpty()){var dynamic=me.cryo.zombierool.core.manager.DynamicResourceManager.getClientSkin("map_actor",texture);if(dynamic!=null)return dynamic;var resource=texture.contains(":")?ResourceLocation.tryParse(texture):null;if(resource!=null)return resource;}
        return new ResourceLocation("zombierool","textures/entity/map_actor_white.png");
    }
    @Override protected float getFlipDegrees(MapActorEntity e){return e.profile().getCompound("animations").contains("death")?0:90;}
    @Override public void render(MapActorEntity e,float yaw,float partial,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
        String key=e.visualKey();model=models.computeIfAbsent(key,k->new Model(e.profile()));super.render(e,yaw,partial,pose,buffers,light);
    }
    @Override protected void scale(MapActorEntity e,PoseStack pose,float partial){float scale=(float)e.profile().getDouble("scale");pose.scale(scale,scale,scale);}
    public static final class Model extends EntityModel<MapActorEntity> {
        private record Bone(ModelPart part,String parent,float baseX,float baseY,float baseZ,float rx,float ry,float rz){}
        private record Box(ModelPart part,List<Bone> path,float r,float g,float b,boolean emissive,float eyeX,float eyeY,float eyeZ){}
        private final Map<String,Bone> bones=new LinkedHashMap<>();
        private final List<Box> parts=new ArrayList<>();
        private final boolean fullBright,fadeDeath;private final float opacity;private float deathOpacity=1;
        private static final class Playback {float previousSwing,attackStart=-10000,changed;String clip="";final me.cryo.zombierool.gameplay.MapActorAnimationSampler.Pose current,previous;Playback(me.cryo.zombierool.gameplay.MapActorAnimationSampler sampler){current=sampler.newPose();previous=sampler.newPose();}}
        private final me.cryo.zombierool.gameplay.MapActorAnimationSampler sampler;
        private final Bone[] boneOrder;
        private final Map<MapActorEntity,Playback> playback=new WeakHashMap<>();
        Model(CompoundTag profile) {
            fullBright=profile.getBoolean("fullBright");fadeDeath=profile.getBoolean("despawnAfterDeath");opacity=profile.contains("renderAlpha")?(float)profile.getDouble("renderAlpha"):1;
            var definitions=MapActorAnimations.bones(profile.getCompound("bones"));
            for(String name:new TreeSet<>(definitions.getAllKeys())){
                var n=definitions.getCompound(name);var parent=definitions.getCompound(n.getString("parent"));
                float x=(float)(n.getDouble("pivotX")-parent.getDouble("pivotX")),y=(float)(n.getDouble("pivotY")-parent.getDouble("pivotY")),z=(float)(n.getDouble("pivotZ")-parent.getDouble("pivotZ"));
                bones.put(name,new Bone(new ModelPart(List.of(),Map.of()),n.getString("parent"),x,y,z,(float)Math.toRadians(n.getDouble("rx")),(float)Math.toRadians(n.getDouble("ry")),(float)Math.toRadians(n.getDouble("rz"))));
            }
            boneOrder=bones.values().toArray(Bone[]::new);sampler=new me.cryo.zombierool.gameplay.MapActorAnimationSampler(profile.getCompound("animations"),List.copyOf(bones.keySet()));
            var input=profile.getList("boxes",10);ListTag boxes=input.isEmpty()?me.cryo.zombierool.gameplay.MapActorTemplates.humanoid():input;
            for(var t:boxes){var b=(CompoundTag)t;var mesh=new MeshDefinition();String bone=b.getString("bone");var pivot=definitions.getCompound(bone);
                mesh.getRoot().addOrReplaceChild("box",CubeListBuilder.create().texOffs(b.getInt("u"),b.getInt("v")).addBox((float)(b.getDouble("x")-pivot.getDouble("pivotX")),(float)(b.getDouble("y")-pivot.getDouble("pivotY")),(float)(b.getDouble("z")-pivot.getDouble("pivotZ")),(float)b.getDouble("sx"),(float)b.getDouble("sy"),(float)b.getDouble("sz")),PartPose.ZERO);
                var path=new ArrayList<Bone>();String next=bone;for(int i=0;i<40&&!next.isEmpty();i++){var current=bones.get(next);if(current==null)break;path.add(0,current);next=current.parent;}
                parts.add(new Box(LayerDefinition.create(mesh,Math.max(1,profile.getInt("textureWidth")),Math.max(1,profile.getInt("textureHeight"))).bakeRoot().getChild("box"),path,(float)b.getDouble("r"),(float)b.getDouble("g"),(float)b.getDouble("b"),b.getBoolean("emissive"),(float)(b.getDouble("x")-pivot.getDouble("pivotX")+b.getDouble("sx")/2)/16,(float)(b.getDouble("y")-pivot.getDouble("pivotY"))/16,(float)(b.getDouble("z")-pivot.getDouble("pivotZ"))/16));
            }
        }
        @Override public void setupAnim(MapActorEntity e,float walk,float amount,float age,float yaw,float pitch){
            deathOpacity=fadeDeath&&!e.isAlive()?Math.max(0,1-(e.deathTime+(age-e.tickCount))/(float)Math.max(1,e.profile().getCompound("animations").getCompound("death").getDouble("duration"))):1;
            for(var entry:bones.entrySet()){
                var b=entry.getValue();var part=b.part;part.x=b.baseX;part.y=b.baseY;part.z=b.baseZ;part.xRot=b.rx;part.yRot=b.ry;part.zRot=b.rz;
                if(!e.profile().getBoolean("sourceMotion")){
                    switch(entry.getKey()){case "head"->{part.yRot=yaw*(float)Math.PI/180;part.xRot=pitch*(float)Math.PI/180;}case "left_leg","right_arm"->part.xRot+=(float)Math.cos(walk*.6662)*amount*1.2f;case "right_leg","left_arm"->part.xRot+=(float)Math.cos(walk*.6662+Math.PI)*amount*1.2f;}
                    if(attackTime>0&&(entry.getKey().equals("right_arm")||entry.getKey().equals("left_arm")))part.xRot-=(float)Math.sin(Math.sqrt(attackTime)*Math.PI)*1.8f;
                }
            }
            boolean custom=e.isAlive()&&!e.customAnimation().isEmpty();
            var state=playback.computeIfAbsent(e,key->new Playback(sampler));
            if(attackTime>0&&(state.previousSwing==0||attackTime<state.previousSwing))state.attackStart=age;state.previousSwing=attackTime;
            boolean attacking=age-state.attackStart<e.profile().getCompound("animations").getCompound("attack").getDouble("duration");
            String movement=amount>.45&&e.profile().getCompound("animations").contains("run")?"run":amount>.02?"walk":"idle";
            String mode=e.profile().getString("speedMode");if(amount>.02&&!mode.equals("custom")&&!mode.isEmpty())movement=mode.equals("run")?"run":"walk";
            String selected=!e.isAlive()?"death":custom?e.customAnimation():e.casting()?"cast":attacking?"attack":movement;
            var clip=sampler.clip(selected);if(clip==null)return;
            double time=custom?e.customAnimationAge(age):selected.equals("death")?e.deathTime+(age-e.tickCount):selected.equals("cast")?e.castAnimationAge(age):selected.equals("attack")?age-state.attackStart:age;
            if(!selected.equals(state.clip)){state.previous.copyFrom(state.current);state.changed=state.clip.isEmpty()?age-6:age;state.clip=selected;}
            sampler.sample(clip,time,state.current);
            if(age-state.changed<6)sampler.blend(state.previous,state.current,Math.max(0,(age-state.changed)/6));
            var sample=state.current;var values=sample.values;
            for(int i=0;i<boneOrder.length;i++){var part=boneOrder[i].part;int mask=sample.masks[i],offset=i*10;
                if((mask&1)!=0)part.xRot=(float)Math.toRadians(values[offset]);if((mask&2)!=0)part.yRot=(float)Math.toRadians(values[offset+1]);if((mask&4)!=0)part.zRot=(float)Math.toRadians(values[offset+2]);
                if((mask&512)!=0){float x=(float)values[offset+6],y=(float)values[offset+7],z=(float)values[offset+8],w=(float)values[offset+9];part.xRot=(float)Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y));part.yRot=(float)Math.asin(net.minecraft.util.Mth.clamp(2*(w*y-z*x),-1,1));part.zRot=(float)Math.atan2(2*(w*z+x*y),1-2*(y*y+z*z));}
                part.x+=(float)values[offset+3];part.y+=(float)values[offset+4];part.z+=(float)values[offset+5];
            }
            if(clip.loop&&bones.containsKey("head")){bones.get("head").part.yRot+=net.minecraft.util.Mth.clamp(yaw,-60,60)*(float)Math.PI/180;bones.get("head").part.xRot+=pitch*(float)Math.PI/360;}
        }
        void renderEyes(PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,ResourceLocation texture,float age,CompoundTag profile){
            for(var box:parts)if(box.emissive){pose.pushPose();for(var bone:box.path)bone.part.translateAndRotate(pose);box.part.render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(texture)),net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                var out=buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());var matrix=pose.last().pose();int red=(int)(255*profile.getDouble("eyeR")),green=(int)(255*profile.getDouble("eyeG")),blue=(int)(255*profile.getDouble("eyeB"));
                for(int i=0;i<4;i++){float t=(age*.035f+i*.25f)%1,y=box.eyeY-t*.2f,x=box.eyeX+net.minecraft.util.Mth.sin(age*.07f+i)*t*.025f,z=box.eyeZ-.012f,w=.013f*(1-t);int alpha=(int)(100*(1-t));out.vertex(matrix,x-w,y,z).color(red,green,blue,alpha).endVertex();out.vertex(matrix,x+w,y,z).color(red,green,blue,alpha).endVertex();out.vertex(matrix,x+w,y-.035f,z).color(red,green,blue,0).endVertex();out.vertex(matrix,x-w,y-.035f,z).color(red,green,blue,0).endVertex();}
                pose.popPose();}
        }
        @Override public void renderToBuffer(PoseStack pose,VertexConsumer consumer,int light,int overlay,float r,float g,float b,float alpha){
            for(var box:parts){pose.pushPose();for(var bone:box.path)bone.part.translateAndRotate(pose);box.part.render(pose,consumer,fullBright?net.minecraft.client.renderer.LightTexture.FULL_BRIGHT:light,overlay,r*box.r,g*box.g,b*box.b,alpha*opacity*deathOpacity);pose.popPose();}
        }
    }
}
