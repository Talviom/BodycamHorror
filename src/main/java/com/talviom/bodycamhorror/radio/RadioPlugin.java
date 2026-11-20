package com.talviom.bodycamhorror.radio;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Рация команды поверх Simple Voice Chat.
 * Во время матча живые союзники слышат друг друга на любом расстоянии, пока говорящий держит клавишу рации (Z, с 0.9).
 * У слушателя такой голос проходит через «радио»-фильтр (RadioVoiceFilter, на клиенте).
 * Обычный голос по близости остаётся: врага рядом по-прежнему слышно.
 * Шёпот (клавиша шёпота в Voice Chat) в рацию не идёт.
 * Мёртвые в рацию не говорят. Во время помех часть слов пропадает.
 */
@ForgeVoicechatPlugin
public class RadioPlugin implements VoicechatPlugin {
    /** Доля пакетов, которая теряется во время помех. */
    private static final double JAM_LOSS = 0.65;

    @Override
    public String getPluginId() {
        return "bodycamhorror";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        // 0.9: на клиенте голос из рации (статичный звук без места в мире) звучит как рация: узкая полоса, хрип, шипение.
        registration.registerEvent(de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent.StaticSound.class,
                e -> e.setRawAudio(RadioVoiceFilter.apply(e.getRawAudio())));
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || event.getPacket().isWhispering()) return;

        Map<UUID, RadioState.Entry> alive = RadioState.alive();
        if (alive.isEmpty()) return;
        UUID senderId = sender.getPlayer().getUuid();
        RadioState.Entry me = alive.get(senderId);
        if (me == null) return;
        // 0.9: в рацию идёт только голос того, кто держит клавишу рации (Z). Без неё голос слышно лишь рядом.
        if (!RadioState.isTalking(senderId)) return;

        boolean senderJammed = RadioState.isJammed(senderId);
        if (senderJammed && ThreadLocalRandom.current().nextDouble() < JAM_LOSS) return;

        VoicechatServerApi api = event.getVoicechat();
        double range = api.getBroadcastRange();
        double rangeSq = range * range;

        for (Map.Entry<UUID, RadioState.Entry> e : alive.entrySet()) {
            if (e.getKey().equals(senderId) || e.getValue().team() != me.team()) continue;
            // Тот, кто рядом, и так слышит обычный голос. Рация нужна только на расстоянии.
            if (e.getValue().pos().distanceToSqr(me.pos()) <= rangeSq) continue;
            // Помехи у слушателя: он теряет часть слов, остальные слышат всё.
            if (!senderJammed && RadioState.isJammed(e.getKey()) && ThreadLocalRandom.current().nextDouble() < JAM_LOSS) continue;
            VoicechatConnection receiver = api.getConnectionOf(e.getKey());
            if (receiver == null) continue;
            api.sendStaticSoundPacketTo(receiver, event.getPacket().staticSoundPacketBuilder().build());
        }
    }
}
