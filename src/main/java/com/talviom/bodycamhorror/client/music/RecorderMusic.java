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
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Клиент: музыка лобби из магнитофона (TapeRecorderBlock). Звук стоит в точке магнитофона и слышен в радиусе
 * около 16 блоков (громкость «Музыкальные блоки»). Пока магнитофон слышно, общая музыка лобби молчит (MusicDirector).
 * Вызывается только из тика блок-сущности на клиенте, поэтому серверу этот класс не нужен.
 */
public final class RecorderMusic {
    public static final ResourceLocation TRACK = new ResourceLocation(BodycamHorror.MOD_ID, "music.lobby_tape");
    private static final double HEAR = 18;
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
            this.volume = 1.0f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.3;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            age++;
            BlockState s = level.getBlockState(pos);
            if (!(s.getBlock() instanceof TapeRecorderBlock) || !s.getValue(TapeRecorderBlock.PLAYING) || !MusicDirector.lobbyTime()) finish();
        }

        void finish() {
            stop();
        }
    }
}
