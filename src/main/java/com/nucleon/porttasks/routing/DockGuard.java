package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.Collection;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.KeyCode;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

/**
 * Stops docking at a port the plan has no business at. When a docking click is made (an option containing
 * "dock", or a click on a port's gangplank) while holding a courier crate in hand (the caller checks), the port
 * is identified by the port-specific gangplank loaded nearest the boat (gangplanks load with the scene as a
 * port comes near; falling back to the nearest water-side navigation point if none has loaded), and the click
 * is blocked if that port has no pickup or delivery anywhere in the plan. As with {@link DepositGuard}, the menu is never
 * changed; shift-click always goes through. Client thread only.
 */
@Slf4j
public final class DockGuard
{
	/** How far from a port's navigation point the boat may be for a dock click to count as that port. */
	private static final int PORT_RADIUS = 80;
	/** How far from a loaded gangplank the boat may be for it to identify the port. */
	private static final int GANGPLANK_RADIUS = 60;

	private final Client client;

	public DockGuard(Client client)
	{
		this.client = client;
	}

	/** Whether this click looks like docking; logged while on a boat, to confirm the real option name. */
	public boolean isDockClick(String option, String target, int objectId)
	{
		String o = option == null ? "" : Text.removeTags(option).toLowerCase(Locale.ROOT);
		// Docking is done from the boat. From the dock, clicking the gangplank is boarding ("Board-previous"),
		// which must never be blocked.
		if (!onBoat() || o.contains("board"))
		{
			return false;
		}
		log.info("[routing] boat click: '{}' on '{}' (id {})", Text.removeTags(option), Text.removeTags(target), objectId);
		if (o.contains("dock"))
		{
			return true;
		}
		for (PortLocation p : PortLocation.values())
		{
			if (p != PortLocation.EMPTY && p.getGangplankObject() == objectId)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the port being docked at if the click should be blocked, else null
	 */
	public PortLocation wrongPort(RoutePlanner.Plan plan, Collection<GameObject> gangplanks)
	{
		if (plan == null || plan.stops.isEmpty() || client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return null;
		}
		PortLocation port = portByGangplank(gangplanks);
		if (port == null)
		{
			port = nearestPort();
		}
		if (port == null)
		{
			return null;
		}
		for (RoutePlanner.Stop s : plan.stops)
		{
			if (s.port == port)
			{
				return null;
			}
		}
		return port;
	}

	/** The port whose gangplank (port-specific ones only) is loaded nearest the boat, or null. */
	private PortLocation portByGangplank(Collection<GameObject> gangplanks)
	{
		WorldPoint here = position();
		if (here == null || gangplanks == null)
		{
			return null;
		}
		PortLocation best = null;
		int bestDist = GANGPLANK_RADIUS + 1;
		for (GameObject g : gangplanks)
		{
			PortLocation p = portForGangplank(g.getId());
			if (p == null)
			{
				continue;
			}
			int d = g.getWorldLocation().distanceTo2D(here);
			if (d < bestDist)
			{
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	private static PortLocation portForGangplank(int objectId)
	{
		for (PortLocation p : PortLocation.values())
		{
			if (p != PortLocation.EMPTY && p.getGangplankObject() == objectId)
			{
				return p;
			}
		}
		return null;
	}

	/** The port nearest the boat (or the player, off the boat), within PORT_RADIUS tiles; else null. */
	public PortLocation nearestPort()
	{
		WorldPoint here = position();
		if (here == null)
		{
			return null;
		}
		PortLocation best = null;
		int bestDist = PORT_RADIUS + 1;
		for (PortLocation p : PortLocation.values())
		{
			if (p == PortLocation.EMPTY)
			{
				continue;
			}
			int d = p.getNavigationLocation().distanceTo2D(here);
			if (d < bestDist)
			{
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	private boolean onBoat()
	{
		Player player = client.getLocalPlayer();
		WorldView wv = player == null ? null : player.getWorldView();
		return wv != null && !wv.isTopLevel();
	}

	/** World position of the boat the player is on (boats have their own coordinates), or the player's. */
	private WorldPoint position()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		WorldView wv = player.getWorldView();
		if (wv != null && !wv.isTopLevel() && wv.getId() != WorldView.TOPLEVEL)
		{
			WorldEntity boat = client.getTopLevelWorldView().worldEntities().byIndex(wv.getId());
			return boat == null ? null : WorldPoint.fromLocalInstance(client, boat.getLocalLocation());
		}
		return player.getWorldLocation();
	}
}
