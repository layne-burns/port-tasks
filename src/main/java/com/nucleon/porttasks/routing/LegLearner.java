package com.nucleon.porttasks.routing;

import com.google.gson.Gson;
import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Leg lengths learned from sailing (LegTracker), kept in the profile: for each pair of ports, either
 * direction, the last {@value #KEEP} legs (tiles and ticks). The route graph plans with their minimum (the
 * best run: the boat only gets faster) or their median (a typical run), per the setting, in place of the
 * hand-drawn path's length. A leg more than twice the current estimate is ignored as a detour. Client
 * thread only.
 */
@Slf4j
public final class LegLearner
{
	/** How a pair's samples become one distance (config). */
	public enum Estimate
	{
		MINIMUM("Minimum (best run)"),
		MEDIAN("Median (typical run)");

		private final String label;

		Estimate(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** One pair's estimate. Immutable. */
	public static final class Learned
	{
		public final PortLocation a;
		public final PortLocation b;
		public final double tiles;
		public final double ticks;
		public final int samples;

		Learned(PortLocation a, PortLocation b, double tiles, double ticks, int samples)
		{
			this.a = a;
			this.b = b;
			this.tiles = tiles;
			this.ticks = ticks;
			this.samples = samples;
		}
	}

	/** Pair key ("PORT_SARIM|PANDEMONIUM", in enum order) -> legs as {tiles, ticks}, oldest first. */
	private static final class Stored extends HashMap<String, List<double[]>>
	{
	}

	static final int KEEP = 25;
	static final double MAX_OVER_ESTIMATE = 2;

	private final Gson gson;
	private final Consumer<String> save;
	private final Stored samples = new Stored();

	/**
	 * @param stored what {@code save} last received, or null
	 * @param save   stores the samples as JSON (the profile)
	 */
	public LegLearner(String stored, Gson gson, Consumer<String> save)
	{
		this.gson = gson;
		this.save = save;
		if (stored != null)
		{
			try
			{
				Stored s = gson.fromJson(stored, Stored.class);
				if (s != null)
				{
					samples.putAll(s);
				}
			}
			catch (RuntimeException e)
			{
				log.debug("learned legs unreadable; starting empty", e);
			}
		}
	}

	/**
	 * Adds a sailed leg, unless it is over twice {@code estimate} (the current planning distance for the pair;
	 * infinite if none). Returns whether it was kept.
	 */
	public boolean record(LegTracker.Leg leg, double estimate)
	{
		if (Double.isFinite(estimate) && leg.tiles > MAX_OVER_ESTIMATE * estimate)
		{
			log.info("[routing] leg {} -> {}: {} tiles is over {}x the estimate {}; ignored as a detour",
				leg.from, leg.to, Math.round(leg.tiles), MAX_OVER_ESTIMATE, Math.round(estimate));
			return false;
		}
		List<double[]> list = samples.computeIfAbsent(key(leg.from, leg.to), k -> new ArrayList<>());
		list.add(new double[]{leg.tiles, leg.ticks});
		while (list.size() > KEEP)
		{
			list.remove(0);
		}
		save.accept(gson.toJson(samples));
		log.info("[routing] leg {} -> {}: {} tiles, {} ticks (estimate was {})",
			leg.from, leg.to, Math.round(leg.tiles), leg.ticks, Math.round(estimate));
		return true;
	}

	/** Every pair with at least one leg, as one distance each. */
	public List<Learned> estimates(Estimate how)
	{
		List<Learned> out = new ArrayList<>();
		for (Map.Entry<String, List<double[]>> e : samples.entrySet())
		{
			String[] ports = e.getKey().split("\\|");
			List<double[]> legs = e.getValue();
			if (legs.isEmpty())
			{
				continue;
			}
			double[] tiles = new double[legs.size()];
			double[] ticks = new double[legs.size()];
			for (int i = 0; i < legs.size(); i++)
			{
				tiles[i] = legs.get(i)[0];
				ticks[i] = legs.get(i)[1];
			}
			out.add(new Learned(PortLocation.valueOf(ports[0]), PortLocation.valueOf(ports[1]),
				estimate(tiles, how), estimate(ticks, how), legs.size()));
		}
		return out;
	}

	/** Number of legs kept for this pair (either direction). */
	public int samples(PortLocation a, PortLocation b)
	{
		return samples.getOrDefault(key(a, b), Collections.emptyList()).size();
	}

	public void clear()
	{
		samples.clear();
		save.accept(null);
	}

	static double estimate(double[] values, Estimate how)
	{
		double[] v = values.clone();
		Arrays.sort(v);
		if (how == Estimate.MINIMUM)
		{
			return v[0];
		}
		int n = v.length;
		return n % 2 == 1 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2;
	}

	private static String key(PortLocation a, PortLocation b)
	{
		return a.ordinal() <= b.ordinal() ? a.name() + "|" + b.name() : b.name() + "|" + a.name();
	}
}
