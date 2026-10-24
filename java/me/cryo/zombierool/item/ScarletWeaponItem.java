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
    public String kind(){var current=getDefinition();return current.weapon_kind!=null&&!current.weapon_kind.isEmpty()?current.weapon_kind:current.id.substring(current.id.lastIndexOf('_')+1);}
    public boolean twoHanded(){return kind().equals("halberd")||kind().equals("greatsword");}
    public static boolean kit(ItemStack stack){return stack.getItem() instanceof ScarletWeaponItem weapon&&weapon.kind().equals("shield");}
    @Override protected boolean canDash(ItemStack stack){return false;}
    @Override protected net.minecraft.sounds.SoundEvent getSwingSound(ItemStack stack){var sounds=getDefinition().sounds;String id=isPackAPunched(stack)?sounds.fire_pap:sounds.fire;return id==null||id.isBlank()?null:net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation(id));}
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){if(hand==net.minecraft.world.InteractionHand.OFF_HAND&&kind().equals("shield")){player.startUsingItem(hand);return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));}if(hand==net.minecraft.world.InteractionHand.MAIN_HAND&&!kind().equals("shield")&&java.util.Set.of("sword","greatsword","halberd","axe","dagger","knife").contains(kind())&&player.getOffhandItem().isEmpty()){player.startUsingItem(hand);return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));}return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));}
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public void releaseUsing(ItemStack stack,net.minecraft.world.level.Level level,LivingEntity entity,int remaining){if(level.isClientSide||!(entity instanceof net.minecraft.server.level.ServerPlayer player)||getUseDuration(stack)-remaining<16)return;stack.getOrCreateTag().putBoolean("zr_heavy_strike",true);player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);strike(player);stack.getOrCreateTag().remove("zr_heavy_strike");}
    @Override public void onUseTick(net.minecraft.world.level.Level level,LivingEntity entity,ItemStack stack,int remaining){}
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
        map.put(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Scarlet weapon speed",20.0/getDefinition().stats.fire_rate-4,AttributeModifier.Operation.ADDITION));
        if(twoHanded())map.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),new AttributeModifier(REACH,"Two-handed reach",Math.max(0,getDefinition().stats.range-2.5),AttributeModifier.Operation.ADDITION));return map.build();
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent e){
        if(e.getEntity().getMainHandItem().getItem() instanceof ScarletWeaponItem weapon){e.setCanceled(true);if(!e.getEntity().level().isClientSide&&e.getTarget() instanceof LivingEntity target)weapon.hurtEnemy(e.getEntity().getMainHandItem(),target,e.getEntity());}
    }
    public static void strike(net.minecraft.server.level.ServerPlayer player){
        if(!(player.getMainHandItem().getItem() instanceof ScarletWeaponItem weapon)||player.isSpectator()||!player.isAlive()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())||me.cryo.zombierool.gameplay.TeleporterPlayerLock.active(player)||me.cryo.zombierool.gameplay.LunarEquipment.busy(player)||me.cryo.zombierool.gameplay.PortableEquipment.inHands(player))return;
        var from=player.getEyePosition();var to=from.add(player.getLookAngle().scale(weapon.getDefinition().stats.range+.5));
        LivingEntity closest=null;double distance=Double.MAX_VALUE;
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().expandTowards(to.subtract(from)).inflate(.5),e->e!=player&&!(e instanceof net.minecraft.world.entity.player.Player)&&e.isAlive())){
            var box=target.getBoundingBox().inflate(.15);var point=box.contains(from)?java.util.Optional.of(from):box.clip(from,to);
            if(point.isPresent()&&from.distanceToSqr(point.get())<distance&&me.cryo.zombierool.gameplay.MeleeTrace.clear(player.level(),from,point.get())){closest=target;distance=from.distanceToSqr(point.get());}
        }
        if(closest!=null)weapon.hurtEnemy(player.getMainHandItem(),closest,player);
    }
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity wielder){
        if(!(wielder instanceof net.minecraft.world.entity.player.Player player)||target.level().isClientSide)return false;
        if(!me.cryo.zombierool.gameplay.MapWeaponPacks.available(getDefinition().id.replace("zombierool:","")))return false;
        initializeIfNeeded(stack);long now=player.level().getGameTime();var tag=stack.getOrCreateTag();int rate=getFireRate(stack,player);
        var eye=player.getEyePosition();var box=target.getBoundingBox();double dx=Math.max(box.minX-eye.x,Math.max(0,eye.x-box.maxX)),dy=Math.max(box.minY-eye.y,Math.max(0,eye.y-box.maxY)),dz=Math.max(box.minZ-eye.z,Math.max(0,eye.z-box.maxZ));
        if(tag.contains(TAG_LAST_FIRE)&&now-tag.getLong(TAG_LAST_FIRE)<rate||!target.isAlive()||!me.cryo.zombierool.gameplay.MeleeTrace.visible(player,target)||dx*dx+dy*dy+dz*dz>Math.pow(getDefinition().stats.range+.5,2))return false;
        if(hasDurability()&&getDurability(stack)<=0)return false;
        boolean heavy=tag.getBoolean("zr_heavy_strike");int combo=tag.contains(TAG_LAST_FIRE)&&now-tag.getLong(TAG_LAST_FIRE)<rate+18?(tag.getInt("zr_combo")+1)%3:0;tag.putInt("zr_combo",combo);tag.putLong(TAG_LAST_FIRE,now);float damage=getWeaponDamage(stack);if(player.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAP.get()))damage*=2;
        if(kind().equals("dagger")&&target.getLookAngle().dot(player.position().subtract(target.position()).normalize())<-.35)damage*=1.75f;
        if(heavy)damage*=1.8f;else if(combo==2)damage*=1.2f;
        boolean hit=me.cryo.zombierool.core.manager.DamageManager.applyDamage(target,player.damageSources().playerAttack(player),damage);
        if(!hit)return false;
        if(player.level() instanceof net.minecraft.server.level.ServerLevel server){server.sendParticles(heavy?net.minecraft.core.particles.ParticleTypes.CRIT:net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,target.getX(),target.getY()+target.getBbHeight()*.55,target.getZ(),heavy?9:1,.15,.15,.15,.03);server.playSound(null,target.blockPosition(),heavy?net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT:net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,net.minecraft.sounds.SoundSource.PLAYERS,.65f,heavy?.75f:1.1f);}
        if(kind().equals("axe")&&target.isAlive()){
            var armor=target.getAttribute(Attributes.ARMOR);if(armor!=null){armor.removeModifier(SHRED);armor.addTransientModifier(new AttributeModifier(SHRED,"Scarlet axe armor break",-4,AttributeModifier.Operation.ADDITION));target.getPersistentData().putLong("scarlet_shred_until",now+100);}
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,30,0));
        }
        if(kind().equals("sword")||kind().equals("shield")||twoHanded()){float cleave=damage*(twoHanded()?.55f:.4f);for(var other:player.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(1.25),e->e!=target&&e!=player&&e.isAlive()&&e instanceof net.minecraft.world.entity.monster.Enemy&&me.cryo.zombierool.gameplay.MeleeTrace.visible(player,e)))me.cryo.zombierool.core.manager.DamageManager.applyDamage(other,player.damageSources().playerAttack(player),cleave);}
        var sound=getSwingSound(stack);if(sound!=null)player.level().playSound(null,player.blockPosition(),sound,net.minecraft.sounds.SoundSource.PLAYERS,1,kind().equals("axe")?.75f:1);
        if(!player.isCreative()){setDurability(stack,Math.max(0,getDurability(stack)-(heavy?3:1)));if(getDurability(stack)==0){player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.ITEM_BREAK,net.minecraft.sounds.SoundSource.PLAYERS,1,1);stack.shrink(1);}}
        player.getCooldowns().addCooldown(this,rate);return true;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){var target=e.getEntity();if(target.level().isClientSide)return;var n=target.getPersistentData();if(n.contains("scarlet_shred_until")&&n.getLong("scarlet_shred_until")<=target.level().getGameTime()){var a=target.getAttribute(Attributes.ARMOR);if(a!=null)a.removeModifier(SHRED);n.remove("scarlet_shred_until");}}
}
