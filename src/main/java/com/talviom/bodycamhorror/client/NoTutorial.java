package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: без ванильного обучения. У нового игрока Minecraft показывает белую плашку
 * «Move with W, A, S and D / Jump with Space» (на русской раскладке с буквами Ц, Ф, Ы, В),
 * она весь матч висит поверх кадра бодикамеры. Выключаем обучение один раз и сохраняем настройки.
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
}
