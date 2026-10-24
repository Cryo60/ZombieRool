package me.cryo.zombierool.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.client.career.LocalCareerManager;
public final class TabChallenges {
 private TabChallenges(){}
 public static void draw(GuiGraphics g,Minecraft mc,int x,int y,int width,int bottom){
  var data=LocalCareerManager.getData();var entries=new java.util.ArrayList<>(data.activeChallenges.entrySet());entries.sort(java.util.Map.Entry.comparingByKey());
  if(entries.isEmpty()||y+40>bottom)return;
  int cols=width>=520?2:1,cell=width/cols,rows=(entries.size()+cols-1)/cols,space=bottom-y-20,row=Math.min(33,space/Math.max(1,rows));if(row<18)return;
  g.fill(x,y,x+width,y+18+row*rows,0xB0101316);g.drawString(mc.font,Component.translatable("gui.zombierool.career.tab.challenges"),x+7,y+5,0xFFE0CC91);y+=18;
  for(int i=0;i<entries.size();i++){
   var e=entries.get(i);var d=e.getValue();int progress=Math.min(d.target,data.challengeProgress.getOrDefault(e.getKey(),0));boolean done=data.challengeCompleted.getOrDefault(e.getKey(),false);int px=x+i%cols*cell+7,py=y+i/cols*row;
   var label=Component.translatable("gui.zombierool.career.challenge."+d.type.name().toLowerCase(java.util.Locale.ROOT),d.target,d.context==null?"":d.context.toUpperCase(java.util.Locale.ROOT));
   String count=done?"✓":progress+" / "+d.target;int tw=mc.font.width(count);g.drawString(mc.font,mc.font.plainSubstrByWidth(label.getString(),cell-tw-25),px,py+3,0xFFE0E0D8);g.drawString(mc.font,count,px+cell-tw-15,py+3,done?0xFF79D79B:0xFFD4BD7D);
   g.fill(px,py+14,px+cell-16,py+17,0xFF353939);g.fill(px,py+14,px+Math.round((cell-16)*Math.min(1,progress/(float)Math.max(1,d.target))),py+17,0xFF9BCDA6);
  }
 }
}
