package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;

/**
 * Where the player's courier boat is (SPEC-routing.md §1, p₀). SAILING_LAST_PERSONAL_BOAT_BOARDED names the
 * boat slot (1–5); SAILING_BOAT_n_PORT holds that boat's dock id, which the SailingDock table maps to a dock
 * row, and Port Tasks' PortLocation is keyed by those rows. Other values (e.g. mooring points) resolve to
 * null. Client thread only.
 */
@Slf4j
public final class BoatLocator
{
	private static final int[] BOAT_PORT_VARBITS = {
		VarbitID.SAILING_BOAT_1_PORT, VarbitID.SAILING_BOAT_2_PORT, VarbitID.SAILING_BOAT_3_PORT,
		VarbitID.SAILING_BOAT_4_PORT, VarbitID.SAILING_BOAT_5_PORT,
	};

	private final Client client;
	/** Dock id (as stored in the boat varbits) -> SailingDock row. */
	private final Map<Integer, Integer> dockRowById = new HashMap<>();

	public BoatLocator(Client client)
	{
		this.client = client;
	}

	/** Reads the SailingDock table; call once the cache is available (as CourierTaskData.loadFromCache). */
	public void load()
	{
		dockRowById.clear();
		for (int row : client.getDBTableRows(DBTableID.SailingDock.ID))
		{
			Object[] id = client.getDBTableField(row, DBTableID.SailingDock.COL_DOCK_ID, 0);
			if (id != null && id.length > 0 && id[0] instanceof Integer)
			{
				dockRowById.put((Integer) id[0], row);
			}
		}
		log.debug("sailing docks: {}", dockRowById.size());
	}

	/** The port a dock id stands for, or null (unknown id, mooring point, or no dock). */
	public PortLocation portForDockId(int dockId)
	{
		Integer row = dockRowById.get(dockId);
		if (row == null)
		{
			return null;
		}
		PortLocation p = PortLocation.fromDbRow(row);
		return p == PortLocation.EMPTY ? null : p;
	}

	/** Boat slot (1–5) of the personal boat last boarded, or 0 if none. */
	public int currentBoatSlot()
	{
		return client.getVarbitValue(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED);
	}

	/** Port where the current boat is docked, or null if it isn't at a known port. */
	public PortLocation boatPort()
	{
		int slot = currentBoatSlot();
		if (slot < 1 || slot > BOAT_PORT_VARBITS.length)
		{
			return null;
		}
		return portForDockId(client.getVarbitValue(BOAT_PORT_VARBITS[slot - 1]));
	}
}
