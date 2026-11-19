package com.talviom.bodycamhorror.gear;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Бинт: держать ПКМ 2 секунды, лечит 3 сердца. Пока бинтуешься, в руке бинт, а не оружие,
 * поэтому стрелять нельзя. Если здоровье полное, не тратится.
 */
public class BandageItem extends Item {
    public static final int USE_TICKS = 40;
    public static final float HEAL = 6f;

    public BandageItem(Properties props) {
        super(props);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() >= player.getMaxHealth()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.6f, 0.7f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer p) {
            p.heal(HEAL);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.7f, 0.6f);
            if (!p.isCreative()) stack.shrink(1);
        }
        return stack;
    }
}
