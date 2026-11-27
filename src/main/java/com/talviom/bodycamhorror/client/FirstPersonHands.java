package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Клиент: тело от мода firstperson при взгляде вниз.
 *
 * В режиме vanillaHandsMode=ALL firstperson прячет руки тела и рисует отдельную ванильную руку с оружием,
 * поэтому глядя в пол видно туловище без рук и парящую руку. Его встроенный dynamicMode убирает парящую
 * руку при наклоне взгляда больше 30 градусов и отдаёт оружие рукам тела. Включаем его при каждом
 * запуске, чтобы у всех игроков было одинаково без ручной правки config/firstperson.json.
 * firstperson не зависимость сборки, поэтому через отражение; если мода нет, ничего не делаем.
 */
public final class FirstPersonHands {
    private static boolean done;

    private FirstPersonHands() {}

    private static void apply() {
        done = true;
        if (!ModList.get().isLoaded("firstperson")) return;
        try {
            Class<?> core = Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore");
            Object instance = core.getField("instance").get(null);
            if (instance == null) { done = false; return; }
            Object config = core.getMethod("getConfig").invoke(instance);
            if (config == null) { done = false; return; }
            Field dynamic = config.getClass().getField("dynamicMode");
            if (dynamic.getBoolean(config)) return;
            dynamic.setBoolean(config, true);
            Method write = core.getMethod("writeSettings");
            write.invoke(instance);
            BodycamHorror.LOGGER.info("firstperson: dynamicMode on (body arms hold the item when looking down)");
        } catch (Throwable t) {
            BodycamHorror.LOGGER.warn("firstperson: could not enable dynamicMode: {}", t.toString());
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class FirstPersonHandsEvents {
        @SubscribeEvent
        public static void onFirstPersonHandsClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || done) return;
            apply();
        }
    }
}
