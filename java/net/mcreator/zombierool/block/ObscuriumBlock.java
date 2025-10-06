package net.mcreator.zombierool.block;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Collections;

public class ObscuriumBlock extends Block {
	public ObscuriumBlock() {
		super(BlockBehaviour.Properties.of()
				.instrument(NoteBlockInstrument.BASEDRUM)
				.sound(SoundType.AMETHYST)
				.strength(-1.0f, 3600000.0f)
				.lightLevel(state -> 7)
				.hasPostProcess((state, world, pos) -> true)
				.emissiveRendering((state, world, pos) -> true)
		);
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
		return 15;
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		return Collections.singletonList(new ItemStack(this));
	}

	@Override
	public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
		if (random.nextFloat() < 0.05f) { // 5% de chance par tick client
			double x = pos.getX() + 0.5;
			double y = pos.getY() + 0.5;
			double z = pos.getZ() + 0.5;

			world.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.01, 0);
		}
	}
}
