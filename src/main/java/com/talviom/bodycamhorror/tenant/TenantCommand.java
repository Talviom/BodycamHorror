package com.talviom.bodycamhorror.tenant;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Проверка Жильца. Добавляется к /bh.
 *
 * /bh tenant spawn [frozen]   — поставить Жильца там, где вы стоите (frozen: стоит на месте, для скриншотов)
 * /bh tenant remove           — убрать всех
 * /bh tenant scare <игрок>    — скример сейчас, без лимитов
 * /bh tenant freeze|unfreeze  — остановить или отпустить
 * /bh tenant info             — состояние каждого Жильца
 */
public final class TenantCommand {
    private TenantCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bh").requires(s -> s.hasPermission(2))
                .then(Commands.literal("tenant")
                        .then(Commands.literal("spawn").executes(c -> spawn(c, false))
                                .then(Commands.literal("frozen").executes(c -> spawn(c, true))))
                        .then(Commands.literal("remove").executes(c -> {
                            List<TenantEntity> all = all(c.getSource().getLevel());
                            all.forEach(Entity::discard);
                            c.getSource().sendSuccess(() -> Component.literal("Tenants removed: " + all.size()), true);
                            return all.size();
                        }))
                        .then(pose())
                        .then(Commands.literal("freeze").executes(c -> freeze(c, true)))
                        .then(Commands.literal("unfreeze").executes(c -> freeze(c, false)))
                        .then(Commands.literal("scare").then(Commands.argument("player", EntityArgument.player())
                                .executes(TenantCommand::scare)))
                        .then(Commands.literal("info").executes(c -> {
                            List<TenantEntity> all = all(c.getSource().getLevel());
                            if (all.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("[tenant] none, noises=" + TenantHearing.count()), false);
                            for (TenantEntity t : all) {
                                String line = "[tenant] " + t.info() + " pos=" + t.blockPosition().toShortString() + " noises=" + TenantHearing.count();
                                c.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return all.size();
                        }))));
    }

    /** /bh tenant pose wait|follow|stalk|scare|grab|stunned|retreat — застыть в позе (для скриншотов). */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> pose() {
        var pose = Commands.literal("pose");
        for (TenantEntity.State s : TenantEntity.State.values()) {
            pose.then(Commands.literal(s.name().toLowerCase()).executes(c -> {
                List<TenantEntity> all = all(c.getSource().getLevel());
                all.forEach(t -> t.pose(s));
                c.getSource().sendSuccess(() -> Component.literal("Tenant pose: " + s.name().toLowerCase()), false);
                return all.size();
            }));
        }
        return pose;
    }

    static List<TenantEntity> all(ServerLevel level) {
        return new java.util.ArrayList<>(level.getEntities(ModEntities.TENANT.get(), e -> true));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, boolean frozen) {
        ServerLevel level = c.getSource().getLevel();
        TenantEntity t = ModEntities.TENANT.get().create(level);
        if (t == null) return 0;
        Vec3 pos = c.getSource().getPosition();
        float yaw = c.getSource().getRotation().y;
        t.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        t.setYHeadRot(yaw);
        t.setYBodyRot(yaw);
        t.setHome(BlockPos.containing(pos));
        t.setFrozen(frozen);
        level.addFreshEntity(t);
        c.getSource().sendSuccess(() -> Component.literal("Tenant spawned at " + BlockPos.containing(pos).toShortString()
                + (frozen ? " (frozen)" : "")), true);
        return 1;
    }

    private static int freeze(CommandContext<CommandSourceStack> c, boolean frozen) {
        List<TenantEntity> all = all(c.getSource().getLevel());
        all.forEach(t -> t.setFrozen(frozen));
        c.getSource().sendSuccess(() -> Component.literal("Tenants " + (frozen ? "frozen" : "released") + ": " + all.size()), false);
        return all.size();
    }

    private static int scare(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        List<TenantEntity> all = all(c.getSource().getLevel());
        if (all.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No tenant"));
            return 0;
        }
        TenantEntity t = all.get(0);
        t.setFrozen(false);
        t.startScare(p, c.getSource().getLevel().getGameTime(), false);
        c.getSource().sendSuccess(() -> Component.literal("Tenant scared " + p.getGameProfile().getName()), false);
        return 1;
    }
}
