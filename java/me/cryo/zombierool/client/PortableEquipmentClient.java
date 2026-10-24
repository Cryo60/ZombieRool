package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.gameplay.PortableEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class PortableEquipmentClient {
    private static final class PreviewType extends RenderType {
        private PreviewType(){super("zr_preview_unused",com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
        static final RenderType TYPE=create("zr_equipment_preview",com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER).setTextureState(new TextureStateShard(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
    private static final java.util.Map<java.util.UUID,net.minecraft.nbt.CompoundTag> pending=new java.util.HashMap<>();
    private static final java.util.Map<String,java.util.Set<net.minecraft.core.BlockPos>> locked=new java.util.HashMap<>();
    private static boolean key;
    public static boolean hasTeamKey(){return key;}
    public static net.minecraft.network.chat.Component contextHint(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.player.isSpectator())return null;
        String action=me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
        var device=PortableEquipment.deviceTarget(mc.player,3);if(device!=null){var hint=net.minecraft.network.chat.Component.translatable("message.zombierool.equipment.retrieve",action);if(mc.level.getBlockEntity(device) instanceof PortableEquipment.Device d&&d.getBlockState().is(PortableEquipment.TURBINE_BLOCK.get()))hint.append(" | ").append(net.minecraft.network.chat.Component.translatable(d.ready()?"message.zombierool.equipment.ready":"message.zombierool.equipment.starting").withStyle(d.ready()?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.GOLD));return hint;}
        if(me.cryo.zombierool.gameplay.WardenLocks.keyTarget(mc.player)!=null&&!key)return net.minecraft.network.chat.Component.translatable("message.zombierool.warden.loot",action);
        var hit=mc.player.pick(3,1,false);if(hit instanceof net.minecraft.world.phys.BlockHitResult block&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&locked(block.getBlockPos()))return net.minecraft.network.chat.Component.translatable(key?"message.zombierool.warden.unlock_hint":"message.zombierool.warden.locked_hint",action);
        return null;
    }
    public static void contextAction(){var mc=Minecraft.getInstance();if(mc.player==null)return;var device=PortableEquipment.deviceTarget(mc.player,3);if(device!=null)me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestRetrieve(device);else if(!key&&me.cryo.zombierool.gameplay.WardenLocks.keyTarget(mc.player)!=null)me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestLoot();else if(mc.player.pick(3,1,false) instanceof net.minecraft.world.phys.BlockHitResult hit)me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestUnlock(hit.getBlockPos());}
    public static int drawKeyTab(net.minecraft.client.gui.GuiGraphics g,int width,int top){if(!key)return 0;var mc=Minecraft.getInstance();int panel=Math.min(720,width-40),left=(width-panel)/2;g.fill(left,top,left+panel,top+25,0xD0152520);g.renderItem(new ItemStack(me.cryo.zombierool.gameplay.WardenLocks.KEY.get()),left+5,top+4);g.drawString(mc.font,net.minecraft.network.chat.Component.translatable("message.zombierool.warden.team_unlocked"),left+29,top+9,0xB1E6AC);return 32;}
    private static net.minecraft.client.model.geom.ModelPart grip;
    private static net.minecraft.client.model.geom.ModelPart grip(){if(grip==null){var mesh=new net.minecraft.client.model.geom.builders.MeshDefinition();mesh.getRoot().addOrReplaceChild("hand",net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(40,24).addBox(-2,-2,-2,4,4,4),net.minecraft.client.model.geom.PartPose.ZERO);grip=net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot();}return grip;}
    public static void receive(java.util.UUID id,net.minecraft.nbt.CompoundTag state,boolean hasKey){var level=Minecraft.getInstance().level;var p=level==null?null:level.getPlayerByUUID(id);if(p!=null)p.getPersistentData().put(PortableEquipment.STATE,state==null?new net.minecraft.nbt.CompoundTag():state.copy());else if(state!=null)pending.put(id,state.copy());key=hasKey;}
    public static void locks(String dimension,java.util.Set<net.minecraft.core.BlockPos> targets){locked.put(dimension,java.util.Set.copyOf(targets));}
    public static boolean locked(net.minecraft.core.BlockPos pos){var level=Minecraft.getInstance().level;return level!=null&&locked.getOrDefault(level.dimension().location().toString(),java.util.Set.of()).contains(pos);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var level=Minecraft.getInstance().level;if(level==null)return;pending.entrySet().removeIf(entry->{var p=level.getPlayerByUUID(entry.getKey());if(p==null)return false;p.getPersistentData().put(PortableEquipment.STATE,entry.getValue());return true;});}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){pending.clear();locked.clear();fallingLocks.clear();key=false;}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null||!PortableEquipment.inHands(mc.player))return;if(!PortableEquipment.carried(mc.player).is(PortableEquipment.SHIELD.get()))e.getGuiGraphics().drawCenteredString(mc.font,net.minecraft.network.chat.Component.translatable("hud.zombierool.equipment.place"),mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()/2+50,0xEEEEEE);}
    private record FallingLock(net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.phys.Vec3 center,double drop,long start){}
    private static final java.util.List<FallingLock> fallingLocks=new java.util.ArrayList<>();
    public static void lockDrop(net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        var mc=Minecraft.getInstance();if(mc.level==null)return;var center=state.getShape(mc.level,pos).bounds().getCenter().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos));
        var facing=state.getValue(me.cryo.zombierool.gameplay.WardenLocks.LockBlock.FACING);var outside=center.add(facing.getStepX()*.3,0,facing.getStepZ()*.3);
        var floor=mc.level.clip(new net.minecraft.world.level.ClipContext(outside,outside.add(0,-4,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        fallingLocks.add(new FallingLock(pos.immutable(),state,center,Math.max(.05,Math.min(4,center.y-floor.getLocation().y-.1)),mc.level.getGameTime()));
    }
    public static float impact(net.minecraft.world.entity.player.Player p,float partial){var n=PortableEquipment.data(p);if(!n.contains("impactTick"))return 0;float age=p.level().getGameTime()+partial-n.getLong("impactTick");return age<0||age>8?0:(float)Math.sin(age*Math.PI/8)*(1-age/8);}
    @SubscribeEvent public static void shake(ViewportEvent.ComputeCameraAngles e){var mc=Minecraft.getInstance();if(mc.player==null)return;float pulse=impact(mc.player,(float)e.getPartialTick());e.setPitch(e.getPitch()+pulse*.9f);e.setRoll(e.getRoll()+pulse*.6f);}
    // Render independently of player layers: gun renderers may replace/filter those layers.
    @SubscribeEvent public static void worldEquipment(RenderLevelStageEvent e){
        var mc=Minecraft.getInstance();if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||mc.level==null)return;var camera=e.getCamera().getPosition();var pose=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();float partial=e.getPartialTick();
        for(var p:mc.level.players()){
            var item=PortableEquipment.carried(p);if(!item.is(PortableEquipment.SHIELD.get())||p.isInvisible()||!p.isAlive()||p==mc.getCameraEntity()&&mc.options.getCameraType().isFirstPerson()||p.distanceToSqr(camera)>4096)continue;
            double x=net.minecraft.util.Mth.lerp(partial,p.xo,p.getX()),y=net.minecraft.util.Mth.lerp(partial,p.yo,p.getY()),z=net.minecraft.util.Mth.lerp(partial,p.zo,p.getZ());float yaw=net.minecraft.util.Mth.rotLerp(partial,p.yBodyRotO,p.yBodyRot);
            pose.pushPose();pose.translate(x-camera.x,y-camera.y+(p.isCrouching()?.5:.65),z-camera.z);pose.mulPose(Axis.YP.rotationDegrees(-yaw));pose.translate(0,0,PortableEquipment.inHands(p)?.55:-.34);pose.mulPose(Axis.ZP.rotationDegrees(impact(p,partial)*6));pose.scale(1.9f,1.3f,1.3f);
            mc.getItemRenderer().renderStatic(item,ItemDisplayContext.NONE,mc.getEntityRenderDispatcher().getPackedLightCoords(p,partial),OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,p.getId());pose.popPose();
        }
        fallingLocks.removeIf(lock->mc.level.getGameTime()-lock.start>30);
        for(var lock:fallingLocks){float age=mc.level.getGameTime()+partial-lock.start;double t=Math.max(0,age)/20;var face=lock.state.getValue(me.cryo.zombierool.gameplay.WardenLocks.LockBlock.FACING);pose.pushPose();pose.translate(lock.center.x-camera.x+face.getStepX()*.12,lock.center.y-camera.y-Math.min(lock.drop,4.9*t*t),lock.center.z-camera.z+face.getStepZ()*.12);pose.mulPose(Axis.YP.rotationDegrees(-face.toYRot()));pose.mulPose(Axis.XP.rotationDegrees((float)Math.min(85,t*330)));pose.mulPose(Axis.YP.rotationDegrees(face.toYRot()));pose.translate(lock.pos.getX()-lock.center.x,lock.pos.getY()-lock.center.y,lock.pos.getZ()-lock.center.z);mc.getBlockRenderer().renderSingleBlock(lock.state,pose,buffers,15728880,OverlayTexture.NO_OVERLAY);pose.popPose();}
    }
    private static void renderSentry(net.minecraft.world.level.block.state.BlockState state,PortableEquipment.Device device,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var mc=Minecraft.getInstance();float hit=device==null?0:device.damageMotion(partial),flash=device==null?0:device.damageFlash(partial);if(flash>0)overlay=OverlayTexture.pack(0,true);pose.pushPose();if(device!=null){pose.translate(.5+.035*hit,0,.5);pose.mulPose(Axis.ZP.rotationDegrees(hit*7));pose.mulPose(Axis.XP.rotationDegrees(hit*3));pose.translate(-.5,0,-.5);}
        var renderer=mc.getBlockRenderer().getModelRenderer();renderer.renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)),state,mc.getBlockRenderer().getBlockModel(state),1,1,1,light,overlay);
        float yaw=device==null?state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).toYRot():device.aimYaw,pitch=device==null?0:device.aimPitch;
        if(device!=null&&device.dying()){float t=device.deathProgress(partial);float eased=1-(1-t)*(1-t)*(1-t);pitch=net.minecraft.util.Mth.lerp(eased,pitch,-70);}
        pose.translate(.5,.83,.5);pose.mulPose(Axis.YP.rotationDegrees(180-yaw));pose.mulPose(Axis.XP.rotationDegrees(pitch+hit*9));pose.translate(-.5,-.83,-.5);
        var head=mc.getItemRenderer().getModel(new ItemStack(PortableEquipment.SENTRY_PARTS.get(1).get()),mc.level,null,0);renderer.renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)),state,head,1,1,1,light,overlay);pose.popPose();
    }
    private static final class Fan implements BlockEntityRenderer<PortableEquipment.Device> {
        Fan(BlockEntityRendererProvider.Context c){}
        @Override public void render(PortableEquipment.Device d,float partial,PoseStack pose,MultiBufferSource b,int light,int overlay){
            if(d.getLevel()==null)return;if(d.getBlockState().is(PortableEquipment.SENTRY_BLOCK.get())){renderSentry(d.getBlockState(),d,partial,pose,b,light,overlay);return;}if(!d.getBlockState().is(PortableEquipment.TURBINE_BLOCK.get()))return;
            var mc=Minecraft.getInstance();float hit=d.damageMotion(partial);pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.ZP.rotationDegrees(hit*6));pose.translate(-.5,0,-.5);
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),b.getBuffer(RenderType.cutout()),d.getBlockState(),mc.getBlockRenderer().getBlockModel(d.getBlockState()),1,1,1,light,overlay);
            pose.pushPose();pose.translate(.5,1.5,.5);pose.mulPose(Axis.YP.rotationDegrees(-d.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).toYRot()));pose.mulPose(Axis.ZP.rotationDegrees((d.getLevel().getGameTime()+partial)*(4+20*d.startupProgress())));
            mc.getItemRenderer().renderStatic(new ItemStack(PortableEquipment.PARTS.get(3).get()),ItemDisplayContext.NONE,light,overlay,pose,b,d.getLevel(),0);pose.popPose();
            var face=d.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            pose.pushPose();pose.translate(.5+face.getStepX()*.225,.94,.5+face.getStepZ()*.225);pose.scale(.10f,.10f,.10f);
            var lamp=d.ready()?net.minecraft.world.level.block.Blocks.LIME_CONCRETE:((d.getLevel().getGameTime()/5)%2==0?net.minecraft.world.level.block.Blocks.ORANGE_CONCRETE:net.minecraft.world.level.block.Blocks.GRAY_CONCRETE);
            mc.getBlockRenderer().renderSingleBlock(lamp.defaultBlockState(),pose,b,15728880,OverlayTexture.NO_OVERLAY);pose.popPose();pose.popPose();
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void input(InputEvent.InteractionKeyMappingTriggered e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;if(!PortableEquipment.inHands(mc.player)){if(e.isUseItem()){var target=PortableEquipment.deviceTarget(mc.player,3);if(target!=null){e.setCanceled(true);e.setSwingHand(false);me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestRetrieve(target);}}return;}
        e.setCanceled(true);e.setSwingHand(false);
        equipmentClick(e.isAttack(),e.isUseItem());
    }
    private static void equipmentClick(boolean attack,boolean use){var mc=Minecraft.getInstance();if(mc.player==null)return;var item=PortableEquipment.carried(mc.player);if(!item.is(PortableEquipment.SHIELD.get())&&(attack||use)||use&&mc.player.isShiftKeyDown()){var hit=PortableEquipment.deploymentHit(mc.player);if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestPlace(hit);}else if(attack&&item.is(PortableEquipment.SHIELD.get()))me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestBash();}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre e){var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;if(!PortableEquipment.inHands(mc.player)){if(mc.options.keyUse.matchesMouse(e.getButton())){var target=PortableEquipment.deviceTarget(mc.player,3);if(target!=null){e.setCanceled(true);mc.options.keyUse.setDown(false);if(e.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS)me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestRetrieve(target);}}return;}boolean attack=mc.options.keyAttack.matchesMouse(e.getButton()),use=mc.options.keyUse.matchesMouse(e.getButton());if(!attack&&!use)return;e.setCanceled(true);if(e.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS)equipmentClick(attack,use);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void suppressWeapons(net.minecraftforge.event.TickEvent.ClientTickEvent e){var mc=Minecraft.getInstance();if(e.phase!=net.minecraftforge.event.TickEvent.Phase.START||mc.player==null||mc.screen!=null||!PortableEquipment.inHands(mc.player))return;while(mc.options.keyAttack.consumeClick())equipmentClick(true,false);while(mc.options.keyUse.consumeClick())equipmentClick(false,true);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);mc.player.stopUsingItem();if(net.minecraftforge.fml.ModList.get().isLoaded("tacz")){var op=com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);var state=op.getDataHolder();if(op.isAim())op.aim(false);state.clientIsAiming=false;state.clientAimingProgress=0;com.tacz.guns.client.gameplay.LocalPlayerDataHolder.oldAimingProgress=0;}}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST,receiveCanceled=true)
    public static void hand(RenderHandEvent e){
        var mc=Minecraft.getInstance();if(mc.player==null||!PortableEquipment.inHands(mc.player))return;e.setCanceled(true);if(e.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        var stack=PortableEquipment.carried(mc.player);boolean shield=stack.is(PortableEquipment.SHIELD.get());var pose=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        pose.pushPose();pose.translate(shield?-.48:.35,shield?-.6:-.65,shield?-1.05:-1);if(shield){pose.translate(0,impact(mc.player,mc.getFrameTime())*.025,impact(mc.player,mc.getFrameTime())*.07);pose.mulPose(Axis.XP.rotationDegrees(impact(mc.player,mc.getFrameTime())*4));pose.scale(1.9f,1.3f,1.3f);}else pose.scale(.38f,.38f,.38f);mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,e.getPackedLight(),OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);pose.popPose();
        pose.pushPose();pose.translate(shield?-.26:.25,shield?-.4:-.55,shield?-.57:-.7);pose.mulPose(Axis.XP.rotationDegrees(-15));grip().render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation())),e.getPackedLight(),OverlayTexture.NO_OVERLAY);pose.popPose();buffers.endBatch();
    }
    private static final org.joml.Matrix4f previewProjection=new org.joml.Matrix4f(),previewView=new org.joml.Matrix4f();
    private static final MultiBufferSource.BufferSource previewBuffers=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(65536));
    private static boolean previewReady;
    @SubscribeEvent public static void preview(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();previewReady=mc.player!=null&&mc.level!=null&&PortableEquipment.inHands(mc.player);
        if(!previewReady)return;previewProjection.set(e.getProjectionMatrix());var camera=e.getCamera();
        previewView.rotationX((float)Math.toRadians(camera.getXRot())).rotateY((float)Math.toRadians(camera.getYRot()+180f));
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void placementOverlay(RenderGuiEvent.Pre e){
        if(!previewReady)return;previewReady=false;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!PortableEquipment.inHands(mc.player))return;
        var previous=com.mojang.blaze3d.systems.RenderSystem.getShader();int[] textures=new int[4];for(int i=0;i<4;i++)textures[i]=com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(i);
        var stack=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();stack.pushPose();stack.setIdentity();stack.mulPoseMatrix(previewView);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(previewProjection,com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        float fogStart=com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart(),fogEnd=com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd();
        try(var state=new MapAtmosphereClient.RenderState()){
            mc.getMainRenderTarget().bindWrite(false);mc.gameRenderer.lightTexture().turnOnLightLayer();try{com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Float.MAX_VALUE);com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(Float.MAX_VALUE);drawPlacementPreview();}finally{mc.gameRenderer.lightTexture().turnOffLightLayer();}
        }finally{com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(fogStart);com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(fogEnd);stack.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();com.mojang.blaze3d.systems.RenderSystem.setShader(()->previous);for(int i=0;i<4;i++)com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(i,textures[i]);}
    }
    private static void drawPlacementPreview(){
        var mc=Minecraft.getInstance();var hit=PortableEquipment.deploymentHit(mc.player);if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return;
        var stack=PortableEquipment.carried(mc.player);if(stack.is(PortableEquipment.SHIELD.get())&&!mc.player.isShiftKeyDown())return;
        var spot=PortableEquipment.placement(mc.level,mc.player,hit,stack);var pose=new PoseStack();var camera=mc.gameRenderer.getMainCamera().getPosition();var buffers=previewBuffers;
        pose.pushPose();pose.translate(spot.pos().getX()-camera.x,spot.pos().getY()-camera.y,spot.pos().getZ()-camera.z);
        MultiBufferSource ghost=type->new me.cryo.zombierool.client.renderer.BuildablePartRenderer.GhostVertex(buffers.getBuffer(PreviewType.TYPE),105);
        boolean inside=new net.minecraft.world.phys.AABB(spot.pos()).expandTowards(0,1,0).inflate(.1).contains(camera);
        if(!inside){if(stack.is(PortableEquipment.SENTRY.get()))renderSentry(spot.state(),null,0,pose,ghost,15728880,OverlayTexture.NO_OVERLAY);else mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),ghost.getBuffer(RenderType.cutout()),spot.state(),mc.getBlockRenderer().getBlockModel(spot.state()),1,1,1,15728880,OverlayTexture.NO_OVERLAY);}
        if(stack.is(PortableEquipment.TURBINE.get())){pose.pushPose();pose.translate(.5,1.5,.5);mc.getItemRenderer().renderStatic(new ItemStack(PortableEquipment.PARTS.get(3).get()),ItemDisplayContext.NONE,15728880,OverlayTexture.NO_OVERLAY,pose,ghost,mc.level,0);pose.popPose();}
        LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),new net.minecraft.world.phys.AABB(0,0,0,1,2,1),spot.valid()?.2f:1f,spot.valid()?1f:.15f,.2f,.9f);pose.popPose();buffers.endBatch();
    }
    @Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(PortableEquipment.DEVICE.get(),Fan::new);}
    }
}
