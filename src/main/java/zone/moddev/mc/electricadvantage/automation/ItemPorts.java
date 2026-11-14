package zone.moddev.mc.electricadvantage.automation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import zone.moddev.mc.electricadvantage.ElectricAdvantage;
import zone.moddev.mc.lib.api.automation.AddonCapabilities;
import zone.moddev.mc.lib.api.automation.AddonSpec;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;

/**
 * Item port addons for MMDLib machines, the item counterpart of Power Advantage's fluid pumps. Installed in an item
 * group's addon slots (the side configuration drawer), an intake port pulls items into the group from neighbouring
 * inventories and an output port pushes them out, through the faces that group is enabled on. They work in any MMDLib
 * machine, including other mods'.
 *
 * <p>They move {@value #BATCH} items every {@value #INTERVAL} tick and need no power, matching the neighbour export
 * that 3.0's oven and refinery did on their own.
 */
public final class ItemPorts {
    /** Items moved per transfer. */
    public static final int BATCH = 16;
    /** Ticks between transfers. */
    public static final int INTERVAL = 1;
    public static final ResourceLocation INTAKE_ID = ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, "item_intake_port");
    public static final ResourceLocation OUTPUT_ID = ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, "item_output_port");
    /** Pulls items from neighbours into the group. */
    public static final AddonSpec INTAKE = new AddonSpec(INTAKE_ID, ResourceType.ITEMS, IoMode.INPUT, BATCH, INTERVAL, 0, false);
    /** Pushes items from the group into neighbours. */
    public static final AddonSpec OUTPUT = new AddonSpec(OUTPUT_ID, ResourceType.ITEMS, IoMode.OUTPUT, BATCH, INTERVAL, 0, false);

    private ItemPorts() {}

    /**
     * Makes {@code item} an MMDLib inventory addon if it is one of the two ports.
     *
     * @param event NeoForge capability registration event
     * @param id the item's registry ID
     * @param item the item
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event, ResourceLocation id, Item item) {
        if (id.equals(INTAKE_ID)) event.registerItem(AddonCapabilities.ADDON, (stack, context) -> addon -> INTAKE, item);
        if (id.equals(OUTPUT_ID)) event.registerItem(AddonCapabilities.ADDON, (stack, context) -> addon -> OUTPUT, item);
    }
}
