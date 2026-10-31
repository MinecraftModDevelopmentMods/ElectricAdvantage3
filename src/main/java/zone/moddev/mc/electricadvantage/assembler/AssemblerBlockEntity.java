package zone.moddev.mc.electricadvantage.assembler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import java.util.*;

public final class AssemblerBlockEntity extends BlockEntity implements ScreenHost {
    public final AssemblerSettings settings;
    public final EnergyBuffer energy;
    public final ItemStorage items;
    private final ItemStack[] ghosts=new ItemStack[9];
    private final IEnergyStorage energyInput;
    private ResourceLocation selected;
    private AssemblerCatalog.Entry current;
    private long catalogRevision=-1,inventoryRevision,plannedInventory=-1;
    private AssemblerPlanner planner;
    private String recipeFingerprint="",workFingerprint="",plannedWorkFingerprint="";
    private int progress;

    public AssemblerBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,AssemblerSettings settings){
        super(type,pos,state);this.settings=settings;Arrays.fill(ghosts,ItemStack.EMPTY);
        energy=new EnergyBuffer(settings.capacity(),settings.rate()).onChange(this::setChanged);energyInput=energy.view(IoMode.INPUT);
        // Buffer slots remain extractable so automation can retrieve containers; only slot 9 is auto-exported.
        items=new ItemStorage(10).role(Role.OUTPUT,9).onChange(()->{inventoryRevision++;setChanged();});
    }
    public IEnergyStorage energyCapability(){return energyInput;}
    public IItemHandler itemCapability(){return items.view(IoMode.BOTH);}
    private AssemblerCatalog.Snapshot catalog(){return AssemblerCatalog.get(level.getRecipeManager());}
    private void syncCatalog(){
        if(level==null||level.isClientSide)return;
        var snapshot=catalog();if(snapshot.revision()==catalogRevision)return;
        var next=selected==null?null:snapshot.byId().get(selected);
        var fingerprint=next==null?"":AssemblerCatalog.fingerprint(next,level.registryAccess(),settings);
        if(catalogRevision!=-1||!fingerprint.equals(recipeFingerprint)){progress=0;workFingerprint="";}
        current=next;recipeFingerprint=fingerprint;catalogRevision=snapshot.revision();planner=null;plannedInventory=-1;
        if(current!=null)showRecipe(current);setChanged();
    }
    public ResourceLocation selectedRecipe(){return selected;}
    public void selectRecipe(ResourceLocation id){
        if(level==null||level.isClientSide||!catalog().byId().containsKey(id))return;
        selected=id;catalogRevision=-1;recipeFingerprint="";progress=0;workFingerprint="";syncCatalog();setChanged();
    }
    public void clearRecipe(){
        selected=null;current=null;progress=0;recipeFingerprint="";workFingerprint="";planner=null;plannedInventory=-1;
        Arrays.fill(ghosts,ItemStack.EMPTY);setChanged();
    }
    private void showRecipe(AssemblerCatalog.Entry entry){
        var previous=Arrays.stream(ghosts).map(ItemStack::copy).toList();Arrays.fill(ghosts,ItemStack.EMPTY);
        for(var cell:entry.cells()){
            var existing=previous.get(cell.gridIndex());
            var examples=cell.ingredient().getItems();
            var example=!existing.isEmpty()&&cell.ingredient().test(existing)?existing:examples.length>0?examples[0]:ItemStack.EMPTY;
            if(!example.isEmpty())ghosts[cell.gridIndex()]=example.copyWithCount(cell.count());
        }
    }
    private List<AssemblerCatalog.Entry> matchingChoices(){
        var input=CraftingInput.of(3,3,Arrays.asList(ghosts));
        if(input.isEmpty())return catalog().entries();
        return catalog().entries().stream().filter(entry->entry.matchesDisplay(input,level)).toList();
    }
    public void cycleRecipe(int direction){
        if(level==null||level.isClientSide)return;
        var choices=matchingChoices();if(choices.isEmpty())return;
        int index=-1;for(int n=0;n<choices.size();n++)if(choices.get(n).id().equals(selected)){index=n;break;}
        int next=index<0?(direction<0?choices.size()-1:0):Math.floorMod(index+direction,choices.size());
        selectRecipe(choices.get(next).id());
    }
    @Override public boolean setScreenGhost(Player player,int index,ItemStack filter){
        if(index<10||index>=19||level==null||level.isClientSide||player.level()!=level||player.distanceToSqr(worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5)>64)return false;
        ghosts[index-10]=filter.isEmpty()?ItemStack.EMPTY:filter.copyWithCount(1);
        selected=null;current=null;progress=0;workFingerprint="";recipeFingerprint="";planner=null;
        var choices=matchingChoices();if(!choices.isEmpty()&&!CraftingInput.of(3,3,Arrays.asList(ghosts)).isEmpty())selectRecipe(choices.getFirst().id());
        setChanged();return true;
    }
    @Override public ItemStack screenGhost(int index){return index>=10&&index<19?ghosts[index-10].copy():ItemStack.EMPTY;}
    @Override public ItemStorage screenItems(){return items;}
    @Override public int[] screenDestinations(ItemStack stack){return new int[]{0,1,2,3,4,5,6,7,8};}

    public static void tick(Level world,BlockPos pos,BlockState state,AssemblerBlockEntity be){
        if(!(world instanceof ServerLevel level))return;
        be.syncCatalog();
        boolean active=!level.hasNeighborSignal(pos)&&be.craftTick();
        var updated=be.getBlockState().setValue(AssemblerBlock.ACTIVE,active).setValue(AssemblerBlock.POWERED,
                be.current!=null&&be.energy.stored()>=be.current.feCost(be.settings));
        if(updated!=be.getBlockState())level.setBlock(pos,updated,3);
        int remaining=be.settings.exportRate();
        for(int n=0;n<6&&remaining>0;n++){
            var side=Direction.values()[(int)((level.getGameTime()+n)%6)];
            if(level.hasChunkAt(pos.relative(side)))remaining-=Transfer.pushItems(level,pos,side,be.outputOnly(),remaining);
        }
    }
    private boolean craftTick(){
        var output=items.getStackInSlot(9);
        // A full output always stops crafting, even if its item differs from the selected product.
        if(!output.isEmpty()&&output.getCount()>=Math.min(items.getSlotLimit(9),output.getMaxStackSize()))return false;
        if(current==null)return false;
        if(planner==null||plannedInventory!=inventoryRevision){
            var buffer=new ArrayList<ItemStack>();for(int slot=0;slot<9;slot++)buffer.add(items.getStackInSlot(slot).copy());
            planner=new AssemblerPlanner(current,level,buffer);plannedInventory=inventoryRevision;plannedWorkFingerprint="";
        }
        if(!planner.done())planner.advance(settings.planningSteps());
        if(!planner.done())return false;
        var plan=planner.plan();
        if(plan==null){if(progress!=0){progress=0;setChanged();}return false;}
        if(plannedWorkFingerprint.isEmpty())plannedWorkFingerprint=AssemblerCatalog.workFingerprint(recipeFingerprint,plan.grid(),level.registryAccess());
        var fingerprint=plannedWorkFingerprint;
        if(!fingerprint.equals(workFingerprint)){workFingerprint=fingerprint;progress=0;setChanged();}
        var result=plan.result();
        if(!output.isEmpty()&&!ItemStack.isSameItemSameComponents(output,result))return false;
        if((long)output.getCount()+result.getCount()>Math.min(items.getSlotLimit(9),result.getMaxStackSize()))return false;
        long cost=current.feCost(settings);if(energy.stored()<cost)return false;
        if(++progress>=current.time(settings)){
            // All simulations and allocation use a copy. Commit only after every result and remainder fits.
            if(!energy.consume(cost))return false;
            for(int slot=0;slot<9;slot++)items.setStackInSlot(slot,plan.buffer().get(slot).copy());
            items.setStackInSlot(9,result.copyWithCount(output.getCount()+result.getCount()));
            progress=0;planner=null;plannedInventory=-1;workFingerprint="";
        }
        setChanged();return true;
    }
    private IItemHandler outputOnly(){return new IItemHandler(){
        @Override public int getSlots(){return 1;}
        @Override public ItemStack getStackInSlot(int slot){return slot==0?items.getStackInSlot(9):ItemStack.EMPTY;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return stack;}
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate){return slot==0?items.extractItem(9,amount,simulate):ItemStack.EMPTY;}
        @Override public int getSlotLimit(int slot){return slot==0?items.getSlotLimit(9):0;}
        @Override public boolean isItemValid(int slot,ItemStack stack){return false;}
    };}
    public long screenEnergy(){return energy.stored();}
    public long screenCapacity(){return energy.capacity();}
    public int screenProgress(){return progress;}
    public int screenTime(){syncCatalog();return current==null?settings.defaultTime():current.time(settings);}
    public Component screenRecipe(){
        syncCatalog();if(current==null)return Component.translatable("screen.electricadvantage.assembler.no_recipe");
        var result=current.recipe().getResultItem(level.registryAccess());
        return Component.translatable("screen.electricadvantage.assembler.selected",result.getCount(),result.getHoverName());
    }
    public Component screenCost(){syncCatalog();return current==null?Component.empty():Component.translatable("screen.electricadvantage.assembler.cost",current.time(settings),current.power(settings),current.feCost(settings));}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.put("Energy",energy.serializeNBT(registries));tag.put("Items",items.serializeNBT(registries));
        if(selected!=null)tag.putString("Recipe",selected.toString());
        tag.putInt("Progress",progress);tag.putString("RecipeFingerprint",recipeFingerprint);tag.putString("WorkFingerprint",workFingerprint);
        for(int n=0;n<9;n++)if(!ghosts[n].isEmpty())tag.put("Ghost"+n,ghosts[n].save(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("Energy"))energy.deserializeNBT(registries,tag.get("Energy"));
        if(tag.contains("Items"))items.deserializeNBT(registries,tag.getCompound("Items"));
        selected=ResourceLocation.tryParse(tag.getString("Recipe"));progress=Math.clamp(tag.getInt("Progress"),0,1000000);
        recipeFingerprint=tag.getString("RecipeFingerprint");workFingerprint=tag.getString("WorkFingerprint");
        for(int n=0;n<9;n++)ghosts[n]=tag.contains("Ghost"+n)?ItemStack.parseOptional(registries,tag.getCompound("Ghost"+n)):ItemStack.EMPTY;
        current=null;catalogRevision=-1;planner=null;plannedInventory=-1;
    }
}
