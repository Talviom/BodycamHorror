package com.talviom.bodycamhorror.bots;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.client.ClientState;
import com.talviom.bodycamhorror.client.TeamSkins;
import com.talviom.bodycamhorror.net.BannerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Клиент: «глаза второго игрока» в проверке по сети. Включается только свойством JVM bh.remoteShots=true
 * (его ставит gradlew runClient -PbhJoin=...), в обычной игре ничего не делает.
 *
 * Второе окно игры само снимает кадры: на каждой заставке раунда и раз в 15 секунд (run2/screenshots/net2_*.png),
 * и пишет в лог с пометкой [bh-remote]: вход на сервер, заставки, какие скины видит у каждого игрока,
 * свой страх и первые услышанные звуки мода. Так видно, что доходит до друга по сети, а не только до хоста.
 */
public final class RemoteShots {
    private static final boolean ON = Boolean.getBoolean("bh.remoteShots");
    private static final String PREFIX = "[bh-remote] ";
    private static final long PERIOD_MS = 15000;

    private static int shots;
    private static long nextPeriodicAt;
    private static long pendingShotAt;
    private static String pendingShotName;
    private static boolean joined;
    private static String lastSkins = "";
    private static int lastFear = -1;
    private static final Set<ResourceLocation> HEARD = new HashSet<>();

    /**
     * Адрес хоста (свойство bh.join). Заходим сами, а не через --quickPlayMultiplayer:
     * у быстрого входа нет данных опроса сервера, клиент Forge считает сервер ванильным,
     * не отвечает на его рукопожатие модов, и вход обрывается по тайм-ауту («Timed out»).
     * Поэтому сначала опрашиваем сервер, как список серверов, и подключаемся уже с этими данными.
     */
    private static final String JOIN = System.getProperty("bh.join", "");
    private static net.minecraft.client.multiplayer.ServerStatusPinger pinger;
    private static net.minecraft.client.multiplayer.ServerData joinData;
    private static long joinPingAt;
    private static boolean joinStarted;

    private RemoteShots() {}

    /** Опросить хоста и зайти к нему (один раз, когда клиент дошёл до главного меню). */
    private static void joinTick() {
        if (JOIN.isBlank() || joinStarted) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null || mc.screen == null || mc.getOverlay() != null) return;
        long now = System.currentTimeMillis();
        if (pinger == null) {
            pinger = new net.minecraft.client.multiplayer.ServerStatusPinger();
            joinData = new net.minecraft.client.multiplayer.ServerData("BhTest host", JOIN, false);
            joinPingAt = now;
            BodycamHorror.LOGGER.info(PREFIX + "pinging " + JOIN);
            try {
                pinger.pingServer(joinData, () -> {});
            } catch (Exception ex) {
                BodycamHorror.LOGGER.warn(PREFIX + "ping failed: " + ex);
            }
            return;
        }
        pinger.tick();
        boolean pinged = joinData.forgeData != null;
        if (!pinged && now - joinPingAt < 10000) return;
        joinStarted = true;
        pinger.removeAll();
        BodycamHorror.LOGGER.info(PREFIX + "connecting to " + JOIN + (pinged ? " (Forge server)" : " (no ping answer)"));
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(JOIN), joinData, false);
    }

    /** Пришла заставка с сервера: снять кадр, когда она проявится. */
    public static void onBanner(BannerPacket.Kind kind) {
        if (!ON) return;
        BodycamHorror.LOGGER.info(PREFIX + "banner " + kind);
        pendingShotName = "net2_" + kind.name().toLowerCase(Locale.ROOT);
        pendingShotAt = System.currentTimeMillis() + 1500;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            if (joined) BodycamHorror.LOGGER.info(PREFIX + "left the server");
            joined = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (!joined) {
            joined = true;
            nextPeriodicAt = now + 8000;
            BodycamHorror.LOGGER.info(PREFIX + "joined as " + mc.player.getGameProfile().getName()
                    + (mc.getCurrentServer() != null ? " on " + mc.getCurrentServer().ip : ""));
        }
        if (pendingShotName != null && now >= pendingShotAt) {
            ClientShots.shot(pendingShotName + "_" + (++shots));
            pendingShotName = null;
        }
        if (now >= nextPeriodicAt) {
            nextPeriodicAt = now + PERIOD_MS;
            ClientShots.shot("net2_view_" + (++shots));
            logSkins(mc);
        }
        int fear = Math.round(ClientState.fear);
        if (Math.abs(fear - lastFear) >= 10 || (fear == 0 && lastFear != 0)) {
            lastFear = fear;
            BodycamHorror.LOGGER.info(PREFIX + "fear " + fear + " inMatch=" + ClientState.inMatch);
        }
    }

    /** Какой скин второе окно рисует у каждого игрока (должна быть форма его команды). */
    private static void logSkins(Minecraft mc) {
        Scoreboard scoreboard = mc.level.getScoreboard();
        StringBuilder b = new StringBuilder();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            PlayerTeam team = scoreboard.getPlayersTeam(name);
            ResourceLocation shown = info.getSkinLocation();
            ResourceLocation wanted = TeamSkins.skinFor(scoreboard, name);
            b.append(name).append(" team=").append(team == null ? "-" : team.getName())
                    .append(" skin=").append(shown.getPath().replace("textures/entity/skin/", ""))
                    .append(shown.equals(wanted) ? " ok" : " WRONG").append("; ");
        }
        String s = b.toString();
        if (!s.equals(lastSkins)) {
            lastSkins = s;
            BodycamHorror.LOGGER.info(PREFIX + "skins " + s);
        }
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class RemoteShotsEvents {
        @SubscribeEvent
        public static void onRemoteClientTick(TickEvent.ClientTickEvent e) {
            if (ON && e.phase == TickEvent.Phase.END) {
                joinTick();
                tick();
            }
        }

        /** Первый раз услышанный звук мода (шипение кассеты, музыка, щитки) пишется в лог. */
        @SubscribeEvent
        public static void onRemoteSound(PlaySoundEvent e) {
            if (!ON) return;
            SoundInstance sound = e.getSound();
            if (sound == null || sound.getLocation() == null) return;
            if (BodycamHorror.MOD_ID.equals(sound.getLocation().getNamespace()) && HEARD.add(sound.getLocation())) {
                BodycamHorror.LOGGER.info(PREFIX + "sound " + sound.getLocation());
            }
        }
    }
}
