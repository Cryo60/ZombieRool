package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.MapActorEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

/** Map-authored ally delivered as an ordinary buildable item, never a wave spawn. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class MechanicalGreeter {
    public static final String PROFILE="mechanical_greeter_r7yr";
    private static final String OWNER="R7YROwner";
    private static final java.util.Map<ServerLevel,java.util.Set<java.util.UUID>> ACTIVE=new java.util.WeakHashMap<>();
    @SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(e.getLevel() instanceof ServerLevel level&&e.getEntity() instanceof MapActorEntity a&&ally(a))ACTIVE.computeIfAbsent(level,k->new java.util.HashSet<>()).add(a.getUUID());}
    @SubscribeEvent public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent e){if(e.getLevel() instanceof ServerLevel level){var ids=ACTIVE.get(level);if(ids!=null)ids.remove(e.getEntity().getUUID());}}
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel level)ACTIVE.remove(level);}
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    public static final RegistryObject<Item> ITEM=ITEMS.register(PROFILE,GreeterItem::new);
    public static final java.util.List<RegistryObject<Item>> PARTS=java.util.stream.Stream.of("ry7r_chassis","ry7r_power_core","ry7r_control_unit").map(id->ITEMS.register(id,()->new Item(new Item.Properties()))).toList();
    public static final RegistryObject<Item> MAP_COMPONENT=ITEMS.register("map_buildable_component",MapComponentItem::new);
    private MechanicalGreeter(){}
    public static void register(IEventBus bus){ITEMS.register(bus);}
    public static boolean ally(LivingEntity e){return e instanceof MapActorEntity actor&&(actor.profile().getString("id").equals(PROFILE)||actor.profile().getBoolean("deployable")&&actor.profile().getString("faction").equals("survivors"));}
    public static boolean undead(LivingEntity e){return !(e instanceof Player)&&!ally(e)&&(e.getMobType()==MobType.UNDEAD||e instanceof me.cryo.zombierool.entity.ZombieEntity||e instanceof me.cryo.zombierool.entity.CrawlerEntity||e instanceof me.cryo.zombierool.entity.HellhoundEntity||e instanceof me.cryo.zombierool.entity.SpecialEnemyEntity||MapActorBehavior.faction(e).equals("scourge")||MapActorBehavior.faction(e).equals("risen"));}
    public static String deploymentProfile(ItemStack stack){return stack.is(ITEM.get())?PROFILE:stack.is(MAP_COMPONENT.get())&&stack.hasTag()?stack.getTag().getString("zr_deploy_profile"):"";}
    public static boolean deploy(ServerPlayer player,Vec3 at){return deploy(player,at,PROFILE);}
    public static boolean deploy(ServerPlayer player,Vec3 at,String profile){
        var definition=MapActors.get(player.serverLevel()).profile(profile);if(definition.isEmpty()||!definition.getBoolean("deployable")||!definition.getString("faction").equals("survivors"))return false;
        if(!player.isAlive()||player.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())||MatchPause.frozen())return false;
        var level=player.serverLevel();
        for(var e:level.getAllEntities())if(e instanceof MapActorEntity actor&&ally(actor)&&actor.isAlive()&&actor.getPersistentData().hasUUID(OWNER)&&actor.getPersistentData().getUUID(OWNER).equals(player.getUUID())){player.displayClientMessage(net.minecraft.network.chat.Component.literal("Your companion is already deployed."),true);return false;}
        String id=MapActors.get(level).spawn(level,profile,at,"");if(id.isEmpty()){player.displayClientMessage(net.minecraft.network.chat.Component.literal("This companion needs free ground and its map profile."),true);return false;}
        var e=level.getEntity(java.util.UUID.fromString(id));if(!(e instanceof MapActorEntity actor))return false;
        actor.getPersistentData().putUUID(OWNER,player.getUUID());actor.getPersistentData().putLong("R7YRNextVoice",level.getGameTime()+40);actor.setYRot(player.getYRot());actor.setYBodyRot(player.getYRot());return true;
    }
    public static final class GreeterItem extends Item {
        GreeterItem(){super(new Item.Properties().stacksTo(1));}
        @Override public void inventoryTick(ItemStack stack,net.minecraft.world.level.Level level,Entity entity,int slot,boolean selected){if(entity instanceof ServerPlayer p&&!p.isCreative()&&!stack.isEmpty()&&PortableEquipment.acquire(p,stack)){stack.shrink(1);p.containerMenu.broadcastChanges();}}
        @Override public InteractionResult useOn(UseOnContext c){if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;if(!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.FAIL;if(!PortableEquipment.acquire(p,c.getItemInHand()))return InteractionResult.FAIL;c.getItemInHand().shrink(1);if(!PortableEquipment.inHands(p))PortableEquipment.toggle(p);return InteractionResult.CONSUME;}
    }
    public static final class MapComponentItem extends Item {
        MapComponentItem(){super(new Item.Properties());}
        @Override public void inventoryTick(ItemStack stack,net.minecraft.world.level.Level level,Entity entity,int slot,boolean selected){if(entity instanceof ServerPlayer p&&!p.isCreative()&&!deploymentProfile(stack).isEmpty()&&PortableEquipment.acquire(p,stack)){stack.shrink(1);p.containerMenu.broadcastChanges();}}
        @Override public InteractionResult useOn(UseOnContext context){if(deploymentProfile(context.getItemInHand()).isEmpty())return InteractionResult.PASS;if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;if(!(context.getPlayer() instanceof ServerPlayer p)||!PortableEquipment.acquire(p,context.getItemInHand()))return InteractionResult.FAIL;context.getItemInHand().shrink(1);if(!PortableEquipment.inHands(p))PortableEquipment.toggle(p);return InteractionResult.CONSUME;}
    }
    @SubscribeEvent public static void friendlyFire(LivingAttackEvent e){if(ally(e.getEntity())&&e.getSource().getEntity() instanceof Player)e.setCanceled(true);}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel level)||!mob.isAlive()||WaveManager.isPausedByPlayer())return;
        if(ally(mob)){
            if(mob.tickCount%10!=0)return;ACTIVE.computeIfAbsent(level,k->new java.util.HashSet<>()).add(mob.getUUID());var data=mob.getPersistentData();
            if(mob.getDeltaMovement().horizontalDistanceSqr()>.0004)MapActorAudio.play((MapActorEntity)mob,"move");
            if(level.getGameTime()>=data.getLong("R7YRNextVoice")){MapActorAudio.play((MapActorEntity)mob,"idle");data.putLong("R7YRNextVoice",level.getGameTime()+480+mob.getRandom().nextInt(280));}
            if(mob.getTarget()==null&&data.hasUUID(OWNER)){var owner=level.getPlayerByUUID(data.getUUID(OWNER));if(owner!=null&&owner.isAlive()&&mob.distanceToSqr(owner)>36&&mob.distanceToSqr(owner)<4096)mob.getNavigation().moveTo(owner,1);}
        }else if(!ACTIVE.getOrDefault(level,java.util.Collections.emptySet()).isEmpty()&&undead(mob)&&!MapActors.owns(mob)&&Math.floorMod(mob.tickCount+mob.getId(),10)==0){
            var current=mob.getTarget();if(current!=null&&!(current instanceof Player)&&!ally(current))return;
            var ids=ACTIVE.getOrDefault(level,java.util.Collections.emptySet());
            MapActorEntity nearest=null;double distance=current==null?400:mob.distanceToSqr(current);
            for(var id:ids){var entity=level.getEntity(id);if(!(entity instanceof MapActorEntity a)||!a.isAlive()||!ally(a))continue;double d=mob.distanceToSqr(a);if(d<distance&&MapActorBehavior.visible(mob,a)){distance=d;nearest=a;}}
            if(nearest!=null)mob.setTarget(nearest);
        }
    }
}
