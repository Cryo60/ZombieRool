package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.*;
import java.util.*;

/** Optional bridge for tacz-mesh-loader 0.1.8, without a mandatory mod dependency. */
public final class TacZMeshCamo {
    private static boolean failed;
    private static Access access;
    private TacZMeshCamo() {}

    private record Access(Field model,Field meshes,Field excluded,Field translucent,
        Field illuminated,Field division,Field ocular,Field ocularRing,
        Field hideOcular,Field hideRing,Method compile) {}

    private static Field field(Class<?> type,String name)throws ReflectiveOperationException {
        var field=type.getDeclaredField(name);field.setAccessible(true);return field;
    }
    static Access inspect(ClassLoader loader)throws ReflectiveOperationException {
        var gun=Class.forName("com.example.taczmeshloader.tacz.TaczPolyMeshGunModel",false,loader);
        var model=Class.forName("com.example.taczmeshloader.core.PolyMeshModel",false,loader);
        var mesh=Class.forName("com.example.taczmeshloader.core.PolyMesh",false,loader);
        return new Access(field(gun,"polyMeshModel"),field(model,"meshMap"),field(model,"excludedBones"),
            field(model,"translucentBones"),field(model,"illuminatedBones"),field(model,"divisionBones"),
            field(model,"ocularBones"),field(model,"ocularRingBones"),field(model,"ocularBonesHidden"),
            field(model,"ocularRingBonesHidden"),mesh.getMethod("compileConsumer",PoseStack.Pose.class,
                VertexConsumer.class,int.class,int.class,float.class,float.class,float.class,float.class));
    }
    public static Pass open(Object gun){
        if(failed)return null;
        try{
            if(access==null)access=inspect(gun.getClass().getClassLoader());
            var model=access.model.get(gun);return model==null?null:new Pass(model,access);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){disable(error);return null;}
    }
    private static void disable(Throwable error){
        if(!failed)org.slf4j.LoggerFactory.getLogger(TacZMeshCamo.class).warn("Optional TACZ polygon camo unavailable",error);
        failed=true;
    }
    public static final class Pass {
        private final Access access;
        private final Map<?,?> meshes;
        private final Set<String> excluded=new HashSet<>(),unpainted=new HashSet<>();
        private Pass(Object model,Access access)throws IllegalAccessException {
            this.access=access;meshes=(Map<?,?>)access.meshes.get(model);
            add(excluded,access.excluded.get(model));add(excluded,access.division.get(model));
            if(access.hideOcular.getBoolean(model))add(excluded,access.ocular.get(model));
            if(access.hideRing.getBoolean(model))add(excluded,access.ocularRing.get(model));
            add(unpainted,access.translucent.get(model));add(unpainted,access.illuminated.get(model));
        }
        private static void add(Set<String> target,Object values){for(var value:(Set<?>)values)target.add((String)value);}
        public boolean excluded(String bone){return excluded.contains(bone);}
        public void compile(String bone,PoseStack.Pose pose,VertexConsumer target,int light,int overlay){
            if(failed||unpainted.contains(bone))return;
            var list=meshes.get(bone);if(!(list instanceof List<?> parts))return;
            try{for(var mesh:parts)access.compile.invoke(mesh,pose,target,light,overlay,1f,1f,1f,1f);}
            catch(ReflectiveOperationException|RuntimeException error){disable(error);}
        }
    }
}
