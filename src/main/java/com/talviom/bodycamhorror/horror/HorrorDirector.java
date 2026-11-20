package com.talviom.bodycamhorror.horror;

import com.talviom.bodycamhorror.HorrorConfig;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.match.MatchState;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.radio.RadioState;
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
 * Хоррор-режиссёр. Следит за раундом и умеет проигрывать события, которые давят на нервы,
 * но не наносят урон и не решают исход перестрелки. Когда и какое событие получит игрок,
 * решает его шкала страха (FearSystem).
 */
public final class HorrorDirector {
    public static final HorrorDirector INSTANCE = new HorrorDirector();

    private record Task(long due, Runnable action) {}

    private final Random rnd = new Random();
    private final List<Task> tasks = new ArrayList<>();
    private MatchState lastState = MatchState.LOBBY;
    private long tick;
    private int liveTicks;

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
                FearSystem.INSTANCE.resetAll();
            }
            if (state == MatchState.LIVE) liveTicks = 0;
            lastState = state;
        }

        Sabotage.tick(level);
        runTasks();
        if (tick % 10 == 0) RadioState.update(server, match);

        if (state == MatchState.LIVE) liveTicks++;
        // Когда и что случится, решает шкала страха каждого игрока (FearSystem).
        FearSystem.INSTANCE.tick(server, liveTicks);
    }

    /** 0.9: ранение в матче пугает и разгоняет сердце (FearSystem.onHurt). */
    @SubscribeEvent
    public void onHorrorPlayerHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !MatchManager.INSTANCE.running()) return;
        FearSystem.INSTANCE.onHurt(p, e.getAmount());
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
            case GLITCH -> {
                BhNetwork.sendGlitch(target, 30 + rnd.nextInt(40), 0.5f + rnd.nextFloat() * 0.5f);
                // Вместе с помехами на секунду-две гаснет фонарик.
                Flashlight.blackout(target, 20 + rnd.nextInt(25));
                playTo(target, "bodycamhorror:radio_static", target.position(), 0.7f, 0.9f + rnd.nextFloat() * 0.2f);
            }
            case RADIO -> {
                RadioState.jam(target.getUUID(), 120 + rnd.nextInt(100));
                BhNetwork.sendGlitch(target, 20, 0.3f);
                playTo(target, "bodycamhorror:radio_static", target.position(), 0.5f, 0.8f);
            }
        }
    }

    /**
     * Шаги за спиной: кто-то подходит, становится громче, потом разворачивается и уходит.
     * На уходе каждый шаг тише и чуть медленнее предыдущего, поэтому шаги затихают постепенно,
     * а не обрываются.
     */
    private void footsteps(ServerPlayer p) {
        Vec3 back = behind(p);
        Vec3 sideDir = new Vec3(-back.z, 0, back.x);
        Vec3 origin = p.position();
        double dist = 7 + rnd.nextDouble() * 4;
        double sideOffset = (rnd.nextDouble() - 0.5) * 4;
        // При уходе шаги чуть уводит в сторону, как будто идущий свернул.
        double drift = (rnd.nextBoolean() ? 1 : -1) * (0.2 + rnd.nextDouble() * 0.3);

        int approach = 3 + rnd.nextInt(3);
        int leave = 5 + rnd.nextInt(3);
        int delay = 0;
        int interval = 7;
        float volume = 0.5f;

        for (int i = 0; i < approach + leave; i++) {
            boolean leaving = i >= approach;
            if (!leaving) {
                // Подходит: ближе и громче.
                volume = Math.min(0.9f, 0.5f + 0.4f * (i + 1) / approach);
                if (i > 0) dist -= 0.8;
            } else {
                // Уходит: дальше, тише, реже.
                dist += 0.9;
                sideOffset += drift;
                volume *= 0.72f;
                interval = Math.min(11, interval + 1);
            }
            Vec3 pos = origin.add(back.scale(dist)).add(sideDir.scale(sideOffset));
            float vol = volume;
            float pitch = 0.85f + rnd.nextFloat() * 0.25f;
            schedule(delay, () -> playTo(p, "minecraft:block.stone.step", pos, vol, pitch));
            // Короткая пауза на месте перед разворотом.
            delay += interval + rnd.nextInt(2) + (i == approach - 1 ? 10 + rnd.nextInt(10) : 0);
        }
    }

    private void whisper(ServerPlayer p) {
        Vec3 pos = p.position().add(behind(p).scale(3 + rnd.nextDouble() * 3));
        playTo(p, "bodycamhorror:whisper", pos, 0.9f, 0.9f + rnd.nextFloat() * 0.2f);
    }

    private void knock(ServerPlayer p) {
        double angle = rnd.nextDouble() * Math.PI * 2;
        double dist = 6 + rnd.nextDouble() * 6;
        Vec3 pos = p.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        if (rnd.nextBoolean()) {
            // Каждый следующий удар тише предыдущего, чтобы стук затихал, а не обрывался.
            for (int i = 0; i < 4; i++) {
                float volume = 0.9f * (float) Math.pow(0.65, i);
                schedule(i * 9, () -> playTo(p, "bodycamhorror:knock", pos, volume, 0.95f));
            }
        } else {
            playTo(p, "minecraft:block.wooden_door.open", pos, 1.0f, 0.5f);
        }
    }

    // ---------- вспомогательное ----------

    private Vec3 behind(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0, -look.z);
        if (back.lengthSqr() < 1.0E-4) return new Vec3(1, 0, 0);
        return back.normalize();
    }

    /** Звук, который слышит только один игрок (для FearSystem). */
    public void playSound(ServerPlayer p, String soundId, Vec3 pos, float volume, float pitch) {
        playTo(p, soundId, pos, volume, pitch);
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
