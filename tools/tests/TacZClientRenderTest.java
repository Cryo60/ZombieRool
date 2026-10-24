package local.hotfixtest;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import me.cryo.zombierool.client.ClientSniperHandler;
import org.lwjgl.opengl.GL11;

/** Isolated real client: verify Mixin application and pixels under a controlled ocular stencil. */
@Mod("hotfixtests") public final class TacZClientRenderTest {
    private boolean done;
    private boolean worldRequested;
    public TacZClientRenderTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent e){
        var mc=Minecraft.getInstance();
        if(done||e.phase!=TickEvent.Phase.END||mc.getOverlay()!=null)return;
        if(!worldRequested){
            worldRequested=true;
            mc.options.pauseOnLostFocus=false;
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            mc.createWorldOpenFlows().loadLevel(mc.screen,"AnimationFixture");
            return;
        }
        if(mc.player==null||mc.level==null||mc.isPaused())return;
        try { if (!GunModeSwitchClientTest.tick()) return; } catch (Throwable failure) { failure.printStackTrace(); done=true; mc.stop(); return; }
        done=true;
        if(Boolean.getBoolean("zr.photon.fixture")){FeedbackShaderScene.start();return;}
        try{
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            CareerPersistenceTest.run();
            PlayerAnimationRenderTest.run();
            KnuckleClientTest.run();
            Oct14ClientTest.run();
            EquipmentClientRenderTest.run();
            SoulObjectiveClientTest.run();
            Oct12ClientTest.run();
            SpecialEnemyClientTest.run();
            var rackBlock=me.cryo.zombierool.block.system.BlindBuySystem.RACK.get();
            var rack=new me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetBlockEntity(net.minecraft.core.BlockPos.ZERO,rackBlock.defaultBlockState());
            rack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("rack item handler")).insertItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND),false);
            int[] draws={0};
            var renderer=new me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetRenderer(null);
            var recording=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->method.getReturnType()==com.mojang.blaze3d.vertex.VertexConsumer.class?proxy:null);
            net.minecraft.client.renderer.MultiBufferSource buffer=type->{draws[0]++;return recording;};
            renderer.render(rack,0,new com.mojang.blaze3d.vertex.PoseStack(),buffer,15728880,0);
            if(draws[0]!=0)throw new AssertionError("Closed secret rack must submit no weapon geometry, even at full brightness");
            rack.setBlockState(rackBlock.defaultBlockState().setValue(me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetBlock.OPEN,true));
            renderer.render(rack,0,new com.mojang.blaze3d.vertex.PoseStack(),buffer,15728880,0);
            if(draws[0]==0)throw new AssertionError("Bought rack must reveal the weapon geometry");
            System.out.println("TACZ CLIENT CHECK: secret rack hides every weapon vertex before purchase and reveals it afterward");
            var json=java.nio.file.Files.readString(java.nio.file.Path.of("tacz/tacz_default_gun/assets/tacz/geo_models/attachment/scope_mk5hd_geo.json"));
            var pojo=com.tacz.guns.client.resource.ClientAssetsManager.GSON.fromJson(json,com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO.class);
            var model=com.tacz.guns.client.resource.index.ClientAttachmentIndex.getAttachmentModel(pojo);
            var hook=java.util.Arrays.stream(model.getClass().getDeclaredMethods()).filter(m->m.getName().endsWith("zr$screamer")&&m.getParameterCount()==1&&m.getParameterTypes()[0]==org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class).findFirst().orElseThrow();
            hook.setAccessible(true);
            System.out.println("TACZ CLIENT CHECK: real ocular class transformed and screamer hook present");
            var target=new com.mojang.blaze3d.pipeline.TextureTarget(256,256,true,Minecraft.ON_OSX);target.enableStencil();target.bindWrite(true);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glStencilMask(255);
            GL11.glClearColor(0,0,1,1);GL11.glClearStencil(0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_STENCIL_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(64,64,128,128);GL11.glClearStencil(254);GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilMask(0);GL11.glStencilOp(GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);ClientSniperHandler.screamerTimer=40;ClientSniperHandler.screamerInTaczScope=true;
            hook.invoke(model,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("renderOcularAndDivision",false));
            var pixels=org.lwjgl.BufferUtils.createByteBuffer(256*256*4);GL11.glReadPixels(0,0,256,256,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            int inside=0,outside=0;
            for(int y=0;y<256;y++)for(int x=0;x<256;x++){
                int i=(y*256+x)*4;boolean changed=(pixels.get(i)&255)!=0||(pixels.get(i+1)&255)!=0||(pixels.get(i+2)&255)!=255;
                if(changed){if(x>=64&&x<192&&y>=64&&y<192)inside++;else outside++;}
            }
            if(inside<1000||outside!=0)throw new AssertionError("Screamer pixels inside="+inside+", outside="+outside);
            System.out.println("TACZ CLIENT CHECK: visible screamer pixels="+inside+", outside ocular mask="+outside);
            GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glStencilMask(255);ClientSniperHandler.screamerTimer=0;target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
            System.out.println("TACZ CLIENT RENDER PASS");
            mc.stop();
        }catch(Throwable error){error.printStackTrace();System.out.println("TACZ CLIENT RENDER FAIL");mc.stop();}
    }
}
