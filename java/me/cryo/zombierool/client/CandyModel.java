package me.cryo.zombierool.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
/** Small wrapped sweets, built from Minecraft-sized cubes, without new textures. */
public final class CandyModel {
    private static final int[] COLORS={0xF45B92,0xF2AF22,0x8EDC44,0xA663DE,0x36BDDE};
    private static final int[][] FACES={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
    private static final int[] X={0,1,1,0,0,1,1,0},Y={0,0,1,1,0,0,1,1},Z={0,0,0,0,1,1,1,1};
    public static void render(PoseStack pose,MultiBufferSource buffers,int seed){
        int color=COLORS[Math.floorMod(seed,COLORS.length)];
        VertexConsumer v=buffers.getBuffer(RenderType.debugQuads());
        cube(pose,v,-.10f,-.07f,-.07f,.10f,.07f,.07f,color);
        cube(pose,v,-.16f,-.05f,-.045f,-.10f,.05f,.045f,0xFFF0CC);
        cube(pose,v,.10f,-.05f,-.045f,.16f,.05f,.045f,0xFFF0CC);
    }
    private static void cube(PoseStack p,VertexConsumer v,float x,float y,float z,float X,float Y,float Z,int c){
        for(int[] face:FACES)for(int i:face)v.vertex(p.last().pose(),CandyModel.X[i]==0?x:X,CandyModel.Y[i]==0?y:Y,CandyModel.Z[i]==0?z:Z).color((c>>16)&255,(c>>8)&255,c&255,255).endVertex();
    }
}
