package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.block.system.BuildableBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;

public final class BuildablePartRenderer implements BlockEntityRenderer<BuildableBlocks.PartSupport> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    public BuildablePartRenderer(BlockEntityRendererProvider.Context c){items=c.getItemRenderer();}
    @Override public void render(BuildableBlocks.PartSupport support,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
        if(support.getBlockState().is(BuildableBlocks.DESTINATION.get())){renderGhost(support,partial,pose,buffer);return;}
        if(support.visual.isEmpty())return;
        var transform=BuildableBlocks.componentPose(support.visual,support.getBlockState().getValue(BuildableBlocks.PartBlock.FACE));
        pose.pushPose();pose.translate(transform.x(),transform.y(),transform.z());pose.mulPose(new org.joml.Quaternionf(transform.rx(),transform.ry(),transform.rz(),transform.rw()));pose.scale(transform.size(),transform.size(),transform.size());
        items.renderStatic(support.visual,transform.context(),light,overlay,pose,buffer,support.getLevel(),support.getBlockPos().hashCode());pose.popPose();
    }
    public static boolean ghostVisible(net.minecraft.world.entity.player.Player player,boolean running){return player!=null&&player.isCreative()&&!running;}
    private void renderGhost(BuildableBlocks.PartSupport support,float partial,PoseStack pose,MultiBufferSource buffer){
        var mc=net.minecraft.client.Minecraft.getInstance();
        boolean ready=me.cryo.zombierool.client.BuildableClient.readyToInstall(support.getBlockPos());
        if(!ready&&!ghostVisible(mc.player,me.cryo.zombierool.gameplay.WaveManager.isGameRunning()))return;
        int alpha=ghostAlpha(ready,support.getLevel().getGameTime()+partial);
        var state=me.cryo.zombierool.gameplay.BuildableSystem.placementState(support.getLevel(),support.getBlockPos(),mc.player,support.visual);
        MultiBufferSource ghost=type->new GhostVertex(buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)),alpha);
        pose.pushPose();
        if(state!=null&&state.getRenderShape()==net.minecraft.world.level.block.RenderShape.MODEL)mc.getBlockRenderer().renderSingleBlock(state,pose,ghost,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        else if(state!=null){
            boolean rendered=false;
            if(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory){
                var preview=factory.newBlockEntity(support.getBlockPos(),state);
                if(preview!=null){
                    // Load while detached: configuring a preview must not notify or register a real machine.
                    if(support.visual.getTag()!=null&&support.visual.getTag().contains("BlockEntityTag"))preview.load(support.visual.getTag().getCompound("BlockEntityTag").copy());
                    preview.setLevel(support.getLevel());var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(preview);
                    if(renderer!=null){renderer.render(preview,0,pose,ghost,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);rendered=true;}
                }
            }
            if(!rendered){pose.translate(.5,.5,.5);items.renderStatic(support.visual,ItemDisplayContext.NONE,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,ghost,support.getLevel(),support.getBlockPos().hashCode());pose.translate(-.5,-.5,-.5);}
        }
        var shape=state==null?net.minecraft.world.phys.shapes.Shapes.empty():state.getShape(support.getLevel(),support.getBlockPos());var bounds=shape.isEmpty()?new net.minecraft.world.phys.AABB(0,0,0,1,1,1):shape.bounds();
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose,buffer.getBuffer(net.minecraft.client.renderer.RenderType.lines()),bounds,.25f,.85f,1f,alpha/255f);pose.popPose();
    }
    public static int ghostAlpha(boolean ready,double ticks){return ready?(int)(65+115*(.5+.5*Math.sin(ticks*Math.PI/20))):90;}
    public static final class GhostVertex implements com.mojang.blaze3d.vertex.VertexConsumer {
        private final com.mojang.blaze3d.vertex.VertexConsumer target;
        private final int alpha;
        public GhostVertex(com.mojang.blaze3d.vertex.VertexConsumer target,int alpha){this.target=target;this.alpha=alpha;}
        public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x,double y,double z){target.vertex(x,y,z);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer color(int r,int g,int b,int a){target.color((r+80)/2,(g+210)/2,(b+255)/2,Math.min(a,alpha));return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer uv(float u,float v){target.uv(u,v);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u,int v){target.overlayCoords(u,v);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u,int v){target.uv2(u,v);return this;}
        public com.mojang.blaze3d.vertex.VertexConsumer normal(float x,float y,float z){target.normal(x,y,z);return this;}
        public void endVertex(){target.endVertex();}
        public void defaultColor(int r,int g,int b,int a){target.defaultColor((r+80)/2,(g+210)/2,(b+255)/2,Math.min(a,alpha));}
        public void unsetDefaultColor(){target.unsetDefaultColor();}
    }

}
