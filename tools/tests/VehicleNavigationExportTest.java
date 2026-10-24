package local.navigationexport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.gameplay.VehicleDecorPhysics;
import me.cryo.zombierool.init.ZombieroolModEntities;

/** Must run against the installed SRG jar, with refmap remapping enabled. */
@Mod("navigationexporttest")
public final class VehicleNavigationExportTest {
    public VehicleNavigationExportTest() { MinecraftForge.EVENT_BUS.addListener(this::started); }
    private void started(ServerStartedEvent event) {
        var server=event.getServer();
        try {
            var level=server.overworld();
            var region=new PathNavigationRegion(level,new BlockPos(-8,60,-8),new BlockPos(16,72,8));
            if(VehicleDecorPhysics.level(region)!=level) throw new AssertionError("Region level accessor failed");
            System.out.println("NAVIGATION EXPORT CHECK: production region accessor resolves its SRG field");
            for(int x=-3;x<12;x++) for(int z=-3;z<4;z++) {
                level.setBlock(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=64;y<67;y++) level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);
            if(zombie==null)throw new AssertionError("Zombie creation failed");
            zombie.setPos(.5,64,.5);
            zombie.getNavigation().createPath(new BlockPos(8,64,0),0);
            for(int i=0;i<40;i++)zombie.tick();
            System.out.println("NAVIGATION EXPORT PASS: zombie route planning and ticking without mixin failure");
        } catch(Throwable error) {
            error.printStackTrace();System.out.println("NAVIGATION EXPORT FAILED: "+error);
        } finally {server.halt(false);}
    }
}
