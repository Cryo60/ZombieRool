package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.player.PlayerDownManager;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Map definitions persist; all carried parts, bench locks and contributions belong to a match. */
@Mod.EventBusSubscriber(modid = "zombierool")
public final class BuildableSystem extends SavedData {
    private CompoundTag definitions = new CompoundTag();
    private final Map<BlockPos, Part> available = new HashMap<>();
    private final Map<UUID, Part> carried = new HashMap<>();
    private final Map<BlockPos, Bench> benches = new HashMap<>();
    private final Map<UUID, Assembly> assembling = new HashMap<>();
    private final Set<BlockPos> hidden = new HashSet<>();
    private final Set<UUID> displays = new HashSet<>();
    private final Map<BlockPos, UUID> partDisplays = new HashMap<>();
    private final Map<BlockPos, Set<UUID>> benchDisplays = new HashMap<>();
    private final Map<BlockPos, UUID> resultDisplays = new HashMap<>();
    private final Map<BlockPos, CompoundTag> outputs = new HashMap<>();
    private final Map<UUID,Installation> installing=new HashMap<>();
    private record Installation(BlockPos pos,Delivery delivery,long finish) {}
    private static final int INSTALL_TICKS=40;
    private final Map<UUID,Delivery> deliveries=new HashMap<>();
    private record Delivery(String recipe,BlockPos bench) {}
    private record Hold(BlockPos pos,long tick) {}
    private final Map<UUID,Hold> holds=new HashMap<>();
    private boolean active;
    private final Set<BlockPos> scriptHidden=new HashSet<>();
    private final Set<String> hiddenRecipes=new HashSet<>();
    public boolean recipeVisible(ServerLevel level,String recipe,boolean visible){if(!recipes().contains(recipe))return false;if(visible)hiddenRecipes.remove(recipe);else hiddenRecipes.add(recipe);sync(level);return true;}
    public boolean componentVisible(ServerLevel level,BlockPos pos,boolean visible){
        if(!scriptHidden.contains(pos)&&(!(level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)||level.getBlockState(pos).getBlock() instanceof me.cryo.zombierool.block.system.BuildableBlocks.Workbench))return false;
        if(!QuestBlockSnapshots.get(level).hidden(level,"lua_component_"+pos.asLong(),pos,!visible))return false;
        if(!visible){scriptHidden.add(pos);removeDisplay(level,partDisplays.remove(pos));}
        else{scriptHidden.remove(pos);var part=available.get(pos);if(part!=null)showSource(level,part);}
        sync(level);return true;
    }
    private ServerLevel ownerLevel;

    public record Part(String recipe, String id, BlockPos source, CompoundTag snapshot) {}
    private record Assembly(BlockPos bench, Part part, long finish) {}
    private static final class Bench {
        String recipe;
        final Set<String> installed = new LinkedHashSet<>();
        int claims;
        Bench(String recipe) { this.recipe = recipe; }
    }
    public static BuildableSystem get(ServerLevel level) {
        var data=level.getDataStorage().computeIfAbsent(BuildableSystem::load, BuildableSystem::new, "zombierool_buildables");data.ownerLevel=level;return data;
    }
    private static BuildableSystem load(CompoundTag n) {
        var data = new BuildableSystem(); data.definitions = n.getCompound("definitions");
        data.removeLegacyAutomaticRecipes();
        for (var tag : n.getList("hidden", Tag.TAG_LONG)) data.hidden.add(BlockPos.of(((LongTag)tag).getAsLong()));
        for (var tag : n.getList("outputs", Tag.TAG_COMPOUND)) { var output = (CompoundTag)tag; data.outputs.put(BlockPos.of(output.getLong("pos")), output); }
        return data;
    }
    /** Migrate only untouched injected entries; authored recipes and physical bindings survive. */
    private void removeLegacyAutomaticRecipes(){
        if(!definitions.getBoolean("tranzitDefaults"))return;
        var expected=Map.of("zombie_shield",List.of("car_door","hand_truck"),"turbine",List.of("mannequin","fan","tail"));
        for(var entry:expected.entrySet()){
            String id=entry.getKey();var recipe=recipes().getCompound(id);
            boolean bound=definitions.getList("anchors",10).stream().anyMatch(t->((CompoundTag)t).getString("recipe").equals(id))
                ||definitions.getList("destinations",10).stream().anyMatch(t->((CompoundTag)t).getString("recipe").equals(id));
            if(!bound&&!recipe.getBoolean("autoParts")&&recipe.getString("mode").equals("item")&&recipe.getInt("limit")==0
                &&recipe.getInt("ticks")==40&&parts(recipe).equals(entry.getValue())
                &&recipe.getCompound("reward").getString("id").equals("zombierool:"+id)
                &&recipe.getString("name").equals(id.equals("turbine")?"Turbine":"Zombie Shield"))recipes().remove(id);
        }
        definitions.remove("tranzitDefaults");setDirty();
    }
    @Override public CompoundTag save(CompoundTag n) {
        n.put("definitions", definitions.copy()); var list = new ListTag();
        hidden.forEach(p -> list.add(LongTag.valueOf(p.asLong()))); n.put("hidden", list);
        var resultBlocks = new ListTag(); outputs.values().forEach(t -> resultBlocks.add(t.copy())); n.put("outputs", resultBlocks); return n;
    }
    public boolean active() { return active; }
    public CompoundTag definitions() { var out=definitions.copy();out.put("presets",me.cryo.zombierool.api.BuildablePresets.catalog(ownerLevel));return out; }
    /** Power remains a map requirement while its buildable switch has not been installed yet. */
    public boolean hasPlannedPowerSwitch(){
        for(String id:recipes().getAllKeys()){
            var recipe=recipes().getCompound(id);
            if(!(ItemStack.of(recipe.getCompound("reward")).getItem() instanceof BlockItem item)||!(item.getBlock() instanceof me.cryo.zombierool.block.PowerSwitchBlock))continue;
            String mode=recipe.getString("mode");
            if(mode.equals("deploy")&&definitions.getList("destinations",10).stream().anyMatch(t->((CompoundTag)t).getString("recipe").equals(id)))return true;
            if(mode.equals("block")&&definitions.getList("anchors",10).stream().anyMatch(t->((CompoundTag)t).getString("recipe").equals(id)&&!((CompoundTag)t).getString("part").isEmpty()))return true;
        }
        return false;
    }
    private CompoundTag recipes() { return definitions.getCompound("recipes"); }
    private static boolean validId(String id) { return id.matches("[a-z0-9_./:-]{1,64}"); }
    private static List<String> parts(CompoundTag recipe) {
        return recipe.getList("parts", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    public void define(String id, String name, String partIds, ItemStack reward, String mode, int ticks, int limit) {
        require(!active, "editing_match");
        require(validId(id), "invalid_id");
        var ids = partIds.isBlank() ? List.<String>of() : Arrays.stream(partIds.split(",")).map(String::trim).toList();
        require(ids.size() <= 32 && ids.stream().allMatch(BuildableSystem::validId)
                && new HashSet<>(ids).size() == ids.size(), "invalid_parts");
        require(Set.of("item", "block", "deploy", "event").contains(mode), "invalid_mode");
        require(mode.equals("event") || !reward.isEmpty(), "empty_reward");
        require(!(mode.equals("block")||mode.equals("deploy")) || reward.getItem() instanceof BlockItem, "block_reward");
        var n = new CompoundTag(); n.putString("name", name.isBlank() ? id : name.substring(0, Math.min(80, name.length())));
        var list = new ListTag(); ids.forEach(p -> list.add(StringTag.valueOf(p))); n.put("parts", list);
        n.put("reward", reward.save(new CompoundTag())); n.putString("mode", mode);
        n.putInt("ticks", Math.max(40, Math.min(1200, ticks))); n.putInt("limit", Math.max(0, Math.min(10000, limit)));
        var recipes = recipes(); recipes.put(id, n); definitions.put("recipes", recipes); setDirty();
    }
    public void editRecipe(String id, String name, ItemStack reward, String mode, int limit) {
        List<String> ids = new ArrayList<>();
        var preset=recipes().getCompound(id).getList("presetPieces",10).copy();
        for(var tag:preset)ids.add(((CompoundTag)tag).getString("part"));
        for (var tag : definitions.getList("anchors", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag)tag;
            if (n.getString("recipe").equals(id) && !n.getString("part").isEmpty() && !ids.contains(n.getString("part"))) ids.add(n.getString("part"));
        }
        define(id, name, String.join(",", ids), reward, mode, 40, limit);
        if(!preset.isEmpty())recipes().getCompound(id).put("presetPieces",preset);
        recipes().getCompound(id).putBoolean("autoParts", true); setDirty();
    }
    public void configurePart(ServerLevel level, BlockPos pos, String recipe, String id, ItemStack visual) {
        require(!active, "editing_match"); require(!visual.isEmpty(), "empty_reward"); require(!id.isBlank() && validId(id), "invalid_parts");
        var n = recipes().getCompound(recipe); require(recipes().contains(recipe), "unknown_recipe");
        if (!parts(n).contains(id)) { var list = n.getList("parts", Tag.TAG_STRING); list.add(StringTag.valueOf(id)); n.put("parts", list); }
        if (level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport support) support.configure(recipe, id, visual);
        itemPart(level, pos, recipe, id, visual); reconcileParts(); setDirty();
    }
    private void reconcileParts() {
        for (String recipe : recipes().getAllKeys()) {
            var n = recipes().getCompound(recipe); if (!n.getBoolean("autoParts")) continue;
            var list = new ListTag(); var ids = new LinkedHashSet<String>();
            for(var tag:n.getList("presetPieces",10))ids.add(((CompoundTag)tag).getString("part"));
            for (var tag : definitions.getList("anchors", Tag.TAG_COMPOUND)) {
                var anchor = (CompoundTag)tag;
                if (anchor.getString("recipe").equals(recipe) && !anchor.getString("part").isEmpty()) ids.add(anchor.getString("part"));
            }
            ids.forEach(id -> list.add(StringTag.valueOf(id))); n.put("parts", list);
        }
    }
    public void remove(String id) {
        require(!active, "editing_match");
        var positions=new HashSet<BlockPos>();var originals=new HashMap<BlockPos,CompoundTag>();
        for(var tag:definitions.getList("anchors",10)){var n=(CompoundTag)tag;if(n.getString("recipe").equals(id)&&!n.getString("part").isEmpty()){var p=BlockPos.of(n.getLong("pos"));positions.add(p);originals.put(p,n.getCompound("state").copy());}}
        for(var tag:definitions.getList("destinations",10)){var n=(CompoundTag)tag;if(n.getString("recipe").equals(id))positions.add(BlockPos.of(n.getLong("pos")));}
        var recipes = recipes(); recipes.remove(id); definitions.put("recipes", recipes);
        var entries = definitions.getList("anchors", Tag.TAG_COMPOUND);
        definitions.getList("destinations",10).removeIf(t->((CompoundTag)t).getString("recipe").equals(id));
        entries.removeIf(t -> ((CompoundTag)t).getString("recipe").equals(id));
        setDirty();
        if(ownerLevel!=null)for(var pos:positions){
            var be=ownerLevel.getBlockEntity(pos);
            if(be instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport support&&support.recipe.equals(id)&&!support.getBlockState().is(me.cryo.zombierool.block.system.BuildableBlocks.DECO.get()))ownerLevel.removeBlock(pos,false);
            else if(originals.containsKey(pos)){var original=NbtUtils.readBlockState(ownerLevel.holderLookup(net.minecraft.core.registries.Registries.BLOCK),originals.get(pos));if(ownerLevel.getBlockState(pos).is(original.getBlock())&&!(original.getBlock() instanceof me.cryo.zombierool.block.system.BuildableBlocks.Workbench)){ownerLevel.removeBlockEntity(pos);ownerLevel.removeBlock(pos,false);}}
        }
    }
    public String createPreset(String kind){
        var preset=me.cryo.zombierool.api.BuildablePresets.get(ownerLevel,kind);String base=kind.replace(':','_');if(base.length()>48)base=base.substring(0,40)+Integer.toHexString(kind.hashCode());String id=base;int suffix=2;while(recipes().contains(id))id=base+"_"+suffix++;
        var pieces=preset.getList("presetPieces",10).copy();var ids=new ArrayList<String>();for(var tag:pieces)ids.add(((CompoundTag)tag).getString("part"));
        define(id,preset.getString("name"),String.join(",",ids),ItemStack.of(preset.getCompound("reward")),preset.getString("mode"),preset.getInt("ticks"),preset.getInt("limit"));
        recipes().getCompound(id).put("presetPieces",pieces);recipes().getCompound(id).putBoolean("autoParts",true);setDirty();return id;
    }
    /** Attach to any decoration, including modded blocks; full block entity data is retained. */
    public void anchor(ServerLevel level, BlockPos pos, String recipe, String part) {
        require(!active, "editing_match");
        if (part.isEmpty()) recipe = ""; // Every bench is free; legacy dedicated bindings migrate here.
        require(!level.getBlockState(pos).isAir(), "target_block");
        require(recipe.isEmpty() && part.isEmpty() || recipes().contains(recipe, Tag.TAG_COMPOUND), "unknown_recipe");
        require(part.isEmpty() || parts(recipes().getCompound(recipe)).contains(part), "unknown_part");
        var n = new CompoundTag(); n.putLong("pos", pos.asLong()); n.putString("recipe", recipe); n.putString("part", part);
        n.put("state", NbtUtils.writeBlockState(level.getBlockState(pos)));
        if (level.getBlockEntity(pos) != null) n.put("be", level.getBlockEntity(pos).saveWithoutMetadata());
        var entries = definitions.getList("anchors", Tag.TAG_COMPOUND);
        entries.removeIf(t -> ((CompoundTag)t).getLong("pos") == pos.asLong()); entries.add(n);
        definitions.put("anchors", entries); setDirty();
    }
    public void itemPart(ServerLevel level, BlockPos pos, String recipe, String part, ItemStack visual) {
        require(!visual.isEmpty(), "empty_reward");
        anchor(level, pos, recipe, part);
        for (var tag : definitions.getList("anchors", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag)tag;
            if (n.getLong("pos") == pos.asLong()) n.put("visual", visual.copyWithCount(1).save(new CompoundTag()));
        }
        setDirty();
    }
    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState applyProperty(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value){var parsed=property.getValue(value);return parsed.isPresent()?state.setValue(property,parsed.get()):state;}
    public void destination(ServerLevel level,BlockPos pos,String recipe){
        require(!active,"editing_match");require(recipes().contains(recipe),"unknown_recipe");
        var entries=definitions.getList("destinations",10);entries.removeIf(t->((CompoundTag)t).getLong("pos")==pos.asLong());
        var n=new CompoundTag();n.putLong("pos",pos.asLong());n.putString("recipe",recipe);entries.add(n);definitions.put("destinations",entries);setDirty();
    }
    public String delivery(UUID player){var d=deliveries.get(player);return d==null?"":d.recipe;}
    /** Consume a scripted delivery without refunding the bench's single claim. */
    public boolean consumeDelivery(ServerPlayer player,String recipe){var id=player.getUUID();var d=deliveries.get(id);if(d==null||!d.recipe.equals(recipe))return false;cancelAction(player);installing.remove(id);deliveries.remove(id);sync(player.serverLevel());return true;}
    private void releaseDelivery(UUID player){installing.remove(player);var d=deliveries.remove(player);if(d!=null){var bench=benches.get(d.bench);if(bench!=null)bench.claims=Math.max(0,bench.claims-1);}}
    public static void holdAction(ServerPlayer player,BlockPos pos,boolean down){
        var data=get(player.serverLevel());var id=player.getUUID();
        if(!down){data.cancelAction(player);return;}
        pos=me.cryo.zombierool.block.system.BuildableBlocks.root(player.level(),pos);
        if(!data.active||!WaveManager.isGameRunning()||!validPlayer(player)||!reachable(player,pos)){data.cancelAction(player);return;}
        var previous=data.holds.get(id);boolean first=previous==null||!previous.pos.equals(pos);
        if(first)data.cancelAction(player);
        data.holds.put(id,new Hold(pos,player.level().getGameTime()));
        var bench=data.benches.get(pos);
        boolean pending=bench!=null&&!data.complete(bench)&&data.carried.containsKey(id)||player.level().getBlockState(pos).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get())&&data.deliveries.containsKey(id);
        if(first&&pending)interact(player,pos);
    }
    private boolean held(ServerPlayer p,BlockPos pos){var hold=holds.get(p.getUUID());return hold!=null&&hold.pos.equals(pos)&&p.level().getGameTime()-hold.tick<=10;}
    private void cancelAction(ServerPlayer player){
        holds.remove(player.getUUID());boolean was=assembling.remove(player.getUUID())!=null;was|=installing.remove(player.getUUID())!=null;
        if(was){EquipmentSystem.animate(player,"knuckle_cancel",1);sync(player.serverLevel());}
    }
    /** Exact ray intersection shared by client selection and server pickup validation. */
    public static boolean aimedPickup(net.minecraft.world.entity.player.Player player,BlockPos pos,AABB bounds){
        Vec3 eye=player.getEyePosition(),end=eye.add(player.getViewVector(1).scale(2.25));var point=bounds.clip(eye,end);
        if(point.isEmpty()&&!bounds.contains(eye))return false;
        var obstruction=player.level().clip(new ClipContext(eye,point.orElse(eye),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return obstruction.getType()==HitResult.Type.MISS||obstruction.getBlockPos().equals(pos);
    }
    private AABB pickupBounds(ServerLevel level,BlockPos pos,Part part){
        var state=NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),part.snapshot.getCompound("state"));
        if(state.getBlock() instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartBlock){
            var stack=partIcon(level,part);var face=state.getValue(me.cryo.zombierool.block.system.BuildableBlocks.PartBlock.FACE);
            return me.cryo.zombierool.api.BuildableVisuals.bounds(stack,face).move(pos);
        }
        var shape=state.getShape(level,pos);return (shape.isEmpty()?new AABB(0,0,0,1,1,1):shape.bounds()).move(pos);
    }
    private void beginInstallation(ServerPlayer p,BlockPos pos){
        if(!held(p,pos))return;
        var d=deliveries.get(p.getUUID());
        if(d==null||!(p.serverLevel().getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport support)||!support.recipe.equals(d.recipe))return;
        if(installing.values().stream().anyMatch(job->job.pos.equals(pos))){message(p,"busy");return;}
        installing.put(p.getUUID(),new Installation(pos,d,p.serverLevel().getGameTime()+INSTALL_TICKS));
        p.stopUsingItem();EquipmentSystem.cancel(p);me.cryo.zombierool.handlers.LethalWeaponManager.cancel(p);EquipmentSystem.animate(p,"knuckle_crack",INSTALL_TICKS);
        sound(p.serverLevel(),pos,"assemble",SoundEvents.ANVIL_USE);sync(p.serverLevel());
    }
    /** Shared by the authoring ghost and the actual installation; never mutates the world. */
    public static net.minecraft.world.level.block.state.BlockState placementState(net.minecraft.world.level.Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,ItemStack reward){
        if(!(reward.getItem() instanceof BlockItem item))return null;
        var marker=level.getBlockState(pos);var property=me.cryo.zombierool.block.system.BuildableBlocks.PartBlock.FACE;
        var face=marker.hasProperty(property)?marker.getValue(property):net.minecraft.core.Direction.UP;
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false);var context=new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,reward,hit);
        var state=item.getBlock().getStateForPlacement(context);
        if(state!=null&&state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE)){
            state=state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,face==net.minecraft.core.Direction.UP?net.minecraft.world.level.block.state.properties.AttachFace.FLOOR:face==net.minecraft.core.Direction.DOWN?net.minecraft.world.level.block.state.properties.AttachFace.CEILING:net.minecraft.world.level.block.state.properties.AttachFace.WALL);
            if(face.getAxis().isHorizontal()&&state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))state=state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,face);
        }
        if(state!=null&&reward.getTag()!=null){var properties=reward.getTag().getCompound("BlockStateTag");for(String key:properties.getAllKeys()){var p=state.getBlock().getStateDefinition().getProperty(key);if(p!=null)state=applyProperty(state,p,properties.getString(key));}}
        return state;
    }
    private void placeDelivery(ServerPlayer p,BlockPos pos){
        if(!(p.serverLevel().getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport support)||!support.getBlockState().is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()))return;
        var d=deliveries.get(p.getUUID());if(d==null||!d.recipe.equals(support.recipe))return;
        var reward=ItemStack.of(recipes().getCompound(d.recipe).getCompound("reward"));if(!(reward.getItem() instanceof BlockItem item))return;
        var level=p.serverLevel();var state=placementState(level,pos,p,reward);
        if(state==null||!state.canSurvive(level,pos)||!level.isUnobstructed(state,pos,net.minecraft.world.phys.shapes.CollisionContext.of(p))){message(p,"blocked");return;}
        // Snapshot before any block callbacks; restoring the map also restores its configured placement marker.
        var original=new CompoundTag();original.putLong("pos",pos.asLong());original.put("state",NbtUtils.writeBlockState(level.getBlockState(pos)));original.put("be",support.saveWithoutMetadata());
        MatchWorldJournal.note(level,pos);outputs.put(pos,original);setDirty();level.removeBlockEntity(pos);
        if(!level.setBlock(pos,state,3)){outputs.remove(pos);restore(level,new Part("","",pos,original));return;}
        if(level.getBlockEntity(pos)!=null&&reward.getTag()!=null&&reward.getTag().contains("BlockEntityTag")){var be=level.getBlockEntity(pos);be.load(reward.getTag().getCompound("BlockEntityTag"));be.setChanged();level.sendBlockUpdated(pos,state,state,3);}
        item.getBlock().setPlacedBy(level,pos,state,p,reward.copy());deliveries.remove(p.getUUID());sync(level);sound(level,pos,"installed",SoundEvents.ANVIL_LAND);
        LuaScriptManager.callEvent("OnBuildablePlaced",p.getUUID().toString(),d.recipe,pos.getX(),pos.getY(),pos.getZ());
    }
    public void unbind(BlockPos pos) {
        require(!active, "editing_match");
        definitions.getList("destinations",10).removeIf(t->((CompoundTag)t).getLong("pos")==pos.asLong());
        definitions.getList("anchors", Tag.TAG_COMPOUND).removeIf(t -> ((CompoundTag)t).getLong("pos") == pos.asLong()); reconcileParts(); setDirty();
    }
    private static void message(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.zombierool.buildable." + key, args), true);
    }
    private void hide(ServerLevel level, Part part) {
        Part occupant=available.get(part.source);
        UUID display = occupant!=null&&!occupant.equals(part)?null:partDisplays.remove(part.source);
        if (display != null) { var entity = level.getEntity(display); if (entity != null) entity.discard(); displays.remove(display); }
        MatchWorldJournal.note(level, part.source); hidden.add(part.source);
        // Removing a container block must not spill its saved inventory into the match.
        level.removeBlockEntity(part.source);
        level.setBlock(part.source, Blocks.AIR.defaultBlockState(), 3);
        setDirty();
    }
    private void restore(ServerLevel level, Part part) {
        var state = NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), part.snapshot.getCompound("state"));
        level.setBlock(part.source, state, 3);
        if (part.snapshot.contains("be", Tag.TAG_COMPOUND) && level.getBlockEntity(part.source) != null) {
            var be = level.getBlockEntity(part.source); be.load(part.snapshot.getCompound("be")); be.setChanged();
            level.sendBlockUpdated(part.source, state, state, 3);
        }
        hidden.remove(part.source);
        setDirty();
    }
    public void start(ServerLevel level) {
        reset(level); active = true;
        Map<String, List<Part>> spawns = new HashMap<>();
        for (Tag tag : definitions.getList("anchors", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag)tag; var pos = BlockPos.of(n.getLong("pos"));
            String recipe = n.getString("recipe"), id = n.getString("part");
            if (level.getBlockState(pos).isAir()) continue;
            n.put("state", NbtUtils.writeBlockState(level.getBlockState(pos))); n.remove("be");
            if (level.getBlockEntity(pos) != null) n.put("be", level.getBlockEntity(pos).saveWithoutMetadata());
            if (id.isEmpty()) { benches.put(pos, new Bench("")); continue; }
            if (!recipes().contains(recipe) || !parts(recipes().getCompound(recipe)).contains(id)) continue;
            var part = new Part(recipe, id, pos, n.copy());
            spawns.computeIfAbsent(recipe + "|" + id, k -> new ArrayList<>()).add(part);
        }
        for (var choices : spawns.values()) {
            Part chosen = choices.get(level.random.nextInt(choices.size())); available.put(chosen.source, chosen);
            for (var other : choices) if (other != chosen) hide(level, other);
            showSource(level, chosen);
        }
        sync(level);
        setDirty();
    }
    public void reset(ServerLevel level) {
        PortableEquipment.reset(level);
        var interrupted=new HashSet<>(assembling.keySet());interrupted.addAll(installing.keySet());
        for (UUID id : interrupted) {
            var player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) EquipmentSystem.animate(player, "knuckle_cancel", 1);
        }
        var hiddenParts = new HashSet<>(hidden);
        for (var output : new HashMap<>(outputs).entrySet()) {
            level.removeBlockEntity(output.getKey());
            restore(level, new Part("", "", output.getKey(), output.getValue()));
        }
        hidden.addAll(hiddenParts);
        for (Tag tag : definitions.getList("anchors", Tag.TAG_COMPOUND)) {
            var n = (CompoundTag)tag; var pos = BlockPos.of(n.getLong("pos"));
            if (hidden.contains(pos)) restore(level, new Part(n.getString("recipe"), n.getString("part"), pos, n));
        }
        for (UUID id : displays) { var entity = level.getEntity(id); if (entity != null) entity.discard(); }
        outputs.clear(); setDirty();
        scriptHidden.clear(); hiddenRecipes.clear(); holds.clear(); installing.clear(); deliveries.clear(); displays.clear(); partDisplays.clear(); benchDisplays.clear(); resultDisplays.clear(); available.clear(); carried.clear(); benches.clear(); assembling.clear(); hidden.clear(); active = false;
        sync(level);
    }
    public static boolean busy(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer p) return get(p.serverLevel()).assembling.containsKey(p.getUUID())||get(p.serverLevel()).installing.containsKey(p.getUUID());
        return entity != null && entity.level().isClientSide && entity.getPersistentData().getBoolean("zr_buildable_busy");
    }
    private static boolean validPlayer(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !PlayerDownManager.isPlayerDown(p.getUUID())
                && !TeleporterPlayerLock.active(p) && !LunarEquipment.busy(p);
    }
    private static boolean reachable(ServerPlayer p, BlockPos pos) {
        Vec3 eye = p.getEyePosition(), target = me.cryo.zombierool.block.system.BuildableBlocks.center(p.level(),pos);
        if (eye.distanceToSqr(target) > 9) return false;
        var hit = p.level().clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || me.cryo.zombierool.block.system.BuildableBlocks.root(p.level(),hit.getBlockPos()).equals(pos);
    }
    public static void interact(ServerPlayer p, BlockPos pos) {
        var level = p.serverLevel(); pos=me.cryo.zombierool.block.system.BuildableBlocks.root(level,pos); var data = get(level);
        if(data.scriptHidden.contains(pos))return;
        if (!data.active || !WaveManager.isGameRunning() || !validPlayer(p) || !reachable(p, pos)
                || level.getBlockState(pos).isAir() && !data.available.containsKey(pos)) return;
        UUID player = p.getUUID();
        if (data.assembling.containsKey(player)||data.installing.containsKey(player)) return;
        if(level.getBlockState(pos).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get())){data.beginInstallation(p,pos);return;}
        Part part = data.available.get(pos);
        if (part != null) {
            if(!aimedPickup(p,pos,data.pickupBounds(level,pos,part)))return;
            if(data.deliveries.containsKey(player))return;
            Part previous = data.carried.remove(player);
            data.available.remove(pos); data.removeDisplay(level, data.partDisplays.remove(pos));
            data.carried.put(player, part); data.hide(level, part);
            if (previous != null) {
                // The previous component is left exactly where the new component was picked up.
                data.available.put(pos, previous);
                UUID display=data.showComponent(level,previous,pos);
                if(display!=null)data.partDisplays.put(pos,display);
                LuaScriptManager.callEvent("OnBuildablePartDropped", player.toString(), previous.recipe, previous.id);
            }
            sound(level, pos, "pickup", SoundEvents.ITEM_PICKUP);
            data.sync(level); LuaScriptManager.callEvent("OnBuildablePartPickedUp", player.toString(), part.recipe, part.id);
            return;
        }
        Bench bench = data.benches.get(pos); if (bench == null) return;
        if (data.complete(bench)) { data.claim(p, pos, bench); return; }
        part = data.carried.get(player);
        if (part == null) { message(p, "need_part"); return; }
        if (!bench.recipe.isEmpty() && !bench.recipe.equals(part.recipe)) { message(p, "wrong_bench"); return; }
        if (bench.installed.contains(part.id)) { message(p, "already_installed"); return; }
        // An object is built at one workbench. A free bench locks only when the first part is installed.
        for (var entry : data.benches.entrySet()) {
            if (!entry.getKey().equals(pos) && entry.getValue().recipe.equals(part.recipe) && !entry.getValue().installed.isEmpty()) {
                message(p, "other_bench"); return;
            }
        }
        final BlockPos benchPos=pos;
        if (data.assembling.values().stream().anyMatch(a -> a.bench.equals(benchPos))) { message(p, "busy"); return; }
        if(!data.held(p,pos))return;
        int ticks = data.recipes().getCompound(part.recipe).getInt("ticks");
        data.assembling.put(player, new Assembly(pos, part, level.getGameTime() + ticks));
        p.stopUsingItem(); EquipmentSystem.cancel(p); me.cryo.zombierool.handlers.LethalWeaponManager.cancel(p);
        // Same hand animation used when depositing a weapon at Pack-a-Punch.
        EquipmentSystem.animate(p, "knuckle_crack", 40);
        sound(level, pos, "assemble", SoundEvents.ANVIL_USE); data.sync(level);
    }
    private boolean complete(Bench b) { return !b.recipe.isEmpty() && recipes().contains(b.recipe) && !parts(recipes().getCompound(b.recipe)).isEmpty() && b.installed.containsAll(parts(recipes().getCompound(b.recipe))); }
    private void claim(ServerPlayer p, BlockPos pos, Bench b) {
        var recipe = recipes().getCompound(b.recipe); int limit = recipe.getInt("limit");
        if (limit > 0 && b.claims >= limit) { message(p, "exhausted"); return; }
        ItemStack reward = ItemStack.of(recipe.getCompound("reward")); String mode = recipe.getString("mode");
        if(mode.equals("deploy")){
            if(deliveries.containsKey(p.getUUID())||carried.containsKey(p.getUUID())){message(p,"hands_full");return;}
            if(definitions.getList("destinations",10).stream().noneMatch(t->((CompoundTag)t).getString("recipe").equals(b.recipe))){message(p,"no_destination");return;}
            deliveries.put(p.getUUID(),new Delivery(b.recipe,pos));
        } else if (mode.equals("item")) {
            if(PortableEquipment.portable(reward)){if(!PortableEquipment.acquire(p,reward)){message(p,"owned");return;}b.claims++;sound(p.serverLevel(),pos,"take",SoundEvents.ITEM_PICKUP);sync(p.serverLevel());LuaScriptManager.callEvent("OnBuildableClaimed",p.getUUID().toString(),b.recipe,pos.getX(),pos.getY(),pos.getZ(),b.claims);return;}
            boolean weapon = me.cryo.zombierool.core.system.WeaponFacade.isWeapon(reward);
            boolean equipment = reward.getItem() instanceof EquipmentSystem.LoadoutItem
                    || reward.getItem() instanceof me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableItem
                    || reward.getItem() == me.cryo.zombierool.core.registry.ZRRegistry.BOWIE_KNIFE;
            // Reusable equipment can be replaced after loss, without filling an inventory with duplicates.
            for (ItemStack owned : p.getInventory().items) if (ItemStack.isSameItemSameTags(owned, reward)) { message(p, "owned"); return; }
            if (ItemStack.isSameItemSameTags(p.getOffhandItem(), reward)) { message(p, "owned"); return; }
            int capacity = 0;
            for (ItemStack slot : p.getInventory().items) {
                if (slot.isEmpty()) capacity += reward.getMaxStackSize();
                else if (ItemStack.isSameItemSameTags(slot, reward)) capacity += Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
            if (weapon || equipment) me.cryo.zombierool.core.system.WeaponFacade.grantWeaponToPlayer(p, reward.copy());
            else {
                if (capacity < reward.getCount()) { message(p, "inventory_full"); return; }
                p.getInventory().add(reward.copy());
            }
            p.containerMenu.broadcastChanges();
        } else if (mode.equals("block")) {
            BlockPos output = pos.above();
            if (!p.serverLevel().getBlockState(output).canBeReplaced()) { message(p, "blocked"); return; }
            if (b.claims > 0) { message(p, "exhausted"); return; }
            MatchWorldJournal.note(p.serverLevel(), output);
            var original = new CompoundTag(); original.putLong("pos", output.asLong());
            original.put("state", NbtUtils.writeBlockState(p.serverLevel().getBlockState(output)));
            if (p.serverLevel().getBlockEntity(output) != null) original.put("be", p.serverLevel().getBlockEntity(output).saveWithoutMetadata());
            outputs.put(output, original); setDirty();
            if (!p.serverLevel().setBlock(output, ((BlockItem)reward.getItem()).getBlock().defaultBlockState(), 3)) return;
            if (reward.hasTag() && reward.getTag().contains("BlockEntityTag") && p.serverLevel().getBlockEntity(output) != null) {
                var entity = p.serverLevel().getBlockEntity(output); entity.load(reward.getTag().getCompound("BlockEntityTag")); entity.setChanged();
            }
        }
        b.claims++;
        // The completed model is the permanent visual state of this bench, even after its last claim.
        sound(p.serverLevel(), pos, "take", SoundEvents.ITEM_PICKUP); sync(p.serverLevel());
        LuaScriptManager.callEvent("OnBuildableClaimed", p.getUUID().toString(), b.recipe, pos.getX(), pos.getY(), pos.getZ(), b.claims);
    }
    public void drop(ServerPlayer p) {
        holds.remove(p.getUUID());
        if(installing.containsKey(p.getUUID()))EquipmentSystem.animate(p,"knuckle_cancel",1);
        if(deliveries.containsKey(p.getUUID())){releaseDelivery(p.getUUID());sync(p.serverLevel());}
        if (assembling.remove(p.getUUID()) != null) EquipmentSystem.animate(p, "knuckle_cancel", 1);
        Part part = carried.remove(p.getUUID()); if (part == null) return;
        returnPart(p.serverLevel(),part); sync(p.serverLevel());
        LuaScriptManager.callEvent("OnBuildablePartDropped", p.getUUID().toString(), part.recipe, part.id);
    }
    private void returnPart(ServerLevel level, Part part) {
        // A swap may have left a different component on this component's original support.
        Part occupant=available.remove(part.source);
        removeDisplay(level,partDisplays.remove(part.source));
        if(occupant!=null&&!occupant.equals(part))returnPart(level,occupant);
        restore(level,part);available.put(part.source,part);showSource(level,part);
    }
    public String carried(UUID player) { var part = carried.get(player); return part == null ? "" : part.recipe + ":" + part.id; }
    public int installed(BlockPos pos) { var b = benches.get(pos); return b == null ? 0 : b.installed.size(); }
    public boolean completed(BlockPos pos) { var b = benches.get(pos); return b != null && complete(b); }
    private void tick(ServerLevel level) {
        if (!active) return;
        if (WaveManager.isPausedByPlayer()) {
            assembling.replaceAll((id, a) -> new Assembly(a.bench, a.part, a.finish + 1));
            installing.replaceAll((id,job)->new Installation(job.pos,job.delivery,job.finish+1));
            var pending=new HashSet<>(assembling.keySet());pending.addAll(installing.keySet());
            if(level.getGameTime()%5==0)for(UUID id:pending){var player=level.getServer().getPlayerList().getPlayer(id);if(player!=null)BuildablePackets.sync(player,state(player));}return;
        }
        for(UUID id:new ArrayList<>(deliveries.keySet())){var p=level.getServer().getPlayerList().getPlayer(id);if(p==null||p.serverLevel()!=level||!validPlayer(p)){releaseDelivery(id);sync(level);}}
        for (UUID id : new ArrayList<>(carried.keySet())) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null && p.serverLevel() == level && !validPlayer(p)) drop(p);
        }
        for(var entry:new ArrayList<>(installing.entrySet())){
            var job=entry.getValue();var p=level.getServer().getPlayerList().getPlayer(entry.getKey());
            if(p==null||p.serverLevel()!=level||!validPlayer(p)||!held(p,job.pos)||!reachable(p,job.pos)||!Objects.equals(deliveries.get(entry.getKey()),job.delivery)||!(level.getBlockEntity(job.pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport marker)||!marker.recipe.equals(job.delivery.recipe)){
                installing.remove(entry.getKey());if(p!=null)EquipmentSystem.animate(p,"knuckle_cancel",1);sync(level);continue;
            }
            if(level.getGameTime()<job.finish){if(level.getGameTime()%5==0)BuildablePackets.sync(p,state(p));continue;}
            installing.remove(entry.getKey());placeDelivery(p,job.pos);sync(level);
        }
        for (var entry : new ArrayList<>(assembling.entrySet())) {
            var p = level.getServer().getPlayerList().getPlayer(entry.getKey()); var a = entry.getValue();
            if (p == null || p.serverLevel() != level || !validPlayer(p) || !held(p,a.bench) || !reachable(p, a.bench)
                    || !Objects.equals(carried.get(entry.getKey()), a.part)) {
                assembling.remove(entry.getKey()); if (p != null) EquipmentSystem.animate(p, "knuckle_cancel", 1); sync(level); continue;
            }
            if (level.getGameTime() < a.finish) {
                if (level.getGameTime() % 5 == 0) BuildablePackets.sync(p, state(p));
                continue;
            }
            assembling.remove(entry.getKey()); var bench = benches.get(a.bench);
            if (bench == null || !bench.recipe.isEmpty() && !bench.recipe.equals(a.part.recipe)) continue;
            // Recheck the cross-bench lock: two first parts can be in progress at the same time.
            if (benches.entrySet().stream().anyMatch(e -> !e.getKey().equals(a.bench) && e.getValue().recipe.equals(a.part.recipe) && !e.getValue().installed.isEmpty())) { sync(level); continue; }
            bench.recipe = a.part.recipe; bench.installed.add(a.part.id); carried.remove(entry.getKey());
            showPart(level, a.bench, a.part, bench.installed.size());
            boolean done = complete(bench); if (done) showResult(level, a.bench, bench); sound(level, a.bench, done ? "complete" : "installed", done ? SoundEvents.ANVIL_LAND : SoundEvents.IRON_TRAPDOOR_CLOSE);
            sync(level);
            LuaScriptManager.callEvent("OnBuildablePartInstalled", p.getUUID().toString(), bench.recipe, a.part.id, a.bench.getX(), a.bench.getY(), a.bench.getZ());
            if (done) LuaScriptManager.callEvent("OnBuildableCompleted", p.getUUID().toString(), bench.recipe, a.bench.getX(), a.bench.getY(), a.bench.getZ());
        }
    }
    private void showPart(ServerLevel level, BlockPos pos, Part part, int index) {
        UUID id = showStack(level, partIcon(level, part), pos.getX() + .25 + ((index - 1) % 3) * .25,
                pos.getY() + 1.22 + ((index - 1) / 9) * .2, pos.getZ() + .25 + (((index - 1) / 3) % 3) * .25, .28f);
        if (id != null) {benchDisplays.computeIfAbsent(pos, k -> new HashSet<>()).add(id);var display=level.getEntity(id);var tag=display.saveWithoutId(new CompoundTag());tag.putString("billboard","vertical");display.load(tag);}
    }
    private void removeDisplay(ServerLevel level, UUID id) {
        if (id == null) return; var entity = level.getEntity(id); if (entity != null) entity.discard(); displays.remove(id);
    }
    private void showResult(ServerLevel level, BlockPos pos, Bench bench) {
        for (UUID id : benchDisplays.getOrDefault(pos, Set.of())) removeDisplay(level, id);
        benchDisplays.remove(pos);
        var recipe = recipes().getCompound(bench.recipe);
        ItemStack reward = ItemStack.of(recipe.getCompound("reward"));
        if (reward.isEmpty()) return;
        var center=me.cryo.zombierool.block.system.BuildableBlocks.center(level,pos);
        // Ground transforms can translate a model downwards: keep enough clearance above the top.
        UUID id = showStack(level, reward, center.x, pos.getY() + 1.38, center.z, .65f);
        if (id != null) {
            resultDisplays.put(pos, id);
            var entity = level.getEntity(id); var n = entity.saveWithoutId(new CompoundTag());
            boolean flatWeapon=me.cryo.zombierool.core.system.WeaponFacade.isWeapon(reward)&&!me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(reward);
            n.putString("item_display", flatWeapon?"fixed":"ground");n.putString("billboard","vertical");
            if(flatWeapon){var transform=n.getCompound("transformation");transform.put("scale",floats(.9f,.9f,.9f));transform.put("left_rotation",floats(0,0,0,1));}
            n.getList("Tags", Tag.TAG_STRING).add(StringTag.valueOf("zr_buildable_result"));
            entity.load(n);
        }
    }
    private static ItemStack partIcon(ServerLevel level, Part part) {
        if (part.snapshot.contains("visual", Tag.TAG_COMPOUND)) return ItemStack.of(part.snapshot.getCompound("visual"));
        var state = NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), part.snapshot.getCompound("state"));
        return new ItemStack(state.getBlock());
    }
    private void showSource(ServerLevel level, Part part) {
        if (!part.snapshot.contains("visual", Tag.TAG_COMPOUND)) return;
        hide(level, part);
        UUID id = showComponent(level,part,part.source);
        if (id != null) partDisplays.put(part.source, id);
    }
    private UUID showComponent(ServerLevel level,Part part,BlockPos pos){
        var saved=NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),part.snapshot.getCompound("state"));
        var face=saved.hasProperty(me.cryo.zombierool.block.system.BuildableBlocks.PartBlock.FACE)?saved.getValue(me.cryo.zombierool.block.system.BuildableBlocks.PartBlock.FACE):net.minecraft.core.Direction.UP;
        var transform=me.cryo.zombierool.block.system.BuildableBlocks.componentPose(partIcon(level,part),face);
        UUID id=showStack(level,partIcon(level,part),pos.getX()+transform.x(),pos.getY()+transform.y(),pos.getZ()+transform.z(),transform.size());if(id==null)return null;
        var entity=level.getEntity(id);var tag=entity.saveWithoutId(new CompoundTag());tag.putString("item_display",transform.context().getSerializedName());
        tag.getCompound("transformation").put("left_rotation",floats(transform.rx(),transform.ry(),transform.rz(),transform.rw()));entity.load(tag);
        return id;
    }
    private UUID showStack(ServerLevel level, ItemStack stack, double x, double y, double z, float size) {
        var entity = net.minecraft.world.entity.EntityType.ITEM_DISPLAY.create(level); if (entity == null) return null;
        var tag = new CompoundTag(); tag.put("item", stack.save(new CompoundTag()));
        tag.putString("item_display", "fixed"); tag.putBoolean("Invulnerable", true); tag.putBoolean("NoGravity", true);
        var transform = new CompoundTag(); transform.put("scale", floats(size, size, size));
        transform.put("translation", floats(0, 0, 0)); transform.put("left_rotation", floats(0, 0, 0, 1));
        transform.put("right_rotation", floats(0, 0, 0, 1)); tag.put("transformation", transform);
        var tags = new ListTag(); tags.add(StringTag.valueOf("zr_buildable_display")); tag.put("Tags", tags);
        entity.load(tag); entity.setPos(x, y, z);
        if (level.addFreshEntity(entity)) { displays.add(entity.getUUID()); return entity.getUUID(); }
        return null;
    }
    private static ListTag floats(float... values) {
        var list = new ListTag(); for (float value : values) list.add(FloatTag.valueOf(value)); return list;
    }
    private static void sound(ServerLevel level, BlockPos pos, String id, SoundEvent fallback) {
        if(id.equals("take")){level.playSound(null,pos,me.cryo.zombierool.init.ZombieroolModSounds.BUY.get(),SoundSource.BLOCKS,.65f,1);return;}
        var event = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buildable_" + id));
        level.playSound(null, pos, event == null ? fallback : event, SoundSource.BLOCKS, .65f, 1);
    }
    public CompoundTag state(ServerPlayer player) {
        var n = new CompoundTag(); var targets = new ListTag();
        if (active) {
            for (var entry : available.entrySet()) {if(scriptHidden.contains(entry.getKey()))continue;
                var t = new CompoundTag(); t.putLong("pos", entry.getKey().asLong()); t.putString("part", entry.getValue().id);t.putBoolean("pickup",true);
                var box=pickupBounds(player.serverLevel(),entry.getKey(),entry.getValue());var bounds=new CompoundTag();bounds.putDouble("x0",box.minX);bounds.putDouble("y0",box.minY);bounds.putDouble("z0",box.minZ);bounds.putDouble("x1",box.maxX);bounds.putDouble("y1",box.maxY);bounds.putDouble("z1",box.maxZ);t.put("bounds",bounds);targets.add(t);
            }
            for (var entry : benches.entrySet()) {if(scriptHidden.contains(entry.getKey()))continue;
                var b = entry.getValue(); var t = new CompoundTag(); t.putLong("pos", entry.getKey().asLong()); t.putBoolean("bench", true);
                t.putString("name", b.recipe.isEmpty() ? "" : recipes().getCompound(b.recipe).getString("name"));
                t.putInt("installed", b.installed.size()); t.putInt("total", b.recipe.isEmpty() ? 0 : parts(recipes().getCompound(b.recipe)).size());
                t.putBoolean("complete", complete(b));
                var recipe=recipes().getCompound(b.recipe);t.putBoolean("exhausted",recipe.getInt("limit")>0&&b.claims>=recipe.getInt("limit")||recipe.getString("mode").equals("block")&&b.claims>0);
                targets.add(t);
            }
        }
        if(active)for(var tag:definitions.getList("destinations",10)){var d=(CompoundTag)tag;var pos=BlockPos.of(d.getLong("pos"));if(player.serverLevel().getBlockState(pos).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get())){var t=new CompoundTag();t.putLong("pos",pos.asLong());t.putBoolean("destination",true);t.putBoolean("ready",delivery(player.getUUID()).equals(d.getString("recipe")));t.put("reward",recipes().getCompound(d.getString("recipe")).getCompound("reward"));targets.add(t);}}
        n.putString("delivery",delivery(player.getUUID()));n.put("targets", targets); n.put("catalog", catalog(player));
        var assembly = assembling.get(player.getUUID());
        if (assembly != null) {
            int duration = Math.max(40, recipes().getCompound(assembly.part.recipe).getInt("ticks"));
            n.putInt("duration", duration); n.putInt("elapsed", (int)Math.max(0, duration - (assembly.finish - player.serverLevel().getGameTime())));
        }
        var installation=installing.get(player.getUUID());
        if(installation!=null){n.putInt("duration",INSTALL_TICKS);n.putInt("elapsed",(int)Math.max(0,INSTALL_TICKS-(installation.finish-player.serverLevel().getGameTime())));n.putBoolean("installing",true);}
        n.putBoolean("paused",WaveManager.isPausedByPlayer()); n.putString("carried", carried(player.getUUID())); n.putBoolean("busy", assembling.containsKey(player.getUUID())||installing.containsKey(player.getUUID()));
        var part = carried.get(player.getUUID());
        if(deliveries.containsKey(player.getUUID()))n.put("icon",recipes().getCompound(deliveries.get(player.getUUID()).recipe).getCompound("reward"));
        if (part != null) {
            n.put("icon", partIcon(player.serverLevel(), part).save(new CompoundTag()));
        }
        return n;
    }
    private ListTag catalog(ServerPlayer player) {
        var list = new ListTag();
        var ids = new ArrayList<>(recipes().getAllKeys());ids.removeAll(hiddenRecipes); Collections.sort(ids);
        for (String recipe : ids) for (String id : parts(recipes().getCompound(recipe))) {
            var n = new CompoundTag(); n.putString("recipe", recipe); n.putString("recipeName", recipes().getCompound(recipe).getString("name")); n.putString("part", id);
            for (var tag : definitions.getList("anchors", Tag.TAG_COMPOUND)) {
                var a = (CompoundTag)tag;
                if (a.getString("recipe").equals(recipe) && a.getString("part").equals(id)) { n.put("icon", partIcon(player.serverLevel(), new Part(recipe, id, BlockPos.of(a.getLong("pos")), a)).save(new CompoundTag())); break; }
            }
            String status = "missing";
            for (var entry : carried.entrySet()) if (entry.getValue().recipe.equals(recipe) && entry.getValue().id.equals(id)) status = entry.getKey().equals(player.getUUID()) ? "self" : "team";
            for (var bench : benches.values()) if (bench.recipe.equals(recipe) && bench.installed.contains(id)) status = "installed";
            n.putString("status", status); list.add(n);
        }
        return list;
    }
    public void sync(ServerLevel level) { for (ServerPlayer p : level.players()) BuildablePackets.sync(p, state(p)); }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level) get(level).tick(level);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) get(p.serverLevel()).drop(p); }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) get(p.serverLevel()).drop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            ServerLevel old = p.server.getLevel(e.getFrom()); if (old != null) get(old).returnFromDimension(old, p);
            get(p.serverLevel()).sync(p.serverLevel());
        }
    }
    private void returnFromDimension(ServerLevel level, ServerPlayer p) {
        holds.remove(p.getUUID());releaseDelivery(p.getUUID()); assembling.remove(p.getUUID()); Part part = carried.remove(p.getUUID());
        if (part != null) {
            returnPart(level,part);
            LuaScriptManager.callEvent("OnBuildablePartDropped", p.getUUID().toString(), part.recipe, part.id);
        }
        sync(level);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) BuildablePackets.sync(p, get(p.serverLevel()).state(p)); }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void interactLock(net.minecraftforge.event.entity.player.PlayerInteractEvent e) {
        if (busy(e.getEntity()) && e.isCancelable()) e.setCanceled(true);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void attackLock(net.minecraftforge.event.entity.player.AttackEntityEvent e) { if (busy(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void useLock(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start e) { if (busy(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void loaded(LevelEvent.Load e) {
        if (e.getLevel() instanceof ServerLevel level && !get(level).active) get(level).reset(level);
    }
    @SubscribeEvent public static void entities(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel level && e.loadedFromDisk() && e.getEntity().getTags().contains("zr_buildable_display")
                && !get(level).displays.contains(e.getEntity().getUUID())) e.setCanceled(true);
    }
}
