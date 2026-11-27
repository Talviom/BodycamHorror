package com.talviom.bodycamhorror.lobby;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Лобби 1.0: папка инструктажа. ПКМ открывает листы на весь экран (client/BriefingScreen):
 * обложка, те же темы, что на стендах, записки от руки и вырванный последний лист.
 * Выдаётся каждому в лобби (LobbyTraining), в матч не попадает: на старте раунда инвентарь очищается.
 */
public class BriefingFolderItem extends Item {
    public BriefingFolderItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.talviom.bodycamhorror.client.BriefingScreen.open());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
