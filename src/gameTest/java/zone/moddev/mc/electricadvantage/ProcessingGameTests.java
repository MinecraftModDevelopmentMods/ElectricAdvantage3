package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.capabilities.Capabilities;
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
        var gear=new ItemStack(Items.IRON_BARS);
        var psu=stack("psu");var plate=new ItemStack(Items.IRON_INGOT);var bricks=new ItemStack(Items.BRICKS);
        var inputs=java.util.Map.of(
            "electric_furnace",java.util.List.of(bricks,bricks,bricks,bricks,psu,bricks,bricks,bricks,bricks),
            "electric_crusher",java.util.List.of(psu,gear,new ItemStack(Items.DIAMOND),plate,frame,plate));
        for(var entry:inputs.entrySet()){
            var be=place(h,entry.getKey());
            h.assertTrue(zone.moddev.mc.lib.gui.Screens.get(id(entry.getKey())).isPresent(),"Processing XML loads: "+entry.getKey());
            var menu=new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,player.getInventory(),be);
            h.assertTrue(menu.slots.size()==be.items.getSlots()+(be.items.getSlots()>0?41:0),"Screen declares every machine slot: "+entry.getKey());
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,entry.getValue().size()/3,entry.getValue());
            var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
            h.assertTrue(recipe.value().assemble(input,h.getLevel().registryAccess()).is(stack(entry.getKey()).getItem()),"Machine crafting recipe: "+entry.getKey());
        }
        h.succeed();
    }
    private static ProcessingBlockEntity place(GameTestHelper h,String name){h.setBlock(4,3,4,block(name));return(ProcessingBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4,3,4)));}
    private static void tick(GameTestHelper h,ProcessingBlockEntity be){ProcessingBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static void step(GameTestHelper h,ProcessingBlockEntity be){be.energy.setStored(be.energy.capacity());tick(h,be);}
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void legacyEnergyKeyLoads(GameTestHelper h){
        var be=place(h,"electric_furnace");
        var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("Energy",321);
        tag.put("Items",be.items.serializeNBT(h.getLevel().registryAccess()));
        be.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(be.energy.stored()==321,"3.0's \"Energy\" key still loads into the MMDLib-owned buffer");
        h.succeed();
    }
    private static ProcessingBlockEntity restore(GameTestHelper h,ProcessingBlockEntity be){
        var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());var restored=(ProcessingBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());
        restored.loadWithComponents(saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);return restored;
    }
    private static int count(ProcessingBlockEntity be,net.minecraft.world.item.Item item){int total=0;for(int slot=be.settings.inputs();slot<be.items.getSlots();slot++)if(be.items.getStackInSlot(slot).is(item))total+=be.items.getStackInSlot(slot).getCount();return total;}
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,name);}
    private static Block block(String name){return BuiltInRegistries.BLOCK.get(id(name));}
    private static ItemStack stack(String name){return new ItemStack(BuiltInRegistries.ITEM.get(id(name)));}
}
