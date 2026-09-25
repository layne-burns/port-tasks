package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Courier task XP and reward-bag contents from the OSRS Wiki, bundled as resources by
 * scripts/update_courier_data.py (SPEC-routing.md §6). The game's task table has no XP, so this is the
 * source of task rewards; tasks are keyed by task id, the value of the PORT_TASK_SLOT_n_ID varbits.
 */
@Slf4j
public final class CourierWikiData
{
	/** One courier task row from the wiki's "Courier tasks" table. */
	public static final class Task
	{
		public String name;
		public int level;
		/** Base Sailing XP; null where the wiki doesn't know it. */
		public Integer xp;
		public String noticeBoard;
		public String cargoPort;
		public String destination;
		public int crates;
	}

	/** One possible drop in a reward bag. */
	public static final class Drop
	{
		public String name;
		/** Item id; null only for coins. */
		public Integer id;
		public int min;
		public int max;
		public boolean noted;
	}

	private static final class TasksFile
	{
		Map<String, Task> tasks;
	}

	private static final class BagsFile
	{
		/** Bag size -> drops every port can give. */
		Map<String, List<Drop>> shared;
		/** Bag size -> destination port name -> that port's signature drops. */
		Map<String, Map<String, List<Drop>>> signature;
	}

	private final Map<String, Task> tasks;
	private final BagsFile bags;

	private CourierWikiData(Map<String, Task> tasks, BagsFile bags)
	{
		this.tasks = tasks;
		this.bags = bags;
	}

	/** Loads the bundled data; on failure returns empty data and logs, so routing degrades instead of breaking. */
	public static CourierWikiData load(Gson gson)
	{
		try
		{
			TasksFile t = read(gson, "courier_tasks.json", TasksFile.class);
			BagsFile b = read(gson, "reward_bags.json", BagsFile.class);
			log.debug("courier wiki data: {} tasks", t.tasks.size());
			return new CourierWikiData(t.tasks, b);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load courier wiki data", e);
			BagsFile empty = new BagsFile();
			empty.shared = Collections.emptyMap();
			empty.signature = Collections.emptyMap();
			return new CourierWikiData(Collections.emptyMap(), empty);
		}
	}

	private static <T> T read(Gson gson, String resource, Class<T> type) throws IOException
	{
		try (InputStream in = CourierWikiData.class.getResourceAsStream("/com/nucleon/porttasks/" + resource))
		{
			if (in == null)
			{
				throw new IOException("missing resource " + resource);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return gson.fromJson(reader, type);
			}
		}
	}

	public Task task(int taskId)
	{
		return tasks.get(Integer.toString(taskId));
	}

	public List<Drop> sharedDrops(BagSize size)
	{
		return bags.shared.getOrDefault(size.wikiName(), Collections.emptyList());
	}

	public List<Drop> signatureDrops(BagSize size, String port)
	{
		return bags.signature.getOrDefault(size.wikiName(), Collections.emptyMap())
			.getOrDefault(port, Collections.emptyList());
	}
}
