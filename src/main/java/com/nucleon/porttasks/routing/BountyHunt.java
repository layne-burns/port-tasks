package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.ToDoubleFunction;
import net.runelite.api.coords.WorldPoint;

/**
 * The bounty hunt (SPEC-routing.md §2.5): for the monsters the player picked, where each body part's task can
 * be had, and which board to go and look at next. Worked out when boards, tasks or settings change; the panel
 * and Shortest Path only read it. Immutable.
 *
 * A board's state for a part's task: OFFERED if it was on the board when last opened this reset cycle, GONE
 * if the board was opened this cycle without it, ALWAYS if the board hasn't been opened but always offers it
 * (the wiki's guaranteed bounty), else UNCHECKED. Boards reroll only at a reset (§2.4.2), so GONE stays true
 * until then.
 */
public final class BountyHunt
{
	public enum State
	{
		OFFERED, ALWAYS, UNCHECKED, GONE
	}

	/** One board offering (or possibly offering) a part's task. */
	public static final class Board
	{
		public final PortLocation port;
		public final State state;
		public final boolean guaranteed;
		public final int qty;
		public final int level;
		public final BagSize bag;
		/** Expected bag value at high-alchemy prices. */
		public final double value;

		Board(PortLocation port, State state, boolean guaranteed, int qty, int level, BagSize bag, double value)
		{
			this.port = port;
			this.state = state;
			this.guaranteed = guaranteed;
			this.qty = qty;
			this.level = level;
			this.bag = bag;
			this.value = value;
		}
	}

	/** One body part of a picked monster. */
	public static final class Part
	{
		public final String monster;
		public final String item;
		/** A task for this part is held already. */
		public final boolean held;
		/** Boards in search order: offered and always first, then unchecked, then gone; nearest first within each. */
		public final List<Board> boards;

		Part(String monster, String item, boolean held, List<Board> boards)
		{
			this.monster = monster;
			this.item = item;
			this.held = held;
			this.boards = Collections.unmodifiableList(boards);
		}
	}

	public static final BountyHunt NONE = new BountyHunt(Collections.emptyList(), null);

	public final List<Part> parts;
	/** The board to go to next (for the first part not held that has somewhere to look), or null. */
	public final PortLocation next;

	private BountyHunt(List<Part> parts, PortLocation next)
	{
		this.parts = Collections.unmodifiableList(parts);
		this.next = next;
	}

	/**
	 * @param dbrowOf      task id -> the game's dbrow for it (what boards are remembered by), or -1 if unknown
	 * @param heldItems    body parts the player holds a bounty task for
	 * @param from         where distances are measured from (the player), or null to keep name order
	 * @param value        a task's expected bag value
	 */
	public static BountyHunt of(BountyWikiData data, List<String> monsters, LoopStatus.Memory memory, IntUnaryOperator dbrowOf,
		Collection<String> heldItems, int sailingLevel, WorldPoint from, ToDoubleFunction<BountyWikiData.Task> value)
	{
		List<Part> parts = new ArrayList<>();
		PortLocation next = null;
		for (String monster : monsters)
		{
			for (BountyWikiData.Part p : data.parts(monster))
			{
				List<Board> boards = new ArrayList<>();
				for (BountyWikiData.Task t : p.tasks)
				{
					if (sailingLevel > 0 && t.level > sailingLevel)
					{
						continue;
					}
					State state;
					if (memory.seen(t.board))
					{
						int dbrow = dbrowOf.applyAsInt(t.taskId);
						state = dbrow >= 0 && memory.offers(t.board).contains(dbrow) ? State.OFFERED : State.GONE;
					}
					else
					{
						state = t.guaranteed ? State.ALWAYS : State.UNCHECKED;
					}
					boards.add(new Board(t.board, state, t.guaranteed, t.qty, t.level, t.bag, value.applyAsDouble(t)));
				}
				if (boards.isEmpty())
				{
					continue; // every task for it is above the player's level
				}
				boards.sort(Comparator.comparingInt((Board b) -> rank(b.state)).thenComparingDouble(b -> distance(from, b.port)));
				boolean held = heldItems.contains(p.item);
				if (!held && next == null && boards.get(0).state != State.GONE)
				{
					next = boards.get(0).port;
				}
				parts.add(new Part(monster, p.item, held, boards));
			}
		}
		return new BountyHunt(parts, next);
	}

	private static int rank(State s)
	{
		switch (s)
		{
			case OFFERED:
			case ALWAYS:
				return 0;
			case UNCHECKED:
				return 1;
			default:
				return 2;
		}
	}

	/** Straight-line tiles from the player to the board (a rough guide: teleports ignore it), or 0 if unknown. */
	private static double distance(WorldPoint from, PortLocation port)
	{
		WorldPoint tile = NoticeBoardTiles.of(port);
		return from == null || tile == null ? 0 : from.distanceTo2D(tile);
	}

	/** Everything shown, as text: two hunts with equal keys look the same. */
	public String key()
	{
		StringBuilder k = new StringBuilder().append(next);
		for (Part p : parts)
		{
			k.append('|').append(p.item).append(p.held ? "!" : "");
			for (Board b : p.boards)
			{
				k.append(' ').append(b.port.ordinal()).append(b.state.ordinal());
			}
		}
		return k.toString();
	}
}
