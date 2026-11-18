package com.talviom.bodycamhorror.client.menu;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.client.Vhs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Клиент: окно игры по-нашему (если не выключено настройкой hud.customMenu).
 * <ul>
 *   <li>Заголовок окна BODYCAM HORROR вместо «Minecraft* 1.20.1 - Singleplayer». Minecraft сам
 *   переписывает заголовок при смене экрана, мира и открытии для сети, поэтому ставим свой заново,
 *   как только что-то из этого поменялось, и на всякий случай раз в секунду.</li>
 *   <li>Свой значок окна и панели задач: объектив камеры с точкой REC (рисует tools/assets/make_icon.py).</li>
 *   <li>Тёмная загрузка игры: включаем ванильную настройку «тёмный фон загрузки». Её читает и экран
 *   загрузки Forge при следующем запуске, так что вместо красного экрана с логотипом будет чёрный.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BhWindow {
    public static final String TITLE = "BODYCAM HORROR";
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

    private static boolean iconDone;
    private static boolean darkDone;
    private static int lastKey;
    private static int ticks;

    private BhWindow() {}

    @SubscribeEvent
    public static void onBhWindowClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        if (!iconDone) {
            iconDone = true;
            setIcon(mc);
        }
        if (!darkDone && mc.options != null) {
            darkDone = true;
            if (!mc.options.darkMojangStudiosBackground().get()) {
                mc.options.darkMojangStudiosBackground().set(true);
                mc.options.save();
                BodycamHorror.LOGGER.info("[BH window] dark loading background turned on");
            }
        }
        IntegratedServer host = mc.getSingleplayerServer();
        int key = Objects.hash(System.identityHashCode(mc.screen), System.identityHashCode(mc.level),
                host != null && host.isPublished(), mc.getCurrentServer() != null);
        if (key != lastKey || ++ticks >= 20) {
            if (key != lastKey && lastKey == 0) BodycamHorror.LOGGER.info("[BH window] title set to " + TITLE);
            lastKey = key;
            ticks = 0;
            mc.getWindow().setTitle(TITLE);
        }
    }

    /** Значок окна из assets/bodycamhorror/icons/icon_N.png. На macOS значок окна задаётся иначе, там пропускаем. */
    private static void setIcon(Minecraft mc) {
        if (Util.getPlatform() == Util.OS.OSX) return;
        List<ByteBuffer> pixels = new ArrayList<>();
        List<int[]> dims = new ArrayList<>();
        try {
            for (int size : ICON_SIZES) {
                Optional<Resource> res = mc.getResourceManager().getResource(Vhs.rl("icons/icon_" + size + ".png"));
                if (res.isEmpty()) continue;
                byte[] png;
                try (InputStream in = res.get().open()) {
                    png = in.readAllBytes();
                }
                ByteBuffer file = MemoryUtil.memAlloc(png.length);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    file.put(png).flip();
                    IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), ch = stack.mallocInt(1);
                    ByteBuffer rgba = STBImage.stbi_load_from_memory(file, w, h, ch, 4);
                    if (rgba == null) continue;
                    pixels.add(rgba);
                    dims.add(new int[]{w.get(0), h.get(0)});
                } finally {
                    MemoryUtil.memFree(file);
                }
            }
            if (pixels.isEmpty()) {
                BodycamHorror.LOGGER.warn("[BH window] no icon images found");
                return;
            }
            GLFWImage.Buffer images = GLFWImage.malloc(pixels.size());
            try {
                for (int i = 0; i < pixels.size(); i++) {
                    images.position(i).width(dims.get(i)[0]).height(dims.get(i)[1]).pixels(pixels.get(i));
                }
                GLFW.glfwSetWindowIcon(mc.getWindow().getWindow(), images.position(0));
            } finally {
                images.free();
            }
            BodycamHorror.LOGGER.info("[BH window] icon set ({} sizes)", pixels.size());
        } catch (Exception ex) {
            BodycamHorror.LOGGER.warn("[BH window] could not set the window icon: {}", ex.toString());
        } finally {
            pixels.forEach(STBImage::stbi_image_free);
        }
    }

    private static boolean enabled() {
        try {
            return BhConfig.CUSTOM_MENU.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
