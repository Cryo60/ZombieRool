package me.cryo.zombierool.util;

import java.util.Random;

/** Cached surface masks, separate from the round sprite used by flying droplets. */
public final class BloodStampMask {
    private static final int SIZE=128, VARIANTS=16;
    private static final byte[][] MASKS=new byte[VARIANTS*2][];
    private BloodStampMask() {}

    public static int sample(double u,double v,int seed,boolean wall){
        if(u<0||u>=1||v<0||v>=1)return 0;
        int index=Math.floorMod(seed,VARIANTS)+(wall?VARIANTS:0);
        byte[] mask=MASKS[index];
        if(mask==null)MASKS[index]=mask=create(index,wall);
        return mask[(int)(v*SIZE)*SIZE+(int)(u*SIZE)]&255;
    }

    private static byte[] create(int seed,boolean wall){
        var random=new Random(0xB100DL+seed*7919L);
        var mask=new byte[SIZE*SIZE];
        // Offset lobes form an uneven connected deposit, without a circular base.
        for(int i=0;i<(wall?6:10);i++){
            double angle=random.nextDouble()*Math.PI*2,offset=random.nextDouble()*(wall?.11:.13);
            ellipse(mask,.5+Math.cos(angle)*offset,.5+Math.sin(angle)*offset,
                .08+random.nextDouble()*(wall?.09:.14),(wall?.035:.065)+random.nextDouble()*.075,angle);
        }
        // Narrow radial sprays and detached satellites leave gaps around the impact.
        for(int i=0;i<(wall?18:12);i++){
            double angle=random.nextDouble()*Math.PI*2;
            double distance=.19+random.nextDouble()*.23;
            double radius=.009+random.nextDouble()*.018;
            ellipse(mask,.5+Math.cos(angle)*distance,.5+Math.sin(angle)*distance,
                radius*(wall?1.8:1.1),radius,angle);
            if(wall&&i%3==0){
                double middle=distance*.65;
                ellipse(mask,.5+Math.cos(angle)*middle,.5+Math.sin(angle)*middle,
                    distance*.24,.009+random.nextDouble()*.01,angle);
            }
        }
        return mask;
    }

    private static void ellipse(byte[] mask,double cx,double cy,double rx,double ry,double angle){
        double cos=Math.cos(angle),sin=Math.sin(angle),bound=Math.max(rx,ry)*1.2+.01;
        int minX=Math.max(0,(int)((cx-bound)*SIZE)),maxX=Math.min(SIZE-1,(int)((cx+bound)*SIZE));
        int minY=Math.max(0,(int)((cy-bound)*SIZE)),maxY=Math.min(SIZE-1,(int)((cy+bound)*SIZE));
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
            double dx=(x+.5)/SIZE-cx,dy=(y+.5)/SIZE-cy;
            double a=(dx*cos+dy*sin)/rx,b=(-dx*sin+dy*cos)/ry;
            double theta=Math.atan2(b,a),phase=cx*73+cy*97;
            double rim=1+.09*Math.sin(theta*7+phase)+.055*Math.sin(theta*13-phase)
                +.035*Math.sin(theta*23+phase);
            double distance=Math.sqrt(a*a+b*b)/rim;
            int alpha=(int)(255*Math.max(0,Math.min(1,(1-distance)*Math.min(rx,ry)*SIZE)));
            int index=y*SIZE+x;if(alpha>(mask[index]&255))mask[index]=(byte)alpha;
        }
    }
}
