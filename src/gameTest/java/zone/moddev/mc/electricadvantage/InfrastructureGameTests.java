package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.electricadvantage.infrastructure.ElectricBlock;
import zone.moddev.mc.electricadvantage.infrastructure.ElectricBlockEntity;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfrastructureGameTests {
    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure", timeoutTicks = 240)
    public static void powerThroughScaffoldAndSwitch(GameTestHelper helper) {
        helper.setBlock(1, 2, 2, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("poweradvantage:infinite_power")));
        helper.setBlock(2, 2, 2, block("electric_conduit"));
        helper.setBlock(3, 2, 2, block("electric_track"));
        helper.setBlock(4, 2, 2, block("electric_switch"));
        helper.setBlock(5, 2, 2, block("electric_conduit"));
        helper.setBlock(6, 2, 2, block("led_bar"));
        helper.useBlock(new BlockPos(4, 2, 2));
        helper.runAfterDelay(45, () -> {
            helper.assertBlockProperty(new BlockPos(6, 2, 2), ElectricBlock.LIT, true);
            var scaffoldPos = helper.absolutePos(new BlockPos(3, 2, 2));
            var state = helper.getLevel().getBlockState(scaffoldPos);
            helper.assertTrue(Block.isShapeFullBlock(state.getCollisionShape(helper.getLevel(), scaffoldPos)), "Scaffold collision must fill a block");
            helper.assertBlockProperty(new BlockPos(2, 2, 2), ElectricBlock.CONNECTIONS.get(Direction.EAST), true);
            helper.useBlock(new BlockPos(4, 2, 2));
            var disabled = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                    helper.absolutePos(new BlockPos(4, 2, 2)), Direction.WEST);
            helper.assertTrue(disabled != null && disabled.receiveEnergy(100, false) == 0, "Open switch refuses input");
            helper.runAfterDelay(55, () -> {
                helper.assertBlockProperty(new BlockPos(6, 2, 2), ElectricBlock.LIT, false);
                helper.useBlock(new BlockPos(4, 2, 2));
                helper.runAfterDelay(45, () -> {
                    helper.assertBlockProperty(new BlockPos(6, 2, 2), ElectricBlock.LIT, true);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void pipeCleanupAndEnergyPersistence(GameTestHelper helper) {
        var pipe = block("pump_pipe_electric");
        for (int y = 1; y <= 5; y++) helper.setBlock(2, y, 2, pipe);
        helper.destroyBlock(new BlockPos(2, 4, 2));
        helper.assertBlockPresent(pipe, new BlockPos(2, 5, 2));
        for (int y = 1; y <= 4; y++) helper.assertBlockNotPresent(pipe, new BlockPos(2, y, 2));
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id("pump_pipe_electric")), "Pump pipe retains its search-only block item");
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        var tab = helper.getLevel().registryAccess().registryOrThrow(Registries.CREATIVE_MODE_TAB).get(id("materials"));
        helper.assertTrue(tab.getDisplayItems().stream().noneMatch(stack -> stack.is(pipe.asItem()))
                && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(pipe.asItem())),
                "Pipe item belongs in search but not the main creative tab");
        var drops = Block.getDrops(pipe.defaultBlockState(), helper.getLevel(), helper.absolutePos(new BlockPos(2, 5, 2)),
                null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.isEmpty(), "Pipe must not drop items");
        helper.setBlock(5, 2, 2, block("electric_conduit"));
        var position = helper.absolutePos(new BlockPos(5, 2, 2));
        var be = (ElectricBlockEntity)helper.getLevel().getBlockEntity(position);
        be.energy.setStored(321);
        var saved = be.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = (ElectricBlockEntity)be.getType().create(position, be.getBlockState());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.energy.stored() == 321, "Cable buffered energy must persist");
        helper.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void oreSpawnProviderAndProfile(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = zone.moddev.mc.orespawn.api.OreSpawnApi.getActiveProfile(level.getServer()).orElseThrow();
        var ores = profile.toJson().getAsJsonObject("ores");
        for (String material : java.util.List.of("lithium", "sulfur")) {
            var rule = id("ore/" + material);
            helper.assertTrue(profile.oreIds().contains(rule), "OreSpawn active profile contains " + rule);
            var ore = ores.getAsJsonObject(rule.toString());
            helper.assertTrue(ore.get("block").getAsString().equals(id(material + "_ore").toString()), "Ore output block: " + material);
            var placement = ore.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
            helper.assertTrue(placement.get("min_y").getAsInt() == 1 && placement.get("max_y").getAsInt() == 32,
                    "OreSpawn placement height band: " + material);
            helper.assertTrue(placement.get("frequency").getAsDouble() == (material.equals("lithium") ? 0.125 : 1.0),
                    "OreSpawn placement frequency: " + material);
            helper.assertTrue(placement.getAsJsonArray("host_tags").asList().stream()
                    .anyMatch(tag -> tag.getAsString().equals("minecraft:stone_ore_replaceables")), "Explicit stone host tag");
            helper.assertTrue(!level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).containsKey(id(material + "_ore"))
                    && !level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(id(material + "_ore")),
                    "Native ore features removed to prevent duplicate generation");
        }
        helper.succeed();
    }
    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void redstoneSwitchAndInputLimits(GameTestHelper helper) {
        var relative = new BlockPos(3, 2, 3);
        helper.setBlock(relative, block("electric_switch"));
        helper.setBlock(3, 2, 4, Blocks.REDSTONE_BLOCK);
        helper.assertBlockProperty(relative, ElectricBlock.ACTIVE, true);
        var cap = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(relative), Direction.UP);
        helper.assertTrue(cap != null && !cap.canExtract(), "Cable capabilities must be input-only");
        helper.assertTrue(cap.receiveEnergy(Integer.MAX_VALUE, true) == 4096 && cap.getEnergyStored() == 0,
                "Input simulation and transfer limit");
        helper.assertTrue(cap.receiveEnergy(Integer.MAX_VALUE, false) == 4096 && cap.receiveEnergy(1, false) == 0,
                "Cable input is bounded by capacity");
        helper.setBlock(3, 2, 4, Blocks.AIR);
        helper.assertBlockProperty(relative, ElectricBlock.ACTIVE, false);
        helper.assertTrue(cap.receiveEnergy(1, false) == 0 && cap.extractEnergy(1, false) == 0,
                "Disabled switch rejects transfer through previously acquired handles");
        helper.useBlock(relative);
        helper.setBlock(2, 2, 3, block("electric_conduit"));
        helper.assertBlockProperty(relative, ElectricBlock.ACTIVE, true);
        helper.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void infrastructureCrafting(GameTestHelper helper) {
        for (ItemStack insulation : java.util.List.of(stack("petrolplastic_ingot"), new ItemStack(Items.DRIED_KELP))) {
            var copper = new ItemStack(Items.COPPER_INGOT);
            craft(helper, "electric_conduit", 6, 3, java.util.List.of(insulation, insulation, insulation,
                    copper, copper, copper, insulation, insulation, insulation));
            craft(helper, "electric_conduit", 1, 2, java.util.List.of(insulation, insulation,
                    new ItemStack(Items.STICK), new ItemStack(Items.STICK)));
        }
        var cable = stack("electric_conduit");
        var frame = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("poweradvantage:steel_frame")));
        craft(helper, "electric_track", 1, 2, java.util.List.of(cable, frame));
        craft(helper, "electric_switch", 1, 3, java.util.List.of(ItemStack.EMPTY, new ItemStack(Items.LEVER),
                ItemStack.EMPTY, cable, frame, cable));
        var glass = new ItemStack(Items.GLASS_PANE);
        var chip = stack("integrated_circuit");
        craft(helper, "led_bar", 3, 3, java.util.List.of(glass, glass, glass, chip, chip, chip, cable, cable, cable));
        helper.succeed();
    }
    private static void craft(GameTestHelper helper, String output, int count, int width, java.util.List<ItemStack> stacks) {
        var input = net.minecraft.world.item.crafting.CraftingInput.of(width, stacks.size() / width, stacks);
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level).orElseThrow();
        var result = recipe.value().assemble(input, level.registryAccess());
        helper.assertTrue(result.is(stack(output).getItem()) && result.getCount() == count, "Crafting output: " + output);
    }
    private static ItemStack stack(String name) { return new ItemStack(BuiltInRegistries.ITEM.get(id(name))); }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, name); }
    private static Block block(String name) { return BuiltInRegistries.BLOCK.get(id(name)); }
}
