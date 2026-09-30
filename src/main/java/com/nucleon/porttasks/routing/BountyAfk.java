package com.nucleon.porttasks.routing;

/**
 * Bounty AFK (SPEC-routing.md §2.5.3): the blackout-while-planted mode. Pure state; the plugin feeds it game
 * events and it reports through a {@link Sink}: chat lines that Watchdog alerts match (show or dismiss the
 * blackout, flash the screen), and start/stop for AnkiScape's Bounty mode. Client thread only.
 *
 *   OFF     -examine monster->            WAITING  (blackout up; armed on that monster)
 *   WAITING -its corpse appears->         DOWN     (blackout down, flash: go and loot)
 *   DOWN    -the boat attacks one->       WAITING  (blackout back)
 *   any     -examine it again / its bounty's parts are in / docked or off the boat
 *            / moved and no attack within the grace->   OFF (blackout gone, flash)
 *
 * The Anki session starts the first time the mode is armed with the boat parked ("planted and firing"),
 * survives moving (repositioning, banking), and stops when the mode goes off.
 */
public final class BountyAfk
{
	public enum State
	{
		OFF, WAITING, DOWN
	}

	/** Where the mode's effects go. */
	public interface Sink
	{
		/** A chat line for Watchdog ("[Bounty AFK] ..."). */
		void chat(String line);

		/** AnkiScape's Bounty mode: "start" or "stop". */
		void anki(String action, String monster);
	}

	public static final String PREFIX = "[Bounty AFK] ";

	private final Sink sink;
	private State state = State.OFF;
	private String monster;
	private boolean sessionStarted;
	/** Tick by which the boat, having moved, must attack again; -1 for none. */
	private int graceUntil = -1;

	public BountyAfk(Sink sink)
	{
		this.sink = sink;
	}

	public State state()
	{
		return state;
	}

	/** The monster the mode is armed on, or null when off. */
	public String monster()
	{
		return monster;
	}

	/** True while a grace deadline is pending (the plugin checks it each tick only then). */
	public boolean graceRunning()
	{
		return graceUntil >= 0;
	}

	/** The player examined a bounty monster; {@code parked} if the boat is stopped at sea. */
	public void examine(String examined, boolean parked)
	{
		if (state != State.OFF && examined.equals(monster))
		{
			off("examined again");
			return;
		}
		if (state != State.OFF)
		{
			off("switched to " + examined);
		}
		monster = examined;
		state = State.WAITING;
		graceUntil = -1;
		sink.chat(PREFIX + "on: " + monster);
		if (parked)
		{
			startSession();
		}
	}

	/** The boat came to a stop at sea. */
	public void parked()
	{
		if (state != State.OFF)
		{
			startSession();
		}
	}

	/** The boat started moving: it has {@code graceTicks} to attack the monster again. */
	public void moving(int tick, int graceTicks)
	{
		if (state != State.OFF && graceUntil < 0)
		{
			graceUntil = tick + graceTicks;
		}
	}

	/** The boat hit one of the armed monster. */
	public void attacked(String target)
	{
		if (state == State.OFF || !target.equals(monster))
		{
			return;
		}
		graceUntil = -1;
		if (state == State.DOWN)
		{
			state = State.WAITING;
			sink.chat(PREFIX + monster + " fighting");
		}
	}

	/** A corpse of this monster appeared near the boat. */
	public void corpse(String of)
	{
		if (state == State.WAITING && of.equals(monster))
		{
			state = State.DOWN;
			sink.chat(PREFIX + monster + " down");
		}
	}

	/** Each tick while the grace runs. */
	public void tick(int tick)
	{
		if (graceUntil >= 0 && tick >= graceUntil)
		{
			off("no attack after moving");
		}
	}

	/** Turns the mode off (docked, off the boat, parts in, ...), if on. */
	public void off(String reason)
	{
		if (state == State.OFF)
		{
			return;
		}
		String was = monster;
		state = State.OFF;
		graceUntil = -1;
		monster = null;
		sink.chat(PREFIX + "off: " + reason);
		if (sessionStarted)
		{
			sessionStarted = false;
			sink.anki("stop", was);
		}
	}

	private void startSession()
	{
		if (!sessionStarted)
		{
			sessionStarted = true;
			sink.anki("start", monster);
		}
	}
}
