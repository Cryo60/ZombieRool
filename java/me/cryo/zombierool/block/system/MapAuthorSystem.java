package me.cryo.zombierool.block.system;

import me.cryo.zombierool.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class MapAuthorSystem {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> GATE=BLOCKS.register("one_way_gate",OneWayGate.Block::new);
    public static final RegistryObject<Block> SOUL_CHEST=BLOCKS.register("soul_chest",SoulChestSystem.ChestBlock::new);
    public static final RegistryObject<BlockEntityType<OneWayGate.Anchor>> GATE_TYPE=ENTITIES.register("one_way_gate",()->BlockEntityType.Builder.of(OneWayGate.Anchor::new,GATE.get()).build(null));
    public static final RegistryObject<BlockEntityType<SoulChestSystem.Chest>> SOUL_TYPE=ENTITIES.register("soul_chest",()->BlockEntityType.Builder.of(SoulChestSystem.Chest::new,SOUL_CHEST.get()).build(null));
    public static final RegistryObject<Block> CERBERUS=BLOCKS.register("cerberus",CerberusSystem.WallBlock::new);
    public static final RegistryObject<Block> COMPLETION=BLOCKS.register("soul_completion_checker",SoulCompletionSystem.LogicBlock::new);
    public static final RegistryObject<BlockEntityType<CerberusSystem.Head>> CERBERUS_TYPE=ENTITIES.register("cerberus",()->BlockEntityType.Builder.of(CerberusSystem.Head::new,CERBERUS.get()).build(null));
    public static final RegistryObject<BlockEntityType<SoulCompletionSystem.Checker>> COMPLETION_TYPE=ENTITIES.register("soul_completion_checker",()->BlockEntityType.Builder.of(SoulCompletionSystem.Checker::new,COMPLETION.get()).build(null));
    static {
        ITEMS.register("cerberus",()->new BlockItem(CERBERUS.get(),new Item.Properties()));
        ITEMS.register("soul_completion_checker",()->new BlockItem(COMPLETION.get(),new Item.Properties()));
        ITEMS.register("one_way_gate",()->new BlockItem(GATE.get(),new Item.Properties()));
        ITEMS.register("soul_chest",()->new BlockItem(SOUL_CHEST.get(),new Item.Properties()));
        for(SignalChannel channel:SignalChannel.values()){
            if(java.util.Set.of("alpha","beta","omega","ultima").contains(channel.id()))continue;
            var activator=BLOCKS.register(channel.id()+"_activator",()->new AbstractActivatorBlock(channel));
            var receptor=BLOCKS.register(channel.id()+"_receptor",()->new AbstractReceptorBlock(channel));
            ITEMS.register(channel.id()+"_activator",()->new BlockItem(activator.get(),new Item.Properties()));
            ITEMS.register(channel.id()+"_receptor",()->new BlockItem(receptor.get(),new Item.Properties()));
        }
    }
    private MapAuthorSystem(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}
}
