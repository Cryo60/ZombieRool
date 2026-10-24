package local.hotfixtest;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import me.cryo.zombierool.client.gui.BlockPowerScreen;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.*;
import net.minecraft.core.BlockPos;
import org.lwjgl.opengl.GL11;

public final class Oct14ClientTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("OCT14 CLIENT CHECK: "+text);}
    private static int[] blood(int copies,String name)throws Exception {
        return blood(copies,name,0);
    }
    private static int[] blood(int copies,String name,int variant)throws Exception {
        var mc=Minecraft.getInstance();var target=new com.mojang.blaze3d.pipeline.TextureTarget(256,256,true,Minecraft.ON_OSX);target.bindWrite(true);
        GL11.glClearColor(.68f,.57f,.4f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();var view=RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(-.65f,.65f,-.65f,.65f,.05f,10),VertexSorting.ORTHOGRAPHIC_Z);
        BlockPos support=mc.player.blockPosition().offset(5,-1,0);var previousBlock=mc.level.getBlockState(support);
        mc.level.setBlock(support,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        try {
            var decal=me.cryo.zombierool.init.ZombieroolModEntities.BLOOD_DECAL.get().create(mc.level);
            decal.setPos(support.getX()+.5,support.getY()+1,support.getZ()+.5);
            var tag=new net.minecraft.nbt.CompoundTag();tag.putFloat("Size",1);tag.putInt("Variant",variant);var read=decal.getClass().getDeclaredMethod("readAdditionalSaveData",net.minecraft.nbt.CompoundTag.class);read.setAccessible(true);read.invoke(decal,tag);
            var pose=new PoseStack();pose.translate(0,0,-1);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));pose.translate(-decal.getX(),-decal.getY(),-decal.getZ());
            var stamp=me.cryo.zombierool.client.BloodSurfaceRenderer.stamp(decal);
            var deposits=new java.util.ArrayList<me.cryo.zombierool.client.BloodSurfaceRenderer.Stamp>();
            for(int i=0;i<copies;i++)deposits.add(new me.cryo.zombierool.client.BloodSurfaceRenderer.Stamp(stamp.origin(),stamp.face(),stamp.pieces(),stamp.color(),stamp.alpha()));
            me.cryo.zombierool.client.BloodSurfaceRenderer.render(deposits,pose,mc.renderBuffers().bufferSource());
            try(var image=new NativeImage(256,256,true)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/"+name+".png"));return image.getPixelsRGBA();}
        } finally {mc.level.setBlock(support,previousBlock,3);view.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
    private static void menu()throws Exception {
        var mc=Minecraft.getInstance();var screen=new BlockPowerScreen(new BlockPowerPackets.Open(BlockPos.ZERO,BlockPowerSettings.Mode.BOTH,false,false));
        screen.init(mc,640,360);check(screen.children().size()==3,"Power menu contains source selection, save and cancel");
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(640,360,true,Minecraft.ON_OSX);target.bindWrite(true);GL11.glClearColor(.1f,.1f,.1f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();var view=RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();view.translate(0,0,-10000);RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,640,360,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
        try {
            var gui=new GuiGraphics(mc,mc.renderBuffers().bufferSource());screen.render(gui,0,0,0);gui.flush();
            try(var image=new NativeImage(640,360,true)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/block-power-menu.png"));}
            var data=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());data.writeBlockPos(BlockPos.ZERO);
            var menu=new me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerManagerMenu(202,mc.player.getInventory(),data);
            var spawner=new me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerManagerScreen(menu,mc.player.getInventory(),net.minecraft.network.chat.Component.translatable("block.zombierool.universal_spawner"));spawner.init(mc,640,360);
            check(spawner.children().stream().filter(widget->widget instanceof net.minecraft.client.gui.components.EditBox).count()==4,"The universal spawner has only its main fields and no advanced settings submenu");
            check(spawner.children().stream().anyMatch(widget->widget instanceof net.minecraft.client.gui.components.CycleButton<?> cycle&&cycle.getValue() instanceof BlockPowerSettings.Mode),"The spawner selects global/local/OR/AND directly in its main menu");
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);spawner.render(gui,0,0,0);gui.flush();
            try(var image=new NativeImage(640,360,true)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/spawner-power-menu.png"));}
        } finally {view.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
    public static void run()throws Exception {
        taczBloodRage();
        dropsIgnoreBarriers();
        var one=blood(1,"blood-cutout-single");var overlap=blood(8,"blood-cutout-overlap");
        int background=one[0],visible=0;for(int color:one)if(color!=background)visible++;
        check(visible>1000&&visible<40000,"Blood has a visible irregular silhouette with a transparent exterior");
        check(!java.util.Arrays.equals(one,overlap),"Separate overlapping deposits increase blood coverage");
        check(me.cryo.zombierool.client.ZrBloodRenderType.texture(0).getPath().equals("textures/particle/blood_drop.png"),"Combat blood restores the texture referenced by the published Git renderer");
        var variants=new java.util.HashSet<Integer>();for(int i=0;i<12;i++)variants.add(java.util.Arrays.hashCode(blood(1,"blood-organic-"+i,i)));
        check(variants.size()>=4,"Blood preserves the hotfix1 palette with several visibly different subtle tints");
        debugClient();
        absorptionHud();
        weaponStatusHud();
        noWallEjection();
        mimicBlood();
        var block=me.cryo.zombierool.init.ZombieroolModBlocks.ALPHA_RECEPTOR.get();
        var player=Minecraft.getInstance().player;var info=player.connection.getPlayerInfo(player.getUUID());
        var modeField=net.minecraft.client.multiplayer.PlayerInfo.class.getDeclaredField("gameMode");modeField.setAccessible(true);var mode=modeField.get(info);
        boolean preview=me.cryo.zombierool.client.LinkRenderer.isSurvivalViewEnabled;me.cryo.zombierool.client.LinkRenderer.isSurvivalViewEnabled=false;
        try {
            modeField.set(info,net.minecraft.world.level.GameType.SURVIVAL);
            check(block.getRenderShape(block.defaultBlockState())==net.minecraft.world.level.block.RenderShape.INVISIBLE,"Signal marker is hidden in survival");
            check(block.getRenderShape(block.defaultBlockState().setValue(SignalTechnicalBlock.SURVIVAL_VISIBLE,true))==net.minecraft.world.level.block.RenderShape.MODEL,"The per-block visibility override works in survival");
            modeField.set(info,net.minecraft.world.level.GameType.CREATIVE);
            check(block.getRenderShape(block.defaultBlockState())==net.minecraft.world.level.block.RenderShape.MODEL,"Map makers see hidden signal markers in creative");
        }finally{modeField.set(info,mode);me.cryo.zombierool.client.LinkRenderer.isSurvivalViewEnabled=preview;}
        menu();preserveDraft();check(GL11.glGetError()==GL11.GL_NO_ERROR,"Blood and power configuration render without GL errors");
    }
    private static void debugClient()throws Exception {
        var mc=Minecraft.getInstance();var player=mc.player;var position=player.position();boolean noclip=DebugPlayerControls.clientNoclip;boolean physics=player.noPhysics;
        int clientWave=WaveManager.getCurrentWave();boolean clientRunning=WaveManager.isGameRunning();
        BlockPos support=player.blockPosition().offset(5,0,0);var old=mc.level.getBlockState(support);var oldUpper=mc.level.getBlockState(support.above());
        try{
            WaveManager.setClientGameRunning(true);
            var serverTask=new java.util.concurrent.FutureTask<Boolean>(()->WaveManager.isGameRunning()==WaveManager.isServerGameRunning());mc.getSingleplayerServer().execute(serverTask);
            check(serverTask.get(10,java.util.concurrent.TimeUnit.SECONDS),"Integrated server state does not read the client's running flag");
            var handler=Class.forName("me.cryo.zombierool.network.packet.S2CWaveUpdatePacket$ClientHandler");var apply=handler.getDeclaredMethod("apply",me.cryo.zombierool.network.packet.S2CWaveUpdatePacket.class);apply.setAccessible(true);
            apply.invoke(null,new me.cryo.zombierool.network.packet.S2CWaveUpdatePacket(2,false));
            check(!WaveManager.isGameRunning(),"A wave number without a running match cannot activate the client game state");
            mc.level.setBlock(support,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);mc.level.setBlock(support.above(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            player.setPos(support.getX()-.5,support.getY(),support.getZ()+.5);DebugPlayerControls.clientNoclip=true;
            boolean wasFlying=player.getAbilities().flying;player.getAbilities().flying=false;
            player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(1.8,0,0));
            check(player.getX()<support.getX()&&!player.noPhysics,"Local player keeps wall collision while walking with noclip enabled");
            player.getAbilities().flying=true;
            player.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(1.8,0,0));
            check(player.getX()>support.getX()+1,"Actual local player noclip crosses a two-block wall");
            player.getAbilities().flying=wasFlying;
            DebugPlayerControls.clientPerkLimit=-1;check(PerksManager.getMaxPerks(player)==Integer.MAX_VALUE,"Client interaction uses unlimited map perks for minus one");
            DebugPlayerControls.clientPerkLimit=0;check(PerksManager.getMaxPerks(player)==0,"Client interaction treats zero as forbidden perks");
            var console=new me.cryo.zombierool.client.gui.SecretConsoleScreen(mc.screen);console.init(mc,640,360);var help=console.getClass().getDeclaredMethod("printHelp");help.setAccessible(true);help.invoke(console);
            var logsField=console.getClass().getDeclaredField("logs");logsField.setAccessible(true);var logs=(java.util.List<?>)logsField.get(null);
            check(logs.size()>30,"Console help includes the map debugging command groups");
        }finally{DebugPlayerControls.clientNoclip=noclip;DebugPlayerControls.clientPerkLimit=4;player.noPhysics=physics;player.setPos(position);mc.level.setBlock(support,old,3);mc.level.setBlock(support.above(),oldUpper,3);WaveManager.setClientWave(clientWave);WaveManager.setClientGameRunning(clientRunning);}
    }
    private static void absorptionHud()throws Exception {
        var mc=Minecraft.getInstance();float previous=mc.player.getAbsorptionAmount();
        var canvas=Oct12ClientTest.class.getDeclaredMethod("canvas",Minecraft.class,String.class,java.util.function.Consumer.class);canvas.setAccessible(true);
        java.util.function.Consumer<GuiGraphics> draw=g->me.cryo.zombierool.client.ClassicZombieHUD.render(g,mc,mc.player,640,360);
        try {
            mc.player.setAbsorptionAmount(0);var empty=(int[])canvas.invoke(null,mc,"health-no-absorption",draw);
            mc.player.setAbsorptionAmount(2.5F);var shield=(int[])canvas.invoke(null,mc,"health-absorption",draw);
            int gold=0;for(int y=36;y<51;y++)for(int x=11;x<125;x++){int c=shield[y*640+x];if((c&255)>180&&((c>>8)&255)>100&&((c>>16)&255)<150&&c!=empty[y*640+x])gold++;}
            check(gold>80,"The mod health HUD draws a separate golden absorption value and bar");
        }finally{mc.player.setAbsorptionAmount(previous);me.cryo.zombierool.client.ClassicZombieHUD.reset();}
    }
    private static void weaponStatusHud()throws Exception {
        var mc=Minecraft.getInstance();var old=mc.player.getMainHandItem();
        var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool","plasmapistol"));
        check(item instanceof me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem,"The HUD test uses a real energy/overheat weapon");
        var gun=(me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem)item;var stack=new net.minecraft.world.item.ItemStack(item);gun.initializeIfNeeded(stack);
        var canvas=Oct12ClientTest.class.getDeclaredMethod("canvas",Minecraft.class,String.class,java.util.function.Consumer.class);canvas.setAccessible(true);
        java.util.function.Consumer<GuiGraphics> draw=g->me.cryo.zombierool.client.ClassicZombieHUD.render(g,mc,mc.player,640,360);
        try{
            mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);gun.setDurability(stack,gun.getMaxDurability(stack));gun.setOverheat(stack,0);
            var full=(int[])canvas.invoke(null,mc,"weapon-status-full",draw);
            gun.setDurability(stack,gun.getMaxDurability(stack)/2);gun.setOverheat(stack,(int)(gun.getMaxOverheat()*.8));
            var used=(int[])canvas.invoke(null,mc,"weapon-status-used",draw);int energy=0,heat=0;
            for(int x=500;x<628;x++){if(full[340*640+x]!=used[340*640+x])energy++;if(full[355*640+x]!=used[355*640+x])heat++;}
            check(energy>=60,"Energy consumption updates the visible durability bar");check(heat>=95,"Rising heat updates a separate overheat bar in the combat HUD");
        }finally{mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,old);me.cryo.zombierool.client.ClassicZombieHUD.reset();}
    }
    private static void noWallEjection()throws Exception {
        var mc=Minecraft.getInstance();var player=mc.player;var original=player.position();var velocity=player.getDeltaMovement();boolean fly=player.getAbilities().flying,clip=DebugPlayerControls.clientNoclip,physics=player.noPhysics;
        var wall=player.blockPosition().offset(9,0,0);var first=mc.level.getBlockState(wall);var second=mc.level.getBlockState(wall.above());
        try {
            mc.level.setBlock(wall,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);mc.level.setBlock(wall.above(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);player.setPos(wall.getX()+.5,wall.getY(),wall.getZ()+.5);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            DebugPlayerControls.clientNoclip=true;player.getAbilities().flying=true;
            var eject=net.minecraft.client.player.LocalPlayer.class.getDeclaredMethod("moveTowardsClosestSpace",double.class,double.class);eject.setAccessible(true);eject.invoke(player,player.getX(),player.getZ());
            check(player.getDeltaMovement().horizontalDistanceSqr()==0,"Flying noclip is never pushed out of a wall by the vanilla local-player routine");
            player.getAbilities().flying=false;DebugPlayerControls.applyCollision(player);eject.invoke(player,player.getX(),player.getZ());check(player.getDeltaMovement().horizontalDistanceSqr()>0,"Normal walking still retains vanilla wall protection");
        }finally{player.setPos(original);player.setDeltaMovement(velocity);player.getAbilities().flying=fly;DebugPlayerControls.clientNoclip=clip;player.noPhysics=physics;mc.level.setBlock(wall,first,3);mc.level.setBlock(wall.above(),second,3);}
    }
    private static void mimicBlood()throws Exception {
        var mc=Minecraft.getInstance();var support=mc.player.blockPosition().offset(11,-1,0);var previous=mc.level.getBlockState(support);
        var target=new com.mojang.blaze3d.pipeline.TextureTarget(256,256,true,Minecraft.ON_OSX);target.bindWrite(true);GL11.glClearColor(.68f,.57f,.4f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();var view=RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(-.65f,.65f,-.65f,.65f,.05f,10),VertexSorting.ORTHOGRAPHIC_Z);
        try {
            mc.level.setBlock(support,me.cryo.zombierool.block.system.BuyWallWeaponSystem.BLOCK.get().defaultBlockState(),3);
            var mimic=(me.cryo.zombierool.block.system.MimicSystem.IMimicContainer)mc.level.getBlockEntity(support);mimic.setMimic(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            var modelPose=new PoseStack();modelPose.translate(0,0,-1);modelPose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));modelPose.translate(-.5,-1,-.5);
            me.cryo.zombierool.block.system.MimicSystem.renderMimic(mimic.getMimic(),support,mc.level,modelPose,mc.renderBuffers().bufferSource(),15728880,0);mc.renderBuffers().bufferSource().endBatch();
            var depthBefore=org.lwjgl.BufferUtils.createFloatBuffer(256*256);GL11.glReadPixels(0,0,256,256,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthBefore);
            var entity=me.cryo.zombierool.init.ZombieroolModEntities.BLOOD_DECAL.get().create(mc.level);entity.setPos(support.getX()+.5,support.getY()+1,support.getZ()+.5);
            var pose=new PoseStack();pose.translate(0,0,-1);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));pose.translate(-entity.getX(),-entity.getY(),-entity.getZ());
            var stamp=me.cryo.zombierool.client.BloodSurfaceRenderer.stamp(entity);check(!stamp.pieces().isEmpty(),"Blood recognizes the actual surface of a mimic wall-weapon block");
            me.cryo.zombierool.client.BloodSurfaceRenderer.render(java.util.List.of(stamp),pose,mc.renderBuffers().bufferSource());
            var depthAfter=org.lwjgl.BufferUtils.createFloatBuffer(256*256);GL11.glReadPixels(0,0,256,256,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depthAfter);
            boolean same=true;for(int n=0;n<256*256;n++)if(depthBefore.get(n)!=depthAfter.get(n)){same=false;break;}
            check(same,"Blood never changes the depth buffer or hides a mimic's model");
            try(var image=new NativeImage(256,256,true)){target.bindRead();image.downloadTexture(0,false);image.flipY();image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/blood-mimic-visible.png"));}
            mimic.setMimic(net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState());check(stamp.pieces().stream().noneMatch(piece->piece.present(mc.level)),"Changing a mimic invalidates old blood geometry");
        }finally{mc.level.setBlock(support,previous,3);view.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);me.cryo.zombierool.client.BloodSurfaceRenderer.clear();}
    }
    private static void preserveDraft()throws Exception {
        var mc=Minecraft.getInstance();var previous=mc.screen;
        var data=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());data.writeBlockPos(BlockPos.ZERO);
        var menu=new me.cryo.zombierool.block.system.PerksSystem.PerksInterfaceMenu(203,mc.player.getInventory(),data);
        var parent=new me.cryo.zombierool.block.system.PerksSystem.PerksInterfaceScreen(menu,mc.player.getInventory(),net.minecraft.network.chat.Component.empty());
        try {
            mc.setScreen(parent);
            var price=(net.minecraft.client.gui.components.EditBox)parent.children().stream().filter(widget->widget instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();price.setValue("1337");
            var selected=parent.getClass().getDeclaredField("currentPerkIndex");selected.setAccessible(true);selected.setInt(parent,1);
            var button=(net.minecraft.client.gui.components.Button)parent.children().stream().filter(widget->widget instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("gui.zombierool.block_power.button").getString())).findFirst().orElseThrow();button.onPress();
            BlockPowerScreen.open(new BlockPowerPackets.Open(BlockPos.ZERO,BlockPowerSettings.Mode.BOTH,false,false));
            check(mc.screen instanceof BlockPowerScreen,"Power settings open through the normal perk configuration button");mc.screen.onClose();
            var restored=(net.minecraft.client.gui.components.EditBox)parent.children().stream().filter(widget->widget instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
            check(mc.screen==parent&&restored.getValue().equals("1337")&&selected.getInt(parent)==1,"Returning from power settings preserves the unsaved perk price and selection");
        }finally{mc.setScreen(previous);}
    }
    private static void dropsIgnoreBarriers()throws Exception {
        var mc=Minecraft.getInstance();var base=mc.player.blockPosition().offset(5,-1,0);
        var old=java.util.List.of(mc.level.getBlockState(base),mc.level.getBlockState(base.above()),mc.level.getBlockState(base.above(2)));
        var sprite=mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(new net.minecraft.resources.ResourceLocation("minecraft","block/stone"));
        var sprites=new net.minecraft.client.particle.SpriteSet(){public net.minecraft.client.renderer.texture.TextureAtlasSprite get(int age,int lifetime){return sprite;}public net.minecraft.client.renderer.texture.TextureAtlasSprite get(net.minecraft.util.RandomSource random){return sprite;}};
        var constructor=me.cryo.zombierool.client.particle.BloodStainParticle.class.getDeclaredConstructor(net.minecraft.client.multiplayer.ClientLevel.class,double.class,double.class,double.class,double.class,double.class,double.class,net.minecraft.client.particle.SpriteSet.class);constructor.setAccessible(true);
        try {
            mc.level.setBlock(base,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);mc.level.setBlock(base.above(2),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            for(var barrier:java.util.List.of(net.minecraft.world.level.block.Blocks.BARRIER,me.cryo.zombierool.init.ZombieroolModBlocks.RESTRICT.get())) {
                mc.level.setBlock(base.above(),barrier.defaultBlockState(),3);
                var drop=(me.cryo.zombierool.client.particle.BloodStainParticle)constructor.newInstance(mc.level,base.getX()+.5,base.getY()+2.5,base.getZ()+.5,0,-.5,0,sprites);
                for(int i=0;i<8&&drop.isAlive();i++)drop.tick();
                var yField=net.minecraft.client.particle.Particle.class.getDeclaredField("y");yField.setAccessible(true);
                check(!drop.isAlive()&&Math.abs(yField.getDouble(drop)-(base.getY()+1.02))<.001,"Blood particles pass through "+barrier+" and hit the visible floor");
            }
        }finally{for(int i=0;i<3;i++)mc.level.setBlock(base.above(i),old.get(i),3);me.cryo.zombierool.client.BloodSplatters.clear();}
    }
    private static void taczBloodRage()throws Exception {
        var server=Minecraft.getInstance().getSingleplayerServer();var uuid=Minecraft.getInstance().player.getUUID();
        var task=new java.util.concurrent.FutureTask<Void>(()->{
            var player=server.getPlayerList().getPlayer(uuid);var level=player.serverLevel();var effects=new java.util.ArrayList<>(player.getActiveEffects());float hp=player.getHealth(),abs=player.getAbsorptionAmount();long time=level.getGameTime();
            var zombie=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(level);
            try{
                player.removeAllEffects();player.addEffect(new net.minecraft.world.effect.MobEffectInstance(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_BLOOD_RAGE.get(),200));
                zombie.setEmergenceTicks(0);zombie.setNoAi(true);zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);zombie.setHealth(1000);
                var direct=new net.minecraft.world.entity.projectile.Arrow(level,player);
                for(boolean armorPiercing:new boolean[]{false,true}){
                    player.setHealth(player.getMaxHealth()/2);player.setAbsorptionAmount(0);float before=player.getHealth();
                    level.getServer().getWorldData().overworldData().setGameTime(time+(armorPiercing?40:20));
                    for(int seed=0;seed<100000;seed++){player.getRandom().setSeed(seed);if(player.getRandom().nextFloat()<.05){player.getRandom().setSeed(seed);break;}}
                    zombie.invulnerableTime=0;var source=com.tacz.guns.init.ModDamageTypes.Sources.bullet(level.registryAccess(),direct,player,armorPiercing);zombie.hurt(source,20);
                    check(player.getHealth()>before,"Blood Rage heals through TacZ's actual "+(armorPiercing?"armor-piercing":"normal")+" bullet damage source");
                }
            }finally{zombie.discard();player.removeAllEffects();for(var effect:effects)player.addEffect(effect);player.setHealth(hp);player.setAbsorptionAmount(abs);level.getServer().getWorldData().overworldData().setGameTime(time);}
            return null;
        });server.execute(task);task.get(15,java.util.concurrent.TimeUnit.SECONDS);
    }
}
