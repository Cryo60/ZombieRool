package local.hotfixtest;



import com.mojang.blaze3d.platform.NativeImage;

import com.mojang.blaze3d.systems.RenderSystem;

import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import net.minecraft.resources.ResourceLocation;

import me.cryo.zombierool.init.ZombieroolModEntities;

import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;

import org.lwjgl.opengl.GL11;



public final class SpecialEnemyClientTest {

    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("SPECIAL CLIENT CHECK: "+text);}
    private static final class ObjVertices implements VertexConsumer {
        final StringBuilder data=new StringBuilder();int count;double x,y,z;float u,v;
        public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
        public void endVertex(){
            data.append("v ").append(x).append(' ').append(y).append(' ').append(z).append('\n');
            data.append("vt ").append(u).append(' ').append(1-v).append('\n');count++;
            if(count%4==0){data.append("f");for(int i=count-3;i<=count;i++)data.append(' ').append(i).append('/').append(i);data.append('\n');}
        }
    }
    private static void exportModel(me.cryo.zombierool.client.renderer.MinecraftBossRenderer renderer,me.cryo.zombierool.entity.SpecialEnemyEntity entity,int choice)throws Exception{
        var vertices=new ObjVertices();var pose=new PoseStack();float scale=entity.isPanzer()?1.45f:1.35f;pose.scale(scale,-scale,-scale);pose.translate(0,-1.5,0);
        renderer.getModel().setupAnim(entity,0,0,0,0,0);renderer.getModel().renderToBuffer(pose,vertices,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,1,1,1,1);
        check(vertices.count>200&&vertices.count%4==0,"Exported actual cuboid geometry "+entity.kind()+" "+choice);
        var folder=java.nio.file.Path.of("D:/Dev/ZombieRool/build/boss-assets/model-choices");java.nio.file.Files.createDirectories(folder);
        String name=entity.kind()+"-"+choice,texture=renderer.getTextureLocation(entity).getPath();texture=texture.substring(texture.lastIndexOf('/')+1);
        java.nio.file.Files.writeString(folder.resolve(name+".obj"),"# Original Minecraft cuboid boss, units: blocks\nmtllib "+name+".mtl\nusemtl boss\n"+vertices.data);
        java.nio.file.Files.writeString(folder.resolve(name+".mtl"),"newmtl boss\nKd 1 1 1\nKa 1 1 1\nillum 1\nmap_Kd "+texture+"\n");
        java.nio.file.Files.copy(java.nio.file.Path.of("D:/Dev/ZombieRool/src/main/resources/assets/zombierool/textures/entities/"+texture),folder.resolve(texture),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void canvas(Minecraft mc,String file,java.util.function.Consumer<GuiGraphics> draw)throws Exception{

        var target=new com.mojang.blaze3d.pipeline.TextureTarget(640,480,true,Minecraft.ON_OSX);target.bindWrite(true);

        GL11.glClearColor(.06f,.07f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);

        var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();

        RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,640,480,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);

        var view=RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();view.translate(0,0,-10000);RenderSystem.applyModelViewMatrix();

        try{

            var gui=new GuiGraphics(mc,mc.renderBuffers().bufferSource());draw.accept(gui);gui.flush();mc.renderBuffers().bufferSource().endBatch();

            try(var image=new NativeImage(640,480,true)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/boss-assets/"+file+".png"));}

            check(GL11.glGetError()==GL11.GL_NO_ERROR,"No GL error rendering "+file);

        }finally{view.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}

    }

    public static void run()throws Exception{

        var mc=Minecraft.getInstance();mc.getWindow().setGuiScale(1);

        var target=ZombieroolModEntities.ZOMBIE.get().create(mc.level);target.setPos(1600,80,0);target.setDeltaMovement(0,.2,0);
        var bullet=new com.tacz.guns.entity.EntityKineticBullet(com.tacz.guns.init.ModEntities.BULLET.get(),mc.level);bullet.setOwner(mc.player);
        var hitMethod=com.tacz.guns.util.EntityUtil.class.getDeclaredMethod("getHitResult",net.minecraft.world.entity.projectile.Projectile.class,net.minecraft.world.entity.Entity.class,net.minecraft.world.phys.Vec3.class,net.minecraft.world.phys.Vec3.class);hitMethod.setAccessible(true);
        var offset=com.tacz.guns.util.HitboxHelper.getFixedBoundingBox(target,mc.player).getCenter().subtract(target.getBoundingBox().getCenter());
        var aim=target.position().add(offset).add(0,target.getEyeHeight(),0);
        var hit=(com.tacz.guns.entity.EntityKineticBullet.EntityResult)hitMethod.invoke(null,bullet,target,aim.add(0,0,-5),aim.add(0,0,5));
        check(hit!=null&&hit.isHeadshot(),"Real TacZ collision classifies the compensated moving zombie head correctly");
        aim=target.position().add(offset).add(0,.6,0);hit=(com.tacz.guns.entity.EntityKineticBullet.EntityResult)hitMethod.invoke(null,bullet,target,aim.add(0,0,-5),aim.add(0,0,5));
        check(hit!=null&&!hit.isHeadshot(),"TacZ body hits remain body hits with the same compensation");
        var types=java.util.List.of(ZombieroolModEntities.PANZER.get(),ZombieroolModEntities.BRUTUS.get(),ZombieroolModEntities.NAPALM_MINION.get(),ZombieroolModEntities.SKELETON_MINION.get());

        var mobs=new java.util.ArrayList<me.cryo.zombierool.entity.SpecialEnemyEntity>();

        for(var type:types){

            var e=type.create(mc.level);mobs.add(e);var renderer=mc.getEntityRenderDispatcher().getRenderer(e);var skin=renderer.getTextureLocation(e);

            try(var input=mc.getResourceManager().getResource(skin).orElseThrow().open();var image=NativeImage.read(input)){

                check(e.isBoss()?image.getWidth()==256&&image.getHeight()==256:e.kind().equals("skeleton_minion")?image.getWidth()==64&&image.getHeight()==32:image.getWidth()==128,"Loaded texture "+type.toShortString());

            }

            if(e.isBoss())check(renderer instanceof me.cryo.zombierool.client.renderer.MinecraftBossRenderer,"Articulated Minecraft cuboid renderer "+e.kind());
            e.hurtTime=10;check(net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(e,0)==net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,"Special damage does not flash red: "+e.kind());e.hurtTime=0;

        }

        for(boolean panzer:new boolean[]{true,false}){
            var root=me.cryo.zombierool.client.renderer.MinecraftBossRenderer.layer(panzer).bakeRoot();
            check(root.hasChild("torso")&&!root.hasChild("body"),"Boss has a custom armature rather than vanilla Steve parts");
            var model=new me.cryo.zombierool.client.renderer.MinecraftBossRenderer.BossModel(root,panzer);
            model.setupAnim(mobs.get(panzer?0:1),2,.8f,0,0,0);
            check(Math.abs(root.getChild("left_leg").getChild("shin").xRot)>0||Math.abs(root.getChild("right_leg").getChild("shin").xRot)>0,"Boss knees articulate during walking");
        }
        canvas(mc,"boss-three-views",gui->{for(int row=0;row<2;row++)for(int col=0;col<3;col++){
            var e=mobs.get(row);e.yBodyRot=180;e.setYRot(180);e.yHeadRot=180;e.yBodyRotO=180;e.yHeadRotO=180;
            var rotation=com.mojang.math.Axis.ZP.rotationDegrees(180);rotation.mul(com.mojang.math.Axis.YP.rotationDegrees(col==0?0:col==1?55:180));
            InventoryScreen.renderEntityInInventory(gui,105+col*210,210+row*230,64,rotation,null,e);
            gui.drawCenteredString(mc.font,(row==0?"Panzer":"Brutus")+" / "+(col==0?"front":col==1?"3D":"back"),105+col*210,220+row*230,0xFFEEEEEE);
        }});
        var renderersField=net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredField("renderers");renderersField.setAccessible(true);
        var originalRenderers=renderersField.get(mc.getEntityRenderDispatcher());
        var context=new net.minecraft.client.renderer.entity.EntityRendererProvider.Context(mc.getEntityRenderDispatcher(),mc.getItemRenderer(),mc.getBlockRenderer(),mc.gameRenderer.itemInHandRenderer,mc.getResourceManager(),mc.getEntityModels(),mc.font);
        try{
            for(int row=0;row<2;row++)for(int choice=0;choice<3;choice++){
                var e=mobs.get(row);final int number=choice+1;
                var replacement=new java.util.HashMap<>((java.util.Map<net.minecraft.world.entity.EntityType<?>,net.minecraft.client.renderer.entity.EntityRenderer<?>>)originalRenderers);
                var proposal=new me.cryo.zombierool.client.renderer.MinecraftBossRenderer(context,e.kind(),choice);exportModel(proposal,e,number);
                replacement.put(e.getType(),proposal);renderersField.set(mc.getEntityRenderDispatcher(),replacement);
                canvas(mc,e.kind()+"-choice-"+number,gui->{
                    gui.drawCenteredString(mc.font,(e.isPanzer()?"Panzer":"Brutus")+" "+number,320,18,0xFFEEEEEE);
                    for(int col=0;col<3;col++){
                        e.yBodyRot=180;e.setYRot(180);e.yHeadRot=180;e.yBodyRotO=180;e.yHeadRotO=180;
                        var rotation=com.mojang.math.Axis.ZP.rotationDegrees(180);rotation.mul(com.mojang.math.Axis.YP.rotationDegrees(col==0?0:col==1?55:180));
                        InventoryScreen.renderEntityInInventory(gui,105+col*210,345,85,rotation,null,e);
                        gui.drawCenteredString(mc.font,col==0?"Face":col==1?"3/4":"Dos",105+col*210,370,0xFFEEEEEE);
                    }
                });
            }
            for(int kind=0;kind<2;kind++){
                var e=mobs.get(kind);
                canvas(mc,e.kind()+"-choices",gui->{
                    for(int choice=0;choice<3;choice++){
                        try{
                            var replacement=new java.util.HashMap<>((java.util.Map<net.minecraft.world.entity.EntityType<?>,net.minecraft.client.renderer.entity.EntityRenderer<?>>)originalRenderers);
                            replacement.put(e.getType(),new me.cryo.zombierool.client.renderer.MinecraftBossRenderer(context,e.kind(),choice));renderersField.set(mc.getEntityRenderDispatcher(),replacement);
                        }catch(Exception failure){throw new RuntimeException(failure);}
                        for(int view=0;view<2;view++){
                            var rotation=com.mojang.math.Axis.ZP.rotationDegrees(180);rotation.mul(com.mojang.math.Axis.YP.rotationDegrees(view==0?0:55));
                            InventoryScreen.renderEntityInInventory(gui,105+choice*210,205+view*230,58,rotation,null,e);
                            gui.drawCenteredString(mc.font,(e.isPanzer()?"Panzer":"Brutus")+" "+(choice+1)+" / "+(view==0?"Face":"3/4"),105+choice*210,215+view*230,0xFFEEEEEE);
                        }
                    }
                });
            }
        }finally{renderersField.set(mc.getEntityRenderDispatcher(),originalRenderers);}
        var variant=ZombieroolModEntities.CUSTOM_MINI_BOSS.get().create(mc.level);var profile=new net.minecraft.nbt.CompoundTag();profile.putString("id","test_addon:warden");profile.putString("template","brutus");profile.putString("name","Warden");variant.setBossProfile(profile);variant.setEmergenceTicks(0);
        check(mc.getEntityRenderDispatcher().getRenderer(variant) instanceof me.cryo.zombierool.client.renderer.CustomMiniBossRenderer,"Datapack boss uses its registered template renderer");canvas(mc,"custom-boss-template",g->InventoryScreen.renderEntityInInventoryFollowsMouse(g,320,360,110,-25,0,variant));
        var chainRoot=mc.player.blockPosition().offset(2,0,0);
        var perkState=me.cryo.zombierool.block.system.PerksSystem.BLOCK.get().defaultBlockState();
        mc.level.setBlock(chainRoot,perkState,3);
        mc.level.setBlock(chainRoot.below(),me.cryo.zombierool.block.system.PerksSystem.DUMMY_BLOCK.get().defaultBlockState().setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART,me.cryo.zombierool.block.system.PerksSystem.DummyPart.LOWER),3);
        mc.level.setBlock(chainRoot.above(),me.cryo.zombierool.block.system.PerksSystem.DUMMY_BLOCK.get().defaultBlockState().setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART,me.cryo.zombierool.block.system.PerksSystem.DummyPart.UPPER),3);
        check(me.cryo.zombierool.client.BrutusChains.parts(mc.level,chainRoot).size()==3,"Chains cover the perk's main block and both dummy parts");
        for(var direction:java.util.List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
            var state=me.cryo.zombierool.block.system.MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING,direction);
            mc.level.setBlock(chainRoot,state,3);var other=me.cryo.zombierool.gameplay.MysteryBoxManager.getOtherPartPos(chainRoot,direction);
            mc.level.setBlock(other,state.setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.PART,true),3);
            check(me.cryo.zombierool.client.BrutusChains.parts(mc.level,chainRoot).contains(other),"Both mystery box parts are chained facing "+direction);
            check(me.cryo.zombierool.gameplay.BrutusMachineLocks.machineRoot(mc.level,other).equals(chainRoot),"Either box half resolves the same unlock action facing "+direction);
            mc.level.setBlock(other,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        }
        for(var pos:java.util.List.of(chainRoot,chainRoot.above(),chainRoot.below()))mc.level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        var locks=new net.minecraft.nbt.CompoundTag();locks.putLongArray("positions",new long[]{chainRoot.asLong()});me.cryo.zombierool.client.BrutusChains.receive(locks);
        var requested=me.cryo.zombierool.client.BrutusUnlockClient.class.getDeclaredField("requested");requested.setAccessible(true);requested.set(null,chainRoot);me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.setDown(true);
        var progress=new net.minecraft.nbt.CompoundTag();progress.putBoolean("unlockAction",true);progress.putInt("elapsed",30);progress.putInt("duration",60);progress.putLong("position",chainRoot.asLong());me.cryo.zombierool.client.BrutusChains.receive(progress);
        check(me.cryo.zombierool.client.BrutusUnlockClient.active()&&me.cryo.zombierool.client.BrutusChains.locked(chainRoot),"Unlock progress activates hand hiding without clearing machine lock state");
        canvas(mc,"brutus-unlock-progress",g->me.cryo.zombierool.client.BrutusUnlockClient.draw(g,640,480));
        progress.putInt("elapsed",-1);me.cryo.zombierool.client.BrutusChains.receive(progress);requested.set(null,null);me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.setDown(false);
        check(!me.cryo.zombierool.client.BrutusUnlockClient.active(),"Cancelled or completed unlocking restores normal hands");
        me.cryo.zombierool.client.BrutusChains.receive(new net.minecraft.nbt.CompoundTag());
        canvas(mc,"brutus-machine-chains",g->{var pose=g.pose();pose.pushPose();pose.translate(320,330,150);pose.scale(130,-130,130);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(35));mc.getBlockRenderer().renderSingleBlock(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),pose,mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);me.cryo.zombierool.client.BrutusChains.draw(mc,pose,false);pose.popPose();});
        var input=new net.minecraft.client.player.Input();input.forwardImpulse=1;input.leftImpulse=1;input.jumping=true;me.cryo.zombierool.client.PanzerCaptureInput.caught=true;me.cryo.zombierool.client.PanzerCaptureInput.input(new net.minecraftforge.client.event.MovementInputUpdateEvent(mc.player,input));check(input.forwardImpulse==0&&input.leftImpulse==0&&!input.jumping,"Panzer capture disables movement input while preserving combat controls");me.cryo.zombierool.client.PanzerCaptureInput.caught=false;
        var fire=ZombieroolModEntities.NAPALM_FIRE.get().create(mc.level);fire.setPos(mc.player.position());
        canvas(mc,"napalm-hud-preview",gui->me.cryo.zombierool.client.NapalmMinionOverlay.renderHeat(gui,.9f,40,640,480));
        canvas(mc,"napalm-fire-preview",gui->{gui.pose().pushPose();gui.pose().translate(320,300,150);gui.pose().scale(44,-44,44);gui.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(35));gui.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(35));mc.getEntityRenderDispatcher().render(fire,0,0,0,0,0,gui.pose(),mc.renderBuffers().bufferSource(),15728880);gui.pose().popPose();});
        var bone=ZombieroolModEntities.GORE_PIECE.get().create(mc.level);var nbt=new net.minecraft.nbt.CompoundTag();bone.saveWithoutId(nbt);nbt.putByte("Kind",me.cryo.zombierool.entity.GorePieceEntity.BONE_SKULL);bone.load(nbt);
        check(mc.getEntityRenderDispatcher().getRenderer(bone).getTextureLocation(bone).getPath().endsWith("bone_gibs.png"),"Skeleton skull uses a bone atlas rather than bloody zombie skin");
        canvas(mc,"bone-gibs-preview",gui->{gui.pose().pushPose();gui.pose().translate(320,230,150);gui.pose().scale(180,-180,180);mc.getEntityRenderDispatcher().render(bone,0,0,0,0,0,gui.pose(),mc.renderBuffers().bufferSource(),15728880);gui.pose().popPose();});
        canvas(mc,"boss-models",gui->{gui.drawCenteredString(mc.font,"Minecraft 3D models",320,18,0xFFDDDDDD);String[] labels={"Panzer","Brutus","Brasero","Squelette vanilla"};for(int i=0;i<mobs.size();i++){int x=80+i*160;InventoryScreen.renderEntityInInventoryFollowsMouse(gui,x,270,75,-25,0,mobs.get(i));gui.drawCenteredString(mc.font,labels[i],x,305,0xFFEEEEEE);}});

        for(int frame:new int[]{10,40,80}){for(var mob:mobs){mob.tickCount=frame;mob.walkAnimation.update(.25f,1);}

            canvas(mc,"boss-animation-"+frame,gui->{for(int i=0;i<2;i++)InventoryScreen.renderEntityInInventoryFollowsMouse(gui,180+i*280,300,90,-25,0,mobs.get(i));});}

        var panzer=mobs.get(0);
        for(String animation:new String[]{"spawn","land","grab","flame"}){
            for(var name:java.util.List.of("BOSS_ANIMATION","ANIMATION_START","ANIMATION_LENGTH")){
                var field=me.cryo.zombierool.entity.SpecialEnemyEntity.class.getDeclaredField(name);field.setAccessible(true);var key=(net.minecraft.network.syncher.EntityDataAccessor)field.get(null);
                panzer.getEntityData().set(key,name.equals("BOSS_ANIMATION")?animation:name.equals("ANIMATION_START")?(Object)Long.valueOf(mc.level.getGameTime()-12):(Object)Integer.valueOf(animation.equals("spawn")?48:animation.equals("land")?28:40));
            }
            panzer.tickCount=12;
            canvas(mc,"panzer-t6-"+animation,gui->{
                gui.drawCenteredString(mc.font,"Panzer / T6 "+animation,320,20,0xFFEEEEEE);
                var rotation=com.mojang.math.Axis.ZP.rotationDegrees(180);rotation.mul(com.mojang.math.Axis.YP.rotationDegrees(55));
                InventoryScreen.renderEntityInInventory(gui,200,350,80,rotation,null,panzer);
            });
        }
        for(int frame:new int[]{0,8,16,24,32,40,47}){
            for(String name:new String[]{"BOSS_ANIMATION","ANIMATION_START","ANIMATION_LENGTH"}){
                var field=me.cryo.zombierool.entity.SpecialEnemyEntity.class.getDeclaredField(name);field.setAccessible(true);var key=(net.minecraft.network.syncher.EntityDataAccessor)field.get(null);
                panzer.getEntityData().set(key,name.equals("BOSS_ANIMATION")?"spawn":name.equals("ANIMATION_START")?(Object)Long.valueOf(mc.level.getGameTime()-frame):(Object)Integer.valueOf(48));
            }
            panzer.tickCount=frame;
            canvas(mc,"panzer-t6-fall-"+frame,gui->{var rotation=com.mojang.math.Axis.ZP.rotationDegrees(180);rotation.mul(com.mojang.math.Axis.YP.rotationDegrees(55));InventoryScreen.renderEntityInInventory(gui,320,350,80,rotation,null,panzer);});
        }
        var animField=me.cryo.zombierool.entity.SpecialEnemyEntity.class.getDeclaredField("BOSS_ANIMATION");animField.setAccessible(true);panzer.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor)animField.get(null),"idle");
        for(var block:java.util.List.of(me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINI_BOSS_SPAWNER_BLOCK.get(),me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINION_SPAWNER_BLOCK.get())){
            var stack=new net.minecraft.world.item.ItemStack(block);
            var inventory=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            var placed=mc.getBlockRenderer().getBlockModel(block.defaultBlockState());
            check(inventory.getParticleIcon().contents().name().equals(placed.getParticleIcon().contents().name()),"Inventory and placed spawner use the same texture "+block.getDescriptionId());
        }
        canvas(mc,"spawner-items",gui->{gui.pose().pushPose();gui.pose().translate(240,120,0);gui.pose().scale(5,5,5);gui.renderItem(new net.minecraft.world.item.ItemStack(me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINI_BOSS_SPAWNER_ITEM.get()),0,0);gui.renderItem(new net.minecraft.world.item.ItemStack(me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINION_SPAWNER_ITEM.get()),24,0);gui.pose().popPose();});
        var pos=mc.player.blockPosition().offset(0,0,8);

        mc.level.setBlock(pos,me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINI_BOSS_SPAWNER_BLOCK.get().defaultBlockState(),3);

        var be=(UniversalSpawnerBlockEntity)mc.level.getBlockEntity(pos);

        be.setConfig(SpawnerMobType.PANZER,"","0","0",false,1);

        var menu=new UniversalSpawnerManagerMenu(999,mc.player.getInventory(),new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()).writeBlockPos(pos));

        var screen=new UniversalSpawnerManagerScreen(menu,mc.player.getInventory(),be.getDisplayName());

        var previous=mc.screen;

        try{

            mc.setScreen(screen);screen.init(mc,640,480);

            check(screen.children().stream().noneMatch(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("...")),"The obsolete spawner submenu button is removed");

            long visible=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox e&&e.visible).count();check(visible==4,"Boss spawner keeps its four local fields; special-enemy rules belong to map config");

            canvas(mc,"spawner-settings",gui->screen.render(gui,0,0,0));
            var oldLanguage=net.minecraft.locale.Language.getInstance();
            try{
                net.minecraft.locale.Language.inject(net.minecraft.client.resources.language.ClientLanguage.loadFrom(mc.getResourceManager(),java.util.List.of("en_us","fr_fr"),false));
                check(!net.minecraft.network.chat.Component.translatable("gui.zombierool.spawner.FirstWave").getString().equals("First boss round:"),"French language changes labels through translation keys");
                canvas(mc,"spawner-settings-fr",gui->screen.render(gui,0,0,0));
            }finally{net.minecraft.locale.Language.inject(oldLanguage);}
            mc.level.setBlock(pos,me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINION_SPAWNER_BLOCK.get().defaultBlockState(),3);
            var minionBe=(UniversalSpawnerBlockEntity)mc.level.getBlockEntity(pos);
            var minionScreen=new UniversalSpawnerManagerScreen(menu,mc.player.getInventory(),minionBe.getDisplayName());mc.setScreen(minionScreen);minionScreen.init(mc,640,480);
            var select=(net.minecraft.client.gui.components.CycleButton<?>)minionScreen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.CycleButton<?>).findFirst().orElseThrow();
            check(select.getValue()==SpawnerMobType.NAPALM_MINION,"Minion UI starts on napalm");select.onPress();check(select.getValue()==SpawnerMobType.SKELETON_MINION,"Minion UI selects skeleton");select.onPress();check(select.getValue()==SpawnerMobType.NAPALM_MINION,"Minion selector contains exactly its two types");
            check(minionScreen.children().stream().noneMatch(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("...")),"The minion spawner also has no obsolete submenu");
            check(minionScreen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox e&&e.visible).count()==4,"Napalm spawner delegates advanced spawn rules to map config");
            canvas(mc,"minion-settings",gui->minionScreen.render(gui,0,0,0));

        }finally{mc.setScreen(previous);mc.level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);}

        var coatedGun=new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));coatedGun.getOrCreateTag().putString("GunId","tacz:ai_awp");coatedGun.getOrCreateTag().putString("zr_camo","camo_bubbles");
        for(boolean upgraded:new boolean[]{false,true}){coatedGun.getOrCreateTag().putBoolean("zombierool:pap",upgraded);canvas(mc,upgraded?"tacz-camo-foil":"tacz-camo-plain",g->{var pose=g.pose();pose.pushPose();pose.translate(320,260,150);pose.scale(210,-210,210);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(65));mc.getItemRenderer().renderStatic(coatedGun,net.minecraft.world.item.ItemDisplayContext.GROUND,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,mc.renderBuffers().bufferSource(),mc.level,0);pose.popPose();});}
        try(var plain=NativeImage.read(java.nio.file.Files.newInputStream(java.nio.file.Path.of("D:/Dev/ZombieRool/build/boss-assets/tacz-camo-plain.png")));var upgraded=NativeImage.read(java.nio.file.Files.newInputStream(java.nio.file.Path.of("D:/Dev/ZombieRool/build/boss-assets/tacz-camo-foil.png")))){int changed=0;for(int y=0;y<480;y++)for(int x=0;x<640;x++)if(plain.getPixelRGBA(x,y)!=upgraded.getPixelRGBA(x,y))changed++;check(changed>100,"Actual TacZ gun mesh gains a visible upgraded sheen over its existing camo: "+changed+" pixels");}

        var mapTag=new net.minecraft.nbt.CompoundTag();mapTag.put("specialEnemies",me.cryo.zombierool.config.SpecialEnemyConfig.defaults());mapTag.putString("focusTab","enemies");var lootTables=new net.minecraft.nbt.ListTag();lootTables.add(net.minecraft.nbt.StringTag.valueOf("zombierool_test:boss_first"));lootTables.add(net.minecraft.nbt.StringTag.valueOf("zombierool_test:boss_recurring"));mapTag.put("availableBossLootTables",lootTables);
        var previousMapScreen=mc.screen;var addonSchema=new net.minecraft.nbt.CompoundTag();addonSchema.putString("category","enemies");var addonDefaults=new net.minecraft.nbt.CompoundTag();addonDefaults.putDouble("addonHealth",1.5);addonDefaults.putBoolean("enabled",true);addonSchema.put("defaults",addonDefaults);var schemas=new net.minecraft.nbt.CompoundTag();schemas.put("test_addon:extra_enemy",addonSchema);mapTag.put("mapConfigSchemas",schemas);var configScreen=new me.cryo.zombierool.client.gui.WorldConfigMenuScreen(new me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket(mapTag,false,1));
        try{
            mc.setScreen(configScreen);configScreen.init(mc,640,480);canvas(mc,"map-config-enemies",g->configScreen.render(g,0,0,0));
            var listField=configScreen.getClass().getDeclaredField("listWidget");listField.setAccessible(true);var settingsList=(net.minecraft.client.gui.components.AbstractSelectionList<?>)listField.get(configScreen);
            check(settingsList.children().size()<20,"Enemy sections initially appear collapsed");Object header=settingsList.children().get(0);var headerButton=header.getClass().getDeclaredField("btn");headerButton.setAccessible(true);var heading=(net.minecraft.client.gui.components.Button)headerButton.get(header);check(heading.getWidth()>=mc.font.width(heading.getMessage())+10,"Fold headings resize to display the full translated title");var expanded=configScreen.getClass().getDeclaredField("expandedCategories");expanded.setAccessible(true);((java.util.Set<String>)expanded.get(configScreen)).addAll(java.util.List.of("enemy:boss","enemy:transition","enemy:loot","enemy:balance","enemy:registered:panzer","enemy:registered:brutus","enemy:napalm","enemy:skeleton","enemy:addon:test_addon:extra_enemy"));var refresh=configScreen.getClass().getDeclaredMethod("refreshList");refresh.setAccessible(true);refresh.invoke(configScreen);settingsList=(net.minecraft.client.gui.components.AbstractSelectionList<?>)listField.get(configScreen);canvas(mc,"map-config-enemies-expanded",g->configScreen.render(g,0,0,0));
            check(settingsList.children().size()>=40,"Map menu exposes boss loot, balance and both minion spawn rule sections");
            check(settingsList.children().stream().anyMatch(entry->{try{var f=((Object)entry).getClass().getDeclaredField("label");f.setAccessible(true);return ((net.minecraft.network.chat.Component)f.get(entry)).getString().equals("addonHealth");}catch(ReflectiveOperationException ex){return false;}}),"Server-declared addon settings appear dynamically in the enemy tab");
            var inventoryOriginal=mc.player.getInventory().getItem(9).copy();var inventoryItem=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3);inventoryItem.getOrCreateTag().putString("QuestKey","warden");inventoryItem.setHoverName(net.minecraft.network.chat.Component.literal("Warden quest key"));mc.player.getInventory().setItem(9,inventoryItem);
            final net.minecraft.world.item.ItemStack[] slotResult={net.minecraft.world.item.ItemStack.EMPTY};var slotScreen=new me.cryo.zombierool.client.gui.MapItemSlotScreen(configScreen,net.minecraft.network.chat.Component.translatable("config.zombierool.enemies.firstLoot"),net.minecraft.world.item.ItemStack.EMPTY,item->slotResult[0]=item);
            mc.setScreen(slotScreen);slotScreen.init(mc,640,480);slotScreen.mouseClicked(240,231,0);slotScreen.mouseReleased(320,200,0);canvas(mc,"map-config-item-slot",g->slotScreen.render(g,0,0,0));
            ((net.minecraft.client.gui.components.Button)slotScreen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().equals(net.minecraft.network.chat.Component.translatable("config.zombierool.apply"))).findFirst().orElseThrow()).onPress();
            check(net.minecraft.world.item.ItemStack.matches(slotResult[0],inventoryItem)&&net.minecraft.world.item.ItemStack.matches(mc.player.getInventory().getItem(9),inventoryItem)&&mc.screen==configScreen,"Drag-to-config copies full NBT and count without consuming inventory");mc.player.getInventory().setItem(9,inventoryOriginal);
            check(me.cryo.zombierool.client.career.CareerUnlockables.CAMOS.get("camo_wesker_coat").rarity.equals("legendary")&&mc.getResourceManager().getResource(new ResourceLocation("zombierool:textures/item/camos/wesker_coat.png")).isPresent(),"Wesker Coat is a legendary unlock with a loaded texture");
            var papDefinition=me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.values().stream().filter(d->d.pap!=null&&d.pap.name!=null&&d.pap.name.startsWith("weapon.")).findFirst().orElseThrow();var namedGun=coatedGun.copy();me.cryo.zombierool.integration.TacZIntegration.applyTaczPap(namedGun,papDefinition);
            check(namedGun.getHoverName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents&&namedGun.getHoverName().getString().equals(net.minecraft.network.chat.Component.translatable(papDefinition.pap.name).getString()),"TacZ PAP item names resolve through language components");
            var originalLanguage=net.minecraft.locale.Language.getInstance();try{net.minecraft.locale.Language.inject(net.minecraft.client.resources.language.ClientLanguage.loadFrom(mc.getResourceManager(),java.util.List.of("en_us","fr_fr"),false));configScreen.init(mc,640,480);canvas(mc,"map-config-enemies-fr",g->configScreen.render(g,0,0,0));settingsList.setScrollAmount(450);canvas(mc,"map-config-balance-fr",g->configScreen.render(g,0,0,0));}finally{net.minecraft.locale.Language.inject(originalLanguage);}
            final String[] selected={""};var picker=new me.cryo.zombierool.client.gui.BossLootTableScreen(configScreen,java.util.List.of("zombierool_test:boss_first","zombierool_test:boss_recurring"),"",id->selected[0]=id);mc.setScreen(picker);picker.init(mc,640,480);canvas(mc,"map-config-loot-picker",g->picker.render(g,0,0,0));
            var search=(net.minecraft.client.gui.components.EditBox)picker.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();search.setValue("zombierool_test:boss_recurring");var save=(net.minecraft.client.gui.components.Button)picker.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().equals(net.minecraft.network.chat.Component.translatable("gui.zombierool.config.save"))).findFirst().orElseThrow();save.onPress();check(selected[0].equals("zombierool_test:boss_recurring")&&mc.screen==configScreen,"Loot picker selection returns to the map draft without losing its edits");
        }finally{mc.setScreen(previousMapScreen);}

        for(var type:java.util.List.of(ZombieroolModEntities.PANZER.get(),ZombieroolModEntities.BRUTUS.get())){
            var boss=type.create(mc.level);var renderer=(me.cryo.zombierool.client.renderer.MinecraftBossRenderer)mc.getEntityRenderDispatcher().getRenderer(boss);
            var flip=renderer.getClass().getDeclaredMethod("getFlipDegrees",me.cryo.zombierool.entity.SpecialEnemyEntity.class);flip.setAccessible(true);check((float)flip.invoke(renderer,boss)==0,"Boss corpse bypasses vanilla sideways rotation");
            boss.deathTime=30;renderer.getModel().setupAnim(boss,0,0,30,0,0);
            canvas(mc,boss.kind()+"-death-oct13",gui->net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(gui,320,410,100,30,0,boss));
        }
        System.out.println("SPECIAL CLIENT PASS");

    }

}
