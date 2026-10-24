package me.cryo.zombierool.client;

import java.lang.invoke.*;

/** Resolve the optional Iris API once. No repeated class lookup or GPU state readback. */
public final class SceneryShaderPass {
    private static final MethodHandle SHADOW=find();
    private static MethodHandle find(){
        for(String name:new String[]{"net.irisshaders.iris.api.v0.IrisApi","net.coderbot.iris.api.v0.IrisApi"})try{
            var api=Class.forName(name);var instance=api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().unreflect(api.getMethod("isRenderingShadowPass")).bindTo(instance).asType(MethodType.methodType(boolean.class));
        }catch(ReflectiveOperationException|LinkageError ignored){}
        return null;
    }
    public static boolean shadow(){if(SHADOW==null)return false;try{return (boolean)SHADOW.invokeExact();}catch(Throwable ignored){return false;}}
    private SceneryShaderPass(){}
}
