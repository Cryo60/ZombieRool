package me.cryo.zombierool.client.renderer;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored cuboids animated by the original source tracks, with connected hips. */
public final class BlockyEnemyRenderer<T extends net.minecraft.world.entity.Mob> extends MobRenderer<T,BlockyEnemyRenderer.Model<T>> {
    private final String kind;
    public BlockyEnemyRenderer(EntityRendererProvider.Context context,String kind){super(context,new Model<>(kind),.5f);this.kind=kind;
        if(kind.startsWith("scourge_")||kind.equals("geist")||kind.equals("abomination"))addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<T,Model<T>>(this){
            @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float walk,float amount,float partial,float age,float yaw,float pitch){if(entity.isAlive())model.renderEyes(pose,buffers,getTextureLocation(entity),age,entity instanceof ZombieEntity z&&z.mapOutfit().getString("faction").equals("risen"));}
        });
    }
    public BlockyEnemyRenderer(EntityRendererProvider.Context context,String kind,net.minecraft.nbt.CompoundTag outfit){this(context,kind);model=new Model<>(kind,outfit);}
    @Override public ResourceLocation getTextureLocation(T e){
        if(e instanceof ZombieEntity risen&&risen.mapOutfit().getString("faction").equals("risen"))return new ResourceLocation("zombierool","textures/entities/blocky/risen_zombie.png");
        if(e instanceof ZombieEntity z){String variant=me.cryo.zombierool.config.EnemySkinPresets.blockyTexture(z.getCustomSkin());if(!variant.isBlank())return new ResourceLocation("zombierool",variant);}
        if(e instanceof SpecialEnemyEntity s&&kind.equals("george_romero")&&s.bossProfile().getBoolean("enraged"))return new ResourceLocation("zombierool","textures/entities/blocky/george_romero_enraged.png");
        return model.texture;
    }
    @Override protected boolean shouldShowName(T e){return !me.cryo.zombierool.gameplay.MapActors.owns(e)&&!kind.equals("abomination")&&super.shouldShowName(e);}
    @Override protected float getFlipDegrees(T e){return 0;}
    @Override protected void scale(T e,PoseStack pose,float partial){
        float scale=model.scale*(e instanceof AbstractZombieRoolEntity z?z.getScale():1);float vertical=1;
        if(e instanceof SpecialEnemyEntity s&&s.isBossCrouched())vertical=.76f;
        pose.scale(scale,scale*vertical,scale);
        if(e instanceof AbstractZombieRoolEntity body&&CerberusCorpsePose.head(body)!=null){
            var anchor=model.headAnchor();pose.translate(-anchor.x,1.501-anchor.y,-anchor.z);
        }
        if(e instanceof AbstractZombieRoolEntity z&&z.isAlive()&&z.getEmergenceTicks()>0)pose.translate(0,e.getBbHeight()/scale*(z.getEmergenceTicks()-partial)/me.cryo.zombierool.gameplay.SpawnEmergence.DURATION,0);
    }
    @Override protected void setupRotations(T e,PoseStack pose,float age,float yaw,float partial){
        if(e instanceof AbstractZombieRoolEntity body&&body.isCerberusFeeding()){CerberusCorpsePose.rotate(body,pose,partial);return;}
        super.setupRotations(e,pose,age,yaw,partial);
        var flight=me.cryo.zombierool.gameplay.TraversalMotion.flight(e);
        if(kind.equals("geist")&&flight!=null&&flight.style==2){pose.translate(0,0,-.55);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));}
    }
    @Override public void render(T e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();
        if(e instanceof AbstractZombieRoolEntity body&&CerberusCorpsePose.head(body)!=null){
            var head=CerberusCorpsePose.head(body);double t=Math.max(0,e.level().getGameTime()+partial-head.phaseStarted);
            // ZombieRenderer already supplies the shared jaw offset; directly registered Geists need it here.
            if(!(e instanceof ZombieEntity)){var offset=CerberusCorpsePose.offset(body,partial);pose.translate(offset.x,offset.y,offset.z);}
            buffers=CerberusCorpsePose.eatingBuffer(body,pose,partial,buffers);
            var mouth=head.mouth(t);var relative=mouth.subtract(head.feedingPosition(body,t));
            double angle=me.cryo.zombierool.block.system.CerberusSystem.headYaw(2,t),pitch=me.cryo.zombierool.block.system.CerberusSystem.headPitch(2,t);
            var forward=net.minecraft.world.phys.Vec3.atLowerCornerOf(head.getBlockState().getValue(me.cryo.zombierool.block.system.CerberusSystem.WallBlock.FACING).getNormal());
            var side=new net.minecraft.world.phys.Vec3(-forward.z,0,forward.x);
            var axis=forward.scale(Math.cos(angle)*Math.cos(pitch)).add(side.scale(-Math.sin(angle)*Math.cos(pitch))).add(0,-Math.sin(pitch),0);
            double swallowed=me.cryo.zombierool.block.system.CerberusSystem.eaten(t,head.chewTime*20)*2.8*model.scale*body.getScale();
            relative=relative.subtract(axis.scale(swallowed));pose.translate(relative.x,relative.y,relative.z);
            model.setupAnim(e,0,0,e.tickCount+partial,0,0);
        }
        try{super.render(e,yaw,partial,pose,buffers,light);if(e instanceof SpecialEnemyEntity boss&&e.isAlive())EncounterRenderer.renderHook(boss,pose,buffers,light);}finally{pose.popPose();}
    }

    private static JsonObject read(String path){try(var in=Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("zombierool",path)).orElseThrow().open();var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}catch(IOException ex){throw new IllegalStateException(path,ex);}}
    public static final class Model<T extends LivingEntity> extends EntityModel<T>{
        private final ModelPart root;
        private final ModelPart[] allParts;
        private ModelPart eyes;
        private final org.joml.Vector3f headCenter=new org.joml.Vector3f();
        private final List<float[]> eyeBoxes=new ArrayList<>();
        private final Map<String,ModelPart> bones=new HashMap<>();
        private final Map<String,Integer> gore=new HashMap<>();
        private final String kind;
        final float scale;
        final ResourceLocation texture;
        private static final String[] JOINTS={"torso","head","left_arm","right_arm","left_forearm","right_forearm","left_leg","right_leg","left_shin","right_shin"};
        private static final String[] LEGS={"left_leg","right_leg"};
        private static final me.cryo.zombierool.core.manager.GoreManager.Limb[] LIMBS=me.cryo.zombierool.core.manager.GoreManager.Limb.values();
        private record Clip(float fps,float[][] frames){}
        private static final class State{
            final float[] current=new float[53],previous=new float[53],target=new float[53];
            String action="";float changed,deathStart,attackStart=-1000,gaitAge=Float.NaN,gaitTime,gaitRate=1;boolean initialized,dead,attacking;
        }
        private final Map<String,Clip> clips=new HashMap<>();
        private final Map<LivingEntity,State> states=new WeakHashMap<>();
        private final Map<String,org.joml.Vector3f> origins=new HashMap<>();
        private final org.joml.Vector3f hip=new org.joml.Vector3f(),vector=new org.joml.Vector3f();
        private final org.joml.Quaternionf rotation=new org.joml.Quaternionf(),other=new org.joml.Quaternionf();
        Model(String kind){this(kind,new net.minecraft.nbt.CompoundTag());}
        Model(String kind,net.minecraft.nbt.CompoundTag outfit){
            this.kind=kind;JsonObject data=read("models/entities/blocky/"+kind+".json");scale=data.get("scale").getAsFloat();texture=new ResourceLocation(data.get("texture").getAsString());
            // Append equipment to the original rig; original flesh, joints and animation files stay intact.
            var mesh=new MeshDefinition();var definitions=new HashMap<String,PartDefinition>();definitions.put("",mesh.getRoot());
            for(var row:data.getAsJsonArray("parts")){
                var p=row.getAsJsonObject();String name=p.get("name").getAsString(),parent=p.has("parent")?p.get("parent").getAsString():"";var cubes=CubeListBuilder.create();
                var glowing=CubeListBuilder.create();
                for(var c:p.getAsJsonArray("cubes")){var cube=c.getAsJsonObject();int tile=cube.get("material").getAsInt();var b=cube.getAsJsonArray("box");cubes.texOffs(tile%4*64,tile/4*32).addBox(f(b,0),f(b,1),f(b,2),f(b,3),f(b,4),f(b,5));if(tile==9&&name.equals("head")){glowing.texOffs(tile%4*64,tile/4*32).addBox(f(b,0)-.01f,f(b,1)-.01f,f(b,2)-.01f,f(b,3)+.02f,f(b,4)+.02f,f(b,5)+.02f);eyeBoxes.add(new float[]{f(b,0)+f(b,3)/2,f(b,1),f(b,2)});}}
                var pivot=p.getAsJsonArray("pivot");var rotation=p.getAsJsonArray("rotation");definitions.put(name,definitions.get(parent).addOrReplaceChild(name,cubes,PartPose.offsetAndRotation(f(pivot,0),f(pivot,1),f(pivot,2),f(rotation,0),f(rotation,1),f(rotation,2))));
                gore.put(name,p.get("gore").getAsInt());
                if(name.equals("head"))definitions.get(name).addOrReplaceChild("glowing_eyes",glowing,PartPose.ZERO);
            }
            int equipmentIndex=0;for(var tag:outfit.getList("armorBoxes",10)){var cube=(net.minecraft.nbt.CompoundTag)tag;var parent=definitions.get(cube.getString("bone"));if(parent==null)continue;var box=cube.getList("box",6);int tile=cube.getInt("material");parent.addOrReplaceChild("map_equipment_"+equipmentIndex++,CubeListBuilder.create().texOffs(tile%4*64,tile/4*32).addBox((float)box.getDouble(0),(float)box.getDouble(1),(float)box.getDouble(2),(float)box.getDouble(3),(float)box.getDouble(4),(float)box.getDouble(5)),PartPose.rotation((float)Math.toRadians(cube.getDouble("rx")),(float)Math.toRadians(cube.getDouble("ry")),(float)Math.toRadians(cube.getDouble("rz"))));}
            for(var row:data.getAsJsonArray("parts")){var part=row.getAsJsonObject();if(!part.get("name").getAsString().equals("head"))continue;float largest=-1;for(var raw:part.getAsJsonArray("cubes")){var box=raw.getAsJsonObject().getAsJsonArray("box");float volume=f(box,3)*f(box,4)*f(box,5);if(volume>largest){largest=volume;headCenter.set((f(box,0)+f(box,3)/2)/16,(f(box,1)+f(box,4)/2)/16,(f(box,2)+f(box,5)/2)/16);}}}
            root=LayerDefinition.create(mesh,256,256).bakeRoot();for(var row:data.getAsJsonArray("parts")){var p=row.getAsJsonObject();String name=p.get("name").getAsString(),parent=p.has("parent")?p.get("parent").getAsString():"";bones.put(name,(parent.isBlank()?root:bones.get(parent)).getChild(name));}
            eyes=b("head").getChild("glowing_eyes");
            // The baked hierarchy is immutable. Reuse its traversal instead of building
            // recursive streams for every enemy on every rendered frame.
            allParts=root.getAllParts().toArray(ModelPart[]::new);
            for(String joint:JOINTS){var part=b(joint);origins.put(joint,new org.joml.Vector3f(part.x,part.y,part.z));}
            var animation=read("zr_animation_data/blocky/"+kind+".json");
            if(animation.get("format").getAsInt()!=2)throw new IllegalStateException("Source motion format: "+kind);
            var anchor=animation.getAsJsonArray("hip");hip.set(f(anchor,0),f(anchor,1),f(anchor,2));
            for(var entry:animation.getAsJsonObject("clips").entrySet()){var c=entry.getValue().getAsJsonObject();var raw=c.getAsJsonArray("frames");float[][] frames=new float[raw.size()][53];for(int i=0;i<frames.length;i++)for(int j=0;j<raw.get(i).getAsJsonArray().size();j++)frames[i][j]=f(raw.get(i).getAsJsonArray(),j);clips.put(entry.getKey(),new Clip(c.get("fps").getAsFloat(),frames));}
        }
        private static float f(JsonArray a,int i){return a.get(i).getAsFloat();}
        private ModelPart b(String name){return bones.get(name);}
        org.joml.Vector3f headAnchor(){var stack=new PoseStack();root.translateAndRotate(stack);b("torso").translateAndRotate(stack);b("head").translateAndRotate(stack);return stack.last().pose().transformPosition(new org.joml.Vector3f(headCenter));}
        void renderEyes(PoseStack pose,MultiBufferSource buffers,ResourceLocation texture,float age,boolean risen){if(!b("head").visible)return;pose.pushPose();root.translateAndRotate(pose);b("torso").translateAndRotate(pose);b("head").translateAndRotate(pose);eyes.render(pose,buffers.getBuffer(RenderType.eyes(texture)),LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            var out=buffers.getBuffer(RenderType.lightning());var matrix=pose.last().pose();for(var eye:eyeBoxes)for(int i=0;i<4;i++){float t=(age*.035f+i*.25f)%1,y=eye[1]/16-t*.2f,x=eye[0]/16+Mth.sin(age*.07f+i)*t*.025f,z=eye[2]/16-.012f,w=.013f*(1-t);int alpha=(int)(100*(1-t));
                out.vertex(matrix,x-w,y,z).color(risen?65:70,risen?255:180,risen?70:255,alpha).endVertex();out.vertex(matrix,x+w,y,z).color(risen?90:90,risen?255:210,risen?80:255,alpha).endVertex();out.vertex(matrix,x+w,y-.035f,z).color(90,risen?255:210,risen?80:255,0).endVertex();out.vertex(matrix,x-w,y-.035f,z).color(risen?65:70,risen?255:180,risen?70:255,0).endVertex();}
            pose.popPose();
        }
        @Override public void setupAnim(T e,float walk,float amount,float age,float yaw,float pitch){
            for(var part:allParts){part.resetPose();part.visible=true;}
            float partial=Mth.clamp(age-e.tickCount,0,1);var state=states.computeIfAbsent(e,key->new State());
            var special=e instanceof SpecialEnemyEntity boss?boss:null;String action=special==null?"idle":special.bossAnimation();
            boolean feeding=e instanceof AbstractZombieRoolEntity body&&body.isCerberusFeeding();
            if(feeding)action="idle";
            if(kind.equals("geist")&&me.cryo.zombierool.gameplay.TraversalMotion.active(e)){amount=Math.max(amount,.65f);action="idle";}
            boolean loop=action.equals("idle");float attack=e.getAttackAnim(partial);
            if(!feeding&&loop&&amount>.025f){
                boolean running=(kind.equals("geist")?amount>(state.action.equals("run")?.34f:.44f):e instanceof ZombieEntity z?!z.isCrawler()&&(z.isSuperSprinter()||e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)>.22):amount>(state.action.equals("run")?.5f:.7f));
                if(e instanceof ZombieEntity z&&!z.mapOutfit().isEmpty()){String mode=z.mapOutfit().getString("speedMode");if(mode.equals("run"))running=true;else if(mode.equals("walk")||mode.equals("trot"))running=false;}
                action=running?"run":"walk";
            }
            if(special==null&&!feeding){if(attack>0&&!state.attacking)state.attackStart=age;state.attacking=attack>0;
                var melee=clips.get("melee");float length=melee==null?20:(melee.frames.length-1)*20/melee.fps*(kind.equals("scourge_skeleton")?.9f:1);
                if(age-state.attackStart<length){action="melee";loop=false;}}
            if(e.deathTime>0&&!feeding){if(!state.dead){state.dead=true;state.deathStart=age-e.deathTime;}action="death";loop=false;}
            else state.dead=false;
            Clip clip=clips.getOrDefault(action,clips.get("idle"));float frame;
            if(feeding)frame=0;
            else if(state.dead)frame=(age-state.deathStart)*clip.fps/20;
            else if(loop){
                if(kind.equals("geist")){
                    float dt=Float.isNaN(state.gaitAge)?0:Mth.clamp(age-state.gaitAge,0,2);
                    if(Float.isNaN(state.gaitAge))state.gaitTime=age;
                    float rate=Mth.clamp(.55f+amount*1.15f,.55f,1.55f);
                    state.gaitRate=Mth.lerp(Math.min(1,dt*.25f),state.gaitRate,rate);
                    state.gaitTime+=dt*state.gaitRate;state.gaitAge=age;frame=state.gaitTime*clip.fps/20;
                }else frame=age*clip.fps/20*(e instanceof ZombieEntity z&&action.equals("walk")&&z.mapOutfit().getString("speedMode").equals("trot")?1.25f:1);
            }
            else if(special!=null)frame=special.bossAnimationAge(partial)/Math.max(1,special.bossAnimationDuration())*(clip.frames.length-1);
            else frame=(age-state.attackStart)*clip.fps/20/(kind.equals("scourge_skeleton")?.9f:1);
            if(!loop)state.gaitAge=age;
            sample(clip,frame,loop,state.target);
            if(!state.initialized){System.arraycopy(state.target,0,state.current,0,53);state.action=action;state.initialized=true;state.changed=age-6;}
            if(!state.action.equals(action)){System.arraycopy(state.current,0,state.previous,0,53);state.action=action;state.changed=age;}
            float blend=feeding?1:Mth.clamp((age-state.changed)/6,0,1);blend=blend*blend*(3-2*blend);
            if(blend<1)interpolate(state.previous,state.target,blend,state.current);else System.arraycopy(state.target,0,state.current,0,53);
            var values=state.current;
            for(int i=0;i<JOINTS.length;i++){load(values,i*4,rotation);var part=b(JOINTS[i]);float x=rotation.x,y=rotation.y,z=rotation.z,w=rotation.w;
                // The bundled JOML 1.10.5 ZYX extractor has an incorrect sign
                // in its X denominator. Compute the matching Rz*Ry*Rx angles.
                part.xRot=(float)Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y));part.yRot=(float)Math.asin(Mth.clamp(2*(w*y-z*x),-1,1));part.zRot=(float)Math.atan2(2*(w*z+x*y),1-2*(y*y+z*z));}
            // Upper body rotates around the hips, never around its neck. The
            // thigh roots follow the source pelvis; every limb stays attached.
            load(values,0,rotation);vector.set(origins.get("torso")).sub(hip).rotate(rotation).add(hip).add(values[40],values[41],values[42]);b("torso").setPos(vector.x+values[47],vector.y+values[48],vector.z+values[49]);
            for(String side:LEGS){load(values,43,rotation);vector.set(origins.get(side)).sub(hip).rotate(rotation).add(hip).add(values[40],values[41],values[42]);b(side).setPos(vector.x+values[50],vector.y+values[51],vector.z+values[52]);}
            if(loop&&!feeding){b("head").yRot+=Mth.clamp(yaw,-60,60)*Mth.DEG_TO_RAD;b("head").xRot+=pitch*Mth.DEG_TO_RAD*.5f;}
            if(e instanceof ZombieEntity z&&!me.cryo.zombierool.configuration.ZRClientConfig.isGoreReduced()){
                int mask=0;for(var limb:LIMBS)if(me.cryo.zombierool.core.manager.GoreManager.hasLostLimb(e,limb))mask|=1<<limb.ordinal();if(z.isHeadshotDeath())mask|=1;
                for(var entry:gore.entrySet())if((mask&entry.getValue())!=0)b(entry.getKey()).visible=false;
            }
        }
        private static void load(float[] values,int offset,org.joml.Quaternionf q){q.set(values[offset],values[offset+1],values[offset+2],values[offset+3]);}
        private void interpolate(float[] a,float[] b,float t,float[] out){
            for(int i=0;i<10;i++){int k=i*4;load(a,k,rotation);load(b,k,other);rotation.slerp(other,t).normalize();out[k]=rotation.x;out[k+1]=rotation.y;out[k+2]=rotation.z;out[k+3]=rotation.w;}
            for(int k=40;k<43;k++)out[k]=Mth.lerp(t,a[k],b[k]);for(int k=47;k<53;k++)out[k]=Mth.lerp(t,a[k],b[k]);load(a,43,rotation);load(b,43,other);rotation.slerp(other,t).normalize();out[43]=rotation.x;out[44]=rotation.y;out[45]=rotation.z;out[46]=rotation.w;
        }
        private void sample(Clip clip,float frame,boolean loop,float[] result){frame=Math.max(0,loop?frame%clip.frames.length:Math.min(frame,clip.frames.length-1));int first=(int)frame,next=loop?(first+1)%clip.frames.length:Math.min(first+1,clip.frames.length-1);interpolate(clip.frames[first],clip.frames[next],frame-first,result);}
        @Override public void renderToBuffer(PoseStack pose,VertexConsumer v,int light,int overlay,float r,float g,float b,float a){root.render(pose,v,light,overlay,r,g,b,a);}
    }
}
