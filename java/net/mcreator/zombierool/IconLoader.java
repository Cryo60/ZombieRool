package net.mcreator.zombierool;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class IconLoader {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            setWindowIcon();
        });
    }

    private static void setWindowIcon() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            long window = minecraft.getWindow().getWindow();

            // Load icons from resources
            InputStream icon16 = IconLoader.class.getResourceAsStream("/assets/zombierool/icons/icon_16x16.png");
            InputStream icon32 = IconLoader.class.getResourceAsStream("/assets/zombierool/icons/icon_32x32.png");

            if (icon16 == null || icon32 == null) {
                System.err.println("[ZombieRool] Icon files not found!");
                return;
            }

            // Load images
            NativeImage image16 = NativeImage.read(icon16);
            NativeImage image32 = NativeImage.read(icon32);

            // Create GLFW images
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLFWImage.Buffer icons = GLFWImage.malloc(2, stack);

                // Set 16x16 icon
                ByteBuffer buffer16 = MemoryUtil.memAlloc(image16.getWidth() * image16.getHeight() * 4);
                fillBuffer(image16, buffer16);
                icons.position(0)
                    .width(image16.getWidth())
                    .height(image16.getHeight())
                    .pixels(buffer16);

                // Set 32x32 icon
                ByteBuffer buffer32 = MemoryUtil.memAlloc(image32.getWidth() * image32.getHeight() * 4);
                fillBuffer(image32, buffer32);
                icons.position(1)
                    .width(image32.getWidth())
                    .height(image32.getHeight())
                    .pixels(buffer32);

                icons.position(0);
                GLFW.glfwSetWindowIcon(window, icons);

                // Free buffers
                MemoryUtil.memFree(buffer16);
                MemoryUtil.memFree(buffer32);
            }

            // Close images
            image16.close();
            image32.close();
            icon16.close();
            icon32.close();

            System.out.println("[ZombieRool] Custom window icon loaded successfully!");

        } catch (IOException e) {
            System.err.println("[ZombieRool] Error loading window icon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void fillBuffer(NativeImage image, ByteBuffer buffer) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getPixelRGBA(x, y);
                // RGBA to ABGR conversion for GLFW
                buffer.put((byte) ((color >> 0) & 0xFF));  // R
                buffer.put((byte) ((color >> 8) & 0xFF));  // G
                buffer.put((byte) ((color >> 16) & 0xFF)); // B
                buffer.put((byte) ((color >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();
    }
}