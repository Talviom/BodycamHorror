package com.talviom.bodycamhorror.movement;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.tenant.TenantHearing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0, правка пользователя: корточки больше не делают игрока беззвучным.
 *
 * Приземление на корточках (после прыжка или падения) звучит по блоку, на который упал, вдвое тише, чем стоя;
 * чем выше упал, тем громче. Жилец слышит такое приземление как шаги (10 блоков), с высоты больше 3 блоков как бег (18).
 * Шаги на корточках по стеклу и железу (осколки, люки, контейнеры) слегка хрустят и лязгают.
 * Шаги в полный рост по стеклу и железу Жилец слышит как бег, на корточках как обычные шаги (TenantHearing).
 * Сам край блока на корточках держит полсекунды, потом игрок соскальзывает (mixin/PlayerEdgeMixin).
 */
public final class SneakSounds {
    private static final double STEP_EVERY = 1.3;
    private static final Map<UUID, Double> WALKED = new HashMap<>();

    private SneakSounds() {}

    /** Блок звучит хрустом стекла или лязгом железа. */
    public static boolean noisySurface(BlockState s) {
        SoundType t = s.getSoundType();
        return t == SoundType.GLASS || t == SoundType.METAL || t == SoundType.COPPER || t == SoundType.CHAIN
                || t == SoundType.LANTERN || t == SoundType.NETHERITE_BLOCK;
    }

    /**
     * Спуск со второго этажа (козырёк, крыша перехода, окно над контейнером) и любое падение с 2,5 блока:
     * к обычному звуку блока добавляется шорох одежды из звуков самой игры (кожаная броня), без своих файлов.
     * На железе (контейнер, козырёк) ещё глухой лязг железного люка, тише и ниже обычного.
     */
    private static void bodyLanded(ServerPlayer p, float distance) {
        if (p.isSpectator() || distance < 2.5f) return;
        ServerLevel level = p.serverLevel();
        BlockState on = p.getBlockStateOn();
        if (on.isAir()) return;
        float k = Mth.clamp(distance / 6f, 0.4f, 1f);
        float soft = p.isCrouching() ? 0.5f : 1f;
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS,
                0.55f * k * soft, 0.8f + level.random.nextFloat() * 0.1f);
        SoundType t = on.getSoundType(level, p.getOnPos(), p);
        if (t == SoundType.METAL || t == SoundType.COPPER || t == SoundType.NETHERITE_BLOCK) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS,
                    0.45f * k * soft, 0.6f + level.random.nextFloat() * 0.08f);
        }
    }

    private static void landed(ServerPlayer p, float distance) {
        bodyLanded(p, distance);
        if (!p.isCrouching() || p.isSpectator() || distance < 0.6f) return;
        ServerLevel level = p.serverLevel();
        BlockState on = p.getBlockStateOn();
        if (on.isAir()) return;
        SoundType t = on.getSoundType(level, p.getOnPos(), p);
        float volume = t.getVolume() * 0.5f * Mth.clamp(0.45f + distance / 5f, 0.45f, 1.2f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), t.getStepSound(), SoundSource.PLAYERS, volume, t.getPitch() * 0.9f);
        if (distance > 1.5f) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), t.getFallSound(), SoundSource.PLAYERS, volume * 0.8f, t.getPitch() * 0.75f);
        }
        if (!p.isCreative()) {
            boolean high = distance > 3f;
            TenantHearing.noise(level, p.position(), high ? 2f : 1f, high ? 18 : 10, p, false, "landing");
        }
    }

    private static void sneakStep(ServerPlayer p) {
        if (!p.isCrouching() || !p.onGround() || p.isSpectator()) {
            WALKED.remove(p.getUUID());
            return;
        }
        double moved = Math.sqrt(p.getDeltaMovement().x * p.getDeltaMovement().x + p.getDeltaMovement().z * p.getDeltaMovement().z);
        double walked = WALKED.getOrDefault(p.getUUID(), 0.0) + moved;
        if (walked < STEP_EVERY) {
            WALKED.put(p.getUUID(), walked);
            return;
        }
        WALKED.put(p.getUUID(), 0.0);
        BlockState on = p.getBlockStateOn();
        if (!noisySurface(on)) return;
        SoundType t = on.getSoundType(p.serverLevel(), p.getOnPos(), p);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), t.getStepSound(), SoundSource.PLAYERS, t.getVolume() * 0.25f, t.getPitch() * 0.95f);
    }

    @Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class SneakSoundsEvents {
        @SubscribeEvent
        public static void onSneakLanding(LivingFallEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) landed(p, e.getDistance());
        }

        @SubscribeEvent
        public static void onSneakStepTick(TickEvent.PlayerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) sneakStep(p);
        }
    }
}
