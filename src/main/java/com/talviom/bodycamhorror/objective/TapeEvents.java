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
    /** Выпавшие колбы-предметы, которые на следующем тике станут 3D-блоком. */
    private final java.util.List<ItemEntity> pending = new java.util.ArrayList<>();

    @SubscribeEvent
    public void onTapeJoinLevel(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof ItemEntity item) || !TapeObjective.isTape(item.getItem())) return;
        TapeObjective.protect(item);
        // 0.9: выпавшая колба (смерть, выброс на G) ложится той же 3D-моделью, а не плоским предметом.
        // 0.9.8: не сразу, а на следующем тике: команда /give бросает «ненастоящий» предмет для анимации
        // (makeFakeItem уже после появления в мире), из него не должна вырастать вторая колба на полу.
        if (!e.loadedFromDisk()) pending.add(item);
    }

    @SubscribeEvent
    public void onTapePendingTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || pending.isEmpty()) return;
        java.util.List<ItemEntity> now = new java.util.ArrayList<>(pending);
        pending.clear();
        for (ItemEntity item : now) {
            // Ненастоящий предмет /give живёт один тик (возраст почти предельный) или уже исчез.
            if (!item.isAlive() || item.getAge() >= 5990 || item.getItem().getCount() != 1) continue;
            if (item.level() instanceof net.minecraft.server.level.ServerLevel sl && TapeObjective.dropAsBlock(sl, item)) {
                item.discard();
            }
        }
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
