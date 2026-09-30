package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The loop (SPEC-routing.md §2.4): the few ports the player sails between, typed as a list of port names
 * separated by commas or new lines. A name matches a port by its full name, with or without "The", or by the
 * start of it when only one port starts that way ("Deepfin", "Lunar"). Immutable.
 */
public final class LoopPorts
{
	public static final LoopPorts NONE = new LoopPorts(EnumSet.noneOf(PortLocation.class), Collections.emptyList());

	private final Set<PortLocation> ports;
	/** Names that matched no port, or more than one, to show the player. */
	private final List<String> unknown;

	private LoopPorts(Set<PortLocation> ports, List<String> unknown)
	{
		this.ports = ports;
		this.unknown = unknown;
	}

	public static LoopPorts parse(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return NONE;
		}
		Set<PortLocation> ports = EnumSet.noneOf(PortLocation.class);
		List<String> unknown = new ArrayList<>();
		for (String part : text.split("[,\\n]"))
		{
			String name = part.trim();
			if (name.isEmpty())
			{
				continue;
			}
			PortLocation p = match(name);
			if (p == null)
			{
				unknown.add(name);
			}
			else
			{
				ports.add(p);
			}
		}
		return new LoopPorts(ports, Collections.unmodifiableList(unknown));
	}

	/** The port this name means, or null if none or several. */
	static PortLocation match(String name)
	{
		String want = norm(name);
		PortLocation prefix = null;
		int prefixMatches = 0;
		for (PortLocation p : PortLocation.values())
		{
			if (p == PortLocation.EMPTY)
			{
				continue;
			}
			String full = norm(p.getName());
			if (full.equals(want))
			{
				return p;
			}
			if (full.startsWith(want))
			{
				prefix = p;
				prefixMatches++;
			}
		}
		return prefixMatches == 1 ? prefix : null;
	}

	private static String norm(String s)
	{
		return s.trim().toLowerCase(Locale.ROOT).replaceFirst("^the ", "");
	}

	/** True if a loop is set. */
	public boolean active()
	{
		return !ports.isEmpty();
	}

	public boolean contains(PortLocation port)
	{
		return ports.contains(port);
	}

	/** True if the loop is set and the task's pickup and delivery both lie in it. */
	public boolean holds(PortLocation pickup, PortLocation delivery)
	{
		return active() && ports.contains(pickup) && ports.contains(delivery);
	}

	public Set<PortLocation> ports()
	{
		return Collections.unmodifiableSet(ports);
	}

	public List<String> unknown()
	{
		return unknown;
	}

	@Override
	public String toString()
	{
		return ports.toString();
	}
}
