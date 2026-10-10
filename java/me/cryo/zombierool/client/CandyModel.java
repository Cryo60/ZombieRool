package me.cryo.zombierool.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
/** Small wrapped sweets, built from Minecraft-sized cubes, without new textures. */
public final class CandyModel {
    public static void render(PoseStack pose,MultiBufferSource buffers,int seed){
        int[] colors={0xF45B92,0xF2AF22,0x8EDC44,0xA663DE,0x36BDDE};int color=colors[Math.floorMod(seed,colors.length)];
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        cube(pose,v,-.10f,-.07f,-.07f,.10f,.07f,.07f,color);
        cube(pose,v,-.16f,-.05f,-.045f,-.10f,.05f,.045f,0xFFF0CC);
        cube(pose,v,.10f,-.05f,-.045f,.16f,.05f,.045f,0xFFF0CC);
    }
    private static void cube(PoseStack p,VertexConsumer v,float x,float y,float z,float X,float Y,float Z,int c){
        float[][] points={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        for(int[] face:faces)for(int i:face){float[] a=points[i];v.vertex(p.last().pose(),a[0],a[1],a[2]).color((c>>16)&255,(c>>8)&255,c&255,255).endVertex();}
    }
}
