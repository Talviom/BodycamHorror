package com.talviom.bodycamhorror.client.music;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.net.BannerPacket.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Клиент: музыка мода вместо ванильной.
 *
 * Ванильная музыка Minecraft не играет совсем, как и пластинки и музыка других модов
 * (каждый заглушённый трек один раз пишется в лог: "[BH music] blocked ..."). Своя музыка:
 * - меню (главный экран, список серверов, настройки до входа в мир), по кругу;
 * - лобби (в мире, когда матч не идёт), по кругу, тише;
 * - начало раунда: один из четырёх отрывков (случайно, не тот же, что в прошлом раунде),
 *   играет всю заставку и ещё 5 секунд после старта, потом затухает;
 * - короткий удар на итог раунда (свой для победы и поражения) и на итог матча.
 * Громкость регулируется ползунком «Музыка» в настройках звука.
 * Если файла трека нет, Minecraft просто ничего не играет.
 */
public final class MusicDirector {
    public static final ResourceLocation MENU = id("music.menu");
    public static final ResourceLocation LOBBY = id("music.lobby");
    /** Несколько разных отрывков на начало раунда: каждый раунд случайный, без повтора подряд. */
    public static final ResourceLocation[] ROUND_START = {
            id("music.round_start_1"), id("music.round_start_2"), id("music.round_start_3"), id("music.round_start_4")
    };
    public static final ResourceLocation ROUND_WON = id("music.round_won");
    public static final ResourceLocation ROUND_LOST = id("music.round_lost");
    public static final ResourceLocation MATCH_WON = id("music.match_won");
    public static final ResourceLocation MATCH_LOST = id("music.match_lost");

    /** Сколько музыка начала раунда звучит после старта, прежде чем затихнуть. */
    private static final long AFTER_GO_MS = 5000;

    private static FadingMusic current;
    private static ResourceLocation currentId;
    private static long currentStartedAt;
    /** Streamed sounds start asynchronously; give a new track this long before treating "not active" as finished. */
    private static final long START_GRACE_MS = 1500;
    private static long roundMusicStopAt;
    private static int lastRoundStart = -1;
    private static final java.util.Random RND = new java.util.Random();
    private static Kind lastKind;
    private static long lastBannerAt;

    /** Какие чужие треки уже заглушены (в лог каждый пишется один раз). */
    private static final Set<ResourceLocation> BLOCKED = new HashSet<>();

    private MusicDirector() {}

    /**
     * Чужая музыка: всё из категорий «Музыка» и «Пластинки», кроме нашей,
     * а также любой звук другого мода или Minecraft, у которого в названии есть music
     * (моды иногда пускают свои треки через другие категории).
     */
    static boolean isForeignMusic(SoundInstance sound) {
        ResourceLocation id = sound.getLocation();
        if (id == null || BodycamHorror.MOD_ID.equals(id.getNamespace())) return false;
        SoundSource source = sound.getSource();
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS) return true;
        return id.getPath().contains("music");
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(BodycamHorror.MOD_ID, path);
    }

    /** Вызывается при каждой заставке с сервера. */
    public static void onBanner(Kind kind) {
        lastKind = kind;
        lastBannerAt = System.currentTimeMillis();
        switch (kind) {
            case ROUND_START -> {
                roundMusicStopAt = 0;
                int i = RND.nextInt(ROUND_START.length);
                if (i == lastRoundStart) i = (i + 1 + RND.nextInt(ROUND_START.length - 1)) % ROUND_START.length;
                lastRoundStart = i;
                play(ROUND_START[i], 0.85f, false, 20, 30);
            }
            case GO -> roundMusicStopAt = System.currentTimeMillis() + AFTER_GO_MS;
            case ROUND_WON -> play(ROUND_WON, 0.9f, false, 2, 20);
            case ROUND_LOST, DRAW -> play(ROUND_LOST, 0.9f, false, 2, 20);
            case MATCH_WON -> play(MATCH_WON, 0.95f, false, 2, 30);
            case MATCH_LOST -> play(MATCH_LOST, 0.95f, false, 2, 30);
            case STOPPED -> fadeOutCurrent(40);
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        // A one-shot track that simply plays to its end is never marked stopped on the instance;
        // ask the sound manager whether it is still playing (after a short grace period at start).
        // Without this, menu and lobby music never came back after a match result or after leaving the world.
        if (current != null && (current.isStopped()
                || (now - currentStartedAt > START_GRACE_MS && !mc.getSoundManager().isActive(current)))) {
            current = null;
            currentId = null;
        }

        // Музыка начала раунда: через 5 секунд после старта плавно уходит.
        if (roundMusicStopAt > 0 && now >= roundMusicStopAt) {
            roundMusicStopAt = 0;
            if (isRoundStart(currentId)) fadeOutCurrent(50);
        }

        ResourceLocation wanted = null;
        if (mc.level == null) {
            lastKind = null;
            if (mc.screen != null) wanted = MENU;
        } else if (inLobby(now)) {
            wanted = LOBBY;
        }

        boolean loopPlaying = MENU.equals(currentId) || LOBBY.equals(currentId);
        if (wanted != null) {
            // Фоновую музыку меню и лобби не перебиваем, пока доигрывает трек раунда или итога.
            boolean busy = current != null && !loopPlaying && !current.isFadingOut();
            if (!busy && !wanted.equals(currentId)) {
                play(wanted, wanted == MENU ? 0.7f : 0.45f, true, 60, 40);
            }
        } else if (loopPlaying) {
            fadeOutCurrent(40);
        }
    }

    private static boolean isRoundStart(ResourceLocation id) {
        for (ResourceLocation r : ROUND_START) if (r.equals(id)) return true;
        return false;
    }

    /** Лобби: в мире, матч не идёт (ещё не начинался или уже закончился). */
    private static boolean inLobby(long now) {
        if (lastKind == null) return true;
        boolean over = lastKind == Kind.MATCH_WON || lastKind == Kind.MATCH_LOST || lastKind == Kind.STOPPED;
        return over && now - lastBannerAt > 8000;
    }

    private static void play(ResourceLocation id, float volume, boolean loop, int fadeIn, int fadeOutPrevious) {
        fadeOutCurrent(fadeOutPrevious);
        BodycamHorror.LOGGER.info("[BH music] play {}", id);
        FadingMusic music = new FadingMusic(id, volume, loop, fadeIn);
        Minecraft.getInstance().getSoundManager().play(music);
        current = music;
        currentId = id;
        currentStartedAt = System.currentTimeMillis();
    }

    private static void fadeOutCurrent(int ticks) {
        if (current != null) current.fadeOut(ticks);
        current = null;
        currentId = null;
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class MusicForgeEvents {
        @SubscribeEvent
        public static void onMusicClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.END) tick();
        }

        /** Чужая музыка не играет совсем: ни Minecraft, ни пластинки, ни музыка других модов. Своя вместо неё. */
        @SubscribeEvent
        public static void onMusicPlaySound(PlaySoundEvent e) {
            SoundInstance sound = e.getSound();
            if (sound == null || !isForeignMusic(sound)) return;
            e.setSound(null);
            if (BLOCKED.add(sound.getLocation())) {
                BodycamHorror.LOGGER.info("[BH music] blocked {} ({})", sound.getLocation(), sound.getSource().getName());
            }
        }
    }
}
