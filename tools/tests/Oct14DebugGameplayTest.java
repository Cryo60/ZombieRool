package local.hotfixtest;

import java.util.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.config.*;
import me.cryo.zombierool.util.BloodSurfaceGeometry;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class Oct14DebugGameplayTest {
    private static final class DamageProbe extends net.minecraftforge.common.util.FakePlayer {
        DamageProbe(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ConsoleProbe"));}
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
        @Override public void teleportTo(double x,double y,double z){absMoveTo(x,y,z,getYRot(),getXRot());}
    }
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("OCT14 DEBUG CHECK: "+message);}
    public static void run(ServerLevel level,ServerPlayer original)throws Exception {
        ServerPlayer player=new DamageProbe(level);level.addNewPlayer(player);
        var config=WorldConfig.get(level);int limit=config.getMaxPerks(),points=PointManager.getScore(player);
        var mode=player.gameMode.getGameModeForPlayer();var position=player.position();var inventory=new net.minecraft.nbt.ListTag();player.getInventory().save(inventory);
        float health=player.getHealth(),absorption=player.getAbsorptionAmount();var effects=new ArrayList<>(player.getActiveEffects());
        var field=WaveManager.class.getDeclaredField("gameRunning");field.setAccessible(true);boolean running=field.getBoolean(null);
        var waveField=WaveManager.class.getDeclaredField("currentWave");waveField.setAccessible(true);int wave=waveField.getInt(null);
        var stateField=WaveManager.class.getDeclaredField("currentState");stateField.setAccessible(true);Object state=stateField.get(null);
        boolean cheats=WaveManager.areCheatsUsed();BlockPos pos=new BlockPos(1360,80,0);
        var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)managerField.get(level);
        for(int x=84;x<=86;x++)for(int z=-1;z<=1;z++){level.getChunk(x,z);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),FullChunkStatus.ENTITY_TICKING);}
        var spawnProtection=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawnProtection.setAccessible(true);int protectedTicks=spawnProtection.getInt(player);boolean invulnerable=player.isInvulnerable();
        try {
            field.setBoolean(null,false);waveField.setInt(null,0);
            check(!WaveManager.forceSetWave(level,2),"Wave 2 requested before start is rejected");
            check(!WaveManager.isServerGameRunning()&&WaveManager.getCurrentWave()==0,"Rejected wave change leaves the match idle and wave zero");
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            me.cryo.zombierool.network.packet.S2CWaveUpdatePacket.encode(new me.cryo.zombierool.network.packet.S2CWaveUpdatePacket(2),buffer);
            check(buffer.readInt()==2&&!buffer.readBoolean(),"Wave packets carry the real running flag independently of the number");buffer.release();
            player.setGameMode(GameType.SPECTATOR);DebugConsole.execute(player,"game stop");check(!player.isSpectator()&&!WaveManager.isServerGameRunning(),"Stopping an idle match repairs the orphaned spectator state");
            player.setGameMode(GameType.ADVENTURE);player.removeAllEffects();player.setAbsorptionAmount(0);player.setHealth(player.getMaxHealth());
            spawnProtection.setInt(player,0);player.setInvulnerable(false);player.getAbilities().invulnerable=false;
            var held=player.getMainHandItem().copy();DebugConsole.execute(player,"god on");
            float before=player.getHealth();player.hurt(level.damageSources().generic(),100);player.hurt(level.damageSources().fellOutOfWorld(),100);
            check(player.getHealth()==before&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID()),"God protects against lethal ordinary and void damage without downing");
            check(player.gameMode.getGameModeForPlayer()==GameType.ADVENTURE&&net.minecraft.world.item.ItemStack.matches(held,player.getMainHandItem()),"God preserves game mode and held weapon");
            DebugConsole.execute(player,"god off");player.invulnerableTime=0;player.hurt(level.damageSources().generic(),1);
            check(player.getHealth()<before,"Turning god off restores damage");
            for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.STONE.defaultBlockState(),3);
            player.absMoveTo(pos.getX()-.5,pos.getY(),pos.getZ()+.5,0,0);
            boolean mayfly=player.getAbilities().mayfly,flying=player.getAbilities().flying;
            DebugConsole.execute(player,"noclip on");
            level.setBlock(pos.offset(-1,-1,0),Blocks.STONE.defaultBlockState(),3);
            player.setOnGround(true);player.move(MoverType.PLAYER,new Vec3(0,-.5,0));
            check(!player.noPhysics&&player.getY()>=pos.getY(),"Enabled noclip keeps floor collision while walking");
            player.move(MoverType.PLAYER,new Vec3(1.8,0,0));check(player.getX()<pos.getX(),"Walking with noclip cannot phase through a wall");
            player.getAbilities().flying=true;player.move(MoverType.PLAYER,new Vec3(1.8,0,0));
            check(player.getX()>pos.getX()+1,"Noclip movement crosses a real two-block wall");
            check(player.gameMode.getGameModeForPlayer()==GameType.ADVENTURE&&player.getAbilities().mayfly&&player.getAbilities().flying,"Noclip grants flight without spectator or creative mode");
            player.getAbilities().flying=false;DebugPlayerControls.applyCollision(player);check(!player.noPhysics,"Leaving flight immediately restores collisions");
            player.absMoveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);DebugConsole.execute(player,"noclip off");
            check(!player.noPhysics&&level.noCollision(player,player.getBoundingBox()),"Disabling noclip restores collision and safely exits the wall");
            check(player.getAbilities().mayfly==mayfly&&player.getAbilities().flying==flying,"Noclip restores original flight abilities");
            DebugConsole.execute(player,"perks limit 6");check(PerksManager.getMaxPerks(player)==6,"Console changes the map perk limit to six");
            var saved=new CompoundTag();config.saveEditable(saved);check(WorldConfig.load(saved).getMaxPerks()==6,"Perk limit persists through world config save/load");
            DebugConsole.execute(player,"perks limit -1");check(PerksManager.getMaxPerks(player)==Integer.MAX_VALUE,"Minus one allows unlimited perks");
            DebugConsole.execute(player,"perks limit 0");check(PerksManager.getMaxPerks(player)==0,"Zero prohibits perks");
            var perk=PerksManager.ALL_PERKS.values().stream().filter(p->p.getAssociatedEffect()!=null).findFirst().orElseThrow();perk.applyEffect(player);
            check(!player.hasEffect(perk.getAssociatedEffect()),"Perk grants cannot bypass the map's zero limit");
            DebugConsole.execute(player,"perks limit -1");DebugConsole.execute(player,"perk give "+perk.getId());
            check(player.hasEffect(perk.getAssociatedEffect()),"Console grants a registered perk by ID");DebugConsole.execute(player,"perks clear");check(PerksManager.getPerkCount(player)==0,"Console clears registered perks");
            DebugConsole.execute(player,"points_set 1234");check(PointManager.getScore(player)==1234,"Console sets exact points");
            DebugConsole.execute(player,"heal");check(player.getHealth()==player.getMaxHealth(),"Console healing restores real health");
            check(DebugConsole.execute(player,"status").contains("1234")==false,"Status reports match state without depending on points");
            for(String type:List.of("panzer","brutus","napalm_minion","skeleton_minion","zombie")){
                DebugConsole.execute(player,"spawn "+type+" 2");int count=0;var targets=new ArrayList<net.minecraft.world.entity.Entity>();
                for(var entity:level.getAllEntities())if(entity.getPersistentData().getBoolean("zr_debug_spawn")){count++;targets.add(entity);}
                check(count==2,"Console spawns two actual registered "+type+" enemies");for(var entity:targets)entity.discard();
            }
            check(DebugConsole.execute(player,"enemies")!=null,"Enemy counts include special enemies");
            boolean rejected=false;try{DebugConsole.execute(player,"spawn zombie 51");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Bulk spawn rejects excessive counts");
            DebugConsole.execute(player,"signal omega on");check(MultiReceptorState.isActivated(level,"omega"),"Console supports the actual Omega channel");DebugConsole.execute(player,"signal omega off");
            DebugConsole.execute(player,"signal omega on");MultiReceptorState.refreshInputs(level);check(MultiReceptorState.isActivated(level,"omega"),"Forced debug signals survive normal input refreshes");
            DebugConsole.execute(player,"signal omega auto");check(!MultiReceptorState.isActivated(level,"omega"),"Auto restores the normal signal state");
            player.setAbsorptionAmount(8);MatchPlayerReset.applyEndLoadout(player,level);check(player.getAbsorptionAmount()==0,"Match end clears perk absorption");
            player.setAbsorptionAmount(8);MatchPlayerReset.applyStartLoadout(player,config,1);check(player.getAbsorptionAmount()==0,"Match start clears leftover absorption");
            surfaces(level,pos);
            bloodVariation(level,pos);
            goreCollision(level,pos);
            DebugConsole.execute(player,"god on");DebugConsole.execute(player,"debug_reset");check(!DebugPlayerControls.god(player)&&!DebugPlayerControls.noclip(player),"Debug reset clears both controls");
        }finally{
            DebugPlayerControls.reset(player);config.setMaxPerks(limit);PointManager.setScore(player,points);
            player.getInventory().load(inventory);player.setGameMode(mode);player.absMoveTo(position.x,position.y,position.z,0,0);player.removeAllEffects();for(var effect:effects)player.addEffect(effect);player.setHealth(health);player.setAbsorptionAmount(absorption);
            field.setBoolean(null,running);waveField.setInt(null,wave);stateField.set(null,state);WaveManager.setCheatsUsed(cheats);
            spawnProtection.setInt(player,protectedTicks);player.setInvulnerable(invulnerable);
            for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        System.out.println("OCT14 DEBUG PASS: "+checks+" checks");
    }
    private static void surfaces(ServerLevel level,BlockPos pos){
        for(int x=-2;x<=2;x++)for(int y=-1;y<=3;y++)for(int z=-2;z<=2;z++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        level.removeBlock(pos.above(),false);level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
        for(Direction face:Direction.values()){
            Vec3 center=Vec3.atCenterOf(pos).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5);
            var pieces=BloodSurfaceGeometry.clip(level,center,face,1.2F,.37F,1);check(!pieces.isEmpty(),"Blood is attached on "+face);
            for(var piece:pieces)for(var v:piece.vertices()){
                double x=center.x+v.x(),y=center.y+v.y(),z=center.z+v.z();
                check(x>=pos.getX()-1e-6&&x<=pos.getX()+1+1e-6&&y>=pos.getY()-1e-6&&y<=pos.getY()+1+1e-6&&z>=pos.getZ()-1e-6&&z<=pos.getZ()+1+1e-6,"Blood vertices never overhang the supporting "+face+" face");
            }
        }
        var center=Vec3.atCenterOf(pos).add(0,.5,0);var pieces=BloodSurfaceGeometry.clip(level,center,Direction.UP,1,0,1);
        level.removeBlock(pos,false);check(pieces.stream().noneMatch(piece->piece.present(level)),"Removing support also removes the visible stain");
        check(BloodSurfaceGeometry.clip(level,center,Direction.UP,1,0,1).isEmpty(),"No surface means no floating blood");
        level.setBlock(pos,Blocks.STONE_SLAB.defaultBlockState(),3);check(!BloodSurfaceGeometry.clip(level,new Vec3(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5),Direction.UP,1,.2F,1).isEmpty(),"Blood supports half-height slabs");
    }
    private static void goreCollision(ServerLevel level,BlockPos pos){
        level.setBlock(pos.above(),me.cryo.zombierool.init.ZombieroolModBlocks.RESTRICT.get().defaultBlockState(),3);
        var from=net.minecraft.world.phys.Vec3.atCenterOf(pos.above(2));var to=net.minecraft.world.phys.Vec3.atCenterOf(pos.below());
        var hit=me.cryo.zombierool.util.GoreCollision.clip(level,from,to);
        check(hit.getBlockPos().equals(pos),"Blood rays pass through restrict and hit the visible ground");
        level.setBlock(pos.above(),Blocks.BARRIER.defaultBlockState(),3);
        check(me.cryo.zombierool.util.GoreCollision.clip(level,from,to).getBlockPos().equals(pos),"Blood rays also ignore vanilla invisible barriers");
        var piece=me.cryo.zombierool.init.ZombieroolModEntities.GORE_PIECE.get().create(level);piece.setPos(pos.getX()+.5,pos.getY()+2.1,pos.getZ()+.5);
        me.cryo.zombierool.util.GoreCollision.move(piece,new Vec3(0,-1.4,0));
        check(piece.onGround()&&Math.abs(piece.getY()-pos.getY()-1)<.001,"Flesh falls through an invisible barrier and lands on visible stone");
        level.setBlock(pos.above(),me.cryo.zombierool.init.ZombieroolModBlocks.RESTRICT.get().defaultBlockState(),3);piece.setPos(pos.getX()+.5,pos.getY()+2.1,pos.getZ()+.5);
        me.cryo.zombierool.util.GoreCollision.move(piece,new Vec3(0,-1.4,0));check(piece.onGround()&&Math.abs(piece.getY()-pos.getY()-1)<.001,"Flesh ignores a restrict block without bypassing the real floor");
        level.removeBlock(pos.above(),false);
    }
    private static void bloodVariation(ServerLevel level,BlockPos pos){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        me.cryo.zombierool.entity.BloodDecalEntity.pool(level,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,100);
        var pools=level.getEntitiesOfClass(me.cryo.zombierool.entity.BloodDecalEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3));
        check(pools.size()==100,"The supported scene produces all 100 blood patches");
        var colors=new HashSet<Integer>();float min=Float.MAX_VALUE,max=0;
        for(var pool:pools){min=Math.min(min,pool.getSize());max=Math.max(max,pool.getSize());colors.add(me.cryo.zombierool.util.BloodAppearance.color(pool.getVariant()));}
        check(max/min>6,"Blood pools include both small drops and broad patches");check(colors.size()>20,"Blood uses many fresh and dried shades");
        var first=pools.get(0);var copy=me.cryo.zombierool.init.ZombieroolModEntities.BLOOD_DECAL.get().create(level);copy.load(first.saveWithoutId(new CompoundTag()));
        check(copy.getVariant()==first.getVariant()&&copy.getSize()==first.getSize(),"Pool shape seed and size persist exactly through NBT");
        for(var pool:pools)pool.discard();
    }
}
