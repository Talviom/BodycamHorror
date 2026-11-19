package com.talviom.bodycamhorror.gear;

import com.talviom.bodycamhorror.ModSounds;
import com.talviom.bodycamhorror.horror.FearSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Бинт: держать ПКМ 2 секунды, лечит 3 сердца и снимает часть страха. Пока бинтуешься, в руке бинт,
 * а не оружие, поэтому стрелять нельзя. Отпустил кнопку раньше времени: перевязка отменяется, бинт цел.
 * Если здоровье полное, не тратится.
 *
 * Клиентская часть (3D-рулон, обматывание в руке, поза в третьем лице) в {@code client/BandageClient.java},
 * полоса «ПЕРЕВЯЗКА» и полоска здоровья в {@code client/HealthHud.java}.
 */
public class BandageItem extends Item {
    public static final int USE_TICKS = 40;
    public static final float HEAL = 6f;
    /** Сколько страха снимает перевязка (страх от 0 до 100). */
    public static final float FEAR_RELIEF = 20f;
    /** Шорох бинта во время перевязки: раз в столько тиков. */
    private static final int WRAP_SOUND_TICKS = 9;

    public BandageItem(Properties props) {
        super(props);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // Своё обматывание вместо натягивания лука: см. BandageClient.
        return UseAnim.CUSTOM;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() >= player.getMaxHealth()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide) return;
        int used = USE_TICKS - remaining;
        if (used % WRAP_SOUND_TICKS == 0 && remaining > 4) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.BANDAGE_WRAP.get(),
                    SoundSource.PLAYERS, 0.7f, 0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer p) {
            p.heal(HEAL);
            FearSystem.INSTANCE.add(p, -FEAR_RELIEF);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.BANDAGE_TIE.get(), SoundSource.PLAYERS, 0.8f, 1f);
            if (!p.isCreative()) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.talviom.bodycamhorror.client.BandageClient.EXTENSIONS);
    }
}
