package me.cryo.zombierool.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.*;
import java.util.*;

/** Exercises the optional bridge against the real, remapped mesh-loader binary without GL. */
public final class TacZMeshCamoTest {
    public static void main(String[] args)throws Exception {
        ClassLoader loader=TacZMeshCamoTest.class.getClassLoader();
        var boneType=Class.forName("com.example.taczmeshloader.api.IPolyMeshBone");
        Object root=Proxy.newProxyInstance(loader,new Class<?>[]{boneType},(p,m,a)->switch(m.getName()){
            case "getName"->"grip";case "getChildren"->List.of();case "isVisible"->true;case "isIlluminated"->false;
            default->m.getReturnType()==float.class?0f:null;
        });
        var mesh=JsonParser.parseString("""
            {"normalized_uvs":true,"positions":[[0,0,0],[1,0,0],[1,1,0],[0,1,0]],
             "normals":[[0,0,1]],"uvs":[[0,0],[1,0],[1,1],[0,1]],
             "polys":[[[0,0,0],[1,0,1],[2,0,2],[3,0,3]]]}
            """).getAsJsonObject();
        var bones=new JsonArray();
        for(String name:List.of("grip","mag_standard","lens","glow","spare","ocular","division")){
            var bone=new JsonObject();bone.addProperty("name",name);bone.add("pivot",JsonParser.parseString("[0,0,0]"));bone.add("poly_mesh",mesh.deepCopy());bones.add(bone);
        }
        var geo=new JsonObject();geo.add("description",JsonParser.parseString("{\"texture_width\":16,\"texture_height\":16}"));geo.add("bones",bones);
        var geos=new JsonArray();geos.add(geo);var document=new JsonObject();document.add("minecraft:geometry",geos);
        var modelType=Class.forName("com.example.taczmeshloader.core.PolyMeshModel");
        var model=modelType.getConstructor(boneType,JsonObject.class).newInstance(root,document);
        for(String[] entry:new String[][]{{"translucentBones","lens"},{"illuminatedBones","glow"},{"excludedBones","spare"},{"ocularBones","ocular"},{"divisionBones","division"}}){
            var f=modelType.getDeclaredField(entry[0]);f.setAccessible(true);((Set<String>)f.get(model)).add(entry[1]);
        }
        var hide=modelType.getDeclaredField("ocularBonesHidden");hide.setAccessible(true);hide.setBoolean(model,true);
        var access=TacZMeshCamo.inspect(loader);
        var ctor=TacZMeshCamo.Pass.class.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var pass=(TacZMeshCamo.Pass)ctor.newInstance(model,access);
        int[] vertices={0};
        VertexConsumer target=(VertexConsumer)Proxy.newProxyInstance(loader,new Class<?>[]{VertexConsumer.class},(p,m,a)->{
            if(m.getName().equals("endVertex"))vertices[0]++;
            return m.getReturnType()==VertexConsumer.class?p:null;
        });
        var pose=new PoseStack();
        var consumer=new TacZCamoOverlay.SurfaceConsumer(target,pose.last());
        pass.compile("grip",pose.last(),consumer,0,0);pass.compile("mag_standard",pose.last(),consumer,0,0);
        if(vertices[0]!=8)throw new AssertionError("Polygon furniture not painted: "+vertices[0]);
        pass.compile("lens",pose.last(),consumer,0,0);pass.compile("glow",pose.last(),consumer,0,0);
        if(vertices[0]!=8)throw new AssertionError("Paint on glass/emissive mesh");
        for(String name:List.of("spare","ocular","division"))if(!pass.excluded(name))throw new AssertionError("Loader exclusion lost: "+name);
        System.out.println("PASS: actual polygon compilation, grip/magazine coating, glass/emissive and scope/spare-magazine exclusions");
    }
}
