package local.hotfixtest;

import me.cryo.zombierool.maptexture.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.util.*;

public final class TextureSpawnerRuntimeTest {
    static void check(boolean value,String message){HotfixRuntimeTest.check(value,message);}
    public static void run(ServerLevel level,ServerPlayer player)throws Exception {
        player.setGameMode(GameType.CREATIVE);player.setPos(30.5,80,5.5);
        var block=(UniversalSpawnerSystem.UniversalSpawnerBlock)UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get();
        int index=0;
        for(var type:UniversalSpawnerSystem.SpawnerMobType.values()){
            BlockPos source=new BlockPos(30+index*3,80,0),target=source.offset(1,0,0);index++;
            level.setBlock(source.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(target.below(),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(source,block.defaultBlockState(),3);level.setBlock(target,Blocks.AIR.defaultBlockState(),3);
            var original=(UniversalSpawnerSystem.UniversalSpawnerBlockEntity)level.getBlockEntity(source);
            original.setConfig(type,"","0","0",false,3);original.tick();
            ItemStack copied=block.getCloneItemStack(level.getBlockState(source),new BlockHitResult(Vec3.atCenterOf(source),Direction.UP,source,false),level,source,player);
            check(copied.getTag().getCompound("BlockStateTag").getString("mob_type").equals(type.getSerializedName()),"Copied "+type+" spawner carries its inventory model predicate");
            check(copied.getTag().getCompound("BlockEntityTag").getString("StartChannels").equals("0"),"Copied spawner preserves channel zero");
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,copied,new BlockHitResult(Vec3.atCenterOf(target.below()).add(0,.5,0),Direction.UP,target.below(),false));
            var result=((BlockItem)copied.getItem()).place(context);
            check(result.consumesAction(),"Copied "+type+" spawner is placed through the real BlockItem path");
            var placed=(UniversalSpawnerSystem.UniversalSpawnerBlockEntity)level.getBlockEntity(target);
            check(placed!=null&&placed.getMobType()==type&&placed.getSpawnWeight()==3,"Placed copy retains full spawner configuration");
            check(SpawnerRegistry.getSpawners(level).contains(placed)&&placed.isActive(level),"Placed channel-zero "+type+" copy is registered and immediately active");
            level.removeBlock(source,false);level.removeBlock(target,false);
            check(!SpawnerRegistry.getSpawners(level).contains(placed),"Removed copy leaves the spawner registry");
        }
        var mimic=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","obstacle_door"));
        BlockPos bottom=new BlockPos(44,80,0);level.setBlock(bottom,mimic.defaultBlockState(),3);level.setBlock(bottom.above(),mimic.defaultBlockState(),3);
        for(Block door:List.of(Blocks.OAK_DOOR,MapTextures.BLOCKS[0].door.get())){
            ItemStack held=new ItemStack(door);player.setItemInHand(InteractionHand.MAIN_HAND,held);
            mimic.use(level.getBlockState(bottom),level,bottom,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(bottom),Direction.NORTH,bottom,false));
            var low=(MimicSystem.IMimicContainer)level.getBlockEntity(bottom);var high=(MimicSystem.IMimicContainer)level.getBlockEntity(bottom.above());
            check(low.getMimic().is(door)&&low.getMimic().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&high.getMimic().is(door)&&high.getMimic().getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER,"Stacked buyable doors copy both halves of "+ForgeRegistries.BLOCKS.getKey(door));
            check(MimicSystem.getConnectedState(high.getMimic(),level,bottom.above()).getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER,"Mimic renderer retains the upper door half");
        }
        var glass=(GlassDefenseDoorBlock)ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","glass_defense_door"));
        BlockPos first=new BlockPos(48,80,0),second=first.east();player.setYRot(180);
        for(BlockPos pos:List.of(first,second)){
            level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            ItemStack stack=new ItemStack(glass);var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false));
            check(((BlockItem)stack.getItem()).place(context).consumesAction(),"Glass double-door leaf places through the real BlockItem path");
        }
        check(level.getBlockState(first).getValue(DoorBlock.HINGE)!=level.getBlockState(second).getValue(DoorBlock.HINGE),"Adjacent Glass Defense Door leaves receive opposite hinges");
        level.removeBlock(first.above(),false);level.removeBlock(first,false);level.removeBlock(second.above(),false);level.removeBlock(second,false);
        for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean reversed:new boolean[]{false,true}){
            BlockPos left=new BlockPos(48,80,0),right=left.relative(facing.getClockWise());player.setYRot(facing.toYRot());
            for(BlockPos pos:reversed?List.of(right,left):List.of(left,right)){
                level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
                ItemStack stack=new ItemStack(glass);var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false));
                check(((BlockItem)stack.getItem()).place(context).consumesAction(),"Double door places facing "+facing+", reverse order="+reversed);
            }
            check(level.getBlockState(left).getValue(DoorBlock.HINGE)==DoorHingeSide.LEFT&&level.getBlockState(right).getValue(DoorBlock.HINGE)==DoorHingeSide.RIGHT
                    &&level.getBlockState(left.above()).getValue(DoorBlock.HINGE)==DoorHingeSide.LEFT&&level.getBlockState(right.above()).getValue(DoorBlock.HINGE)==DoorHingeSide.RIGHT,"Both halves keep hinges outside the pair for "+facing+", reverse order="+reversed);
            for(BlockPos pos:List.of(left,right)){level.removeBlock(pos.above(),false);level.removeBlock(pos,false);}
        }
        for(Direction facing:Direction.Plane.HORIZONTAL)for(DoorHingeSide hinge:DoorHingeSide.values())for(boolean opened:new boolean[]{false,true}){
            var state=glass.defaultBlockState().setValue(DoorBlock.FACING,facing).setValue(DoorBlock.HINGE,hinge).setValue(DoorBlock.OPEN,opened);
            var expected=Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING,facing).setValue(DoorBlock.HINGE,hinge).setValue(DoorBlock.OPEN,opened).getCollisionShape(level,bottom);
            check(!Shapes.joinIsNotEmpty(state.getCollisionShape(level,bottom),expected,BooleanOp.NOT_SAME),"Glass door collision matches vanilla for "+facing+"/"+hinge+"/"+opened);
        }
        for(int shape=0;shape<MapTextures.SHAPES.length;shape++){boolean open=true;for(var blocks:MapTextures.BLOCKS)open&=!blocks.get(shape).get().defaultBlockState().canOcclude();check(open,"All Texture Kit "+MapTextures.SHAPES[shape]+" slots preserve transparent face visibility");}
        check(MapTextures.humanize("walls/red_bricks-old").equals("Red Bricks Old"),"Texture names are humanized without their folder path");
        String nested="architecture/interior/concrete/red_bricks";
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());byte[][] empty=new byte[MapTexturePixels.FACES.length][];Arrays.setAll(empty,i->new byte[0]);
        var packet=new me.cryo.zombierool.network.packet.S2CMapTextureSlotPacket(0,nested,"stone",empty);me.cryo.zombierool.network.packet.S2CMapTextureSlotPacket.encode(packet,buffer);
        check(me.cryo.zombierool.network.packet.S2CMapTextureSlotPacket.decode(buffer).name().equals(nested),"Folder texture paths longer than 32 characters survive network encoding");buffer.release();
        var root=MapTextures.customDir();Files.createDirectories(root.resolve("hotfix_test_source"));
        var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);image.setRGB(2,3,0x0055FF44);
        javax.imageio.ImageIO.write(image,"png",root.resolve("hotfix_test_source/red_bricks.png").toFile());
        javax.imageio.ImageIO.write(image,"png",root.resolve("hotfix_test_source/red_bricks_top.png").toFile());
        javax.imageio.ImageIO.write(image,"png",root.resolve("hotfix_test_source/red_bricks_other.png").toFile());
        var names=MapTextures.syncFolder(level);int slot=names.indexOf("hotfix_test_source/red_bricks");
        check(slot>=0,"Recursive Texture Kit scan finds nested textures");
        MapTextures.moveTexture(slot,"hotfix_test_target");names=MapTextures.syncFolder(level);
        check(names.get(slot).equals("hotfix_test_target/red_bricks")&&Files.exists(root.resolve("hotfix_test_target/red_bricks_top.png")),"Moving a texture keeps its slot and moves its separate faces");
        check(Files.exists(root.resolve("hotfix_test_source/red_bricks_other.png")),"Moving a texture preserves similarly named independent textures");
        var decoded=javax.imageio.ImageIO.read(root.resolve("hotfix_test_target/red_bricks.png").toFile());check(decoded.getRGB(2,3)>>>24==0,"Folder moves retain fully transparent PNG pixels");
        var menu=new MapTextures.TextureKitMenu(127,player.getInventory(),new ArrayList<>(names),new ArrayList<>(MapTextures.soundList()));
        player.containerMenu=menu;player.getInventory().clearContent();
        check(menu.slots.size()==36&&menu.clickMenuButton(player,slot*MapTextures.SHAPES.length+7)&&player.getInventory().contains(new ItemStack(MapTextures.BLOCKS[slot].head.get()))&&player.containerMenu==menu,"Head is granted immediately while the menu remains open");
        me.cryo.zombierool.hotfix.HotfixTextureFolders.applyRequest(player,new me.cryo.zombierool.hotfix.HotfixTextureFolders.Request(0,slot,"hotfix_test_target/nested"));
        check(Files.isDirectory(root.resolve("hotfix_test_target/nested")),"In-game folder request creates a nested destination");
        me.cryo.zombierool.hotfix.HotfixTextureFolders.applyRequest(player,new me.cryo.zombierool.hotfix.HotfixTextureFolders.Request(1,slot,"hotfix_test_target/nested"));
        check(Files.exists(root.resolve("hotfix_test_target/nested/red_bricks.png"))&&Files.exists(root.resolve("hotfix_test_target/nested/red_bricks_top.png"))&&!Files.exists(root.resolve("hotfix_test_target/red_bricks.png")),"Drag/drop server request moves the texture and its faces");
        var refreshed=(MapTextures.TextureKitMenu)player.containerMenu;
        check(refreshed.preferredSlot==slot&&refreshed.preferredFolder.equals("hotfix_test_target/nested")&&refreshed.names.get(slot).equals("hotfix_test_target/nested/red_bricks"),"Menu stays on the moved texture in its destination folder");
        me.cryo.zombierool.hotfix.HotfixTextureFolders.applyRequest(player,new me.cryo.zombierool.hotfix.HotfixTextureFolders.Request(1,slot,"/"));
        check(Files.exists(root.resolve("red_bricks.png"))&&Files.exists(root.resolve("red_bricks_top.png")),"Dropping on Up can move a texture back to the root");
        me.cryo.zombierool.hotfix.HotfixTextureFolders.applyRequest(player,new me.cryo.zombierool.hotfix.HotfixTextureFolders.Request(1,slot,"hotfix_test_target"));
        player.setGameMode(GameType.SURVIVAL);
        me.cryo.zombierool.hotfix.HotfixTextureFolders.applyRequest(player,new me.cryo.zombierool.hotfix.HotfixTextureFolders.Request(1,slot,"hotfix_test_source"));
        check(Files.exists(root.resolve("hotfix_test_target/red_bricks.png")),"Folder moves reject requests from non-creative players");player.setGameMode(GameType.CREATIVE);
        player.containerMenu=player.inventoryMenu;
        Files.delete(root.resolve("hotfix_test_source/red_bricks_other.png"));Files.delete(root.resolve("hotfix_test_target/red_bricks.png"));Files.delete(root.resolve("hotfix_test_target/red_bricks_top.png"));Files.delete(root.resolve("hotfix_test_target/nested"));Files.delete(root.resolve("hotfix_test_source"));Files.delete(root.resolve("hotfix_test_target"));MapTextures.syncFolder(level);
        for(int height:new int[]{32,64}){
            var skin=new BufferedImage(64,height,BufferedImage.TYPE_INT_ARGB);skin.setRGB(8,8,0xFF123456);skin.setRGB(40,8,0x8000FF00);
            var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(skin,"png",bytes);
            var faces=TextureAtlasImport.convertHead(bytes.toByteArray(),16);check(faces.size()==6&&faces.get("").getRGB(0,0)>>>24==255,"Head import handles a 64x"+height+" Minecraft skin and its overlay");
        }
        player.setGameMode(GameType.SURVIVAL);
    }
}
