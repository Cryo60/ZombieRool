package me.cryo.zombierool.client.gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
public final class RewardEventsScreen extends Screen {
 private final Screen parent;private java.util.List<me.cryo.zombierool.client.career.RewardEvents.Event> events=java.util.List.of();private String status="";private EditBox url;
 public RewardEventsScreen(Screen parent){super(Component.literal("Reward events"));this.parent=parent;}
 @Override protected void init(){url=addRenderableWidget(new EditBox(font,16,36,width-120,20,Component.literal("Raw public Gist URL")));url.setMaxLength(2048);url.setValue(me.cryo.zombierool.client.career.RewardEvents.url());addRenderableWidget(Button.builder(Component.literal("Refresh"),b->{try{me.cryo.zombierool.client.career.RewardEvents.saveUrl(url.getValue());status="Loading...";me.cryo.zombierool.client.career.RewardEvents.fetch((list,message)->{events=list;status=message;if(minecraft.screen==this)rebuildWidgets();});}catch(Exception e){status=e.getMessage();}}).bounds(width-96,36,80,20).build());int y=76;for(var event:events.stream().limit(Math.max(1,(height-140)/24)).toList()){addRenderableWidget(Button.builder(Component.literal(event.name()),b->{status=me.cryo.zombierool.client.career.RewardVoucher.redeem(event.code()).getString();}).bounds(16,y,width-32,20).build());y+=24;}addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-96,height-28,80,20).build());}
 @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float p){renderBackground(g);g.fill(0,0,width,height,0xFF10151D);g.drawCenteredString(font,title,width/2,12,0xFFFFFF);super.render(g,x,y,p);g.drawString(font,font.plainSubstrByWidth(status,width-30),16,height-50,0xCCBBAA);}
 @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
