package me.cryo.zombierool.block.system;

import me.cryo.zombierool.block.AbstractTechnicalBlock;
import me.cryo.zombierool.gameplay.LunarEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** Invisible during gameplay; persistent bounded atmosphere editable in creative. */
public final class AtmosphereZoneBlock extends AbstractTechnicalBlock implements EntityBlock {
    public AtmosphereZoneBlock(){super(Properties.of().strength(-1,3600000).noCollission().noOcclusion());}
    @Override protected void addTechnicalTooltip(List<Component> lines){lines.add(Component.translatable("tooltip.zombierool.atmosphere_zone"));}
    @Override protected VoxelShape getTechnicalCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Zone(p,s);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
        if(p instanceof ServerPlayer sp&&p.isCreative()&&l.getBlockEntity(pos) instanceof Zone z)LunarZonePackets.open(sp,z);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock())&&l instanceof ServerLevel server){LunarEnvironment.get(server).remove(id(pos));LunarEnvironment.get(server).unbind(pos);}super.onRemove(s,l,pos,next,moving);}
    public static String id(BlockPos p){return "@block/"+p.asLong();}
    public static final class Zone extends BlockEntity {
        private CompoundTag config=defaults();
        public Zone(BlockPos p,BlockState s){super(LunarStation.ZONE_ENTITY.get(),p,s);}
        public static String randomId(){return "zone_"+java.util.UUID.randomUUID().toString().replace("-","").substring(0,12);}
        public static CompoundTag defaults(){var n=new CompoundTag();n.putString("id",randomId());n.putBoolean("enabled",true);n.putBoolean("vacuum",true);n.putDouble("gravity",.167);n.putInt("radiusX",8);n.putInt("radiusZ",8);n.putInt("height",8);return n;}
        public CompoundTag config(){return config.copy();}
        public AABB bounds(){int x=config.getInt("radiusX"),z=config.getInt("radiusZ");var p=worldPosition;return new AABB(p.getX()-x,p.getY(),p.getZ()-z,p.getX()+x+1,p.getY()+config.getInt("height"),p.getZ()+z+1);}
        public void configure(CompoundTag n){
            String name=n.getString("id").trim();if(name.isBlank())name=randomId();if(name.length()>128||name.startsWith("@block/"))return;
            if(level instanceof ServerLevel server&&!LunarEnvironment.get(server).canBind(name,worldPosition))return;
            config=defaults();config.putString("id",name);config.putBoolean("enabled",n.getBoolean("enabled"));config.putBoolean("vacuum",n.getBoolean("vacuum"));config.putDouble("gravity",LunarEnvironment.gravity(n.getDouble("gravity")));
            for(String k:List.of("radiusX","radiusZ","height"))config.putInt(k,Math.max(1,Math.min(256,n.getInt(k))));setChanged();apply();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }
        private void apply(){if(!(level instanceof ServerLevel server))return;var environment=LunarEnvironment.get(server);String id=id(worldPosition);if(config.getString("id").isBlank()||!environment.canBind(config.getString("id"),worldPosition)){config.putString("id",randomId());setChanged();}environment.bind(config.getString("id"),worldPosition);
            if(!config.getBoolean("enabled")){environment.remove(id);return;}
            var p=worldPosition;int x=config.getInt("radiusX"),z=config.getInt("radiusZ");var box=new AABB(p.getX()-x,p.getY(),p.getZ()-z,p.getX()+x+1,p.getY()+config.getInt("height"),p.getZ()+z+1);
            if(!environment.matches(id,box,config.getDouble("gravity"),config.getBoolean("vacuum")))environment.put(id,box,config.getDouble("gravity"),config.getBoolean("vacuum"));
        }
        @Override public void onLoad(){super.onLoad();apply();}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
        @Override public void load(CompoundTag n){super.load(n);if(n.contains("Atmosphere",10)){config=n.getCompound("Atmosphere").copy();apply();}}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.put("Atmosphere",config.copy());}
    }
}
