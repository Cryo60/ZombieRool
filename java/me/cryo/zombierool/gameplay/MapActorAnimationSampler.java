package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Compile immutable keyframes once; render frames reuse numeric pose buffers. */
public final class MapActorAnimationSampler {
    public static final int STRIDE=10,QUATERNION=1<<9;
    private static final String[] CHANNELS={"rx","ry","rz","x","y","z","qx","qy","qz","qw"};
    public static final class Pose {
        public final double[] values;public final int[] masks;
        private Pose(int bones){values=new double[bones*STRIDE];masks=new int[bones];}
        public void copyFrom(Pose other){System.arraycopy(other.values,0,values,0,values.length);System.arraycopy(other.masks,0,masks,0,masks.length);}
    }
    public static final class Clip {
        public final double duration;public final boolean loop;private final double[] times;private final Pose[] frames;
        private Clip(double duration,boolean loop,double[] times,Pose[] frames){this.duration=duration;this.loop=loop;this.times=times;this.frames=frames;}
    }
    private final Map<String,Clip> clips=new HashMap<>();private final int boneCount;
    private final org.joml.Quaternionf leftQ=new org.joml.Quaternionf(),rightQ=new org.joml.Quaternionf();
    public MapActorAnimationSampler(CompoundTag animations,List<String> bones){
        boneCount=bones.size();
        for(String name:animations.getAllKeys()){
            var input=animations.getCompound(name);var rows=input.getList("frames",10);double[] times=new double[rows.size()];Pose[] frames=new Pose[rows.size()];
            for(int f=0;f<rows.size();f++){
                var row=rows.getCompound(f);times[f]=row.getDouble("time");var pose=newPose();frames[f]=pose;
                for(int b=0;b<boneCount;b++){var source=row.getCompound(bones.get(b));int offset=b*STRIDE;pose.values[offset+9]=1;
                    for(int c=0;c<STRIDE;c++)if(source.contains(CHANNELS[c])){pose.masks[b]|=1<<c;pose.values[offset+c]=source.getDouble(CHANNELS[c]);}
                }
            }
            clips.put(name,new Clip(Math.max(1,input.getDouble("duration")),input.getBoolean("loop"),times,frames));
        }
    }
    public Pose newPose(){return new Pose(boneCount);}
    public Clip clip(String name){return clips.get(name);}
    public void sample(Clip clip,double tick,Pose out){
        if(clip.frames.length==0){Arrays.fill(out.masks,0);Arrays.fill(out.values,0);return;}
        double time=clip.loop?Math.max(0,tick)%clip.duration:Math.max(0,Math.min(clip.duration,tick));
        int lo=0,hi=clip.times.length;while(lo<hi){int mid=(lo+hi)>>>1;if(clip.times[mid]<time)lo=mid+1;else hi=mid;}
        int right=Math.min(lo,clip.frames.length-1),left=Math.max(0,right-1);
        if(clip.times[right]<=time)left=right;
        double span=clip.times[right]-clip.times[left],mix=span<=0?0:Math.max(0,Math.min(1,(time-clip.times[left])/span));
        interpolate(clip.frames[left],clip.frames[right],mix,out);
    }
    public void blend(Pose previous,Pose current,double mix){interpolate(previous,current,mix,current);}
    private void interpolate(Pose a,Pose b,double mix,Pose out){
        for(int bone=0;bone<boneCount;bone++){
            int offset=bone*STRIDE,mask=a.masks[bone]|b.masks[bone];
            if((mask&QUATERNION)!=0){
                if((a.masks[bone]&QUATERNION)!=0)leftQ.set((float)a.values[offset+6],(float)a.values[offset+7],(float)a.values[offset+8],(float)a.values[offset+9]);else leftQ.identity();
                if((b.masks[bone]&QUATERNION)!=0)rightQ.set((float)b.values[offset+6],(float)b.values[offset+7],(float)b.values[offset+8],(float)b.values[offset+9]);else rightQ.identity();
                leftQ.slerp(rightQ,(float)mix).normalize();out.values[offset+6]=leftQ.x;out.values[offset+7]=leftQ.y;out.values[offset+8]=leftQ.z;out.values[offset+9]=leftQ.w;
            }
            for(int c=0;c<6;c++)out.values[offset+c]=a.values[offset+c]+(b.values[offset+c]-a.values[offset+c])*mix;
            out.masks[bone]=mask;
        }
    }
}
