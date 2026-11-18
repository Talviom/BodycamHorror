package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: без ванильного обучения. У нового игрока Minecraft показывает белую плашку
 * «Move with W, A, S and D / Jump with Space» (на русской раскладке с буквами Ц, Ф, Ы, В),
 * она весь матч висит поверх кадра бодикамеры. Выключаем обучение один раз и сохраняем настройки.
 *
 * Там же убираем синюю ванильную плашку «Chat messages can't be verified»: на сервере без входа
 * в аккаунт (как у нас через «Открыть для сети») она выскакивает у каждого друга при входе.
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NoTutorial {
    private NoTutorial() {}

    @SubscribeEvent
    public static void onNoTutorialClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.tutorialStep == TutorialSteps.NONE) return;
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.save();
        BodycamHorror.LOGGER.info("[BH ui] vanilla tutorial hints turned off");
    }

    /**
     * Bleeding Mod при входе в мир пишет в чат «доступна новая версия» со ссылкой, и выключить это в его настройках нельзя,
     * а Yuushya пишет объявление про бесплатность своих модов. Такие строки до игрока не доходят.
     */
    @SubscribeEvent
    public static void onNoUpdateSpamChat(net.minecraftforge.client.event.ClientChatReceivedEvent e) {
        if (e.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith("bleedingmod.update")) {
            e.setCanceled(true);
            BodycamHorror.LOGGER.info("[BH ui] mod update notice hidden ({})", t.getKey());
            return;
        }
        // Yuushya при каждом входе пишет общее объявление «все работы Yuushya бесплатны, купившим вернуть деньги».
        String text = e.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
        if (text.contains("yuushya") && (text.contains("refund") || text.contains("free")
                || text.contains("возврат") || text.contains("бесплатн"))) {
            e.setCanceled(true);
            BodycamHorror.LOGGER.info("[BH ui] Yuushya notice hidden");
        }
    }

    @SubscribeEvent
    public static void onNoChatToastClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // В 1.20.1 нет SystemToast.forceHide: если плашка про чат появилась, убираем всплывашки целиком
        // (других всплывашек в нашей игре нет: рецепты и достижения в бодикаме не нужны).
        if (mc.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) {
            mc.getToasts().clear();
            BodycamHorror.LOGGER.info("[BH ui] chat verification toast hidden");
            return;
        }
        // Ванильные «Открыты новые рецепты!» и достижения: в полной сборке они всплывали поверх REC при старте матча.
        Object none = net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN;
        if (mc.getToasts().getToast(net.minecraft.client.gui.components.toasts.RecipeToast.class, none) != null
                || mc.getToasts().getToast(net.minecraft.client.gui.components.toasts.AdvancementToast.class, none) != null) {
            mc.getToasts().clear();
            BodycamHorror.LOGGER.info("[BH ui] recipe/advancement toast hidden");
        }
    }
}
