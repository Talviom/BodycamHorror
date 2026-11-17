package com.talviom.bodycamhorror.client.music;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Музыкальный трек с плавным появлением и затуханием. Играет «в голове» игрока
 * (без позиции и затухания по расстоянию), громкость зависит от ползунка «Музыка».
 */
public class FadingMusic extends AbstractSoundInstance implements TickableSoundInstance {
    private final float target;
    private final int fadeInTicks;
    private int fadeOutTicks;
    private boolean fadingOut;
    private boolean stopped;

    public FadingMusic(ResourceLocation id, float volume, boolean loop, int fadeInTicks) {
        super(id, SoundSource.MUSIC, RandomSource.create());
        this.target = volume;
        this.fadeInTicks = Math.max(1, fadeInTicks);
        this.volume = fadeInTicks <= 0 ? volume : 0.001f;
        this.looping = loop;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    /** Плавно затихнуть и остановиться. */
    public void fadeOut(int ticks) {
        if (fadingOut) return;
        fadingOut = true;
        fadeOutTicks = Math.max(1, ticks);
    }

    public boolean isFadingOut() {
        return fadingOut;
    }

    @Override
    public void tick() {
        if (stopped) return;
        if (fadingOut) {
            volume -= target / fadeOutTicks;
            if (volume <= 0.001f) {
                volume = 0f;
                stopped = true;
            }
        } else if (volume < target) {
            volume = Math.min(target, volume + target / fadeInTicks);
        }
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
