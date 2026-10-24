package me.cryo.zombierool.gameplay;
import me.cryo.zombierool.block.system.TraversalSystem;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool")
public final class TransportEvents {
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){
        if(e.getLevel() instanceof net.minecraft.world.level.Level level){TraversalSystem.unload(level);me.cryo.zombierool.block.system.TeleporterSystem.unload(level);me.cryo.zombierool.block.system.OneWayGate.unload(level);me.cryo.zombierool.block.system.SoulChestSystem.unload(level);me.cryo.zombierool.block.system.SoulCompletionSystem.unload(level);}
    }
    @SubscribeEvent public static void living(LivingEvent.LivingTickEvent e){if(!e.getEntity().level().isClientSide&&e.getEntity() instanceof Mob mob&&!FiestaFreeze.frozen(mob)){me.cryo.zombierool.block.system.OneWayGate.tick(mob);TraversalSystem.approach(mob);}}
}
