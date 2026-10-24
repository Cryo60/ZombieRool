package me.cryo.zombierool.client;

import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.block.system.TraversalSystem.*;
import me.cryo.zombierool.gameplay.TraversalCurve;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Creative builder preview, using the same placement context and draft as the server. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class NavigationPlacementPreview {
    private static Anchor wallPreview;
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event){
        if(wallPreview!=null&&wallPreview.getLevel()==event.getLevel())wallPreview=null;
    }
    private NavigationPlacementPreview(){}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!mc.player.isCreative()||mc.screen!=null||!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return;
        var stack=mc.player.getMainHandItem();if(!(stack.getItem() instanceof BlockItem item))return;
        boolean traversal=item.getBlock() instanceof TraversalBlock,gate=item.getBlock() instanceof OneWayGate.Block,teleporter=item.getBlock() instanceof TeleporterSystem.Platform;
        if(!traversal&&!gate&&!teleporter)return;
        var context=new BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,stack,hit);BlockPos pos=context.getClickedPos();var state=item.getBlock().getStateForPlacement(context);if(state==null)return;
        boolean valid=context.canPlace()&&state.canSurvive(mc.level,pos);
        if(teleporter)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            BlockPos part=pos.offset(x,0,z);
            valid&=mc.level.getBlockState(part).canBeReplaced(context)&&!mc.level.getBlockState(part.below()).getCollisionShape(mc.level,part.below()).isEmpty();
        }
        Anchor root=null;int phase=0;
        if(traversal){
            var block=(TraversalBlock)item.getBlock();var draft=stack.getTag();
            if(draft!=null&&draft.getString("TraversalDimension").equals(mc.level.dimension().location().toString())&&draft.getInt("TraversalPhase")>0){
                root=TraversalSystem.entry(mc.level,BlockPos.of(draft.getLong("TraversalEntry")));
                if(root!=null&&root.kind()==block.kind&&root.getBlockPos().distSqr(pos)<=65536){phase=draft.getInt("TraversalPhase");if(block.kind!=Kind.TRAVERSAL)phase=2;}else root=null;
            }
            if(block.kind==Kind.WALL){
                if(wallPreview==null||wallPreview.getLevel()!=mc.level||!wallPreview.getBlockPos().equals(pos)||wallPreview.getBlockState()!=state){wallPreview=new Anchor(pos,state);wallPreview.setLevel(mc.level);}
                root=wallPreview;valid&=root.findWallExit();
            }
            else if(phase!=1)valid&=!mc.level.getBlockState(pos.below()).getCollisionShape(mc.level,pos.below()).isEmpty();
            if(phase==1&&root!=null)valid&=pos.getY()>=root.getBlockPos().getY();
            if(phase==2&&root!=null&&block.kind==Kind.TRAVERSAL)valid&=root.apex!=null&&root.apex.getY()>=pos.getY();
            state=state.setValue(TraversalBlock.PHASE,phase);
        }
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffer=mc.renderBuffers().bufferSource();var lines=buffer.getBuffer(RenderType.lines());
        float r=valid?.25f:1,g=valid?.95f:.15f,b=valid?1:.15f;
        double height=item.getBlock()==TraversalSystem.ZIPLINE.get()?2:.3;
        LevelRenderer.renderLineBox(pose,lines,new AABB(pos).setMaxY(pos.getY()+height).inflate(.005),r,g,b,.85f);
        if(teleporter){
            Vec3 center=Vec3.atBottomCenterOf(pos).add(0,.13,0);
            for(int i=0;i<64;i++){double a=i*Math.PI/32,c=(i+1)*Math.PI/32;line(pose,lines,center.add(Math.cos(a)*1.5,0,Math.sin(a)*1.5),center.add(Math.cos(c)*1.5,0,Math.sin(c)*1.5),r,g,b);}
        }
        if(traversal&&root!=null&&valid){
            var kind=((TraversalBlock)item.getBlock()).kind;Vec3 start=root.start(),end=Vec3.atBottomCenterOf(pos),peak=null;
            if(kind==Kind.WALL)end=root.end();
            if(kind==Kind.TRAVERSAL){if(phase==1){end=end.add(0,root.clearance,0);}else if(root.apex!=null)peak=root.peak();}
            if(kind==Kind.JUMP)peak=start.add(end).scale(.5).add(0,root.clearance,0);
            if(kind==Kind.ZIPLINE){start=start.add(0,1.95,0);end=end.add(0,1.95,0);}
            Vec3 old=start;
            for(int i=1;i<=32;i++){
                double t=i/32.0;Vec3 point=start.lerp(end,t);
                if(peak!=null){var p=TraversalCurve.sample(new TraversalCurve.Point(start.x,start.y,start.z),new TraversalCurve.Point(peak.x,peak.y,peak.z),new TraversalCurve.Point(end.x,end.y,end.z),t);point=new Vec3(p.x(),p.y(),p.z());}
                line(pose,lines,old,point,r,g,b);old=point;
            }
            LevelRenderer.renderLineBox(pose,lines,new AABB(start,start).inflate(.1),r,g,b,1);
            LevelRenderer.renderLineBox(pose,lines,new AABB(end,end).inflate(.15),r,g,b,1);
        }
        if(gate){var direction=state.getValue(OneWayGate.Block.FACING);Vec3 start=Vec3.atBottomCenterOf(pos).add(0,.3,0),end=start.add(direction.getStepX()*2,0,direction.getStepZ()*2);line(pose,lines,start,end,r,g,b);Vec3 back=end.subtract(start).normalize().scale(-.4),side=new Vec3(-direction.getStepZ()*.25,0,direction.getStepX()*.25);line(pose,lines,end,end.add(back).add(side),r,g,b);line(pose,lines,end,end.add(back).subtract(side),r,g,b);}
        buffer.endBatch();pose.popPose();
    }
    private static void line(com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer consumer,Vec3 a,Vec3 b,float r,float g,float blue){
        var n=b.subtract(a).normalize();for(var p:new Vec3[]{a,b})consumer.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(r,g,blue,.9f).normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
