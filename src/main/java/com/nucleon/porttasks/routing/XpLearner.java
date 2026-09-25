package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Learns each courier task's real base XP from play: when the last crate of a task is delivered, the next
 * Sailing XP gain within a couple of ticks is that task's reward. Learned values override the wiki's and are
 * kept in the profile.
 *
 * XP boosts (e.g. keg of horizon's lure) inflate the drop, but bag size and the spec use base XP, so a value
 * is only learned when it is within 15% of the wiki's (or the wiki has none); anything further off is logged
 * as a probable boost and ignored. Client thread only.
 */
@Slf4j
public final class XpLearner
{
	/** Task id -> learned base XP, as stored in the profile. */
	private static final class LearnedXp extends HashMap<Integer, Integer>
	{
	}

	private static final String KEY = "routingLearnedXp";
	private static final int MATCH_TICKS = 3;
	private static final double MAX_DEVIATION = 0.15;

	private final ConfigManager configManager;
	private final String group;
	private final Gson gson;
	private final CourierWikiData wiki;
	private final LearnedXp learned = new LearnedXp();

	private int pendingTaskId = -1;
	private int pendingTick;
	private int lastSailingXp = -1;

	public XpLearner(ConfigManager configManager, String group, Gson gson, CourierWikiData wiki)
	{
		this.configManager = configManager;
		this.group = group;
		this.gson = gson;
		this.wiki = wiki;
		String stored = configManager.getConfiguration(group, KEY);
		if (stored != null)
		{
			try
			{
				LearnedXp m = gson.fromJson(stored, LearnedXp.class);
				if (m != null)
				{
					learned.putAll(m);
				}
			}
			catch (RuntimeException e)
			{
				log.debug("learned XP unreadable; starting empty", e);
			}
		}
	}

	/** Base XP for a task: learned from play if known, else the wiki's; null if neither knows it. */
	public Integer xp(int taskId)
	{
		Integer l = learned.get(taskId);
		if (l != null)
		{
			return l;
		}
		CourierWikiData.Task t = wiki.task(taskId);
		return t == null ? null : t.xp;
	}

	/** The final crate of a task was delivered this tick; its XP drop should follow shortly. */
	public void expectCompletion(int taskId, int tick)
	{
		pendingTaskId = taskId;
		pendingTick = tick;
	}

	/** Sailing XP changed to totalXp this tick. */
	public void onSailingXp(int totalXp, int tick)
	{
		int previous = lastSailingXp;
		lastSailingXp = totalXp;
		if (previous < 0 || pendingTaskId < 0 || totalXp <= previous)
		{
			return;
		}
		if (tick - pendingTick > MATCH_TICKS)
		{
			pendingTaskId = -1;
			return;
		}
		int gained = totalXp - previous;
		int taskId = pendingTaskId;
		pendingTaskId = -1;

		CourierWikiData.Task t = wiki.task(taskId);
		Integer wikiXp = t == null ? null : t.xp;
		if (wikiXp != null && Math.abs(gained - wikiXp) > wikiXp * MAX_DEVIATION)
		{
			log.info("[routing] task {} gave {} XP, wiki says {}: probably boosted, not learned", taskId, gained, wikiXp);
			return;
		}
		Integer old = learned.put(taskId, gained);
		if (old == null || old != gained)
		{
			configManager.setConfiguration(group, KEY, gson.toJson(learned));
		}
		log.info("[routing] task {} ({}) gave {} XP; wiki {}; learned", taskId, t == null ? "?" : t.name, gained, wikiXp);
	}
}
