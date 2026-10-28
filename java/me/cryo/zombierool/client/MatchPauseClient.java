package me.cryo.zombierool.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.gameplay.MatchPause;
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class MatchPauseClient {
 private static Object lastLevel;
 private static long voteDeadline;
 private static CompoundTag state=new CompoundTag();
 public static boolean voting(){return state.getBoolean("voting");}
 public static void showVote(){Minecraft.getInstance().setScreen(new VoteScreen());}
 private static int seconds(){return (int)Math.max(0,(voteDeadline-net.minecraft.Util.getMillis()+999)/1000);}
 private static String voteLabel(){return state.getString("requester")+" requested a "+(state.getBoolean("target")?"pause":"resume")+" | Yes: "+state.getInt("yes")+"/"+state.getInt("needed")+" | "+seconds()+"s";}
 @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post e){if(Minecraft.getInstance().screen==null)banner(e.getGuiGraphics());}
 @SubscribeEvent public static void screen(net.minecraftforge.client.event.ScreenEvent.Render.Post e){banner(e.getGuiGraphics());}
 private static void banner(GuiGraphics g){
  var mc=Minecraft.getInstance();if(mc.level==null||!paused()&&!voting())return;
  int w=mc.getWindow().getGuiScaledWidth(),box=Math.min(w-16,Math.max(230,mc.font.width(voteLabel())+24)),left=(w-box)/2;
  g.pose().pushPose();g.pose().translate(0,0,600);
  g.fill(left,8,left+box,42,0xe6101118);g.fill(left,8,left+box,10,paused()?0xffae2525:0xffdda53b);
  g.drawCenteredString(mc.font,paused()?"MATCH PAUSED":"PAUSE VOTE IN PROGRESS",w/2,14,0xffffff);
  g.drawCenteredString(mc.font,voting()?voteLabel():"All gameplay is frozen. Vote to resume to continue.",w/2,28,0xeeeeee);g.pose().popPose();
 }
 public static boolean paused(){return state.getBoolean("paused");}
 public static void receive(CompoundTag n){
  if(n==null)return;state=n.copy();voteDeadline=net.minecraft.Util.getMillis()+n.getLong("remaining");var mc=Minecraft.getInstance();
  if(paused()||n.getBoolean("canVote"))mc.setScreen(new VoteScreen());
  else if(mc.screen instanceof VoteScreen)mc.setScreen(null);
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(mc.level==null){state=new CompoundTag();lastLevel=null;return;}
  if(lastLevel!=mc.level){lastLevel=mc.level;MatchPause.send("state");}
  if(paused()&&mc.screen==null)mc.setScreen(new VoteScreen());
 }
 public static final class VoteScreen extends Screen {
  public VoteScreen(){super(Component.literal("Match pause"));}
  @Override protected void init(){
   int y=height/2;
   if(state.getBoolean("canVote")){
    addRenderableWidget(Button.builder(Component.literal("Vote Yes"),b->MatchPause.send("yes")).bounds(width/2-105,y+12,100,20).build());
    addRenderableWidget(Button.builder(Component.literal("Vote No"),b->MatchPause.send("no")).bounds(width/2+5,y+12,100,20).build());
   }else if(paused()&&!state.getBoolean("voting"))addRenderableWidget(Button.builder(Component.literal("Vote to resume"),b->MatchPause.send("resume")).bounds(width/2-100,y+12,200,20).build());
   if(paused())addRenderableWidget(Button.builder(Component.literal("Disconnect"),b->{if(minecraft.level!=null)minecraft.level.disconnect();minecraft.clearLevel();minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());}).bounds(width/2-100,y+40,200,20).build());
   else addRenderableWidget(Button.builder(Component.literal("Continue playing"),b->minecraft.setScreen(null)).bounds(width/2-100,y+40,200,20).build());
  }
  @Override public void render(GuiGraphics g,int x,int y,float pt){
   g.fill(0,0,width,height,0xbb101018);g.drawCenteredString(font,paused()?"MATCH PAUSED":"PAUSE VOTE",width/2,height/2-48,0xffffff);
   String label=state.getBoolean("voting")?"Vote to "+(state.getBoolean("target")?"pause":"resume")+": "+state.getInt("yes")+" / "+state.getInt("needed")+" required — "+seconds()+"s remaining":"World simulation is frozen.";
   g.drawCenteredString(font,label,width/2,height/2-24,0xcccccc);super.render(g,x,y,pt);
  }
  @Override public boolean shouldCloseOnEsc(){return !paused();}
  @Override public boolean isPauseScreen(){return false;}
 }
}
