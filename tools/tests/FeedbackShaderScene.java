package local.hotfixtest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.gameplay.TraversalMotion;

/** Actual world/hand shader passes, separate from the offscreen vanilla GL checks. */
public final class FeedbackShaderScene {
    private int ticks,phase;
    private volatile boolean ready;
    private boolean initialized;
    private int[] plain;
    private boolean autoTest;
    private final boolean quick=Boolean.getBoolean("zr.photon.quick");
    private final boolean clean=Boolean.getBoolean("zr.photon.clean");
    private final String[] guns=clean||quick?new String[]{"m4a1"}:new String[]{"m4a1","m1014","hk_mp5a5","g36k"};
    public static void start(){var scene=new FeedbackShaderScene();scene.setup();MinecraftForge.EVENT_BUS.addListener(scene::tick);MinecraftForge.EVENT_BUS.addListener(scene::render);MinecraftForge.EVENT_BUS.addListener(scene::stage);}
    private void stage(net.minecraftforge.client.event.RenderLevelStageEvent event){
        if(initialized&&ticks==30&&phase==0&&event.getStage()==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS){
            var pads=TeleporterSystem.loaded(Minecraft.getInstance().level);System.out.println("PHOTON CHECK: world electricity candidates "+pads.size()+" "+pads.stream().map(p->p.getBlockPos()+" "+p.busy+" "+p.chargeUntil+" "+p.role()).toList());
        }
    }
    private void setup(){
        var mc=Minecraft.getInstance();
        mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var p=new BlockPos(0,180,0);
            me.cryo.zombierool.config.WorldConfig.get(level).setDayNightMode("day");
            level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setWeatherParameters(10000,0,false,false);
            for(int x=-6;x<=6;x++)for(int z=-1;z<=10;z++){
                level.setBlock(p.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState(),3);
                for(int y=0;y<=6;y++)level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            for(int y=0;y<=5;y++)for(int z=5;z<=8;z++)level.setBlock(p.offset(4,y,z),Blocks.STONE_BRICKS.defaultBlockState(),3);
            var at=p.offset(0,0,3);for(int i=0;i<9;i++)level.setBlock(at.offset(i%3-1,0,i/3-1),TeleporterSystem.PLATFORM.get().defaultBlockState().setValue(TeleporterSystem.Platform.PART,i),3);
            var pad=(TeleporterSystem.Pad)level.getBlockEntity(at);pad.busy=!clean;pad.chargeUntil=level.getGameTime()+20000;pad.sync();
            var player=level.getServer().getPlayerList().getPlayer(mc.player.getUUID());player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.teleportTo(level,.5,180,.5,0,10);ready=true;
        });
    }
    private void initialize(){
        var mc=Minecraft.getInstance();mc.setScreen(null);mc.options.hideGui=false;mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        ClientEnvironmentEffects.setFogPreset("none",0,0,0,1,512,false);
        me.cryo.zombierool.gameplay.WaveManager.setClientGameRunning(true);BloodSplatters.clientTick(true,true);
        mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);mc.player.setNoGravity(true);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setYRot(0);mc.player.setXRot(10);mc.player.yRotO=0;mc.player.xRotO=10;
        var p=mc.player.blockPosition();mc.player.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
        if(!clean){
        var crawler=me.cryo.zombierool.init.ZombieroolModEntities.CRAWLER.get().create(mc.level);
        Vec3 from=Vec3.atBottomCenterOf(p.offset(3,3,6));crawler.setPos(from);crawler.setNoAi(true);crawler.setEmergenceTicks(0);mc.level.putNonPlayerEntity(410002,crawler);
        TraversalMotion.receive(crawler,new TransportPackets.Jump(crawler.getId(),crawler.getUUID(),from,from.add(1,0,0),from,20000,0,2));
        var knight=me.cryo.zombierool.init.ZombieroolModEntities.WHITE_KNIGHT.get().create(mc.level);knight.setPos(Vec3.atBottomCenterOf(p.offset(-2,0,5)));knight.setNoAi(true);mc.level.putNonPlayerEntity(410003,knight);
        for(int i=0;i<5;i++)BloodSplatters.add(Vec3.atBottomCenterOf(p.offset(i-2,0,4)),net.minecraft.core.Direction.UP,.25f,.3f,.008f,.015f);
        }
        if(clean)try{var missing=TacZCamoOverlay.class.getDeclaredField("MISSING");missing.setAccessible(true);((java.util.Set<String>)missing.get(null)).add("_pap");}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        mc.gui.getChat().clearMessages(true);equip();
    }
    private String selectedCamo(){return guns[phase/2].equals("g36k")?"camo_lordaeron":"camo_bubbles";}
    private void equip(){
        var mc=Minecraft.getInstance();var stack=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:modern_kinetic_gun")));
        stack.getOrCreateTag().putString("GunId","tacz:"+guns[phase/2]);
        stack.getOrCreateTag().putBoolean(me.cryo.zombierool.ZombieroolKeys.PAP,true);
        if(phase%2==1)stack.getOrCreateTag().putString("zr_camo",selectedCamo());
        me.cryo.zombierool.core.system.WeaponFacade.setAmmo(stack,7);me.cryo.zombierool.core.system.WeaponFacade.setReserve(stack,32);
        mc.player.getInventory().selected=1;mc.player.getInventory().setItem(1,stack);ticks=0;
        mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.getInventory().selected=1;player.getInventory().setItem(1,stack.copy());
            me.cryo.zombierool.career.ServerCareerManager.setEquippedCamos(player,phase%2==1?java.util.Map.of(me.cryo.zombierool.core.system.WeaponFacade.getCareerWeaponId(stack),selectedCamo()):java.util.Map.of());
            player.inventoryMenu.broadcastChanges();
        });
    }
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        if(autoTest){
            var mc=Minecraft.getInstance();ticks++;
            if(me.cryo.zombierool.core.system.WeaponFacade.getAmmo(mc.player.getMainHandItem())>0&&!com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player).getSynReloadState().getStateType().isReloading()){System.out.println("PHOTON CHECK: real automatic reload fills the empty magazine without a key press");System.out.println("PHOTON SCENE PASS");mc.stop();return;}
            if(ticks>200){System.out.println("PHOTON SCENE FAIL: automatic reload timeout");mc.stop();}return;
        }
        if(!initialized){var mc=Minecraft.getInstance();if(!ready||mc.player.getY()<179||!(mc.level.getBlockEntity(new BlockPos(0,180,3)) instanceof TeleporterSystem.Pad))return;initialize();initialized=true;}
        ticks++;
        var pad=TeleporterSystem.pad(Minecraft.getInstance().level,new BlockPos(0,180,3));
        if(pad!=null){pad.busy=!clean;pad.chargeUntil=Minecraft.getInstance().level.getGameTime()+1000;}
    }
    private void render(RenderGuiEvent.Post event){
        if(autoTest||!initialized||ticks<(clean||quick?100:35))return;var mc=Minecraft.getInstance();
        try{
            if(phase==0){
                if(!clean&&TeleporterSystem.loaded(mc.level).stream().noneMatch(p->p.busy&&p.chargeUntil>mc.level.getGameTime()))throw new AssertionError("Client chunk did not register its charging teleporter for observers");
                Class<?> iris=Class.forName("net.irisshaders.iris.Iris");var pack=(java.util.Optional<?>)iris.getMethod("getCurrentPack").invoke(null);
                if(pack.isEmpty())throw new AssertionError("Photon shader pack is not active");
                var manager=iris.getMethod("getPipelineManager").invoke(null);Object pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
                if(pipeline.getClass().getName().contains("Vanilla"))throw new AssertionError("Shader fixture is using the vanilla pipeline");
                System.out.println("PHOTON CHECK: actual shader pipeline "+pipeline.getClass().getName());
            }
            event.getGuiGraphics().flush();
            if(phase%2==1){
                var held=Minecraft.getInstance().player.getMainHandItem();
                if(!held.getOrCreateTag().getString("zr_camo").equals(selectedCamo()))throw new AssertionError("Authoritative fixture inventory lost its camo");
                var rendered=com.tacz.guns.api.TimelessAPI.getGunDisplay(held).orElseThrow().getGunModel().getCurrentGunItem();
                if(!rendered.getOrCreateTag().getString("zr_camo").equals(selectedCamo()))throw new AssertionError("Actual first-person model is still reading stale camo NBT");
                System.out.println("PHOTON CHECK: first-person model receives the equipped camo for "+guns[phase/2]);
                var selected=TacZCamoOverlay.pattern(held);
                var painted=(net.minecraft.client.renderer.texture.DynamicTexture)mc.getTextureManager().getTexture(selected);
                painted.getPixels().writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/photon-"+(clean?"clean-":"")+guns[phase/2]+"-pattern.png"));
            }
            if(phase%2==0){
                var base=com.tacz.guns.api.TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow().getModelTexture();
                int unit=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
                com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                int binding=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
                var original=mc.getTextureManager().getTexture(base);original.bind();
                int width=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH),height=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
                System.out.println("PHOTON CHECK: "+guns[phase/2]+" live source "+base+" "+original.getClass().getName()+" "+width+"x"+height);
                try(var image=new com.mojang.blaze3d.platform.NativeImage(width,height,true)){image.downloadTexture(0,false);image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/"+guns[phase/2]+"-live-source.png"));}
                com.mojang.blaze3d.platform.GlStateManager._bindTexture(binding);com.mojang.blaze3d.systems.RenderSystem.activeTexture(unit);
            }
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                image.writeToFile(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/photon-"+(clean?"clean-":"")+guns[phase/2]+(phase%2==0?"-plain":guns[phase/2].equals("g36k")?"-lordaeron":"-bubbles")+".png"));
                int[] pixels=image.getPixelsRGBA();if(phase%2==0)plain=pixels;
                else{int changes=0;for(int i=0;i<pixels.length;i++)if(pixels[i]!=plain[i])changes++;if(changes<100)throw new AssertionError("No visible changes in actual Photon hand pass");System.out.println("PHOTON CHECK: "+guns[phase/2]+" real hand changes "+changes+" pixels");}
            }
            if(org.lwjgl.opengl.GL11.glGetError()!=org.lwjgl.opengl.GL11.GL_NO_ERROR)throw new AssertionError("GL error in real Photon world pass");
            if(++phase==guns.length*2){
                if(quick){autoTest=true;ticks=0;var empty=me.cryo.zombierool.core.system.WeaponFacade.createUnmappedTaczWeaponStack(new ResourceLocation("tacz:m4a1"),false);me.cryo.zombierool.core.system.WeaponFacade.setAmmo(empty,0);me.cryo.zombierool.core.system.WeaponFacade.setReserve(empty,60);mc.player.getInventory().setItem(1,empty);mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.getInventory().setItem(1,empty.copy());player.inventoryMenu.broadcastChanges();});}
                else{System.out.println("PHOTON SCENE PASS");mc.stop();}
            }else equip();
        }catch(Throwable error){error.printStackTrace();System.out.println("PHOTON SCENE FAIL");mc.stop();}
    }
}
