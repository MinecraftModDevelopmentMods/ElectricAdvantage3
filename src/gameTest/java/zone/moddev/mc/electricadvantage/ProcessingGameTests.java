package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.electricadvantage.processing.*;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessingGameTests {
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void fourParallelSmeltsAndSavedProgress(GameTestHelper h){
        var be=place(h,"electric_furnace");
        be.items.setStackInSlot(0,stack("lithium_dust"));
        be.items.setStackInSlot(1,stack("silicon_mix"));
        be.items.setStackInSlot(2,stack("solder_mix"));
        be.items.setStackInSlot(3,new ItemStack(Items.RAW_IRON));
        for(int tick=0;tick<75;tick++)step(h,be);
        h.assertTrue(be.screenProgress0()==75&&be.screenProgress3()==75,"Four independent smelting lanes progress together");
        var restored=restore(h,be);
        for(int tick=75;tick<200;tick++)step(h,restored);
        for(String output:java.util.List.of("lithium_ingot","silicon_ingot","solder"))h.assertTrue(count(restored,stack(output).getItem())==1,"Smelting output: "+output);
        h.assertTrue(count(restored,Items.IRON_INGOT)==1,"Vanilla smelting recipes are supported");
        for(int slot=0;slot<4;slot++)h.assertTrue(restored.items.getStackInSlot(slot).isEmpty(),"Each lane consumes one input");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void crusherParallelismAndAtomicBackpressure(GameTestHelper h){
        var be=place(h,"electric_crusher");
        be.items.setStackInSlot(0,stack("lithium_ore"));be.items.setStackInSlot(1,stack("sulfur_ore"));be.items.setStackInSlot(2,stack("lithium_ingot"));
        for(int tick=0;tick<200;tick++)step(h,be);
        h.assertTrue(count(be,stack("lithium_dust").getItem())==3&&count(be,stack("sulfur_dust").getItem())==4,"Three lanes use MMDLib crushing recipes and counts");
        for(int slot=3;slot<9;slot++)be.items.setStackInSlot(slot,new ItemStack(Items.STONE,64));
        be.items.setStackInSlot(8,new ItemStack(stack("lithium_dust").getItem(),63));be.items.setStackInSlot(0,stack("lithium_ore"));
        be.energy.setStored(500);tick(h,be);
        h.assertTrue(be.energy.stored()==500&&be.items.getStackInSlot(0).getCount()==1&&be.items.getStackInSlot(8).getCount()==63,"A two-item result must not partially insert into one free space");
        be.items.setStackInSlot(8,new ItemStack(stack("lithium_dust").getItem(),62));
        for(int tick=0;tick<200;tick++)step(h,be);
        h.assertTrue(be.items.getStackInSlot(0).isEmpty()&&be.items.getStackInSlot(8).getCount()==64,"Unblocking output completes exactly once");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void ovenFoodFilterRedstoneAndChangedInput(GameTestHelper h){
        var be=place(h,"electric_oven");
        h.assertTrue(!be.itemCapability().insertItem(0,new ItemStack(Items.RAW_IRON),false).isEmpty(),"Oven rejects non-food smelting input");
        be.items.setStackInSlot(0,new ItemStack(Items.BEEF));
        for(int tick=0;tick<50;tick++)step(h,be);
        be.items.setStackInSlot(0,new ItemStack(Items.POTATO));step(h,be);
        h.assertTrue(be.screenProgress0()==1,"Changing the recipe input resets its progress");
        h.setBlock(4,3,5,Blocks.REDSTONE_BLOCK);be.energy.setStored(500);tick(h,be);
        h.assertTrue(be.energy.stored()==500&&be.screenProgress0()==0,"Redstone stops cooking without consuming energy");
        h.setBlock(4,3,5,Blocks.AIR);
        for(int tick=0;tick<100;tick++)step(h,be);
        h.assertTrue(count(be,Items.BAKED_POTATO)==1,"Oven cooks a food result in 100 ticks");
        h.assertTrue(!be.itemCapability().insertItem(1,new ItemStack(Items.BAKED_POTATO),false).isEmpty(),"Automation cannot insert into output slots");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void distillerPendingOutputAndFluidRoles(GameTestHelper h){
        var be=place(h,"electric_still");
        h.assertTrue(be.items.getSlots()==0,"Distiller has no hidden item inventory");
        var crude=zone.moddev.mc.poweradvantage.init.Fluids.CRUDE_OIL.get();
        var refined=zone.moddev.mc.poweradvantage.init.Fluids.REFINED_OIL.get();
        h.assertTrue(be.fluidCapability().fill(new FluidStack(Fluids.WATER,100),FluidAction.EXECUTE)==0,"Only recipe inputs enter distiller");
        be.fluidCapability().fill(new FluidStack(crude,2),FluidAction.EXECUTE);step(h,be);
        h.assertTrue(be.screenInputFluid().isEmpty()&&be.screenOutputFluid().isEmpty()&&be.screenProgress()==1,"Input consumed once and output remains pending");
        be=restore(h,be);
        be.output.tank(0).setFluid(new FluidStack(refined,1000));step(h,be);
        h.assertTrue(be.screenProgress()==2,"Pending operation reaches completion without losing its output");
        long stored=be.energy.stored();tick(h,be);
        h.assertTrue(be.energy.stored()==stored&&!be.getBlockState().getValue(ProcessingBlock.ACTIVE),"Blocked completed output is idle and does not consume extra energy");
        be.fluidCapability().drain(1000,FluidAction.EXECUTE);tick(h,be);
        h.assertTrue(be.screenOutputFluid().getAmount()==1&&be.screenInputFluid().isEmpty(),"Saved pending output completes once space returns");
        be.fluidCapability().fill(new FluidStack(crude,20),FluidAction.EXECUTE);
        h.assertTrue(be.fluidCapability().drain(new FluidStack(crude,20),FluidAction.EXECUTE).isEmpty()&&be.screenInputFluid().getAmount()==20,"External drain cannot steal input fluid");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void refineryAmountTimingPersistenceAndBlockedOutput(GameTestHelper h){
        var be=place(h,"plastic_refinery");
        h.assertTrue(be.items.getSlots()==1,"Refinery has exactly one item output");
        var crude=zone.moddev.mc.poweradvantage.init.Fluids.CRUDE_OIL.get();
        var refined=zone.moddev.mc.poweradvantage.init.Fluids.REFINED_OIL.get();
        h.assertTrue(be.fluidCapability().fill(new FluidStack(Fluids.WATER,100),FluidAction.EXECUTE)==0,"Refinery rejects unconfigured fluids");
        be.fluidCapability().fill(new FluidStack(crude,100),FluidAction.EXECUTE);
        for(int tick=0;tick<40;tick++)step(h,be);
        be=restore(h,be);
        for(int tick=40;tick<100;tick++)step(h,be);
        h.assertTrue(be.screenInputFluid().isEmpty()&&count(be,stack("petrolplastic_ingot").getItem())==1,"100 mB and 100 powered ticks make one plastic ingot across reload");
        be.items.setStackInSlot(0,new ItemStack(stack("petrolplastic_ingot").getItem(),64));
        be.fluidCapability().fill(new FluidStack(refined,100),FluidAction.EXECUTE);
        be.energy.setStored(500);tick(h,be);
        h.assertTrue(be.energy.stored()==500&&be.screenInputFluid().getAmount()==100,"Full output preserves oil and energy");
        be.items.setStackInSlot(0,ItemStack.EMPTY);
        for(int tick=0;tick<100;tick++)step(h,be);
        h.assertTrue(be.screenInputFluid().isEmpty()&&count(be,stack("petrolplastic_ingot").getItem())==1,"Configured refined oil also produces plastic");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",timeoutTicks=350)
    public static void liveElectricSupplyAndItemExport(GameTestHelper h){
        h.setBlock(1,3,4,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("poweradvantage:infinite_power")));
        h.setBlock(2,3,4,block("electric_conduit"));
        h.setBlock(3,3,4,block("electric_furnace"));
        var be=(ProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3,3,4)));
        be.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON));
        h.setBlock(4,3,4,Blocks.CHEST);
        h.runAfterDelay(260,()->{
            var target=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(new BlockPos(4,3,4)),Direction.WEST);
            int count=0;for(int slot=0;slot<target.getSlots();slot++)if(target.getStackInSlot(slot).is(Items.IRON_INGOT))count+=target.getStackInSlot(slot).getCount();
            h.assertTrue(count==1&&be.items.getStackInSlot(0).isEmpty(),"Live electric network smelts and exports an ingot to an adjacent chest");
            h.succeed();
        });
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void processingMenusAndRecipes(GameTestHelper h){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var frame=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("poweradvantage:steel_frame")));
        var gear=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mmdlib:iron_gear")));
        var psu=stack("psu");var plate=new ItemStack(Items.IRON_INGOT);var bricks=new ItemStack(Items.BRICKS);var bucket=new ItemStack(Items.BUCKET);
        var inputs=java.util.Map.of(
            "electric_furnace",java.util.List.of(bricks,bricks,bricks,bricks,psu,bricks,bricks,bricks,bricks),
            "electric_crusher",java.util.List.of(psu,gear,new ItemStack(Items.DIAMOND),plate,frame,plate),
            "electric_oven",java.util.List.of(psu,new ItemStack(Items.GLASS_PANE),psu,plate,frame,plate),
            "electric_still",java.util.List.of(psu,bucket,bucket,plate,frame,plate),
            "plastic_refinery",java.util.List.of(psu,new ItemStack(Items.PISTON),gear,plate,frame,plate));
        for(var entry:inputs.entrySet()){
            var be=place(h,entry.getKey());
            h.assertTrue(zone.moddev.mc.lib.gui.Screens.get(id(entry.getKey())).isPresent(),"Processing XML loads: "+entry.getKey());
            var menu=new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,player.getInventory(),be);
            h.assertTrue(menu.slots.size()==be.items.getSlots()+(be.items.getSlots()>0?36:0),"Screen declares every machine slot: "+entry.getKey());
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,entry.getValue().size()/3,entry.getValue());
            var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
            h.assertTrue(recipe.value().assemble(input,h.getLevel().registryAccess()).is(stack(entry.getKey()).getItem()),"Machine crafting recipe: "+entry.getKey());
        }
        h.succeed();
    }
    private static ProcessingBlockEntity place(GameTestHelper h,String name){h.setBlock(4,3,4,block(name));return(ProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4,3,4)));}
    private static void tick(GameTestHelper h,ProcessingBlockEntity be){ProcessingBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static void step(GameTestHelper h,ProcessingBlockEntity be){be.energy.setStored(be.energy.capacity());tick(h,be);}
    private static ProcessingBlockEntity restore(GameTestHelper h,ProcessingBlockEntity be){
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());var restored=(ProcessingBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());
        restored.loadWithComponents(saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);return restored;
    }
    private static int count(ProcessingBlockEntity be,net.minecraft.world.item.Item item){int total=0;for(int slot=be.settings.inputs();slot<be.items.getSlots();slot++)if(be.items.getStackInSlot(slot).is(item))total+=be.items.getStackInSlot(slot).getCount();return total;}
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,name);}
    private static Block block(String name){return BuiltInRegistries.BLOCK.get(id(name));}
    private static ItemStack stack(String name){return new ItemStack(BuiltInRegistries.ITEM.get(id(name)));}
}
