package zone.moddev.mc.electricadvantage.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.electricadvantage.Config;
import zone.moddev.mc.poweradvantage.util.FluidSearch;
import net.minecraft.world.level.material.Fluid;
import java.util.ArrayList;
import zone.moddev.mc.electricadvantage.infrastructure.PumpPipeBlock;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/**
 * Extends a pump pipe down through air, then takes one bucket per pump interval from the source
 * feeding its tip. A body of tagged fluid with enough connected sources is treated as naturally
 * infinite and keeps its source blocks. Fluid tracing is Power Advantage's {@link FluidSearch}.
 */
public final class PumpBlockEntity extends BlockEntity implements ScreenHost {
    public enum Status {IDLE,EXTENDING,PUMPING_INFINITE,PUMPING,NO_FLUID,TANK_FULL,NO_POWER,DISABLED}
    private static final java.util.function.Supplier<net.minecraft.world.level.block.Block> PIPE=com.google.common.base.Suppliers.memoize(
            ()->net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("electricadvantage","pump_pipe_electric")));
    /** Fluids that count as naturally infinite once a body reaches the configured size. */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> INFINITE=net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.FLUID,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("electricadvantage","pump_infinite"));
    public final PumpSettings settings;
    public final EnergyBuffer energy;
    public final FluidStorage tank=new FluidStorage();
    private final IEnergyStorage energyInput;
    private final ItemStorage noItems=new ItemStorage(0);
    private int cooldown,depth;
    // Cached infinite/finite verdict for the body at the pipe tip. Pumping only shrinks a body, so a
    // finite verdict stays valid too; both are rechecked after infinite_recheck ticks.
    private BlockPos verdictTip;
    private Fluid verdictFluid;
    private int verdictThreshold;
    private boolean verdictInfinite;
    private long verdictUntil;
    private Status status=Status.IDLE;

    public PumpBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,PumpSettings settings){
        super(type,pos,state);this.settings=settings;
        energy=new EnergyBuffer(settings.capacity(),settings.rate()).onChange(this::setChanged);energyInput=energy.view(IoMode.INPUT);
        tank.addTank(settings.tankCapacity(),Role.OUTPUT);tank.onChange(this::setChanged);
    }
    public IEnergyStorage energyCapability(){return energyInput;}
    public IFluidHandler fluidCapability(){return tank.view(IoMode.BOTH);}
    public Status status(){return status;}
    public int depth(){return depth;}

    public static void tick(Level world,BlockPos pos,BlockState state,PumpBlockEntity be){
        if(!(world instanceof ServerLevel level))return;
        boolean active=be.work(level);
        var updated=be.getBlockState().setValue(PumpBlock.ACTIVE,active).setValue(PumpBlock.POWERED,be.energy.stored()>=be.settings.pumpCost());
        if(updated!=be.getBlockState())level.setBlock(pos,updated,3);
        int remaining=be.settings.fluidExport();
        for(int n=0;n<6&&remaining>0&&!be.tank.getFluidInTank(0).isEmpty();n++){
            var side=Direction.values()[(int)((level.getGameTime()+n)%6)];
            if(level.hasChunkAt(pos.relative(side)))remaining-=Transfer.pushFluids(level,pos,side,be.tank.view(IoMode.OUTPUT),remaining);
        }
    }
    private void status(Status next){if(status!=next){status=next;setChanged();}}
    private boolean work(ServerLevel level){
        if(level.hasNeighborSignal(worldPosition)){status(Status.DISABLED);return false;}
        if(cooldown>0){cooldown--;setChanged();return status==Status.EXTENDING||status==Status.PUMPING||status==Status.PUMPING_INFINITE;}
        var tip=worldPosition.below();
        while(!level.isOutsideBuildHeight(tip)&&level.isLoaded(tip)&&level.getBlockState(tip).getBlock() instanceof PumpPipeBlock)tip=tip.below();
        int reach=worldPosition.getY()-tip.getY()-1;
        if(reach!=depth){depth=reach;setChanged();}
        if(level.isOutsideBuildHeight(tip)||!level.isLoaded(tip)){status(Status.NO_FLUID);return false;}
        if(level.isEmptyBlock(tip)){
            if(!energy.consume(settings.pipeCost())){status(Status.NO_POWER);return false;}
            level.setBlock(tip,PIPE.get().defaultBlockState(),3);
            level.playSound(null,tip,SoundEvents.STONE_PLACE,SoundSource.BLOCKS,0.3f,1f);
            depth++;verdictTip=null;cooldown=settings.pipeInterval();status(Status.EXTENDING);return true;
        }
        if(tank.getTankCapacity(0)-tank.getFluidInTank(0).getAmount()<1000){status(Status.TANK_FULL);return false;}
        var source=findSource(level,tip);
        if(source==null){status(Status.NO_FLUID);cooldown=settings.pumpInterval();return false;}
        var fluid=level.getFluidState(source).getType();
        var bucket=new FluidStack(fluid,1000);
        if(tank.tank(0).fill(bucket,IFluidHandler.FluidAction.SIMULATE)!=1000){status(Status.TANK_FULL);return false;}
        long cost=settings.pumpCost()+(long)settings.depthCost()*Math.max(0,worldPosition.getY()-source.getY());
        if(energy.stored()<cost){status(Status.NO_POWER);return false;}
        boolean infinite=infinite(level,tip,source,fluid);
        if(!infinite){
            var state=level.getBlockState(source);
            if(!(state.getBlock() instanceof BucketPickup pickup)||pickup.pickupBlock(null,level,source,state).isEmpty()){
                status(Status.NO_FLUID);cooldown=settings.pumpInterval();return false;
            }
        }
        energy.consume(cost);tank.tank(0).fill(bucket,IFluidHandler.FluidAction.EXECUTE);
        level.playSound(null,worldPosition,SoundEvents.BUCKET_FILL,SoundSource.BLOCKS,0.5f,1f);
        cooldown=settings.pumpInterval();status(infinite?Status.PUMPING_INFINITE:Status.PUMPING);setChanged();
        return true;
    }
    /** The source feeding the pipe tip, or the blocks beside the lowest pipe, traced upstream by PA3. */
    private static BlockPos findSource(Level level,BlockPos tip){
        var seeds=new ArrayList<BlockPos>();seeds.add(tip);
        for(var side:Direction.Plane.HORIZONTAL)seeds.add(tip.above().relative(side));
        for(var seed:seeds){
            if(!level.isLoaded(seed))continue;
            var state=level.getFluidState(seed);
            if(state.isEmpty())continue;
            // PA3's upstream trace does not check chunk loading; reject a source it found in an unloaded chunk.
            var source=FluidSearch.findSourceBlock(level,state.getType(),seed);
            if(source!=null&&level.isLoaded(source)&&level.getFluidState(source).isSource())return source;
        }
        return null;
    }
    private boolean infinite(Level level,BlockPos tip,BlockPos source,Fluid fluid){
        int threshold=Config.pumpInfiniteThreshold();
        if(threshold<=0||!fluid.defaultFluidState().is(INFINITE))return false;
        long time=level.getGameTime();
        if(tip.equals(verdictTip)&&fluid.isSame(verdictFluid)&&threshold==verdictThreshold&&time<verdictUntil)return verdictInfinite;
        verdictInfinite=FluidSearch.countSourceBlocks(level,fluid,source,threshold)>=threshold;
        verdictTip=tip;verdictFluid=fluid;verdictThreshold=threshold;verdictUntil=time+settings.infiniteRecheck();
        return verdictInfinite;
    }
    public int comparator(){var fluid=tank.getFluidInTank(0);return fluid.isEmpty()?0:Math.max(1,15*fluid.getAmount()/settings.tankCapacity());}

    @Override public ItemStorage screenItems(){return noItems;}
    public long screenEnergy(){return energy.stored();}
    public long screenCapacity(){return energy.capacity();}
    public FluidStack screenFluid(){return tank.getFluidInTank(0);}
    public int screenFluidCapacity(){return settings.tankCapacity();}
    public Component screenStatus(){return Component.translatable("screen.electricadvantage.pump.status."+status.name().toLowerCase(java.util.Locale.ROOT));}
    public Component screenDepth(){return Component.translatable("screen.electricadvantage.pump.depth",depth);}

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        tag.put("Energy",energy.serializeNBT(registries));tag.put("Tank",tank.serializeNBT(registries));
        tag.putInt("Cooldown",cooldown);tag.putInt("Depth",depth);tag.putString("Status",status.name());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("Energy"))energy.deserializeNBT(registries,tag.get("Energy"));
        if(tag.contains("Tank"))tank.deserializeNBT(registries,tag.getCompound("Tank"));
        cooldown=Math.clamp(tag.getInt("Cooldown"),0,1200);depth=Math.max(0,tag.getInt("Depth"));
        try{status=Status.valueOf(tag.getString("Status"));}catch(IllegalArgumentException missing){status=Status.IDLE;}
        // Fluid bodies change while unloaded; always trace again.
        verdictTip=null;
    }
}
