package me.cryo.zombierool.client;
import net.minecraft.core.BlockPos;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class BrutusChains {
    private static final Set<BlockPos> POSITIONS=new HashSet<>();
    public static int unlockCost=2000;
    public static void receive(net.minecraft.nbt.CompoundTag n){if(n.getBoolean("unlockAction")){BrutusUnlockClient.receive(n);return;}unlockCost=n.contains("unlockCost")?n.getInt("unlockCost"):2000;POSITIONS.clear();for(long p:n.getLongArray("positions"))POSITIONS.add(BlockPos.of(p));}
    public static boolean locked(BlockPos p){return POSITIONS.contains(p);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel().isClientSide())POSITIONS.clear();}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void render(net.minecraftforge.client.event.RenderLevelStageEvent e){if(e.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return;var camera=e.getCamera().getPosition();for(var pos:POSITIONS){if(pos.distToCenterSqr(camera)>4096||!mc.level.hasChunkAt(pos))continue;var be=mc.level.getBlockEntity(pos);if(!me.cryo.zombierool.gameplay.BrutusMachineLocks.isMachine(be))continue;var pose=e.getPoseStack();pose.pushPose();pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);for(var part:parts(mc.level,pos)){pose.pushPose();pose.translate(part.getX()-pos.getX(),part.getY()-pos.getY(),part.getZ()-pos.getZ());drawPart(mc,pose);pose.popPose();}pose.popPose();}}
    public static List<BlockPos> parts(net.minecraft.world.level.Level level,BlockPos root){
        var parts=new ArrayList<BlockPos>();parts.add(root);
        if(level.getBlockEntity(root) instanceof me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlockEntity){
            for(var pos:List.of(root.below(),root.above())){
                var state=level.getBlockState(pos);
                if(state.getBlock() instanceof me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock){
                    var main=state.getValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART)==me.cryo.zombierool.block.system.PerksSystem.DummyPart.LOWER?pos.above():pos.below();
                    if(main.equals(root))parts.add(pos);
                }
            }
        }else if(level.getBlockEntity(root) instanceof me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlockEntity){
            var state=level.getBlockState(root);var pos=me.cryo.zombierool.gameplay.MysteryBoxManager.getOtherPartPos(root,state.getValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING));
            var other=level.getBlockState(pos);
            if(other.getBlock()==state.getBlock()&&other.getValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.PART))parts.add(pos);
        }
        return parts;
    }
    private static void segment(net.minecraft.client.Minecraft mc,com.mojang.blaze3d.vertex.PoseStack pose,double x,double y,double z,net.minecraft.core.Direction.Axis axis){
        pose.pushPose();pose.translate(x,y,z);
        var state=net.minecraft.world.level.block.Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS,axis);
        mc.getBlockRenderer().renderSingleBlock(state,pose,mc.renderBuffers().bufferSource(),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);pose.popPose();
    }
    private static void drawPart(net.minecraft.client.Minecraft mc,com.mojang.blaze3d.vertex.PoseStack pose){
        // Block models are centred at .5: offset the chain's centre just beyond each face.
        for(double face:new double[]{-.515,.515}){
            segment(mc,pose,face,0,0,net.minecraft.core.Direction.Axis.Z);
            segment(mc,pose,face,0,0,net.minecraft.core.Direction.Axis.Y);
            segment(mc,pose,0,0,face,net.minecraft.core.Direction.Axis.X);
            segment(mc,pose,0,0,face,net.minecraft.core.Direction.Axis.Y);
            segment(mc,pose,0,face,0,net.minecraft.core.Direction.Axis.X);
            segment(mc,pose,0,face,0,net.minecraft.core.Direction.Axis.Z);
        }
    }
    public static void draw(net.minecraft.client.Minecraft mc,com.mojang.blaze3d.vertex.PoseStack pose,boolean tall){
        for(int y=tall?-1:0;y<=(tall?1:0);y++){pose.pushPose();pose.translate(0,y,0);drawPart(mc,pose);pose.popPose();}
    }
}
