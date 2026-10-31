package zone.moddev.mc.electricadvantage.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import java.util.*;

/**
 * Three growth lanes, each a soil (slot 0-2) and a seed (slot 3-5); both are catalysts and are
 * never consumed. Slots 6-11 are shared outputs and slot 12 is the chamber's upgrade.
 */
public final class GrowthChamberBlockEntity extends BlockEntity implements ScreenHost {
    public static final int LANES=3,SEED=3,OUTPUT=6,OUTPUTS=6,UPGRADE=12;
    public final GrowthChamberSettings settings;
    public final ItemStorage items;
    private long greenhouse;
    private final int[] progress=new int[LANES];
    private final ResourceLocation[] recipeIds=new ResourceLocation[LANES];
    private final ItemStack[] workSeeds=new ItemStack[LANES],workSoils=new ItemStack[LANES];
    // A finished harvest is rolled once and held until all of it fits, so nothing is spilled or re-rolled.
    private final List<List<ItemStack>> pending=new ArrayList<>();

    public GrowthChamberBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,GrowthChamberSettings settings){
        super(type,pos,state);this.settings=settings;
        Arrays.fill(workSeeds,ItemStack.EMPTY);Arrays.fill(workSoils,ItemStack.EMPTY);
        for(int n=0;n<LANES;n++)pending.add(new ArrayList<>());
        items=new ItemStorage(UPGRADE+1).onChange(this::setChanged)
                .role(Role.INPUT,0,1,2,3,4,5).limit(1,0,1,2,3,4,5,UPGRADE)
                .filter(this::isSoil,0,1,2).filter(this::isSeed,3,4,5)
                .role(Role.OUTPUT,6,7,8,9,10,11)
                .role(Role.MANUAL,UPGRADE).filter(stack->stack.is(FarmingSystems.UPGRADES),UPGRADE);
    }
    private List<RecipeHolder<GrowingRecipe>> recipes(){return level==null?List.of():level.getRecipeManager().getAllRecipesFor(FarmingSystems.TYPE.get());}
    public boolean isSoil(ItemStack stack){return recipes().stream().anyMatch(r->r.value().soil().test(stack));}
    public boolean isSeed(ItemStack stack){return recipes().stream().anyMatch(r->r.value().seed().test(stack));}
    public IItemHandler itemCapability(){return items.view(IoMode.BOTH);}
    public long greenhouse(){return greenhouse;}
    public long greenhouseSpace(){return Math.max(0,settings.capacity()-greenhouse);}
    /** Called by an adjacent controller cluster; returns the amount accepted. */
    public long acceptGreenhouse(long amount){
        long accepted=Math.min(Math.max(0,amount),greenhouseSpace());
        if(accepted>0){greenhouse+=accepted;setChanged();}
        return accepted;
    }
    public void setGreenhouse(long amount){greenhouse=Math.clamp(amount,0,settings.capacity());setChanged();}

    private Optional<RecipeHolder<GrowingRecipe>> recipe(int lane){
        var seed=items.getStackInSlot(SEED+lane);var soil=items.getStackInSlot(lane);
        if(level==null||seed.isEmpty()||soil.isEmpty())return Optional.empty();
        return level.getRecipeManager().getRecipeFor(FarmingSystems.TYPE.get(),new GrowingRecipe.Input(seed,soil),level);
    }
    public static void tick(Level world,BlockPos pos,BlockState state,GrowthChamberBlockEntity be){
        if(!(world instanceof ServerLevel level))return;
        boolean disabled=level.hasNeighborSignal(pos),active=false;
        for(int lane=0;lane<LANES;lane++)active|=be.growLane(lane,disabled);
        var updated=be.getBlockState().setValue(GrowthChamberBlock.ACTIVE,active).setValue(GrowthChamberBlock.POWERED,be.greenhouse>0);
        if(updated!=be.getBlockState())level.setBlock(pos,updated,3);
        int remaining=be.settings.itemExport();
        for(int n=0;n<6&&remaining>0;n++){
            var side=Direction.values()[(int)((level.getGameTime()+n)%6)];
            if(level.hasChunkAt(pos.relative(side)))remaining-=Transfer.pushItems(level,pos,side,be.items.view(IoMode.OUTPUT),remaining);
        }
    }
    private boolean growLane(int lane,boolean disabled){
        if(!pending.get(lane).isEmpty()){
            if(!emit(pending.get(lane)))return false;
            pending.get(lane).clear();setChanged();
        }
        var found=recipe(lane);
        if(found.isEmpty()){reset(lane);return false;}
        var seed=items.getStackInSlot(SEED+lane);var soil=items.getStackInSlot(lane);
        if(!found.get().id().equals(recipeIds[lane])||!ItemStack.isSameItemSameComponents(seed,workSeeds[lane])||!ItemStack.isSameItemSameComponents(soil,workSoils[lane])){
            reset(lane);recipeIds[lane]=found.get().id();workSeeds[lane]=seed.copyWithCount(1);workSoils[lane]=soil.copyWithCount(1);setChanged();
        }
        // Without power or with a redstone signal, growth pauses and keeps its progress.
        if(disabled||greenhouse<1)return false;
        greenhouse--;progress[lane]++;setChanged();
        var recipe=found.get().value();
        if(progress[lane]>=recipe.time()){
            progress[lane]=0;
            var harvest=recipe.harvest(seed,FarmingSystems.fertility(soil),items.getStackInSlot(UPGRADE),level.random);
            if(!emit(harvest))pending.get(lane).addAll(harvest);
        }
        return true;
    }
    private void reset(int lane){
        if(progress[lane]!=0||recipeIds[lane]!=null)setChanged();
        progress[lane]=0;recipeIds[lane]=null;workSeeds[lane]=ItemStack.EMPTY;workSoils[lane]=ItemStack.EMPTY;
    }
    /** Inserts the whole harvest into the outputs, or nothing at all. */
    private boolean emit(List<ItemStack> harvest){
        var simulated=new ArrayList<ItemStack>();
        for(int slot=OUTPUT;slot<OUTPUT+OUTPUTS;slot++)simulated.add(items.getStackInSlot(slot).copy());
        for(var stack:harvest)if(!insert(simulated,stack))return false;
        for(int n=0;n<OUTPUTS;n++)if(!ItemStack.matches(simulated.get(n),items.getStackInSlot(OUTPUT+n)))items.setStackInSlot(OUTPUT+n,simulated.get(n));
        return true;
    }
    private boolean insert(List<ItemStack> outputs,ItemStack stack){
        var remaining=stack.copy();
        for(int pass=0;pass<2&&!remaining.isEmpty();pass++)for(int n=0;n<outputs.size()&&!remaining.isEmpty();n++){
            var existing=outputs.get(n);
            if(pass==0?existing.isEmpty()||!ItemStack.isSameItemSameComponents(existing,remaining):!existing.isEmpty())continue;
            int limit=Math.min(items.getSlotLimit(OUTPUT+n),remaining.getMaxStackSize());
            int moved=Math.min(remaining.getCount(),limit-existing.getCount());
            if(moved<=0)continue;
            outputs.set(n,remaining.copyWithCount(existing.getCount()+moved));remaining.shrink(moved);
        }
        return remaining.isEmpty();
    }
    public List<ItemStack> pendingHarvest(int lane){return pending.get(lane).stream().map(ItemStack::copy).toList();}
    public void dropContents(Level level,BlockPos pos){
        items.dropAll(level,pos);
        for(var lane:pending)for(var stack:lane)net.minecraft.world.Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),stack.copy());
        pending.forEach(List::clear);
    }

    @Override public ItemStorage screenItems(){return items;}
    @Override public int[] screenDestinations(ItemStack stack){
        if(stack.is(FarmingSystems.UPGRADES))return new int[]{UPGRADE};
        boolean seed=isSeed(stack),soil=isSoil(stack);
        if(seed&&soil)return new int[]{3,4,5,0,1,2};
        return seed?new int[]{3,4,5}:soil?new int[]{0,1,2}:new int[0];
    }
    public long screenGreenhouse(){return greenhouse;}
    public long screenGreenhouseCapacity(){return settings.capacity();}
    private int time(int lane){return recipe(lane).map(r->r.value().time()).orElse(1);}
    public int screenProgress0(){return progress[0];}
    public int screenProgress1(){return progress[1];}
    public int screenProgress2(){return progress[2];}
    public int screenTime0(){return time(0);}
    public int screenTime1(){return time(1);}
    public int screenTime2(){return time(2);}

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        tag.put("Items",items.serializeNBT(registries));tag.putLong("Greenhouse",greenhouse);tag.putIntArray("Progress",progress);
        for(int lane=0;lane<LANES;lane++){
            if(recipeIds[lane]!=null){
                tag.putString("Recipe"+lane,recipeIds[lane].toString());
                tag.put("Seed"+lane,workSeeds[lane].save(registries));tag.put("Soil"+lane,workSoils[lane].save(registries));
            }
            if(!pending.get(lane).isEmpty()){
                var list=new ListTag();for(var stack:pending.get(lane))list.add(stack.save(registries));
                tag.put("Pending"+lane,list);
            }
        }
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("Items"))items.deserializeNBT(registries,tag.getCompound("Items"));
        greenhouse=Math.clamp(tag.getLong("Greenhouse"),0,settings.capacity());
        var saved=tag.getIntArray("Progress");
        for(int lane=0;lane<LANES;lane++){
            progress[lane]=lane<saved.length?Math.clamp(saved[lane],0,1000000):0;
            recipeIds[lane]=ResourceLocation.tryParse(tag.getString("Recipe"+lane));
            workSeeds[lane]=tag.contains("Seed"+lane)?ItemStack.parseOptional(registries,tag.getCompound("Seed"+lane)):ItemStack.EMPTY;
            workSoils[lane]=tag.contains("Soil"+lane)?ItemStack.parseOptional(registries,tag.getCompound("Soil"+lane)):ItemStack.EMPTY;
            pending.get(lane).clear();
            for(var entry:tag.getList("Pending"+lane,Tag.TAG_COMPOUND)){
                var stack=ItemStack.parseOptional(registries,(CompoundTag)entry);
                if(!stack.isEmpty())pending.get(lane).add(stack);
            }
        }
    }
}
