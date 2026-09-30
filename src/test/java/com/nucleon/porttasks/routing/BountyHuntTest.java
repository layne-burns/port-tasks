package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BountyHuntTest
{
	private static BountyWikiData.Row row(String board, String item, boolean guaranteed, int level)
	{
		BountyWikiData.Row r = new BountyWikiData.Row();
		r.noticeBoard = board;
		r.monster = "Great white shark";
		r.item = item;
		r.qty = 5;
		r.level = level;
		r.xp = 40370;
		r.guaranteed = guaranteed;
		r.bag = "Huge";
		return r;
	}

	private final BountyWikiData data;
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

		@Override
		public boolean gatherDone()
		{
			return false;
		}
	};

	public BountyHuntTest()
	{
		Map<String, BountyWikiData.Row> rows = new HashMap<>();
		rows.put("553", row("Port Roberts", "Great white shark jaw", true, 75));
		rows.put("483", row("Port Khazard", "Great white shark jaw", false, 75));
		rows.put("493", row("Corsair Cove", "Great white shark liver", false, 75));
		rows.put("527", row("Land's End", "Great white shark liver", false, 75));
		rows.put("575", row("Prifddinas", "Great white shark liver", false, 95));
		data = BountyWikiData.fromRows(rows);
	}

	// Task id doubles as dbrow here.
	private BountyHunt hunt(Set<String> held)
	{
		return hunt(held, Set.of());
	}

	private BountyHunt hunt(Set<String> held, Set<PortLocation> unreachable)
	{
		return BountyHunt.of(data, List.of("Great white shark"), unreachable, memory, id -> id, held, 99, null, t -> 1000);
	}

	@Test
	public void unreachableBoardsAreLeftOut()
	{
		// At 99 Prifddinas' level-95 liver task counts, until Prifddinas is unreachable (no Song of the Elves).
		assertEquals(3, hunt(Set.of()).parts.get(1).boards.size());
		BountyHunt h = hunt(Set.of(), Set.of(PortLocation.PRIFDDINAS));
		assertEquals(2, h.parts.get(1).boards.size());
		for (BountyHunt.Board b : h.parts.get(1).boards)
		{
			assertTrue(b.port != PortLocation.PRIFDDINAS);
		}
	}

	@Test
	public void guaranteedFirstThenUncheckedAndLevelFiltered()
	{
		BountyHunt h = hunt(Set.of());
		assertEquals(2, h.parts.size());
		BountyHunt.Part jaw = h.parts.get(0);
		assertEquals("Great white shark jaw", jaw.item);
		assertEquals(PortLocation.PORT_ROBERTS, jaw.boards.get(0).port);
		assertEquals(BountyHunt.State.ALWAYS, jaw.boards.get(0).state);
		assertEquals(BountyHunt.State.UNCHECKED, jaw.boards.get(1).state);
		assertEquals(3, h.parts.get(1).boards.size());
		// At level 92 Prifddinas' level-95 task is left out.
		BountyHunt low = BountyHunt.of(data, List.of("Great white shark"), Set.of(), memory, id -> id, Set.of(), 92, null, t -> 1000);
		assertEquals(2, low.parts.get(1).boards.size());
		assertEquals(PortLocation.PORT_ROBERTS, h.next);
	}

	@Test
	public void seenBoardsAreOfferedOrGone()
	{
		seen.put(PortLocation.CORSAIR_COVE, Set.of(1));
		seen.put(PortLocation.LANDS_END, Set.of(527));
		BountyHunt h = hunt(Set.of("Great white shark jaw"), Set.of(PortLocation.PRIFDDINAS));
		assertTrue(h.parts.get(0).held);
		BountyHunt.Part liver = h.parts.get(1);
		assertEquals(PortLocation.LANDS_END, liver.boards.get(0).port);
		assertEquals(BountyHunt.State.OFFERED, liver.boards.get(0).state);
		assertEquals(BountyHunt.State.GONE, liver.boards.get(1).state);
		// The jaw is held, so the hunt heads for the liver.
		assertEquals(PortLocation.LANDS_END, h.next);
	}

	@Test
	public void nothingLeftToCheck()
	{
		seen.put(PortLocation.CORSAIR_COVE, Set.of());
		seen.put(PortLocation.LANDS_END, Set.of());
		assertNull(hunt(Set.of("Great white shark jaw"), Set.of(PortLocation.PRIFDDINAS)).next);
	}
}
