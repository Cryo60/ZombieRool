package me.cryo.zombierool.client;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
/** Original combat-blood texture from the published renderer, with the hotfix1 translucent blend and depth writes. */
public final class ZrBloodRenderType {
    private static final ResourceLocation TEXTURE=new ResourceLocation("zombierool","textures/particle/blood_drop.png");
    public static final RenderType DECAL=RenderType.entityTranslucent(TEXTURE);
    public static ResourceLocation texture(int index){return TEXTURE;}
    public static RenderType variant(int index){return DECAL;}
    public static RenderType layer(int index){return DECAL;}
    public static int layerCount(){return 8;}
    private ZrBloodRenderType(){}
}
