package me.cryo.zombierool.hotfix;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import me.cryo.zombierool.maptexture.*;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid="zombierool",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class HotfixTextureFolders {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("zombierool","texture_folders"),()->"1","1"::equals,"1"::equals);
    public record Request(int action,int slot,String path) {}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(()->{CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(HotfixTextureFolders::encode).decoder(HotfixTextureFolders::decode).consumerNetworkThread(HotfixTextureFolders::handle).add();});}
    public static void encode(Request msg,FriendlyByteBuf buf){buf.writeVarInt(msg.action());buf.writeVarInt(msg.slot());buf.writeUtf(msg.path(),128);}
    public static Request decode(FriendlyByteBuf buf){return new Request(buf.readVarInt(),buf.readVarInt(),buf.readUtf(128));}
    public static void handle(Request msg,Supplier<NetworkEvent.Context> ctx){
        var context=ctx.get();context.enqueueWork(()->applyRequest(context.getSender(),msg));context.setPacketHandled(true);
    }
    public static void applyRequest(net.minecraft.server.level.ServerPlayer player,Request msg){
            if(player==null||!player.isCreative()||!(player.containerMenu instanceof MapTextures.TextureKitMenu menu))return;
            if(msg.action()==2||msg.action()==3){try{int index=Integer.parseInt(msg.path());if(msg.slot()<0||msg.slot()>=MapTextures.SLOTS)return;if(msg.action()==2&&index>=0&&index<MapTextures.SOUND_IDS.length)menu.clickMenuButton(player,MapTextures.SOUND_BUTTON+msg.slot()*MapTextures.SOUND_IDS.length+index);if(msg.action()==3&&index>=0&&index<MapTextures.SHAPES.length)menu.clickMenuButton(player,msg.slot()*MapTextures.SHAPES.length+index);}catch(NumberFormatException ignored){}return;}
            String path=MapTextures.sanitize(msg.path());if(path.isEmpty()&&!(msg.action()==1&&msg.path().trim().equals("/"))||path.startsWith("import/")||path.equals("import"))return;
            try{
                var root=MapTextures.customDir();if(root==null)return;root=root.toAbsolutePath().normalize();var destination=root.resolve(path).normalize();if(!destination.startsWith(root))return;
                if(msg.action()==0)java.nio.file.Files.createDirectories(destination);
                else if(msg.action()==1)MapTextures.moveTexture(msg.slot(),path);
                else return;
                var level=player.server.overworld();var names=MapTextures.syncFolder(level);MapTextureSync.refresh(level,null);var sounds=MapTextures.soundList();
                int slot=msg.slot()>=0&&msg.slot()<names.size()?msg.slot():-1;
                NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,p)->{var refreshed=new MapTextures.TextureKitMenu(id,inventory,names,sounds);refreshed.preferredSlot=slot;refreshed.preferredFolder=path;return refreshed;},Component.translatable("item.zombierool.texture_kit")),buf->{buf.writeVarInt(names.size());for(String name:names)buf.writeUtf(name);buf.writeVarInt(sounds.size());for(String sound:sounds)buf.writeUtf(sound);MapTextures.writeFolders(buf);buf.writeVarInt(slot);buf.writeUtf(path);});
                player.displayClientMessage(Component.translatable(msg.action()==1?"gui.zombierool.texture_kit.moved":"gui.zombierool.texture_kit.folder_created",msg.action()==1?MapTextures.humanize(names.get(msg.slot())):path),true);
            }catch(java.io.IOException e){player.displayClientMessage(Component.translatable("gui.zombierool.texture_error"),true);}
    }
}
