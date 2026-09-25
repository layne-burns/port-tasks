package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.CourierTask;
import com.nucleon.porttasks.CourierTaskData;
import com.nucleon.porttasks.enums.PortLocation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.util.Text;

/**
 * Stops depositing a crate at the wrong port's ledger. When "Deposit" is clicked on a port's ledger table
 * and every courier crate the player holds belongs to tasks bound for other ports, the click is blocked
 * with a chat message naming the right port(s), in the manner of Pickpocket Guardian: the menu itself is
 * never changed, only the player's own mistaken click is not sent. Shift-click always goes through, and
 * anything the check can't be sure about (no courier crate held, a crate for this port, a crate matching
 * no task) is allowed. Cargo holds are never guarded. Client thread only.
 */
public final class DepositGuard
{
	private final Client client;

	public DepositGuard(Client client)
	{
		this.client = client;
	}

	/**
	 * @return the reason to block this click, or null to let it through
	 */
	public String check(String option, int objectId, List<CourierTask> tasks)
	{
		if (option == null || !Text.removeTags(option).toLowerCase(Locale.ROOT).contains("deposit"))
		{
			return null;
		}
		PortLocation port = ledgerPort(objectId);
		if (port == null || client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return null;
		}

		Set<String> elsewhere = new LinkedHashSet<>();
		boolean heldAny = false;
		for (int itemId : heldItemIds())
		{
			boolean matchesTask = false;
			for (CourierTask t : tasks)
			{
				CourierTaskData d = t.getData();
				if (d.cargo != itemId || t.getDelivered() >= d.cargoAmount)
				{
					continue;
				}
				matchesTask = true;
				if (d.getDeliveryLocation() == port)
				{
					return null; // at least one held crate belongs here
				}
				elsewhere.add(d.getDeliveryLocation().getName());
			}
			heldAny |= matchesTask;
		}
		if (!heldAny)
		{
			return null;
		}
		return "Blocked deposit at " + port.getName() + ": the crate you're holding is for "
			+ String.join(" / ", elsewhere) + ". Shift-click to deposit anyway.";
	}

	private static PortLocation ledgerPort(int objectId)
	{
		for (PortLocation p : PortLocation.values())
		{
			if (p != PortLocation.EMPTY && p.getLedgerObject() == objectId)
			{
				return p;
			}
		}
		return null;
	}

	/** Item ids in the inventory and worn equipment (a held crate may count as either). */
	private Set<Integer> heldItemIds()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer c = client.getItemContainer(container);
			if (c == null)
			{
				continue;
			}
			for (Item item : c.getItems())
			{
				if (item.getId() > 0)
				{
					ids.add(item.getId());
				}
			}
		}
		return ids;
	}
}
