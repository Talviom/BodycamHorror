package com.talviom.bodycamhorror.gear;

import com.talviom.bodycamhorror.BodycamHorror;
import com.talviom.bodycamhorror.lobby.PosterBlock;
import com.talviom.bodycamhorror.lobby.PosterBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Снаряжение матча (бинт, батарейка) и плакат с правилами в лобби.
 * Бинт заменил золотое яблоко: лечит медленно и занимает руки.
 */
public final class ModGear {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BodycamHorror.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BodycamHorror.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BodycamHorror.MOD_ID);

    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage", () -> new BandageItem(new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> BATTERY = ITEMS.register("battery", () -> new BatteryItem(new Item.Properties().stacksTo(4)));

    /** Плакат «полевое руководство» на стене лобби: правила, управление, опасности, что делать с Жильцом. */
    public static final RegistryObject<Block> POSTER = BLOCKS.register("lobby_poster", () -> new PosterBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(-1f, 3600000f).sound(SoundType.WOOD)
                    .noLootTable().noOcclusion().noCollission()));
    public static final RegistryObject<Item> POSTER_ITEM = ITEMS.register("lobby_poster", () -> new BlockItem(POSTER.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<PosterBlockEntity>> POSTER_BE = BLOCK_ENTITIES.register("lobby_poster",
            () -> BlockEntityType.Builder.of(PosterBlockEntity::new, POSTER.get()).build(null));

    /** Лобби 1.0: лист на стене (памятка, записка, фото, схема, монитор), текст из lang-файлов (BriefSheetRenderer). */
    public static final RegistryObject<Block> BRIEF_SHEET = BLOCKS.register("brief_sheet", () -> new com.talviom.bodycamhorror.lobby.BriefSheetBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(-1f, 3600000f).sound(SoundType.WOOD)
                    .noLootTable().noOcclusion().noCollission()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.talviom.bodycamhorror.lobby.BriefSheetBlockEntity>> BRIEF_SHEET_BE = BLOCK_ENTITIES.register("brief_sheet",
            () -> BlockEntityType.Builder.of(com.talviom.bodycamhorror.lobby.BriefSheetBlockEntity::new, BRIEF_SHEET.get()).build(null));

    /** Лобби: граммофон (id прежний, tape_recorder), из которого играет музыка лобби (TapeRecorderBlock). */
    public static final RegistryObject<Block> TAPE_RECORDER = BLOCKS.register("tape_recorder", () -> new com.talviom.bodycamhorror.lobby.TapeRecorderBlock(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                    .strength(1f).sound(net.minecraft.world.level.block.SoundType.METAL).noLootTable().noOcclusion()));
    public static final RegistryObject<Item> TAPE_RECORDER_ITEM = ITEMS.register("tape_recorder",
            () -> new net.minecraft.world.item.BlockItem(TAPE_RECORDER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<com.talviom.bodycamhorror.lobby.TapeRecorderBlockEntity>> TAPE_RECORDER_BE = BLOCK_ENTITIES.register("tape_recorder",
            () -> BlockEntityType.Builder.of(com.talviom.bodycamhorror.lobby.TapeRecorderBlockEntity::new, TAPE_RECORDER.get()).build(null));
    /** Лобби 1.0: папка инструктажа. Есть только в лобби, на старте раунда забирается вместе со всем инвентарём. */
    public static final RegistryObject<Item> BRIEFING_FOLDER = ITEMS.register("briefing_folder",
            () -> new com.talviom.bodycamhorror.lobby.BriefingFolderItem(new Item.Properties().stacksTo(1)));

    private ModGear() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModGear::addGearToTabs);
    }

    private static void addGearToTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            e.accept(BANDAGE);
            e.accept(BATTERY);
            e.accept(POSTER_ITEM);
            e.accept(BRIEFING_FOLDER);
        }
    }
}
