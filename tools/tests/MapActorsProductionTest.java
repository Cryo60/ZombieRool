package local.actorproduction;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.MapActorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("actorproduction")
public final class MapActorsProductionTest {
    public MapActorsProductionTest(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    private void start(ServerStartedEvent event){var server=event.getServer();try{
        if(net.minecraftforge.fml.ModList.get().isLoaded("tacz"))throw new AssertionError("Expected a standalone ZombieRool test");
        var level=server.overworld();for(int x=-4;x<=8;x++)for(int z=-4;z<=4;z++)for(int y=79;y<86;y++)level.setBlock(new BlockPos(x,y,z),y==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        var data=MapActors.get(level);var id=data.spawn(level,"scarlet_zombie",new net.minecraft.world.phys.Vec3(.5,80,.5),"");
        if(id.isEmpty())throw new AssertionError("Scarlet zombie did not spawn");var zombie=(me.cryo.zombierool.entity.MapActorEntity)level.getEntity(java.util.UUID.fromString(id));
        if(zombie.getMobType()!=net.minecraft.world.entity.MobType.UNDEAD||zombie.getMaxHealth()!=MapActorBehavior.waveHealth(level))throw new AssertionError("Undead status/attributes did not survive production remapping");
        id=data.spawn(level,"balnazzar",new net.minecraft.world.phys.Vec3(5.5,80,.5),"");if(id.isEmpty())throw new AssertionError("Boss did not spawn");var boss=(MapActorEntity)level.getEntity(java.util.UUID.fromString(id));
        if(Math.abs(boss.getBbHeight()-4.725)>.02)throw new AssertionError("Dynamic dimensions override lost during remapping: "+boss.getBbHeight());
        boss.setCastingTicks(30);if(!boss.casting())throw new AssertionError("Casting state failed");
        var image=data.profile("balnazzar").getCompound("animations").getCompound("idle");if(!MapActorAnimations.sample(image,15).getCompound("left_wing").contains("qw"))throw new AssertionError("Quaternion animation did not survive remapping");
        for(String weapon:java.util.List.of("fire_wand","shadow_wand","longbow","scarlet_crossbow"))if(!(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool",weapon)) instanceof me.cryo.zombierool.item.FantasyRangedWeaponItem))throw new AssertionError("Missing weapon "+weapon);
        System.out.println("ACTOR PRODUCTION PASS: standalone Forge startup, undead actor, boss dimensions, casting, animation and four weapons");
    }catch(Throwable t){t.printStackTrace();System.out.println("ACTOR PRODUCTION FAIL: "+t);}finally{server.halt(false);}}
}
