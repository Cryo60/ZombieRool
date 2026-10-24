import me.cryo.zombierool.util.BloodStampMask;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.HashSet;

public final class BloodStampMaskTest {
    public static void main(String[] args)throws Exception{
        var preview=new BufferedImage(768,384,BufferedImage.TYPE_INT_RGB);
        var hashes=new HashSet<Integer>();
        for(int kind=0;kind<2;kind++)for(int seed=0;seed<16;seed++){
            int visible=0,solid=0,hash=1,asymmetry=0;
            for(int y=0;y<128;y++)for(int x=0;x<128;x++){
                int a=BloodStampMask.sample((x+.5)/128,(y+.5)/128,seed,kind==0);
                int mirror=BloodStampMask.sample((127-x+.5)/128,(y+.5)/128,seed,kind==0);
                if(a>0)visible++;if(a==255)solid++;if(a!=mirror)asymmetry++;hash=31*hash+a;
                if((x==0||y==0||x==127||y==127)&&a!=0)throw new AssertionError("Mask touches tile boundary");
                if(seed<4){
                    int r=(85*a+150*(255-a))/255,g=(12*a+145*(255-a))/255,b=(18*a+130*(255-a))/255;
                    for(int j=0;j<3;j++)for(int i=0;i<3;i++){
                        int px=seed*192+x*3/2,py=kind*192+y*3/2;
                        preview.setRGB(Math.min(767,px+i/2),Math.min(383,py+j/2),(r<<16)|(g<<8)|b);
                    }
                }
            }
            if(visible<500||visible>8000||solid<100||asymmetry<300)throw new AssertionError("Invalid shape "+seed+"/"+kind);
            hashes.add(hash);
        }
        if(hashes.size()!=32)throw new AssertionError("Repeated mask variants");
        ImageIO.write(preview,"png",new File("build/camo-check/blood-surface-masks.png"));
        System.out.println("PASS: 32 distinct irregular masks, opaque centers, transparent borders and asymmetry");
    }
}
