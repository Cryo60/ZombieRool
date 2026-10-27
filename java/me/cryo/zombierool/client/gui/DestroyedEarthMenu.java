package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Matrix4f;
import me.cryo.zombierool.gameplay.SkyObjects;
import me.cryo.zombierool.client.SkyObjectsRenderer;

/** One choice per launch; navigating menus never changes the scene or restarts its music. */
public final class DestroyedEarthMenu {
    public static final boolean ACTIVE=new java.util.Random().nextBoolean();
    private static final long START=net.minecraft.Util.getMillis();
    private static final CompoundTag SCENE=scene();
    private static CompoundTag scene(){
        var result=new CompoundTag();var list=new ListTag();
        var earth=SkyObjects.object("menu_earth","destroyed_earth",159,3,14);
        earth.putDouble("distance",95);earth.putDouble("rotation",35);earth.putDouble("roll",-8);earth.putDouble("spin",2.0);earth.putDouble("tilt",18);list.add(earth);result.put("skyObjects",list);return result;
    }
    public static void draw(GuiGraphics g,int width,int height){
        g.fill(0,0,width,height,0xff03050c);
        float seconds=(net.minecraft.Util.getMillis()-START)/1000f;
        var stars=new java.util.Random(10411);
        for(int i=0;i<180;i++){int x=stars.nextInt(Math.max(1,width)),y=stars.nextInt(Math.max(1,height));int a=(int)(65+60*(.5+.5*Math.sin(seconds*.4+i)));g.fill(x,y,x+1,y+1,(a<<24)|0xb8c8e0);}
        g.flush();
        SkyObjectsRenderer.renderAt(SCENE,new Matrix4f().perspective((float)Math.toRadians(55),width/(float)Math.max(1,height),.05f,2000),new Matrix4f(),-1,-1,seconds);
        g.fillGradient(0,0,width/2,height,0x68000000,0xa0000000);
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,net.minecraft.client.Minecraft.ON_OSX);
    }
    private DestroyedEarthMenu(){}
}
