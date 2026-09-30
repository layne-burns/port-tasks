package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import com.nucleon.porttasks.enums.PortLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;

/**
 * Bounty tasks from the OSRS Wiki, bundled by scripts/update_bounty_data.py (SPEC-routing.md §2.5): which
 * notice boards offer each monster's parts, and which board always does. Every board has one bounty it always
 * offers ("guaranteed"); the rest of its bounties are a random draw. Immutable after loading.
 */
@Slf4j
public final class BountyWikiData
{
	/** One bounty task row. */
	public static final class Task
	{
		public int taskId;
		public int level;
		public Integer xp;
		public PortLocation board;
		public String monster;
		public String item;
		public int qty;
		public String rarity;
		public boolean guaranteed;
		public BagSize bag;
	}

	/** One body part of a monster, and the boards that offer it. */
	public static final class Part
	{
		public final String monster;
		public final String item;
		/** Its tasks, guaranteed boards first, then by board name. */
		public final List<Task> tasks;

		Part(String monster, String item, List<Task> tasks)
		{
			this.monster = monster;
			this.item = item;
			this.tasks = tasks;
		}

		public boolean hasGuaranteed()
		{
			return tasks.stream().anyMatch(t -> t.guaranteed);
		}
	}

	/** One row of bounty_tasks.json. */
	static final class Row
	{
		int level;
		Integer xp;
		String noticeBoard;
		String monster;
		String item;
		int qty;
		String rarity;
		boolean guaranteed;
		String bag;
	}

	private static final class TasksFile
	{
		Map<String, Row> tasks;
	}

	/** Monster -> its parts, both in name order. */
	private final Map<String, List<Part>> byMonster;
	private final Map<Integer, Task> byId = new java.util.HashMap<>();

	private BountyWikiData(Map<String, List<Part>> byMonster)
	{
		this.byMonster = byMonster;
		byMonster.values().forEach(parts -> parts.forEach(p -> p.tasks.forEach(t -> byId.put(t.taskId, t))));
	}

	/** The task with this id (the PORT_TASK_SLOT_n_ID varbit value), or null. */
	public Task task(int taskId)
	{
		return byId.get(taskId);
	}

	/** Loads the bundled data; on failure returns empty data and logs, so the hunt degrades instead of breaking. */
	public static BountyWikiData load(Gson gson)
	{
		try (InputStream in = BountyWikiData.class.getResourceAsStream("/com/nucleon/porttasks/bounty_tasks.json"))
		{
			if (in == null)
			{
				throw new IOException("missing resource bounty_tasks.json");
			}
			TasksFile f;
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				f = gson.fromJson(reader, TasksFile.class);
			}
			return fromRows(f.tasks);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Failed to load bounty wiki data", e);
			return new BountyWikiData(Collections.emptyMap());
		}
	}

	static BountyWikiData fromRows(Map<String, Row> rows)
	{
		Map<String, Map<String, List<Task>>> grouped = new TreeMap<>();
		for (Map.Entry<String, Row> e : rows.entrySet())
		{
			Row r = e.getValue();
			PortLocation board = port(r.noticeBoard);
			if (board == null || r.monster == null || r.item == null)
			{
				log.debug("bounty task {}: unknown board {}", e.getKey(), r.noticeBoard);
				continue;
			}
			Task t = new Task();
			t.taskId = Integer.parseInt(e.getKey());
			t.level = r.level;
			t.xp = r.xp;
			t.board = board;
			t.monster = r.monster;
			t.item = r.item;
			t.qty = r.qty;
			t.rarity = r.rarity;
			t.guaranteed = r.guaranteed;
			t.bag = size(r.bag);
			grouped.computeIfAbsent(r.monster, k -> new TreeMap<>()).computeIfAbsent(r.item, k -> new ArrayList<>()).add(t);
		}
		Map<String, List<Part>> byMonster = new LinkedHashMap<>();
		grouped.forEach((monster, items) ->
		{
			List<Part> parts = new ArrayList<>();
			items.forEach((item, tasks) ->
			{
				tasks.sort(Comparator.comparing((Task t) -> !t.guaranteed).thenComparing(t -> t.board.getName()));
				parts.add(new Part(monster, item, Collections.unmodifiableList(tasks)));
			});
			byMonster.put(monster, Collections.unmodifiableList(parts));
		});
		return new BountyWikiData(Collections.unmodifiableMap(byMonster));
	}

	private static PortLocation port(String name)
	{
		for (PortLocation p : PortLocation.values())
		{
			if (p.getName().equals(name))
			{
				return p;
			}
		}
		return null;
	}

	private static BagSize size(String name)
	{
		for (BagSize s : BagSize.values())
		{
			if (s.wikiName().equals(name))
			{
				return s;
			}
		}
		return null;
	}

	/** Every monster with a bounty, in name order. */
	public List<String> monsters()
	{
		return new ArrayList<>(byMonster.keySet());
	}

	/** A monster's parts, in name order; empty if unknown. */
	public List<Part> parts(String monster)
	{
		return byMonster.getOrDefault(monster, Collections.emptyList());
	}

	/** Monster names from a comma-separated setting, matched without regard to case; unknown names dropped. */
	public List<String> parseMonsters(String setting)
	{
		TreeSet<String> out = new TreeSet<>();
		if (setting != null)
		{
			for (String s : setting.split(","))
			{
				for (String m : byMonster.keySet())
				{
					if (m.equalsIgnoreCase(s.trim()))
					{
						out.add(m);
					}
				}
			}
		}
		return new ArrayList<>(out);
	}
}
