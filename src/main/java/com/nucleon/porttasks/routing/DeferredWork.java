package com.nucleon.porttasks.routing;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Work that several events in one tick would each redo, done once at the end of the tick instead. At login
 * and when a task is taken a slot's varbits arrive together, and each crate moved changes a varbit and the
 * inventory; each event used to replan, rescore the board and rebuild the view and the side panel.
 * Callers mark what went stale (flags they define); the first mark since the last run schedules one run,
 * which hands the worker every flag marked since. Single-threaded (the client thread).
 */
public final class DeferredWork
{
	private final Consumer<Runnable> scheduler;
	private final IntConsumer worker;
	private int stale;

	/**
	 * @param scheduler runs a runnable later on the same thread, e.g. {@code clientThread::invokeAtTickEnd}
	 * @param worker    does the work for a set of flags
	 */
	public DeferredWork(Consumer<Runnable> scheduler, IntConsumer worker)
	{
		this.scheduler = scheduler;
		this.worker = worker;
	}

	/** Marks this work stale: done at the next run, scheduled now if none is pending. */
	public void mark(int flags)
	{
		if (flags == 0)
		{
			return;
		}
		boolean first = stale == 0;
		stale |= flags;
		if (first)
		{
			scheduler.accept(this::run);
		}
	}

	/** Forgets what is marked, so a pending run does nothing (shutdown). */
	public void clear()
	{
		stale = 0;
	}

	private void run()
	{
		int flags = stale;
		stale = 0; // a mark made by the worker schedules another run
		if (flags != 0)
		{
			worker.accept(flags);
		}
	}
}
