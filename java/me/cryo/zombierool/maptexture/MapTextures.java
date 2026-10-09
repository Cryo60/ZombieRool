package me.cryo.zombierool.maptexture;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.registry.ZRBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.SimpleMenuProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Thirty-two texture slots shipped inside the world folder. Each slot is a full
 * block plus stairs, slab, fence, wall and pane. Players who download the map
 * get the PNG files with it; the client builds a resource pack from that folder.
 */
@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID)
public final class MapTextures {
    public static final int SLOTS = 32;
    public static final String[] SHAPES = {"cube", "stairs", "slab", "fence", "wall", "pane", "door"};
    public static final String[] SOUND_IDS = {
            "stone", "wood", "gravel", "grass", "metal", "glass", "wool", "sand",
            "snow", "mud", "deepslate", "netherrack", "amethyst", "slime", "honey",
            "moss", "sculk", "cherry_wood", "nether_wood", "bamboo", "copper", "tuff",
            "basalt", "lantern", "anvil", "wet_grass"
    };
    public static final int SOUND_BUTTON = 1000;

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);
    public static final RegistryObject<MenuType<TextureKitMenu>> MENU = MENUS.register("texture_kit", () -> IForgeMenuType.create(TextureKitMenu::new));
    public static RegistryObject<Item> KIT;

    public static final SlotBlocks[] BLOCKS = new SlotBlocks[SLOTS];
    private static Path worldRoot;
    private static String[] cached = new String[SLOTS];
    private static final String[] cachedSounds = new String[SLOTS];

    static {
        Arrays.fill(cached, "");
        Arrays.fill(cachedSounds, "stone");
    }

    private MapTextures() {}

    public static final class SlotBlocks {
        public final RegistryObject<Block> cube;
        public final RegistryObject<Block> stairs;
        public final RegistryObject<Block> slab;
        public final RegistryObject<Block> fence;
        public final RegistryObject<Block> wall;
        public final RegistryObject<Block> pane;
        public final RegistryObject<Block> door;

        SlotBlocks(RegistryObject<Block> cube, RegistryObject<Block> stairs, RegistryObject<Block> slab,
                   RegistryObject<Block> fence, RegistryObject<Block> wall, RegistryObject<Block> pane, RegistryObject<Block> door) {
            this.cube = cube;
            this.stairs = stairs;
            this.slab = slab;
            this.fence = fence;
            this.wall = wall;
            this.pane = pane;
            this.door = door;
        }

        public RegistryObject<Block> get(int shape) {
            return switch (shape) {
                case 1 -> stairs;
                case 2 -> slab;
                case 3 -> fence;
                case 4 -> wall;
                case 5 -> pane;
                case 6 -> door;
                default -> cube;
            };
        }
    }

    public static void registerBlocks() {
        for (int i = 0; i < SLOTS; i++) {
            final int slot = i;
            String name = "maptex_" + i;
            RegistryObject<Block> cube = register(name, () -> new TexBlock(slot, false));
            RegistryObject<Block> stairs = register(name + "_stairs", () -> new TexStairs(slot, () -> cube.get().defaultBlockState()));
            RegistryObject<Block> slab = register(name + "_slab", () -> new TexSlab(slot));
            RegistryObject<Block> fence = register(name + "_fence", () -> new TexFence(slot));
            RegistryObject<Block> wall = register(name + "_wall", () -> new TexWall(slot));
            RegistryObject<Block> pane = register(name + "_pane", () -> new TexPane(slot));
            RegistryObject<Block> door = register(name + "_door", () -> new net.minecraft.world.level.block.DoorBlock(props(true).sound(SoundType.METAL), net.minecraft.world.level.block.state.properties.BlockSetType.IRON));
            BLOCKS[i] = new SlotBlocks(cube, stairs, slab, fence, wall, pane, door);
            ZRBlocks.CUTOUT_BLOCKS.add(door);
            ZRBlocks.CUTOUT_BLOCKS.add(pane);
            ZRBlocks.CUTOUT_BLOCKS.add(fence);
        }
        KIT = ZRBlocks.ITEMS.register("texture_kit", TextureKitItem::new);
        ZRBlocks.ITEM_IDS.add(new ResourceLocation(ZombieroolMod.MODID, "texture_kit"));
    }

    private static BlockBehaviour.Properties props(boolean open) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().sound(SoundType.STONE).strength(2.0f, 3600000.0f);
        if (open) properties = properties.noOcclusion().isViewBlocking((state, level, pos) -> false);
        return properties;
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = ZRBlocks.BLOCKS.register(name, factory);
        ZRBlocks.ITEMS.register(name, () -> new MapTextureBlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static String[] slots() {
        return cached.clone();
    }

    public static String textureName(int slot) {
        if (slot < 0 || slot >= SLOTS) return "";
        return cached[slot] == null ? "" : cached[slot];
    }

    public static void applyClientSlot(int slot,String name,String sound){if(slot<0||slot>=SLOTS)return;cached[slot]=sanitize(name);cachedSounds[slot]=isSound(sound)?sound:"stone";}

    public static String soundId(int slot) {
        if (slot < 0 || slot >= SLOTS || cachedSounds[slot] == null || cachedSounds[slot].isEmpty()) return "stone";
        return cachedSounds[slot];
    }

    public static List<String> soundList() {
        return new ArrayList<>(Arrays.asList(cachedSounds));
    }

    public static SoundType soundOf(int slot) {
        return switch (soundId(slot)) {
            case "wood", "cherry_wood", "nether_wood", "bamboo" -> wood(soundId(slot));
            case "gravel" -> SoundType.GRAVEL;
            case "grass" -> SoundType.GRASS;
            case "metal", "copper", "anvil", "lantern" -> metal(soundId(slot));
            case "glass" -> SoundType.GLASS;
            case "wool" -> SoundType.WOOL;
            case "sand" -> SoundType.SAND;
            case "snow" -> SoundType.SNOW;
            case "mud" -> SoundType.MUD;
            case "deepslate" -> SoundType.DEEPSLATE;
            case "netherrack" -> SoundType.NETHERRACK;
            case "amethyst" -> SoundType.AMETHYST;
            case "slime" -> SoundType.SLIME_BLOCK;
            case "honey" -> SoundType.HONEY_BLOCK;
            case "moss" -> SoundType.MOSS;
            case "sculk" -> SoundType.SCULK;
            case "tuff" -> SoundType.TUFF;
            case "basalt" -> SoundType.BASALT;
            case "wet_grass" -> SoundType.WET_GRASS;
            default -> SoundType.STONE;
        };
    }

    private static SoundType wood(String id) {
        return switch (id) {
            case "cherry_wood" -> SoundType.CHERRY_WOOD;
            case "nether_wood" -> SoundType.NETHER_WOOD;
            case "bamboo" -> SoundType.BAMBOO_WOOD;
            default -> SoundType.WOOD;
        };
    }

    private static SoundType metal(String id) {
        return switch (id) {
            case "copper" -> SoundType.COPPER;
            case "anvil" -> SoundType.ANVIL;
            case "lantern" -> SoundType.LANTERN;
            default -> SoundType.METAL;
        };
    }

    public static void setSound(int slot, String id) {
        if (slot < 0 || slot >= SLOTS || !isSound(id)) return;
        cachedSounds[slot] = id;
        if (worldRoot == null) return;
        try {
            writeManifest(worldRoot.resolve("zombierool").resolve("custom_blocks").resolve("manifest.txt"), cached, cachedSounds);
        } catch (IOException ignored) {}
    }

    public static boolean isSound(String id) {
        for (String preset : SOUND_IDS) if (preset.equals(id)) return true;
        return false;
    }

    public static Path customDir() {
        return worldRoot == null ? null : worldRoot.resolve("zombierool").resolve("custom_blocks");
    }

    /** Assigns new PNG files to free slots and returns the 32 names (empty if unused). */
    public static List<String> syncFolder(ServerLevel level) {
        worldRoot = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path dir = worldRoot.resolve("zombierool").resolve("custom_blocks");
        try {
            Files.createDirectories(dir);
            Files.createDirectories(dir.resolve("import"));
            Path readme = dir.resolve("readme.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        Drop PNG files here.
                        Name: lowercase letters, digits and _. Example: bricks.png
                        Optional faces: bricks_top.png, bricks_side.png, bricks_bottom.png
                        Open the Texture Kit (deco tab, creative) to get the block, stairs, slab, fence, wall, pane and iron door.
                        Import PNG atlases from custom_blocks/import in the pixel editor.
                        Formats: square face, 4x3 cube net, or 1x2 door (upper then lower).
                        Textures update directly in the atlas, without reloading resource packs.
                        Pick a vanilla sound in that screen. It applies to every shape of that texture.
                        This folder ships with the map. Players who download it see the textures.
                        32 textures maximum.
                        """, StandardCharsets.UTF_8);
            }
            cached = readSlots(worldRoot);
            for (int i = 0; i < SLOTS; i++) {
                if (cached[i].isEmpty()) continue;
                if (!Files.exists(dir.resolve(cached[i] + ".png"))) {
                    cached[i] = "";
                    cachedSounds[i] = "stone";
                }
            }
            Set<String> used = new HashSet<>();
            for (String name : cached) if (!name.isEmpty()) used.add(name);
            List<Path> pngs = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
                for (Path png : stream) pngs.add(png);
            }
            pngs.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()));
            for (Path png : pngs) {
                String stem = png.getFileName().toString();
                stem = stem.substring(0, stem.length() - 4);
                String id = sanitize(stem);
                if (id.isEmpty() || used.contains(id) || faceOfExisting(id, dir, used)) continue;
                int free = freeSlot(cached);
                if (free < 0) break;
                if (!id.equals(stem)) {
                    Path target = dir.resolve(id + ".png");
                    if (!Files.exists(target)) Files.copy(png, target);
                }
                cached[free] = id;
                if (cachedSounds[free] == null || cachedSounds[free].isEmpty()) cachedSounds[free] = "stone";
                used.add(id);
            }
            writeManifest(dir.resolve("manifest.txt"), cached, cachedSounds);
        } catch (IOException ignored) {
            cached = readSlots(worldRoot);
        }
        return Arrays.asList(cached.clone());
    }

    public static String[] readSlots(Path root) {
        String[] slots = new String[SLOTS];
        Arrays.fill(slots, "");
        Arrays.fill(cachedSounds, "stone");
        if (root == null) return slots;
        Path manifest = root.resolve("zombierool").resolve("custom_blocks").resolve("manifest.txt");
        if (!Files.exists(manifest)) return slots;
        try {
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 2) continue;
                int index = Integer.parseInt(parts[0]);
                String name = sanitize(parts[1]);
                if (index < 0 || index >= SLOTS || name.isEmpty()) continue;
                slots[index] = name;
                if (parts.length >= 3 && isSound(parts[2])) cachedSounds[index] = parts[2];
            }
        } catch (Exception ignored) {}
        return slots;
    }

    private static void writeManifest(Path manifest, String[] slots, String[] sounds) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].isEmpty()) continue;
            String sound = sounds != null && i < sounds.length && isSound(sounds[i]) ? sounds[i] : "stone";
            builder.append(i).append(' ').append(slots[i]).append(' ').append(sound).append('\n');
        }
        String text = builder.toString();
        if (Files.exists(manifest) && text.equals(Files.readString(manifest, StandardCharsets.UTF_8))) return;
        Files.writeString(manifest, text, StandardCharsets.UTF_8);
    }

    private static int freeSlot(String[] slots) {
        for (int i = 0; i < slots.length; i++) if (slots[i].isEmpty()) return i;
        return -1;
    }

    private static boolean faceOfExisting(String id, Path dir, Set<String> used) {
        for (String suffix : new String[]{"_top", "_side", "_bottom", "_upper", "_lower", "_north", "_south", "_east", "_west"}) {
            if (!id.endsWith(suffix)) continue;
            String base = id.substring(0, id.length() - suffix.length());
            if (used.contains(base) || Files.exists(dir.resolve(base + ".png"))) return true;
        }
        return false;
    }

    public static String sanitize(String raw) {
        String cleaned = raw.toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9_]", "");
        if (cleaned.length() > 32) cleaned = cleaned.substring(0, 32);
        return cleaned;
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            worldRoot = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            cached = readSlots(worldRoot);
        }
    }

    public static class TextureKitItem extends Item {
        public TextureKitItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.isCreative()) {
                    player.displayClientMessage(Component.translatable("message.zombierool.maptex.creative"), true);
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
                List<String> names = syncFolder(serverPlayer.serverLevel());
                List<String> sounds = soundList();
                NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                        (id, inv, p) -> new TextureKitMenu(id, inv, names, sounds),
                        Component.translatable("item.zombierool.texture_kit")
                ), buf -> {
                    buf.writeVarInt(names.size());
                    for (String name : names) buf.writeUtf(name == null ? "" : name);
                    buf.writeVarInt(sounds.size());
                    for (String sound : sounds) buf.writeUtf(sound == null ? "stone" : sound);
                });
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    }

    public static class MapTextureBlockItem extends BlockItem {
        public MapTextureBlockItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(this);
            if (id == null) return super.getName(stack);
            String path = id.getPath();
            int shape = 0;
            int slot = -1;
            if (path.startsWith("maptex_")) {
                String rest = path.substring("maptex_".length());
                int cut = rest.indexOf('_');
                String number = cut < 0 ? rest : rest.substring(0, cut);
                String suffix = cut < 0 ? "" : rest.substring(cut + 1);
                try { slot = Integer.parseInt(number); } catch (NumberFormatException ignored) {}
                shape = switch (suffix) {
                    case "stairs" -> 1;
                    case "slab" -> 2;
                    case "fence" -> 3;
                    case "wall" -> 4;
                    case "pane" -> 5;
                    case "door" -> 6;
                    default -> 0;
                };
            }
            String texture = textureName(slot);
            if (texture.isEmpty()) return Component.translatable("block.zombierool.maptex");
            return Component.literal(texture + " (" + Component.translatable("shape.zombierool." + SHAPES[shape]).getString() + ")");
        }
    }

    public static class TexBlock extends Block {
        private final int slot;
        TexBlock(int slot, boolean open) { super(props(open)); this.slot = slot; }
        @Override public SoundType getSoundType(BlockState state) { return soundOf(slot); }
    }

    public static final class TexStairs extends StairBlock {
        private final int slot;
        TexStairs(int slot, java.util.function.Supplier<BlockState> base) { super(base, props(false)); this.slot = slot; }
        @Override public SoundType getSoundType(BlockState state) { return soundOf(slot); }
    }

    public static final class TexSlab extends SlabBlock {
        private final int slot;
        TexSlab(int slot) { super(props(false)); this.slot = slot; }
        @Override public SoundType getSoundType(BlockState state) { return soundOf(slot); }
    }

    public static final class TexFence extends FenceBlock {
        private final int slot;
        TexFence(int slot) { super(props(true)); this.slot = slot; }
        @Override public SoundType getSoundType(BlockState state) { return soundOf(slot); }
    }

    public static final class TexWall extends WallBlock {
        private final int slot;
        TexWall(int slot) { super(props(true)); this.slot = slot; }
        @Override public SoundType getSoundType(BlockState state) { return soundOf(slot); }
    }

    public static final class TexPane extends IronBarsBlock {
        private final int slot;
        TexPane(int slot) { super(props(true)); this.slot = slot; }
        @Override public SoundType getSoundType(BlockState state) { return soundOf(slot); }
    }

    public static class TextureKitMenu extends AbstractContainerMenu {
        public final List<String> names;
        public final List<String> sounds;

        public TextureKitMenu(int id, Inventory inventory, List<String> names, List<String> sounds) {
            super(MENU.get(), id);
            this.names = names;
            this.sounds = sounds;
        }

        public TextureKitMenu(int id, Inventory inventory, net.minecraft.network.FriendlyByteBuf buf) {
            super(MENU.get(), id);
            int count = buf.readVarInt();
            this.names = new ArrayList<>();
            for (int i = 0; i < count; i++) names.add(buf.readUtf());
            this.sounds = new ArrayList<>();
            if (buf.isReadable()) {
                int soundCount = buf.readVarInt();
                for (int i = 0; i < soundCount; i++) sounds.add(buf.readUtf());
            }
        }

        @Override
        public boolean clickMenuButton(Player player, int id) {
            if (!player.isCreative()) return false;
            if (id >= SOUND_BUTTON) {
                int packed = id - SOUND_BUTTON;
                int slot = packed / SOUND_IDS.length;
                int sound = packed % SOUND_IDS.length;
                if (slot < 0 || slot >= SLOTS) return false;
                setSound(slot, SOUND_IDS[sound]);
                if (slot < sounds.size()) sounds.set(slot, SOUND_IDS[sound]);
                return true;
            }
            int shape = id % SHAPES.length;
            int slot = id / SHAPES.length;
            if (slot < 0 || slot >= names.size() || slot >= SLOTS) return false;
            if (names.get(slot) == null || names.get(slot).isEmpty()) return false;
            ItemStack stack = new ItemStack(BLOCKS[slot].get(shape).get(), 64);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            return true;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return player.isCreative();
        }
    }
}
