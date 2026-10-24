package me.cryo.zombierool.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "zombierool")
public final class BuildablePackets {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("zombierool", "buildables"), () -> "1", "1"::equals, "1"::equals);
    public record Request(String action, CompoundTag data) {}
    public record Update(boolean menu, CompoundTag data) {}
    public static void register() {
        CHANNEL.registerMessage(0, Request.class, (p,b) -> { b.writeUtf(p.action,32); b.writeNbt(p.data); }, b -> new Request(b.readUtf(32),b.readNbt()), (p,c) -> {
            var ctx=c.get(); ctx.enqueueWork(() -> { var player=ctx.getSender(); if(player!=null && player.hasPermissions(2) && p.data!=null) handle(player,p); }); ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Update.class, (p,b) -> { b.writeBoolean(p.menu); b.writeNbt(p.data); }, b -> new Update(b.readBoolean(),b.readNbt()), (p,c) -> {
            c.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> me.cryo.zombierool.client.BuildableClient.receive(p.menu,p.data)));
            c.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void request(String action, CompoundTag n) { CHANNEL.sendToServer(new Request(action,n)); }
    public static void sync(ServerPlayer p, CompoundTag n) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),new Update(false,n)); }
    public static void open(ServerPlayer p, String status) {
        var hit=p.pick(6,0,false);
        openAt(p,hit.getType()==HitResult.Type.BLOCK ? ((net.minecraft.world.phys.BlockHitResult)hit).getBlockPos() : null,status);
    }
    public static void openAt(ServerPlayer p, BlockPos pos, String status) { openAt(p,pos,status,""); }
    private static void openAt(ServerPlayer p, BlockPos pos, String status, String selected) {
        var n=BuildableSystem.get(p.serverLevel()).definitions(); n.putString("statusKey",status);n.putString("selected",selected);
        if(pos!=null){
            n.putLong("target",pos.asLong());
            if(p.serverLevel().getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport support){
                n.putBoolean("destinationEditor",support.getBlockState().is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()));n.putBoolean("partEditor",true);n.putString("selected",selected.isEmpty()?support.recipe:selected);n.putString("partId",support.part);n.put("visual",support.visual.save(new CompoundTag()));
            }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Update(true,n));
    }
    private static String identifier(String name) {
        var id=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./:-]","_");
        return id.substring(0,Math.min(64,id.length()));
    }
    private static void openRecipes(ServerPlayer p, BlockPos pos, String status, String selected) {
        var n=BuildableSystem.get(p.serverLevel()).definitions();n.putString("statusKey",status);n.putString("selected",selected);
        if(pos!=null)n.putLong("target",pos.asLong());
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Update(true,n));
    }
    private static net.minecraft.world.item.ItemStack chosenItem(ServerPlayer p, CompoundTag n, String prefix) {
        int slot=n.contains(prefix+"Slot")?n.getInt(prefix+"Slot"):-1;
        if(slot>=0&&slot<36)return p.getInventory().getItem(slot).copy();
        if(n.contains(prefix+"Item")){
            var key=ResourceLocation.tryParse(n.getString(prefix+"Item"));
            var item=key==null?null:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(key);
            return item==null?net.minecraft.world.item.ItemStack.EMPTY:new net.minecraft.world.item.ItemStack(item);
        }
        return p.getMainHandItem().copy();
    }
    private static void closeEditor(ServerPlayer p,String key){
        var n=new CompoundTag();n.putBoolean("close",true);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Update(true,n));
        p.displayClientMessage(Component.translatable(key),true);
    }
    private static void handle(ServerPlayer p, Request request) {
        var n=request.data; var data=BuildableSystem.get(p.serverLevel());
        BlockPos pos=n.contains("target")?BlockPos.of(n.getLong("target")):null;
        try {
            if (!request.action.equals("open") && WaveManager.isGameRunning()) throw new IllegalArgumentException("editing_match");
            if(pos!=null && (p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64 || !p.serverLevel().hasChunkAt(pos)))throw new IllegalArgumentException("target_block");
            String id=n.getString("id");
            switch(request.action){
                case "preset" -> {String created=data.createPreset(n.getString("preset"));openRecipes(p,pos,"message.zombierool.buildable.saved",created);return;}
                case "save" -> {
                    if(id.isBlank()){
                        id=identifier(n.getString("name"));String base=id;int suffix=2;
                        while(data.definitions().getCompound("recipes").contains(id))id=base.substring(0,Math.min(57,base.length()))+"_"+suffix++;
                    }
                    var old=data.definitions().getCompound("recipes").getCompound(id);
                    var reward=n.getBoolean("capture")?chosenItem(p,n,"reward"):net.minecraft.world.item.ItemStack.of(old.getCompound("reward"));
                    data.editRecipe(id,n.getString("name"),reward,n.getString("mode"),n.getInt("limit"));
                }
                case "part" -> {
                    if(pos==null || !(p.serverLevel().getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport support))throw new IllegalArgumentException("target_block");
                    var visual=n.getBoolean("capture")?chosenItem(p,n,"visual"):support.visual.copy();
                    String part=identifier(n.getString("part").trim());
                    if(part.isEmpty()&&!visual.isEmpty())part=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(visual.getItem()).toString();
                    if(support.getBlockState().is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get())){support.configure(id,"destination",visual);data.destination(p.serverLevel(),pos,id);}else data.configurePart(p.serverLevel(),pos,id,part,visual);
                }
                case "support", "destination" -> {
                    if(!data.definitions().getCompound("recipes").contains(id))throw new IllegalArgumentException("unknown_recipe");
                    var visual=chosenItem(p,n,"visual");
                    if(n.getBoolean("duplicate"))for(var entry:data.definitions().getList("anchors",10)){
                        var anchor=(CompoundTag)entry;
                        if(anchor.getString("recipe").equals(id)&&anchor.getString("part").equals(n.getString("part"))){visual=net.minecraft.world.item.ItemStack.of(anchor.getCompound("visual"));break;}
                    }
                    if(n.getBoolean("duplicate"))for(var entry:data.definitions().getCompound("recipes").getCompound(id).getList("presetPieces",10)){var piece=(CompoundTag)entry;if(piece.getString("part").equals(n.getString("part")))visual=net.minecraft.world.item.ItemStack.of(piece.getCompound("visual"));}
                    if(request.action.equals("destination"))visual=net.minecraft.world.item.ItemStack.of(data.definitions().getCompound("recipes").getCompound(id).getCompound("reward"));
                    if(visual.isEmpty())throw new IllegalArgumentException("empty_reward");
                    String part=n.getString("part");if(part.isBlank())part=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(visual.getItem()).toString();
                    if(!part.matches("[a-z0-9_./:-]{1,64}"))throw new IllegalArgumentException("invalid_parts");
                    boolean destination=request.action.equals("destination");
                    if(destination){visual=net.minecraft.world.item.ItemStack.of(data.definitions().getCompound("recipes").getCompound(id).getCompound("reward"));if(!(visual.getItem() instanceof net.minecraft.world.item.BlockItem))throw new IllegalArgumentException("block_reward");part="destination";}
                    var stack=new net.minecraft.world.item.ItemStack(destination?me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get():me.cryo.zombierool.block.system.BuildableBlocks.PART.get());
                    var tag=new CompoundTag();tag.putString("recipe",id);tag.putString("part",part);tag.put("visual",visual.copyWithCount(1).save(new CompoundTag()));
                    stack.getOrCreateTag().put("BlockEntityTag",tag);
                    stack.setHoverName(Component.translatable(destination?"gui.zombierool.buildable.destination_named":"gui.zombierool.buildable.support_named",Component.translatable(visual.getDescriptionId())));
                    if(!p.getInventory().add(stack))p.drop(stack,false);
                    closeEditor(p,destination?"gui.zombierool.buildable.place_destination":"gui.zombierool.buildable.place_support");return;
                }
                case "recipes" -> {openRecipes(p,pos,"","");return;}
                case "remove" -> data.remove(id);
                case "open" -> {open(p,"");return;}
                default -> {return;}
            }
            data.sync(p.serverLevel());
            if(request.action.equals("save"))openRecipes(p,pos,"message.zombierool.buildable.saved",id);
            else closeEditor(p,"message.zombierool.buildable.saved");
        }catch(IllegalArgumentException ex){openAt(p,pos,"message.zombierool.buildable."+ex.getMessage());}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("zombierool")
                .then(net.minecraft.commands.Commands.literal("buildables").requires(s -> s.hasPermission(2))
                .executes(c -> { open(c.getSource().getPlayerOrException(),""); return 1; })));
    }
}
