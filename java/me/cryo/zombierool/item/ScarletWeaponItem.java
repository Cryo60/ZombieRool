package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import com.google.common.collect.*;

/** Ordinary ZombieRool melee weapons: slots, PAP, perks and repairable durability. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class ScarletWeaponItem extends WeaponImplementations.MeleeWeaponItem {
    private static final java.util.UUID REACH=java.util.UUID.fromString("727a994c-6bdc-4a50-aab1-28963f4b9780"),SHRED=java.util.UUID.fromString("3b3f97b0-18f2-4cc6-8f2a-1dfc39cf2430");
    public ScarletWeaponItem(WeaponSystem.Definition def){super(def);}
    @Override public net.minecraft.network.chat.Component getName(ItemStack stack){if(me.cryo.zombierool.gameplay.AshbringerCombat.matches(stack)&&stack.getOrCreateTag().getInt("zr_purification")>=3)return net.minecraft.network.chat.Component.literal("Almost Purified Ashbringer").withStyle(net.minecraft.ChatFormatting.GOLD);return super.getName(stack);}
    @Override public boolean isFoil(ItemStack stack){return me.cryo.zombierool.gameplay.AshbringerCombat.matches(stack)&&stack.getOrCreateTag().getInt("zr_purification")>0||super.isFoil(stack);}
    public String kind(){var current=getDefinition();return current.weapon_kind!=null&&!current.weapon_kind.isEmpty()?current.weapon_kind:current.id.substring(current.id.lastIndexOf('_')+1);}
    public boolean twoHanded(){return kind().equals("halberd")||kind().equals("greatsword");}
    public static boolean kit(ItemStack stack){return stack.getItem() instanceof ScarletWeaponItem weapon&&weapon.kind().equals("shield");}
    @Override protected boolean canDash(ItemStack stack){return false;}
    @Override protected net.minecraft.sounds.SoundEvent getSwingSound(ItemStack stack){var sounds=getDefinition().sounds;String id=isPackAPunched(stack)?sounds.fire_pap:sounds.fire;return id==null||id.isBlank()?null:net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation(id));}
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
        if(kind().equals("shield"))return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
        return super.use(level,player,hand);
    }
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return kind().equals("shield")?UseAnim.BLOCK:UseAnim.NONE;}
    @Override public boolean canPerformAction(ItemStack stack,net.minecraftforge.common.ToolAction action){return kind().equals("shield")&&action==net.minecraftforge.common.ToolActions.SHIELD_BLOCK||super.canPerformAction(stack,action);}
    @Override public void inventoryTick(ItemStack stack,net.minecraft.world.level.Level level,Entity entity,int slot,boolean selected){super.inventoryTick(stack,level,entity,slot,selected);stack.getOrCreateTag().putInt("HideFlags",stack.getOrCreateTag().getInt("HideFlags")|2);}

    /** One durable weapon slot owns both hands. The guard cannot become another weapon or a free drop. */
    public static final class Guard extends Item {
        public Guard(){super(new Item.Properties().stacksTo(1));}
        @Override public String getDescriptionId(){return "item.zombierool.scarlet_guard";}
        @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.BLOCK;}
        @Override public int getUseDuration(ItemStack stack){return 72000;}
        @Override public boolean canPerformAction(ItemStack stack,net.minecraftforge.common.ToolAction action){return action==net.minecraftforge.common.ToolActions.SHIELD_BLOCK;}
        @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){if(hand==net.minecraft.world.InteractionHand.OFF_HAND&&kit(player.getMainHandItem())){player.startUsingItem(hand);return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));}return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));}
    }
    private static ItemStack previous(ItemStack guard){return ItemStack.of(guard.getOrCreateTag().getCompound("PreviousOffhand"));}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hands(net.minecraftforge.event.TickEvent.PlayerTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||e.player.level().isClientSide)return;
        var player=e.player;var inventory=player.getInventory();
        // Recover a displaced offhand even if the player moved the attached guard in a menu.
        for(int i=0;i<inventory.items.size();i++)if(inventory.items.get(i).getItem() instanceof Guard){var old=previous(inventory.items.get(i));inventory.items.set(i,ItemStack.EMPTY);if(!old.isEmpty()&&!inventory.add(old))player.drop(old,false);}
        var off=player.getOffhandItem();boolean wants=player.isAlive()&&kit(player.getMainHandItem());
        if(off.getItem() instanceof Guard&&!wants){var old=previous(off);player.stopUsingItem();player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,old);}
        else if(wants&&!(off.getItem() instanceof Guard)){var guard=new ItemStack(me.cryo.zombierool.init.ZombieroolModItems.SCARLET_GUARD.get());if(!off.isEmpty())guard.getOrCreateTag().put("PreviousOffhand",off.save(new net.minecraft.nbt.CompoundTag()));player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,guard);}
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void toss(net.minecraftforge.event.entity.item.ItemTossEvent e){if(e.getEntity().getItem().getItem() instanceof Guard){var old=previous(e.getEntity().getItem());if(old.isEmpty())e.getEntity().discard();else e.getEntity().setItem(old);}}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void drops(net.minecraftforge.event.entity.living.LivingDropsEvent e){e.getDrops().removeIf(drop->{if(!(drop.getItem().getItem() instanceof Guard))return false;var old=previous(drop.getItem());if(old.isEmpty())return true;drop.setItem(old);return false;});}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e){if(e.getItemStack().getItem() instanceof ScarletWeaponItem){e.getItemStack().getOrCreateTag().putInt("HideFlags",e.getItemStack().getOrCreateTag().getInt("HideFlags")|2);e.getToolTip().removeIf(line->line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text&&(text.getKey().startsWith("attribute.modifier.")||text.getKey().startsWith("item.modifiers.")));}}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void block(net.minecraftforge.event.entity.living.ShieldBlockEvent e){var target=e.getEntity();var stack=target.getUseItem().getItem() instanceof Guard?target.getMainHandItem():target.getUseItem();if(kit(stack)&&!target.level().isClientSide){var weapon=(ScarletWeaponItem)stack.getItem();e.setShieldTakesDamage(false);weapon.initializeIfNeeded(stack);if(!(target instanceof net.minecraft.world.entity.player.Player p)||!p.isCreative()){weapon.setDurability(stack,weapon.getDurability(stack)-Math.max(1,(int)Math.ceil(e.getBlockedDamage())));if(weapon.getDurability(stack)==0){stack.shrink(1);target.stopUsingItem();}}}}
    @Override public Multimap<Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot){
        var base=super.getDefaultAttributeModifiers(slot);if(slot!=EquipmentSlot.MAINHAND)return base;
        var map=ImmutableMultimap.<Attribute,AttributeModifier>builder();for(var entry:base.entries())if(entry.getKey()!=Attributes.ATTACK_SPEED)map.put(entry);
        map.put(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Scarlet weapon speed",20.0/Math.max(6,Math.min(20,Math.round(getDefinition().stats.fire_rate*.85f)))-4,AttributeModifier.Operation.ADDITION));
        if(twoHanded())map.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),new AttributeModifier(REACH,"Two-handed reach",Math.max(0,getDefinition().stats.range-2.5),AttributeModifier.Operation.ADDITION));return map.build();
    }
    public static void strike(net.minecraft.server.level.ServerPlayer player){me.cryo.zombierool.gameplay.MeleeCombat.request(player,false);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){var target=e.getEntity();if(target.level().isClientSide)return;var n=target.getPersistentData();if(n.contains("scarlet_shred_until")&&n.getLong("scarlet_shred_until")<=target.level().getGameTime()){var a=target.getAttribute(Attributes.ARMOR);if(a!=null)a.removeModifier(SHRED);n.remove("scarlet_shred_until");}}
}
