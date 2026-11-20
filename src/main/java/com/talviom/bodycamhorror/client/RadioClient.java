package com.talviom.bodycamhorror.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.RadioTalkPacket;
import com.talviom.bodycamhorror.radio.RadioVoiceFilter;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Клиент, 0.9 (пункт 14): рация на отдельной клавише Z.
 *
 * Держишь Z и говоришь: голос уходит союзникам на любом расстоянии (сервер: RadioPlugin, RadioState),
 * у них он звучит через радио-фильтр. Обычный голос рядом слышат все, в том числе враги, а рацию только своя команда.
 * Нажатие и отпускание щёлкают тангентой с шипением у тебя и у союзников; на экране значок «РАЦИЯ: ПЕРЕДАЧА»,
 * у слушающих «РАЦИЯ: ПРИЁМ».
 *
 * Голос в Simple Voice Chat должен идти: в режиме «активация голосом» достаточно держать Z, в режиме
 * «нажми и говори» нужно держать и его клавишу. Сборка для друзей ставит активацию голосом.
 * Один раз убирает с Z «осмотр и обвесы» TACZ (в матче не нужен), если он там стоит.
 */
public final class RadioClient {
    public static final KeyMapping RADIO = new KeyMapping(
            "key.bodycamhorror.radio", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.bodycamhorror");
    private static final String MARKER = "bodycamhorror-radiokey09.done";

    private static boolean talking;
    private static long cueUntilMs;
    private static boolean checkedKeys;

    private RadioClient() {}

    /** Союзник нажал или отпустил рацию. */
    public static void onTeammateCue(boolean on) {
        play(on ? ModSounds.RADIO_ON.get() : ModSounds.RADIO_OFF.get(), on ? 0.55f : 0.5f);
        cueUntilMs = on ? System.currentTimeMillis() + 60_000 : System.currentTimeMillis() + 300;
    }

    public static boolean talking() {
        return talking;
    }

    private static void play(SoundEvent sound, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 0.95f + (float) Math.random() * 0.1f, volume));
    }

    private static void tick(Minecraft mc) {
        if (!checkedKeys) freeZ(mc);
        boolean can = mc.player != null && ClientState.inMatch && mc.player.isAlive() && !mc.player.isSpectator();
        boolean want = can && mc.screen == null && RADIO.isDown();
        // Открыл чат или инвентарь, отпустил Z или умер: эфир кончился.
        if (want != talking) {
            talking = want;
            BhNetwork.CHANNEL.sendToServer(new RadioTalkPacket(want));
            play(want ? ModSounds.RADIO_ON.get() : ModSounds.RADIO_OFF.get(), 0.6f);
        }
        while (RADIO.consumeClick()) { /* нажатия считаем по isDown */ }
        if (!can) cueUntilMs = 0;
    }

    /** Один раз: Z освобождается от «осмотра и обвесов» TACZ, если у игрока он там стоит. */
    private static void freeZ(Minecraft mc) {
        checkedKeys = true;
        Path marker = FMLPaths.CONFIGDIR.get().resolve(MARKER);
        if (Files.exists(marker)) return;
        boolean changed = false;
        for (KeyMapping k : mc.options.keyMappings) {
            if (k == RADIO || !"key.tacz.refit.desc".equals(k.getName())) continue;
            InputConstants.Key key = k.getKey();
            if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() == GLFW.GLFW_KEY_Z) {
                k.setKey(InputConstants.UNKNOWN);
                changed = true;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
            BodycamHorror.LOGGER.info("0.9 keys: TACZ refit unbound from Z, Z is the radio");
        }
        try {
            Files.writeString(marker, "radio on Z (0.9)\n");
        } catch (Exception e) {
            BodycamHorror.LOGGER.warn("Could not write {}: {}", MARKER, e.toString());
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc, int width) {
        if (mc.player == null || mc.options.hideGui || !ClientState.inMatch) return;
        long now = System.currentTimeMillis();
        boolean rx = !talking && (now < cueUntilMs || RadioVoiceFilter.msSinceHeard() < 350);
        if (!talking && !rx) return;
        String text = Vhs.tr(talking ? "bh.hud.radio_tx" : "bh.hud.radio_rx");
        int x = width - 8 - Vhs.width(text);
        int y = 8 + 36;
        // Неровное мерцание, как у слабого сигнала; точка эфира того же засохшего красного, что и REC.
        float flick = 0.75f + 0.25f * (float) Math.sin(now / 37.0) * (float) Math.sin(now / 91.0);
        int alpha = (int) (0xE0 * flick) << 24;
        if (talking && (now / 400) % 2 == 0) Vhs.plain(g, "●", x - Vhs.width("● "), y, 0xFF000000 | Vhs.DRIED_BLOOD);
        Vhs.ghost(g, Vhs.text(text), x, y, alpha | (talking ? Vhs.BONE : Vhs.ASH), 1f, 0.3f);
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class RadioModEvents {
        @SubscribeEvent
        public static void registerRadioKey(RegisterKeyMappingsEvent e) {
            e.register(RADIO);
        }

        @SubscribeEvent
        public static void registerRadioOverlay(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("bodycam_radio", (gui, g, partialTick, width, height) -> draw(g, Minecraft.getInstance(), width));
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class RadioForgeEvents {
        @SubscribeEvent
        public static void onRadioClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            tick(Minecraft.getInstance());
        }
    }
}
