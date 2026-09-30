package com.nucleon.porttasks;

import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.routing.LoopStatus;
import com.nucleon.porttasks.routing.RoutePlanner;
import com.nucleon.porttasks.routing.RoutingService;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * What the overlays show about the held tasks at the current port: the text above the player, the crates to
 * mark in the cargo hold, the helm label, the ledger labels and the next-stop panel's lines. Worked out by
 * {@link #build} when something it depends on changes (task progress, the plan, the inventory, the port the
 * player is at, a setting), so the overlays only draw it. Immutable.
 */
final class PortView
{
	static final Color REMINDER = new Color(255, 200, 0);
	static final Color WARNING = new Color(255, 60, 60);

	/** How a crate in the cargo hold is marked: tinted with a label, or dimmed. */
	static final class CrateMark
	{
		/** "TAKE" or "NEXT"; null to dim the crate. */
		final String label;

		CrateMark(String label)
		{
			this.label = label;
		}
	}

	/** One label on a dock ledger table. */
	static final class LedgerLabel
	{
		final String text;
		final Color colour;

		LedgerLabel(String text, Color colour)
		{
			this.text = text;
			this.colour = colour;
		}
	}

	/** One line of the next-stop panel. */
	static final class Line
	{
		final String text;
		final Color colour;

		Line(String text, Color colour)
		{
			this.text = text;
			this.colour = colour;
		}
	}

	static final PortView EMPTY = new PortView(null, null, null, Collections.emptyMap(), Color.GREEN, 0, null,
		Collections.emptyMap(), Collections.emptyList());

	/** The port the player is at (docked, or on foot by the boat), or null. */
	final PortLocation dockedPort;
	/** Text above the player (a warning, else the cargo reminder), or null. */
	final String overheadText;
	final Color overheadColour;
	/** Cargo hold: item id -> how to mark it; crates not in the map are left alone. */
	final Map<Integer, CrateMark> crateMarks;
	/** Tint for crates marked TAKE / NEXT. */
	final Color takeColour;
	/** Crates still to pick up across part-loaded tasks (0: no helm label), and the outline colour. */
	final int helmMissing;
	final Color helmColour;
	/** Ledger object id -> its labels, one per task using that ledger. */
	final Map<Integer, List<LedgerLabel>> ledgerLabels;
	/** Next-stop panel lines (without the title); empty if there is no plan. */
	final List<Line> routeLines;

	private PortView(PortLocation dockedPort, String overheadText, Color overheadColour, Map<Integer, CrateMark> crateMarks,
		Color takeColour, int helmMissing, Color helmColour, Map<Integer, List<LedgerLabel>> ledgerLabels, List<Line> routeLines)
	{
		this.dockedPort = dockedPort;
		this.overheadText = overheadText;
		this.overheadColour = overheadColour;
		this.crateMarks = crateMarks;
		this.takeColour = takeColour;
		this.helmMissing = helmMissing;
		this.helmColour = helmColour;
		this.ledgerLabels = ledgerLabels;
		this.routeLines = routeLines;
	}

	/**
	 * @param warning   a wrong-port or wrong-crate warning to show above the player, or null
	 * @param cargoName item id -> short cargo name ("lead" for "Crate of lead")
	 */
	static PortView build(List<CourierTask> tasks, RoutingService routing, PortLocation dockedPort, PortTasksConfig config,
		String warning, IntFunction<String> cargoName, LoopStatus loop, int freeSlots)
	{
		String cargo = config.routingCargoReminder() ? reminder(tasks, dockedPort, cargoName) : null;
		List<Line> lines = new ArrayList<>();
		if (config.routingNextStopPanel())
		{
			lines.addAll(routeLines(tasks, routing, dockedPort, cargoName));
			lines.addAll(loopLines(loop));
		}
		return new PortView(dockedPort,
			warning != null ? warning : cargo != null ? cargo : boardReminder(loop, dockedPort, freeSlots),
			warning != null ? WARNING : REMINDER,
			config.routingHighlightHold() ? crateMarks(tasks, routing, dockedPort) : Collections.emptyMap(),
			config.routingTakeColor(),
			helmMissing(tasks), helmColour(tasks),
			ledgerLabels(tasks),
			Collections.unmodifiableList(lines));
	}

	/**
	 * Loop mode: docked at a loop port with a free task slot, where the board hasn't been seen since the reset
	 * or still offers worthwhile in-loop tasks: a nudge to look. Null otherwise.
	 */
	static String boardReminder(LoopStatus loop, PortLocation docked, int freeSlots)
	{
		LoopStatus.Board b = docked == null || freeSlots <= 0 ? null : loop.board(docked);
		if (b == null)
		{
			return null;
		}
		if (!b.seen)
		{
			return "Check the notice board";
		}
		return b.worthwhile > 0 ? "Notice board: " + b.worthwhile + " loop task" + (b.worthwhile == 1 ? "" : "s") : null;
	}

	/** Loop mode's lines for the next-stop panel: the phase, and while gathering, the boards still to see. */
	private static List<Line> loopLines(LoopStatus loop)
	{
		List<Line> lines = new ArrayList<>();
		switch (loop.phase)
		{
			case GATHER:
				int seen = 0;
				for (LoopStatus.Board b : loop.boards)
				{
					seen += b.seen ? 1 : 0;
				}
				lines.add(new Line("Loop: gather, " + seen + "/" + loop.boards.size() + " boards seen", REMINDER));
				for (LoopStatus.Board b : loop.boards)
				{
					if (!b.seen)
					{
						lines.add(new Line("  Look at " + b.port.getName(), Color.LIGHT_GRAY));
					}
				}
				break;
			case SAIL:
				int left = 0;
				for (LoopStatus.Board b : loop.boards)
				{
					left += b.worthwhile;
				}
				lines.add(new Line("Loop: " + left + " task" + (left == 1 ? "" : "s") + " left on its boards", Color.WHITE));
				break;
			case DRY:
				lines.add(new Line("Loop dry: " + loop.tasksToReset + " tasks to board reset", WARNING));
				break;
			default:
				break;
		}
		return lines;
	}

	/**
	 * "Grab 2 more crates of lead / ..." while docked where crates are still to be picked up; null if none.
	 * Also the reason the plugin blocks setting sail from this port.
	 */
	static String reminder(List<CourierTask> tasks, PortLocation port, IntFunction<String> cargoName)
	{
		if (port == null)
		{
			return null;
		}
		String text = null;
		for (CourierTask t : tasks)
		{
			CourierTaskData d = t.getData();
			int left = d.cargoAmount - t.getCargoTaken();
			if (d.getCargoLocation() != port || left <= 0)
			{
				continue;
			}
			String line = "Grab " + left + " more " + (left == 1 ? "crate" : "crates") + " of " + cargoName.apply(d.cargo);
			text = text == null ? line : text + " / " + line;
		}
		return text;
	}

	/**
	 * Docked: crates for this port are "TAKE". At sea: crates for the next stop are "NEXT", but only if it has
	 * deliveries (collecting cargo needs empty hands, so nothing is suggested before a pickup-only stop).
	 * Other courier crates are dimmed.
	 */
	private static Map<Integer, CrateMark> crateMarks(List<CourierTask> tasks, RoutingService routing, PortLocation docked)
	{
		PortLocation port = docked;
		String label = "TAKE";
		if (port == null)
		{
			port = routing.nextStop();
			label = "NEXT";
			if (port == null || !routing.hasDeliveriesAt(port))
			{
				return Collections.emptyMap();
			}
		}
		CrateMark take = new CrateMark(label);
		CrateMark dim = new CrateMark(null);
		Map<Integer, CrateMark> marks = new HashMap<>();
		for (CourierTask t : tasks)
		{
			CourierTaskData d = t.getData();
			if (t.getDelivered() >= d.cargoAmount)
			{
				continue;
			}
			if (d.getDeliveryLocation() == port)
			{
				marks.put(d.cargo, take);
			}
			else
			{
				marks.putIfAbsent(d.cargo, dim);
			}
		}
		return Collections.unmodifiableMap(marks);
	}

	private static int helmMissing(List<CourierTask> tasks)
	{
		int missing = 0;
		for (CourierTask t : tasks)
		{
			if (partLoaded(t))
			{
				missing += t.getData().cargoAmount - t.getCargoTaken();
			}
		}
		return missing;
	}

	private static Color helmColour(List<CourierTask> tasks)
	{
		for (CourierTask t : tasks)
		{
			if (partLoaded(t))
			{
				return t.getOverlayColor();
			}
		}
		return null;
	}

	private static boolean partLoaded(CourierTask t)
	{
		return t.getCargoTaken() > 0 && t.getCargoTaken() < t.getData().cargoAmount;
	}

	/** Each task's pickup ledger shows "Cargo: taken/needed" and its delivery ledger "Delivered: n/needed". */
	private static Map<Integer, List<LedgerLabel>> ledgerLabels(List<CourierTask> tasks)
	{
		Map<Integer, List<LedgerLabel>> labels = new HashMap<>();
		for (CourierTask t : tasks)
		{
			CourierTaskData d = t.getData();
			if (t.getCargoTaken() < d.cargoAmount)
			{
				labels.computeIfAbsent(d.getCargoLocation().getLedgerObject(), k -> new ArrayList<>())
					.add(new LedgerLabel(String.format("Cargo: %d/%d", t.getCargoTaken(), d.cargoAmount), t.getOverlayColor()));
			}
			if (t.getDelivered() < d.cargoAmount)
			{
				labels.computeIfAbsent(d.getDeliveryLocation().getLedgerObject(), k -> new ArrayList<>())
					.add(new LedgerLabel(String.format("Delivered: %d/%d", t.getDelivered(), d.cargoAmount), t.getOverlayColor()));
			}
		}
		return Collections.unmodifiableMap(labels);
	}

	/** The next stop and what to do there, then the one after; work left at this port comes first as "Here". */
	private static List<Line> routeLines(List<CourierTask> tasks, RoutingService routing, PortLocation docked,
		IntFunction<String> cargoName)
	{
		RoutePlanner.Plan plan = routing.plan();
		if (plan == null || plan.stops.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Line> lines = new ArrayList<>();
		boolean startsHere = plan.stops.get(0).port == docked;
		String[] labels = startsHere ? new String[]{"Here", "Next", "Then"} : new String[]{"Next", "Then"};
		int shown = 0;
		while (shown < labels.length && shown < plan.stops.size())
		{
			RoutePlanner.Stop stop = plan.stops.get(shown);
			lines.add(new Line(labels[shown] + ": " + stop.port.getName(), shown == 0 ? Color.YELLOW : Color.WHITE));
			for (int id : stop.pickups)
			{
				CourierTask t = task(tasks, id);
				if (t != null)
				{
					lines.add(new Line("  Pick up " + (t.getData().cargoAmount - t.getCargoTaken()) + " " + cargoName.apply(t.getData().cargo),
						Color.LIGHT_GRAY));
				}
			}
			for (int id : stop.deliveries)
			{
				CourierTask t = task(tasks, id);
				if (t != null)
				{
					lines.add(new Line("  Deliver " + (t.getData().cargoAmount - t.getDelivered()) + " " + cargoName.apply(t.getData().cargo),
						Color.LIGHT_GRAY));
				}
			}
			shown++;
		}
		int remaining = plan.stops.size() - shown;
		if (remaining > 0)
		{
			lines.add(new Line("+" + remaining + " more stop" + (remaining == 1 ? "" : "s"), Color.GRAY));
		}
		return Collections.unmodifiableList(lines);
	}

	private static CourierTask task(List<CourierTask> tasks, int taskId)
	{
		for (CourierTask t : tasks)
		{
			if (t.getData().getId() == taskId)
			{
				return t;
			}
		}
		return null;
	}
}
