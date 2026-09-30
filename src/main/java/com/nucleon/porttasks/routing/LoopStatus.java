package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Where loop mode stands (SPEC-routing.md §2.4.2), worked out when boards, tasks or settings change and read
 * by the panel and overlays. Immutable.
 *
 *  - GATHER: a loop board hasn't been seen since the boards last reset. Go and look at it.
 *  - SAIL:   every loop board has been seen and at least one still offers a task worth taking.
 *  - DRY:    every loop board has been seen and none offers one. New offers only come with the next reset,
 *            so the way out is completing tasks: filler tasks, quickest first.
 */
public final class LoopStatus
{
	public enum Phase
	{
		OFF, GATHER, SAIL, DRY
	}

	/** One loop board: seen or not, and how many worthwhile in-loop tasks it still offers. */
	public static final class Board
	{
		public final PortLocation port;
		public final boolean seen;
		public final int worthwhile;

		Board(PortLocation port, boolean seen, int worthwhile)
		{
			this.port = port;
			this.seen = seen;
			this.worthwhile = worthwhile;
		}
	}

	/** What loop mode remembers of the boards this reset cycle ({@link LoopBoards}). */
	public interface Memory
	{
		boolean seen(PortLocation board);

		Set<Integer> offers(PortLocation board);
	}

	public static final LoopStatus OFF = new LoopStatus(Phase.OFF, Collections.emptyList(), 0);

	public final Phase phase;
	/** The loop's ports that have a notice board, in loop order. */
	public final List<Board> boards;
	/** Port tasks still to complete before the boards reset. */
	public final int tasksToReset;

	private LoopStatus(Phase phase, List<Board> boards, int tasksToReset)
	{
		this.phase = phase;
		this.boards = Collections.unmodifiableList(boards);
		this.tasksToReset = tasksToReset;
	}

	/**
	 * @param worthwhile whether an offered courier task (by dbrow) is worth taking: in the loop, bag size
	 *                   allowed, level high enough, not held
	 */
	public static LoopStatus of(LoopPorts loop, Memory memory, IntPredicate worthwhile, int tasksToReset)
	{
		if (!loop.active())
		{
			return OFF;
		}
		List<Board> boards = new ArrayList<>();
		boolean allSeen = true;
		int total = 0;
		for (PortLocation p : loop.ports())
		{
			if (p.getNoticeboardObject() == -1)
			{
				continue; // no notice board here: nothing to gather
			}
			boolean seen = memory.seen(p);
			int n = 0;
			for (int dbrow : memory.offers(p))
			{
				if (worthwhile.test(dbrow))
				{
					n++;
				}
			}
			boards.add(new Board(p, seen, n));
			allSeen &= seen;
			total += n;
		}
		Phase phase = boards.isEmpty() ? Phase.OFF : !allSeen ? Phase.GATHER : total > 0 ? Phase.SAIL : Phase.DRY;
		return new LoopStatus(phase, boards, tasksToReset);
	}

	/** Everything shown, as text: two statuses with equal keys look the same. */
	public String key()
	{
		StringBuilder k = new StringBuilder().append(phase).append(' ').append(tasksToReset);
		for (Board b : boards)
		{
			k.append(' ').append(b.port.ordinal()).append(b.seen ? ':' : '?').append(b.worthwhile);
		}
		return k.toString();
	}

	/** The first loop board not seen yet, or null. */
	public PortLocation nextUnseen()
	{
		for (Board b : boards)
		{
			if (!b.seen)
			{
				return b.port;
			}
		}
		return null;
	}

	/** This port's loop board, or null if it isn't one. */
	public Board board(PortLocation port)
	{
		for (Board b : boards)
		{
			if (b.port == port)
			{
				return b;
			}
		}
		return null;
	}
}
