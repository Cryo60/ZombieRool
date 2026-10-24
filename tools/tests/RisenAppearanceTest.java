package local.strathprobe;

import java.util.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.MapActorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("strathprobe")
public final class RisenAppearanceTest {
    public RisenAppearanceTest(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("RISEN CHECK: "+text);}
    private void start(ServerStartedEvent event){var level=event.getServer().overworld();try{
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=199;y<=205;y++)level.setBlock(new BlockPos(x,y,z),y==199?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        var actors=MapActors.get(level);int total=0;
        check(actors.ids().stream().noneMatch(id->id.startsWith("scarlet_zombie")&&id.endsWith("_female")),"The catalogue contains no separate female Risen");
        for(String suffix:List.of("","_swordsman","_axeman","_pikeman","_archer","_mage")){
            String id="scarlet_zombie"+suffix;var definition=actors.profile(id);
            check(definition.getList("appearances",10).size()==1,"Both appearances belong to the single role "+id);
            int female=0,male=0;
            for(int i=0;i<48;i++){
                String uuid=actors.spawn(level,id,new Vec3(.5,200,.5),"");if(uuid.isEmpty())throw new AssertionError("Spawn failed: "+id+" #"+i);
                var actor=(MapActorEntity)level.getEntity(UUID.fromString(uuid));var p=actor.profile();int choice=p.getInt("appearance");
                if(choice==0)male++;else female++;
                if(!p.getString("id").equals(id)||p.contains("appearances"))throw new AssertionError("Unexpected identity or unselected payload");
                var voice=p.getCompound("sounds").getList("death",8).getString(0);
                if(!voice.contains(choice==0?"risen_male_":"risen_female_"))throw new AssertionError("Voice differs from chosen appearance");
                if(p.getDouble("damage")!=definition.getDouble("damage")||!p.getString("callback").equals(definition.getString("callback")))throw new AssertionError("Appearance changed role mechanics");
                String texture=p.getString("texture");var saved=new CompoundTag();actor.addAdditionalSaveData(saved);actor.readAdditionalSaveData(saved);
                if(actor.profile().getInt("appearance")!=choice||!actor.profile().getString("texture").equals(texture))throw new AssertionError("Reload rerolled appearance");
                if(choice==1){saved.getCompound("MapProfile").putString("id",id+"_female");saved.getCompound("MapProfile").remove("appearance");actor.readAdditionalSaveData(saved);if(!actor.profile().getString("id").equals(id)||actor.profile().getInt("appearance")!=1)throw new AssertionError("Legacy female entity migration failed");}
                actor.discard();total++;
            }
            check(male>5&&female>5,id+" randomly produces both appearances (male="+male+", female="+female+")");
            var marker=new BlockPos(10,200,10);var rule=new CompoundTag();rule.putString("profile",id+"_female");rule.putString("trigger","game_start");actors.marker(marker,rule);
            check(actors.marker(marker).getString("profile").equals(id),"Old spawner settings migrate to the unified role");actors.removeMarker(marker);
            String alias=actors.spawn(level,id+"_female",new Vec3(.5,200,.5),"");
            check(!alias.isEmpty(),"Old female spawners remain usable as a unified role");level.getEntity(UUID.fromString(alias)).discard();
        }
        var pos=new BlockPos(0,200,0);var scene=AmbientScenery.defaults(pos);scene.putString("dcaModel","dwarven");var clean=AmbientScenery.sanitize(scene,pos);
        check(clean.getString("dcaModel").equals("dwarven")&&AmbientScenery.muzzle(clean)==2.92,"Dwarven artillery model and muzzle survive server validation");
        System.out.println("RISEN PASS: "+total+" real spawns, automatic genders, voice consistency, reload persistence, legacy compatibility and artillery validation");
    }catch(Throwable error){error.printStackTrace();}finally{event.getServer().halt(false);}}
}
