package me.cryo.zombierool.client;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
/** Forge's contextual textures are shared by vanilla and Embeddium fluid meshing. */
public final class CustomWaterVisuals implements IClientFluidTypeExtensions {
 private final IClientFluidTypeExtensions original;
 private static final java.util.Map<IClientFluidTypeExtensions,CustomWaterVisuals> CACHE=new java.util.WeakHashMap<>();
 private CustomWaterVisuals(IClientFluidTypeExtensions original){this.original=original;}
 public static Object wrap(Object value){if(!(value instanceof IClientFluidTypeExtensions original)||value instanceof CustomWaterVisuals)return value;return CACHE.computeIfAbsent(original,CustomWaterVisuals::new);}
 private int slot(BlockAndTintGetter level,BlockPos pos){return me.cryo.zombierool.maptexture.CustomWaterlogging.slot(level,pos);}
 @Override public ResourceLocation getStillTexture(){return original.getStillTexture();}
 @Override public ResourceLocation getFlowingTexture(){return original.getFlowingTexture();}
 @Override public ResourceLocation getOverlayTexture(){return original.getOverlayTexture();}
 @Override public int getTintColor(){return original.getTintColor();}
 @Override public ResourceLocation getStillTexture(FluidState state,BlockAndTintGetter level,BlockPos pos){int slot=slot(level,pos);return slot<0?original.getStillTexture(state,level,pos):me.cryo.zombierool.maptexture.MapTextureWater.texture(slot);}
 @Override public ResourceLocation getFlowingTexture(FluidState state,BlockAndTintGetter level,BlockPos pos){int slot=slot(level,pos);return slot<0?original.getFlowingTexture(state,level,pos):me.cryo.zombierool.maptexture.MapTextureWater.texture(slot);}
 @Override public ResourceLocation getOverlayTexture(FluidState state,BlockAndTintGetter level,BlockPos pos){return slot(level,pos)<0?original.getOverlayTexture(state,level,pos):null;}
 @Override public int getTintColor(FluidState state,BlockAndTintGetter level,BlockPos pos){return slot(level,pos)<0?original.getTintColor(state,level,pos):0xffffffff;}
 @Override public ResourceLocation getRenderOverlayTexture(net.minecraft.client.Minecraft mc){return original.getRenderOverlayTexture(mc);}
 @Override public void renderOverlay(net.minecraft.client.Minecraft mc,com.mojang.blaze3d.vertex.PoseStack pose){original.renderOverlay(mc,pose);}
 @Override public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera,float partial,net.minecraft.client.multiplayer.ClientLevel level,int distance,float darkness,org.joml.Vector3f color){return original.modifyFogColor(camera,partial,level,distance,darkness,color);}
 @Override public void modifyFogRender(net.minecraft.client.Camera camera,net.minecraft.client.renderer.FogRenderer.FogMode mode,float distance,float partial,float near,float far,com.mojang.blaze3d.shaders.FogShape shape){original.modifyFogRender(camera,mode,distance,partial,near,far,shape);}
}
