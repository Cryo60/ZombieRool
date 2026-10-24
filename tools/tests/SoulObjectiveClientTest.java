package local.hotfixtest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class SoulObjectiveClientTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("SOUL CLIENT CHECK: "+message);}
    public static void run()throws Exception{
        var mc=Minecraft.getInstance();
        var rendererClass=BuyWallWeaponSystem.BuyWallWeaponRenderer.class;
        var chalk=rendererClass.getDeclaredMethod("createChalkOutline",NativeImage.class);chalk.setAccessible(true);
        try(var source=new NativeImage(256,256,true)){
            for(int y=0;y<256;y++)for(int x=0;x<256;x++)source.setPixelRGBA(x,y,0);
            for(int y=60;y<170;y++)for(int x=30;x<220;x++)source.setPixelRGBA(x,y,0xFFFFFFFF);
            source.setPixelRGBA(10,10,0x60FFFFFF);source.setPixelRGBA(11,10,0x80FFFFFF);source.setPixelRGBA(240,240,0xFFFFFFFF);
            try(var result=(NativeImage)chalk.invoke(null,source)){
                check((result.getPixelRGBA(10,10)>>>24)==0&&(result.getPixelRGBA(240,240)>>>24)==0,"Chalk removes faint alpha noise and isolated white-dot islands");
                check((result.getPixelRGBA(30,100)>>>24)==255&&(result.getPixelRGBA(100,100)>>>24)==0,"Chalk retains the readable perimeter without filling the weapon body");
            }
        }
        for(String id:new String[]{"winters_howl","vr11","m1014"}){
            var path=new ResourceLocation("zombierool","textures/item/weapons/"+id+".png");
            try(var input=mc.getResourceManager().getResource(path).orElseThrow().open();var src=NativeImage.read(input);var outline=(NativeImage)chalk.invoke(null,src)){
                int white=0;for(int y=0;y<outline.getHeight();y++)for(int x=0;x<outline.getWidth();x++)if((outline.getPixelRGBA(x,y)>>>24)>0)white++;
                check(white>500,"Actual weapon texture produces a visible cleaned chalk outline: "+id);
            }
        }
        for(String id:new String[]{"cerberus","soul_completion_checker"}){
            var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool",id));
            var model=mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(item),mc.level,mc.player,0);
            check(!model.getParticleIcon().contents().name().getPath().equals("missingno"),"Loaded registered item model: "+id);
        }
        var dog=new CerberusSystem.Head(BlockPos.ZERO,MapAuthorSystem.CERBERUS.get().defaultBlockState());dog.setLevel(mc.level);
        check(mc.getBlockEntityRenderDispatcher().getRenderer(dog) instanceof me.cryo.zombierool.client.renderer.CerberusRenderer,"Cerberus head has a registered animated renderer");
        int[] vertices={0};var consumer=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(SoulObjectiveClientTest.class.getClassLoader(),new Class[]{VertexConsumer.class},(p,m,args)->{if(m.getName().equals("endVertex")||m.getName().equals("vertex")&&m.getReturnType()==void.class)vertices[0]++;return m.getReturnType()==VertexConsumer.class?p:null;});
        net.minecraft.client.renderer.MultiBufferSource buffer=type->consumer;var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(dog);
        renderer.render(dog,0,new PoseStack(),buffer,15728880,0);check(vertices[0]==0,"Dormant wall mark submits no protruding head geometry");
        dog.revealed=true;dog.phase=2;dog.phaseStarted=mc.level.getGameTime()-8;renderer.render(dog,0,new PoseStack(),buffer,15728880,0);check(vertices[0]>100,"Feeding Cerberus submits a real articulated head and glowing eyes");
        var config=dog.config();config.putInt("phase",2);config.putBoolean("revealed",true);dog.load(config);check(dog.phase==2&&dog.revealed,"Client receives the feeding phase from the block-entity update");
        vertices[0]=0;dog.completed=true;dog.phase=3;dog.phaseStarted=mc.level.getGameTime();renderer.render(dog,0,new PoseStack(),buffer,15728880,0);check(vertices[0]>100,"Completed Cerberus howls before withdrawing instead of disappearing instantly");
        vertices[0]=0;dog.phaseStarted=mc.level.getGameTime()-CerberusSystem.DEPARTURE_TICKS;renderer.render(dog,0,new PoseStack(),buffer,15728880,0);check(vertices[0]==4,"After withdrawal only the illuminated wall mark remains");
        var markPoints=new java.util.ArrayList<org.joml.Vector3f>();
        var markConsumer=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(SoulObjectiveClientTest.class.getClassLoader(),new Class[]{VertexConsumer.class},(p,m,args)->{if(m.getName().equals("vertex")&&args.length==4&&args[0] instanceof org.joml.Matrix4f matrix)markPoints.add(matrix.transformPosition(new org.joml.Vector3f((float)args[1],(float)args[2],(float)args[3])));return m.getReturnType()==VertexConsumer.class?p:null;});
        renderer.render(dog,0,new PoseStack(),type->markConsumer,0,0);check(markPoints.size()==4&&markPoints.stream().allMatch(p->p.z<15.95f/16),"Completed emissive mark is in front of the opaque north-face block, including in darkness");
        var clear=net.minecraft.client.particle.ParticleEngine.class.getDeclaredMethod("clearParticles");clear.setAccessible(true);clear.invoke(mc.particleEngine);
        me.cryo.zombierool.client.ClientWeaponVfxHandler.handleVfx("WINTERS_HOWL",mc.player.getEyePosition(),mc.player.getEyePosition().add(0,0,12),false,false);
        mc.particleEngine.tick();check(Integer.parseInt(mc.particleEngine.countParticles())>=85,"Winter's Howl spawns a dense snowflake blast in the actual particle engine");
        var previous=mc.screen;me.cryo.zombierool.client.gui.TransportScreen.open(new TransportPackets.Open(BlockPos.ZERO,"cerberus",config));
        var fields=mc.screen.getClass().getDeclaredField("fields");fields.setAccessible(true);var editors=(java.util.Map<?,?>)fields.get(mc.screen);
        check(editors.containsKey("objectiveId")&&editors.containsKey("chewTime"),"Cerberus editor exposes Objective ID and chewing time");mc.setScreen(previous);
        var checker=new SoulCompletionSystem.Checker(BlockPos.ZERO,MapAuthorSystem.COMPLETION.get().defaultBlockState());
        me.cryo.zombierool.client.gui.TransportScreen.open(new TransportPackets.Open(BlockPos.ZERO,"soul_checker",checker.config()));
        editors=(java.util.Map<?,?>)fields.get(mc.screen);check(editors.containsKey("objectiveIds")&&editors.containsKey("enableChannels")&&editors.containsKey("disableChannels")&&editors.containsKey("channelDuration"),"Completion checker exposes IDs plus enable/disable spawner channels and duration");mc.setScreen(previous);
        preview(mc,dog);
        eatingAndCamo(mc);
        camoAndRespawn(mc);
        System.out.println("SOUL CLIENT PASS");
    }
    private static void eatingAndCamo(Minecraft mc)throws Exception{
        check(Math.abs(CerberusSystem.barkMotion(4,0))>.05&&Math.abs(CerberusSystem.barkMotion(4,1))<.04&&Math.abs(CerberusSystem.barkMotion(4,2))<.02,"Barks give a short restrained nod instead of frantic yaw and roll");
        check(CerberusSystem.barkMotion(-1,0)==0&&CerberusSystem.barkMotion(14,1)==0,"Bark motion stops outside the actual clip");
        for(int duration:new int[]{20,100,120}){double previous=0;for(int i=0;i<=duration;i++){double progress=CerberusSystem.eaten(i,duration);if(progress<previous||progress>1)throw new AssertionError("Consumption reverses at "+i);previous=progress;}check(previous==1,"Swallowing advances monotonically and finishes completely for "+duration+" ticks");}
        var clip=Class.forName("me.cryo.zombierool.client.renderer.CerberusCorpsePose$BiteClip");var constructor=clip.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var points=new java.util.ArrayList<double[]>();
        var target=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(SoulObjectiveClientTest.class.getClassLoader(),new Class[]{VertexConsumer.class},(p,m,args)->{if(m.getName().equals("vertex")&&args.length==3)points.add(new double[]{((Number)args[0]).doubleValue(),((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue()});return m.getReturnType()==VertexConsumer.class?p:null;});
        var clipping=(VertexConsumer)constructor.newInstance(target,new org.joml.Vector3f(1,0,0),0f);
        for(float[] p:new float[][]{{-1,0,0},{1,0,0},{1,1,0},{-1,1,0}})clipping.vertex(p[0],p[1],p[2]).color(255,255,255,255).uv(p[0],p[1]).overlayCoords(0,0).uv2(240,240).normal(0,0,1).endVertex();
        check(points.size()==4&&points.stream().allMatch(p->p[0]>=0)&&points.stream().anyMatch(p->p[0]==0),"The bite plane cuts swallowed corpse surfaces exactly at the teeth");
        points.clear();for(int i=0;i<4;i++)clipping.vertex(-1,i%2,i/2).color(255,255,255,255).uv(0,0).overlayCoords(0,0).uv2(240,240).normal(0,0,1).endVertex();check(points.isEmpty(),"Fully swallowed surfaces emit no vertices before corpse cleanup");
        var allow=me.cryo.zombierool.client.BloodSplatters.class.getDeclaredMethod("allowImpactSound",net.minecraft.client.multiplayer.ClientLevel.class,Vec3.class);allow.setAccessible(true);
        me.cryo.zombierool.client.BloodSplatters.clear();long clock=mc.level.getGameTime();
        check((boolean)allow.invoke(null,mc.level,Vec3.ZERO),"First gore impact can play gibs");
        check(!(boolean)allow.invoke(null,mc.level,new Vec3(40,0,0)),"Simultaneous sprays share a global sound limit even far apart");
        mc.level.setGameTime(clock+20);check(!(boolean)allow.invoke(null,mc.level,new Vec3(7,0,0)),"A nearby spray remains muted for the full length of the gibs clip");
        mc.level.setGameTime(clock+50);check((boolean)allow.invoke(null,mc.level,Vec3.ZERO),"Gibs becomes audible again after the cooldown");mc.level.setGameTime(clock);me.cryo.zombierool.client.BloodSplatters.clear();
        var paintedClass=Class.forName("me.cryo.zombierool.client.TacZCamoOverlay$Pattern");var create=paintedClass.getDeclaredConstructors()[0];create.setAccessible(true);
        NativeImage pattern;try(var input=mc.getResourceManager().open(new ResourceLocation("zombierool:textures/item/camos/bubbles.png"))){pattern=NativeImage.read(input);}
        var camo=create.newInstance(pattern,3,new int[]{0,1},true);
        var current=paintedClass.getDeclaredMethod("current");current.setAccessible(true);var tex=paintedClass.getDeclaredField("texture");tex.setAccessible(true);
        mc.level.setGameTime(0);current.invoke(camo);var texture=(net.minecraft.client.renderer.texture.DynamicTexture)tex.get(camo);var before=texture.getPixels().getPixelsRGBA();
        check(java.util.Arrays.stream(before).anyMatch(p->p>>>24==0),"Transparent parts of the camo leave the original gun visible without black squares");
        check(java.util.Arrays.stream(before).anyMatch(p->p>>>24>0&&p>>>24<255),"Bubbles preserve partial coverage along their curved boundaries");
        mc.level.setGameTime(1);current.invoke(camo);boolean changes=!java.util.Arrays.equals(before,texture.getPixels().getPixelsRGBA());
        check(changes,"Interpolated camo changes within a three-tick source frame");
        texture.bind();check(org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER)==org.lwjgl.opengl.GL11.GL_LINEAR,"TacZ camo texture uses smooth GPU sampling");
        texture.getPixels().writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/tacz-bubbles-preview.png"));
        var close=paintedClass.getDeclaredMethod("close");close.setAccessible(true);close.invoke(camo);mc.level.setGameTime(clock);
        // Drive the real editor buttons, then capture the exact draft sent to the server.
        var settings=TeleporterConfig.defaults(TeleporterConfig.Mode.MOON);me.cryo.zombierool.client.gui.TransportScreen.open(new TransportPackets.Open(BlockPos.ZERO,"teleporter",settings));
        var screen=mc.screen;var page=screen.getClass().getDeclaredField("page");page.setAccessible(true);page.setInt(screen,1);screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        var buttons=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).toList();
        var power=buttons.stream().filter(b->b.getMessage().getString().startsWith("Power required:")).findFirst().orElseThrow();power.onPress();
        var draft=screen.getClass().getDeclaredField("draft");draft.setAccessible(true);check(((net.minecraft.nbt.CompoundTag)draft.get(screen)).getBoolean("requiresPower"),"Power required toggles On in the actual teleporter editor");
        screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());check(((net.minecraft.nbt.CompoundTag)draft.get(screen)).getBoolean("requiresPower"),"Resizing and rebuilding teleporter buttons preserves Power required On");mc.setScreen(null);
    }
    private static void camoAndRespawn(Minecraft mc)throws Exception{
        var hasPurchased=BuyWallWeaponSystem.BuyWallWeaponRenderer.class.getDeclaredMethod("hasPlayerPurchased",net.minecraft.world.entity.player.Player.class,net.minecraft.world.item.ItemStack.class);hasPurchased.setAccessible(true);
        var nativeGun=new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:m40a3")));
        var mapped=new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));mapped.getOrCreateTag().putString("GunId","tacz:ai_awp");mapped.getOrCreateTag().putString("zombierool:weapon_id","m40a3");
        mapped.getOrCreateTag().putString("zr_camo","camo_bubbles");
        var display=com.tacz.guns.api.TimelessAPI.getGunDisplay(mapped).orElseThrow();
        var baseTexture=display.getModelTexture();
        var camoTexture=me.cryo.zombierool.client.TacZCamoOverlay.pattern(mapped);
        check(!camoTexture.equals(baseTexture),"Actual TacZ AWP texture resolves to a painted animated camo: "+baseTexture+" -> "+camoTexture);
        check(mc.getTextureManager().getTexture(camoTexture) instanceof net.minecraft.client.renderer.texture.DynamicTexture,"TacZ uses the uploaded camo texture rather than the unpainted gun");
        check(me.cryo.zombierool.core.system.WeaponFacade.getWallPurchaseId(nativeGun).equals(me.cryo.zombierool.core.system.WeaponFacade.getWallPurchaseId(mapped)),"Native wall template and mapped TacZ purchase share one purchase identity");
        BuyWallWeaponSystem.BuyWallWeaponRenderer.markAsPurchased(mc.player,me.cryo.zombierool.core.system.WeaponFacade.getWallPurchaseId(mapped));
        var saved=mc.player.getInventory().save(new net.minecraft.nbt.ListTag());mc.player.getInventory().clearContent();
        try{check((boolean)hasPurchased.invoke(null,mc.player,nativeGun),"A purchased mapped wall remains filled after swapping the gun out of the inventory");}finally{mc.player.getInventory().load(saved);BuyWallWeaponSystem.BuyWallWeaponRenderer.clearAllPurchases();}
        var maskMethod=me.cryo.zombierool.client.render.CamoBakedModelManager.class.getDeclaredMethod("getOpaqueMap",ResourceLocation.class);maskMethod.setAccessible(true);
        for(String id:new String[]{"haymaker","hk21","intervention","olympia","peacekeeper","storm","superbow","svt40","type100"}){
            var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool",id));if(item==null||item==net.minecraft.world.item.Items.AIR)continue;
            var plain=new net.minecraft.world.item.ItemStack(item);var base=mc.getItemRenderer().getModel(plain,mc.level,mc.player,0);var stack=plain.copy();stack.getOrCreateTag().putString("zr_camo","camo_gold");
            var coated=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);var original=base.getQuads(null,null,net.minecraft.util.RandomSource.create(42));var quads=coated.getQuads(null,null,net.minecraft.util.RandomSource.create(42));
            check(!original.isEmpty()&&quads.containsAll(original)&&quads.size()>original.size(),"Camo retains every original gun surface under its coating: "+id);
            check(coated==mc.getItemRenderer().getModel(stack,mc.level,mc.player,0),"Camo reuses cached geometry across frames: "+id);
            var sprite=original.get(0).getSprite().contents().name();var mask=maskMethod.invoke(null,sprite);var opaque=mask.getClass().getDeclaredField("opaque");opaque.setAccessible(true);boolean[][] pixels=(boolean[][])opaque.get(mask);
            try(var input=mc.getResourceManager().getResource(new ResourceLocation(sprite.getNamespace(),"textures/"+sprite.getPath()+".png")).orElseThrow().open();var img=NativeImage.read(input)){
                boolean clean=true;for(int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++)if(pixels[x][y]&&(img.getPixelRGBA(x,y)>>>24)<192)clean=false;
                check(clean,"Camo excludes faint antialias pixels from its painted silhouette: "+id);
            }
        }
        var owner=mc.player;var tick=new net.minecraftforge.event.TickEvent.ClientTickEvent(net.minecraftforge.event.TickEvent.Phase.END);
        me.cryo.zombierool.client.screens.TeleporterChargeOverlay.start(120);check(me.cryo.zombierool.client.screens.TeleporterChargeOverlay.active(),"Teleporter charge starts normally");
        int oldTicks=owner.tickCount;owner.tickCount=0;me.cryo.zombierool.client.screens.TeleporterChargeOverlay.tick(tick);check(me.cryo.zombierool.client.screens.TeleporterChargeOverlay.active(),"Teleport charge lifetime uses world time rather than the reset player tick counter");owner.tickCount=oldTicks;
        int[] callbacks={0};me.cryo.zombierool.client.ThirdPersonAnimHandler.startAnim(owner.getUUID(),"grenade_throw",15,()->callbacks[0]++);
        try{mc.player=null;me.cryo.zombierool.client.screens.TeleporterChargeOverlay.tick(tick);me.cryo.zombierool.client.ThirdPersonAnimHandler.onClientTick(tick);}finally{mc.player=owner;}
        check(!me.cryo.zombierool.client.screens.TeleporterChargeOverlay.active()&&me.cryo.zombierool.client.ThirdPersonAnimHandler.currentAnim(owner.getUUID())==null&&callbacks[0]==0,"Losing the old respawn player clears teleport effects and grenade animation without a throw callback");
    }
    private static void preview(Minecraft mc,CerberusSystem.Head dog)throws Exception{
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(512,256,true,Minecraft.ON_OSX);target.bindWrite(true);
        org.lwjgl.opengl.GL11.glClearColor(.04f,.025f,.03f,1);org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        var projection=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());var sorting=com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,512,256,0,1000,21000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        var view=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();view.translate(0,0,-10000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        var gui=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());gui.drawString(mc.font,"CERBERUS / WALL MARK",12,12,0xFFE6C5);gui.pose().pushPose();gui.pose().translate(40,64,0);gui.pose().scale(6,6,6);gui.renderItem(new net.minecraft.world.item.ItemStack(MapAuthorSystem.CERBERUS.get()),0,0);gui.pose().popPose();gui.flush();
        dog.completed=false;dog.revealed=true;dog.phase=2;
        dog.appearedAt=mc.level.getGameTime()-80;dog.phaseStarted=mc.level.getGameTime()-8;
        mc.level.setBlock(BlockPos.ZERO,dog.getBlockState(),3);var attached=(CerberusSystem.Head)mc.level.getBlockEntity(BlockPos.ZERO);attached.phase=2;attached.phaseStarted=dog.phaseStarted;
        var pose=new PoseStack();pose.translate(270,180,0);pose.scale(65,-65,65);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(12));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(152));
        var buffers=mc.renderBuffers().bufferSource();mc.getBlockEntityRenderDispatcher().getRenderer(dog).render(dog,.5f,pose,buffers,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);buffers.endBatch();
        var body=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(mc.level);body.setHealth(0);body.deathTime=19;body.setCerberusFeeding(true);
        var facing=dog.getBlockState().getValue(CerberusSystem.WallBlock.FACING);Vec3 point=dog.feedingPosition(body,8);body.setPos(point);body.setYRot(facing.toYRot()-(float)Math.toDegrees(CerberusSystem.headYaw(2,8)));body.setXRot(-90-(float)Math.toDegrees(CerberusSystem.headPitch(2,8)));body.xRotO=body.getXRot();
        body.setCerberusHead(BlockPos.ZERO);body.xOld=point.x;body.yOld=point.y;body.zOld=point.z;
        var corpseRenderer=mc.getEntityRenderDispatcher().getRenderer(body);
        for(float partial:new float[]{.1f,.5f,.9f}){Vec3 draw=point.add(corpseRenderer.getRenderOffset(body,partial));Vec3 targetPoint=attached.feedingPosition(body,8+partial);check(draw.distanceToSqr(targetPoint)<.000001,"Corpse stays clamped to the jaws between entity packets at fraction "+partial);}
        var offset=corpseRenderer.getRenderOffset(body,.5f);pose.pushPose();pose.translate(point.x+offset.x,point.y+offset.y,point.z+offset.z);corpseRenderer.render(body,body.getYRot(),.5f,pose,buffers,15728880);pose.popPose();buffers.endBatch();
        try(var image=new NativeImage(512,256,false)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/cerberus-preview.png"));}
        view.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
    }

}
