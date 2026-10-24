package local.hotfixtest;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import me.cryo.zombierool.core.registry.ZRThrowableRegistry;
import me.cryo.zombierool.gameplay.EquipmentSystem.Kind;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import org.lwjgl.opengl.GL11;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;

public final class EquipmentClientRenderTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("EQUIPMENT CLIENT CHECK: "+message);}
    public static void run()throws Exception{
        var mc=Minecraft.getInstance();
        long time=mc.level.getGameTime();me.cryo.zombierool.client.EquipmentFlashOverlay.flash(80);
        check(me.cryo.zombierool.client.EquipmentFlashOverlay.opacity(0)==1,"Own Flash initially covers the screen");
        mc.level.setGameTime(time+50);check(Math.abs(me.cryo.zombierool.client.EquipmentFlashOverlay.opacity(0)-.5f)<.001,"Own Flash fades gradually");
        mc.level.setGameTime(time+80);check(me.cryo.zombierool.client.EquipmentFlashOverlay.opacity(0)==0,"Own Flash clears after four gameplay seconds");mc.level.setGameTime(time);
        var clay=new me.cryo.zombierool.gameplay.EquipmentEntity(mc.level,mc.player,Kind.CLAYMORE);clay.setPos(mc.player.position());clay.land();
        var armed=me.cryo.zombierool.gameplay.EquipmentEntity.class.getDeclaredField("ARMED");armed.setAccessible(true);
        @SuppressWarnings("unchecked") var armedKey=(net.minecraft.network.syncher.EntityDataAccessor<Boolean>)armed.get(null);
        int[] laserVertices={0};
        var lineConsumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(EquipmentClientRenderTest.class.getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(p,m,args)->{if(m.getName().equals("endVertex"))laserVertices[0]++;return m.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?p:null;});
        var silent=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(EquipmentClientRenderTest.class.getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(p,m,args)->m.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?p:null);
        net.minecraft.client.renderer.MultiBufferSource geometry=type->type==net.minecraft.client.renderer.RenderType.lines()?lineConsumer:silent;
        var deviceRenderer=mc.getEntityRenderDispatcher().getRenderer(clay);deviceRenderer.render(clay,0,0,new com.mojang.blaze3d.vertex.PoseStack(),geometry,15728880);
        check(laserVertices[0]==0,"Claymore has no lasers before arming");clay.getEntityData().set(armedKey,true);deviceRenderer.render(clay,0,0,new com.mojang.blaze3d.vertex.PoseStack(),geometry,15728880);
        check(laserVertices[0]==4,"Armed client Claymore draws both red laser lines");
        var models=new java.util.ArrayList<ItemStack>();
        for(var k:Kind.values()){
            var stack=new ItemStack(ZRThrowableRegistry.loadoutItem(k));models.add(stack);var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            check(model.isGui3d()&&!model.getParticleIcon().contents().name().getPath().equals("missingno"),"Loaded 3D geometry and textures for "+k.id);
            var wall=new me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlockEntity(mc.player.blockPosition(),me.cryo.zombierool.block.system.BuyWallWeaponSystem.BLOCK.get().defaultBlockState());wall.setLevel(mc.level);wall.setItem(0,stack);
            int[] vertices={0};var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(EquipmentClientRenderTest.class.getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(p,m,args)->{if(m.getName().equals("endVertex"))vertices[0]++;return m.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?p:null;});
            new me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponRenderer(null).render(wall,0,new com.mojang.blaze3d.vertex.PoseStack(),type->consumer,15728880,0);
            check(vertices[0]>24,"Wall chalk renders actual "+k.id+" silhouette geometry rather than a four-vertex particle square");
            var hold=PlayerAnimationRegistry.getAnimation(new ResourceLocation("zombierool",k.id+"_hold"));
            var release=PlayerAnimationRegistry.getAnimation(new ResourceLocation("zombierool",k.id+(k==Kind.CLAYMORE||k==Kind.BETTY?"_place":"_throw")));
            check(hold!=null&&hold.isInfinite&&release!=null&&!release.isInfinite,"Registered hold/release animations for "+k.id);
            me.cryo.zombierool.client.ThirdPersonAnimHandler.startAnim(mc.player.getUUID(),k.id+"_hold",200);
            check(me.cryo.zombierool.client.ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID()),"Actual first-person playback starts for "+k.id);
        }
        models.add(new ItemStack(ZRThrowableRegistry.C4_DETONATOR.get()));
        var gun=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:m1014")));models.add(gun);
        check(!mc.getItemRenderer().getModel(gun,mc.level,mc.player,0).getParticleIcon().contents().name().getPath().equals("missingno"),"Native M1014 item has its weapon texture");
        check(!mc.getItemRenderer().getModel(gun,mc.level,mc.player,0).isGui3d(),"Native M1014 uses a flat handmade weapon sprite");
        check(mc.getItemRenderer().getModel(gun,mc.level,mc.player,0).getParticleIcon().contents().width()==1024,"M1014 retains its sharp 1024-pixel source in the actual item atlas");
        for(String id:new String[]{"winters_howl","vr11"}){
            var weapon=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool",id)));
            models.add(weapon);
            check(!weapon.isEmpty()&&!mc.getItemRenderer().getModel(weapon,mc.level,mc.player,0).getParticleIcon().contents().name().getPath().equals("missingno"),"Wonder weapon sprite loads: "+id);
            check(mc.getItemRenderer().getModel(weapon,mc.level,mc.player,0).getParticleIcon().contents().width()==1024,"Reference-based wonder texture retains full detail in the item atlas: "+id);
        }
        var human=me.cryo.zombierool.init.ZombieroolModEntities.VRIL_HUMAN.get().create(mc.level);
        check(mc.getEntityRenderDispatcher().getRenderer(human) instanceof me.cryo.zombierool.client.renderer.VrilHumanRenderer,"Converted human has a registered human renderer");
        var target=new TextureTarget(512,384,true,Minecraft.ON_OSX);target.bindWrite(true);
        GL11.glClearColor(.055f,.065f,.07f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        var projection=new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,512,384,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
        var pose=RenderSystem.getModelViewStack();pose.pushPose();pose.setIdentity();pose.translate(0,0,-10000);RenderSystem.applyModelViewMatrix();
        var gui=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
        for(int i=0;i<models.size();i++){
            int x=(i%4)*128,y=(i/4)*128;
            gui.fill(x+4,y+4,x+124,y+124,0xFF192126);gui.drawString(mc.font,models.get(i).getHoverName(),x+8,y+10,0xFFFFFF,false);
            gui.pose().pushPose();gui.pose().translate(x+40,y+42,0);gui.pose().scale(3,3,3);gui.renderItem(models.get(i),0,0);gui.pose().popPose();
        }
        gui.flush();
        // Read the framebuffer rather than the currently bound item atlas.
        var pixels=org.lwjgl.BufferUtils.createByteBuffer(512*384*4);GL11.glReadPixels(0,0,512,384,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        try(var image=new NativeImage(512,384,false)){
            for(int y=0;y<384;y++)for(int x=0;x<512;x++){int i=(y*512+x)*4;image.setPixelRGBA(x,383-y,((pixels.get(i+3)&255)<<24)|((pixels.get(i+2)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i)&255));}
            var path=java.nio.file.Path.of("../equipment-preview.png");image.writeToFile(path);
        }
        pose.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"All equipment renders without GL errors");
        System.out.println("EQUIPMENT CLIENT RENDER PASS");
    }
}
