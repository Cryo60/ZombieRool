package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.*;
import java.util.*;

/** The portable skeleton and linear keyframe format used by map entity JSON files. */
public final class MapActorAnimations {
    public static final List<String> CLIPS=List.of("idle","walk","attack","cast","death");
    public static final List<String> CHANNELS=List.of("rx","ry","rz","x","y","z");
    public static final List<String> QUATERNION=List.of("qx","qy","qz","qw");
    private static void number(CompoundTag out,CompoundTag in,String key,double fallback,double min,double max){double v=in.contains(key,99)?in.getDouble(key):fallback;if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid animation value: "+key);out.putDouble(key,Math.max(min,Math.min(max,v)));}
    private static void bone(CompoundTag bones,String name,String parent,double x,double y,double z){var n=new CompoundTag();n.putString("parent",parent);n.putDouble("pivotX",x);n.putDouble("pivotY",y);n.putDouble("pivotZ",z);bones.put(name,n);}
    public static CompoundTag bones(CompoundTag input){
        var out=new CompoundTag();bone(out,"root","",0,24,0);bone(out,"body","root",0,24,0);bone(out,"head","root",0,0,0);bone(out,"left_arm","root",5,2,0);bone(out,"right_arm","root",-5,2,0);bone(out,"left_leg","root",2,12,0);bone(out,"right_leg","root",-2,12,0);
        if(input.size()>32)throw new IllegalArgumentException("Maximum 32 custom bones");
        for(String key:input.getAllKeys()){
            MapActors.id(key);var in=input.getCompound(key);var n=new CompoundTag();String parent=in.contains("parent")?in.getString("parent"):"root";n.putString("parent",key.equals("root")?"":parent);
            for(String axis:List.of("X","Y","Z"))number(n,in,"pivot"+axis,out.getCompound(key).getDouble("pivot"+axis),-64,64);
            for(String rotation:List.of("rx","ry","rz"))number(n,in,rotation,0,-360,360);out.put(key,n);
        }
        for(String key:out.getAllKeys()){var seen=new HashSet<String>();String next=key;while(!next.isEmpty()){if(!seen.add(next)||!out.contains(next))throw new IllegalArgumentException("Invalid or cyclic bone parent: "+key);next=out.getCompound(next).getString("parent");}}
        return out;
    }
    public static CompoundTag sanitize(CompoundTag input,CompoundTag bones){
        if(input.size()>16)throw new IllegalArgumentException("Maximum 16 animations");
        var out=new CompoundTag();for(String clip:new TreeSet<>(input.getAllKeys())){MapActors.id(clip);if(!input.contains(clip,10))throw new IllegalArgumentException("Animation must be an object: "+clip);var in=input.getCompound(clip);var n=new CompoundTag();number(n,in,"duration",20,1,200);n.putBoolean("loop",in.contains("loop")?in.getBoolean("loop"):List.of("idle","walk").contains(clip));
            var frames=new ArrayList<CompoundTag>();for(Tag t:in.getList("frames",10)){if(frames.size()>=128)throw new IllegalArgumentException("Maximum 128 keyframes per animation");var f=(CompoundTag)t;var clean=new CompoundTag();number(clean,f,"time",0,0,n.getDouble("duration"));
                for(String bone:bones.getAllKeys()){if(!f.contains(bone,10))continue;var pose=new CompoundTag();for(String key:CHANNELS)if(f.getCompound(bone).contains(key,99))number(pose,f.getCompound(bone),key,0,key.startsWith("r")?-360:-64,key.startsWith("r")?360:64);
                    if(f.getCompound(bone).contains("qw")){for(String key:QUATERNION)number(pose,f.getCompound(bone),key,key.equals("qw")?1:0,-1,1);var q=quaternion(pose);if(q.lengthSquared()<.00001)throw new IllegalArgumentException("Zero animation quaternion");q.normalize();writeQuaternion(pose,q);}
                    clean.put(bone,pose);}frames.add(clean);
            }
            frames.sort(Comparator.comparingDouble(f->f.getDouble("time")));var list=new ListTag();double previous=-1;for(var frame:frames){double time=frame.getDouble("time");if(time==previous)throw new IllegalArgumentException("Animation keyframe times must be unique");previous=time;list.add(frame);}n.put("frames",list);out.put(clip,n);
        }return out;
    }
    public static CompoundTag sample(CompoundTag clip,double tick){
        var frames=clip.getList("frames",10);if(frames.isEmpty())return new CompoundTag();
        double duration=Math.max(1,clip.getDouble("duration"));double time=clip.getBoolean("loop")?Math.max(0,tick)%duration:Math.max(0,Math.min(duration,tick));
        var a=frames.getCompound(0);var b=a;
        for(Tag t:frames){var f=(CompoundTag)t;if(f.getDouble("time")<=time)a=f;if(f.getDouble("time")>=time){b=f;break;}b=f;}
        double span=b.getDouble("time")-a.getDouble("time"),mix=span<=0?0:Math.max(0,Math.min(1,(time-a.getDouble("time"))/span));
        return blend(a,b,mix);
    }
    public static CompoundTag blend(CompoundTag a,CompoundTag b,double mix){
        var result=new CompoundTag();var names=new HashSet<>(a.getAllKeys());names.addAll(b.getAllKeys());names.remove("time");
        for(String name:names){var pose=new CompoundTag();var left=a.getCompound(name);var right=b.getCompound(name);for(String key:CHANNELS)if(left.contains(key)||right.contains(key))pose.putDouble(key,left.getDouble(key)+(right.getDouble(key)-left.getDouble(key))*mix);
            if(left.contains("qw")||right.contains("qw"))writeQuaternion(pose,quaternion(left).slerp(quaternion(right),(float)mix).normalize());result.put(name,pose);}return result;
    }
    public static org.joml.Quaternionf quaternion(CompoundTag pose){return pose.contains("qw")?new org.joml.Quaternionf((float)pose.getDouble("qx"),(float)pose.getDouble("qy"),(float)pose.getDouble("qz"),(float)pose.getDouble("qw")):new org.joml.Quaternionf();}
    private static void writeQuaternion(CompoundTag pose,org.joml.Quaternionf q){pose.putDouble("qx",q.x);pose.putDouble("qy",q.y);pose.putDouble("qz",q.z);pose.putDouble("qw",q.w);}
    private MapActorAnimations(){}
}
