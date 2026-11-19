package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

import static zone.moddev.mc.electricadvantage.XmlMachineGameTests.*;

/** The Electric Arc Furnace and Electric Rock Crusher: XML machines with one recipe processor per lane. */
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
        run(be,75);
        h.assertTrue(progress(be,"lane_0")>0&&progress(be,"lane_0")==progress(be,"lane_3"),"Four independent smelting lanes progress together");
        var restored=restore(h,be);
        h.assertTrue(progress(restored,"lane_0")==progress(be,"lane_0"),"Lane progress survives a save");
        run(restored,130);
        for(String output:java.util.List.of("lithium_ingot","silicon_ingot","solder"))h.assertTrue(count(restored,item(output))==1,"Smelting output: "+output);
        h.assertTrue(count(restored,Items.IRON_INGOT)==1,"Vanilla smelting recipes are supported");
        for(int slot=0;slot<4;slot++)h.assertTrue(restored.items.getStackInSlot(slot).isEmpty(),"Each lane consumes one input");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void smeltingCostsEightFePerTickFor200Ticks(GameTestHelper h){
        var recipe=h.getLevel().getRecipeManager().byKey(id("generated/arc_smelting/minecraft/iron_ingot_from_smelting_raw_iron")).orElseThrow();
        h.assertTrue(recipe.value() instanceof zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry entry
            &&entry.recipe().time()==200,"Arc furnace recipes take 200 ticks, whatever the furnace time");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void crusherParallelismAndAtomicBackpressure(GameTestHelper h){
        var be=place(h,"electric_crusher");
        be.items.setStackInSlot(0,stack("lithium_ore"));be.items.setStackInSlot(1,stack("sulfur_ore"));be.items.setStackInSlot(2,stack("lithium_ingot"));
        run(be,205);
        h.assertTrue(count(be,item("lithium_dust"))==3&&count(be,item("sulfur_dust"))==4,"Three lanes use MMDLib crushing recipes and counts");
        for(int slot=3;slot<9;slot++)be.items.setStackInSlot(slot,new ItemStack(Items.STONE,64));
        be.items.setStackInSlot(8,new ItemStack(item("lithium_dust"),63));be.items.setStackInSlot(0,stack("lithium_ore"));
        be.energy.setStored(be.energy.capacity());
        for(int tick=0;tick<5;tick++)be.serverTick();
        h.assertTrue(be.energy.stored()==be.energy.capacity()&&be.items.getStackInSlot(0).getCount()==1&&be.items.getStackInSlot(8).getCount()==63,
            "A two-item result must not start, use power or partially insert into one free space");
        be.items.setStackInSlot(8,new ItemStack(item("lithium_dust"),62));
        run(be,205);
        h.assertTrue(be.items.getStackInSlot(0).isEmpty()&&be.items.getStackInSlot(8).getCount()==64,"Unblocking output completes exactly once");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",timeoutTicks=350)
    public static void liveElectricSupplyAndItemExport(GameTestHelper h){
        h.setBlock(1,3,4,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("poweradvantage:infinite_power")));
        h.setBlock(2,3,4,block("electric_conduit"));
        h.setBlock(3,3,4,block("electric_furnace"));
        var be=(DefinedMachineEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3,3,4)));
        be.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON));
        var automation=be.screenAutomation();
        automation.slots().setStackInSlot(AutomationInventory.addonSlot(automation.index("output"),0),new ItemStack(item("item_output_port")));
        h.setBlock(4,3,4,Blocks.CHEST);
        h.runAfterDelay(260,()->{
            var target=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(new BlockPos(4,3,4)),Direction.WEST);
            int count=0;for(int slot=0;slot<target.getSlots();slot++)if(target.getStackInSlot(slot).is(Items.IRON_INGOT))count+=target.getStackInSlot(slot).getCount();
            h.assertTrue(count==1&&be.items.getStackInSlot(0).isEmpty(),"Live electric network smelts, and an Item Output Port exports the ingot to an adjacent chest");
            h.succeed();
        });
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void processingMenusAndRecipes(GameTestHelper h){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var frame=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("poweradvantage:steel_frame")));
        var gear=new ItemStack(Items.IRON_BARS);
        var psu=stack("psu");var plate=new ItemStack(Items.IRON_INGOT);var bricks=new ItemStack(Items.BRICKS);
        var inputs=java.util.Map.of(
            "electric_furnace",java.util.List.of(bricks,bricks,bricks,bricks,psu,bricks,bricks,bricks,bricks),
            "electric_crusher",java.util.List.of(psu,gear,new ItemStack(Items.DIAMOND),plate,frame,plate));
        for(var entry:inputs.entrySet()){
            var be=place(h,entry.getKey());
            var menu=new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,player.getInventory(),be);
            h.assertTrue(menu.slots.size()>=be.items.getSlots()+41,"Screen holds every machine slot and the player inventory: "+entry.getKey());
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,entry.getValue().size()/3,entry.getValue());
            var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
            h.assertTrue(recipe.value().assemble(input,h.getLevel().registryAccess()).is(item(entry.getKey())),"Machine crafting recipe: "+entry.getKey());
        }
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void version30SavesKeepItemsAndEnergy(GameTestHelper h){
        var registries=h.getLevel().registryAccess();
        var be=place(h,"electric_furnace");
        var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("Energy",321);
        tag.put("Items",legacyItems(registries,10,1,new ItemStack(Items.RAW_GOLD,2),5,new ItemStack(Items.IRON_INGOT,9)));
        be.loadWithComponents(tag,registries);
        h.assertTrue(be.energy.stored()==321,"3.0's \"Energy\" key loads into the buffer");
        h.assertTrue(be.items.getStackInSlot(1).is(Items.RAW_GOLD)&&be.items.getStackInSlot(5).getCount()==9,"3.0's slot order is kept: inputs, then outputs");
        h.succeed();
    }
    private static int progress(DefinedMachineEntity be,String lane){return be.features.processor(lane).orElseThrow().state().progress();}
    private static DefinedMachineEntity restore(GameTestHelper h,DefinedMachineEntity be){
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());var restored=(DefinedMachineEntity)be.getType().create(be.getBlockPos(),be.getBlockState());
        restored.loadWithComponents(saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);return restored;
    }
    /** Items of {@code item} in the output group. */
    private static int count(DefinedMachineEntity be,net.minecraft.world.item.Item item){
        int total=0;for(var stack:be.inventoryContents("output"))if(stack.is(item))total+=stack.getCount();return total;
    }
    private static ItemStack stack(String name){return new ItemStack(item(name));}
}
