package local.atmospherevisual;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.LunarStation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class EnvironmentalSuitVisualTest {
    boolean requested,done;int age,checks;volatile String failure;net.minecraft.resources.ResourceLocation originalSkin;
    public EnvironmentalSuitVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+label);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t.toString();t.printStackTrace();}});}
    void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/"+name+".png"));}}
    static class Preview extends net.minecraft.client.gui.screens.Screen {
        Preview(){super(net.minecraft.network.chat.Component.literal("Environmental equipment"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
            var mc=Minecraft.getInstance();g.fill(0,0,width,height,0xff19232e);
            var style=LunarEquipment.style(mc.player);
            g.drawCenteredString(font,net.minecraft.network.chat.Component.translatable("item.zombierool."+style.id+"_helmet"),width/2,18,0xfff1ece0);
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g,width/2,height-35,105,-27,0,mc.player);
            var station=switch(style){case DIVING->LunarStation.DIVING_STATION_ITEM.get();case HAZMAT->LunarStation.HAZMAT_STATION_ITEM.get();default->LunarStation.STATION_ITEM.get();};
            g.pose().pushPose();g.pose().translate(50,100,0);g.pose().scale(5,5,5);g.renderItem(new ItemStack(station),0,0);g.pose().popPose();
            g.pose().pushPose();g.pose().translate(width-120,100,0);g.pose().scale(5,5,5);g.renderItem(new ItemStack(LunarStation.helmet(style)),0,0);g.pose().popPose();
        }
    }
    void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null)return;age++;
        if(age==30){originalSkin=mc.player.getSkinTextureLocation();server(p->{p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);p.getInventory().clearContent();MapAtmosphere.get(p.serverLevel()).configure(p.serverLevel(),MapAtmosphere.defaults());for(var pos:BlockPos.betweenClosed(95,79,94,107,84,108))p.serverLevel().setBlock(pos,pos.getY()==79?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);p.teleportTo(p.serverLevel(),100.5,80,100.5,180,10);});}
        if(age==40&&Boolean.getBoolean("suits.skyRegression"))age=400;
        for(var style:LunarEquipment.SuitStyle.values()){
            int start=55+style.ordinal()*120;
            if(age==start)server(p->{var at=new BlockPos(100,80,98);var station=switch(style){case DIVING->LunarStation.DIVING_STATION.get();case HAZMAT->LunarStation.HAZMAT_STATION.get();default->LunarStation.STATION.get();};p.serverLevel().setBlock(at,station.defaultBlockState(),3);((LunarStation.StationBlock)station).setPlacedBy(p.serverLevel(),at,station.defaultBlockState(),p,ItemStack.EMPTY);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(at.above()));});
            if(age==start+15){var at=new BlockPos(100,81,98);var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.SOUTH,at,false);mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);}
            if(age==start+30)shot(style.id+"-equip-animation");
            if(age==start+65){check(LunarEquipment.equipped(mc.player)&&LunarEquipment.style(mc.player)==style,style+" station upper half equips and syncs its variant");check(mc.player.getSkinTextureLocation().equals(me.cryo.zombierool.client.EnvironmentalSuitVisuals.texture(mc.player)),style+" skin substitution also reaches first-person arms");var icon=new ItemStack(LunarStation.helmet(style));check(mc.getItemRenderer().getModel(icon,mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),style+" equipment has an inventory model");for(var item:java.util.List.of(LunarStation.helmet(style),switch(style){case DIVING->LunarStation.DIVING_STATION_ITEM.get();case HAZMAT->LunarStation.HAZMAT_STATION_ITEM.get();default->LunarStation.STATION_ITEM.get();})){
                    var model=mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0);
                    check(model.getQuads(null,null,net.minecraft.util.RandomSource.create()).stream().noneMatch(q->q.getSprite().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())),style+" item/station textures are present in the block atlas");
                }mc.setScreen(new Preview());}
            if(age==start+70)shot(style.id+"-equipment");
            if(age==start+75){mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);}
            if(age==start+80)shot(style.id+"-back");
            if(age==start+82){mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);LunarEquipment.requestToggle();}
            if(age==start+110){check(!LunarEquipment.equipped(mc.player)&&mc.player.getSkinTextureLocation().equals(originalSkin),style+" removal restores original skin");}
        }
        if(age==415){
            var drop=mc.particleEngine.createParticle(me.cryo.zombierool.init.ZombieroolModParticleTypes.BLOOD_STAIN.get(),100.5,81,100.5,.05,.1,.05);
            check(drop instanceof me.cryo.zombierool.client.particle.BloodStainParticle,"Blood provider creates a combat drop on dry ground");
            var sprite=net.minecraft.client.particle.TextureSheetParticle.class.getDeclaredField("sprite");sprite.setAccessible(true);
            check(((net.minecraft.client.renderer.texture.TextureAtlasSprite)sprite.get(drop)).contents().name().toString().equals("zombierool:blood_drop"),"Blood provider resolves the expected resource");
            var textureId=new net.minecraft.resources.ResourceLocation("zombierool:textures/environment/highrise_sky.png");
            int sky=mc.getTextureManager().getTexture(textureId).getId();
            int active=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
            com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE3);org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE3);com.mojang.blaze3d.systems.RenderSystem.bindTexture(sky);org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,sky);
            var legacy=new net.minecraft.client.renderer.texture.DynamicTexture(8,8,true);
            boolean leaked=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D)!=sky;
            System.out.println("BLOOD SKY REGRESSION: unguarded dynamic allocation changes sky sampler = "+leaked);
            legacy.close();com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE3);org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE3);org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,sky);com.mojang.blaze3d.systems.RenderSystem.bindTexture(sky);
            var tileClass=Class.forName("me.cryo.zombierool.client.BloodSurfaceRenderer$Tile");var constructor=tileClass.getDeclaredConstructor();constructor.setAccessible(true);var tile=constructor.newInstance();
            check(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE)==org.lwjgl.opengl.GL13.GL_TEXTURE3&&org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D)==sky,"Creating a blood surface preserves the skybox sampler binding");
            var faceClass=Class.forName("me.cryo.zombierool.client.BloodSurfaceRenderer$Face");var faceConstructor=faceClass.getDeclaredConstructor(BlockPos.class,Direction.class,long.class);faceConstructor.setAccessible(true);var face=faceConstructor.newInstance(new BlockPos(100,79,100),Direction.UP,80L*4096);
            var raster=me.cryo.zombierool.client.BloodSurfaceRenderer.class.getDeclaredMethod("raster",tileClass,faceClass,java.util.List.class);raster.setAccessible(true);raster.invoke(null,tile,face,java.util.List.of());
            check(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE)==org.lwjgl.opengl.GL13.GL_TEXTURE3&&org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D)==sky,"Updating a blood surface preserves the skybox sampler binding");
            var close=tileClass.getDeclaredMethod("close");close.setAccessible(true);close.invoke(tile);com.mojang.blaze3d.systems.RenderSystem.activeTexture(active);org.lwjgl.opengl.GL13.glActiveTexture(active);

        }
        if(age==420)server(p->{var n=new net.minecraft.nbt.CompoundTag();me.cryo.zombierool.config.WorldConfig.get(p.serverLevel()).saveEditable(n);me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket.addLootTables(n,p.serverLevel());mc.execute(()->mc.setScreen(new me.cryo.zombierool.client.gui.WorldConfigMenuScreen(new me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket(n,false,0))));});
        if(age==425){for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("config.zombierool.tab.player").getString())){button.onPress();break;}}
        if(age==428){boolean found=false;for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.ContainerObjectSelectionList<?> list)for(var entry:list.children())for(var control:entry.children())if(control instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().startsWith(net.minecraft.network.chat.Component.translatable("config.zombierool.startingSuit").getString())){button.onPress();found=true;break;}check(found,"Map Config Player tab opens the starting-suit list");}
        if(age==430){int count=0;boolean picked=false;for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.ObjectSelectionList<?> list){count=list.children().size();for(var row:list.children())if(row.getNarration().getString().equals("Plongée")){row.mouseClicked(180,80,0);picked=true;break;}}check(count==4&&picked,"Starting-suit selector has exactly None, Astronaut, Diving and Hazmat");}
        if(age==432){shot("starting-suit-menu");for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().equals(net.minecraft.network.chat.Component.translatable("config.zombierool.apply"))){button.onPress();break;}}
        if(age==440)server(p->{var config=me.cryo.zombierool.config.WorldConfig.get(p.serverLevel());check(config.getAddonConfig().getCompound("zombierool:lunar").getString("startingSuit").equals("diving"),"Map Config persists the selected starting suit through the server");MatchPlayerReset.applyStartLoadout(p,config,1);});
        if(age==450){check(LunarEquipment.equipped(mc.player)&&LunarEquipment.style(mc.player)==LunarEquipment.SuitStyle.DIVING&&!LunarEquipment.busy(mc.player),"Match loadout arrives already equipped without an equip animation");server(p->LunarEquipment.revoke(p));mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);server(p->{p.teleportTo(p.serverLevel(),100.5,80,104.5,180,12);});}
        if(age>=465&&age<470||age>=485&&age<490){for(int i=0;i<15;i++)mc.level.addParticle(me.cryo.zombierool.init.ZombieroolModParticleTypes.BLOOD_STAIN.get(),100.5+(i%5-2)*.18,81+(i/5)*.12,102.5,0,.03,0);}
        if(age==468)shot("blood-dry-ground");
        if(age==475){me.cryo.zombierool.client.MapAtmosphereClient.receive("state",MapAtmosphere.preset("die_rise"));}
        if(age==488)shot("blood-dry-ground-skybox");
        if(age==500){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" environmental suit checks");mc.stop();}
    }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
