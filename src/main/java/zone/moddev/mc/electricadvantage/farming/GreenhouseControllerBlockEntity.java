package zone.moddev.mc.electricadvantage.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import java.util.*;

/**
 * Converts electricity, water and fertilizer into greenhouse power and shares it fairly across the
 * growth chambers it touches, directly or through other touching chambers. There is no greenhouse cable.
 */
public final class GreenhouseControllerBlockEntity extends BlockEntity implements ScreenHost {
    public final GreenhouseControllerSettings settings;
    public final EnergyBuffer energy;
    public final FluidStorage water=new FluidStorage();
    public final ItemStorage items;
    private final IEnergyStorage energyInput;
    private long fertilizer,greenhouse;
    private final List<BlockPos> chambers=new ArrayList<>();
    private long nextScan=Long.MIN_VALUE;
    private int rotation;

    public GreenhouseControllerBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,GreenhouseControllerSettings settings){
        super(type,pos,state);this.settings=settings;
        energy=new EnergyBuffer(settings.energyCapacity(),settings.rate()).onChange(this::setChanged);energyInput=energy.view(IoMode.INPUT);
        water.addTank(settings.waterCapacity(),Role.INPUT,fluid->fluid.getFluid().is(FluidTags.WATER));water.onChange(this::setChanged);
        items=new ItemStorage(1).onChange(this::setChanged).role(Role.INPUT,0).filter(stack->FarmingSystems.fertilizer(stack)>0,0);
    }
    public IEnergyStorage energyCapability(){return energyInput;}
    public IFluidHandler fluidCapability(){return water.view(IoMode.INPUT);}
    public IItemHandler itemCapability(){return items.view(IoMode.INPUT);}
    public long fertilizer(){return fertilizer;}
    public long greenhouse(){return greenhouse;}
    public void setGreenhouse(long amount){greenhouse=Math.clamp(amount,0,settings.buffer());setChanged();}
    public List<BlockPos> chambers(){return List.copyOf(chambers);}
    public void rescan(){nextScan=Long.MIN_VALUE;}

    public static void tick(Level world,BlockPos pos,BlockState state,GreenhouseControllerBlockEntity be){
        if(!(world instanceof ServerLevel level))return;
        be.refuel();
        boolean disabled=level.hasNeighborSignal(pos);
        if(!disabled){be.produce();be.distribute(level);}
        var updated=be.getBlockState().setValue(GreenhouseControllerBlock.POWERED,!disabled&&be.greenhouse>0);
        if(updated!=be.getBlockState())level.setBlock(pos,updated,3);
    }
    /** Takes one fertilizer item whenever its whole value fits in the pool. */
    private void refuel(){
        var stack=items.getStackInSlot(0);int value=FarmingSystems.fertilizer(stack);
        if(value>0&&fertilizer+value<=Math.max(settings.fertilizerCapacity(),value)){
            fertilizer+=value;var remaining=stack.copy();remaining.shrink(1);items.setStackInSlot(0,remaining);setChanged();
        }
    }
    private long units(long available,int perUnit){return perUnit==0?Long.MAX_VALUE:available/perUnit;}
    private void produce(){
        long made=Math.min(settings.production(),settings.buffer()-greenhouse);
        made=Math.min(made,units(energy.stored(),settings.fePerUnit()));
        made=Math.min(made,units(water.getFluidInTank(0).getAmount(),settings.waterPerUnit()));
        made=Math.min(made,units(fertilizer,settings.fertilizerPerUnit()));
        if(made<=0)return;
        energy.consume(made*settings.fePerUnit());
        water.tank(0).drain((int)(made*settings.waterPerUnit()),IFluidHandler.FluidAction.EXECUTE);
        fertilizer-=made*settings.fertilizerPerUnit();greenhouse+=made;setChanged();
    }
    /** Bounded flood fill over touching growth chambers, starting from this controller's faces. */
    private void scan(ServerLevel level){
        chambers.clear();
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();seen.add(worldPosition);queue.add(worldPosition);
        while(!queue.isEmpty()&&chambers.size()<settings.maxChambers()){
            var from=queue.poll();
            for(var side:Direction.values()){
                var next=from.relative(side);
                if(!seen.add(next)||!level.isLoaded(next))continue;
                if(level.getBlockEntity(next) instanceof GrowthChamberBlockEntity){
                    chambers.add(next.immutable());queue.add(next);
                    if(chambers.size()>=settings.maxChambers())break;
                }
            }
        }
    }
    private void distribute(ServerLevel level){
        long time=level.getGameTime();
        if(time>=nextScan){scan(level);nextScan=time+settings.scanInterval();}
        var targets=new ArrayList<GrowthChamberBlockEntity>();
        for(var at:chambers)if(level.isLoaded(at)&&level.getBlockEntity(at) instanceof GrowthChamberBlockEntity chamber)targets.add(chamber);
        if(targets.isEmpty()||greenhouse<=0)return;
        // Equal shares, rotating the remainder so no chamber is always first or last.
        Collections.rotate(targets,-(rotation++%targets.size()));
        long before=greenhouse;
        while(greenhouse>0){
            var hungry=targets.stream().filter(c->c.greenhouseSpace()>0).toList();
            if(hungry.isEmpty())break;
            long share=Math.max(1,greenhouse/hungry.size());
            for(var chamber:hungry){if(greenhouse<=0)break;greenhouse-=chamber.acceptGreenhouse(Math.min(share,greenhouse));}
        }
        if(greenhouse!=before)setChanged();
    }
    /** EA2 behaviour: 15 when energy, water and fertilizer are all enough for a unit, otherwise 0. */
    public int comparator(){
        return energy.stored()>=settings.fePerUnit()&&water.getFluidInTank(0).getAmount()>=settings.waterPerUnit()
                &&fertilizer>=settings.fertilizerPerUnit()?15:0;
    }

    @Override public ItemStorage screenItems(){return items;}
    public long screenEnergy(){return energy.stored();}
    public long screenCapacity(){return energy.capacity();}
    public FluidStack screenWater(){return water.getFluidInTank(0);}
    public int screenWaterCapacity(){return settings.waterCapacity();}
    public long screenFertilizer(){return fertilizer;}
    public long screenFertilizerCapacity(){return settings.fertilizerCapacity();}
    public long screenGreenhouse(){return greenhouse;}
    public long screenGreenhouseCapacity(){return settings.buffer();}
    public Component screenChambers(){return Component.translatable("screen.electricadvantage.greenhouse.chambers",chambers.size(),settings.maxChambers());}

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        tag.put("Energy",energy.serializeNBT(registries));tag.put("Water",water.serializeNBT(registries));tag.put("Items",items.serializeNBT(registries));
        tag.putLong("Fertilizer",fertilizer);tag.putLong("Greenhouse",greenhouse);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("Energy"))energy.deserializeNBT(registries,tag.get("Energy"));
        if(tag.contains("Water"))water.deserializeNBT(registries,tag.getCompound("Water"));
        if(tag.contains("Items"))items.deserializeNBT(registries,tag.getCompound("Items"));
        fertilizer=Math.max(0,tag.getLong("Fertilizer"));greenhouse=Math.clamp(tag.getLong("Greenhouse"),0,settings.buffer());
        rescan();
    }
}
