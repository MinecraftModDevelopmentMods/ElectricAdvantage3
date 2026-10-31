package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.electricadvantage.generation.*;
import zone.moddev.mc.electricadvantage.infrastructure.ElectricBlock;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GenerationGameTests {
    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void steamConversionBackpressureAndPersistence(GameTestHelper h) {
        var be = place(h, "steam_powered_generator", new BlockPos(4,3,4));
        var steam = zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get();
        var fluid = be.fluidCapability();
        h.assertTrue(fluid.fill(new FluidStack(Fluids.WATER,1000), FluidAction.EXECUTE)==0, "Only PA3 steam is accepted");
        h.assertTrue(fluid.fill(new FluidStack(steam,32), FluidAction.SIMULATE)==32 && be.screenSteam().isEmpty(), "Steam fill simulation");
        fluid.fill(new FluidStack(steam,32), FluidAction.EXECUTE);
        tick(h, be);
        h.assertTrue(be.energy.stored()==4 && be.screenSteam().isEmpty(), "32 steam / electricity and 0.25 electricity / FE means 32 mB -> 4 FE");
        fluid.fill(new FluidStack(steam,7), FluidAction.EXECUTE);
        tick(h,be);
        h.assertTrue(be.energy.stored()==4 && be.screenSteam().getAmount()==7, "Sub-FE steam remains in tank");
        fluid.fill(new FluidStack(steam,1), FluidAction.EXECUTE);
        tick(h,be);
        h.assertTrue(be.energy.stored()==5 && be.screenSteam().isEmpty(), "Whole-FE conversion preserves the remainder");
        be.energy.setStored(be.energy.capacity());
        fluid.fill(new FluidStack(steam,32), FluidAction.EXECUTE);
        tick(h,be);
        h.assertTrue(be.screenSteam().getAmount()==32, "Full energy buffer must not consume steam");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored=(GenerationBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());
        restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(restored.energy.stored()==be.energy.stored() && restored.screenSteam().getAmount()==32, "Steam and energy persist");
        h.setBlock(4,3,5,Blocks.REDSTONE_BLOCK);
        be.energy.setStored(0);
        tick(h,be);
        h.assertTrue(be.energy.stored()==0 && be.screenSteam().getAmount()==32, "Redstone disables conversion");
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure", timeoutTicks = 180)
    public static void batteryArraysNeverCycleAndFeedLoads(GameTestHelper h) {
        var first=place(h,"electric_battery_array",new BlockPos(2,3,3));
        var second=place(h,"electric_battery_array",new BlockPos(4,3,3));
        first.items.setStackInSlot(0,stack("lead_acid_battery"));
        var empty=stack("lead_acid_battery");
        empty.getCapability(Capabilities.EnergyStorage.ITEM).extractEnergy(Integer.MAX_VALUE,false);
        second.items.setStackInSlot(0,empty);
        h.setBlock(3,3,3,block("electric_conduit"));
        h.runAfterDelay(35,()->{
            h.assertTrue(first.screenEnergy()==1000 && second.screenEnergy()==0,"Connected arrays must not exchange energy");
            h.setBlock(3,3,4,block("electric_conduit"));
            h.setBlock(4,3,4,block("led_bar"));
            h.runAfterDelay(35,()->{
                h.assertBlockProperty(new BlockPos(4,3,4),ElectricBlock.LIT,true);
                h.assertTrue(first.screenEnergy()<1000 && second.screenEnergy()==0,"Backup powers loads without charging another array");
                h.setBlock(2,3,2,Blocks.REDSTONE_BLOCK);
                h.runAfterDelay(50,()->{
                    h.assertBlockProperty(new BlockPos(4,3,4),ElectricBlock.LIT,false);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void arrayAggregationSlotsAndSavedCharge(GameTestHelper h) {
        var be=place(h,"electric_battery_array",new BlockPos(4,3,4));
        h.assertTrue(be.items.getSlots()==8 && !be.items.insertItem(0,new ItemStack(Items.DIRT),false).isEmpty(),"Eight battery-only slots");
        be.items.setStackInSlot(0,stack("lead_acid_battery"));
        be.items.setStackInSlot(1,stack("lithium_battery"));
        var cap=be.capability();
        h.assertTrue(cap.getEnergyStored()==9000 && cap.getMaxEnergyStored()==9000,"Mixed battery capacities aggregate");
        h.assertTrue(cap.extractEnergy(1234,true)==1234 && cap.getEnergyStored()==9000,"Array simulation is read-only");
        cap.extractEnergy(1234,false);
        h.assertTrue(cap.getEnergyStored()==7766,"Array discharge uses item charge");
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored=(GenerationBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());
        restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(restored.screenEnergy()==7766 && restored.screenCapacity()==9000,"Inventory and charge persist");
        h.assertTrue(restored.capability().receiveEnergy(Integer.MAX_VALUE,false)==1234,"Array charging stops at battery capacity");
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void solarLightWeatherAndRedstone(GameTestHelper h) {
        var level=h.getLevel();
        long oldTime=level.getDayTime();
        boolean rain=level.isRaining(),thunder=level.isThundering();
        float rainLevel=level.getRainLevel(1), thunderLevel=level.getThunderLevel(1);
        try {
            var be=place(h,"photovoltaic_generator",new BlockPos(5,3,5));
            for(int y=4;y<12;y++) h.setBlock(5,y,5,Blocks.AIR);
            level.setDayTime(6000); level.setWeatherParameters(1000,0,false,false); level.setRainLevel(0); level.setThunderLevel(0);
            h.assertTrue(be.solarIntensity()>0.99,"Daylight solar intensity");
            tick(h,be);
            h.assertTrue(be.energy.stored()>0,"Sunlight generates FE");
            int daylightComparator=be.comparator();
            level.setWeatherParameters(0,1000,true,false); level.setRainLevel(1); level.setThunderLevel(0);
            h.assertTrue(be.solarIntensity()<=be.settings.rainFactor()+0.0001,"Rain reduces output");
            level.setWeatherParameters(0,1000,true,true); level.setRainLevel(1); level.setThunderLevel(1);
            h.assertTrue(be.solarIntensity()<=be.settings.thunderFactor()+0.0001,"Thunder reduces output");
            level.setDayTime(18000); level.setWeatherParameters(1000,0,false,false); level.setRainLevel(0); level.setThunderLevel(0);
            h.assertTrue(be.solarIntensity()==0,"No unlit night generation");
            level.setDayTime(6000); h.setBlock(5,3,6,Blocks.REDSTONE_BLOCK);
            long stored=be.energy.stored(); tick(h,be);
            h.assertTrue(be.energy.stored()==stored && be.comparator()==0 && daylightComparator>0,"Redstone disables generation/comparator output");
        } finally { level.setDayTime(oldTime); level.setWeatherParameters(1000,1000,rain,thunder); level.setRainLevel(rainLevel); level.setThunderLevel(thunderLevel); }
        h.succeed();
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure", timeoutTicks = 100)
    public static void hydroFlowAndBladeDamage(GameTestHelper h) {
        for(int x=2;x<=7;x++) for(int z=2;z<=4;z++) h.setBlock(x,1,z,Blocks.STONE);
        for(int x=2;x<=7;x++){h.setBlock(x,2,2,Blocks.STONE);h.setBlock(x,2,4,Blocks.STONE);}
        h.setBlock(3,2,3,Blocks.WATER);
        h.setBlock(4,2,3,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1));
        var be=place(h,"hydroelectric_generator",new BlockPos(4,3,3));
        h.assertTrue(be.waterFlow().horizontalDistanceSqr()>0.00001,"Horizontal water flow spins turbine");
        tick(h,be);
        h.assertTrue(be.energy.stored()>0,"Flowing water generates electricity");
        var victim=h.spawn(EntityType.PIG,4.5f,2,3.5f);
        h.runAfterDelay(12,()->{
            h.assertTrue(victim.getHealth()<victim.getMaxHealth(),"Turbine damages entities in its blade volume");
            h.setBlock(4,2,3,Blocks.STONE);
            long before=be.energy.stored(); tick(h,be);
            h.assertTrue(be.energy.stored()==before,"No water flow means no hydro generation");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void generatorScreensAndBatteryMenu(GameTestHelper h) {
        int x=2;
        for(String name: java.util.List.of("photovoltaic_generator","hydroelectric_generator","steam_powered_generator","electric_battery_array")) {
            var be=place(h,name,new BlockPos(x++,3,4));
            h.assertTrue(zone.moddev.mc.lib.gui.Screens.get(id(name)).isPresent(),"XML screen loaded: "+name);
            var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
            player.setPos(be.getBlockPos().getX()+0.5,be.getBlockPos().getY()+0.5,be.getBlockPos().getZ()+0.5);
            var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,player.getInventory(),be);
            h.assertTrue(menu.stillValid(player),"MMDLib menu binds to the placed machine");
            if(be.array()) {
                h.assertTrue(menu.slots.size()==44,"Eight real battery slots plus player inventory");
                h.assertTrue(menu.getSlot(0).mayPlace(stack("lithium_battery"))
                        && !menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)),"Battery menu respects item filters");
            }

        }
        h.succeed();
    }
    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure",timeoutTicks=150)
    public static void powerAdvantageSteamNetwork(GameTestHelper h) {
        h.setBlock(1,3,3,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("poweradvantage:infinite_steam")));
        h.setBlock(2,3,3,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("poweradvantage:fluid_pipe")));
        var be=place(h,"steam_powered_generator",new BlockPos(3,3,3));
        h.setBlock(3,3,4,block("electric_conduit"));
        h.setBlock(4,3,4,block("led_bar"));
        h.runAfterDelay(60,()->{
            h.assertTrue(be.screenSteam().getAmount()>0 && be.energy.stored()>0,"PA3 pipe supplies real steam to generator");
            h.assertBlockProperty(new BlockPos(4,3,4),ElectricBlock.LIT,true);
            h.succeed();
        });
    }

    @GameTest(templateNamespace = ElectricAdvantage.MOD_ID, template = "infrastructure")
    public static void generationRecipes(GameTestHelper h) {
        var psu=stack("psu");var wire=stack("electric_conduit");var circuit=stack("control_circuit");
        var plate=new ItemStack(Items.IRON_INGOT);
        var frame=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("poweradvantage:steel_frame")));
        var pipe=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("poweradvantage:fluid_pipe")));
        var gear=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mmdlib:iron_gear")));
        craft(h,"steam_powered_generator",java.util.List.of(psu,pipe,circuit,plate,frame,plate));
        craft(h,"hydroelectric_generator",java.util.List.of(psu,gear,gear,plate,frame,plate));
        craft(h,"electric_battery_array",java.util.List.of(psu,new ItemStack(Items.CHEST),ItemStack.EMPTY,plate,frame,plate));
        var glass=new ItemStack(Items.GLASS_PANE);var silicon=stack("silicon_ingot");
        craft(h,"photovoltaic_generator",java.util.List.of(glass,glass,glass,silicon,silicon,silicon,wire,psu,wire));
        h.succeed();
    }
    private static void craft(GameTestHelper h,String name,java.util.List<ItemStack> stacks){
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,stacks.size()/3,stacks);
        var level=h.getLevel();
        var recipe=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level).orElseThrow();
        var result=recipe.value().assemble(input,level.registryAccess());
        h.assertTrue(result.is(stack(name).getItem()) && result.getCount()==1,"Machine crafting output: "+name);
    }

    private static GenerationBlockEntity place(GameTestHelper h,String name,BlockPos pos){h.setBlock(pos,block(name));return (GenerationBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));}
    private static void tick(GameTestHelper h,GenerationBlockEntity be){GenerationBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,name);}
    private static Block block(String name){return BuiltInRegistries.BLOCK.get(id(name));}
    private static ItemStack stack(String name){return new ItemStack(BuiltInRegistries.ITEM.get(id(name)));}
}
