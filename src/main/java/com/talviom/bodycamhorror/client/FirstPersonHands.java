package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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
 * запуске, чтобы у всех игроков было одинаково без ручной правки config/firstperson.json. Режим OFF
 * (по умолчанию у нового firstperson.json) переводим в ALL: иначе вид оружия TACZ пропал бы всегда.
 *
 * Оружие TACZ рисуется своим слушателем RenderHandEvent, а firstperson при взгляде вниз прячет только
 * ванильную руку своим миксином, поэтому пистолет от первого лица продолжал висеть в кадре рядом с
 * пистолетом в руках тела. Когда предметы держит тело (см. bodyHoldsItems), отменяем RenderHandEvent
 * с наивысшим приоритетом, до того как TACZ его получит (TACZ отменённые события не принимает).
 *
 * firstperson не зависимость сборки, поэтому через отражение; если мода нет, ничего не делаем.
 */
public final class FirstPersonHands {
    private static boolean done;

    // Кэш отражения для проверки на каждом кадре; hideReady=false, пока firstperson не готов.
    private static boolean hideReady;
    private static boolean hideBroken;
    private static Object fpInstance;
    private static Method fpIsEnabled;
    private static Method fpGetLogicHandler;
    private static Method fpShowVanillaHands;
    private static Method fpLookingDown;

    private FirstPersonHands() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void apply() {
        done = true;
        if (!ModList.get().isLoaded("firstperson")) return;
        try {
            Class<?> core = Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore");
            Object instance = core.getField("instance").get(null);
            if (instance == null) { done = false; return; }
            Object config = core.getMethod("getConfig").invoke(instance);
            if (config == null) { done = false; return; }
            prepareHide(core, instance);
            boolean changed = false;
            Field hands = config.getClass().getField("vanillaHandsMode");
            Object mode = hands.get(config);
            if (mode != null && "OFF".equals(((Enum<?>) mode).name())) {
                hands.set(config, Enum.valueOf((Class) hands.getType(), "ALL"));
                changed = true;
            }
            Field dynamic = config.getClass().getField("dynamicMode");
            if (!dynamic.getBoolean(config)) {
                dynamic.setBoolean(config, true);
                changed = true;
            }
            if (!changed) return;
            Method write = core.getMethod("writeSettings");
            write.invoke(instance);
            BodycamHorror.LOGGER.info("firstperson: dynamicMode on (body arms hold the item when looking down)");
        } catch (Throwable t) {
            BodycamHorror.LOGGER.warn("firstperson: could not enable dynamicMode: {}", t.toString());
        }
    }

    private static void prepareHide(Class<?> core, Object instance) {
        try {
            fpIsEnabled = core.getMethod("isEnabled");
            fpGetLogicHandler = core.getMethod("getLogicHandler");
            Class<?> logic = Class.forName("dev.tr7zw.firstperson.LogicHandler");
            fpShowVanillaHands = logic.getMethod("showVanillaHands");
            fpLookingDown = logic.getMethod("lookingDown");
            fpInstance = instance;
            hideReady = true;
        } catch (Throwable t) {
            hideBroken = true;
            BodycamHorror.LOGGER.warn("firstperson: could not hook first-person hands: {}", t.toString());
        }
    }

    /**
     * Предметы сейчас в руках тела: firstperson не показывает ванильные руки вообще, либо (dynamicMode)
     * игрок смотрит вниз больше чем на 30 градусов. Во втором случае firstperson прячет ванильную руку
     * своим миксином, а не через RenderHandEvent, поэтому TACZ это событие всё равно получал.
     */
    private static boolean bodyHoldsItems() {
        if (!hideReady || hideBroken) return false;
        try {
            if (!(Boolean) fpIsEnabled.invoke(fpInstance)) return false;
            Object logic = fpGetLogicHandler.invoke(fpInstance);
            if (logic == null) return false;
            return !(Boolean) fpShowVanillaHands.invoke(logic) || (Boolean) fpLookingDown.invoke(logic);
        } catch (Throwable t) {
            hideBroken = true;
            BodycamHorror.LOGGER.warn("firstperson: first-person hands check failed, leaving hands as they are: {}", t.toString());
            return false;
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class FirstPersonHandsEvents {
        @SubscribeEvent
        public static void onFirstPersonHandsClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || done) return;
            apply();
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onFirstPersonHandsRenderHand(RenderHandEvent e) {
            if (bodyHoldsItems()) e.setCanceled(true);
        }
    }
}
