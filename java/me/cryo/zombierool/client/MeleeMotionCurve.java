package me.cryo.zombierool.client;

/** Continuous authored key poses, independent of vanilla's fixed six-tick arm swing. */
public final class MeleeMotionCurve {
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float[] sample(float age,int duration,int contact,int combo,boolean heavy,boolean quick,boolean two,boolean axe){
        float direction=combo==1?-1:1;
        float[] guard={0,0,0,0,0,0,0};
        float[] wind={.10f*direction,.13f,.12f,-36,14*direction,-30*direction,-.13f*direction};
        float[] cut={-.22f*direction,-.025f,-.22f,18,-22*direction,38*direction,.18f*direction};
        float[] follow={-.32f*direction,-.10f,-.10f,32,-30*direction,58*direction,.23f*direction};
        if(quick){wind=new float[]{.035f,.025f,.12f,-15,5,-8,0};cut=new float[]{-.04f,-.01f,-.37f,8,-6,7,.06f};follow=new float[]{-.08f,-.03f,-.25f,14,-8,12,.08f};}
        if(heavy||axe||combo==2&&!quick){
            wind=new float[]{.10f,.12f,.08f,-38,12,-16,-.1f};
            cut=new float[]{-.12f,-.04f,-.28f,28,-15,20,.16f};
            follow=new float[]{-.20f,-.19f,-.12f,57,-20,29,.20f};
        }
        if(two){wind[0]*=.7f;cut[0]*=.8f;follow[0]*=.8f;}
        float ready=Math.max(.5f,contact-1.25f),end=Math.min(duration-2,contact+1.5f);
        float[] a,b;float t;
        if(age<=ready){a=heavy?wind:guard;b=wind;t=age/ready;}
        else if(age<=contact){a=wind;b=cut;t=(age-ready)/(contact-ready);}
        else if(age<=end){a=cut;b=follow;t=(age-contact)/(end-contact);}
        else{a=follow;b=guard;t=(age-end)/(duration-end);}
        float f=smooth(t);float[] out=new float[7];for(int i=0;i<7;i++)out[i]=a[i]+(b[i]-a[i])*f;return out;
    }
    private MeleeMotionCurve(){}
}
