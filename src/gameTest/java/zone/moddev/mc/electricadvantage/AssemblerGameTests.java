package zone.moddev.mc.electricadvantage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.electricadvantage.assembler.*;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AssemblerGameTests {
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void nativeImportsManualOnlyAndExplicitPrecedence(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var catalog=AssemblerCatalog.get(manager);
        h.assertTrue(catalog.byId().get(imported("test/imported_shapeless")).imported(),"Shapeless recipes import");
        h.assertTrue(catalog.byId().get(imported("test/imported_shaped")).imported(),"Shaped recipes import");
        h.assertTrue(manager.byKey(id("test/blocked_manual_only")).isPresent()&&!catalog.byId().containsKey(imported("test/blocked_manual_only")),"Manual-only recipe stays available by hand but is not imported");
        h.assertTrue(!catalog.byId().get(imported("test/native_collision")).imported(),"Explicit recipe wins an exact imported-ID collision");
        h.assertTrue(!catalog.byId().containsKey(imported("test/native_overridden"))&&catalog.byId().containsKey(id("test/assembler_override")),"Explicit override suppresses only its corresponding import");
        h.assertTrue(manager.byKey(id("test/assembler_priority")).orElseThrow().value().isSpecial(),"Assembler recipes stay out of the vanilla recipe book");
        var be=place(h);var player=player(h,be);
        be.setScreenGhost(player,10,new ItemStack(Items.IRON_INGOT));
        h.assertTrue(be.selectedRecipe().equals(id("test/assembler_priority")),"Explicit matching recipes are selected before imported alternatives");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void tenTicksHundredFeAndFullOutputStop(GameTestHelper h){
        var be=place(h);be.selectRecipe(imported("test/imported_shapeless"));
        be.items.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,3));be.energy.setStored(5000);
        be.items.setStackInSlot(9,new ItemStack(Items.DIAMOND,64));
        ticks(h,be,20);
        h.assertTrue(be.screenProgress()==0&&be.energy.stored()==5000&&be.items.getStackInSlot(0).getCount()==3,"A full output always stops crafting, even with a different item");
        be.items.setStackInSlot(9,ItemStack.EMPTY);ticks(h,be,9);
        h.assertTrue(be.screenProgress()==9&&be.energy.stored()==5000&&be.items.getStackInSlot(9).isEmpty(),"Imported recipe waits ten ticks and charges once at completion");
        ticks(h,be,1);
        h.assertTrue(be.energy.stored()==4900&&be.items.getStackInSlot(0).getCount()==2&&be.items.getStackInSlot(9).is(Items.REDSTONE)&&be.items.getStackInSlot(9).getCount()==2,"One craft consumes exactly 100 FE and one ingredient");
        ticks(h,be,1);be.items.setStackInSlot(9,new ItemStack(Items.REDSTONE,64));ticks(h,be,10);
        h.assertTrue(be.screenProgress()==1&&be.energy.stored()==4900,"Full output pauses existing progress without spending energy");
        be.items.setStackInSlot(9,new ItemStack(Items.REDSTONE,63));ticks(h,be,10);
        h.assertTrue(be.screenProgress()==1&&be.items.getStackInSlot(0).getCount()==2,"Whole result must fit; partial space cannot consume input");
        be.items.setStackInSlot(9,ItemStack.EMPTY);ticks(h,be,9);
        h.assertTrue(be.energy.stored()==4800&&be.items.getStackInSlot(0).getCount()==1,"Craft resumes only when output fits");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void sizedCellsSplitStacksAndCustomTimePower(GameTestHelper h){
        var be=place(h);be.selectRecipe(id("test/sized_shaped"));be.energy.setStored(5000);
        be.items.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,2));be.items.setStackInSlot(1,new ItemStack(Items.IRON_INGOT,4));
        be.items.setStackInSlot(2,new ItemStack(Items.COPPER_INGOT));be.items.setStackInSlot(3,new ItemStack(Items.COPPER_INGOT));
        h.assertTrue(be.screenGhost(10).getCount()==3&&be.screenGhost(11).getCount()==2,"Ghost grid shows per-cell quantities");
        ticks(h,be,3);be=restore(h,be);ticks(h,be,2);
        h.assertTrue(be.items.getStackInSlot(9).isEmpty()&&be.screenProgress()==5,"Custom duration and saved progress are retained");
        ticks(h,be,1);
        h.assertTrue(be.items.getStackInSlot(9).is(Items.DIAMOND)&&be.items.getStackInSlot(9).getCount()==2&&be.energy.stored()==4956,"Six-tick custom craft charges 11 electricity / 44 FE");
        for(int slot=0;slot<4;slot++)h.assertTrue(be.items.getStackInSlot(slot).isEmpty(),"Repeated sized cells consume all six iron and two copper from split stacks");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void alternativesAllocateWithoutMixingARecipeCell(GameTestHelper h){
        var be=place(h);be.selectRecipe(id("test/sized_shapeless"));be.energy.setStored(5000);
        be.items.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,2));be.items.setStackInSlot(1,new ItemStack(Items.COPPER_INGOT));be.items.setStackInSlot(2,new ItemStack(Items.COPPER_INGOT));
        ticks(h,be,4);
        h.assertTrue(be.items.getStackInSlot(9).is(Items.DIAMOND)&&be.energy.stored()==4972,"Tag alternatives leave exact iron ingredients available and combine matching buffer stacks");
        be.items.setStackInSlot(9,ItemStack.EMPTY);be.selectRecipe(id("test/no_mixed_cell"));
        be.items.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));be.items.setStackInSlot(1,new ItemStack(Items.COPPER_INGOT));
        long energy=be.energy.stored();ticks(h,be,10);
        h.assertTrue(be.items.getStackInSlot(9).isEmpty()&&be.energy.stored()==energy,"A count-two cell cannot mix one copper and one iron");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void containerRemaindersAndFullPartsBuffer(GameTestHelper h){
        var be=place(h);be.selectRecipe(id("test/container_space"));be.energy.setStored(5000);
        be.items.setStackInSlot(0,new ItemStack(Items.HONEY_BOTTLE,2));
        for(int slot=1;slot<9;slot++)be.items.setStackInSlot(slot,new ItemStack(Items.STONE,64));
        ticks(h,be,10);
        h.assertTrue(be.items.getStackInSlot(0).getCount()==2&&be.items.getStackInSlot(9).isEmpty()&&be.energy.stored()==5000,"Craft waits when a bottle remainder cannot fit");
        be.items.setStackInSlot(8,ItemStack.EMPTY);ticks(h,be,2);
        h.assertTrue(countBuffer(be,Items.GLASS_BOTTLE)==1&&be.items.getStackInSlot(0).getCount()==1&&be.energy.stored()==4992,"Bottle remainder returns to buffer without losing items");
        for(int slot=0;slot<10;slot++)be.items.setStackInSlot(slot,ItemStack.EMPTY);
        be.selectRecipe(id("test/sized_containers"));
        be.items.setStackInSlot(0,new ItemStack(Items.MILK_BUCKET));be.items.setStackInSlot(1,new ItemStack(Items.MILK_BUCKET));
        ticks(h,be,2);
        h.assertTrue(countBuffer(be,Items.BUCKET)==2&&be.items.getStackInSlot(9).is(Items.SUGAR),"Each consumed container in a sized cell returns its remainder");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void mmdlibGhostSelectionAndAlternateControls(GameTestHelper h){
        var be=place(h);var player=player(h,be);
        var menu=new MachineMenu(9,player.getInventory(),be);var previous=player.containerMenu;player.containerMenu=menu;
        try{
            h.assertTrue(be.items.getSlots()==10&&menu.slots.size()==55,"Nine buffer slots, one output, nine ghosts, and player inventory");
            var ghost=menu.elements.stream().filter(e->e instanceof zone.moddev.mc.lib.gui.model.Element.Slot slot&&slot.index()==10).findFirst().orElseThrow();
            menu.setCarried(new ItemStack(Items.IRON_INGOT,12));
            h.assertTrue(menu.ghost(player,menu.index(ghost),menu.getCarried())&&menu.getCarried().getCount()==12,"Ghost selection consumes no cursor items");
            h.assertTrue(be.selectedRecipe().equals(id("test/assembler_priority")),"Authored recipe is the initial matching choice");
            var found=new HashSet<ResourceLocation>();
            for(int n=0;n<30;n++){found.add(be.selectedRecipe());be.cycleRecipe(1);}
            h.assertTrue(found.contains(imported("test/imported_shapeless"))&&found.contains(imported("test/alternate")),"Alternate outputs can both be selected for the same grid");
            h.assertTrue(!menu.ghost(player,menu.index(ghost)+10000,new ItemStack(Items.DIRT)),"Invalid ghost element index is rejected");
            be.items.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));be.clearRecipe();
            h.assertTrue(be.items.getStackInSlot(0).getCount()==1&&be.screenGhost(10).isEmpty(),"Clearing selection preserves real parts");
        }finally{player.containerMenu=previous;}
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void recipeReplacementRemovalAndInvalidation(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var original=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var source=id("test/reload_native");var be=place(h);be.selectRecipe(imported("test/reload_native"));
        be.items.setStackInSlot(0,new ItemStack(Items.OBSIDIAN,2));be.energy.setStored(5000);ticks(h,be,5);
        try{
            var replacement=new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(Items.GOLD_INGOT),NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.OBSIDIAN)));
            var changed=new ArrayList<RecipeHolder<?>>();for(var holder:original)if(!holder.id().equals(source))changed.add(holder);
            changed.add(new RecipeHolder<>(source,replacement));manager.replaceRecipes(changed);AssemblerCatalog.rebuild(manager);
            ticks(h,be,1);h.assertTrue(be.screenProgress()==1&&be.items.getStackInSlot(9).isEmpty(),"Recipe replacement invalidates old progress");
            ticks(h,be,9);h.assertTrue(be.items.getStackInSlot(9).is(Items.GOLD_INGOT),"Replacement result is used");
            changed.removeIf(holder->holder.id().equals(source));manager.replaceRecipes(changed);AssemblerCatalog.rebuild(manager);ticks(h,be,20);
            h.assertTrue(be.items.getStackInSlot(0).getCount()==1&&be.energy.stored()==4900,"Removed recipe cannot craft stale output");
        }finally{manager.replaceRecipes(original);AssemblerCatalog.rebuild(manager);}
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure")
    public static void powerRedstoneAndShapedImports(GameTestHelper h){
        var be=place(h);be.selectRecipe(imported("test/imported_shaped"));be.energy.setStored(99);
        be.items.setStackInSlot(0,new ItemStack(Items.DIAMOND,2));ticks(h,be,15);
        h.assertTrue(be.screenProgress()==0&&be.items.getStackInSlot(0).getCount()==2,"Insufficient full-craft power pauses without consuming parts");
        be.energy.receiveEnergy(1,false);h.setBlock(4,3,5,Blocks.REDSTONE_BLOCK);ticks(h,be,15);
        h.assertTrue(be.screenProgress()==0&&be.energy.stored()==100,"Redstone stops crafting");
        h.setBlock(4,3,5,Blocks.AIR);ticks(h,be,10);
        h.assertTrue(be.energy.stored()==0&&be.items.getStackInSlot(9).is(Items.EMERALD)&&be.items.getStackInSlot(9).getCount()==2,"Shaped recipe gathers its two cells from the parts buffer");
        h.succeed();
    }
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="infrastructure",batch="assembler_reload",timeoutTicks=600)
    public static void actualDatapackReloadRefreshesCatalog(GameTestHelper h){
        var level=h.getLevel();var previous=level.getRecipeManager();long revision=AssemblerCatalog.get(previous).revision();
        CompletableFuture<Void> reload=level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());
        h.succeedWhen(()->{
            h.assertTrue(reload.isDone(),"Waiting for datapack reload");reload.join();
            var snapshot=AssemblerCatalog.get(level.getRecipeManager());
            h.assertTrue(level.getRecipeManager()!=previous&&snapshot.revision()>revision,"Reload listener rebuilds imports for the newly installed recipe manager");
            h.assertTrue(snapshot.byId().containsKey(imported("test/imported_shapeless"))&&!snapshot.byId().containsKey(imported("test/blocked_manual_only")),"Reload retains imports and manual-only exclusion");
        });
    }
    private static AssemblerBlockEntity place(GameTestHelper h){h.setBlock(4,3,4,BuiltInRegistries.BLOCK.get(id("electric_fabricator")));return(AssemblerBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4,3,4)));}
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,AssemblerBlockEntity be){var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());player.setPos(be.getBlockPos().getX()+0.5,be.getBlockPos().getY()+0.5,be.getBlockPos().getZ()+0.5);return player;}
    private static void ticks(GameTestHelper h,AssemblerBlockEntity be,int count){for(int n=0;n<count;n++)AssemblerBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);}
    private static int countBuffer(AssemblerBlockEntity be,Item item){int count=0;for(int n=0;n<9;n++)if(be.items.getStackInSlot(n).is(item))count+=be.items.getStackInSlot(n).getCount();return count;}
    private static AssemblerBlockEntity restore(GameTestHelper h,AssemblerBlockEntity be){var saved=be.saveWithoutMetadata(h.getLevel().registryAccess());var restored=(AssemblerBlockEntity)be.getType().create(be.getBlockPos(),be.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);return restored;}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,path);}
    private static ResourceLocation imported(String path){return AssemblerCatalog.importedId(id(path));}
}
