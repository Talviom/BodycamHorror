package com.talviom.bodycamhorror.client;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.sabotage.PowerSwitchBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиент: своя подсказка под прицелом, когда смотришь на щиток или генератор: «[F] CUT THE POWER»,
 * «[F] RESTORE POWER» и так же для генератора. Печатной машинкой цвета кости вместо жёлтой надписи TACZ
 * «Нажмите [F] для взаимодействия» (её цвет задан в коде TACZ, поэтому её выключаем в настройках TACZ).
 * Клавиша берётся из настроек управления TACZ, без TACZ подставляется F.
 */
@Mod.EventBusSubscriber(modid = BodycamHorror.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InteractHint {
    private InteractHint() {}

    @SubscribeEvent
    public static void onInteractHintRender(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) return;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        boolean breaker = state.is(ModBlocks.BREAKER.get());
        if (!breaker && !state.is(ModBlocks.GENERATOR.get())) return;
        boolean on = !state.hasProperty(PowerSwitchBlock.POWERED) || state.getValue(PowerSwitchBlock.POWERED);
        String action = breaker ? (on ? "CUT THE POWER" : "RESTORE POWER")
                : (on ? "SHUT DOWN THE GENERATOR" : "START THE GENERATOR");
        String key = LobbyNotes.keyLabel("F", "tacz", "interact");
        if (key == null) return;
        int w = e.getWindow().getGuiScaledWidth();
        int h = e.getWindow().getGuiScaledHeight();
        Vhs.ghostCentered(e.getGuiGraphics(), Vhs.title("[" + key + "] " + action), w / 2, h / 2 + 14,
                0xC8000000 | Vhs.BONE, 1f, 0.15f);
    }
}
