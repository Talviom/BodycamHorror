package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;

/**
 * Клиент: в начале раунда (заставка ROUND_START) стираем всё, что чужие моды рисуют только у игрока:
 * лужи и брызги крови и раны (Better Blood Overlay), кровь и дырки от пуль (TaCZ Tactical Breaching),
 * тела и оторванные части (Ragdollified). Серверную часть уборки делает match/RoundCleanup.
 * Через рефлексию: если мода нет, он просто пропускается.
 */
public final class RoundCleanupClient {
    private static final String[] CLEARS = {
            "tailorworks.betterbloodoverlay_1_20_1.BloodDecalManager",
            "tailorworks.betterbloodoverlay_1_20_1.WoundStore",
            "com.victoriomods.taczballisticbreaching.BulletDecalManager",
            "com.victoriomods.taczballisticbreaching.ClientBloodHitFxHandler",
            "com.raiiiden.ragdollified.client.ClientRagdollManager",
            "com.raiiiden.ragdollified.client.ClientDetachedLimbManager",
    };

    private RoundCleanupClient() {}

    public static void run() {
        int done = 0;
        for (String cls : CLEARS) {
            try {
                Class.forName(cls).getMethod("clear").invoke(null);
                done++;
            } catch (ClassNotFoundException ex) {
                // мода нет в сборке
            } catch (Throwable ex) {
                BodycamHorror.LOGGER.warn("[BH cleanup] could not clear {}: {}", cls, ex.toString());
            }
        }
        BodycamHorror.LOGGER.info("[BH cleanup] client: cleared blood, decals and bodies ({} mods)", done);
    }
}
