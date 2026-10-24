package local.atmospherevisual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.client.*;

@net.minecraftforge.fml.common.Mod("atmospherevisual")
public final class GripFeedbackVisualTest {
    private final String[] weapons=Boolean.getBoolean("atmosphere.meleeGrips")?new String[]{"scarlet_halberd","stratholme_claymore","stratholme_runic_greatsword"}:new String[]{"fire_wand","shadow_wand","longbow","stratholme_shortbow","stratholme_warbow","scarlet_axe","scarlet_halberd","stratholme_claymore","stratholme_runic_greatsword","scarlet_crossbow","stratholme_hunting_crossbow","stratholme_heavy_crossbow","stratholme_flintlock","stratholme_musket","stratholme_blunderbuss","stratholme_dwarven_rifle","scarlet_sword"};
    private boolean opened,done;private int age,checks;private volatile Throwable failure;
    public GripFeedbackVisualTest(){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
    private void photo(Minecraft mc,String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    private void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!opened){opened=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        age++;
        if(age==20)mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);level.setDayTime(6000);me.cryo.zombierool.gameplay.MapActors.end(level);for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=64;y<=74;y++)level.setBlock(new net.minecraft.core.BlockPos(x,y,z),y==64?net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}catch(Throwable t){failure=t;}});
        for(int i=0;i<weapons.length;i++){String id=weapons[i];int begin=60+i*65;
            if(age==begin){mc.options.keyUse.setDown(false);mc.options.hideGui=false;mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.gameRenderer.setRenderHand(true);mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(level,.5,65,.5,180,0);p.stopUsingItem();p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,ItemStack.EMPTY);p.getInventory().selected=1;var stack=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool",id)));((WeaponSystem.BaseGunItem)stack.getItem()).initializeIfNeeded(stack);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);});}
            if(age==begin+10){photo(mc,id+"-fp-idle");var stack=mc.player.getMainHandItem();var baked=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);check(MapWeaponPackClient.model(stack,mc.player)!=null,"Map model loaded: "+id);WallModelIconCache.get(stack,baked,ItemDisplayContext.FIXED);}
            if(age==begin+12){mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);}
            if(age==begin+20){photo(mc,id+"-tp-front-idle");var stack=mc.player.getMainHandItem();var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);var icon=WallModelIconCache.get(stack,model,ItemDisplayContext.FIXED);check(icon!=null,"Wall silhouette available: "+id);if(mc.getTextureManager().getTexture(icon.chalk()) instanceof net.minecraft.client.renderer.texture.DynamicTexture texture)texture.getPixels().writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),id+"-chalk.png"));
                if(((WeaponSystem.BaseGunItem)stack.getItem()).getDefinition().weapon_kind.matches("pistol|musket|rifle|blunderbuss|crossbow")){
                    var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);var pose=new com.mojang.blaze3d.vertex.PoseStack();renderer.getModel().translateToHand(net.minecraft.world.entity.HumanoidArm.RIGHT,pose);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));model.getTransforms().getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(false,pose);var forward=new org.joml.Vector3f(0,0,-1);pose.last().normal().transform(forward);check(forward.z<-.3,"Barrel points in front of the player: "+id+" vector="+forward);
                }
            }
            if(age==begin+23){mc.options.keyUse.setDown(mc.player.getMainHandItem().getItem() instanceof me.cryo.zombierool.item.MapBowItem);mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).swing(net.minecraft.world.InteractionHand.MAIN_HAND));}
            if(age==begin+27)photo(mc,id+"-tp-action");
            if(age==begin+30){mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).swing(net.minecraft.world.InteractionHand.MAIN_HAND));}
            if(age==begin+34)photo(mc,id+"-fp-action");
            if(age==begin+43)mc.options.keyUse.setDown(false);
        }
        if(age==60+weapons.length*65){for(String set:java.util.List.of("gambeson","chainmail","plate"))for(String part:java.util.List.of("helmet","chestplate","leggings","boots")){var stack=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool","map_"+set+"_"+part)));check(me.cryo.zombierool.init.CreativeCatalog.category(stack)==me.cryo.zombierool.init.CreativeCatalog.Category.MAP_WEAPONS,"Map armor creative category: "+set+" "+part);}var availableEggs=me.cryo.zombierool.init.ZombieroolModTabs.ZB_RCT.get().getDisplayItems();for(String builtin:me.cryo.zombierool.gameplay.MapActorTemplates.BUILTINS)if(!java.util.Set.of("builtin_zombie","builtin_crawler","builtin_hellhound","builtin_white_knight").contains(builtin))check(availableEggs.stream().filter(egg->egg.hasTag()&&egg.getTag().getString("MapActor").equals(builtin)).count()==1,"One creative egg for the existing enemy: "+builtin);mc.getSingleplayerServer().execute(()->{var variants=new net.minecraft.nbt.CompoundTag();variants.putString("crawlerVariant","geist");variants.putString("specialWaveEnemy","pentagon_thief");me.cryo.zombierool.config.SpecialEnemyConfig.update(mc.getSingleplayerServer().overworld(),variants);});}
        if(age==75+weapons.length*65){var contents=me.cryo.zombierool.init.ZombieroolModTabs.ZB_RCT.get().getDisplayItems();for(String id:java.util.List.of("crawler_spawn_egg","hellhound_spawn_egg"))check(contents.stream().noneMatch(stack->net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath().equals(id)),"Configured original egg hidden: "+id);for(String id:java.util.List.of("builtin_geist","builtin_pentagon_thief"))check(contents.stream().filter(stack->stack.hasTag()&&stack.getTag().getString("MapActor").equals(id)).count()==1,"Exactly one configured replacement egg: "+id);done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" grip, silhouette, category and egg checks");mc.stop();}
    }catch(Throwable t){done=true;t.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
