package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.CourierTaskData;
import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.enums.TaskReward;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * Phase 1 checks (SPEC-routing.md §7). Logs, at INFO so they show without debug logging:
 *  - each courier task as the plugin and the wiki see it, to confirm the task-id join and compare XP;
 *  - changes to the varbits that may hold the boat's location, with the nearest port to the player,
 *    so the right varbit and its value encoding can be identified.
 * Temporary; removed once phase 1 is confirmed. Client thread only.
 */
@Slf4j
public final class RoutingDiagnostics
{
	/** Candidate boat-location varbits, by name for the log. */
	public static final Map<Integer, String> BOAT_VARBITS = Map.of(
		VarbitID.SAILING_BOAT_1_PORT, "SAILING_BOAT_1_PORT",
		VarbitID.SAILING_BOAT_2_PORT, "SAILING_BOAT_2_PORT",
		VarbitID.SAILING_BOAT_3_PORT, "SAILING_BOAT_3_PORT",
		VarbitID.SAILING_BOAT_4_PORT, "SAILING_BOAT_4_PORT",
		VarbitID.SAILING_BOAT_5_PORT, "SAILING_BOAT_5_PORT",
		VarbitID.SAILING_BOARDED_BOAT_LAST_DOCK, "SAILING_BOARDED_BOAT_LAST_DOCK",
		VarbitID.SAILING_BOARDED_BOAT_LAST_STANDARD_DOCK, "SAILING_BOARDED_BOAT_LAST_STANDARD_DOCK",
		VarbitID.SAILING_BOARDED_BOAT_LAST_MOORING_POINT, "SAILING_BOARDED_BOAT_LAST_MOORING_POINT",
		VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED, "SAILING_LAST_PERSONAL_BOAT_BOARDED"
	);

	private final Client client;
	private final CourierWikiData wiki;
	private final RewardValuer valuer;
	private final BoatLocator boats;
	private final XpLearner xp;

	public RoutingDiagnostics(Client client, CourierWikiData wiki, RewardValuer valuer, BoatLocator boats, XpLearner xp)
	{
		this.xp = xp;
		this.client = client;
		this.wiki = wiki;
		this.valuer = valuer;
		this.boats = boats;
	}

	public void logTask(int slot, CourierTaskData d)
	{
		CourierWikiData.Task w = wiki.task(d.getId());
		int pluginXp = TaskReward.getIntRewardForTask(d.getDbrow());
		if (w == null)
		{
			log.info("[routing] slot {} task id {} (dbrow {}) '{}': NOT in wiki data; plugin XP {}",
				slot, d.getId(), d.getDbrow(), d.taskName, pluginXp);
			return;
		}
		boolean namesMatch = w.name != null && w.name.equalsIgnoreCase(d.taskName);
		boolean portsMatch = w.cargoPort.equals(d.getCargoLocation().getName())
			&& w.destination.equals(d.getDeliveryLocation().getName());
		Integer baseXp = xp.xp(d.getId());
		String value = baseXp == null ? "?" : String.format("%.0f", valuer.expectedTaskValue(baseXp, w.destination));
		log.info("[routing] slot {} task id {} (dbrow {}): plugin '{}' / wiki '{}' names {} | {} -> {} ports {} | "
				+ "crates plugin {} wiki {} | XP plugin {} wiki {} used {} | bag {} | expected value {} gp",
			slot, d.getId(), d.getDbrow(), d.taskName, w.name, namesMatch ? "match" : "DIFFER",
			w.cargoPort, w.destination, portsMatch ? "match" : "DIFFER (plugin " + d.getCargoLocation() + " -> " + d.getDeliveryLocation() + ")",
			d.cargoAmount, w.crates, pluginXp, w.xp, baseXp, baseXp == null ? "?" : BagSize.forXp(baseXp).wikiName(), value);
	}

	public void logBoatVarbit(int varbitId, int value)
	{
		String name = BOAT_VARBITS.get(varbitId);
		PortLocation near = nearestPort();
		PortLocation asDock = boats.portForDockId(value);
		PortLocation boat = boats.boatPort();
		log.info("[routing] {} = {} (as dock id: {}) | boat slot {} at {} | player nearest port: {}",
			name, value, asDock == null ? "none" : asDock.getName(), boats.currentBoatSlot(),
			boat == null ? "?" : boat.getName(), near == null ? "?" : near.getName());
	}

	/** Every candidate varbit's current value, e.g. at login. */
	public void logAllBoatVarbits()
	{
		for (int id : BOAT_VARBITS.keySet())
		{
			logBoatVarbit(id, client.getVarbitValue(id));
		}
	}

	private PortLocation nearestPort()
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}
		WorldPoint me = client.getLocalPlayer().getWorldLocation();
		PortLocation best = null;
		int bestDist = Integer.MAX_VALUE;
		for (PortLocation p : PortLocation.values())
		{
			if (p == PortLocation.EMPTY)
			{
				continue;
			}
			int d = p.getNavigationLocation().distanceTo2D(me);
			if (d < bestDist)
			{
				bestDist = d;
				best = p;
			}
		}
		return best;
	}
}
