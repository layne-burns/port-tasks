package com.nucleon.porttasks.routing;

import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
	public void lootAlertWaitsForThreeCorpses()
	{
		afk.lootAlert(3, 300, 33);
		afk.examine(SHARK, true);
		afk.corpse(SHARK, 1, 0);
		afk.corpse(SHARK, 2, 10);
		assertEquals(BountyAfk.State.WAITING, afk.state());
		afk.corpse(SHARK, 3, 20);
		assertEquals(BountyAfk.State.DOWN, afk.state());
		assertEquals("[Bounty AFK] 3 to loot: Great white shark down", out.get(2));
		// Crew hits while the batch waits don't bring the blackout back; after looting, the next hit does.
		afk.attacked(SHARK);
		assertEquals(BountyAfk.State.DOWN, afk.state());
		afk.corpseGone(1);
		afk.corpseGone(2);
		afk.corpseGone(3);
		afk.attacked(SHARK);
		assertEquals(BountyAfk.State.WAITING, afk.state());
		assertEquals("[Bounty AFK] Great white shark fighting", out.get(3));
	}

	@Test
	public void lootAlertBeforeTheOldestDespawns()
	{
		afk.lootAlert(3, 300, 33);
		afk.examine(SHARK, true);
		afk.corpse(SHARK, 1, 100);
		assertTrue(afk.needsTick());
		afk.tick(366);
		assertEquals(BountyAfk.State.WAITING, afk.state());
		afk.tick(367); // 300 - 33 ticks after it appeared: ~20 s before it despawns
		assertEquals(BountyAfk.State.DOWN, afk.state());
		assertEquals("[Bounty AFK] 1 to loot: Great white shark down", out.get(2));
	}

	@Test
	public void corpsesOfOtherMonstersDontCount()
	{
		afk.lootAlert(1, 300, 33);
		afk.examine(SHARK, true);
		afk.corpse("Orca", 1, 0);
		assertEquals(BountyAfk.State.WAITING, afk.state());
		assertFalse(afk.needsTick());
	}

	@Test
	public void autoArmOnlyWhenOff()
	{
		afk.autoArm(SHARK, true);
		assertEquals(BountyAfk.State.WAITING, afk.state());
		afk.autoArm(SHARK, true); // already on: nothing new, and it doesn't toggle off like an examine
		assertEquals(List.of("[Bounty AFK] on: Great white shark", "anki start"), out);
		assertTrue(afk.examine(SHARK, true)); // the manual switch still turns it off
		assertEquals(BountyAfk.State.OFF, afk.state());
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
