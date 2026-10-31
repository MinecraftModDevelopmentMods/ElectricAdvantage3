package zone.moddev.mc.electricadvantage.processing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe;

public final class ProcessingBlockEntity extends BlockEntity implements ScreenHost {
    public final ProcessingSettings settings;
    public final EnergyBuffer energy;
    public final ItemStorage items;
    public final FluidStorage input = new FluidStorage();
    public final FluidStorage output = new FluidStorage();
    private final int[] progress;
    private final ResourceLocation[] recipeIds;
    private final ItemStack[] workInputs;
    private final IEnergyStorage energyInput;
    private int fluidProgress;
    private int fluidDuration;
    private ResourceLocation refiningFluid;
    private FluidStack pending = FluidStack.EMPTY;

    private record Work(ResourceLocation id,ItemStack result){}

    public ProcessingBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,ProcessingSettings settings){
        super(type,pos,state);
        this.settings=settings;
        energy=new EnergyBuffer(settings.capacity(),settings.rate()).onChange(this::setChanged);
        energyInput=energy.view(IoMode.INPUT);
        items=new ItemStorage(settings.inputs()+settings.outputs()).onChange(this::setChanged);
        for(int slot=0;slot<settings.inputs();slot++) items.role(Role.INPUT,slot).filter(stack -> findWork(stack)!=null,slot);
        for(int slot=settings.inputs();slot<items.getSlots();slot++) items.role(Role.OUTPUT,slot);
        progress=new int[settings.inputs()];recipeIds=new ResourceLocation[settings.inputs()];
        workInputs=new ItemStack[settings.inputs()];java.util.Arrays.fill(workInputs,ItemStack.EMPTY);
        if(fluidMachine()) input.addTank(settings.fluidCapacity(),Role.INPUT,this::acceptsFluid);
        if(distiller()) output.addTank(settings.fluidCapacity(),Role.OUTPUT);
        input.onChange(this::setChanged);output.onChange(this::setChanged);
    }
    public boolean distiller(){return settings.kind().equals("distilling");}
    public boolean refinery(){return settings.kind().equals("refining");}
    public boolean fluidMachine(){return distiller()||refinery();}
    public IEnergyStorage energyCapability(){return energyInput;}
    public net.neoforged.neoforge.items.IItemHandler itemCapability(){return items.view(IoMode.BOTH);}
    public IFluidHandler fluidCapability(){
        return new IFluidHandler(){
            @Override public int getTanks(){return input.getTanks()+output.getTanks();}
            @Override public FluidStack getFluidInTank(int tank){return tank<input.getTanks()?input.getFluidInTank(tank):output.getFluidInTank(tank-input.getTanks());}
            @Override public int getTankCapacity(int tank){return tank<input.getTanks()?input.getTankCapacity(tank):output.getTankCapacity(tank-input.getTanks());}
            @Override public boolean isFluidValid(int tank,FluidStack stack){return tank>=0&&tank<input.getTanks()&&input.isFluidValid(tank,stack);}
            @Override public int fill(FluidStack stack,FluidAction action){return input.fill(stack,action);}
            @Override public FluidStack drain(FluidStack stack,FluidAction action){return output.drain(stack,action);}
            @Override public FluidStack drain(int amount,FluidAction action){return output.drain(amount,action);}
        };
    }
    private boolean acceptsFluid(FluidStack fluid){
        if(distiller()) return level!=null&&DistillingRecipe.accepts(level,fluid);
        return settings.acceptedFluids().contains(BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
    }
    private Work findWork(ItemStack stack){
        if(level==null||stack.isEmpty()) return null;
        var query=new SingleRecipeInput(stack);
        if(settings.kind().equals("crushing")){
            var recipe=level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(),query,level);
            return recipe.map(r -> new Work(r.id(),r.value().assemble(query,level.registryAccess()))).orElse(null);
        }
        if(!settings.kind().equals("smelting")&&!settings.kind().equals("oven")) return null;
        var recipe=level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,query,level);
        if(recipe.isEmpty()) return null;
        var result=recipe.get().value().assemble(query,level.registryAccess());
        if(settings.kind().equals("oven")&&!result.has(DataComponents.FOOD)) return null;
        return result.isEmpty()?null:new Work(recipe.get().id(),result);
    }
    private boolean fits(ItemStack result){
        var remaining=result.copy();
        for(int slot=settings.inputs();slot<items.getSlots()&&!remaining.isEmpty();slot++) remaining=items.insertResult(slot,remaining,true);
        return remaining.isEmpty();
    }
    private void emit(ItemStack result){
        var remaining=result.copy();
        for(int slot=settings.inputs();slot<items.getSlots()&&!remaining.isEmpty();slot++) remaining=items.insertResult(slot,remaining,false);
        if(!remaining.isEmpty()) throw new IllegalStateException("Processing output reservation changed during a server tick");
    }
    public static void tick(Level world,BlockPos pos,BlockState state,ProcessingBlockEntity be){
        if(!(world instanceof ServerLevel level)) return;
        boolean disabled=level.hasNeighborSignal(pos);
        boolean active=be.fluidMachine()?be.processFluid(disabled):be.processItems(disabled);
        var updated=be.getBlockState().setValue(ProcessingBlock.ACTIVE,active)
                .setValue(ProcessingBlock.POWERED,be.energy.stored()>=be.settings.cost());
        if(updated!=be.getBlockState())level.setBlock(pos,updated,3);
        int itemsLeft=be.settings.itemExport(),fluidsLeft=be.settings.fluidExport();
        for(int n=0;n<6;n++){
            Direction side=Direction.values()[(int)((level.getGameTime()+n)%6)];
            if(!level.hasChunkAt(pos.relative(side))) continue;
            if(itemsLeft>0) itemsLeft-=Transfer.pushItems(level,pos,side,be.items.view(IoMode.OUTPUT),itemsLeft);
            if(fluidsLeft>0&&be.output.getTanks()>0) fluidsLeft-=Transfer.pushFluids(level,pos,side,be.output.view(IoMode.OUTPUT),fluidsLeft);
        }
    }
    private boolean processItems(boolean disabled){
        boolean active=false;
        for(int slot=0;slot<settings.inputs();slot++){
            var stack=items.getStackInSlot(slot);
            var work=findWork(stack);
            if(work==null||work.result().isEmpty()||!fits(work.result())){reset(slot);continue;}
            if(!work.id().equals(recipeIds[slot])||!ItemStack.isSameItemSameComponents(stack,workInputs[slot])){
                reset(slot);recipeIds[slot]=work.id();workInputs[slot]=stack.copyWithCount(1);setChanged();
            }
            if(disabled||!energy.consume(settings.cost())){
                int previous=progress[slot];
                progress[slot]=settings.kind().equals("oven")&&disabled?0:Math.max(0,previous-1);
                if(previous!=progress[slot])setChanged();
                continue;
            }
            active=true;
            if(++progress[slot]>=settings.ticks()){
                emit(work.result());
                var remaining=stack.copy();remaining.shrink(1);items.setStackInSlot(slot,remaining);
                reset(slot);
            }
            setChanged();
        }
        return active;
    }
    private void reset(int slot){
        if(progress[slot]!=0||recipeIds[slot]!=null)setChanged();
        progress[slot]=0;recipeIds[slot]=null;workInputs[slot]=ItemStack.EMPTY;
    }
    private boolean processFluid(boolean disabled){
        if(disabled)return false;
        if(refinery())return refine();
        if(pending.isEmpty()){
            var recipe=DistillingRecipe.find(level,input.getFluidInTank(0));
            if(recipe.isEmpty())return false;
            var result=recipe.get().value().output().copy();
            if(output.tank(0).fill(result,IFluidHandler.FluidAction.SIMULATE)!=result.getAmount()||energy.stored()<settings.cost())return false;
            input.tank(0).drain(recipe.get().value().input().amount(),IFluidHandler.FluidAction.EXECUTE);
            pending=result;fluidDuration=recipe.get().value().duration();fluidProgress=0;setChanged();
        }
        boolean working=false;
        if(fluidProgress<fluidDuration){
            if(!energy.consume(settings.cost()))return false;
            fluidProgress++;working=true;setChanged();
        }
        if(fluidProgress>=fluidDuration&&output.tank(0).fill(pending,IFluidHandler.FluidAction.SIMULATE)==pending.getAmount()){
            output.tank(0).fill(pending,IFluidHandler.FluidAction.EXECUTE);
            pending=FluidStack.EMPTY;fluidProgress=0;fluidDuration=0;working=true;setChanged();
        }
        return working;
    }
    private boolean refine(){
        var fluid=input.getFluidInTank(0);
        var result=new ItemStack(BuiltInRegistries.ITEM.get(settings.resultItem()));
        if(fluid.isEmpty()||!acceptsFluid(fluid)||fluid.getAmount()<settings.fluidPerItem()||result.isEmpty()||!fits(result)){
            if(fluidProgress!=0){fluidProgress=0;setChanged();}return false;
        }
        var identity=BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        if(!identity.equals(refiningFluid)){fluidProgress=0;refiningFluid=identity;setChanged();}
        if(!energy.consume(settings.cost()))return false;
        if(++fluidProgress>=settings.ticks()){
            input.tank(0).drain(settings.fluidPerItem(),IFluidHandler.FluidAction.EXECUTE);emit(result);fluidProgress=0;
        }
        setChanged();return true;
    }
    public long screenEnergy(){return energy.stored();}
    public long screenCapacity(){return energy.capacity();}
    public int screenDuration(){return distiller()?Math.max(1,fluidDuration):settings.ticks();}
    public int screenProgress(){return fluidProgress;}
    public int screenProgress0(){return progress.length>0?progress[0]:0;}
    public int screenProgress1(){return progress.length>1?progress[1]:0;}
    public int screenProgress2(){return progress.length>2?progress[2]:0;}
    public int screenProgress3(){return progress.length>3?progress[3]:0;}
    public FluidStack screenInputFluid(){return input.getTanks()>0?input.getFluidInTank(0):FluidStack.EMPTY;}
    public FluidStack screenOutputFluid(){return output.getTanks()>0?output.getFluidInTank(0):FluidStack.EMPTY;}
    public int screenFluidCapacity(){return settings.fluidCapacity();}
    @Override public ItemStorage screenItems(){return items;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        tag.put("Energy",energy.serializeNBT(registries));tag.put("Items",items.serializeNBT(registries));
        tag.put("Input",input.serializeNBT(registries));tag.put("Output",output.serializeNBT(registries));
        tag.putIntArray("Progress",progress);
        for(int slot=0;slot<progress.length;slot++)if(recipeIds[slot]!=null){
            tag.putString("Recipe"+slot,recipeIds[slot].toString());tag.put("Work"+slot,workInputs[slot].save(registries));
        }
        tag.putInt("FluidProgress",fluidProgress);tag.putInt("FluidDuration",fluidDuration);
        if(!pending.isEmpty())tag.put("Pending",pending.save(registries));
        if(refiningFluid!=null)tag.putString("RefiningFluid",refiningFluid.toString());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("Energy"))energy.deserializeNBT(registries,tag.get("Energy"));
        if(tag.contains("Items"))items.deserializeNBT(registries,tag.getCompound("Items"));
        if(tag.contains("Input"))input.deserializeNBT(registries,tag.getCompound("Input"));
        if(tag.contains("Output"))output.deserializeNBT(registries,tag.getCompound("Output"));
        var saved=tag.getIntArray("Progress");
        for(int slot=0;slot<progress.length;slot++){
            progress[slot]=slot<saved.length?Math.clamp(saved[slot],0,settings.ticks()):0;
            recipeIds[slot]=ResourceLocation.tryParse(tag.getString("Recipe"+slot));
            workInputs[slot]=tag.contains("Work"+slot)?ItemStack.parseOptional(registries,tag.getCompound("Work"+slot)):ItemStack.EMPTY;
        }
        fluidDuration=Math.clamp(tag.getInt("FluidDuration"),0,1000000);
        fluidProgress=Math.clamp(tag.getInt("FluidProgress"),0,Math.max(settings.ticks(),fluidDuration));
        pending=tag.contains("Pending")?FluidStack.parseOptional(registries,tag.getCompound("Pending")):FluidStack.EMPTY;
        refiningFluid=ResourceLocation.tryParse(tag.getString("RefiningFluid"));
    }
}
