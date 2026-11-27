package com.talviom.bodycamhorror.arena;

import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.gear.ModGear;
import com.talviom.bodycamhorror.lobby.BriefSheetBlock;
import com.talviom.bodycamhorror.lobby.BriefSheetBlockEntity;
import com.talviom.bodycamhorror.lobby.LobbyLampBlock;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import com.talviom.bodycamhorror.objective.TapeObjective;
import com.talviom.bodycamhorror.sabotage.EmergencyLampBlock;
import com.talviom.bodycamhorror.sabotage.PowerSwitchBlock;
import com.talviom.bodycamhorror.util.ItemSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Лобби 1.0: «пункт сбора отряда», одна комната 20 x 14, высота 5. Схема согласована с пользователем 2026-11-27.
 *
 * Игрок появляется у запертых дверей и смотрит на дальнюю стену: доска инструктажа со схемой больницы,
 * мониторы с помехами над столом дежурного, доска пропавших над архивом плёнок.
 * Слева шкафчики и площадка «Альфы», справа «Браво». В центре стол инструктажа и пульт «Запись».
 * Ближний левый угол: стойки с оружием и выдача учебного набора. Ближний правый: тренировочный угол
 * (щиток с двумя лампами и аварийной, учебная кассета и учебный выход, уступ со стеклом и железом).
 * 9 стендов-памяток с записками от руки (тексты в lang-файлах, рисует BriefSheetRenderer).
 *
 * Координаты в коде «по схеме»: dx 0..19 слева направо, dz 0..13 от дальней стены к ближней (как на плане),
 * если стоять на месте появления лицом к дальней стене. Мебель из Macaw's Furniture, без мода ванильная замена.
 */
public final class LobbyBuilder {
    /** Внутренность 20 x 14: стены на местных x = 0 и 21, z = 0 и 15; пол y = -1, потолок y = 5. */
    private static final int W = 21;
    private static final int D = 15;
    private static final int H = 5;

    // Направления «по схеме» в настоящие: игрок смотрит на +Z (юг), его левая рука на +X.
    private static final Direction TO_NEAR = Direction.NORTH;
    private static final Direction TO_FAR = Direction.SOUTH;
    private static final Direction TO_RIGHT = Direction.WEST;
    private static final Direction TO_LEFT = Direction.EAST;

    private final ServerLevel level;
    private final BlockPos base;
    private final RandomSource rnd = RandomSource.create(1983);
    private final Set<BlockPos> connect = new LinkedHashSet<>();
    private final List<String> missing = new ArrayList<>();

    private LobbyBuilder(ServerLevel level, BlockPos base) {
        this.level = level;
        this.base = base;
    }

    /** @param base угол лобби на уровне пола (блок, на котором стоят игроки, находится под ним) */
    public static Component build(ServerLevel level, BlockPos base) {
        return new LobbyBuilder(level, base).run();
    }

    private Component run() {
        clear();
        shell();
        lights();
        farWall();
        lockers(true);
        lockers(false);
        center();
        nearLeft();
        nearRight();
        stands();
        details();
        reconnect();

        BlockPos spawn = at(10, 0, 13);
        MapData map = MapData.get(level);
        map.setLobby(spawn);
        map.setLobbyPad(BhTeam.ALPHA, at(3, 0, 6));
        map.setLobbyPad(BhTeam.BRAVO, at(16, 0, 6));
        map.clearLobbyPoints();
        map.setLobbyPoint("kit", at(0, 0, 11));
        map.setLobbyPoint("kit2", at(0, 0, 12));
        map.setLobbyPoint("console", at(9, 1, 9));
        map.setLobbyPoint("tape", at(18, 1, 10));
        map.setLobbyPoint("exit", at(18, 0, 12));
        map.setLobbyPoint("breaker", at(19, 1, 13));
        map.setLobbyPoint("emergency", at(16, 4, 13));
        map.setLobbyPoint("flicker", at(3, 4, 8));
        map.setLobbyPoint("radio", at(10, 1, 1));
        map.setLobbyPoint("min", base.offset(1, 0, 1));
        map.setLobbyPoint("max", base.offset(W - 1, H - 1, D - 1));
        TapeObjective.placeTapeBlock(level, at(18, 1, 10), 1);
        level.setDefaultSpawnPos(spawn, 0f);

        if (!missing.isEmpty()) BodycamHorror.LOGGER.warn("[bh-lobby] modded blocks missing, vanilla used instead: {}", missing);
        return Component.translatable("bh.cmd.lobby_built", spawn.toShortString());
    }

    // ---------- коробка ----------

    private void clear() {
        AABB box = new AABB(base.offset(-1, -2, -1), base.offset(W + 2, H + 2, D + 2));
        for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, box)) f.discard();
        for (int x = -1; x <= W + 1; x++) {
            for (int z = -1; z <= D + 1; z++) {
                for (int y = -2; y <= H + 1; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    private void shell() {
        BlockState lino = Blocks.BROWN_TERRACOTTA.defaultBlockState();
        BlockState worn = Blocks.PACKED_MUD.defaultBlockState();
        BlockState tile = Blocks.WHITE_TERRACOTTA.defaultBlockState();
        BlockState plaster = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState stain = Blocks.ANDESITE.defaultBlockState();
        BlockState cornice = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = 0; x <= W; x++) {
            for (int z = 0; z <= D; z++) {
                boolean outer = x == 0 || x == W || z == 0 || z == D;
                level.setBlock(base.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(base.offset(x, -1, z), outer ? cornice : (rnd.nextInt(9) == 0 ? worn : lino), 2);
                level.setBlock(base.offset(x, H, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                level.setBlock(base.offset(x, H + 1, z), Blocks.STONE.defaultBlockState(), 2);
                if (!outer) continue;
                for (int y = 0; y < H; y++) {
                    BlockState s = y < 2 ? tile : y == H - 1 ? cornice : (rnd.nextInt(10) == 0 ? stain : plaster);
                    level.setBlock(base.offset(x, y, z), s, 2);
                }
            }
        }
        // Запертые двери на улицу за спиной у места появления.
        door(9, DoorHingeSide.LEFT);
        door(10, DoorHingeSide.RIGHT);
    }

    private void door(int dx, DoorHingeSide hinge) {
        BlockState d = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, TO_NEAR).setValue(DoorBlock.HINGE, hinge);
        set(dx, 0, 14, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(dx, 1, 14, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private void lights() {
        int[][] lamps = {{3, 2}, {10, 2}, {16, 2}, {3, 8}, {10, 7}, {16, 8}};
        for (int[] l : lamps) set(l[0], 4, l[1], ModBlocks.LOBBY_LAMP.get().defaultBlockState().setValue(LobbyLampBlock.LIT, true));
        // Тренировочный угол: две настоящие лампы под щитком угла и аварийная, которая горит, пока щиток выключен.
        set(15, 4, 11, ModBlocks.LAMP.get().defaultBlockState());
        set(18, 4, 11, ModBlocks.LAMP.get().defaultBlockState());
        set(16, 4, 13, ModBlocks.EMERGENCY_LAMP.get().defaultBlockState().setValue(EmergencyLampBlock.FLOOR, false));
    }

    // ---------- дальняя стена ----------

    private void farWall() {
        // Доска инструктажа (dx 0..6) и доска пропавших (dx 13..18): вместо штукатурки дерево и пробка.
        for (int y = 1; y <= 3; y++) {
            for (int dx = 0; dx <= 6; dx++) set(dx, y, -1, Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int dx = 13; dx <= 18; dx++) set(dx, y, -1, Blocks.BROWN_TERRACOTTA.defaultBlockState());
        }
        // Стол дежурного, стул, рация, полка с телевизорами.
        for (int dx = 8; dx <= 10; dx++) {
            set(dx, 0, 1, furniture("mcwfurnitures:dark_oak_desk", TO_NEAR, slabTop(Blocks.DARK_OAK_SLAB)));
            set(dx, 1, 0, slabTop(Blocks.DARK_OAK_SLAB));
            set(dx, 2, 0, Blocks.BLACK_TERRACOTTA.defaultBlockState());
            set(dx, 3, 0, Blocks.BLACK_TERRACOTTA.defaultBlockState());
        }
        set(9, 0, 0, furniture("mcwfurnitures:dark_oak_chair", TO_NEAR, stairs(Blocks.DARK_OAK_STAIRS, TO_FAR)));
        set(10, 1, 1, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, TO_NEAR).setValue(TrapDoorBlock.HALF, Half.BOTTOM));
        set(8, 1, 1, Blocks.LIGHTNING_ROD.defaultBlockState());
        // Архив плёнок под доской пропавших.
        for (int dx = 13; dx <= 18; dx++) set(dx, 0, 0, furniture("mcwfurnitures:dark_oak_bookshelf", TO_NEAR, Blocks.BOOKSHELF.defaultBlockState()));
        // Часы над дверью в никуда: в этом мире всегда полночь.
        frame(7, 3, 0, TO_NEAR, new ItemStack(Items.CLOCK));
    }

    // ---------- шкафчики и площадки ----------

    /** Шкафчики встроены в боковую стену, дверцы из железных люков; над ними полоса цвета команды. */
    private void lockers(boolean alpha) {
        int wallDx = alpha ? -1 : 20;
        int cellDx = alpha ? 0 : 19;
        Direction plate = alpha ? TO_RIGHT : TO_LEFT;
        BlockState body = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
        BlockState band = (alpha ? Blocks.BLUE_TERRACOTTA : Blocks.RED_TERRACOTTA).defaultBlockState();
        for (int dz = 3; dz <= 10; dz++) {
            set(wallDx, 0, dz, body);
            set(wallDx, 1, dz, body);
            set(wallDx, 2, dz, band);
            for (int y = 0; y <= 1; y++) {
                set(cellDx, y, dz, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, plate)
                        .setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, y == 0 ? Half.BOTTOM : Half.TOP));
            }
        }
        sign(cellDx, 2, 6, plate, Component.translatable(alpha ? "bh.sign.alpha" : "bh.sign.bravo"), Component.translatable("bh.sign.stand_here"));
        // Площадка 3 x 3 в полу перед шкафчиками.
        int cx = alpha ? 3 : 16;
        BlockState pad = (alpha ? Blocks.BLUE_TERRACOTTA : Blocks.RED_TERRACOTTA).defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(cx + dx, -1, 6 + dz, pad);
        }
        // Лавки раздевалки вдоль шкафчиков, проход к площадке свободен.
        int benchDx = alpha ? 1 : 18;
        for (int dz : new int[]{3, 4, 9, 10}) set(benchDx, 0, dz, slabBottom(Blocks.SPRUCE_SLAB));
    }

    // ---------- центр ----------

    private void center() {
        // Стол инструктажа 6 x 2, стулья с двух сторон.
        for (int dx = 7; dx <= 12; dx++) {
            for (int dz = 5; dz <= 6; dz++) set(dx, 0, dz, furniture("mcwfurnitures:dark_oak_table", TO_NEAR, slabTop(Blocks.DARK_OAK_SLAB)));
            set(dx, 0, 4, furniture("mcwfurnitures:dark_oak_chair", TO_NEAR, stairs(Blocks.DARK_OAK_STAIRS, TO_FAR)));
            set(dx, 0, 7, furniture("mcwfurnitures:dark_oak_chair", TO_FAR, stairs(Blocks.DARK_OAK_STAIRS, TO_NEAR)));
        }
        // Пульт «Запись»: тумба, на ней красная кнопка (её место в MapData: console).
        set(9, 0, 9, furniture("mcwfurnitures:dark_oak_counter", TO_NEAR, Blocks.POLISHED_DEEPSLATE.defaultBlockState()));
        set(10, 0, 9, furniture("mcwfurnitures:dark_oak_counter", TO_NEAR, Blocks.POLISHED_DEEPSLATE.defaultBlockState()));
        set(9, 1, 9, Blocks.CRIMSON_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR).setValue(ButtonBlock.FACING, TO_NEAR));
        // Курилка: урна и лавка.
        set(12, 0, 11, Blocks.CAULDRON.defaultBlockState());
        set(11, 0, 11, slabBottom(Blocks.SPRUCE_SLAB));
        set(11, 0, 10, slabBottom(Blocks.SPRUCE_SLAB));
    }

    // ---------- ближний левый угол: оружие, выдача, кофе ----------

    private void nearLeft() {
        // Стойка с оружием на ближней стене: тёмное дерево, в рамках оружие отрядов и находки.
        for (int dx = 1; dx <= 5; dx++) {
            for (int y = 1; y <= 2; y++) set(dx, y, 14, Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState());
        }
        frame(5, 2, 13, TO_FAR, kitGun(BhConfig.START_KIT.get()));
        frame(4, 2, 13, TO_FAR, kitGun(BhConfig.START_KIT_BRAVO.get()));
        frame(2, 2, 13, TO_FAR, new ItemStack(ModBlocks.FLASHLIGHT.get()));
        List<ItemStack> found = lootGuns();
        if (!found.isEmpty()) frame(5, 1, 13, TO_FAR, found.get(0));
        if (found.size() > 1) frame(3, 1, 13, TO_FAR, found.get(1));
        frame(1, 1, 13, TO_FAR, new ItemStack(ModGear.BATTERY.get()));
        frame(2, 1, 13, TO_FAR, new ItemStack(ModGear.BANDAGE.get()));
        // Выдача учебного набора: тумбы у левой стены (места в MapData: kit, kit2).
        set(0, 0, 11, furniture("mcwfurnitures:spruce_double_drawer", TO_RIGHT, Blocks.BARREL.defaultBlockState()));
        set(0, 0, 12, furniture("mcwfurnitures:spruce_double_drawer", TO_RIGHT, Blocks.BARREL.defaultBlockState()));
        // Кофе Петровича.
        set(6, 0, 13, furniture("mcwfurnitures:dark_oak_counter", TO_FAR, Blocks.SPRUCE_PLANKS.defaultBlockState()));
        set(7, 0, 13, furniture("mcwfurnitures:dark_oak_counter", TO_FAR, Blocks.SPRUCE_PLANKS.defaultBlockState()));
        set(7, 1, 13, Blocks.BREWING_STAND.defaultBlockState());
        set(6, 1, 13, Blocks.FLOWER_POT.defaultBlockState());
    }

    // ---------- ближний правый угол: тренировка ----------

    private void nearRight() {
        // Уступ 2 блока: стекло, железо, кирпич. Подняться по ступеньке, присесть у края, соскользнуть.
        for (int dx = 14; dx <= 16; dx++) {
            for (int dz = 12; dz <= 13; dz++) {
                set(dx, 0, dz, Blocks.STONE_BRICKS.defaultBlockState());
                set(dx, 1, dz, (dx == 14 ? Blocks.GLASS : dx == 15 ? Blocks.IRON_BLOCK : Blocks.CRACKED_STONE_BRICKS).defaultBlockState());
            }
        }
        set(13, 0, 13, stairs(Blocks.SPRUCE_STAIRS, TO_RIGHT));
        set(13, 0, 12, stairs(Blocks.SPRUCE_STAIRS, TO_RIGHT));
        // Учебная кассета на тумбочке, учебный выход в углу, щиток на правой стене.
        set(18, 0, 10, furniture("mcwfurnitures:dark_oak_end_table", TO_NEAR, Blocks.BARREL.defaultBlockState()));
        set(18, 0, 12, ModBlocks.EXIT_MARKER.get().defaultBlockState());
        set(19, 1, 13, ModBlocks.BREAKER.get().defaultBlockState().setValue(PowerSwitchBlock.FACING, TO_LEFT).setValue(PowerSwitchBlock.POWERED, true));
    }

    // ---------- стенды ----------

    private void stands() {
        int memo = BriefSheetBlockEntity.MEMO;
        int note = BriefSheetBlockEntity.NOTE;
        int photo = BriefSheetBlockEntity.PHOTO;
        int label = BriefSheetBlockEntity.LABEL;
        // Доска инструктажа: 1 «Задача», схема больницы, 2 «Ход матча».
        sheet(0, 1, 0, TO_NEAR, "task", memo, 0.05f, 0.05f, 1.9f, 1.9f, 0f);
        sheet(2, 1, 0, TO_NEAR, "plan", BriefSheetBlockEntity.PLAN, 0.15f, 0.0f, 2.7f, 2.03f, 0f);
        sheet(5, 1, 0, TO_NEAR, "match", memo, 0.05f, 0.05f, 1.9f, 1.9f, 0f);
        sheet(2, 3, 0, TO_NEAR, "board", label, 0.7f, 0.25f, 1.6f, 0.4f, 0f);
        sheet(1, 3, 0, TO_NEAR, "task_a", note, 0.15f, 0.05f, 0.62f, 0.62f, -6f);
        sheet(6, 3, 0, TO_NEAR, "match_a", note, 0.15f, 0.08f, 0.62f, 0.62f, 5f);
        // 7 «Связь и клавиши» рядом со столом дежурного, 6 мониторов над ним.
        sheet(11, 1, 0, TO_NEAR, "comms", memo, 0.05f, 0.05f, 1.9f, 1.9f, 0f);
        sheet(11, 3, 0, TO_NEAR, "comms_a", note, 0.4f, 0.1f, 0.7f, 0.62f, -4f);
        int cam = 1;
        for (int y = 3; y >= 2; y--) {
            for (int dx = 8; dx <= 10; dx++) sheet(dx, y, 1, TO_NEAR, Integer.toString(cam++), BriefSheetBlockEntity.MONITOR, 0.06f, 0.1f, 0.88f, 0.78f, 0f);
        }
        sheet(8, 0, 2, TO_NEAR, "radio", label, 0.6f, 0.45f, 1.6f, 0.4f, 0f);
        // Доска пропавших: 6 фото, подпись, 4 записки; подпись архива на полках.
        float[][] photos = {{13, 2, .15f, .1f, -3}, {14, 1, .3f, .35f, 2}, {15, 2, .1f, .05f, 1}, {16, 1, .4f, .3f, -2},
                {17, 2, .2f, .1f, 4}, {18, 1, .1f, .35f, -1}};
        for (int i = 0; i < photos.length; i++) {
            float[] p = photos[i];
            sheet((int) p[0], (int) p[1], 0, TO_NEAR, Integer.toString(i + 1), photo, p[2], p[3], 0.55f, 0.69f, p[4]);
        }
        sheet(15, 3, 0, TO_NEAR, "missing", label, 0.2f, 0.35f, 1.6f, 0.4f, 0f);
        sheet(13, 3, 0, TO_NEAR, "missing_a", note, 0.1f, 0.2f, 0.7f, 0.55f, -5f);
        sheet(17, 3, 0, TO_NEAR, "missing_b", note, 0.2f, 0.15f, 0.75f, 0.6f, 4f);
        sheet(16, 2, 0, TO_NEAR, "missing_c", note, 0.55f, 0.55f, 0.7f, 0.5f, -3f);
        sheet(18, 2, 0, TO_NEAR, "missing_d", note, 0.2f, 0.6f, 0.65f, 0.5f, 6f);
        sheet(15, 0, 1, TO_NEAR, "archive", label, 0.2f, 0.5f, 1.6f, 0.4f, 0f);

        // Левая стена: 8 «Если тебя убили» у дальнего угла, 3 «Снаряжение» над выдачей.
        sheet(0, 1, 1, TO_RIGHT, "death", memo, 0.05f, 0.05f, 1.9f, 1.9f, 0f);
        sheet(0, 3, 1, TO_RIGHT, "death_a", note, 0.5f, 0.1f, 0.65f, 0.6f, 3f);
        sheet(0, 1, 12, TO_RIGHT, "gear", memo, 0.05f, 0.05f, 1.9f, 1.9f, 0f);
        sheet(0, 3, 12, TO_RIGHT, "gear_a", note, 0.4f, 0.1f, 0.62f, 0.55f, -5f);
        sheet(1, 0, 12, TO_RIGHT, "kit", label, 0.2f, 0.45f, 1.6f, 0.4f, 0f);

        // Ближняя стена: 5 «Страх и раны» над кофе, 6 «Шум» над уступом, подписи оружия и тренировки.
        sheet(7, 2, 13, TO_FAR, "fear", memo, 0.05f, 0.05f, 1.9f, 1.9f, 0f);
        sheet(8, 3, 13, TO_FAR, "fear_a", note, 0.1f, 0.1f, 0.7f, 0.6f, 4f);
        sheet(7, 0, 12, TO_FAR, "coffee", label, 0.2f, 0.45f, 1.6f, 0.4f, 0f);
        sheet(5, 3, 13, TO_FAR, "weapons", label, 0.1f, 0.3f, 1.6f, 0.4f, 0f);
        sheet(16, 2, 13, TO_FAR, "noise", memo, 0.05f, 0.1f, 1.9f, 1.8f, 0f);
        sheet(14, 3, 13, TO_FAR, "noise_a", note, 0.2f, 0.2f, 0.7f, 0.55f, -4f);
        sheet(19, 2, 13, TO_FAR, "training", label, 0.1f, 0.4f, 1.6f, 0.4f, 0f);
        sheet(19, 0, 13, TO_FAR, "exit", label, 0.1f, 0.45f, 1.6f, 0.4f, 0f);

        // Правая стена: 4 «Свет» рядом со щитком угла.
        sheet(19, 1, 11, TO_LEFT, "light", memo, 0.05f, 0.05f, 1.9f, 1.9f, 0f);
        sheet(19, 3, 11, TO_LEFT, "light_a", note, 0.1f, 0.1f, 0.7f, 0.6f, -3f);
        sheet(19, 3, 12, TO_LEFT, "light_b", note, 0.3f, 0.15f, 0.6f, 0.5f, 5f);

        // Пульт «Запись»: подпись на тумбе, лицом к месту появления.
        sheet(9, 0, 10, TO_NEAR, "console", label, 0.2f, 0.45f, 1.6f, 0.4f, 0f);
    }

    private void details() {
        // Паутина по углам под потолком: здание давно стоит без людей.
        set(0, 4, 13, Blocks.COBWEB.defaultBlockState());
        set(19, 4, 0, Blocks.COBWEB.defaultBlockState());
        set(0, 4, 0, Blocks.COBWEB.defaultBlockState());
        // Трубы вдоль потолка у правой стены.
        for (int dz = 0; dz <= 13; dz++) set(19, 4, dz, dz == 0 ? Blocks.COBWEB.defaultBlockState()
                : Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.Z));
        set(19, 4, 11, Blocks.AIR.defaultBlockState());
        set(19, 4, 12, Blocks.AIR.defaultBlockState());
    }

    // ---------- помощники ----------

    /** Местная точка по координатам схемы: dx слева направо, dz от дальней стены к ближней. */
    private BlockPos at(int dx, int y, int dz) {
        return base.offset(20 - dx, y, 14 - dz);
    }

    private void set(int dx, int y, int dz, BlockState s) {
        BlockPos pos = at(dx, y, dz);
        level.setBlock(pos, s, 2 | 16);
        connect.add(pos);
    }

    private void reconnect() {
        for (BlockPos pos : connect) {
            BlockState s = level.getBlockState(pos);
            if (s.isAir() || s.getBlock() instanceof DoorBlock) continue;
            BlockState u = Block.updateFromNeighbourShapes(s, level, pos);
            if (u != s && !u.isAir()) level.setBlock(pos, u, 2 | 16);
        }
    }

    /** Мебель из Macaw's Furniture, повёрнутая лицом в нужную сторону; без мода ванильная замена (и запись в лог). */
    private BlockState furniture(String id, Direction facing, BlockState fallback) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (b == null || b == Blocks.AIR) {
            if (!missing.contains(id)) missing.add(id);
            return fallback;
        }
        return face(b.defaultBlockState(), facing);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState face(BlockState s, Direction facing) {
        for (Property<?> p : s.getProperties()) {
            if (p.getName().equals("facing") && p.getPossibleValues().contains(facing)) {
                return s.setValue((Property) p, facing);
            }
        }
        return s;
    }

    private static BlockState slabTop(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState slabBottom(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState stairs(Block stairs, Direction facing) {
        return stairs.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private void sheet(int dx, int y, int dz, Direction facing, String topic, int style, float x, float yOff, float w, float h, float tilt) {
        set(dx, y, dz, ModGear.BRIEF_SHEET.get().defaultBlockState().setValue(BriefSheetBlock.FACING, facing));
        if (level.getBlockEntity(at(dx, y, dz)) instanceof BriefSheetBlockEntity be) be.set(topic, style, x, yOff, w, h, tilt);
    }

    /** Рамка с предметом на стене: невидимая, закреплённая (не повернуть и не снять). */
    private void frame(int dx, int y, int dz, Direction facing, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemFrame f = new ItemFrame(level, at(dx, y, dz), facing);
        f.setItem(stack.copy(), false);
        CompoundTag t = new CompoundTag();
        f.saveWithoutId(t);
        t.putBoolean("Fixed", true);
        t.putBoolean("Invisible", true);
        t.putBoolean("Invulnerable", true);
        f.load(t);
        level.addFreshEntity(f);
    }

    private static ItemStack kitGun(List<? extends String> kit) {
        for (String spec : kit) {
            if (spec.contains("modern_kinetic_gun")) return ItemSpec.parse(spec);
        }
        return ItemStack.EMPTY;
    }

    /** Оружие из таблицы лута (строки вида weight;item;count;nbt). */
    private static List<ItemStack> lootGuns() {
        List<ItemStack> out = new ArrayList<>();
        for (String entry : BhConfig.LOOT_TABLE.get()) {
            if (!entry.contains("modern_kinetic_gun")) continue;
            int cut = entry.indexOf(';');
            ItemStack s = ItemSpec.parse(cut >= 0 ? entry.substring(cut + 1) : entry);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void sign(int dx, int y, int dz, Direction facing, Component... lines) {
        BlockPos pos = at(dx, y, dz);
        BlockState state = Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
        level.setBlock(pos, state, 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(DyeColor.LIGHT_GRAY).setHasGlowingText(true);
            for (int i = 0; i < lines.length && i < 4; i++) text = text.setMessage(i + (lines.length < 3 ? 1 : 0), lines[i]);
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
}
