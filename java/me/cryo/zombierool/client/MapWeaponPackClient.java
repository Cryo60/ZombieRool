package me.cryo.zombierool.client;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import com.google.gson.*;

/** Map assets are decoded directly. Joining and leaving never reload Minecraft resources. */
public final class MapWeaponPackClient {
    private static ByteArrayOutputStream transfer;private static int expected,next;
    private static final Map<String,byte[]> assets=new HashMap<>();
    private static final Map<String,JsonObject> definitions=new HashMap<>();
    private static final Map<String,BakedModel> models=new HashMap<>();
    private static final Map<String,Sprite> textures=new HashMap<>();
    private static final Map<String,String> translations=new HashMap<>();
    private static final Map<String,List<String>> sounds=new HashMap<>();
    private static final Map<String,File> audio=new HashMap<>();
    public static int generation;private static Object languageOwner;private static java.lang.reflect.Field languageField;private static Object oldLanguage;
    private static void applyLanguage()throws Exception{var owner=net.minecraft.locale.Language.getInstance();for(var field:owner.getClass().getDeclaredFields())if(java.util.Map.class.isAssignableFrom(field.getType())){field.setAccessible(true);languageOwner=owner;languageField=field;oldLanguage=field.get(owner);var map=new java.util.HashMap<String,String>((java.util.Map<String,String>)oldLanguage);map.putAll(translations);field.set(owner,map);break;}}
    public static void part(int index,int total,byte[] bytes){
        if(total<0||total>32*1024*1024)throw new IllegalArgumentException("Map asset size");
        if(index==-1){transfer=new ByteArrayOutputStream();expected=total;next=0;return;}
        if(transfer==null||expected!=total)return;
        if(index>=0){if(index!=next++||transfer.size()+bytes.length>expected){transfer=null;return;}transfer.writeBytes(bytes);return;}
        if(index!=-2||transfer.size()!=expected){transfer=null;return;}
        byte[] data=transfer.toByteArray();transfer=null;clear();
        try{
            var folder=Minecraft.getInstance().gameDirectory.toPath().resolve("zombierool/cache/weapon-audio");Files.createDirectories(folder);
            try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(data))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){String name=entry.getName();if(name.startsWith("assets/")&&!name.contains(".."))assets.put(name,zip.readNBytes(4*1024*1024));}}
            byte[] armor=assets.get("assets/zombierool/map_armor.json");if(armor!=null)me.cryo.zombierool.gameplay.MapArmor.load(JsonParser.parseString(utf(armor)).getAsJsonObject());
            for(String lang:List.of("en_us",Minecraft.getInstance().options.languageCode)){byte[] json=assets.get("assets/zombierool/lang/"+lang+".json");if(json!=null)JsonParser.parseString(utf(json)).getAsJsonObject().entrySet().forEach(e->translations.put(e.getKey(),e.getValue().getAsString()));}
            applyLanguage();
            // Install alongside the synchronized pack, after the world registry is ready.
            net.minecraft.client.renderer.item.ItemProperties.registerGeneric(new ResourceLocation("zombierool","purification"),(stack,level,entity,seed)->Math.max(0,Math.min(3,stack.getOrCreateTag().getInt("zr_purification")))/3f);
            byte[] definitions=assets.get("assets/zombierool/sounds.json");if(definitions!=null)for(var row:JsonParser.parseString(utf(definitions)).getAsJsonObject().entrySet()){
                var ids=new ArrayList<String>();for(var sound:row.getValue().getAsJsonObject().getAsJsonArray("sounds")){String id=sound.isJsonPrimitive()?sound.getAsString():sound.getAsJsonObject().get("name").getAsString();ids.add(id.contains(":")?id:"minecraft:"+id);}sounds.put("zombierool:"+row.getKey(),ids);
            }
            for(var row:assets.entrySet())if(row.getKey().endsWith(".ogg")){String id=row.getKey().substring(7).replaceFirst("/sounds/",":").replaceFirst("\\.ogg$","");String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(row.getValue()));Path path=folder.resolve(hash+".ogg");if(!Files.exists(path))Files.write(path,row.getValue());audio.put(id,path.toFile());}
        }catch(Exception e){me.cryo.zombierool.ZombieroolMod.LOGGER.error("Cannot decode map weapon assets",e);}
    }
    private static String utf(byte[] bytes){return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
    public static net.minecraft.client.sounds.WeighedSoundEvents soundEvent(ResourceLocation id){var choices=sounds.get(id.toString());if(choices==null)return null;var event=new net.minecraft.client.sounds.WeighedSoundEvents(id,null);for(String choice:choices){var file=audio.get(choice);if(file!=null)event.addSound(new net.minecraft.client.resources.sounds.Sound("zombierool:dynamic_"+file.getName().replace(".ogg",""),net.minecraft.util.valueproviders.ConstantFloat.of(1),net.minecraft.util.valueproviders.ConstantFloat.of(1),1,net.minecraft.client.resources.sounds.Sound.Type.FILE,true,false,16));}return event;}
    public static File audioFile(String name){for(var file:audio.values())if(file.getName().equals(name+".ogg"))return file;return null;}
    public static String translation(String key){return translations.get(key);}
    public static String texture(String id){try{return sprite(id).atlasLocation().toString();}catch(Exception e){var location=new ResourceLocation(id);return location.getNamespace()+":textures/"+location.getPath()+".png";}}
    public static void clear(){if(languageOwner!=null)try{languageField.set(languageOwner,oldLanguage);}catch(Exception ignored){}languageOwner=null;transfer=null;assets.clear();models.clear();definitions.clear();translations.clear();sounds.clear();audio.clear();for(var sprite:textures.values()){Minecraft.getInstance().getTextureManager().release(sprite.atlasLocation());sprite.contents().close();}textures.clear();generation++;}
    private static JsonObject json(String name){return definitions.computeIfAbsent(name,key->{byte[] data=assets.get("assets/"+key.replace(":","/models/")+".json");return data==null?null:JsonParser.parseString(utf(data)).getAsJsonObject();});}
    private static JsonObject merged(String name,int depth){if(depth>12)return new JsonObject();var child=json(name);if(child==null)return new JsonObject();var out=child.has("parent")?merged(child.get("parent").getAsString(),depth+1):new JsonObject();for(var e:child.entrySet()){if(e.getKey().equals("textures")||e.getKey().equals("display")){var base=out.has(e.getKey())?out.getAsJsonObject(e.getKey()).deepCopy():new JsonObject();e.getValue().getAsJsonObject().entrySet().forEach(x->base.add(x.getKey(),x.getValue()));out.add(e.getKey(),base);}else out.add(e.getKey(),e.getValue());}out.remove("parent");return out;}
    public static BakedModel model(ItemStack stack,LivingEntity entity){if(stack.getItem() instanceof net.minecraft.world.item.BlockItem block&&block.getBlock() instanceof me.cryo.zombierool.gameplay.MapArmor.Station)return null;var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());if(id==null)return null;String name=id.getNamespace()+":item/"+id.getPath();var root=json(name);if(root==null)return null;
        if(root.has("overrides"))for(var entry:root.getAsJsonArray("overrides")){var row=entry.getAsJsonObject();boolean match=true;for(var p:row.getAsJsonObject("predicate").entrySet()){var function=net.minecraft.client.renderer.item.ItemProperties.getProperty(stack.getItem(),new ResourceLocation(p.getKey()));if(function==null||function.call(stack,Minecraft.getInstance().level,entity,0)<p.getValue().getAsFloat())match=false;}if(match)name=row.get("model").getAsString();}
        try{return bake(name);}catch(Exception e){me.cryo.zombierool.ZombieroolMod.LOGGER.error("Map model {}",name,e);return null;}}
    private static Sprite sprite(String id)throws IOException{Sprite cached=textures.get(id);if(cached!=null)return cached;var location=new ResourceLocation(id);byte[] png=assets.get("assets/"+location.getNamespace()+"/textures/"+location.getPath()+".png");if(png==null)throw new IOException("Missing map texture "+id);
        var image=com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(png));var dynamic=com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(png));var atlas=Minecraft.getInstance().getTextureManager().register("map_weapon_"+generation,new DynamicTexture(dynamic));var contents=new SpriteContents(location,new net.minecraft.client.resources.metadata.animation.FrameSize(image.getWidth(),image.getHeight()),image,net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.EMPTY);var sprite=new Sprite(atlas,contents,image.getWidth(),image.getHeight());textures.put(id,sprite);return sprite;}
    private static final class Sprite extends TextureAtlasSprite{Sprite(ResourceLocation atlas,SpriteContents contents,int w,int h){super(atlas,contents,w,h,0,0);}}
    private static BakedModel bake(String id)throws IOException{var cached=models.get(id);if(cached!=null)return cached;var json=merged(id,0);var block=BlockModel.fromString(json.toString());if(!json.has("elements")&&json.has("textures")&&json.getAsJsonObject("textures").has("layer0")){block=new ItemModelGenerator().generateBlockModel(material->{try{return sprite(material.texture().toString());}catch(IOException e){throw new java.io.UncheckedIOException(e);}},block);}if(block.getElements().isEmpty())throw new IOException("No map geometry "+id);var quads=new ArrayList<BakedQuad>();for(var element:block.getElements())for(var face:element.faces.entrySet()){String ref=block.getMaterial(face.getValue().texture).texture().toString();quads.add(BlockModel.bakeFace(element,face.getValue(),sprite(ref),face.getKey(),net.minecraft.client.resources.model.BlockModelRotation.X0_Y0,new ResourceLocation(id)));}var result=new Model(quads,block.getTransforms(),null);models.put(id,result);return result;}
    private static final class Model implements BakedModel{
        final List<BakedQuad> quads;final ItemTransforms transforms;final ResourceLocation texture;Model(List<BakedQuad> q,ItemTransforms t,ResourceLocation texture){quads=List.copyOf(q);transforms=t;this.texture=texture;}
        public List<BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.Direction side,net.minecraft.util.RandomSource random){return side==null?quads:List.of();}
        public boolean useAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean usesBlockLight(){return false;}public boolean isCustomRenderer(){return false;}public TextureAtlasSprite getParticleIcon(){return quads.get(0).getSprite();}public ItemTransforms getTransforms(){return transforms;}public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
        public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){if(texture!=null)return List.of(this);var groups=new LinkedHashMap<ResourceLocation,List<BakedQuad>>();for(var q:quads)groups.computeIfAbsent(q.getSprite().atlasLocation(),k->new ArrayList<>()).add(q);return groups.entrySet().stream().map(e->(BakedModel)new Model(e.getValue(),transforms,e.getKey())).toList();}
        public List<net.minecraft.client.renderer.RenderType> getRenderTypes(ItemStack stack,boolean fabulous){return List.of(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture==null?getParticleIcon().atlasLocation():texture));}
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Connection{
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){clear();me.cryo.zombierool.gameplay.MapWeaponPacks.clear();me.cryo.zombierool.gameplay.MapArmor.clear();me.cryo.zombierool.core.system.WeaponSystem.Loader.loadWeapons();me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.keySet().removeIf(id->!me.cryo.zombierool.gameplay.MapWeaponPacks.available(id));}
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models{
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void bake(net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event){for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS){var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);if(id==null||!id.getNamespace().equals("zombierool")||!(me.cryo.zombierool.gameplay.MapWeaponPacks.RESERVED.contains(id.getPath())||id.getPath().startsWith("stratholme_")||id.getPath().startsWith("map_")||id.getPath().equals("scarlet_guard")))continue;var key=new net.minecraft.client.resources.model.ModelResourceLocation(id,"inventory");var original=event.getModels().get(key);if(original==null)continue;event.getModels().put(key,new net.minecraftforge.client.model.BakedModelWrapper<BakedModel>(original){private final ItemOverrides dynamic=new ItemOverrides(){public BakedModel resolve(BakedModel model,ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,LivingEntity entity,int seed){var loaded=MapWeaponPackClient.model(stack,entity);return loaded!=null?loaded:original.getOverrides().resolve(original,stack,level,entity,seed);}};public ItemOverrides getOverrides(){return dynamic;}});}}
    }
    private MapWeaponPackClient(){}
}
