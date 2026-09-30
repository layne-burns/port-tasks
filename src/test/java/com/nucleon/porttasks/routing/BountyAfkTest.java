package com.nucleon.porttasks.routing;

import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class BountyAfkTest
{
	private final List<String> out = new ArrayList<>();
	private final BountyAfk afk = new BountyAfk(new BountyAfk.Sink()
	{
		@Override
		public void chat(String line)
		{
			out.add(line);
		}

		@Override
		public void anki(String action, String monster)
		{
			out.add("anki " + action);
		}
	});

	private static final String SHARK = "Great white shark";

	@Test
	public void cycleOfBlackoutCorpseAttack()
	{
		afk.examine(SHARK, true);
		assertEquals(List.of("[Bounty AFK] on: Great white shark", "anki start"), out);
		afk.corpse(SHARK);
		assertEquals(BountyAfk.State.DOWN, afk.state());
		assertEquals("[Bounty AFK] Great white shark down", out.get(2));
		afk.corpse(SHARK); // a second corpse while already down says nothing
		assertEquals(3, out.size());
		afk.attacked(SHARK);
		assertEquals(BountyAfk.State.WAITING, afk.state());
		assertEquals("[Bounty AFK] Great white shark fighting", out.get(3));
	}

	@Test
	public void examineAgainTurnsItOffAndStopsTheSession()
	{
		afk.examine(SHARK, true);
		afk.examine(SHARK, true);
		assertEquals(BountyAfk.State.OFF, afk.state());
		assertEquals(List.of("[Bounty AFK] on: Great white shark", "anki start", "[Bounty AFK] off: examined again", "anki stop"), out);
	}

	@Test
	public void sessionWaitsUntilParked()
	{
		afk.examine(SHARK, false);
		assertEquals(List.of("[Bounty AFK] on: Great white shark"), out);
		afk.parked();
		afk.parked();
		assertEquals(List.of("[Bounty AFK] on: Great white shark", "anki start"), out);
	}

	@Test
	public void movingWithoutAttackingEndsItAfterTheGrace()
	{
		afk.examine(SHARK, true);
		afk.moving(100, 33);
		afk.tick(120);
		assertEquals(BountyAfk.State.WAITING, afk.state());
		afk.tick(133);
		assertEquals(BountyAfk.State.OFF, afk.state());
		assertEquals("[Bounty AFK] off: no attack after moving", out.get(out.size() - 2));
	}

	@Test
	public void anAttackCancelsTheGrace()
	{
		afk.examine(SHARK, true);
		afk.moving(100, 33);
		afk.attacked("Orca"); // another monster doesn't count
		afk.attacked(SHARK);
		assertFalse(afk.graceRunning());
		afk.tick(500);
		assertEquals(BountyAfk.State.WAITING, afk.state());
	}

	@Test
	public void offWithoutASessionSendsNoStop()
	{
		afk.examine(SHARK, false);
		afk.off("docked");
		assertEquals(List.of("[Bounty AFK] on: Great white shark", "[Bounty AFK] off: docked"), out);
		afk.off("again");
		assertEquals(2, out.size());
	}
}
