package com.talviom.bodycamhorror.command;

import com.mojang.brigadier.CommandDispatcher;
import com.talviom.bodycamhorror.arena.ArenaBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * /bh arena build — построить тестовую арену рядом с тем местом, где вы стоите.
 * Заменяет блоки в области 41x41x6 и перезаписывает спавны и точки лута. Добавляется к /bh.
 */
public final class ArenaCommand {
    private ArenaCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bh").requires(s -> s.hasPermission(2))
                .then(Commands.literal("arena").then(Commands.literal("build").executes(c -> {
                    BlockPos base = BlockPos.containing(c.getSource().getPosition()).offset(2, 0, 2);
                    Component result = ArenaBuilder.build(c.getSource().getServer().overworld(), base);
                    c.getSource().sendSuccess(() -> result, true);
                    return 1;
                }).then(Commands.literal("facility").executes(c -> {
                    // «Комплекс»: три уровня, лестницы и спуски, зеркальный лут (FacilityBuilder).
                    BlockPos base = BlockPos.containing(c.getSource().getPosition()).offset(2, 0, 2);
                    Component result = com.talviom.bodycamhorror.arena.FacilityBuilder.build(c.getSource().getServer().overworld(), base);
                    c.getSource().sendSuccess(() -> result, true);
                    return 1;
                })).then(Commands.literal("hospital").executes(c -> {
                    // «Больница» (1.0): основная карта, корпус в два этажа с двором-колодцем и хозблоком (HospitalBuilder).
                    BlockPos base = BlockPos.containing(c.getSource().getPosition()).offset(2, 0, 2);
                    Component result = com.talviom.bodycamhorror.arena.HospitalBuilder.build(c.getSource().getServer().overworld(), base);
                    c.getSource().sendSuccess(() -> result, true);
                    return 1;
                })))));
    }
}
