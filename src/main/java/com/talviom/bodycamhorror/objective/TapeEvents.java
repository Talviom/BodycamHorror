package com.talviom.bodycamhorror.objective;

import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.match.MatchState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * События кассеты: в мире она лежит 3D-блоком (TapeBlock), предметом остаётся лишь там, где положить её некуда;
 * такой предмет не исчезает на земле, и подобрать его во время матча может только живой игрок.
 */
public class TapeEvents {
    @SubscribeEvent
    public void onTapeJoinLevel(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof ItemEntity item) || !TapeObjective.isTape(item.getItem())) return;
        // 0.9: выпавшая кассета (смерть, выброс на G) ложится на пол той же 3D-моделью, а не плоским предметом.
        if (!e.loadedFromDisk() && e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl
                && item.getItem().getCount() == 1 && TapeObjective.dropAsBlock(sl, item)) {
            e.setCanceled(true);
            return;
        }
        TapeObjective.protect(item);
    }

    @SubscribeEvent
    public void onTapePickupAttempt(EntityItemPickupEvent e) {
        if (!TapeObjective.isTape(e.getItem().getItem())) return;
        MatchManager m = MatchManager.INSTANCE;
        if (!m.running()) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || !m.isAlive(p) || m.state() != MatchState.LIVE) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onTapePickedUp(PlayerEvent.ItemPickupEvent e) {
        if (!TapeObjective.isTape(e.getStack())) return;
        if (e.getEntity() instanceof ServerPlayer p && MatchManager.INSTANCE.running()) TapeObjective.INSTANCE.onPickedUp(p);
    }
}
