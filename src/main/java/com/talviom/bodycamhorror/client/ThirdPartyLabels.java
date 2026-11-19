package com.talviom.bodycamhorror.client;

import java.util.Map;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: кнопки чужих модов, у которых надпись вшита в код (не через lang-файл), поэтому перевести их
 * через resources/overrides нельзя. После открытия экрана такая надпись заменяется нашим ключом из lang.
 * Better Blood Overlay: кнопка «Blood...» в настройках.
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThirdPartyLabels {
    private ThirdPartyLabels() {}

    private static final Map<String, String> LITERALS = Map.of(
            "Blood...", "bh.thirdparty.blood_settings");

    @SubscribeEvent
    public static void onThirdPartyLabelsInit(ScreenEvent.Init.Post e) {
        for (var child : e.getListenersList()) {
            if (!(child instanceof AbstractButton b)) continue;
            if (!(b.getMessage().getContents() instanceof LiteralContents lit)) continue;
            String key = LITERALS.get(lit.text());
            if (key != null) b.setMessage(Component.translatable(key));
        }
    }
}
