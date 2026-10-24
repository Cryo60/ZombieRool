package me.cryo.zombierool.client.renderer;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class CustomMiniBossRenderer extends EntityRenderer<SpecialEnemyEntity>{
    private final MinecraftBossRenderer panzer,brutus;
    private final java.util.Map<String,EntityRenderer<SpecialEnemyEntity>> encounters=new java.util.HashMap<>();
    public CustomMiniBossRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=.65f;panzer=new MinecraftBossRenderer(c,"panzer");brutus=new MinecraftBossRenderer(c,"brutus");for(String kind:java.util.List.of("george_romero","george_romero_enraged","astronaut","abomination"))encounters.put(kind,new BlockyEnemyRenderer<>(c,kind.replace("_enraged","")));}
    private String encounterKind(SpecialEnemyEntity e){String kind=me.cryo.zombierool.gameplay.EncounterAbilities.kind(e);return kind.equals("george_romero")&&e.bossProfile().getBoolean("enraged")?"george_romero_enraged":kind;}
    private MinecraftBossRenderer delegate(SpecialEnemyEntity e){return e.isPanzer()?panzer:brutus;}
    @Override public void render(SpecialEnemyEntity e,float yaw,float pt,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){var custom=encounters.get(encounterKind(e));if(custom!=null)custom.render(e,yaw,pt,pose,buffers,light);else delegate(e).render(e,yaw,pt,pose,buffers,light);}
    @Override public ResourceLocation getTextureLocation(SpecialEnemyEntity e){var custom=encounters.get(encounterKind(e));return custom==null?delegate(e).getTextureLocation(e):custom.getTextureLocation(e);}
}
