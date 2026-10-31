package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.electricadvantage.farming.*;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmingGameTests {
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void wheatGrowsWithoutConsumingSeedOrSoil(GameTestHelper h){
        var be=chamber(h,4,3,4);
        be.items.setStackInSlot(0,new ItemStack(Items.DIRT));be.items.setStackInSlot(3,new ItemStack(Items.WHEAT_SEEDS));
        powered(h,be,799);
        h.assertTrue(be.screenProgress0()==799&&count(be,Items.WHEAT)==0,"Wheat takes 800 growth ticks");
        powered(h,be,1);
        h.assertTrue(count(be,Items.WHEAT)==1&&be.screenProgress0()==0,"Fertility-0 soil yields exactly the base wheat");
        h.assertTrue(be.items.getStackInSlot(0).is(Items.DIRT)&&be.items.getStackInSlot(3).is(Items.WHEAT_SEEDS),"Soil and seed are catalysts");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void soilRestrictionsAndSlotRules(GameTestHelper h){
        var be=chamber(h,4,3,4);var handler=be.itemCapability();
        h.assertTrue(!handler.insertItem(0,new ItemStack(Items.DIAMOND),false).isEmpty(),"Non-soil items are rejected from soil slots");
        h.assertTrue(!handler.insertItem(3,new ItemStack(Items.DIAMOND),false).isEmpty(),"Non-seed items are rejected from seed slots");
        h.assertTrue(!handler.insertItem(12,new ItemStack(BuiltInRegistries.ITEM.get(id("shearing_upgrade"))),false).isEmpty(),"Automation cannot insert upgrades");
        h.assertTrue(handler.insertItem(3,new ItemStack(Items.NETHER_WART,5),false).getCount()==4,"Seed slots hold a single catalyst item");
        handler.insertItem(0,new ItemStack(Items.DIRT),false);
        be.setGreenhouse(50);ticks(h,be,10);
        h.assertTrue(be.screenProgress0()==0&&be.screenGreenhouse()==50,"Nether wart does not grow on dirt and uses no power");
        h.assertTrue(handler.extractItem(3,1,false).isEmpty(),"Automation cannot pull catalysts out");
        be.items.setStackInSlot(0,new ItemStack(Items.SOUL_SAND));powered(h,be,1);
        h.assertTrue(be.screenProgress0()==1,"Nether wart grows on soul sand");
        be.items.setStackInSlot(1,new ItemStack(Items.WATER_BUCKET));be.items.setStackInSlot(4,new ItemStack(Items.KELP));
        be.items.setStackInSlot(2,new ItemStack(Items.END_STONE));be.items.setStackInSlot(5,new ItemStack(Items.CHORUS_FLOWER));
        powered(h,be,1);
        h.assertTrue(be.screenProgress1()==1&&be.screenProgress2()==1,"Water bucket and end stone act as soils");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void fertilityBonusAndShearingUpgrade(GameTestHelper h){
        var be=chamber(h,4,3,4);
        be.items.setStackInSlot(0,new ItemStack(Items.DIRT));be.items.setStackInSlot(3,new ItemStack(Items.STICK));
        be.items.setStackInSlot(1,new ItemStack(Items.MUD));be.items.setStackInSlot(4,new ItemStack(Items.STICK));
        powered(h,be,5);
        h.assertTrue(count(be,Items.DIAMOND)==3&&count(be,Items.EMERALD)==0,"Fertility 1 adds one item per full bonus roll; upgrade results stay locked");
        for(int slot=6;slot<12;slot++)be.items.setStackInSlot(slot,ItemStack.EMPTY);
        be.items.setStackInSlot(12,new ItemStack(BuiltInRegistries.ITEM.get(id("shearing_upgrade"))));
        powered(h,be,5);
        h.assertTrue(count(be,Items.DIAMOND)==3&&count(be,Items.EMERALD)==2,"The shearing upgrade unlocks its results in every lane");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void fullOutputHoldsHarvestAcrossReload(GameTestHelper h){
        var be=chamber(h,4,3,4);
        be.items.setStackInSlot(0,new ItemStack(Items.DIRT));be.items.setStackInSlot(3,new ItemStack(Items.STICK));
        for(int slot=6;slot<12;slot++)be.items.setStackInSlot(slot,new ItemStack(Items.STONE,64));
        powered(h,be,5);
        h.assertTrue(be.pendingHarvest(0).size()==1&&be.pendingHarvest(0).getFirst().is(Items.DIAMOND),"A harvest that does not fit is held");
        be.setGreenhouse(50);ticks(h,be,20);
        h.assertTrue(be.screenGreenhouse()==50&&be.screenProgress0()==0,"A lane holding a harvest does not grow or use power");
        be=restore(h,be);
        h.assertTrue(be.pendingHarvest(0).size()==1,"Held harvest survives save/reload");
        be.items.setStackInSlot(11,ItemStack.EMPTY);ticks(h,be,1);
        h.assertTrue(be.pendingHarvest(0).isEmpty()&&be.items.getStackInSlot(11).is(Items.DIAMOND),"Held harvest is inserted once there is room");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void progressSurvivesReloadPowerLossAndRedstone(GameTestHelper h){
        var be=chamber(h,4,3,4);
        be.items.setStackInSlot(0,new ItemStack(Items.DIRT));be.items.setStackInSlot(3,new ItemStack(Items.WHEAT_SEEDS));
        powered(h,be,300);be=restore(h,be);
        h.assertTrue(be.screenProgress0()==300,"Growth progress survives save/reload (EA2 reset it)");
        be.setGreenhouse(0);ticks(h,be,10);
        h.assertTrue(be.screenProgress0()==300,"Losing power pauses growth without losing progress");
        h.setBlock(4,3,5,Blocks.REDSTONE_BLOCK);powered(h,be,10);
        h.assertTrue(be.screenProgress0()==300&&be.screenGreenhouse()==be.settings.capacity(),"Redstone pauses growth");
        h.setBlock(4,3,5,Blocks.AIR);powered(h,be,1);
        h.assertTrue(be.screenProgress0()==301,"Growth resumes");
        be.items.setStackInSlot(3,new ItemStack(Items.CARROT));powered(h,be,1);
        h.assertTrue(be.screenProgress0()==1,"Changing the seed restarts growth");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void duplicationPlantsCopyTheirSeed(GameTestHelper h){
        var be=chamber(h,4,3,4);
        be.items.setStackInSlot(0,new ItemStack(Items.DIRT));be.items.setStackInSlot(3,new ItemStack(Items.POPPY));
        be.items.setStackInSlot(1,new ItemStack(Items.WATER_BUCKET));be.items.setStackInSlot(4,new ItemStack(Items.BRAIN_CORAL_FAN));
        powered(h,be,800);
        h.assertTrue(count(be,Items.POPPY)==1&&count(be,Items.BRAIN_CORAL_FAN)==1,"Tag-based duplication recipes yield a copy of the seed");
        h.assertTrue(!be.itemCapability().insertItem(5,new ItemStack(Items.WITHER_ROSE),true).isEmpty(),"Wither roses are not duplicated");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void controllerConvertsAndSharesAcrossTouchingChambers(GameTestHelper h){
        var controller=controller(h,3,3,3);
        var a=chamber(h,4,3,3);var b=chamber(h,5,3,3);var far=chamber(h,8,3,3);
        controller.energy.setStored(10000);controller.fluidCapability().fill(new FluidStack(Fluids.WATER,4000),FluidAction.EXECUTE);
        controller.items.setStackInSlot(0,new ItemStack(Items.BONE_MEAL,2));
        GreenhouseControllerBlockEntity.tick(h.getLevel(),controller.getBlockPos(),controller.getBlockState(),controller);
        h.assertTrue(controller.chambers().size()==2,"Only chambers touching the controller or its cluster connect");
        h.assertTrue(controller.fertilizer()==184&&controller.items.getStackInSlot(0).getCount()==1,"One bone meal adds 200 fertilizer; 16 units use 16");
        h.assertTrue(controller.energy.stored()==10000-16*32&&controller.screenWater().getAmount()==4000-16*2,"Each unit costs 32 FE and 2 mB of water");
        h.assertTrue(a.greenhouse()==8&&b.greenhouse()==8&&far.greenhouse()==0,"Power is shared equally across the cluster");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void controllerInputsComparatorAndRedstone(GameTestHelper h){
        var controller=controller(h,4,3,4);
        h.assertTrue(controller.fluidCapability().fill(new FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE)==0,"Only water is accepted");
        h.assertTrue(!controller.itemCapability().insertItem(0,new ItemStack(Items.DIRT),false).isEmpty(),"Only fertilizer is accepted");
        h.assertTrue(controller.itemCapability().insertItem(0,new ItemStack(Items.BONE_MEAL),false).isEmpty(),"Bone meal is a fertilizer");
        controller.fluidCapability().fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);
        h.assertTrue(controller.comparator()==0,"Comparator is off without energy and fertilizer");
        controller.energy.setStored(10000);h.setBlock(4,3,5,Blocks.REDSTONE_BLOCK);
        GreenhouseControllerBlockEntity.tick(h.getLevel(),controller.getBlockPos(),controller.getBlockState(),controller);
        h.assertTrue(controller.greenhouse()==0&&controller.comparator()==15,"Redstone stops conversion; comparator reports a full set of inputs");
        h.setBlock(4,3,5,Blocks.AIR);
        GreenhouseControllerBlockEntity.tick(h.getLevel(),controller.getBlockPos(),controller.getBlockState(),controller);
        h.assertTrue(controller.greenhouse()==16,"Conversion resumes without redstone");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void screensBindSlotsAndValues(GameTestHelper h){
        var chamber=chamber(h,4,3,4);var controller=controller(h,6,3,4);
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var chamberMenu=new zone.moddev.mc.lib.gui.runtime.MachineMenu(9,player.getInventory(),chamber);
        var controllerMenu=new zone.moddev.mc.lib.gui.runtime.MachineMenu(10,player.getInventory(),controller);
        h.assertTrue(chamberMenu.slots.size()==13+36,"Chamber: three soils, three seeds, six outputs, one upgrade and the player inventory");
        h.assertTrue(controllerMenu.slots.size()==1+36,"Controller: fertilizer slot and the player inventory");
        h.assertTrue(chamberMenu.getSlot(12).getMaxStackSize()==1,"Upgrade slot holds one item");
        h.succeed();
    }
    private static GrowthChamberBlockEntity chamber(GameTestHelper h,int x,int y,int z){
        h.setBlock(x,y,z,BuiltInRegistries.BLOCK.get(id("growth_chamber")));
        return(GrowthChamberBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x,y,z)));
    }
    private static GreenhouseControllerBlockEntity controller(GameTestHelper h,int x,int y,int z){
        h.setBlock(x,y,z,BuiltInRegistries.BLOCK.get(id("growth_chamber_controller")));
        return(GreenhouseControllerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x,y,z)));
    }
    private static void ticks(GameTestHelper h,GrowthChamberBlockEntity be,int count){for(int n=0;n<count;n++)GrowthChamberBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static void powered(GameTestHelper h,GrowthChamberBlockEntity be,int count){for(int n=0;n<count;n++){be.setGreenhouse(be.settings.capacity());ticks(h,be,1);}}
    private static int count(GrowthChamberBlockEntity be,Item item){int total=0;for(int slot=6;slot<12;slot++)if(be.items.getStackInSlot(slot).is(item))total+=be.items.getStackInSlot(slot).getCount();return total;}
    private static GrowthChamberBlockEntity restore(GameTestHelper h,GrowthChamberBlockEntity be){var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());var restored=(GrowthChamberBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);return restored;}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,path);}
}
