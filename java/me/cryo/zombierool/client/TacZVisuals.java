package me.cryo.zombierool.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Optional gun-pack icons and live animation-stack metadata. */
public final class TacZVisuals {
    private TacZVisuals(){}
    private static final ThreadLocal<Boolean> CAREER_RENDER=ThreadLocal.withInitial(()->false);
    public static boolean isCareerRender(){return CAREER_RENDER.get();}
    public static java.util.List<String> careerWeapons(){
        prepareMenuCatalog();
        var ids=new java.util.TreeSet<>(me.cryo.zombierool.core.system.WeaponFacade.getCareerWeaponIds());
        if(!ModList.get().isLoaded("tacz"))return new java.util.ArrayList<>(ids);
        var mapped=new java.util.HashSet<String>();
        for(var def:me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.values())
            mapped.addAll(me.cryo.zombierool.integration.TacZMappings.mappedIds(def));
        // Client packs are available in the main-menu career too, before a
        // server has supplied its common gun index.
        for(var entry:com.tacz.guns.api.TimelessAPI.getAllClientGunIndex())
            if(!mapped.contains(entry.getKey().toString()))ids.add(entry.getKey().toString());
        return new java.util.ArrayList<>(ids);
    }
    /** Local pack metadata is available before the server sends common gun data. */
    public static void prepareMenuCatalog(){
        if(!ModList.get().isLoaded("tacz"))return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.level!=null||!com.tacz.guns.api.TimelessAPI.getAllClientGunIndex().isEmpty())return;
        var resources=new java.util.LinkedHashMap<ResourceLocation,net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream>>();
        try(var packs=mc.getResourceManager().listPacks()){
            packs.forEach(pack->{for(String namespace:pack.getNamespaces(net.minecraft.server.packs.PackType.SERVER_DATA))
                pack.listResources(net.minecraft.server.packs.PackType.SERVER_DATA,namespace,"index/guns",(id,source)->{
                    if(id.getPath().endsWith(".json"))resources.put(id,source);
                });});
        }
        resources.forEach((path,source)->{
            try(var reader=new java.io.InputStreamReader(source.get(),java.nio.charset.StandardCharsets.UTF_8)){
                var pojo=com.tacz.guns.client.resource.ClientAssetsManager.GSON.fromJson(reader,com.tacz.guns.resource.pojo.GunIndexPOJO.class);
                String file=path.getPath();var id=new ResourceLocation(path.getNamespace(),file.substring("index/guns/".length(),file.length()-5));
                com.tacz.guns.client.resource.ClientIndexManager.GUN_INDEX.put(id,com.tacz.guns.client.resource.index.ClientGunIndex.getInstance(pojo));
            }catch(java.io.IOException|RuntimeException error){
                org.slf4j.LoggerFactory.getLogger(TacZVisuals.class).warn("Cannot read local career gun {}: {}",path,error.getMessage());
            }
        });
    }
    /** Cosmetic menu stack only; gameplay availability remains server-authoritative. */
    public static ItemStack careerPreview(String id,net.minecraft.client.player.LocalPlayer player){
        if(!ModList.get().isLoaded("tacz"))return me.cryo.zombierool.core.system.WeaponFacade.createCareerPreview(id,player);
        prepareMenuCatalog();
        ResourceLocation gunId=id.contains(":")&&!id.startsWith("zombierool:")?ResourceLocation.tryParse(id):null;
        if(gunId==null){
            var def=me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.get(id.replace("zombierool:",""));
            var mapping=me.cryo.zombierool.integration.TacZMappings.choose(def,key->com.tacz.guns.api.TimelessAPI.getClientGunIndex(new ResourceLocation(key)).isPresent());
            if(mapping!=null)gunId=new ResourceLocation(mapping.gun_id);
        }
        if(gunId!=null){
            var index=com.tacz.guns.api.TimelessAPI.getClientGunIndex(gunId);
            if(index.isPresent()){
                var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz",index.get().getItemType()+"_gun"));
                if(item!=null&&item!=net.minecraft.world.item.Items.AIR){var stack=new ItemStack(item);stack.getOrCreateTag().putString("GunId",gunId.toString());return stack;}
            }
        }
        return me.cryo.zombierool.core.system.WeaponFacade.createCareerPreview(id,null);
    }
    /** Career thumbnails must reach TACZ's custom renderer even when its baked item model is missing. */
    public static void renderCareerPreview(ItemStack stack,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay){
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(ModList.get().isLoaded("tacz")&&com.tacz.guns.api.item.IGun.getIGunOrNull(stack)!=null){
            prepareMenuCatalog();
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if(com.tacz.guns.api.TimelessAPI.getGunDisplay(gun.getGunDisplayId(stack),gun.getGunId(stack)).isPresent()){
                pose.pushPose();boolean previous=CAREER_RENDER.get();CAREER_RENDER.set(true);
                try{pose.translate(-.5,-.5,-.5);net.minecraftforge.client.extensions.common.IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(stack,net.minecraft.world.item.ItemDisplayContext.FIXED,pose,buffers,light,overlay);}
                finally{CAREER_RENDER.set(previous);pose.popPose();}
                return;
            }
        }
        mc.getItemRenderer().renderStatic(stack,net.minecraft.world.item.ItemDisplayContext.FIXED,light,overlay,pose,buffers,mc.level,0);
    }
    public static ItemStack firstPersonStack(ItemStack cached,net.minecraft.client.player.LocalPlayer player){
        if(player==null)return cached;
        var held=player.getMainHandItem();
        var oldGun=com.tacz.guns.api.item.IGun.getIGunOrNull(cached);
        var liveGun=com.tacz.guns.api.item.IGun.getIGunOrNull(held);
        // TacZ keeps a stack for its equip/unequip animation. A career change can
        // replace inventory NBT while that older stack still drives the renderer.
        return oldGun!=null&&liveGun!=null&&oldGun.getGunId(cached).equals(liveGun.getGunId(held))?held:cached;
    }
    public static ResourceLocation wallIcon(ItemStack stack){
        if(!ModList.get().isLoaded("tacz"))return null;
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        if(gun!=null)return com.tacz.guns.api.TimelessAPI.getGunDisplay(stack).map(com.tacz.guns.client.resource.GunDisplayInstance::getSlotTexture).orElse(null);
        var attachment=com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(stack);
        if(attachment!=null)return com.tacz.guns.api.TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack)).map(com.tacz.guns.client.resource.index.ClientAttachmentIndex::getSlotTexture).orElse(null);
        return null;
    }
}
