package com.talviom.bodycamhorror.horror;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.HorrorConfig;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.match.MatchState;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.sabotage.Sabotage;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Хоррор-режиссёр. Следит за раундом и подкидывает события, которые давят на нервы,
 * но не наносят урон и не решают исход перестрелки. Чем дольше идёт раунд и чем меньше
 * осталось живых, тем чаще события. Игрок, оставшийся один, получает их чаще остальных.
 */
public final class HorrorDirector {
    public static final HorrorDirector INSTANCE = new HorrorDirector();

    private record Task(long due, Runnable action) {}

    private final Random rnd = new Random();
    private final List<Task> tasks = new ArrayList<>();
    private MatchState lastState = MatchState.LOBBY;
    private long tick;
    private int liveTicks;
    private int nextEventAt;

    private HorrorDirector() {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        tick++;

        MatchManager match = MatchManager.INSTANCE;
        MatchState state = match.state();
        if (state != lastState) {
            if (state == MatchState.PREP) {
                tasks.clear();
                Sabotage.resetAll(level);
            }
            if (state == MatchState.LIVE) {
                liveTicks = 0;
                nextEventAt = HorrorConfig.QUIET_SECONDS.get() * 20;
            }
            lastState = state;
        }

        Sabotage.tick(level);
        runTasks();

        if (state != MatchState.LIVE || !HorrorConfig.ENABLED.get()) return;
        liveTicks++;

        List<ServerPlayer> alive = alivePlayers(server, match);
        if (alive.isEmpty()) return;

        if (HorrorConfig.HEARTBEAT.get() && liveTicks % 25 == 0) heartbeat(alive, match);

        if (liveTicks >= nextEventAt) {
            ServerPlayer target = pickTarget(alive, match);
            fire(pickEvent(), target);
            nextEventAt = liveTicks + nextInterval(alive.size());
        }
    }

    // ---------- выбор цели и события ----------

    private List<ServerPlayer> alivePlayers(MinecraftServer server, MatchManager match) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (match.teamOf(p) != null && !p.isSpectator() && p.isAlive()) out.add(p);
        }
        return out;
    }

    private boolean isAlone(ServerPlayer p, List<ServerPlayer> alive, MatchManager match) {
        BhTeam team = match.teamOf(p);
        for (ServerPlayer other : alive) {
            if (other != p && match.teamOf(other) == team && other.distanceToSqr(p) < 12 * 12) return false;
        }
        return true;
    }

    private ServerPlayer pickTarget(List<ServerPlayer> alive, MatchManager match) {
        // Одиночки попадают в список дважды: им достаётся больше.
        List<ServerPlayer> pool = new ArrayList<>(alive);
        for (ServerPlayer p : alive) if (isAlone(p, alive, match)) pool.add(p);
        return pool.get(rnd.nextInt(pool.size()));
    }

    private HorrorEvent pickEvent() {
        int total = 0;
        for (HorrorEvent ev : HorrorEvent.values()) total += ev.weight;
        int r = rnd.nextInt(total);
        for (HorrorEvent ev : HorrorEvent.values()) {
            r -= ev.weight;
            if (r < 0) return ev;
        }
        return HorrorEvent.WHISPER;
    }

    private int nextInterval(int aliveCount) {
        int min = HorrorConfig.MIN_INTERVAL_SECONDS.get();
        int max = Math.max(min, HorrorConfig.MAX_INTERVAL_SECONDS.get());
        float roundTicks = BhConfig.ROUND_SECONDS.get() * 20f;
        float tension = Math.min(1f, liveTicks / roundTicks);
        if (aliveCount <= 2) tension = Math.min(1f, tension + 0.35f);
        float seconds = max - (max - min) * tension;
        seconds *= 0.7f + rnd.nextFloat() * 0.6f;
        return Math.max(40, (int) (seconds * 20));
    }

    // ---------- события ----------

    public void fire(HorrorEvent event, ServerPlayer target) {
        switch (event) {
            case FOOTSTEPS -> footsteps(target);
            case WHISPER -> whisper(target);
            case KNOCK -> knock(target);
            case FLICKER -> {
                int radius = HorrorConfig.FLICKER_RADIUS.get();
                if (!Sabotage.flickerAround(target.serverLevel(), target.blockPosition(), radius)) whisper(target);
            }
            case GLITCH -> BhNetwork.sendGlitch(target, 30 + rnd.nextInt(40), 0.5f + rnd.nextFloat() * 0.5f);
        }
    }

    private void footsteps(ServerPlayer p) {
        Vec3 back = behind(p);
        Vec3 side = new Vec3(-back.z, 0, back.x).scale((rnd.nextDouble() - 0.5) * 4);
        Vec3 origin = p.position();
        double start = 7 + rnd.nextDouble() * 4;
        int steps = 4 + rnd.nextInt(4);
        for (int i = 0; i < steps; i++) {
            Vec3 pos = origin.add(back.scale(start - i * 0.8)).add(side);
            float pitch = 0.85f + rnd.nextFloat() * 0.25f;
            schedule(i * 7 + rnd.nextInt(2), () -> playTo(p, "minecraft:block.stone.step", pos, 0.9f, pitch));
        }
    }

    private void whisper(ServerPlayer p) {
        Vec3 pos = p.position().add(behind(p).scale(3 + rnd.nextDouble() * 3));
        playTo(p, "minecraft:ambient.cave", pos, 0.8f, 0.5f + rnd.nextFloat() * 0.4f);
    }

    private void knock(ServerPlayer p) {
        double angle = rnd.nextDouble() * Math.PI * 2;
        double dist = 6 + rnd.nextDouble() * 6;
        Vec3 pos = p.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        if (rnd.nextBoolean()) {
            for (int i = 0; i < 3; i++) {
                schedule(i * 9, () -> playTo(p, "minecraft:entity.zombie.attack_wooden_door", pos, 0.7f, 0.6f));
            }
        } else {
            playTo(p, "minecraft:block.wooden_door.open", pos, 1.0f, 0.5f);
        }
    }

    private void heartbeat(List<ServerPlayer> alive, MatchManager match) {
        for (ServerPlayer p : alive) {
            boolean wounded = p.getHealth() <= p.getMaxHealth() * 0.4f;
            boolean last = true;
            boolean enemies = false;
            BhTeam team = match.teamOf(p);
            for (ServerPlayer other : alive) {
                if (other == p) continue;
                if (match.teamOf(other) == team) last = false;
                else enemies = true;
            }
            if (wounded || (last && enemies)) {
                playTo(p, "minecraft:entity.warden.heartbeat", p.position(), wounded ? 1.0f : 0.6f, 1.0f);
            }
        }
    }

    // ---------- вспомогательное ----------

    private Vec3 behind(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0, -look.z);
        if (back.lengthSqr() < 1.0E-4) return new Vec3(1, 0, 0);
        return back.normalize();
    }

    /** Звук, который слышит только один игрок. */
    private void playTo(ServerPlayer p, String soundId, Vec3 pos, float volume, float pitch) {
        if (p.hasDisconnected()) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
        if (sound == null) return;
        p.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.AMBIENT,
                pos.x, pos.y, pos.z, volume, pitch, rnd.nextLong()));
    }

    private void schedule(int delayTicks, Runnable action) {
        tasks.add(new Task(tick + delayTicks, action));
    }

    private void runTasks() {
        if (tasks.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.due() <= tick) {
                due.add(t.action());
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }
}
