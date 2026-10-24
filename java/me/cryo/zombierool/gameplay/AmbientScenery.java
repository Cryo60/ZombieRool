package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.block.system.MapDeviceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** Bounded, deterministic decorative devices. No entities, projectiles, damage or fire blocks. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class AmbientScenery {
    public static boolean kind(MapDeviceSystem.Kind k){return k==MapDeviceSystem.Kind.DCA||k==MapDeviceSystem.Kind.AIRCRAFT||k==MapDeviceSystem.Kind.TORNADO||k==MapDeviceSystem.Kind.VEHICLE;}
    public static CompoundTag defaults(BlockPos pos){var n=new CompoundTag();n.putString("variant","normal");n.putString("vehicle","tank");n.putString("carModel","sedan");n.putString("era","legacy");n.putString("dcaModel","flak88");n.putString("fireMode","varied");n.putDouble("trackingTurnSpeed",22);n.putDouble("altitude",50);n.putDouble("radius",48);n.putDouble("flightSpeed",8);n.putDouble("size",1);n.putInt("amount",4);n.putDouble("tracerMinHeight",30);n.putDouble("tracerMaxHeight",40);n.putDouble("heading",0);n.putDouble("yaw",0);n.putDouble("pitch",0);n.putDouble("roll",0);n.putDouble("pathLength",256);n.putDouble("soundRange",128);n.putDouble("targetX",pos.getX()+.5);n.putDouble("targetY",pos.getY()+65);n.putDouble("targetZ",pos.getZ()+60);n.putBoolean("loop",true);n.put("route",new ListTag());return n;}
    private static double value(CompoundTag n,String key,double fallback,double min,double max){double v=n.contains(key)?n.getDouble(key):fallback;return Double.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
    public static CompoundTag sanitize(CompoundTag input,BlockPos pos){var n=defaults(pos);n.putString("variant",java.util.Set.of("normal","fire","airship","necropolis","broken","crashed","burnt").contains(input.getString("variant"))?input.getString("variant"):"normal");
        n.putString("vehicle",(java.util.Set.of("tank","plane","helicopter","truck","car").contains(input.getString("vehicle"))||transports().contains(input.getString("vehicle")))?input.getString("vehicle"):"tank");
        n.putString("carModel",carModels().contains(input.getString("carModel"))?input.getString("carModel"):"sedan");
        n.putString("era",input.getString("era").equals("modern")||n.getString("vehicle").equals("helicopter")?"modern":"legacy");
        n.putString("dcaModel",java.util.Set.of("flak88","light20","quad14","modern","dwarven").contains(input.getString("dcaModel"))?input.getString("dcaModel"):"flak88");
        n.putString("fireMode",java.util.Set.of("regular","varied","tracking").contains(input.getString("fireMode"))?input.getString("fireMode"):"varied");
        n.putDouble("trackingTurnSpeed",value(input,"trackingTurnSpeed",22,5,90));
        n.putDouble("tracerMinHeight",value(input,"tracerMinHeight",30,2,96));n.putDouble("tracerMaxHeight",Math.max(n.getDouble("tracerMinHeight"),value(input,"tracerMaxHeight",40,2,96)));
        n.putDouble("heading",value(input,"heading",0,-360,360));n.putDouble("yaw",value(input,"yaw",n.getDouble("heading"),-360,360));n.putDouble("pitch",value(input,"pitch",0,-180,180));n.putDouble("roll",value(input,"roll",0,-180,180));n.putDouble("pathLength",value(input,"pathLength",256,64,384));n.putDouble("soundRange",value(input,"soundRange",128,16,192));
        n.putDouble("altitude",value(input,"altitude",50,8,192));n.putDouble("radius",value(input,"radius",48,8,192));n.putDouble("flightSpeed",value(input,"flightSpeed",8,n.getString("variant").equals("necropolis")?0:.25,40));n.putDouble("size",value(input,"size",n.getString("variant").equals("necropolis")?24:1,n.getString("variant").equals("necropolis")?4:.25,n.getString("variant").equals("necropolis")?64:4));n.putInt("amount",(int)value(input,"amount",4,1,8));n.putBoolean("loop",!input.contains("loop")||input.getBoolean("loop"));
        for(String key:java.util.List.of("targetX","targetY","targetZ")){double base=key.equals("targetX")?pos.getX():key.equals("targetY")?pos.getY():pos.getZ();n.putDouble(key,value(input,key,n.getDouble(key),base-512,base+512));}
        var route=new ListTag();for(var tag:input.getList("route",10)){if(route.size()>=32)break;var row=(CompoundTag)tag;var clean=new CompoundTag();for(String key:java.util.List.of("x","y","z")){double base=key.equals("x")?pos.getX():key.equals("y")?pos.getY():pos.getZ();clean.putDouble(key,value(row,key,base,base-192,base+192));}route.add(clean);}n.put("route",route);return n;
    }
    public static java.util.List<String> transports(){return java.util.List.of("city_bus","school_bus","coach","box_truck","pickup","tanker","delivery_van","ambulance","pave_low","stryker","btr80","bmp2","humvee","snowmobile","zodiac");}
    public static java.util.List<String> carModels(){return java.util.List.of("sedan","compact","muscle","sport","estate","suv","taxi","police");}
    public static String vehicleMesh(CompoundTag n){return (n.getString("vehicle").equals("car")?"car_"+n.getString("carModel"):transports().contains(n.getString("vehicle"))?n.getString("vehicle"):n.getString("vehicle")+"_"+n.getString("era"))+"_"+(n.getString("variant").equals("normal")?"normal":(n.getString("vehicle").equals("car")||transports().contains(n.getString("vehicle")))&&(n.getString("variant").equals("fire")||n.getString("variant").equals("burnt"))?"burnt":"broken");}
    public static String vehicle(MapDeviceSystem.Device d){return d.scenery.getString("vehicle");}
    public static java.util.List<String> states(String vehicle){if(vehicle.equals("car")||transports().contains(vehicle))return java.util.List.of("normal","broken","burnt","fire");return vehicle.equals("plane")||vehicle.equals("helicopter")?java.util.List.of("normal","broken","crashed","fire"):java.util.List.of("normal","broken","fire");}
    public static double muzzle(CompoundTag n){return switch(n.getString("dcaModel")){case "flak88"->4.22;case "dwarven"->2.92;default->2.0;};}
    public static Vec3 target(MapDeviceSystem.Device d){var n=d.scenery;return new Vec3(n.getDouble("targetX"),n.getDouble("targetY"),n.getDouble("targetZ"));}
    private static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    private static double unit(long x){return (mix(x)>>>11)*0x1.0p-53;}
    private static long seed(MapDeviceSystem.Device d){return mix(d.getBlockPos().asLong()^0x7e61a5d3L);}
    public static double dcaPeriod(MapDeviceSystem.Device d){return d.scenery.getString("fireMode").equals("regular")?d.interval/20.0:Math.max(.12,Math.min(.5,d.interval/80.0));}
    private static double offset(MapDeviceSystem.Device d){return unit(seed(d))*61;}
    public static long dcaCycle(MapDeviceSystem.Device d,double seconds){return (long)Math.floor((Math.max(0,seconds)+offset(d))/dcaPeriod(d));}
    public static double dcaAge(MapDeviceSystem.Device d,double seconds,long cycle){return seconds+offset(d)-cycle*dcaPeriod(d);}
    public static boolean dcaFires(MapDeviceSystem.Device d,long cycle){
        double local=cycle*dcaPeriod(d)-offset(d);if(local<0)return false;
        String mode=d.scenery.getString("fireMode");if(mode.equals("tracking")&&!tracking(d).found)return false;
        if(mode.equals("regular"))return true;
        // Different scene durations and per-shot gaps prevent synchronized metronomic fire.
        double duration=6+unit(seed(d)^231)*9;
        double clock=cycle*dcaPeriod(d)+unit(seed(d)^73)*97;long section=(long)Math.floor(clock/duration);double phase=clock-section*duration;
        double choice=unit(seed(d)^mix(section));
        if(choice<.23)return false;
        if(mode.equals("tracking")&&unit(seed(d)^mix(cycle)^511)<.22)return false;
        if(choice<.66){double burst=.8+unit(seed(d)^section^37)*1.1;return phase%burst<Math.min(.62,burst*.45);}
        return true;
    }
    public record Aim(Vec3 point,boolean found){}
    public static Aim tracking(MapDeviceSystem.Device d){
        long tick=d.getLevel().getGameTime();if(d.sceneryAim!=null&&tick>=d.sceneryAimTick&&tick-d.sceneryAimTick<4)return d.sceneryAim;
        Vec3 muzzle=Vec3.atLowerCornerOf(d.getBlockPos()).add(.5,1.4,.5),best=target(d);double priority=-1;boolean found=false;
        double dwell=5+unit(seed(d)^127)*8;long selection=(long)Math.floor(((tick-d.sceneryStart)/20.0+unit(seed(d)^53)*41)/dwell);
        for(var source:MapDeviceSystem.devices(d.getLevel())){
            if(source.isRemoved()||source.scenery.getString("variant").equals("necropolis")||source.kind()!=MapDeviceSystem.Kind.AIRCRAFT||!source.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE))continue;
            double seconds=(tick-source.sceneryStart)/20.0;var origin=Vec3.atLowerCornerOf(source.getBlockPos()).add(.5,0,.5);
            for(int i=0;i<source.scenery.getInt("amount");i++){
                if(!aircraftVisible(source.scenery,i,seconds))continue;
                var at=aircraftPosition(source.scenery,i,seconds).add(origin);double distance=at.distanceToSqr(muzzle);
                if(at.y<muzzle.y+4||distance>192*192)continue;
                double candidate=unit(seed(d)^mix(selection)^mix(source.getBlockPos().asLong())^mix(i+33));
                if(candidate<=priority)continue;
                priority=candidate;found=true;best=aircraftPosition(source.scenery,i,seconds+Math.min(2,Math.sqrt(distance)/60)).add(origin);
                double clock=(tick-d.sceneryStart)/20.0,phase=unit(seed(d)^selection)*Math.PI*2;
                best=best.add(Math.sin(clock*.47+phase)*3.5,Math.sin(clock*.31+phase)*1.8,Math.cos(clock*.39+phase)*3.5);
            }
        }
        d.sceneryAimTick=tick;return d.sceneryAim=new Aim(best,found);
    }
    public static Vec3 dcaTarget(MapDeviceSystem.Device d){return d.scenery.getString("fireMode").equals("tracking")?tracking(d).point:target(d);}
    public record Angles(float yaw,float pitch){}
    public static float approachAngle(float current,float target,float maxStep){return current+net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(target-current),-maxStep,maxStep);}
    private static Angles angles(Vec3 direction){return new Angles((float)Math.toDegrees(Math.atan2(direction.x,direction.z)),(float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))));}
    public static void tickAim(MapDeviceSystem.Device d){
        if(d.kind()!=MapDeviceSystem.Kind.DCA||d.getLevel()==null)return;
        long tick=d.getLevel().getGameTime();if(d.sceneryRotationReady&&d.sceneryRotationTick==tick)return;
        var origin=Vec3.atLowerCornerOf(d.getBlockPos()).add(.5,1.4,.5);
        if(!d.sceneryRotationReady){var start=angles(target(d).subtract(origin));d.sceneryYaw=d.sceneryPrevYaw=start.yaw;d.sceneryPitch=d.sceneryPrevPitch=start.pitch;d.sceneryRotationTick=tick-1;d.sceneryRotationReady=true;}
        float dt=Math.max(1,Math.min(2,tick-d.sceneryRotationTick));d.sceneryRotationTick=tick;d.sceneryPrevYaw=d.sceneryYaw;d.sceneryPrevPitch=d.sceneryPitch;
        var desired=angles(dcaTarget(d).subtract(origin));boolean tracking=d.scenery.getString("fireMode").equals("tracking");float step=(float)d.scenery.getDouble("trackingTurnSpeed")/20*dt;
        d.sceneryYaw=tracking?approachAngle(d.sceneryYaw,desired.yaw,step):desired.yaw;d.sceneryPitch=tracking?approachAngle(d.sceneryPitch,desired.pitch,step*.65f):desired.pitch;
    }
    public static Angles dcaAngles(MapDeviceSystem.Device d,float partial){tickAim(d);return new Angles(net.minecraft.util.Mth.rotLerp(partial,d.sceneryPrevYaw,d.sceneryYaw),net.minecraft.util.Mth.lerp(partial,d.sceneryPrevPitch,d.sceneryPitch));}
    public static Vec3 tornadoPosition(CompoundTag n,BlockPos base,double seconds){var route=n.getList("route",10);if(route.isEmpty())return Vec3.atLowerCornerOf(base).add(.5,0,.5);if(route.size()==1)return point(route.getCompound(0));double speed=n.getDouble("flightSpeed"),total=0;int segments=n.getBoolean("loop")?route.size():route.size()-1;
        for(int i=0;i<segments;i++)total+=point(route.getCompound(i)).distanceTo(point(route.getCompound((i+1)%route.size())));if(total<.001)return point(route.getCompound(0));double distance=n.getBoolean("loop")?(seconds*speed)%total:Math.min(seconds*speed,total);
        for(int i=0;i<segments;i++){var a=point(route.getCompound(i));var b=point(route.getCompound((i+1)%route.size()));double length=a.distanceTo(b);if(distance<=length)return a.lerp(b,length<.001?0:distance/length);distance-=length;}return point(route.getCompound(route.size()-1));
    }
    private static Vec3 point(CompoundTag row){return new Vec3(row.getDouble("x"),row.getDouble("y"),row.getDouble("z"));}
    public static Vec3 aircraftPosition(CompoundTag n,int index,double seconds){
        if(n.getString("variant").equals("necropolis")){double heading=Math.toRadians(n.getDouble("heading")),length=n.getDouble("pathLength"),along=Math.sin(seconds*n.getDouble("flightSpeed")/Math.max(1,length))*length*.5;return new Vec3(Math.sin(heading)*along+index*n.getDouble("size")*2.5,n.getDouble("altitude")+Math.sin(seconds*.16+index)*.5,Math.cos(heading)*along);}

        if(n.getString("variant").equals("airship")){
            double length=airshipLength(n),travel=seconds*n.getDouble("flightSpeed")+index*length/Math.max(1,n.getInt("amount"))+airshipPadding(n);
            double along=(n.getBoolean("loop")?((travel%length)+length)%length:Math.min(length,Math.max(0,travel)))-length*.5,angle=Math.toRadians(n.getDouble("heading")),lane=(index-(n.getInt("amount")-1)*.5)*18;
            return new Vec3(Math.sin(angle)*along+Math.cos(angle)*lane,n.getDouble("altitude")+index*4,Math.cos(angle)*along-Math.sin(angle)*lane);
        }
        // Paired pursuit lanes with overtaking, climbing and diving; never converge on a shared centre.
        double radius=n.getDouble("radius"),speed=n.getDouble("flightSpeed"),pair=index/2;
        double turn=pair%2==0?1:-1,phase=pair*2.3999632297;
        double chase=Math.sin(seconds*.13+pair)*.32;
        double a=seconds*speed/radius*turn+phase+(index%2==0?0:.42+chase);
        double lane=pair%2==0?-radius*.28:radius*.28;
        double climb=Math.sin(a*1.7+pair)*5+Math.sin(seconds*.19+index)*1.5;
        return new Vec3(Math.cos(a)*radius+lane,n.getDouble("altitude")+pair*6+climb,Math.sin(a)*radius*.72);
    }

    public static Vec3 aircraftDirection(CompoundTag n,int index,double seconds){if(java.util.Set.of("airship","necropolis").contains(n.getString("variant"))){double a=Math.toRadians(n.getDouble("heading"));return new Vec3(Math.sin(a),0,Math.cos(a));}return aircraftPosition(n,index,seconds+.08).subtract(aircraftPosition(n,index,seconds)).normalize();}
    public static double airshipPadding(CompoundTag n){return Math.max(256,n.getDouble("pathLength")*.5);}
    public static double airshipLength(CompoundTag n){return n.getDouble("pathLength")+2*airshipPadding(n);}
    public static float aircraftOpacity(CompoundTag n,int index,double seconds){if(!n.getString("variant").equals("airship"))return 1;double length=airshipLength(n),travel=seconds*n.getDouble("flightSpeed")+index*length/Math.max(1,n.getInt("amount"))+airshipPadding(n);if(!n.getBoolean("loop")&&travel>=length)return 0;double phase=n.getBoolean("loop")?((travel%length)+length)%length:travel;double edge=Math.min(phase,length-phase)/airshipPadding(n);edge=Math.max(0,Math.min(1,edge));return (float)(edge*edge*(3-2*edge));}
    public static boolean aircraftVisible(CompoundTag n,int index,double seconds){return aircraftOpacity(n,index,seconds)>.001f;}
    public static boolean aircraftFiring(CompoundTag n,int index,double seconds,int interval){if(java.util.Set.of("airship","necropolis").contains(n.getString("variant"))||n.getInt("amount")<2)return false;var at=aircraftPosition(n,index,seconds);var enemy=aircraftPosition(n,(index+1)%n.getInt("amount"),seconds);return aircraftDirection(n,index,seconds).dot(enemy.subtract(at).normalize())>.25;}
    /** Target sampled at launch time; returned coordinates are relative to the shooter's generator. */
    public static Aim aircraftOpponent(MapDeviceSystem.Device d,int index,double seconds){
        var origin=Vec3.atLowerCornerOf(d.getBlockPos()).add(.5,0,.5);
        var at=aircraftPosition(d.scenery,index,seconds).add(origin);
        var forward=aircraftDirection(d.scenery,index,seconds);
        Vec3 best=Vec3.ZERO;double score=-1;
        for(var source:MapDeviceSystem.devices(d.getLevel())){
            if(source.isRemoved()||source.kind()!=MapDeviceSystem.Kind.AIRCRAFT||java.util.Set.of("airship","necropolis").contains(source.scenery.getString("variant"))||!source.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE))continue;
            double clock=seconds+(d.sceneryStart-source.sceneryStart)/20.0;
            var base=Vec3.atLowerCornerOf(source.getBlockPos()).add(.5,0,.5);
            for(int other=0;other<source.scenery.getInt("amount");other++){
                if(source==d&&other==index)continue;
                var target=aircraftPosition(source.scenery,other,clock).add(base);var delta=target.subtract(at);double distance=delta.length();
                double alignment=forward.dot(delta.normalize());
                if(distance<5||distance>100||alignment<.7)continue;
                double priority=alignment-distance*.001;
                if(priority>score){score=priority;best=aircraftPosition(source.scenery,other,clock+distance/90).add(base).subtract(origin);}
            }
        }
        return new Aim(best,score>=0);
    }
    public static boolean aircraftFiring(MapDeviceSystem.Device d,int index,double seconds){
        if(java.util.Set.of("airship","necropolis").contains(d.scenery.getString("variant")))return false;
        double burst=seconds+index*.63+unit(seed(d))*9;
        return burst%3.7<1.4&&aircraftOpponent(d,index,seconds).found;
    }
    public static long shotCycle(double seconds,int interval){return (long)Math.floor(Math.max(0,seconds)*20/Math.max(1,interval));}
    public static double shotPhase(double seconds,int interval){double period=Math.max(1,interval)/20.0;return Math.max(0,seconds)-shotCycle(seconds,interval)*period;}
    public static double recoil(double age,double period){double duration=Math.min(.65,period*.9);if(age>=duration)return 0;double kick=Math.min(.045,duration*.2);return age<kick?.32*Math.sin(age/kick*Math.PI*.5):.32*Math.pow(1-(age-kick)/(duration-kick),2);}

    public static void giveRouteTool(net.minecraft.server.level.ServerPlayer player,MapDeviceSystem.Device device){
        var inv=player.getInventory();int slot=-1;String dimension=player.level().dimension().location().toString();
        for(int i=0;i<36;i++){var existing=inv.getItem(i);if(existing.getItem() instanceof RouteTool&&existing.hasTag()&&existing.getTag().getLong("generator")==device.getBlockPos().asLong()&&dimension.equals(existing.getTag().getString("dimension"))){slot=i;break;}}
        if(slot<0){slot=inv.getFreeSlot();if(slot<0){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.scenery.inventory_full"),true);return;}var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:scenery_route_tool"));var stack=new net.minecraft.world.item.ItemStack(item);stack.getOrCreateTag().putLong("generator",device.getBlockPos().asLong());stack.getOrCreateTag().putString("dimension",dimension);stack.setHoverName(net.minecraft.network.chat.Component.translatable(device.kind()==MapDeviceSystem.Kind.DCA?"gui.zombierool.scenery.targetTool":"gui.zombierool.scenery.routeTool"));inv.setItem(slot,stack);}
        if(PortableEquipment.inHands(player))PortableEquipment.toggle(player);
        if(slot<9)inv.selected=slot;else{var held=inv.getItem(inv.selected);inv.setItem(inv.selected,inv.getItem(slot));inv.setItem(slot,held);}
        inv.setChanged();player.inventoryMenu.broadcastChanges();player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(inv.selected));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(device.kind()==MapDeviceSystem.Kind.DCA?"message.zombierool.scenery.target_tool_hint":"message.zombierool.scenery.route_tool_hint"),true);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void targetClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event){
        var stack=event.getItemStack();if(!(stack.getItem() instanceof RouteTool tool)||!stack.hasTag()||!stack.getTag().contains("generator")||event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if(!event.getLevel().isClientSide)tool.point(event.getEntity(),stack,event.getHitVec().getLocation().add(0,.02,0));
    }
    public static final class RouteTool extends net.minecraft.world.item.Item {
        public RouteTool(){super(new Properties().stacksTo(1));}
        private boolean point(net.minecraft.world.entity.player.Player p,net.minecraft.world.item.ItemStack stack,Vec3 point){if(!(p instanceof net.minecraft.server.level.ServerPlayer player)||!p.isCreative())return false;var n=stack.getOrCreateTag();var pos=BlockPos.of(n.getLong("generator"));if(!n.contains("generator")||!n.getString("dimension").equals(p.level().dimension().location().toString())||!p.level().hasChunkAt(pos)||!(p.level().getBlockEntity(pos) instanceof MapDeviceSystem.Device d)||(d.kind()!=MapDeviceSystem.Kind.TORNADO&&d.kind()!=MapDeviceSystem.Kind.DCA)||!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z)||Math.abs(point.x-pos.getX())>(d.kind()==MapDeviceSystem.Kind.DCA?512:192)||Math.abs(point.y-pos.getY())>(d.kind()==MapDeviceSystem.Kind.DCA?512:192)||Math.abs(point.z-pos.getZ())>(d.kind()==MapDeviceSystem.Kind.DCA?512:192)){p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.scenery.route_invalid"),true);return false;}
            var config=d.config();if(d.kind()==MapDeviceSystem.Kind.DCA){var scene=config.getCompound("scenery");scene.putDouble("targetX",point.x);scene.putDouble("targetY",point.y);scene.putDouble("targetZ",point.z);d.configure(config);p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.scenery.target_set"),true);return true;}var rows=config.getCompound("scenery").getList("route",10);if(p.isShiftKeyDown()){if(!rows.isEmpty())rows.remove(rows.size()-1);}else{if(rows.size()>=32)return false;var row=new CompoundTag();row.putDouble("x",point.x);row.putDouble("y",point.y);row.putDouble("z",point.z);rows.add(row);}config.getCompound("scenery").put("route",rows);d.configure(config);p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.scenery.route_count",rows.size()),true);return true;
        }
        private Vec3 targetFromTool(net.minecraft.world.entity.player.Player p,net.minecraft.world.item.ItemStack stack){var n=stack.getOrCreateTag();var pos=BlockPos.of(n.getLong("generator"));if(p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos) instanceof MapDeviceSystem.Device d&&d.kind()==MapDeviceSystem.Kind.DCA)return p.getEyePosition().add(p.getLookAngle().scale(128));return p.position();}
        @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){if(context.getPlayer()==null)return net.minecraft.world.InteractionResult.PASS;if(!context.getLevel().isClientSide)point(context.getPlayer(),context.getItemInHand(),context.getClickLocation().add(0,.02,0));return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
        @Override public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){var stack=player.getItemInHand(hand);if(!level.isClientSide)point(player,stack,targetFromTool(player,stack));return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide);}
    }
    private AmbientScenery(){}
}
