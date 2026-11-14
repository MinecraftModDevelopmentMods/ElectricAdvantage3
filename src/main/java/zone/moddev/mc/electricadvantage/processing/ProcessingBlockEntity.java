package zone.moddev.mc.electricadvantage.processing;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.lib.machine.neoforge.BaseMachine;
import zone.moddev.mc.lib.machine.neoforge.MachineInventoryLifecycle;

/**
 * Electric Arc Furnace and Electric Rock Crusher: several parallel input lanes, each smelting or crushing one item at
 * a time into shared output slots, which are pushed into neighbours.
 *
 * <p>Built on MMDLib's {@link BaseMachine}, which owns storage persistence, client sync and dropping contents. These
 * stay Java because MMDLib XML machines have one processor each, and these machines have one per lane.
 *
 * <p>Save layout: items under {@code Items} as in 3.0; lane state ({@code Progress}, {@code Recipe<n>},
 * {@code Work<n>}) top-level. The energy buffer is saved under MMDLib's {@code energy} key; 3.0's {@code Energy} is
 * still read.
 */
public final class ProcessingBlockEntity extends BaseMachine {
    public final ProcessingSettings settings;
    public final EnergyBuffer energy;
    public final ItemStorage items;
    private final int[] progress;
    private final ResourceLocation[] recipeIds;
    private final ItemStack[] workInputs;
    private final IEnergyStorage energyInput;
    private final MachineInventoryLifecycle inventory;

    private record Work(ResourceLocation id, ItemStack result) {}

    public ProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ProcessingSettings settings) {
        super(type, pos, state);
        this.settings = settings;
        energy = new EnergyBuffer(settings.capacity(), settings.rate()).onChange(this::setChanged);
        energyInput = energy.view(IoMode.INPUT);
        items = new ItemStorage(settings.inputs() + settings.outputs()).onChange(this::setChanged);
        for (int slot = 0; slot < settings.inputs(); slot++)
            items.role(Role.INPUT, slot).filter(stack -> findWork(stack) != null, slot);
        for (int slot = settings.inputs(); slot < items.getSlots(); slot++) items.role(Role.OUTPUT, slot);
        progress = new int[settings.inputs()];
        recipeIds = new ResourceLocation[settings.inputs()];
        workInputs = new ItemStack[settings.inputs()];
        java.util.Arrays.fill(workInputs, ItemStack.EMPTY);
        // No automation groups: EA3 registers this machine's capabilities itself.
        inventory = genericInventory(new AutomationInventory(this, List.of())).items("Items", items);
    }

    @Override protected EnergyBuffer powerStorage() { return energy; }
    @Override protected MachineInventoryLifecycle machineInventory() { return inventory; }
    /** No tanks, so nothing but items (dropped by MMDLib) is displaced when the machine breaks. */
    @Override protected void recoverResources(Level level, BlockPos pos, CompoundTag payload) {}

    public IEnergyStorage energyCapability() { return energyInput; }
    public net.neoforged.neoforge.items.IItemHandler itemCapability() { return items.view(IoMode.BOTH); }

    private Work findWork(ItemStack stack) {
        if (level == null || stack.isEmpty()) return null;
        var query = new SingleRecipeInput(stack);
        if (settings.kind().equals("crushing")) {
            var recipe = level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(), query, level);
            return recipe.map(r -> new Work(r.id(), r.value().assemble(query, level.registryAccess()))).orElse(null);
        }
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, query, level);
        if (recipe.isEmpty()) return null;
        var result = recipe.get().value().assemble(query, level.registryAccess());
        return result.isEmpty() ? null : new Work(recipe.get().id(), result);
    }

    private boolean fits(ItemStack result) {
        var remaining = result.copy();
        for (int slot = settings.inputs(); slot < items.getSlots() && !remaining.isEmpty(); slot++)
            remaining = items.insertResult(slot, remaining, true);
        return remaining.isEmpty();
    }

    private void emit(ItemStack result) {
        var remaining = result.copy();
        for (int slot = settings.inputs(); slot < items.getSlots() && !remaining.isEmpty(); slot++)
            remaining = items.insertResult(slot, remaining, false);
        if (!remaining.isEmpty()) throw new IllegalStateException("Processing output reservation changed during a server tick");
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ProcessingBlockEntity be) {
        be.serverTick();
    }

    @Override
    protected void afterMachineTick() {
        boolean active = processItems(level.hasNeighborSignal(worldPosition));
        var updated = getBlockState().setValue(ProcessingBlock.ACTIVE, active)
            .setValue(ProcessingBlock.POWERED, energy.stored() >= settings.cost());
        if (updated != getBlockState()) level.setBlock(worldPosition, updated, 3);
        int itemsLeft = settings.itemExport();
        for (int n = 0; n < 6 && itemsLeft > 0; n++) {
            Direction side = Direction.values()[(int) ((level.getGameTime() + n) % 6)];
            if (!level.hasChunkAt(worldPosition.relative(side))) continue;
            itemsLeft -= Transfer.pushItems(level, worldPosition, side, items.view(IoMode.OUTPUT), itemsLeft);
        }
    }

    private boolean processItems(boolean disabled) {
        boolean active = false;
        for (int slot = 0; slot < settings.inputs(); slot++) {
            var stack = items.getStackInSlot(slot);
            var work = findWork(stack);
            if (work == null || work.result().isEmpty() || !fits(work.result())) { reset(slot); continue; }
            if (!work.id().equals(recipeIds[slot]) || !ItemStack.isSameItemSameComponents(stack, workInputs[slot])) {
                reset(slot);
                recipeIds[slot] = work.id();
                workInputs[slot] = stack.copyWithCount(1);
                setChanged();
            }
            if (disabled || !energy.consume(settings.cost())) {
                int previous = progress[slot];
                progress[slot] = Math.max(0, previous - 1);
                if (previous != progress[slot]) setChanged();
                continue;
            }
            active = true;
            if (++progress[slot] >= settings.ticks()) {
                emit(work.result());
                var remaining = stack.copy();
                remaining.shrink(1);
                items.setStackInSlot(slot, remaining);
                reset(slot);
            }
            setChanged();
        }
        return active;
    }

    private void reset(int slot) {
        if (progress[slot] != 0 || recipeIds[slot] != null) setChanged();
        progress[slot] = 0;
        recipeIds[slot] = null;
        workInputs[slot] = ItemStack.EMPTY;
    }

    public long screenStoredEnergy() { return energy.stored(); }
    public long screenCapacity() { return energy.capacity(); }
    public int screenDuration() { return settings.ticks(); }
    public int screenProgress0() { return progress.length > 0 ? progress[0] : 0; }
    public int screenProgress1() { return progress.length > 1 ? progress[1] : 0; }
    public int screenProgress2() { return progress.length > 2 ? progress[2] : 0; }
    public int screenProgress3() { return progress.length > 3 ? progress[3] : 0; }
    @Override public ItemStorage screenItems() { return items; }

    @Override
    protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("Progress", progress);
        for (int slot = 0; slot < progress.length; slot++)
            if (recipeIds[slot] != null) {
                tag.putString("Recipe" + slot, recipeIds[slot].toString());
                tag.put("Work" + slot, workInputs[slot].save(registries));
            }
    }

    @Override
    protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        // 3.0 saved the buffer as "Energy"; MMDLib's lifecycle has already read "energy" if present.
        if (!tag.contains("energy") && tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        var saved = tag.getIntArray("Progress");
        for (int slot = 0; slot < progress.length; slot++) {
            progress[slot] = slot < saved.length ? Math.clamp(saved[slot], 0, settings.ticks()) : 0;
            // An idle lane saves no recipe; tryParse("") would give "minecraft:", not null.
            recipeIds[slot] = tag.contains("Recipe" + slot) ? ResourceLocation.tryParse(tag.getString("Recipe" + slot)) : null;
            workInputs[slot] = tag.contains("Work" + slot)
                ? ItemStack.parseOptional(registries, tag.getCompound("Work" + slot)) : ItemStack.EMPTY;
        }
    }
}
