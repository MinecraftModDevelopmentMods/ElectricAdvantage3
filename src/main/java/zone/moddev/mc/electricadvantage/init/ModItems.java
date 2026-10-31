package zone.moddev.mc.electricadvantage.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.electricadvantage.infrastructure.Infrastructure;
import zone.moddev.mc.electricadvantage.infrastructure.ElectricBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import zone.moddev.mc.electricadvantage.ElectricAdvantage;
import zone.moddev.mc.electricadvantage.items.BatteryItem;
import zone.moddev.mc.electricadvantage.items.BatteryImplementation;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar;

/** Queues this namespace's data-defined materials and standalone content. */
public final class ModItems {
    private final RuntimeContentRegistrar runtime;
    private final DeferredRegister<BlockEntityType<?>> blockEntities;

    public ModItems(IEventBus modBus) {
        Infrastructure.register();
        zone.moddev.mc.electricadvantage.assembler.AssemblerSystems.register(modBus);
        zone.moddev.mc.electricadvantage.processing.ProcessingSystems.register();
        zone.moddev.mc.electricadvantage.generation.GenerationSystems.register();
        zone.moddev.mc.electricadvantage.farming.FarmingSystems.register(modBus);
        blockEntities = DeferredRegister.create(
                Registries.BLOCK_ENTITY_TYPE, ElectricAdvantage.MOD_ID);
        blockEntities.register(modBus);
        var extensions = MaterialExtensions.GLOBAL;
        extensions.registerItemImplementation(
                ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, "battery"),
                new BatteryImplementation());
        runtime = new RuntimeContentRegistrar(ElectricAdvantage.MOD_ID, extensions, modBus);
        // Definition loading finishes before new-registry events.
        modBus.addListener(this::queueDefinitions);
        modBus.addListener(this::capabilities);
        // Generated pump shafts retain EA2's search-only item visibility.
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) ->
                event.getParentEntries().stream().filter(stack -> stack.getItem() instanceof net.minecraft.world.item.BlockItem item
                        && item.getBlock() instanceof zone.moddev.mc.electricadvantage.infrastructure.PumpPipeBlock)
                        .toList().forEach(stack -> event.remove(stack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_TAB_ONLY)));
    }

    private void queueDefinitions(NewRegistryEvent event) {
        MMDLib.loadedMaterials().forEach((id, plan) -> {
            if (id.getNamespace().equals(ElectricAdvantage.MOD_ID)) runtime.queue(plan);
        });
        MMDLib.standaloneItems().forEach((id, plan) -> {
            if (id.getNamespace().equals(ElectricAdvantage.MOD_ID)) runtime.queue(plan);
        });
        MMDLib.standaloneBlocks().forEach((id, plan) -> {
            if (id.getNamespace().equals(ElectricAdvantage.MOD_ID)) runtime.queue(plan);
        });
        runtime.blockEntityHandles().values().forEach(handle -> handle.register(blockEntities));
    }

    private void capabilities(RegisterCapabilitiesEvent event) {
        runtime.blockEntityHandles().values().forEach(handle -> {
            if (handle.prepared().blockEntityClass() == zone.moddev.mc.electricadvantage.generation.GenerationBlockEntity.class) {
                var binding = handle.binding().as(zone.moddev.mc.electricadvantage.generation.GenerationBlockEntity.class);
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, binding.type().get(), (be, side) -> be.capability());
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, binding.type().get(),
                        (be, side) -> be.settings.kind().equals("steam") ? be.fluidCapability() : null);
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, binding.type().get(), (be, side) -> be.array() ? be.items : null);
            }
            if (handle.prepared().blockEntityClass() == zone.moddev.mc.electricadvantage.processing.ProcessingBlockEntity.class) {
                var binding = handle.binding().as(zone.moddev.mc.electricadvantage.processing.ProcessingBlockEntity.class);
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, binding.type().get(), (be, side) -> be.energyCapability());
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, binding.type().get(), (be, side) -> be.items.getSlots()>0 ? be.itemCapability() : null);
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, binding.type().get(), (be, side) -> be.fluidMachine() ? be.fluidCapability() : null);
            }
            if (handle.prepared().blockEntityClass() == zone.moddev.mc.electricadvantage.assembler.AssemblerBlockEntity.class) {
                var binding = handle.binding().as(zone.moddev.mc.electricadvantage.assembler.AssemblerBlockEntity.class);
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, binding.type().get(), (be, side) -> be.energyCapability());
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, binding.type().get(), (be, side) -> be.itemCapability());
            }
            if (handle.prepared().blockEntityClass() == zone.moddev.mc.electricadvantage.farming.GrowthChamberBlockEntity.class) {
                var binding = handle.binding().as(zone.moddev.mc.electricadvantage.farming.GrowthChamberBlockEntity.class);
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, binding.type().get(), (be, side) -> be.itemCapability());
            }
            if (handle.prepared().blockEntityClass() == zone.moddev.mc.electricadvantage.farming.GreenhouseControllerBlockEntity.class) {
                var binding = handle.binding().as(zone.moddev.mc.electricadvantage.farming.GreenhouseControllerBlockEntity.class);
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, binding.type().get(), (be, side) -> be.energyCapability());
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, binding.type().get(), (be, side) -> be.fluidCapability());
                event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, binding.type().get(), (be, side) -> be.itemCapability());
            }
            if (handle.prepared().blockEntityClass() == ElectricBlockEntity.class) {
                var binding = handle.binding().as(ElectricBlockEntity.class);
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, binding.type().get(), (be, side) -> be.input());
            }
        });
        runtime.items().values().forEach(holder -> {
            if (holder.get() instanceof BatteryItem battery) {
                event.registerItem(Capabilities.EnergyStorage.ITEM,
                        (stack, context) -> battery.storage(stack), battery);
            }
        });
    }
}
