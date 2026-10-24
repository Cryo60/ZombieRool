package me.cryo.zombierool.init;

import net.minecraft.world.item.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

@Mod.EventBusSubscriber(modid="zombierool",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CreativeCatalog {
    public enum Category { ALL, TOOLS, SPAWNERS, NAVIGATION, SIGNALS, MACHINES, CRAFTING, COMPONENTS, WEAPONS, MAP_WEAPONS, SUITS, AMBIENCE, QUESTS, BUILDING, DECORATION, TEXTURES }
    private CreativeCatalog(){}
    private static final Set<String> TOOLS=Set.of("blood_brush","chalk","atmosphere_zone","sound_emitter","particle_emitter");
    private static final Set<String> NAVIGATION=Set.of("path","limit","restrict","zombie_pass","traversal_node","jump_link","crawler_wall_link","one_way_gate","zipline","obstacle_door","damage_barrier","death_barrier","traitor");
    private static final Set<String> MACHINES=Set.of("machine_delivery","ammo_crate","buy_wall_weapon","blind_buy_cabinet","defense_door","defense_wall","glass_defense_door","iron_defense_door","der_wunderfizz","electric_trap","fire_pit","mystery_box","pack_a_punch","perks_a_cola","secret_weapon_rack","teleporter","trap_controller","turret","lunar_helmet_station");
    private static final Set<String> PROPS=Set.of("black_pumpkin","teddy_bear","decorative_component","sandbags","barbed_wire","brain","modern_furnace","scattered_documents","storage_box","wood_crate","spider_web","tiny_bones","tiny_candles","tiny_flame","tiny_skull");
    public static Category category(ItemStack stack){
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());if(id==null)return Category.TOOLS;
        String p=id.getPath();
        if(stack.getItem() instanceof me.cryo.zombierool.gameplay.MapArmor.Piece)return Category.MAP_WEAPONS;
        if(me.cryo.zombierool.gameplay.MapWeaponPacks.RESERVED.contains(p)||p.startsWith("stratholme_"))return Category.MAP_WEAPONS;
        if(p.startsWith("map_")&&p.endsWith("_station"))return Category.MACHINES;
        if(stack.is(me.cryo.zombierool.api.BuildableVisuals.COMPONENTS)||stack.is(me.cryo.zombierool.api.BuildableVisuals.UPRIGHT))return Category.COMPONENTS;
          if(p.startsWith("furniture_"))return Category.DECORATION;
        if(me.cryo.zombierool.gameplay.AmbientScenery.transports().stream().anyMatch(transport->p.equals("decor_"+transport)))return Category.AMBIENCE;
        if(p.equals("decor_car")||p.startsWith("decor_car_")||java.util.Set.of("ambient_dca","aircraft_spawner","tornado_generator","decor_tank","decor_plane","decor_helicopter","decor_truck").contains(p))return Category.AMBIENCE;
        if(PROPS.contains(p))return Category.DECORATION;
        if(p.startsWith("maptex_")||p.equals("texture_kit"))return Category.TEXTURES;
        if(p.equals("music_volume"))return Category.AMBIENCE;
        if(TOOLS.contains(p))return Category.TOOLS;
        if(stack.getItem() instanceof SpawnEggItem||p.equals("map_actor_spawn_egg")||p.endsWith("_spawner"))return Category.SPAWNERS;
        if(p.equals("map_actor_marker"))return Category.MACHINES;
        if(NAVIGATION.contains(p))return Category.NAVIGATION;
        if(p.equals("power_switch")||p.equals("teleporter_switch")||p.equals("soul_completion_checker")||p.endsWith("_activator")||p.endsWith("_receptor")||p.equals("activator"))return Category.SIGNALS;
        if(p.startsWith("buildable_"))return Category.CRAFTING;
        if(p.startsWith("pap_")||p.startsWith("shield_")||p.startsWith("turbine_")||p.startsWith("sentry_")&&!p.equals("sentry_turret"))return Category.COMPONENTS;
        if(stack.getItem() instanceof me.cryo.zombierool.gameplay.PortableEquipment.Portable)return Category.CRAFTING;
        if(p.equals("warden_lock")||p.equals("warden_key"))return Category.QUESTS;
        if(stack.getItem() instanceof me.cryo.zombierool.block.system.LunarStation.SuitItem)return Category.SUITS;
        if(stack.getItem() instanceof BlockItem block&&block.getBlock() instanceof me.cryo.zombierool.block.system.LunarStation.StationBlock)return Category.MACHINES;
        if(stack.getItem() instanceof me.cryo.zombierool.item.ScarletWeaponItem||p.equals("scarlet_shield")||me.cryo.zombierool.core.system.WeaponFacade.isWeapon(stack)||stack.getItem() instanceof me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableItem||stack.getItem() instanceof me.cryo.zombierool.gameplay.EquipmentSystem.LoadoutItem||stack.getItem() instanceof ArmorItem||Set.of("lunar_helmet","stielhandgranate","bowie_knife").contains(p))return Category.WEAPONS;
        if(p.equals("lunar_helmet"))return Category.SUITS;
        if(MACHINES.contains(p))return Category.MACHINES;
        if(Set.of("meteorite","soul_chest","cerberus","ingot_sale").contains(p))return Category.QUESTS;
        // Solid materials and their stairs/slabs/walls form the building palette; loose props are explicit above.
        return stack.getItem() instanceof BlockItem?Category.BUILDING:Category.TOOLS;
    }
    public static Comparator<ItemStack> order(){return Comparator.comparingInt((ItemStack stack)->category(stack).ordinal()).thenComparing(stack->{var id=ForgeRegistries.ITEMS.getKey(stack.getItem());return id==null?"":id.toString();});}
    private static boolean hidden(String path){return me.cryo.zombierool.client.MapActorCatalogue.replaces(path)||!me.cryo.zombierool.gameplay.MapWeaponPacks.available(path)|| Set.of("scarlet_guard","creative_icon","buildable_part","buildable_destination","scenery_route_tool","furniture_bed_head","furniture_bed_foot","furniture_hospital_bed_head","furniture_hospital_bed_foot").contains(path)||path.startsWith("maptex_")||path.startsWith("anim_")||path.endsWith("_opened");}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fill(BuildCreativeModeTabContentsEvent event){
        if(!event.getTabKey().location().toString().equals("zombierool:zb_rct"))return;
        var hidden=new ArrayList<ItemStack>();
        for(var entry:event.getEntries()){
            var id=ForgeRegistries.ITEMS.getKey(entry.getKey().getItem());
            if(id!=null&&id.getNamespace().equals("zombierool")&&hidden(id.getPath()))hidden.add(entry.getKey());
        }
        hidden.forEach(event.getEntries()::remove);
        ForgeRegistries.ITEMS.getEntries().stream().filter(e->e.getKey().location().getNamespace().equals("zombierool")||new ItemStack(e.getValue()).is(me.cryo.zombierool.api.BuildableVisuals.COMPONENTS)||new ItemStack(e.getValue()).is(me.cryo.zombierool.api.BuildableVisuals.UPRIGHT))
            .sorted(Comparator.comparing(e->e.getKey().location().getPath())).forEach(e->{
                String p=e.getKey().location().getPath();
                if(hidden(p)||p.equals("map_actor_spawn_egg"))return;
                ItemStack stack=new ItemStack(e.getValue());
                if(!event.getEntries().contains(stack))event.accept(stack);
            });
    }
}
