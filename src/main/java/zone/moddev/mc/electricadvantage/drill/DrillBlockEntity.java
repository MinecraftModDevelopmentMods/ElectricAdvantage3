package zone.moddev.mc.electricadvantage.drill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import java.util.ArrayList;
import java.util.List;

/**
 * EA2's electric mining drill. Every power update it looks along its facing, up to its range, through
 * air and replaceable blocks (fluids included), and targets the 3x3 face around the first solid block.
 * Each tick of mining costs energy; the face is removed once progress reaches the sum of its blocks'
 * strengths, and the drops (worked out when the face was targeted) go to its output slots. At the end
 * of its range it moves onto adjacent electric scaffold, leaving a cable where it was and a steel frame
 * in front of that.
 */
public final class DrillBlockEntity extends BlockEntity implements ScreenHost {
    public enum Status {IDLE,MINING,OUTPUT_FULL,END_OF_RANGE,NO_POWER,DISABLED}
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> LASER=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse("electricadvantage:laser_drill"));
    private static final java.util.function.Supplier<Block> TRACK=block("electricadvantage:electric_track"),
            CABLE=block("electricadvantage:electric_conduit"),FRAME=block("poweradvantage:steel_frame");
    public final DrillSettings settings;
    public final EnergyBuffer energy;
    public final ItemStorage items;
    private final IEnergyStorage energyInput;
    private int progress,progressGoal,age,beamLength;
    private BlockPos target,savedTarget;
    private BlockState[] targetStates;
    private List<ItemStack> targetDrops;
    private Direction facing;
    private boolean endOfRange;
    private Status status=Status.IDLE;

    public DrillBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,DrillSettings settings){
        super(type,pos,state);this.settings=settings;
        energy=new EnergyBuffer(settings.capacity(),settings.rate()).onChange(this::setChanged);energyInput=energy.view(IoMode.INPUT);
        items=new ItemStorage(6).onChange(this::setChanged);
        for(int slot=0;slot<items.getSlots();slot++)items.role(Role.OUTPUT,slot);
    }
    private static java.util.function.Supplier<Block> block(String id){
        return com.google.common.base.Suppliers.memoize(()->BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)));
    }
    public IEnergyStorage energyCapability(){return energyInput;}
    public net.neoforged.neoforge.items.IItemHandler itemCapability(){return items.view(IoMode.BOTH);}
    public Status status(){return status;}
    public BlockPos target(){return target;}
    public int progress(){return progress;}
    public int progressGoal(){return progressGoal;}
    /** Blocks from the drill to the targeted face, or 0 when no beam is shown. */
    public int beamLength(){return beamLength;}
    private boolean powered(){return energy.stored()>settings.progressCost();}

    public static void tick(Level world,BlockPos pos,BlockState state,DrillBlockEntity be){
        if(world instanceof ServerLevel level)be.work(level);
    }
    private void work(ServerLevel level){
        updateFacing();
        if(savedTarget!=null){
            // Block types and drops are read again once the level is available; progress is kept.
            int saved=progress;targetFace(level,savedTarget);progress=Math.min(saved,progressGoal);savedTarget=null;
        }
        boolean disabled=level.hasNeighborSignal(worldPosition);
        if(disabled){
            if(progress>0){progress=0;setChanged();}
        }else if(target!=null){
            if(powered()&&fits(targetDrops)&&canMine(level,target)){
                energy.consume(settings.progressCost());
                if(++progress>=progressGoal)mine(level);
                setChanged();
            }else if(progress>0){progress--;setChanged();}
        }
        // EA2's power update: every update_interval ticks, staggered by position.
        if(++age>=settings.updateInterval())age=0;
        if(age==Math.floorMod(worldPosition.hashCode(),settings.updateInterval())&&powerUpdate(level,disabled))return;
        status(disabled?Status.DISABLED:!powered()?Status.NO_POWER:target==null?(endOfRange?Status.END_OF_RANGE:Status.IDLE)
                :fits(targetDrops)?Status.MINING:Status.OUTPUT_FULL);
        var updated=getBlockState().setValue(DrillBlock.ACTIVE,powered()&&!disabled);
        if(updated!=getBlockState())level.setBlock(worldPosition,updated,3);
    }
    private void status(Status next){if(status!=next){status=next;setChanged();}}
    /** Rotating the drill drops its target, as in EA2. */
    private void updateFacing(){
        var current=getBlockState().getValue(DrillBlock.FACING);
        if(facing!=null&&facing!=current){untarget();sync();}
        facing=current;
    }

    /** Targets, re-checks or moves, then sets the beam, exports one item and burns anything in the beam. Returns true once the drill has moved. */
    private boolean powerUpdate(ServerLevel level,boolean disabled){
        if(disabled||energy.stored()<=0){untarget();}
        else if(target==null){
            endOfRange=false;
            var next=worldPosition.relative(facing);
            for(int distance=0;;distance++){
                if(distance==settings.range()){endOfRange=true;break;}
                // Unlike EA2, the drill doesn't load chunks; it waits for the one ahead.
                if(!level.isLoaded(next))break;
                var state=level.getBlockState(next);
                if(state.isAir()||state.canBeReplaced()){
                    next=next.relative(facing);
                    if(next.getY()<=level.getMinBuildHeight()||next.getY()>=level.getMaxBuildHeight()-1){endOfRange=true;break;}
                }else{
                    if(canMine(level,next))targetFace(level,next);else endOfRange=true;
                    break;
                }
            }
            if(endOfRange&&followTrack(level))return true;
        }else{
            var area=area(target);
            for(int i=0;i<area.length;i++)if(level.getBlockState(area[i]).getBlock()!=targetStates[i].getBlock()){untarget();break;}
        }
        boolean beam=powered()&&!disabled&&target!=null;
        int length=beam?worldPosition.distManhattan(target):0;
        if(length!=beamLength){beamLength=length;sync();}
        var behind=facing.getOpposite();
        if(!disabled&&level.isLoaded(worldPosition.relative(behind))&&!level.isEmptyBlock(worldPosition.relative(behind)))
            Transfer.pushItems(level,worldPosition,behind,items.view(IoMode.OUTPUT),1);
        if(beam){
            // EA2's box runs from the target's corner to the far corner of the drill.
            var box=new AABB(target.getX(),target.getY(),target.getZ(),worldPosition.getX()+1,worldPosition.getY()+1,worldPosition.getZ()+1);
            var damage=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LASER));
            for(var victim:level.getEntitiesOfClass(LivingEntity.class,box)){victim.igniteForSeconds(settings.burnSeconds());victim.hurt(damage,settings.beamDamage());}
        }
        return false;
    }
    /** Moves onto the first adjacent scaffold (down, up, north, south, west, east), leaving a cable behind. */
    private boolean followTrack(ServerLevel level){
        if(energy.stored()<settings.moveCost())return false;
        Direction way=null;
        for(var side:Direction.values())if(level.getBlockState(worldPosition.relative(side)).is(TRACK.get())){way=side;break;}
        if(way==null)return false;
        untarget();
        var registries=level.registryAccess();
        var data=saveWithoutMetadata(registries);
        var next=worldPosition.relative(way);
        level.setBlock(next,getBlockState(),3);
        if(!(level.getBlockEntity(next) instanceof DrillBlockEntity moved))return false;
        moved.loadWithComponents(data,registries);
        moved.energy.consume(settings.moveCost());
        for(int slot=0;slot<items.getSlots();slot++)items.setStackInSlot(slot,ItemStack.EMPTY);
        // EA2 overwrites whatever is in front of the old position, bedrock included.
        level.setBlock(worldPosition.relative(facing),FRAME.get().defaultBlockState(),3);
        level.setBlock(worldPosition,CABLE.get().defaultBlockState(),3);
        return true;
    }
    private void mine(ServerLevel level){
        level.playSound(null,target,targetStates[0].getSoundType().getPlaceSound(),SoundSource.BLOCKS,0.5f,1f);
        level.playSound(null,worldPosition,SoundEvents.SAND_BREAK,SoundSource.BLOCKS,0.5f,1f);
        for(var pos:area(target))if(canMine(level,pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        for(var drop:targetDrops){
            var remaining=drop.copy();
            for(int slot=0;slot<items.getSlots()&&!remaining.isEmpty();slot++)remaining=items.insertResult(slot,remaining,false);
        }
        untarget();
    }
    private void targetFace(ServerLevel level,BlockPos center){
        var area=area(center);
        target=center;progress=0;progressGoal=0;
        targetStates=new BlockState[area.length];targetDrops=new ArrayList<>();
        for(int i=0;i<area.length;i++){
            var state=level.getBlockState(area[i]);
            targetStates[i]=state;
            progressGoal+=strength(level,area[i],state);
            targetDrops.addAll(Block.getDrops(state,level,area[i],level.getBlockEntity(area[i])));
        }
        setChanged();
    }
    private void untarget(){
        if(target!=null||progress!=0)setChanged();
        target=null;targetStates=null;targetDrops=null;progress=0;progressGoal=0;
    }
    /** The centre first, then the other eight blocks of the face across the drill's axis. */
    private BlockPos[] area(BlockPos center){
        var area=new BlockPos[9];area[0]=center;
        int n=1;
        for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++){
            if(a==0&&b==0)continue;
            area[n++]=switch(facing.getAxis()){
                case X->center.offset(0,a,b);
                case Y->center.offset(a,0,b);
                case Z->center.offset(a,b,0);
            };
        }
        return area;
    }
    /** Unbreakable blocks have negative hardness. */
    private static boolean canMine(Level level,BlockPos pos){return level.getBlockState(pos).getDestroySpeed(level,pos)>=0;}
    private int strength(Level level,BlockPos pos,BlockState state){
        if(state.isAir())return 0;
        return (int)Math.max(settings.miningTimeFactor()*state.getDestroySpeed(level,pos),0.5f*settings.miningTimeFactor());
    }
    /** Whether all the drops fit in the output slots together. */
    private boolean fits(List<ItemStack> drops){
        var slots=new ItemStack[items.getSlots()];
        for(int slot=0;slot<slots.length;slot++)slots[slot]=items.getStackInSlot(slot).copy();
        for(var drop:drops){
            int left=drop.getCount();
            for(int slot=0;slot<slots.length&&left>0;slot++){
                int limit=Math.min(drop.getMaxStackSize(),items.getSlotLimit(slot));
                if(slots[slot].isEmpty()){int n=Math.min(left,limit);slots[slot]=drop.copyWithCount(n);left-=n;}
                else if(ItemStack.isSameItemSameComponents(slots[slot],drop)){int n=Math.max(0,Math.min(left,limit-slots[slot].getCount()));slots[slot].grow(n);left-=n;}
            }
            if(left>0)return false;
        }
        return true;
    }
    private void sync(){if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}

    @Override public ItemStorage screenItems(){return items;}
    public long screenEnergy(){return energy.stored();}
    public long screenCapacity(){return energy.capacity();}
    public int screenProgress(){return progress;}
    public int screenProgressGoal(){return Math.max(1,progressGoal);}
    public Component screenStatus(){return Component.translatable("screen.electricadvantage.drill.status."+status.name().toLowerCase(java.util.Locale.ROOT));}

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        tag.put("Energy",energy.serializeNBT(registries));tag.put("Items",items.serializeNBT(registries));
        tag.putString("Status",status.name());
        var saved=target!=null?target:savedTarget;
        if(saved!=null){tag.putLong("Target",saved.asLong());tag.putInt("Progress",progress);}
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("Energy"))energy.deserializeNBT(registries,tag.get("Energy"));
        if(tag.contains("Items"))items.deserializeNBT(registries,tag.getCompound("Items"));
        if(tag.contains("Status"))try{status=Status.valueOf(tag.getString("Status"));}catch(IllegalArgumentException unknown){status=Status.IDLE;}
        if(tag.contains("Target")){savedTarget=BlockPos.of(tag.getLong("Target"));progress=Math.max(0,tag.getInt("Progress"));}
        if(tag.contains("Beam"))beamLength=Math.max(0,tag.getInt("Beam"));
    }
    /** Clients only need the beam length; the direction comes from the block state. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();tag.putInt("Beam",beamLength);return tag;}
    @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
