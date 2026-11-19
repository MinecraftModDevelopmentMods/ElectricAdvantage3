package zone.moddev.mc.electricadvantage.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.electricadvantage.machine.ElectricMachineEntity;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * The growth chamber: three lanes, each a soil and a seed that are never consumed, sharing six outputs and one
 * upgrade. {@link GrowthChamberProcessor} grows the lanes; this holds their state and the greenhouse power pool that a
 * touching Control Unit fills, saved under 3.0's keys ({@code Greenhouse}, {@code Progress}, {@code Recipe<n>},
 * {@code Seed<n>}, {@code Soil<n>}, {@code Pending<n>}) so 3.0 and 3.1 chambers keep their growth.
 */
public final class GrowthChamberEntity extends ElectricMachineEntity {
    public static final int LANES = 3, CAPACITY = 200;
    private long greenhouse;
    final int[] progress = new int[LANES];
    final ResourceLocation[] recipeIds = new ResourceLocation[LANES];
    final ItemStack[] workSeeds = new ItemStack[LANES], workSoils = new ItemStack[LANES];
    // A finished harvest is rolled once and held until all of it fits, so nothing is spilled or re-rolled.
    final List<List<ItemStack>> pending = new ArrayList<>();

    public GrowthChamberEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineDefinition definition,
            MachineRecipeType recipes) {
        super(type, pos, state, definition, recipes);
        Arrays.fill(workSeeds, ItemStack.EMPTY);
        Arrays.fill(workSoils, ItemStack.EMPTY);
        for (int lane = 0; lane < LANES; lane++) pending.add(new ArrayList<>());
    }

    public ItemStack soil(int lane) { return items.inventory("soil").getStackInSlot(lane); }
    public ItemStack seed(int lane) { return items.inventory("seed").getStackInSlot(lane); }
    public ItemStack upgrade() { return items.inventory("upgrade").getStackInSlot(0); }

    public long greenhouse() { return greenhouse; }
    public long greenhouseSpace() { return Math.max(0, CAPACITY - greenhouse); }
    /** Called by a Control Unit in this chamber's cluster; returns the amount accepted. */
    public long acceptGreenhouse(long amount) {
        long accepted = Math.min(Math.max(0, amount), greenhouseSpace());
        if (accepted > 0) { greenhouse += accepted; setChanged(); }
        return accepted;
    }
    public void setGreenhouse(long amount) { greenhouse = Math.clamp(amount, 0, CAPACITY); setChanged(); }
    boolean useGreenhouse() {
        if (greenhouse < 1) return false;
        greenhouse--;
        setChanged();
        return true;
    }

    public int progress(int lane) { return progress[lane]; }
    public List<ItemStack> pendingHarvest(int lane) { return pending.get(lane).stream().map(ItemStack::copy).toList(); }

    Optional<RecipeHolder<GrowingRecipe>> recipe(int lane) {
        var seed = seed(lane);
        var soil = soil(lane);
        if (level == null || seed.isEmpty() || soil.isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(FarmingSystems.TYPE.get(), new GrowingRecipe.Input(seed, soil), level);
    }

    /** MMDLib drops the slots; held harvests are dropped here. */
    @Override
    public void dropContents(Level level, BlockPos pos) {
        super.dropContents(level, pos);
        for (var lane : pending) for (var stack : lane) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
        pending.forEach(List::clear);
    }

    private static int time(DefinedMachineEntity machine, int lane) {
        return machine instanceof GrowthChamberEntity chamber ? chamber.recipe(lane).map(r -> r.value().time()).orElse(1) : 1;
    }
    private static int progress(DefinedMachineEntity machine, int lane) {
        return machine instanceof GrowthChamberEntity chamber ? chamber.progress[lane] : 0;
    }
    public static int screenProgress0(DefinedMachineEntity machine) { return progress(machine, 0); }
    public static int screenProgress1(DefinedMachineEntity machine) { return progress(machine, 1); }
    public static int screenProgress2(DefinedMachineEntity machine) { return progress(machine, 2); }
    public static int screenTime0(DefinedMachineEntity machine) { return time(machine, 0); }
    public static int screenTime1(DefinedMachineEntity machine) { return time(machine, 1); }
    public static int screenTime2(DefinedMachineEntity machine) { return time(machine, 2); }
    public static long screenGreenhouse(DefinedMachineEntity machine) {
        return machine instanceof GrowthChamberEntity chamber ? chamber.greenhouse : 0;
    }
    public static long screenGreenhouseCapacity(DefinedMachineEntity machine) { return CAPACITY; }

    @Override
    protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveMachineData(tag, registries);
        tag.putLong("Greenhouse", greenhouse);
        tag.putIntArray("Progress", progress);
        for (int lane = 0; lane < LANES; lane++) {
            if (recipeIds[lane] != null) {
                tag.putString("Recipe" + lane, recipeIds[lane].toString());
                tag.put("Seed" + lane, workSeeds[lane].save(registries));
                tag.put("Soil" + lane, workSoils[lane].save(registries));
            }
            if (!pending.get(lane).isEmpty()) {
                var list = new ListTag();
                for (var stack : pending.get(lane)) list.add(stack.save(registries));
                tag.put("Pending" + lane, list);
            }
        }
    }

    @Override
    protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadMachineData(tag, registries);
        greenhouse = Math.clamp(tag.getLong("Greenhouse"), 0, CAPACITY);
        var saved = tag.getIntArray("Progress");
        for (int lane = 0; lane < LANES; lane++) {
            progress[lane] = lane < saved.length ? Math.clamp(saved[lane], 0, 1000000) : 0;
            // An idle lane saves no recipe; tryParse("") would give "minecraft:", not null.
            recipeIds[lane] = tag.contains("Recipe" + lane) ? ResourceLocation.tryParse(tag.getString("Recipe" + lane)) : null;
            workSeeds[lane] = tag.contains("Seed" + lane) ? ItemStack.parseOptional(registries, tag.getCompound("Seed" + lane)) : ItemStack.EMPTY;
            workSoils[lane] = tag.contains("Soil" + lane) ? ItemStack.parseOptional(registries, tag.getCompound("Soil" + lane)) : ItemStack.EMPTY;
            pending.get(lane).clear();
            for (var entry : tag.getList("Pending" + lane, Tag.TAG_COMPOUND)) {
                var stack = ItemStack.parseOptional(registries, (CompoundTag) entry);
                if (!stack.isEmpty()) pending.get(lane).add(stack);
            }
        }
    }
}
