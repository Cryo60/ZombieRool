package me.cryo.zombierool.maptexture;

/** Alpha mask for the opaque 16x32 source art; leaves the metal/wood frame intact. */
public final class GlassDoorAlpha {
    private GlassDoorAlpha(){}
    public static int alpha(int red,int green,int blue,int x,int y){
        if(x<2||x>=14||y<2||y>=15)return 255;
        // Dark pixels represent missing glass; light pixels are remaining glass fragments.
        return Math.max(red,Math.max(green,blue))<90?0:144;
    }
}
