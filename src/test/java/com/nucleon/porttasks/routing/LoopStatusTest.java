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
}
