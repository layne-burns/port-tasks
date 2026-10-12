package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.CourierTaskData;
import com.nucleon.porttasks.enums.TaskReward;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 1 check (SPEC-routing.md §7): logs each courier task taken as the plugin and the wiki see it, to
 * confirm the task-id join and compare XP. At debug, so it costs nothing unless turned on. (The boat-location
 * varbit logging that was here found SAILING_BOAT_n_PORT; see BoatLocator.) Client thread only.
 */
@Slf4j
public final class RoutingDiagnostics
{
	private final CourierWikiData wiki;
	private final RewardValuer valuer;
	private final XpLearner xp;

	public RoutingDiagnostics(CourierWikiData wiki, RewardValuer valuer, XpLearner xp)
	{
		this.xp = xp;
		this.wiki = wiki;
		this.valuer = valuer;
	}

	public void logTask(int slot, CourierTaskData d)
	{
		if (!log.isDebugEnabled())
		{
			return; // the expected value below prices the bag's drops; not worth it for a dropped line
		}
		CourierWikiData.Task w = wiki.task(d.getId());
		int pluginXp = TaskReward.getIntRewardForTask(d.getDbrow());
		if (w == null)
		{
			log.debug("[routing] slot {} task id {} (dbrow {}) '{}': NOT in wiki data; plugin XP {}",
				slot, d.getId(), d.getDbrow(), d.taskName, pluginXp);
			return;
		}
		boolean namesMatch = w.name != null && w.name.equalsIgnoreCase(d.taskName);
		boolean portsMatch = w.cargoPort.equals(d.getCargoLocation().getName())
			&& w.destination.equals(d.getDeliveryLocation().getName());
		Integer baseXp = xp.xp(d.getId());
		String value = baseXp == null ? "?" : String.format("%.0f", valuer.expectedTaskValue(baseXp, w.destination));
		log.debug("[routing] slot {} task id {} (dbrow {}): plugin '{}' / wiki '{}' names {} | {} -> {} ports {} | "
				+ "crates plugin {} wiki {} | XP plugin {} wiki {} used {} | bag {} | expected value {} gp",
			slot, d.getId(), d.getDbrow(), d.taskName, w.name, namesMatch ? "match" : "DIFFER",
			w.cargoPort, w.destination, portsMatch ? "match" : "DIFFER (plugin " + d.getCargoLocation() + " -> " + d.getDeliveryLocation() + ")",
			d.cargoAmount, w.crates, pluginXp, w.xp, baseXp, baseXp == null ? "?" : BagSize.forXp(baseXp).wikiName(), value);
	}
}
