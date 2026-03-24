
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.Block;

import me.cryo.zombierool.block.ZombiePassBlock;
import me.cryo.zombierool.block.UltimaReceptorBlock;
import me.cryo.zombierool.block.UltimaActivatorBlock;
import me.cryo.zombierool.block.TraitorBlock;
import me.cryo.zombierool.block.RestrictBlock;
import me.cryo.zombierool.block.PowerSwitchBlock;
import me.cryo.zombierool.block.PathBlock;
import me.cryo.zombierool.block.OmegaReceptorBlock;
import me.cryo.zombierool.block.OmegaActivatorBlock;
import me.cryo.zombierool.block.LimitBlock;
import me.cryo.zombierool.block.DerWunderfizzBlock;
import me.cryo.zombierool.block.BlackPumpkinBlock;
import me.cryo.zombierool.block.BetaReceptorBlock;
import me.cryo.zombierool.block.BetaActivatorBlock;
import me.cryo.zombierool.block.AmmoCrateBlock;
import me.cryo.zombierool.block.AlphaReceptorBlock;
import me.cryo.zombierool.block.AlphaActivatorBlock;
import me.cryo.zombierool.block.ActivatorBlock;
import me.cryo.zombierool.ZombieroolMod;

public class ZombieroolModBlocks {
	public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
	public static final RegistryObject<Block> PATH = REGISTRY.register("path", () -> new PathBlock());
	public static final RegistryObject<Block> LIMIT = REGISTRY.register("limit", () -> new LimitBlock());
	public static final RegistryObject<Block> RESTRICT = REGISTRY.register("restrict", () -> new RestrictBlock());
	public static final RegistryObject<Block> POWER_SWITCH = REGISTRY.register("power_switch", () -> new PowerSwitchBlock());
	public static final RegistryObject<Block> ACTIVATOR = REGISTRY.register("activator", () -> new ActivatorBlock());
	public static final RegistryObject<Block> ALPHA_ACTIVATOR = REGISTRY.register("alpha_activator", () -> new AlphaActivatorBlock());
	public static final RegistryObject<Block> ALPHA_RECEPTOR = REGISTRY.register("alpha_receptor", () -> new AlphaReceptorBlock());
	public static final RegistryObject<Block> BETA_ACTIVATOR = REGISTRY.register("beta_activator", () -> new BetaActivatorBlock());
	public static final RegistryObject<Block> BETA_RECEPTOR = REGISTRY.register("beta_receptor", () -> new BetaReceptorBlock());
	public static final RegistryObject<Block> OMEGA_ACTIVATOR = REGISTRY.register("omega_activator", () -> new OmegaActivatorBlock());
	public static final RegistryObject<Block> OMEGA_RECEPTOR = REGISTRY.register("omega_receptor", () -> new OmegaReceptorBlock());
	public static final RegistryObject<Block> ULTIMA_ACTIVATOR = REGISTRY.register("ultima_activator", () -> new UltimaActivatorBlock());
	public static final RegistryObject<Block> ULTIMA_RECEPTOR = REGISTRY.register("ultima_receptor", () -> new UltimaReceptorBlock());
	public static final RegistryObject<Block> TRAITOR = REGISTRY.register("traitor", () -> new TraitorBlock());
	public static final RegistryObject<Block> ZOMBIE_PASS = REGISTRY.register("zombie_pass", () -> new ZombiePassBlock());
	public static final RegistryObject<Block> AMMO_CRATE = REGISTRY.register("ammo_crate", () -> new AmmoCrateBlock());
	public static final RegistryObject<Block> BLACK_PUMPKIN = REGISTRY.register("black_pumpkin", () -> new BlackPumpkinBlock());
	public static final RegistryObject<Block> DER_WUNDERFIZZ = REGISTRY.register("der_wunderfizz", () -> new DerWunderfizzBlock());
}
