package me.cryo.zombierool.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import me.cryo.zombierool.block.system.BuildableBlocks.ComponentPose;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.registries.ForgeRegistries;

/** Common-side extension points: register on both logical sides; no client renderer dependency. */
public final class BuildableVisuals {
    public static final TagKey<Item> UPRIGHT=TagKey.create(Registries.ITEM,new ResourceLocation("zombierool","upright_components"));
    public static final TagKey<Item> COMPONENTS=TagKey.create(Registries.ITEM,new ResourceLocation("zombierool","buildable_components"));
    private static final Map<ResourceLocation,BiFunction<ItemStack,Direction,ComponentPose>> POSES=new ConcurrentHashMap<>();
    private BuildableVisuals(){}
    public static void registerPose(ResourceLocation item,BiFunction<ItemStack,Direction,ComponentPose> provider){POSES.put(java.util.Objects.requireNonNull(item),java.util.Objects.requireNonNull(provider));}
    public static net.minecraft.world.phys.AABB bounds(ItemStack stack,Direction face){
        var pose=me.cryo.zombierool.block.system.BuildableBlocks.componentPose(stack,face);boolean upright=stack.is(UPRIGHT),cube=stack.getItem() instanceof net.minecraft.world.item.BlockItem||upright||pose.context()==ItemDisplayContext.NONE;
        double half=cube?.525:.42;var local=cube?new net.minecraft.world.phys.AABB(-half,-.525,-half,half,upright?1:.525,half):pose.context()==ItemDisplayContext.GROUND?new net.minecraft.world.phys.AABB(-.42,-.1,-.42,.42,.1,.42):new net.minecraft.world.phys.AABB(-.42,-.42,-.1,.42,.42,.1);
        var q=new org.joml.Quaternionf(pose.rx(),pose.ry(),pose.rz(),pose.rw());double ax=Double.POSITIVE_INFINITY,ay=ax,az=ax,bx=Double.NEGATIVE_INFINITY,by=bx,bz=bx;
        for(int i=0;i<8;i++){var v=new org.joml.Vector3f((float)((i&1)==0?local.minX:local.maxX),(float)((i&2)==0?local.minY:local.maxY),(float)((i&4)==0?local.minZ:local.maxZ)).mul(pose.size()).rotate(q);ax=Math.min(ax,v.x);ay=Math.min(ay,v.y);az=Math.min(az,v.z);bx=Math.max(bx,v.x);by=Math.max(by,v.y);bz=Math.max(bz,v.z);}
        return new net.minecraft.world.phys.AABB(ax+pose.x(),ay+pose.y(),az+pose.z(),bx+pose.x(),by+pose.y(),bz+pose.z());
    }
    public static ComponentPose resolve(ItemStack stack,Direction face,ComponentPose fallback){
        var provider=POSES.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));var pose=provider==null?fallback:provider.apply(stack,face);if(pose==null)pose=fallback;
        if(!stack.hasTag()||!stack.getTag().contains("zr_component_pose",10))return pose;
        var n=stack.getTag().getCompound("zr_component_pose");var q=new org.joml.Quaternionf(pose.rx(),pose.ry(),pose.rz(),pose.rw());
        q.rotateXYZ((float)Math.toRadians(number(n,"pitch",0,-360,360)),(float)Math.toRadians(number(n,"yaw",0,-360,360)),(float)Math.toRadians(number(n,"roll",0,-360,360)));
        var context=pose.context();if(n.contains("context"))try{context=ItemDisplayContext.valueOf(n.getString("context").toUpperCase(java.util.Locale.ROOT));}catch(IllegalArgumentException ignored){}
        return new ComponentPose(pose.x()+number(n,"x",0,-2,2),pose.y()+number(n,"y",0,-2,2),pose.z()+number(n,"z",0,-2,2),(float)number(n,"scale",pose.size(),.05,4),context,q.x(),q.y(),q.z(),q.w());
    }
    private static double number(net.minecraft.nbt.CompoundTag n,String key,double fallback,double min,double max){double v=n.contains(key,99)?n.getDouble(key):fallback;return Double.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
}
