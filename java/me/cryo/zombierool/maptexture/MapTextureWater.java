package me.cryo.zombierool.maptexture;

import me.cryo.zombierool.core.registry.ZRBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.registries.*;

/** Real, flowing water with the selected map texture; registry IDs stay slot-based. */
public final class MapTextureWater {
    public static final DeferredRegister<FluidType> TYPES=DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,"zombierool");
    public static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(ForgeRegistries.FLUIDS,"zombierool");
    public static final RegistryObject<FluidType>[] TYPE=new RegistryObject[MapTextures.SLOTS];
    public static final RegistryObject<ForgeFlowingFluid.Source>[] SOURCE=new RegistryObject[MapTextures.SLOTS];
    public static final RegistryObject<ForgeFlowingFluid.Flowing>[] FLOW=new RegistryObject[MapTextures.SLOTS];
    public static final RegistryObject<Block>[] BLOCKS=new RegistryObject[MapTextures.SLOTS];
    public static final RegistryObject<Item>[] BUCKETS=new RegistryObject[MapTextures.SLOTS];
    private MapTextureWater(){}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){
        TYPES.register(bus);FLUIDS.register(bus);
        for(int i=0;i<MapTextures.SLOTS;i++){
            final int slot=i;String name="maptex_"+i+"_water";
            TYPE[i]=TYPES.register(name,()->new WaterType(slot));
            SOURCE[i]=FLUIDS.register(name,()->new ForgeFlowingFluid.Source(properties(slot)));
            FLOW[i]=FLUIDS.register(name+"_flowing",()->new ForgeFlowingFluid.Flowing(properties(slot)));
            BLOCKS[i]=ZRBlocks.BLOCKS.register(name,()->new LiquidBlock(SOURCE[slot],net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(Blocks.WATER)));
            BUCKETS[i]=ZRBlocks.ITEMS.register(name+"_bucket",()->new BucketItem(SOURCE[slot],new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)){
                @Override public net.minecraft.network.chat.Component getName(ItemStack stack){return net.minecraft.network.chat.Component.translatable("item.zombierool.map_water_bucket",MapTextures.humanize(MapTextures.textureName(slot)));}
            });
            ZRBlocks.ITEMS.register(name,()->new BlockItem(BLOCKS[slot].get(),new Item.Properties()));
        }
    }
    private static ForgeFlowingFluid.Properties properties(int slot){return new ForgeFlowingFluid.Properties(TYPE[slot],SOURCE[slot],FLOW[slot]).block(()->(LiquidBlock)BLOCKS[slot].get()).bucket(BUCKETS[slot]).slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5).explosionResistance(100);}
    public static ResourceLocation texture(int slot){return new ResourceLocation("zombierool","block/maptex/"+slot+"_side");}
    private static final class WaterType extends FluidType {
        private final int slot;
        WaterType(int slot){super(Properties.create().descriptionId("shape.zombierool.water").canSwim(true).canDrown(true).canPushEntity(true).canExtinguish(true).canHydrate(true).canConvertToSource(true).supportsBoating(true).fallDistanceModifier(0).density(1000).viscosity(1000).sound(net.minecraftforge.common.SoundActions.BUCKET_FILL,net.minecraft.sounds.SoundEvents.BUCKET_FILL).sound(net.minecraftforge.common.SoundActions.BUCKET_EMPTY,net.minecraft.sounds.SoundEvents.BUCKET_EMPTY));this.slot=slot;}
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
            @Override public ResourceLocation getStillTexture(){return texture(slot);}
            @Override public ResourceLocation getFlowingTexture(){return texture(slot);}
            @Override public int getTintColor(){return 0xFFFFFFFF;}
            @Override public ResourceLocation getRenderOverlayTexture(net.minecraft.client.Minecraft mc){return me.cryo.zombierool.client.MapTextureClient.waterOverlay(slot);}
            @Override public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera,float partial,net.minecraft.client.multiplayer.ClientLevel level,int distance,float darken,org.joml.Vector3f color){int rgb=MapTextureAppearance.color(slot);return new org.joml.Vector3f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);}
        });}
    }
}
