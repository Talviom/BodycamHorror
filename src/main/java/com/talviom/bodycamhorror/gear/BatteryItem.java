package com.talviom.bodycamhorror.gear;

import com.talviom.bodycamhorror.flashlight.Flashlight;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Батарейка для фонарика: ПКМ, и заряд фонарика сразу полный. Без фонарика в инвентаре не тратится. */
public class BatteryItem extends Item {
    public BatteryItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        if (!Flashlight.hasFlashlight(p)) {
            p.displayClientMessage(Component.literal("No flashlight to put it in"), true);
            return InteractionResultHolder.fail(stack);
        }
        Flashlight.refill(p);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6f, 1.6f);
        p.displayClientMessage(Component.literal("Fresh battery"), true);
        if (!p.isCreative()) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}
