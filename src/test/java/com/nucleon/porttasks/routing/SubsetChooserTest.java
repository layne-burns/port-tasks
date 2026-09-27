package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import static com.nucleon.porttasks.enums.PortLocation.ALDARIN;
import static com.nucleon.porttasks.enums.PortLocation.DEEPFIN_POINT;
import static com.nucleon.porttasks.enums.PortLocation.PORT_TYRAS;
import static com.nucleon.porttasks.enums.PortLocation.SUNSET_COAST;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SubsetChooserTest
{
	private static final double EPS = 1e-9;

	/** Ports on a line: Deepfin -100, Aldarin (the board) 0, Sunset 30, Tyras 60. */
	private static final Map<PortLocation, Integer> X = new EnumMap<>(PortLocation.class);

	static
	{
		X.put(DEEPFIN_POINT, -100);
		X.put(ALDARIN, 0);
		X.put(SUNSET_COAST, 30);
		X.put(PORT_TYRAS, 60);
	}

	private final SubsetChooser chooser = new SubsetChooser((a, b) -> Math.abs(X.get(a) - X.get(b)));

	private static SubsetChooser.Candidate offer(int dbrow, PortLocation from, PortLocation to, double reward)
	{
		return new SubsetChooser.Candidate(dbrow, new RoutePlanner.TaskState(dbrow, from, to, true), reward);
	}

	// A (the other way, 400 over 100 tiles = 4/tile) is the best single task, but B, C and D run the same
	// way: together 370 over 60 tiles = 6.2/tile, and anything with A must sail both ways.
	private static final SubsetChooser.Candidate A = offer(1, ALDARIN, DEEPFIN_POINT, 400);
	private static final SubsetChooser.Candidate B = offer(2, ALDARIN, SUNSET_COAST, 100);
	private static final SubsetChooser.Candidate C = offer(3, SUNSET_COAST, PORT_TYRAS, 120);
	private static final SubsetChooser.Candidate D = offer(4, ALDARIN, PORT_TYRAS, 150);

	@Test
	public void aSetBeatsTheBestSingleTask()
	{
		SubsetChooser.Result single = chooser.choose(ALDARIN, Collections.emptyList(), 0, List.of(A, B, C, D), 1, null, 0);
		assertEquals(List.of(1), single.dbrows);
		assertEquals(4, single.rate, EPS);

		SubsetChooser.Result three = chooser.choose(ALDARIN, Collections.emptyList(), 0, List.of(A, B, C, D), 3, null, 0);
		assertEquals(List.of(2, 3, 4), three.dbrows);
		assertEquals(370.0 / 60, three.rate, EPS);
		assertTrue(three.exact);
		// 4 singles + 6 pairs + 4 triples, and the empty set.
		assertEquals(15, three.evaluated);
	}

	@Test
	public void takesNothingWhenNothingImprovesTheRate()
	{
		// Held: Aldarin -> Tyras for 600 (10/tile). A drags the rate down; so would anything else here.
		List<RoutePlanner.TaskState> held = List.of(new RoutePlanner.TaskState(9, ALDARIN, PORT_TYRAS, true));
		SubsetChooser.Result r = chooser.choose(ALDARIN, held, 600, List.of(A), 4, null, 0);
		assertTrue(r.dbrows.isEmpty());
		assertEquals(10, r.heldRate, EPS);
	}

	@Test
	public void aFreeRideIsAlwaysTaken()
	{
		// Held: Aldarin -> Tyras. B (Aldarin -> Sunset) is on the way: same tiles, more XP.
		List<RoutePlanner.TaskState> held = List.of(new RoutePlanner.TaskState(9, ALDARIN, PORT_TYRAS, true));
		SubsetChooser.Result r = chooser.choose(ALDARIN, held, 600, List.of(B), 4, null, 0);
		assertEquals(List.of(2), r.dbrows);
	}

	@Test
	public void noFreeSlotsMeansNoSuggestion()
	{
		SubsetChooser.Result r = chooser.choose(ALDARIN, Collections.emptyList(), 0, List.of(A, B), 0, null, 0);
		assertTrue(r.dbrows.isEmpty());
	}

	@Test
	public void setCounts()
	{
		assertEquals(1 + 12 + 66 + 220 + 495 + 792, SubsetChooser.setCount(12, 5));
		assertEquals(8, SubsetChooser.setCount(3, 5));
	}

	@Test
	public void bigBoardsKeepTheStrongestCandidatesAndSaySo()
	{
		PortLocation[] ports = {ALDARIN, SUNSET_COAST, PORT_TYRAS, DEEPFIN_POINT};
		List<SubsetChooser.Candidate> many = new java.util.ArrayList<>();
		for (int i = 0; i < 30; i++)
		{
			many.add(offer(100 + i, ports[i % 3], ports[1 + i % 3], 50 + i));
		}
		SubsetChooser.Result r = chooser.choose(ALDARIN, Collections.emptyList(), 0, many, 5, null, 10);
		assertFalse(r.exact);
		assertTrue(r.evaluated <= SubsetChooser.MAX_SETS);
		assertFalse(r.dbrows.isEmpty());
	}
}
