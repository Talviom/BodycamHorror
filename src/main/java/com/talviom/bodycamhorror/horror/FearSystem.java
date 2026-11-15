package com.talviom.bodycamhorror.horror;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.HorrorConfig;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.match.MatchState;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.HudStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Шкала страха (0..100) у каждого живого игрока. Она и решает, когда и что случится:
 * случайного таймера больше нет.
 *
 * Растёт: темнота на уровне глаз без включённого фонарика, рядом нет союзника,
 * долго стоишь на месте в полутьме, мало здоровья, последняя минута раунда,
 * гибель союзника рядом, каждое случившееся событие.
 * Падает: свет или включённый фонарик, союзник рядом.
 *
 * События начинаются со страха 20. Чем выше страх, тем чаще и сильнее события:
 * 20..45 дальний стук и шёпот, 45..70 шаги за спиной и шёпот рядом,
 * от 70 стук рядом, помехи бодикама, глушение рации, мигание ламп.
 * Пока идёт перестрелка (недавно получил или нанёс урон), событий нет: бой важнее.
 */
public final class FearSystem {
    public static final FearSystem INSTANCE = new FearSystem();

    public static final float EVENTS_FROM = 20f;
    private static final int UPDATE_TICKS = 10;
    private static final int HUD_TICKS = 5;

    private static final class State {
        float fear;
        long lastCombat = -100000;
        long nextEventAt = -1;
        long stillSince;
        Vec3 lastPos;
        long lastHeartbeat;
        boolean dark;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private final Random rnd = new Random();
    private long tick;

    private FearSystem() {}

    public float fear(ServerPlayer p) {
        State s = states.get(p.getUUID());
        return s == null ? 0f : s.fear;
    }

    public void setFear(ServerPlayer p, float value) {
        State s = state(p);
        s.fear = Mth.clamp(value, 0f, 100f);
        s.nextEventAt = -1;
    }

    public void add(ServerPlayer p, float amount) {
        State s = state(p);
        s.fear = Mth.clamp(s.fear + amount, 0f, 100f);
    }

    public void resetAll() {
        states.clear();
    }

    /** Игрок получил или нанёс урон: на время боя события не мешают. */
    public void markCombat(ServerPlayer p) {
        state(p).lastCombat = tick;
    }

    /** Союзник погиб: страшно всем в команде, а тем, кто рядом, особенно. */
    public void onPlayerDied(MinecraftServer server, ServerPlayer victim) {
        MatchManager match = MatchManager.INSTANCE;
        BhTeam team = match.teamOf(victim);
        if (team == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == victim || match.teamOf(p) != team || p.isSpectator()) continue;
            add(p, p.distanceToSqr(victim) < 20 * 20 ? 20f : 8f);
        }
        states.remove(victim.getUUID());
    }

    /** Вызывается каждый тик из HorrorDirector. */
    public void tick(MinecraftServer server, int liveTicks) {
        tick++;
        MatchManager match = MatchManager.INSTANCE;
        boolean live = match.state() == MatchState.LIVE && HorrorConfig.ENABLED.get();

        List<ServerPlayer> alive = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (match.running() && match.teamOf(p) != null && !p.isSpectator() && p.isAlive()) alive.add(p);
        }

        if (live) {
            if (tick % UPDATE_TICKS == 0) {
                for (ServerPlayer p : alive) update(p, alive, match, liveTicks);
            }
            if (HorrorConfig.HEARTBEAT.get()) for (ServerPlayer p : alive) heartbeat(p);
        }

        if (tick % HUD_TICKS == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean inMatch = alive.contains(p);
                State s = states.get(p.getUUID());
                BhNetwork.sendHud(p, new HudStatePacket(
                        inMatch && s != null ? s.fear : 0f,
                        Flashlight.charge(p),
                        Flashlight.hasFlashlight(p),
                        Flashlight.isOn(p),
                        inMatch,
                        inMatch && s != null && s.dark,
                        inMatch && s != null && tick - s.lastCombat < 40));
            }
        }
    }

    private void update(ServerPlayer p, List<ServerPlayer> alive, MatchManager match, int liveTicks) {
        State s = state(p);
        float dt = UPDATE_TICKS / 20f;

        BlockPos eye = BlockPos.containing(p.getEyePosition());
        int light = p.serverLevel().getMaxLocalRawBrightness(eye);
        boolean flashlight = Flashlight.isOn(p);
        boolean dark = light <= HorrorConfig.DARK_LIGHT_LEVEL.get() && !flashlight;
        s.dark = dark;

        // Союзники рядом.
        BhTeam team = match.teamOf(p);
        boolean allyNear = false;
        boolean allyClose = false;
        for (ServerPlayer o : alive) {
            if (o == p || match.teamOf(o) != team) continue;
            double d = o.distanceToSqr(p);
            if (d < 12 * 12) allyNear = true;
            if (d < 6 * 6) allyClose = true;
        }

        // Стоит на месте.
        Vec3 pos = p.position();
        if (s.lastPos == null || s.lastPos.distanceToSqr(pos) > 1.0) {
            s.lastPos = pos;
            s.stillSince = tick;
        }
        boolean still = tick - s.stillSince > 10 * 20;

        float gain = 0f;
        if (dark) gain += HorrorConfig.FEAR_DARK_PER_SECOND.get().floatValue();
        if (!allyNear) gain += HorrorConfig.FEAR_ALONE_PER_SECOND.get().floatValue();
        if (still && light <= 7 && !flashlight) gain += 0.5f;
        if (p.getHealth() < p.getMaxHealth() * 0.4f) gain += 1f;
        int roundTicks = BhConfig.ROUND_SECONDS.get() * 20;
        if (roundTicks - liveTicks < 60 * 20) gain += 0.5f;

        float calm = 0f;
        if (!dark) calm += HorrorConfig.FEAR_CALM_PER_SECOND.get().floatValue();
        if (allyClose) calm += 1f;

        s.fear = Mth.clamp(s.fear + (gain - calm) * dt, 0f, 100f);

        maybeFire(p, s, liveTicks);
    }

    private void maybeFire(ServerPlayer p, State s, int liveTicks) {
        if (s.fear < EVENTS_FROM) {
            s.nextEventAt = -1;
            return;
        }
        if (s.nextEventAt < 0) {
            // Страх только что перешёл порог: первое событие через несколько секунд.
            s.nextEventAt = liveTicks + 100 + rnd.nextInt(140);
            return;
        }
        if (liveTicks < s.nextEventAt) return;
        if (liveTicks < HorrorConfig.QUIET_SECONDS.get() * 20) return;
        int combat = HorrorConfig.COMBAT_SECONDS.get() * 20;
        if (tick - s.lastCombat < combat) {
            s.nextEventAt = liveTicks + 60;
            return;
        }

        HorrorEvent ev;
        int minSec;
        int maxSec;
        if (s.fear < 45f) {
            ev = rnd.nextBoolean() ? HorrorEvent.KNOCK : HorrorEvent.WHISPER;
            minSec = 20;
            maxSec = 35;
        } else if (s.fear < 70f) {
            ev = rnd.nextInt(3) == 0 ? HorrorEvent.WHISPER : HorrorEvent.FOOTSTEPS;
            minSec = 14;
            maxSec = 24;
        } else {
            HorrorEvent[] strong = {HorrorEvent.KNOCK, HorrorEvent.GLITCH, HorrorEvent.RADIO, HorrorEvent.FLICKER, HorrorEvent.FOOTSTEPS};
            ev = strong[rnd.nextInt(strong.length)];
            minSec = 9;
            maxSec = 16;
        }
        HorrorDirector.INSTANCE.fire(ev, p);
        s.fear = Math.min(100f, s.fear + 4f);
        s.nextEventAt = liveTicks + (minSec + rnd.nextInt(maxSec - minSec + 1)) * 20;
    }

    private void heartbeat(ServerPlayer p) {
        State s = states.get(p.getUUID());
        if (s == null || s.fear < 55f) return;
        int period = s.fear >= 85f ? 14 : s.fear >= 70f ? 18 : 24;
        if (tick - s.lastHeartbeat < period) return;
        s.lastHeartbeat = tick;
        float volume = 0.35f + (s.fear - 55f) / 45f * 0.55f;
        HorrorDirector.INSTANCE.playSound(p, "bodycamhorror:heartbeat", p.position(), volume, 1.0f);
    }

    private State state(ServerPlayer p) {
        return states.computeIfAbsent(p.getUUID(), id -> new State());
    }
}
