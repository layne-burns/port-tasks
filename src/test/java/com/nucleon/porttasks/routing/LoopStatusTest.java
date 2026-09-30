package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class LoopStatusTest
{
	private final Map<PortLocation, Set<Integer>> seen = new EnumMap<>(PortLocation.class);
	private boolean boarded;
	private final LoopStatus.Memory memory = new LoopStatus.Memory()
	{
		@Override
		public boolean seen(PortLocation board)
		{
			return seen.containsKey(board);
		}

		@Override
		public Set<Integer> offers(PortLocation board)
		{
			return seen.getOrDefault(board, Collections.emptySet());
		}

		@Override
		public boolean gatherDone()
		{
			return boarded;
		}
	};
	// Deepfin Point and Red Rock both have notice boards.
	private final LoopPorts loop = LoopPorts.parse("Deepfin, Red Rock");

	@Test
	public void phases()
	{
		assertEquals(LoopStatus.Phase.OFF, LoopStatus.of(LoopPorts.NONE, memory, d -> true, 8).phase);

		LoopStatus s = LoopStatus.of(loop, memory, d -> true, 8);
		assertEquals(LoopStatus.Phase.GATHER, s.phase);
		assertEquals(PortLocation.DEEPFIN_POINT, s.nextUnseen());

		seen.put(PortLocation.DEEPFIN_POINT, Set.of(1, 2));
		assertEquals(PortLocation.RED_ROCK, LoopStatus.of(loop, memory, d -> true, 8).nextUnseen());

		seen.put(PortLocation.RED_ROCK, Set.of(3));
		s = LoopStatus.of(loop, memory, d -> d == 2, 5);
		assertEquals(LoopStatus.Phase.SAIL, s.phase);
		assertNull(s.nextUnseen());
		assertEquals(1, s.board(PortLocation.DEEPFIN_POINT).worthwhile);
		assertEquals(0, s.board(PortLocation.RED_ROCK).worthwhile);

		s = LoopStatus.of(loop, memory, d -> false, 5);
		assertEquals(LoopStatus.Phase.DRY, s.phase);
		assertEquals(5, s.tasksToReset);
	}

	@Test
	public void seaOnlyBoardsAreLeftOutOfTheGather()
	{
		LoopPorts seaOnly = LoopPorts.parse("Deepfin");
		LoopStatus s = LoopStatus.of(loop, seaOnly, Collections.emptySet(), memory, d -> true, 8);
		assertEquals(LoopStatus.Phase.GATHER, s.phase);
		assertEquals(PortLocation.RED_ROCK, s.nextUnseen());
		seen.put(PortLocation.RED_ROCK, Set.of(3));
		// Deepfin is still unseen, but it's reached by sea: the gather is done.
		assertEquals(LoopStatus.Phase.SAIL, LoopStatus.of(loop, seaOnly, Collections.emptySet(), memory, d -> true, 8).phase);
	}

	@Test
	public void unusableBoardsAreLeftOut()
	{
		// Red Rock's board locked behind a quest: only Deepfin's is listed or gathered.
		LoopStatus s = LoopStatus.of(loop, LoopPorts.NONE, Set.of(PortLocation.RED_ROCK), memory, d -> true, 8);
		assertEquals(1, s.boards.size());
		assertEquals(PortLocation.DEEPFIN_POINT, s.nextUnseen());
	}

	@Test
	public void boardingEndsTheGather()
	{
		seen.put(PortLocation.DEEPFIN_POINT, Set.of(1));
		assertEquals(LoopStatus.Phase.GATHER, LoopStatus.of(loop, memory, d -> true, 8).phase);
		boarded = true;
		LoopStatus s = LoopStatus.of(loop, memory, d -> true, 8);
		assertEquals(LoopStatus.Phase.SAIL, s.phase);
		// Red Rock stays unseen (a dock there still gets the reminder), but nothing sends the player to it.
		assertEquals(false, s.board(PortLocation.RED_ROCK).seen);
	}
}
