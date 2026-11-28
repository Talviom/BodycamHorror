package com.talviom.bodycamhorror.horror;

import com.talviom.bodycamhorror.HorrorConfig;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.match.MatchState;
import com.talviom.bodycamhorror.net.BhNetwork;
import com.talviom.bodycamhorror.net.DreadPacket;
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
            case BREATH -> playTo(target, "bodycamhorror:tenant_breath", target.position().add(behind(target).scale(1.2)).add(0, 1.4, 0), 0.9f, 0.9f);
            case TAPE_HISS -> playTo(target, "bodycamhorror:specimen_knock", around(target, 5, 9), 0.8f, 0.85f + rnd.nextFloat() * 0.2f);
            case DOOR -> playTo(target, "bodycamhorror:door_slam", around(target, 5, 9), 1.0f, 0.9f + rnd.nextFloat() * 0.15f);
            case METAL -> playTo(target, "bodycamhorror:metal_bang", around(target, 14, 22), 1.0f, 0.85f + rnd.nextFloat() * 0.25f);
            case CRY -> playTo(target, "bodycamhorror:far_cry", around(target, 16, 24), 1.0f, 0.9f + rnd.nextFloat() * 0.15f);
            case PHONE -> phone(target);
            case SURGE -> {
                if (!surge(target)) whisper(target);
            }
            case WAVE -> {
                if (!wave(target)) knock(target);
            }
            case TIMECODE -> BhNetwork.sendDread(target, new DreadPacket(DreadPacket.TIMECODE, 0, 0, 0, 0, 40 + rnd.nextInt(40)));
            case REWIND -> {
                BhNetwork.sendDread(target, new DreadPacket(DreadPacket.REWIND, 0, 0, 0, 0, 28));
                playTo(target, "bodycamhorror:tape_rewind", target.position(), 0.9f, 1.0f);
            }
            case SILHOUETTE -> {
                if (!silhouette(target)) breathOrWhisper(target);
            }
        }
    }

    // ---------- 0.9: новые ивенты ----------

    private void breathOrWhisper(ServerPlayer p) {
        if (rnd.nextBoolean()) fire(HorrorEvent.BREATH, p);
        else whisper(p);
    }

    /** Телефон звонит в стороне, после звонков трубку снимают: дыхание оттуда же. */
    private void phone(ServerPlayer p) {
        Vec3 pos = around(p, 7, 12);
        playTo(p, "bodycamhorror:phone_ring", pos, 1.0f, 1.0f);
        schedule(100, () -> playTo(p, "minecraft:block.lever.click", pos, 0.8f, 0.6f));
        schedule(112, () -> playTo(p, "bodycamhorror:tenant_breath", pos, 1.0f, 0.85f));
    }

    /** Скачок напряжения: лампы вокруг гудят, потом все гаснут на 2-3 с. */
    private boolean surge(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<net.minecraft.core.BlockPos> lamps = Sabotage.lampsAround(level, p.blockPosition(), 18);
        if (lamps.isEmpty()) return false;
        for (int i = 0; i < Math.min(3, lamps.size()); i++) {
            net.minecraft.core.BlockPos l = lamps.get(rnd.nextInt(lamps.size()));
            playTo(p, "bodycamhorror:lamp_buzz", Vec3.atCenterOf(l), 1.0f, 1.3f + rnd.nextFloat() * 0.2f);
        }
        Sabotage.darkPulse(level, p.blockPosition(), 18, 12, 40 + rnd.nextInt(20));
        schedule(12, () -> playTo(p, "bodycamhorror:breaker_off", p.position(), 0.5f, 1.4f));
        return true;
    }

    /** Волна: лампы гаснут по очереди, от самой дальней к игроку, и потом загораются. */
    private boolean wave(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        net.minecraft.core.BlockPos at = p.blockPosition();
        List<net.minecraft.core.BlockPos> lamps = new ArrayList<>(Sabotage.lampsAround(level, at, 26));
        if (lamps.size() < 2) return false;
        lamps.sort((a, b) -> Double.compare(b.distSqr(at), a.distSqr(at)));
        int delay = 0;
        for (net.minecraft.core.BlockPos l : lamps) {
            Sabotage.darkPulse(level, l, 1, delay, 70 - Math.min(40, delay / 2));
            int d = delay;
            schedule(d, () -> playTo(p, "minecraft:block.lever.click", Vec3.atCenterOf(l), 0.35f, 0.5f));
            delay += 5;
        }
        return true;
    }

    /**
     * Силуэт Жильца в конце коридора по взгляду игрока, в 10-22 блоках. Только у него на экране;
     * исчезает, когда подходишь ближе 7 блоков или через 6 с. Нет места (стена близко): false.
     */
    private boolean silhouette(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) return false;
        flat = flat.normalize();
        Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(flat.scale(24)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        double free = hit.getLocation().distanceTo(eye);
        if (free < 11) return false;
        double dist = Math.min(free - 1.5, 10 + rnd.nextDouble() * 10);
        Vec3 at = p.position().add(flat.scale(dist));
        // Пол под ним: ищем вниз до 3 блоков.
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i < 3 && level.getBlockState(feet.below()).isAir(); i++) feet = feet.below();
        if (level.getBlockState(feet.below()).isAir()) return false;
        float yaw = (float) (Math.atan2(-flat.z, -flat.x) * 180 / Math.PI) - 90f;
        BhNetwork.sendDread(p, new DreadPacket(DreadPacket.SILHOUETTE, at.x, feet.getY(), at.z, yaw, 120));
        return true;
    }

    /** Точка вокруг игрока на расстоянии от min до max, в случайную сторону. */
    private Vec3 around(ServerPlayer p, double min, double max) {
        double angle = rnd.nextDouble() * Math.PI * 2;
        double dist = min + rnd.nextDouble() * (max - min);
        return p.position().add(Math.cos(angle) * dist, 0.5 + rnd.nextDouble(), Math.sin(angle) * dist);
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
        int interval = 10;
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
                interval = Math.min(14, interval + 1);
            }
            Vec3 pos = origin.add(back.scale(dist)).add(sideDir.scale(sideOffset));
            float vol = volume;
            float pitch = 0.85f + rnd.nextFloat() * 0.25f;
            // 0.9: свой тяжёлый шаркающий шаг, чтобы обманку не путали с шагами врага.
            schedule(delay, () -> playTo(p, "bodycamhorror:fake_step", pos, vol, pitch));
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
