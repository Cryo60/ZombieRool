package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.gameplay.LunarEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class LunarEquipmentClient {
    private record Surface(ResourceLocation texture,float[][] vertices,boolean visor){}
    private static List<Surface> legacyHelmet;
    public static final KeyMapping TOGGLE=new KeyMapping("key.zombierool.suit",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_J,"category.zombierool.keys");
    public static final KeyMapping PORTABLE=new KeyMapping("key.zombierool.lunar_helmet",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,"category.zombierool.keys");
    private static CompoundTag pendingEnvironment;
    public static void environment(CompoundTag data){var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().toString().equals(data.getString("dimension")))pendingEnvironment=data.copy();else me.cryo.zombierool.gameplay.LunarPhysics.receive(level,data);}
    private static final Map<UUID,CompoundTag> pending=new HashMap<>();
    public static void receive(UUID uuid,CompoundTag data){var level=Minecraft.getInstance().level;var player=level==null?null:level.getPlayerByUUID(uuid);if(player!=null){player.getPersistentData().put(LunarEquipment.KEY,data.copy());if(!data.getBoolean("owned")){ThirdPersonAnimHandler.stopAnim(uuid,"helmet_equip");ThirdPersonAnimHandler.stopAnim(uuid,"helmet_remove");}}else pending.put(uuid,data.copy());}
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){pending.clear();pendingEnvironment=null;ThirdPersonAnimHandler.reset();if(e.getPlayer()!=null)e.getPlayer().getPersistentData().remove(LunarEquipment.KEY);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        if(pendingEnvironment!=null&&mc.level.dimension().location().toString().equals(pendingEnvironment.getString("dimension"))){me.cryo.zombierool.gameplay.LunarPhysics.receive(mc.level,pendingEnvironment);pendingEnvironment=null;}
        pending.entrySet().removeIf(entry->{var p=mc.level.getPlayerByUUID(entry.getKey());if(p==null)return false;p.getPersistentData().put(LunarEquipment.KEY,entry.getValue());return true;});
        var state=LunarEquipment.data(mc.player);LunarEquipment.applyGravity(mc.player,me.cryo.zombierool.gameplay.LunarPhysics.gravityAt(mc.player));
        while(PORTABLE.consumeClick())if(mc.screen==null&&!ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID()))me.cryo.zombierool.gameplay.PortableEquipmentPackets.requestToggle();
        while(TOGGLE.consumeClick())if(mc.screen==null&&!ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID()))LunarEquipment.requestToggle();
    }
    private static List<Surface> loadLegacyHelmet(){
        var id=me.cryo.zombierool.config.EnemySkinPresets.gearMesh("@preset/zombies/moon/3");var result=new ArrayList<Surface>();
        try(var raw=LunarEquipmentClient.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath());var reader=new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(raw),java.nio.charset.StandardCharsets.UTF_8)){
            var root=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();for(var value:root.getAsJsonArray("surfaces")){var s=value.getAsJsonObject();if(!"head".equals(s.get("part").getAsString()))continue;
                var array=s.getAsJsonArray("vertices");float[][] vertices=new float[array.size()][8];for(int i=0;i<vertices.length;i++)for(int j=0;j<8;j++)vertices[i][j]=array.get(i).getAsJsonArray().get(j).getAsFloat();
                result.add(new Surface(new ResourceLocation(s.get("texture").getAsString()),vertices,result.size()==1));
            }
        }catch(Exception e){throw new IllegalStateException("Cannot load lunar helmet",e);}return List.copyOf(result);
    }
    /** Preserve the original lunar mesh; cosmetic variants use their own authored helmets. */
    public static void renderHelmet(PoseStack pose,MultiBufferSource buffers,int light){
        renderHelmet(pose,buffers,light,Minecraft.getInstance().player);
    }
    public static void renderHelmet(PoseStack pose,MultiBufferSource buffers,int light,net.minecraft.world.entity.player.Player player){
        var style=player==null?LunarEquipment.SuitStyle.LUNAR:LunarEquipment.style(player);
        if(style!=LunarEquipment.SuitStyle.LUNAR){EnvironmentalSuitVisuals.helmet(pose,buffers,light,style);return;}
        if(legacyHelmet==null)legacyHelmet=loadLegacyHelmet();var matrix=pose.last();for(var surface:legacyHelmet){var out=buffers.getBuffer(surface.visor?RenderType.entityTranslucent(surface.texture):RenderType.entityCutoutNoCull(surface.texture));
            for(int i=0;i<surface.vertices.length;i+=3)for(int c=0;c<4;c++){var v=surface.vertices[i+Math.min(c,2)];out.vertex(matrix.pose(),v[0]/16,v[1]/16,v[2]/16).color(1f,1f,1f,surface.visor?.22f:1f).uv(v[3],v[4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix.normal(),v[5],v[6],v[7]).endVertex();}
        }
    }
    public static boolean helmetAnimation(net.minecraft.world.entity.player.Player p){String anim=ThirdPersonAnimHandler.currentAnim(p.getUUID());return ("helmet_equip".equals(anim)||"helmet_remove".equals(anim))&&ThirdPersonAnimHandler.isAnimationPlaying(p.getUUID());}
    public static boolean showProp(net.minecraft.world.entity.player.Player p){String anim=ThirdPersonAnimHandler.currentAnim(p.getUUID());int tick=ThirdPersonAnimHandler.animationTick(p.getUUID());return "helmet_equip".equals(anim)?tick<LunarEquipment.SEAL_TICK:"helmet_remove".equals(anim)&&tick>=LunarEquipment.UNSEAL_TICK;}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST,receiveCanceled=true) public static void hand(RenderHandEvent e){var mc=Minecraft.getInstance();if(mc.player!=null&&helmetAnimation(mc.player))LunarFirstPersonAnimation.render(e);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void hud(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;var n=LunarEquipment.data(mc.player);int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();var g=e.getGuiGraphics();
        if(n.getBoolean("needsOxygen")&&!n.getBoolean("equipped")&&!mc.player.isCreative()&&!mc.player.isSpectator()){
            int capacity=Math.max(1,n.getInt("capacity")),air=n.getInt("air");g.fill(w/2-51,h-64,w/2+51,h-57,0xcc121820);g.fill(w/2-50,h-63,w/2-50+Math.round(100f*air/capacity),h-58,air<capacity/3?0xffd54c43:0xff70b9dc);
            g.drawCenteredString(mc.font,net.minecraft.network.chat.Component.translatable("hud.zombierool.oxygen",(air+19)/20),w/2,h-76,0xffe9eef3);
        }
    }
    /** Independent suit and portable slots, each with its own binding. */
    public static void equipmentIcon(net.minecraft.client.gui.GuiGraphics g,Minecraft mc,net.minecraft.world.entity.player.Player p,int x,int y,boolean compact){
        var state=LunarEquipment.data(p);var portable=me.cryo.zombierool.gameplay.PortableEquipment.carried(p);
        if(!portable.isEmpty())slotIcon(g,mc,portable,x,y,compact,PORTABLE,me.cryo.zombierool.gameplay.PortableEquipment.inHands(p)?0xff96dad1:0xff777777);
        if(state.getBoolean("owned"))slotIcon(g,mc,new net.minecraft.world.item.ItemStack(me.cryo.zombierool.block.system.LunarStation.helmet(LunarEquipment.style(p))),x-(compact?30:48),y,compact,TOGGLE,state.getBoolean("changing")?0xffe6bd68:state.getBoolean("equipped")?0xff96dad1:0xff777777);
    }
    private static void slotIcon(net.minecraft.client.gui.GuiGraphics g,Minecraft mc,net.minecraft.world.item.ItemStack stack,int x,int y,boolean compact,KeyMapping binding,int color){g.renderItem(stack,x,y);g.fill(x,y+17,x+16,y+19,color);if(compact){g.pose().pushPose();g.pose().translate(x+22,y-10,0);g.pose().scale(.65f,.65f,1);String key=binding.getTranslatedKeyMessage().getString();g.drawString(mc.font,key,-mc.font.width(key),0,0xffbab8af,true);g.pose().popPose();}else g.drawString(mc.font,binding.getTranslatedKeyMessage(),x+18,y+8,color,false);}
    @SubscribeEvent public static void visor(RenderGuiEvent.Pre e){var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||!LunarEquipment.equipped(mc.player)||!mc.options.getCameraType().isFirstPerson())return;var g=e.getGuiGraphics();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();com.mojang.blaze3d.systems.RenderSystem.depthMask(false);g.pose().pushPose();g.blit(new ResourceLocation("zombierool","textures/gui/moon_visor.png"),0,0,w,h,0,0,1024,512,1024,512);g.pose().popPose();g.flush();com.mojang.blaze3d.systems.RenderSystem.depthMask(true);com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
    public static final class HelmetLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
        public HelmetLayer(PlayerRenderer parent){super(parent);}
        @Override public void render(PoseStack pose,MultiBufferSource buffer,int light,AbstractClientPlayer p,float a,float b,float partial,float age,float yaw,float pitch){
            if(p.isInvisible()||!LunarEquipment.equipped(p))return;
            EnvironmentalSuitVisuals.outfit(pose,buffer,light,p,getParentModel());
            pose.pushPose();getParentModel().head.translateAndRotate(pose);
            if(LunarEquipment.style(p)==LunarEquipment.SuitStyle.LUNAR){pose.translate(0,-.25,0);pose.scale(1.22f,1.22f,1.22f);pose.translate(0,.25,0);}
            renderHelmet(pose,buffer,light,p);pose.popPose();
        }
    }
    @Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(TOGGLE);e.register(PORTABLE);}
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e){for(String skin:e.getSkins()){if(e.getSkin(skin) instanceof PlayerRenderer renderer)renderer.addLayer(new HelmetLayer(renderer));}}
    }
    private LunarEquipmentClient(){}
}
