package me.cryo.zombierool.mixins;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PathNavigationRegion.class)
public interface VehicleNavigationRegionAccessor {@Accessor("level") Level zr$vehicleLevel();}
