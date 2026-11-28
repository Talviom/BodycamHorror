package com.talviom.bodycamhorror.client.music;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.lobby.TapeRecorderBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Клиент: музыка лобби из магнитофона (TapeRecorderBlock). Магнитофон стоит в центре лобби; в радиусе FLAT
 * (вся комната) громкость ровная, дальше до HEAR плавно стихает (громкость «Музыкальные блоки»). Пока магнитофон слышно, общая музыка лобби молчит (MusicDirector).
 * Вызывается только из тика блок-сущности на клиенте, поэтому серверу этот класс не нужен.
 */
public final class RecorderMusic {
    public static final ResourceLocation TRACK = new ResourceLocation(BodycamHorror.MOD_ID, "music.lobby_tape");
    private static final double HEAR = 20;
    /** Громкость (просьбы 2026-11-28: 0.4, потом ещё на 30% тише). */
    private static final float VOLUME = 0.28f;
    /** До этого расстояния громкость ровная (вся комната 20 x 14 от центра), дальше плавно стихает к HEAR. */
    private static final double FLAT = 13;
    private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();
    private static long heardAt;

    private RecorderMusic() {}

    /** Слышно ли сейчас какой-нибудь магнитофон (последние полсекунды). */
    public static boolean heard() {
        return System.currentTimeMillis() - heardAt < 500;
    }

    public static void tick(Level level, BlockPos pos, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != level) return;
        boolean want = state.getValue(TapeRecorderBlock.PLAYING) && MusicDirector.lobbyTime()
                && mc.player.blockPosition().distSqr(pos) < HEAR * HEAR;
        Loop loop = PLAYING.get(pos);
        if (!want) {
            if (loop != null) {
                loop.finish();
                PLAYING.remove(pos);
            }
            return;
        }
        heardAt = System.currentTimeMillis();
        if (loop == null || loop.isStopped() || !mc.getSoundManager().isActive(loop) && loop.age > 30) {
            Loop fresh = new Loop(pos.immutable(), level);
            mc.getSoundManager().play(fresh);
            PLAYING.put(pos.immutable(), fresh);
        }
    }

    private static float levelAt(double distSqr) {
        double d = Math.sqrt(distSqr);
        if (d <= FLAT) return VOLUME;
        return (float) (VOLUME * Math.max(0, 1 - (d - FLAT) / (HEAR - FLAT)));
    }

    /** Бесконечная петля в точке магнитофона; сама замолкает, если магнитофон убрали или выключили. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final BlockPos pos;
        private final Level level;
        int age;

        Loop(BlockPos pos, Level level) {
            super(SoundEvent.createVariableRangeEvent(TRACK), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.pos = pos;
            this.level = level;
            this.looping = true;
            this.delay = 0;
            // Громкость считаем сами (tick): по всей комнате ровно, без затухания с расстоянием.
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = levelAt(Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.distanceToSqr(Vec3.atCenterOf(pos)));
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.3;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            age++;
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof TapeRecorderBlock) || !s.getValue(TapeRecorderBlock.PLAYING) || !MusicDirector.lobbyTime()) finish();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) volume = levelAt(mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
        }

        void finish() {
            stop();
        }
    }
}
