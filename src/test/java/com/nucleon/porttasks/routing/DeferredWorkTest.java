package com.nucleon.porttasks.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DeferredWorkTest
{
	private final List<Runnable> scheduled = new ArrayList<>();
	private final List<Integer> ran = new ArrayList<>();
	private final DeferredWork work = new DeferredWork(scheduled::add, ran::add);

	@Test
	public void marksBeforeTheRunAreDoneOnceWithEveryFlag()
	{
		work.mark(1);
		work.mark(2);
		work.mark(1);
		assertEquals(1, scheduled.size());
		assertTrue(ran.isEmpty());
		scheduled.remove(0).run();
		assertEquals(List.of(3), ran);
	}

	@Test
	public void aMarkAfterTheRunSchedulesAgain()
	{
		work.mark(1);
		scheduled.remove(0).run();
		work.mark(4);
		assertEquals(1, scheduled.size());
		scheduled.remove(0).run();
		assertEquals(List.of(1, 4), ran);
	}

	@Test
	public void aMarkDuringTheRunSchedulesAnother()
	{
		DeferredWork[] self = new DeferredWork[1];
		self[0] = new DeferredWork(scheduled::add, flags ->
		{
			ran.add(flags);
			if (flags == 1)
			{
				self[0].mark(4);
			}
		});
		self[0].mark(1);
		scheduled.remove(0).run();
		assertEquals(1, scheduled.size());
		scheduled.remove(0).run();
		assertEquals(List.of(1, 4), ran);
	}

	@Test
	public void clearDropsPendingWork()
	{
		work.mark(1);
		work.clear();
		scheduled.remove(0).run();
		assertTrue(ran.isEmpty());
		work.mark(2);
		assertEquals(1, scheduled.size());
	}

	@Test
	public void markingNothingSchedulesNothing()
	{
		work.mark(0);
		assertTrue(scheduled.isEmpty());
	}
}
