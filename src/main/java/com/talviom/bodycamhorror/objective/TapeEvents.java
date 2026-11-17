package com.talviom.bodycamhorror.objective;

import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.match.MatchState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** События кассеты: не исчезает на земле, подобрать её во время матча может только живой игрок. */
public class TapeEvents {
    @SubscribeEvent
    public void onTapeJoinLevel(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof ItemEntity item && TapeObjective.isTape(item.getItem())) TapeObjective.protect(item);
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
