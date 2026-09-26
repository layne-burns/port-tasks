package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
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

	// Move mode is 4 while the player is on the boat at a dock, 0-3 at sea (seen in game, 2026-09-25).
	private static final int MOVE_MODE_DOCKED = 4;
	/** How near the boat's dock the player must be, off the boat, to count as at that port. */
	private static final int AT_PORT_RADIUS = 60;

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

	/**
	 * The port the player is at right now: on the boat while it is docked, or off the boat near the dock it
	 * is moored at. Null at sea, or when the player is away from the boat (e.g. teleported to a board).
	 * Unlike {@link #boatPort()}, whose varbit keeps the last dock while sailing, this is null at sea.
	 */
	public PortLocation dockedPort()
	{
		PortLocation port = boatPort();
		if (port == null)
		{
			return null;
		}
		if (client.getVarbitValue(VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT) == 1)
		{
			return client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE) == MOVE_MODE_DOCKED ? port : null;
		}
		Player player = client.getLocalPlayer();
		WorldPoint where = player == null ? null : player.getWorldLocation();
		return where != null && port.getNavigationLocation().distanceTo2D(where) <= AT_PORT_RADIUS ? port : null;
	}

	/**
	 * Port where the current boat was last docked, or null if unknown. The varbit keeps this dock while the
	 * boat is at sea, so it is the start for planning, not a test for being at port (see {@link #dockedPort()}).
	 */
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
