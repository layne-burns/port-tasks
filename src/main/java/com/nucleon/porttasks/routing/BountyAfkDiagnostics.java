package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.BountyTaskData;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

/**
 * Bounty AFK, phase 0 (SPEC-routing.md §2.5.3): logs what the game shows while hunting at sea, to pin down
 * what the AFK mode can rely on: whose hitsplats crew cannon hits are, what the crew says when switching fire
 * mode (chat or overhead text), what an Examine click on a sea monster looks like, and the corpse's spawn and
 * despawn. Only while the player is on their boat. Temporary: removed once the AFK mode is built on it.
 * Client thread only.
 */
@Slf4j
public final class BountyAfkDiagnostics
{
	/** Live NPC id -> monster task name, rebuilt on demand from the game's task table. */
	private final Map<Integer, String> live = new HashMap<>();
	private final Map<Integer, String> dead = new HashMap<>();

	private void index()
	{
		if (!live.isEmpty())
		{
			return;
		}
		for (BountyTaskData d : BountyTaskData.all())
		{
			live.put(d.npcId, d.taskName);
			dead.put(d.getDeadNpcId(), d.taskName);
		}
	}

	/** A hitsplat on an NPC: logged if it is a bounty monster (live or dead). */
	public void hitsplat(Actor target, int type, boolean mine, boolean others, int amount, WorldPoint boat)
	{
		if (!(target instanceof NPC))
		{
			return;
		}
		index();
		int id = ((NPC) target).getId();
		String name = live.getOrDefault(id, dead.get(id));
		if (name == null)
		{
			return;
		}
		WorldPoint at = target.getWorldLocation();
		log.info("[bountyafk] hitsplat on {} (npc {}, {}): type {} mine {} others {} amount {} dist {}", target.getName(), id,
			live.containsKey(id) ? "live" : "dead", type, mine, others, amount,
			boat == null || at == null ? "?" : Integer.toString(boat.distanceTo2D(at)));
	}

	/** Overhead text on any actor (crew, the player, monsters). */
	public void overhead(Actor actor, String text)
	{
		log.info("[bountyafk] overhead {} {} '{}': '{}'", actor instanceof NPC ? "npc" : "player",
			actor instanceof NPC ? ((NPC) actor).getId() : "", actor.getName(), text);
	}

	/** A chat message of any type but public/private chat. */
	public void chat(String type, String name, String message)
	{
		log.info("[bountyafk] chat {} '{}': '{}'", type, name, message);
	}

	/** A menu click on an NPC (Examine and the rest). */
	public void npcClick(String option, String target, NPC npc)
	{
		index();
		log.info("[bountyafk] click '{}' on '{}' npc {} ({})", option, target, npc.getId(),
			live.containsKey(npc.getId()) ? "bounty monster" : dead.containsKey(npc.getId()) ? "bounty corpse" : "other");
	}

	/** A bounty corpse spawned or despawned. */
	public void corpse(NPC npc, boolean spawned, WorldPoint boat)
	{
		WorldPoint at = npc.getWorldLocation();
		log.info("[bountyafk] corpse {} {} (npc {}) dist {}", spawned ? "spawned" : "despawned", npc.getName(), npc.getId(),
			boat == null || at == null ? "?" : Integer.toString(boat.distanceTo2D(at)));
	}
}
