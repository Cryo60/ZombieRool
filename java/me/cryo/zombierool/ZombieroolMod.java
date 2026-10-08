/*
 *    MCreator note:
 *
 *    If you lock base mod element files, you can edit this file and it won't get overwritten.
 *    If you change your modid or package, you need to apply these changes to this file MANUALLY.
 *
 *    Settings in @Mod annotation WON'T be changed in case of the base mod element
 *    files lock too, so you need to set them manually here in such case.
 *
 *    If you do not lock base mod element files in Workspace settings, this file
 *    will be REGENERATED on each build.
 *
 */
package me.cryo.zombierool;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

import me.cryo.zombierool.init.ZombieroolModTabs;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.init.ZombieroolModItems;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.AbstractMap;

@Mod("zombierool")
public class ZombieroolMod {
	public static final Logger LOGGER = LogManager.getLogger(ZombieroolMod.class);
	public static final String MODID = "zombierool";

	public ZombieroolMod() {
		MinecraftForge.EVENT_BUS.register(this);
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		ZombieroolModSounds.REGISTRY.register(bus);
		ZombieroolModBlocks.REGISTRY.register(bus);
        me.cryo.zombierool.block.system.MapDeviceSystem.register(bus);
		ZombieroolModBlockEntities.REGISTRY.register(bus);
		ZombieroolModItems.REGISTRY.register(bus);
		ZombieroolModEntities.REGISTRY.register(bus);

		ZombieroolModTabs.REGISTRY.register(bus);

		ZombieroolModMobEffects.REGISTRY.register(bus);

		ZombieroolModParticleTypes.REGISTRY.register(bus);

	}

	private static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

	public static void queueServerWork(int tick, Runnable action) {
		workQueue.add(new AbstractMap.SimpleEntry(action, tick));
	}

	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			List<AbstractMap.SimpleEntry<Runnable, Integer>> actions = new ArrayList<>();
			workQueue.forEach(work -> {
				work.setValue(work.getValue() - 1);
				if (work.getValue() == 0)
					actions.add(work);
			});
			actions.forEach(e -> e.getKey().run());
			workQueue.removeAll(actions);
		}
	}
}
