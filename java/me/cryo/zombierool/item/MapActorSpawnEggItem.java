package me.cryo.zombierool.item;

import me.cryo.zombierool.gameplay.MapActors;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** One registered item, one map-owned profile ID per egg. Never embeds a foreign map model. */
public final class MapActorSpawnEggItem extends Item {
    public MapActorSpawnEggItem(){super(new Item.Properties());}
    public static ItemStack egg(String id,String name){var stack=new ItemStack(me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_EGG_ITEM.get());stack.getOrCreateTag().putString("MapActor",id);stack.getOrCreateTag().putString("ActorName",name);return stack;}
    public static ItemStack egg(String id,String name,String key){var stack=egg(id,name);stack.getOrCreateTag().putString("ActorNameKey",key);return stack;}
    @Override public Component getName(ItemStack stack){return stack.hasTag()&&!stack.getTag().getString("ActorName").isEmpty()?Component.translatable("item.zombierool.map_actor_spawn_egg.named",stack.getTag().getString("ActorNameKey").isEmpty()?Component.literal(stack.getTag().getString("ActorName")):Component.translatable(stack.getTag().getString("ActorNameKey"))):super.getName(stack);}
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<Component> lines,TooltipFlag flag){super.appendHoverText(stack,level,lines,flag);if(!stack.hasTag()||stack.getTag().getString("MapActor").isEmpty())lines.add(Component.translatable("item.zombierool.map_actor_spawn_egg.help"));}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null||!player.isCreative()||!player.hasPermissions(2))return InteractionResult.FAIL;
        if(context.getLevel() instanceof ServerLevel level){
            var stack=context.getItemInHand();String id=stack.getOrCreateTag().getString("MapActor");
            if(id.isEmpty()){var data=new net.minecraft.nbt.CompoundTag();data.putString("action","actor_open_catalog");me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->(net.minecraft.server.level.ServerPlayer)player),new me.cryo.zombierool.scripting.LuaWorkbenchPackets.Response(data));return InteractionResult.SUCCESS;}
            var pos=context.getClickedPos();if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())pos=pos.relative(context.getClickedFace());
            var actor=MapActors.get(level).spawn(level,id,net.minecraft.world.phys.Vec3.atBottomCenterOf(pos),"");
            if(actor.isEmpty())player.displayClientMessage(Component.translatable("gui.zombierool.actors.spawn_blocked"),true);
            return actor.isEmpty()?InteractionResult.FAIL:InteractionResult.SUCCESS;
        }return InteractionResult.SUCCESS;
    }
}
