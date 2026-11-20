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
 * Падает: свет ламп, союзник рядом.
 *
 * 0.9 (пункт 2): страх растёт быстрее. Темнота +3 в секунду; включённый фонарик больше не успокаивает,
 * а только останавливает рост от темноты; погасшие лампы рядом пугают; урон пугает (+2,5 за каждую
 * единицу здоровья) и на 8 секунд разгоняет сердцебиение, даже при низком страхе.
 *
 * События начинаются со страха 25. Чем выше страх, тем чаще и сильнее события:
 * 25..50 дальний стук и шёпот (раз в 30-45 с), 50..75 шаги за спиной и шёпот рядом (22-32 с),
 * от 75 стук рядом, помехи бодикама, глушение рации, мигание ламп (16-24 с, не больше 3 за раунд).
 * Пока идёт перестрелка (недавно получил или нанёс урон), событий нет: бой важнее.
 *
 * Баланс v0.5.2 (модель в tools/tests/fear_sim.py): чем выше страх, тем медленнее он растёт,
 * а после каждого события наступает передышка: страх не растёт и немного спадает. Получается пила
 * "нарастание, испуг, отпустило", а не потолок 100 на весь раунд. Одно событие дважды подряд не бывает.
 */
public final class FearSystem {
    public static final FearSystem INSTANCE = new FearSystem();

    public static final float EVENTS_FROM = 25f;
    /** Чем выше страх, тем медленнее он растёт: при 65 вдвое медленнее, к 100 почти стоит. */
    private static final float HEADROOM = 130f;
    private static final int STRONG_PER_ROUND = 3;
    private static final int UPDATE_TICKS = 10;
    private static final int HUD_TICKS = 5;
    private static final float HURT_FEAR_PER_HP = 2.5f;
    private static final int HURT_HEARTBEAT_TICKS = 8 * 20;
    /** Лампы ближе этого считаются «светом» для покоя (свет фонарика не считается). */
    private static final double LAMP_CALM_RANGE = 6.0;
    /** Погасшие лампы в этом радиусе пугают, если ты в темноте. */
    private static final int LAMPS_OUT_RANGE = 10;
    /** В прогоне сценария (tools/tests) страх пишется в лог, по нему строится график. */
    private static final boolean TRACE = System.getProperty("bh.testScript") != null;

    private static final class State {
        float fear;
        long lastCombat = -100000;
        long nextEventAt = -1;
        long stillSince;
        Vec3 lastPos;
        long lastHeartbeat;
        boolean dark;
        /** До этого момента (тики раунда) передышка после испуга. */
        long reliefUntil = -1;
        HorrorEvent lastEvent;
        int strongUsed;
        /** До этого тика сердце стучит после ранения (0.9). */
        long hurtUntil = -1;
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

    /** Идёт перестрелка: игрок недавно ранил или был ранен (настройка combatSeconds). */
    public boolean inCombat(ServerPlayer p) {
        State s = states.get(p.getUUID());
        return s != null && tick - s.lastCombat < HorrorConfig.COMBAT_SECONDS.get() * 20L;
    }

    /** 0.9: ранение пугает и разгоняет сердце на 8 с. Вызывается из HorrorDirector (LivingHurtEvent). */
    public void onHurt(ServerPlayer p, float amount) {
        if (amount <= 0f) return;
        State s = state(p);
        s.fear = Mth.clamp(s.fear + Math.min(25f, amount * HURT_FEAR_PER_HP), 0f, 100f);
        s.hurtUntil = tick + HURT_HEARTBEAT_TICKS;
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
                        Flashlight.isLit(p),
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
        // Свет фонарика тоже даёт яркость у глаз (блоки света вдоль луча), поэтому «спокойный свет»
        // это горящая лампа рядом или яркое место при выключенном фонарике.
        boolean lampLight = com.talviom.bodycamhorror.tenant.TenantRules.litNear(p.serverLevel(), p.position(), LAMP_CALM_RANGE)
                || (!flashlight && light > HorrorConfig.DARK_LIGHT_LEVEL.get());

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
        if (still && light <= 7 && !flashlight) gain += 0.3f;
        if (p.getHealth() < p.getMaxHealth() * 0.4f) gain += 1f;
        if (!lampLight && lampsOutNear(p.serverLevel(), p.blockPosition())) gain += 0.5f;
        int roundTicks = BhConfig.ROUND_SECONDS.get() * 20;
        if (roundTicks - liveTicks < 60 * 20) gain += 0.4f;
        gain *= Math.max(0f, 1f - s.fear / HEADROOM);

        float calm = 0f;
        if (lampLight) calm += HorrorConfig.FEAR_CALM_PER_SECOND.get().floatValue();
        if (allyClose) calm += 1f;
        if (liveTicks < s.reliefUntil) {
            // Передышка после испуга: страх не растёт и понемногу отпускает.
            gain = 0f;
            calm += HorrorConfig.FEAR_RELIEF_PER_SECOND.get().floatValue();
        }

        s.fear = Mth.clamp(s.fear + (gain - calm) * dt, 0f, 100f);

        if (TRACE && tick % 20 == 0) {
            com.talviom.bodycamhorror.BodycamHorror.LOGGER.info("[fear-trace] t={} p={} fear={} dark={} ally={} light={}",
                    liveTicks / 20, p.getGameProfile().getName(), Math.round(s.fear), dark, allyClose ? "close" : allyNear ? "near" : "none", flashlight);
        }
        maybeFire(p, s, liveTicks);
    }

    private void maybeFire(ServerPlayer p, State s, int liveTicks) {
        if (s.fear < EVENTS_FROM) {
            s.nextEventAt = -1;
            return;
        }
        if (s.nextEventAt < 0) {
            // Страх только что перешёл порог: первое событие через несколько секунд.
            s.nextEventAt = liveTicks + 120 + rnd.nextInt(160);
            return;
        }
        if (liveTicks < s.nextEventAt || liveTicks < s.reliefUntil) return;
        if (liveTicks < HorrorConfig.QUIET_SECONDS.get() * 20) return;
        int combat = HorrorConfig.COMBAT_SECONDS.get() * 20;
        if (tick - s.lastCombat < combat) {
            s.nextEventAt = liveTicks + 60;
            return;
        }

        HorrorEvent[] pool;
        int minSec;
        int maxSec;
        int reliefSec;
        boolean strong = s.fear >= 75f && s.strongUsed < STRONG_PER_ROUND;
        if (s.fear < 50f) {
            pool = new HorrorEvent[]{HorrorEvent.KNOCK, HorrorEvent.WHISPER};
            minSec = 30;
            maxSec = 45;
            reliefSec = 10;
        } else if (!strong) {
            pool = new HorrorEvent[]{HorrorEvent.WHISPER, HorrorEvent.FOOTSTEPS, HorrorEvent.FOOTSTEPS};
            minSec = 22;
            maxSec = 32;
            reliefSec = 14;
        } else {
            pool = new HorrorEvent[]{HorrorEvent.KNOCK, HorrorEvent.GLITCH, HorrorEvent.RADIO, HorrorEvent.FLICKER, HorrorEvent.FOOTSTEPS};
            minSec = 16;
            maxSec = 24;
            reliefSec = 20;
            s.strongUsed++;
        }
        HorrorEvent ev = pool[rnd.nextInt(pool.length)];
        // Одно и то же событие дважды подряд надоедает: берём другое.
        for (int i = 0; i < 4 && ev == s.lastEvent; i++) ev = pool[rnd.nextInt(pool.length)];
        s.lastEvent = ev;
        if (TRACE) {
            com.talviom.bodycamhorror.BodycamHorror.LOGGER.info("[fear-event] t={} p={} ev={} fear={}",
                    liveTicks / 20, p.getGameProfile().getName(), ev.id, Math.round(s.fear));
        }
        HorrorDirector.INSTANCE.fire(ev, p);
        s.fear = Math.min(100f, s.fear + 4f);
        s.nextEventAt = liveTicks + (minSec + rnd.nextInt(maxSec - minSec + 1)) * 20;
        s.reliefUntil = liveTicks + reliefSec * 20L;
    }

    private void heartbeat(ServerPlayer p) {
        State s = states.get(p.getUUID());
        if (s == null) return;
        boolean hurt = tick < s.hurtUntil;
        if (s.fear < 65f && !hurt) return;
        int period = s.fear >= 85f ? 18 : s.fear >= 75f ? 24 : 30;
        if (hurt) period = Math.min(period, 14);
        if (tick - s.lastHeartbeat < period) return;
        s.lastHeartbeat = tick;
        float volume = 0.25f + Math.max(0f, s.fear - 65f) / 35f * 0.45f;
        if (hurt) volume = Math.max(volume, 0.6f);
        HorrorDirector.INSTANCE.playSound(p, "bodycamhorror:heartbeat", p.position(), volume, 1.0f);
    }

    /** Рядом есть погасшая лампа (щиток, генератор, мигание). */
    private static boolean lampsOutNear(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        long r2 = (long) LAMPS_OUT_RANGE * LAMPS_OUT_RANGE;
        for (BlockPos lamp : com.talviom.bodycamhorror.sabotage.SabotageData.get(level).lamps) {
            if (lamp.distSqr(pos) > r2 || !level.isLoaded(lamp)) continue;
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(lamp);
            if (st.getBlock() instanceof com.talviom.bodycamhorror.sabotage.LampBlock
                    && !st.getValue(com.talviom.bodycamhorror.sabotage.LampBlock.LIT)) return true;
        }
        return false;
    }

    private State state(ServerPlayer p) {
        return states.computeIfAbsent(p.getUUID(), id -> new State());
    }
}
