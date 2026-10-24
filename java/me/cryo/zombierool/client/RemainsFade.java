package me.cryo.zombierool.client;

import java.util.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public final class RemainsFade {
    private record Fade(long start,long end){}
    private static final Map<UUID,Fade> fades=new HashMap<>();
    private static final Map<RenderType,RenderType> translucent=new IdentityHashMap<>();
    public static void receive(UUID id,long start,long end){fades.put(id,new Fade(start,end));}
    public static float opacity(Entity entity,float partial){var fade=fades.get(entity.getUUID());if(fade==null)return 1;float progress=net.minecraft.util.Mth.clamp((entity.level().getGameTime()+partial-fade.start)/(float)Math.max(1,fade.end-fade.start),0,1);return 1-progress*progress*(3-2*progress);}
    public static MultiBufferSource buffers(MultiBufferSource source,Entity entity,float partial){float alpha=opacity(entity,partial);if(alpha>=1)return source;return type->new Alpha(source.getBuffer(translucent.computeIfAbsent(type,RemainsFade::translucent)),alpha);}
    private static RenderType translucent(RenderType type){if(type.format()!=com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY)return type;try{
        Object state=null;for(var field:type.getClass().getDeclaredFields())if(field.getType()==RenderType.CompositeState.class){field.setAccessible(true);state=field.get(type);break;}if(state==null)return type;
        Object texture=null;for(var field:state.getClass().getDeclaredFields())if(RenderStateShard.EmptyTextureStateShard.class.isAssignableFrom(field.getType())){field.setAccessible(true);texture=field.get(state);break;}
        for(Class<?> c=texture==null?null:texture.getClass();c!=null;c=c.getSuperclass())for(var field:c.getDeclaredFields())if(field.getType()==Optional.class){field.setAccessible(true);var optional=(Optional<?>)field.get(texture);if(optional.isPresent()&&optional.get() instanceof ResourceLocation id)return RenderType.entityTranslucent(id);}
    }catch(ReflectiveOperationException ignored){}return type;}
    private record Alpha(VertexConsumer out,float alpha) implements VertexConsumer{
        public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);return this;}public VertexConsumer color(int r,int g,int b,int a){out.color(r,g,b,(int)(a*alpha));return this;}public VertexConsumer uv(float u,float v){out.uv(u,v);return this;}public VertexConsumer overlayCoords(int u,int v){out.overlayCoords(u,v);return this;}public VertexConsumer uv2(int u,int v){out.uv2(u,v);return this;}public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);return this;}public void endVertex(){out.endVertex();}public void defaultColor(int r,int g,int b,int a){out.defaultColor(r,g,b,(int)(a*alpha));}public void unsetDefaultColor(){out.unsetDefaultColor();}
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Events{@net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){fades.clear();translucent.clear();}}
    private RemainsFade(){}
}
