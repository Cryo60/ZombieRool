package me.cryo.zombierool.client;

import me.cryo.zombierool.client.gui.BuildableScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="zombierool", value=Dist.CLIENT)
public final class BuildableClient {
    private static final Map<BlockPos,CompoundTag> TARGETS=new HashMap<>();
    private static String carried="",delivery="";
    private static net.minecraft.nbt.ListTag catalog=new net.minecraft.nbt.ListTag();
    private static int elapsed,duration,page;
    private static long receivedTick;
    private static net.minecraft.world.item.ItemStack icon=net.minecraft.world.item.ItemStack.EMPTY;
    private static BlockPos heldTarget;
    public static boolean busy;
    private static boolean paused,installing;
    public static void receive(boolean menu,CompoundTag n) {
        if(n==null)return;
        if(menu){
            var mc=Minecraft.getInstance();if(n.getBoolean("close")){mc.setScreen(null);return;}
            if(mc.screen instanceof BuildableScreen screen)screen.update(n);
            else mc.setScreen(new BuildableScreen(n));
            return;
        }
        TARGETS.clear(); for(var t:n.getList("targets",Tag.TAG_COMPOUND)){var entry=(CompoundTag)t;TARGETS.put(BlockPos.of(entry.getLong("pos")),entry);}
        delivery=n.getString("delivery");installing=n.getBoolean("installing");paused=n.getBoolean("paused");catalog=n.getList("catalog",Tag.TAG_COMPOUND);elapsed=n.getInt("elapsed");duration=n.getInt("duration");receivedTick=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        carried=n.getString("carried");busy=n.getBoolean("busy");icon=net.minecraft.world.item.ItemStack.of(n.getCompound("icon"));
        if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.getPersistentData().putBoolean("zr_buildable_busy",busy);
    }
    public static Component hint(BlockPos pos,String key) {
        var n=TARGETS.get(pos);if(n==null)return null;
        if(n.getBoolean("destination"))return n.getBoolean("ready")?Component.translatable("message.zombierool.buildable.place",key,net.minecraft.world.item.ItemStack.of(n.getCompound("reward")).getHoverName()):Component.empty();
        if(!n.getBoolean("bench"))return Component.empty();
        String name=n.getString("name");
        if(n.getBoolean("exhausted"))return Component.translatable("message.zombierool.buildable.exhausted");
        if(n.getInt("total")==0)return Component.translatable(carried.isEmpty()?"message.zombierool.buildable.need_part":"message.zombierool.buildable.add",key);
        if(n.getBoolean("complete"))return Component.translatable("message.zombierool.buildable.take",key,name);
        return Component.translatable("message.zombierool.buildable.bench_short",key,name,n.getInt("installed"),n.getInt("total"));
    }
    public static boolean requiresHold(BlockPos pos){var target=TARGETS.get(pos);return target!=null&&(target.getBoolean("destination")&&target.getBoolean("ready")||target.getBoolean("bench")&&!target.getBoolean("complete"));}
    public static boolean isPickup(BlockPos pos){var target=TARGETS.get(pos);return target!=null&&target.getBoolean("pickup");}
    public static boolean aimedPickup(net.minecraft.world.entity.player.Player player,BlockPos pos){
        var target=TARGETS.get(pos);if(target==null||!target.getBoolean("pickup"))return false;var b=target.getCompound("bounds");
        return me.cryo.zombierool.gameplay.BuildableSystem.aimedPickup(player,pos,new net.minecraft.world.phys.AABB(b.getDouble("x0"),b.getDouble("y0"),b.getDouble("z0"),b.getDouble("x1"),b.getDouble("y1"),b.getDouble("z1")));
    }
    public static void updateHold(boolean down,BlockPos pos){
        var mc=Minecraft.getInstance();boolean allowed=down&&pos!=null&&requiresHold(pos)&&mc.player!=null;
        if(heldTarget!=null&&(!allowed||!heldTarget.equals(pos)))cancelHold();
        if(allowed){boolean first=heldTarget==null;heldTarget=pos;if(first||mc.player.tickCount%4==0)me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendToServer(new me.cryo.zombierool.network.packet.C2SUnifiedInteractPacket(pos,me.cryo.zombierool.network.InteractionType.BUILDABLE_HOLD));}
    }
    public static void cancelHold(){
        if(heldTarget!=null&&Minecraft.getInstance().getConnection()!=null)me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendToServer(new me.cryo.zombierool.network.packet.C2SUnifiedInteractPacket(heldTarget,me.cryo.zombierool.network.InteractionType.BUILDABLE_CANCEL));
        heldTarget=null;
    }
    public static boolean readyToInstall(BlockPos pos){var target=TARGETS.get(pos);return target!=null&&target.getBoolean("destination")&&target.getBoolean("ready");}
    public static boolean hasTarget(BlockPos pos){return TARGETS.containsKey(pos);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){heldTarget=null;TARGETS.clear();carried=delivery="";busy=installing=false;icon=net.minecraft.world.item.ItemStack.EMPTY;catalog.clear();page=elapsed=duration=0;}
    @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui||mc.screen!=null||mc.options.keyPlayerList.isDown())return;
        var graphics=e.getGuiGraphics();
        if(!icon.isEmpty()&&(!carried.isEmpty()||!delivery.isEmpty())){
            int panel=Math.min(190,mc.getWindow().getGuiScaledWidth()-20),left=mc.getWindow().getGuiScaledWidth()-panel-10,top=43;
            graphics.fill(left,top,left+panel,top+31,0xC015181C);graphics.fill(left,top,left+2,top+31,0xFFF4B743);
            graphics.renderItem(icon,left+7,top+8);
            graphics.drawString(mc.font,Component.translatable(delivery.isEmpty()?"gui.zombierool.buildable.carry_component":"gui.zombierool.buildable.carry_machine"),left+30,top+5,0xF4B743);
            graphics.drawString(mc.font,mc.font.plainSubstrByWidth(icon.getHoverName().getString(),panel-36),left+30,top+17,0xEEEEEE);
        }
        if(!busy||duration<=0)return;
        float progress=Math.min(1,(elapsed+(paused?0:Math.max(0,mc.level.getGameTime()-receivedTick)+mc.getFrameTime()))/duration);
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),x=(w-150)/2,y=h/2+55;
        var g=e.getGuiGraphics();g.fill(x,y,x+150,y+10,0xCC151515);g.fill(x+1,y+1,x+1+(int)(148*progress),y+9,0xFFFFB34A);
        g.drawCenteredString(mc.font,Component.translatable(installing?"message.zombierool.buildable.installing_bar":"message.zombierool.buildable.assembling_bar"),w/2,y-mc.font.lineHeight-3,0xFFFFFFFF);
    }
    @SubscribeEvent public static void hideInstallationOutline(net.minecraftforge.client.event.RenderHighlightEvent.Block e){
        var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getBlockState(e.getTarget().getBlockPos()).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get())&&!readyToInstall(e.getTarget().getBlockPos())&&!me.cryo.zombierool.client.renderer.BuildablePartRenderer.ghostVisible(mc.player,me.cryo.zombierool.gameplay.WaveManager.isGameRunning()))e.setCanceled(true);
    }
    private record RecipeStrip(String title,List<CompoundTag> pieces,boolean quest) {}
    public static int drawTab(net.minecraft.client.gui.GuiGraphics g,int width,int top,int availableHeight){return drawTab(g,width,top,availableHeight,Map.of(),Map.of());}
    public static int drawTab(net.minecraft.client.gui.GuiGraphics g,int width,int top,int availableHeight,Map<String,Integer> collected,Map<String,Integer> totals){
        int keyHeight=PortableEquipmentClient.drawKeyTab(g,width,top);top+=keyHeight;availableHeight-=keyHeight;
        var mc=Minecraft.getInstance();int panel=Math.min(720,width-40),left=(width-panel)/2;
        var groups=new ArrayList<RecipeStrip>();
        var byRecipe=new LinkedHashMap<String,List<CompoundTag>>();
        for(var tag:catalog){var n=(CompoundTag)tag;byRecipe.computeIfAbsent(n.getString("recipe"),k->new ArrayList<>()).add(n);}
        for(var pieces:byRecipe.values())groups.add(new RecipeStrip(pieces.get(0).getString("recipeName"),pieces,false));
        if(groups.isEmpty())return keyHeight;
        // Split oversized recipes into strips, then pack whole recipe groups into rows.
        var strips=new ArrayList<RecipeStrip>();int maxSlots=Math.max(1,(panel-12)/30);
        for(var group:groups)for(int i=0;i<group.pieces.size();i+=maxSlots)strips.add(new RecipeStrip(group.title,group.pieces.subList(i,Math.min(i+maxSlots,group.pieces.size())),group.quest));
        var rows=new ArrayList<List<RecipeStrip>>();var row=new ArrayList<RecipeStrip>();int used=0;
        for(var group:strips){int size=Math.max(108,group.pieces.size()*30+12);size=Math.max(size,Math.min(panel,mc.font.width(group.title)+12));
            if(used+size>panel&&!row.isEmpty()){rows.add(row);row=new ArrayList<>();used=0;}row.add(group);used+=size+8;
        }if(!row.isEmpty())rows.add(row);
        int visibleRows=Math.max(1,(availableHeight-38)/67),pages=Math.max(1,(rows.size()+visibleRows-1)/visibleRows);page=Math.floorMod(page,pages);
        int count=Math.min(visibleRows,rows.size()-page*visibleRows),height=22+count*67;
        g.fill(left,top,left+panel,top+height,0xA8000000);
        g.drawString(mc.font,Component.translatable("gui.zombierool.buildable.tab_recipes"),left+5,top+5,0xDEDEDE);
        if(pages>1)g.drawString(mc.font,(page+1)+"/"+pages+" · "+Component.translatable("gui.zombierool.buildable.scroll").getString(),left+panel-100,top+5,0xAAAAAA);
        for(int r=0;r<count;r++){
            int x=left,y=top+22+r*67;
            for(var group:rows.get(page*visibleRows+r)){
                int size=Math.max(108,group.pieces.size()*30+12);size=Math.max(size,Math.min(panel,mc.font.width(group.title)+12));
                g.fill(x,y,x+size,y+61,0x7A000000);g.drawString(mc.font,group.title,x+5,y+3,0xE2E2E2);
                for(int i=0;i<group.pieces.size();i++){
                    var n=group.pieces.get(i);String status=n.getString("status");int color=status.equals("installed")?0xCCCCCC:status.equals("self")?0xF4B743:0x444444;int sx=x+5+i*30,sy=y+17;
                    g.fill(sx,sy,sx+27,sy+32,0x8F111111);g.renderOutline(sx,sy,27,32,0xFF000000|color);
                    var stack=net.minecraft.world.item.ItemStack.of(n.getCompound("icon"));g.pose().pushPose();g.pose().translate(sx+3,sy+4,0);g.pose().scale(1.3f,1.3f,1.3f);g.renderItem(stack,0,0);g.pose().popPose();
                    if(!status.equals("installed")){g.pose().pushPose();g.pose().translate(0,0,300);g.fill(sx+1,sy+1,sx+26,sy+31,0xCC333333);g.pose().popPose();if(status.equals("self")){g.pose().pushPose();g.pose().translate(0,0,310);g.renderOutline(sx,sy,27,32,0xFFF4B743);g.fill(sx+20,sy+2,sx+25,sy+7,0xFFF4B743);g.pose().popPose();}}
                    else {g.fill(sx+1,sy+29,sx+26,sy+31,0xFF000000|color);if(status.equals("installed"))g.drawString(mc.font,"+",sx+19,sy+21,color);}
                }x+=size+8;
            }
        }
        return keyHeight+height+12;
    }
    @SubscribeEvent public static void scroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent e){
        if(Minecraft.getInstance().options.keyPlayerList.isDown()&&!catalog.isEmpty()){page+=e.getScrollDelta()>0?-1:1;e.setCanceled(true);}
    }
}
