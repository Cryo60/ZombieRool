package me.cryo.zombierool.gameplay;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.*;
import java.util.*;

/** Map-owned armor balance, applied after ZombieRool's fixed enemy damage. */
public final class MapArmor {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    private static final Map<String,Float> protection=new HashMap<>();
    private static final Map<String,Integer> prices=new HashMap<>(),repairs=new HashMap<>(),durabilities=new HashMap<>();
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    public static final List<String> SETS=List.of("gambeson","chainmail","plate");
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){for(var set:SETS){for(var type:ArmorItem.Type.values())ITEMS.register("map_"+set+"_"+type.getName(),()->new Piece(set,type));var block=BLOCKS.register("map_"+set+"_station",()->new Station(set));ITEMS.register("map_"+set+"_station",()->new BlockItem(block.get(),new Item.Properties()));}BLOCKS.register(bus);ITEMS.register(bus);}
    public static boolean reserved(String id){return id.startsWith("map_gambeson_")||id.startsWith("map_chainmail_")||id.startsWith("map_plate_");}
    public static void clear(){protection.clear();prices.clear();repairs.clear();durabilities.clear();}
    public static boolean available(String id){if(!reserved(id))return true;return SETS.stream().anyMatch(set->id.startsWith("map_"+set+"_")&&protection.containsKey(set));}
    public static void load(com.google.gson.JsonObject pack){
        if(!pack.has("armor"))return;var armor=pack.getAsJsonObject("armor");
        for(var set:SETS)if(armor.has(set)){float value=armor.get(set).getAsFloat();if(Float.isFinite(value)){protection.put(set,Math.max(0,Math.min(.6f,value)));int index=SETS.indexOf(set);prices.put(set,500*(index+1)*(index+1));repairs.put(set,150*(index+1));}}
        for(var set:SETS){if(pack.has("armor_prices")&&pack.getAsJsonObject("armor_prices").has(set))prices.put(set,Math.max(0,Math.min(100000,pack.getAsJsonObject("armor_prices").get(set).getAsInt())));if(pack.has("armor_repairs")&&pack.getAsJsonObject("armor_repairs").has(set))repairs.put(set,Math.max(0,Math.min(100000,pack.getAsJsonObject("armor_repairs").get(set).getAsInt())));}
        for(var set:SETS){int base=set.equals("plate")?480:set.equals("chainmail")?320:180;durabilities.put(set,pack.has("armor_durability")&&pack.getAsJsonObject("armor_durability").has(set)?Math.max(16,Math.min(4000,pack.getAsJsonObject("armor_durability").get(set).getAsInt())):base);}
    }
    public static float reduce(Player player,net.minecraft.world.damagesource.DamageSource source,float amount){
        if(!(source.getEntity() instanceof net.minecraft.world.entity.Mob)||amount<=0||source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR))return amount;
        float reduction=0;for(var stack:player.getArmorSlots())if(stack.getItem() instanceof Piece piece&&stack.getDamageValue()<stack.getMaxDamage())reduction+=protection.getOrDefault(piece.set,0f)/4;
        return amount*(1-Math.min(.6f,reduction));
    }
    public static boolean equip(net.minecraft.server.level.ServerPlayer player,String set){
        if(!protection.containsKey(set))return false;
        for(var type:ArmorItem.Type.values()){var item=ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool","map_"+set+"_"+type.getName()));if(item==null)return false;}
        for(var type:ArmorItem.Type.values()){
            var previous=player.getItemBySlot(type.getSlot());if(!previous.isEmpty()&&!player.getInventory().add(previous.copy()))player.drop(previous.copy(),false);
            player.setItemSlot(type.getSlot(),new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool","map_"+set+"_"+type.getName()))));
        }return true;
    }
    public static String purchase(net.minecraft.server.level.ServerPlayer player,String set){
        if(!protection.containsKey(set)||!WaveManager.isGameRunning()||!MapActorBehavior.eligible(player))return "unavailable";
        boolean complete=true,damaged=false;for(var type:ArmorItem.Type.values()){var stack=player.getItemBySlot(type.getSlot());if(!(stack.getItem() instanceof Piece piece)||!piece.set.equals(set))complete=false;damaged|=stack.isDamaged();}
        if(complete&&!damaged)return "full";int cost=(complete?repairs:prices).get(set);var cap=player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).resolve();
        if(cap.isEmpty()||cap.get().getPoints()<cost)return "funds";
        if(complete){for(var stack:player.getArmorSlots())stack.setDamageValue(0);}else if(!equip(player,set))return "unavailable";
        cap.get().setPoints(cap.get().getPoints()-cost);cap.get().sync(player);player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_CHAIN,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1);return complete?"repaired":"equipped";
    }
    public static final class Station extends net.minecraft.world.level.block.Block {
        public final String set;
        private Station(String set){super(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(-1,3600000).noOcclusion());this.set=set;}
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return net.minecraft.world.phys.shapes.Shapes.or(box(1,0,2,15,2,14),box(7,2,7,9,7,9),box(1,5,5,15,13,11),box(5,13,5,11,19,11));}
        @Override public net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
            if(hand!=net.minecraft.world.InteractionHand.MAIN_HAND)return net.minecraft.world.InteractionResult.CONSUME;
            if(player instanceof net.minecraft.server.level.ServerPlayer server)interact(server,set);
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
    public static void interact(net.minecraft.server.level.ServerPlayer player,String set){String result=player.isCreative()?"preview":purchase(player,set);player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.map_armor."+result,prices.getOrDefault(set,0),repairs.getOrDefault(set,0)),true);}
    public static net.minecraft.network.chat.Component hint(Player player,String set,String key){boolean complete=true;for(var type:ArmorItem.Type.values())if(!(player.getItemBySlot(type.getSlot()).getItem() instanceof Piece piece)||!piece.set.equals(set))complete=false;return net.minecraft.network.chat.Component.translatable("message.zombierool.map_armor."+(complete?"repair_hint":"buy_hint"),key,net.minecraft.network.chat.Component.translatable("block.zombierool.map_"+set+"_station"),(complete?repairs:prices).getOrDefault(set,0));}
    public static final class Piece extends ArmorItem {
        public final String set;
        private Piece(String set,Type type){super(material(set),type,new Item.Properties().stacksTo(1));this.set=set;}
        @Override public int getMaxDamage(ItemStack stack){return durabilities.getOrDefault(set,super.getMaxDamage(stack));}
        @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){super.appendHoverText(stack,level,lines,flag);lines.add(net.minecraft.network.chat.Component.translatable("tooltip.zombierool.map_armor.set_protection",Math.round(protection.getOrDefault(set,0f)*100)).withStyle(net.minecraft.ChatFormatting.GRAY));lines.add(net.minecraft.network.chat.Component.translatable("tooltip.zombierool.map_armor.condition",stack.getMaxDamage()-stack.getDamageValue(),stack.getMaxDamage()).withStyle(net.minecraft.ChatFormatting.GRAY));}
        @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,String type){return me.cryo.zombierool.client.MapWeaponPackClient.texture("zombierool:models/armor/"+set+"_layer_"+(slot==EquipmentSlot.LEGS?2:1));}
    }
    private static ArmorMaterial material(String set){return new ArmorMaterial(){
        public int getDurabilityForType(ArmorItem.Type type){return set.equals("plate")?480:set.equals("chainmail")?320:180;}
        public int getDefenseForType(ArmorItem.Type type){return 0;}
        public int getEnchantmentValue(){return 0;}
        public net.minecraft.sounds.SoundEvent getEquipSound(){return net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_CHAIN;}
        public Ingredient getRepairIngredient(){return Ingredient.EMPTY;}
        public String getName(){return "map_"+set;}
        public float getToughness(){return 0;}
        public float getKnockbackResistance(){return 0;}
    };}
    private MapArmor(){}
}
