
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.Block;

import me.cryo.zombierool.block.AbstractActivatorBlock;
import me.cryo.zombierool.block.AbstractReceptorBlock;
import me.cryo.zombierool.block.ActivatorBlock;
import me.cryo.zombierool.block.AmmoCrateBlock;
import me.cryo.zombierool.block.BlackPumpkinBlock;
import me.cryo.zombierool.block.DamageBarrierBlock;
import me.cryo.zombierool.block.DeathBarrierBlock;
import me.cryo.zombierool.block.DerWunderfizzBlock;
import me.cryo.zombierool.block.LimitBlock;
import me.cryo.zombierool.block.PathBlock;
import me.cryo.zombierool.block.PowerSwitchBlock;
import me.cryo.zombierool.block.RestrictBlock;
import me.cryo.zombierool.block.SignalChannel;
import me.cryo.zombierool.block.TraitorBlock;
import me.cryo.zombierool.block.ZombiePassBlock;
import me.cryo.zombierool.ZombieroolMod;

public class ZombieroolModBlocks {
	public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
	public static final RegistryObject<Block> PATH = REGISTRY.register("path", () -> new PathBlock());
	public static final RegistryObject<Block> LIMIT = REGISTRY.register("limit", () -> new LimitBlock());
	public static final RegistryObject<Block> RESTRICT = REGISTRY.register("restrict", () -> new RestrictBlock());
	public static final RegistryObject<Block> DEATH_BARRIER = REGISTRY.register("death_barrier", () -> new DeathBarrierBlock());
	public static final RegistryObject<Block> DAMAGE_BARRIER = REGISTRY.register("damage_barrier", () -> new DamageBarrierBlock());
	public static final RegistryObject<Block> POWER_SWITCH = REGISTRY.register("power_switch", () -> new PowerSwitchBlock());
	public static final RegistryObject<Block> ACTIVATOR = REGISTRY.register("activator", () -> new ActivatorBlock());
	public static final RegistryObject<Block> ALPHA_ACTIVATOR = REGISTRY.register("alpha_activator", () -> new AbstractActivatorBlock(SignalChannel.ALPHA));
	public static final RegistryObject<Block> ALPHA_RECEPTOR = REGISTRY.register("alpha_receptor", () -> new AbstractReceptorBlock(SignalChannel.ALPHA));
	public static final RegistryObject<Block> BETA_ACTIVATOR = REGISTRY.register("beta_activator", () -> new AbstractActivatorBlock(SignalChannel.BETA));
	public static final RegistryObject<Block> BETA_RECEPTOR = REGISTRY.register("beta_receptor", () -> new AbstractReceptorBlock(SignalChannel.BETA));
	public static final RegistryObject<Block> OMEGA_ACTIVATOR = REGISTRY.register("omega_activator", () -> new AbstractActivatorBlock(SignalChannel.OMEGA));
	public static final RegistryObject<Block> OMEGA_RECEPTOR = REGISTRY.register("omega_receptor", () -> new AbstractReceptorBlock(SignalChannel.OMEGA));
	public static final RegistryObject<Block> ULTIMA_ACTIVATOR = REGISTRY.register("ultima_activator", () -> new AbstractActivatorBlock(SignalChannel.ULTIMA));
	public static final RegistryObject<Block> ULTIMA_RECEPTOR = REGISTRY.register("ultima_receptor", () -> new AbstractReceptorBlock(SignalChannel.ULTIMA));
	public static final RegistryObject<Block> TRAITOR = REGISTRY.register("traitor", () -> new TraitorBlock());
	public static final RegistryObject<Block> ZOMBIE_PASS = REGISTRY.register("zombie_pass", () -> new ZombiePassBlock());
	public static final RegistryObject<Block> AMMO_CRATE = REGISTRY.register("ammo_crate", () -> new AmmoCrateBlock());
	public static final RegistryObject<Block> BLACK_PUMPKIN = REGISTRY.register("black_pumpkin", () -> new BlackPumpkinBlock());
	public static final RegistryObject<Block> DER_WUNDERFIZZ = REGISTRY.register("der_wunderfizz", () -> new DerWunderfizzBlock());
}
