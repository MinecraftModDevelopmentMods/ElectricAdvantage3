package zone.moddev.mc.electricadvantage.turret;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.energy.IEnergyStorage;
import zone.moddev.mc.electricadvantage.Config;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import java.util.*;

/**
 * Locks onto the nearest visible target, charges, then fires a beam that hurts every living entity
 * along it up to the first solid block. The normal turret targets monsters and players outside its
 * owner's team; the hidden "evil" variant targets everything except monsters and ambient mobs.
 */
public final class LaserTurretBlockEntity extends BlockEntity implements ScreenHost {
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> LASER=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse("electricadvantage:laser"));
    public final TurretSettings settings;
    public final EnergyBuffer energy;
    private final IEnergyStorage energyInput;
    private final ItemStorage noItems=new ItemStorage(0);
    private UUID owner;
    private String team;
    private int targetId=Integer.MIN_VALUE;
    private int attack,scanCountdown=-1;
    // Client-side aim, in degrees; yaw 0 faces +X as in EA2.
    public float yaw,pitch,oldYaw,oldPitch;
    public double beamLength;

    public LaserTurretBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,TurretSettings settings){
        super(type,pos,state);this.settings=settings;
        energy=new EnergyBuffer(settings.capacity(),settings.rate()).onChange(this::setChanged);energyInput=energy.view(IoMode.INPUT);
    }
    public IEnergyStorage energyCapability(){return energyInput;}
    public Vec3 optic(){return Vec3.atLowerCornerOf(worldPosition).add(0.5,0.75,0.5);}
    public boolean locked(){return targetId!=Integer.MIN_VALUE;}
    public Entity target(){return level==null||!locked()?null:level.getEntity(targetId);}
    public boolean showBeam(){return locked()&&attack>0&&attack<=settings.blastTime();}
    public UUID owner(){return owner;}
    public String team(){return team;}
    public void setOwner(Player player){
        owner=player.getUUID();var playerTeam=player.getTeam();team=playerTeam==null?null:playerTeam.getName();setChanged();
    }
    public void setTeam(String name){team=name==null||name.isEmpty()?null:name;setChanged();}
    private boolean active(Level level){return !level.hasNeighborSignal(worldPosition)&&energy.stored()>=settings.shotCost();}

    public static void tick(Level world,BlockPos pos,BlockState state,LaserTurretBlockEntity be){
        if(!(world instanceof ServerLevel level))return;
        if(be.attack>0)be.attack--;
        boolean active=be.active(level);
        if(active){
            if(!be.locked()){
                // Staggered by position so neighbouring turrets do not all scan on the same tick.
                if(be.scanCountdown<0)be.scanCountdown=Math.floorMod(pos.hashCode(),be.settings.scanInterval());
                if(be.scanCountdown--==0){be.scanCountdown=be.settings.scanInterval()-1;be.acquire(level);}
            }
            be.validate(level);
            if(be.locked()){
                if(be.attack==be.settings.hitTime())be.fire(level);
                else if(be.attack<=1){be.attack=be.settings.chargeTime();be.sync();}
            }
            be.energy.consume(Math.min(be.energy.stored(),be.settings.idleCost()));
        } else if(be.locked())be.setTarget(null);
        var updated=be.getBlockState().setValue(LaserTurretBlock.ACTIVE,active).setValue(LaserTurretBlock.POWERED,be.energy.stored()>=be.settings.shotCost());
        if(updated!=be.getBlockState())level.setBlock(pos,updated,3);
    }
    /** EA2 team rule: players are enemies only when the turret has a team and they are not on it. */
    public boolean isEnemy(Player player){
        if(player.isCreative()||player.isSpectator())return false;
        if(team==null)return false;
        return player.getTeam()==null||!team.equals(player.getTeam().getName());
    }
    public boolean isTarget(LivingEntity entity){
        if(!entity.isAlive()||entity instanceof ArmorStand)return false;
        if(settings.evil()){
            if(entity instanceof Player player)return !player.isCreative()&&!player.isSpectator();
            return !(entity instanceof Enemy)&&entity.getType().getCategory()!=MobCategory.AMBIENT;
        }
        if(entity instanceof Player player)return isEnemy(player);
        return entity instanceof Enemy;
    }
    private void acquire(ServerLevel level){
        var optic=optic();double range=settings.targetRange();
        level.getEntitiesOfClass(LivingEntity.class,new AABB(optic,optic).inflate(range),e->isTarget(e)&&center(e).distanceToSqr(optic)<=range*range&&canSee(level,e))
                .stream().min(Comparator.comparingDouble(e->center(e).distanceToSqr(optic))).ifPresent(this::setTarget);
    }
    private void validate(ServerLevel level){
        var target=target();
        if(!locked())return;
        if(!(target instanceof LivingEntity living)||!isTarget(living)||center(target).distanceTo(optic())>settings.targetRange()||!canSee(level,target))setTarget(null);
    }
    private static Vec3 center(Entity entity){return entity.position().add(0,entity.getBbHeight()*0.5,0);}
    public boolean canSee(Level level,Entity entity){
        var optic=optic();var to=center(entity);
        // Start just outside the turret head so the turret's own block never blocks the view.
        var from=optic.add(to.subtract(optic).normalize());
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType()!=HitResult.Type.BLOCK;
    }
    private Vec3 terminus(Level level,Vec3 direction){
        var optic=optic();var end=optic.add(direction.scale(settings.attackRange()));
        var hit=level.clip(new ClipContext(optic.add(direction.scale(0.5)),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
        return hit.getType()==HitResult.Type.BLOCK?hit.getLocation():end;
    }
    private Vec3 aim(){var target=target();return target==null?null:center(target).subtract(optic()).normalize();}
    private void fire(ServerLevel level){
        var direction=aim();if(direction==null||!energy.consume(settings.shotCost()))return;
        var optic=optic();var end=terminus(level,direction);
        var sound=BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.tryParse(Config.turretLaserSound())).orElse(SoundEvents.ENDERMAN_TELEPORT);
        level.playSound(null,optic.x,optic.y,optic.z,sound,SoundSource.BLOCKS,1f,1f);
        var damage=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LASER));
        Player ownerPlayer=owner==null?null:level.getPlayerByUUID(owner);
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,new AABB(optic,end).inflate(1),e->e.isAlive()&&e.getBoundingBox().clip(optic,end).isPresent())){
            victim.igniteForSeconds(settings.burnSeconds());
            victim.hurt(damage,settings.damage());
            // EA2 turned victims on the owner; this also names the owner in the death message.
            if(ownerPlayer!=null&&victim!=ownerPlayer)victim.setLastHurtByMob(ownerPlayer);
            var feet=victim.blockPosition();
            if(settings.igniteGround()&&level.isEmptyBlock(feet)&&!level.isEmptyBlock(feet.below())&&BaseFireBlock.canBePlacedAt(level,feet,net.minecraft.core.Direction.UP))
                level.setBlockAndUpdate(feet,BaseFireBlock.getState(level,feet));
        }
        setChanged();sync();
        var target=target();if(target==null||!target.isAlive())setTarget(null);
    }
    public void setTarget(Entity entity){
        targetId=entity==null?Integer.MIN_VALUE:entity.getId();
        attack=entity==null?0:settings.chargeTime();
        setChanged();sync();
    }
    private void sync(){if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}

    /** Client: idle sweep, track the target at 9°/tick, or droop when unpowered (EA2 animation). */
    public static void clientTick(Level level,BlockPos pos,BlockState state,LaserTurretBlockEntity be){
        if(be.attack>0)be.attack--;
        be.oldYaw=be.yaw;be.oldPitch=be.pitch;
        if(!state.getValue(LaserTurretBlock.ACTIVE)){if(be.pitch>-30)be.pitch=Math.max(-30,be.pitch-2);return;}
        var target=be.target();
        if(target==null){
            be.pitch=approach(be.pitch,0,2);be.yaw+=2;
            if(be.yaw>180){be.yaw-=360;be.oldYaw-=360;}
            return;
        }
        var delta=center(target).subtract(be.optic());
        float goalYaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x));
        float goalPitch=(float)Math.toDegrees(Math.asin(delta.y/Math.max(1e-6,delta.length())));
        if(goalYaw-be.yaw>180){be.yaw+=360;be.oldYaw+=360;}else if(goalYaw-be.yaw<-180){be.yaw-=360;be.oldYaw-=360;}
        be.yaw=approach(be.yaw,goalYaw,9);be.pitch=approach(be.pitch,goalPitch,9);
        be.beamLength=be.showBeam()?be.optic().distanceTo(be.terminus(level,delta.normalize())):0;
    }
    private static float approach(float value,float goal,float step){return Math.abs(goal-value)<=step?goal:value+Math.signum(goal-value)*step;}
    public int comparator(){return locked()?15:(int)(8*energy.stored()/Math.max(1,energy.capacity()));}

    @Override public ItemStorage screenItems(){return noItems;}
    public long screenEnergy(){return energy.stored();}
    public long screenCapacity(){return energy.capacity();}
    public Component screenStatus(){
        var target=target();
        if(level!=null&&level.hasNeighborSignal(worldPosition))return Component.translatable("screen.electricadvantage.turret.disabled");
        if(energy.stored()<settings.shotCost())return Component.translatable("screen.electricadvantage.turret.no_power");
        return target==null?Component.translatable("screen.electricadvantage.turret.scanning"):Component.translatable("screen.electricadvantage.turret.target",target.getDisplayName());
    }
    public Component screenTeam(){if(settings.evil())return Component.translatable("screen.electricadvantage.turret.evil");return team==null?Component.translatable("screen.electricadvantage.turret.no_team"):Component.translatable("screen.electricadvantage.turret.team",team);}

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        tag.put("Energy",energy.serializeNBT(registries));
        if(owner!=null)tag.putUUID("Owner",owner);
        if(team!=null)tag.putString("Team",team);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        if(tag.contains("Energy"))energy.deserializeNBT(registries,tag.get("Energy"));
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        team=tag.contains("Team")?tag.getString("Team"):null;
        if(tag.contains("Target")){targetId=tag.getInt("Target");attack=tag.getInt("Attack");}
    }
    /** Clients only need the target and charge timer; aim is derived from the target each tick. */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        var tag=new CompoundTag();tag.putInt("Target",targetId);tag.putInt("Attack",attack);return tag;
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
