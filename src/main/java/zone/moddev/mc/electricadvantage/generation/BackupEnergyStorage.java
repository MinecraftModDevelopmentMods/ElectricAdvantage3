package zone.moddev.mc.electricadvantage.generation;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Marks a backup store so electric routing can charge it last and prevent store-to-store cycling. */
public interface BackupEnergyStorage extends IEnergyStorage {}
