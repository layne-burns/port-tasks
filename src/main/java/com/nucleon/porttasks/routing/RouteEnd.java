package com.nucleon.porttasks.routing;

import com.nucleon.porttasks.enums.PortLocation;

/** Where a planned route should finish (SPEC-routing.md §2.1, the sweep): anywhere, or a chosen far end. */
public enum RouteEnd
{
	ANYWHERE("Anywhere", null),
	LUNAR_ISLE("Lunar Isle", PortLocation.LUNAR_ISLE),
	DEEPFIN_POINT("Deepfin Point", PortLocation.DEEPFIN_POINT),
	RELLEKKA("Rellekka", PortLocation.RELLEKKA),
	NEITIZNOT("Neitiznot", PortLocation.NEITIZNOT),
	PISCATORIS("Piscatoris", PortLocation.PISCATORIS),
	PORT_ROBERTS("Port Roberts", PortLocation.PORT_ROBERTS),
	ALDARIN("Aldarin", PortLocation.ALDARIN),
	SUNSET_COAST("Sunset Coast", PortLocation.SUNSET_COAST),
	VOID_KNIGHTS_OUTPOST("Void Knights' Outpost", PortLocation.VOID_KNIGHTS_OUTPOST),
	RED_ROCK("Red Rock", PortLocation.RED_ROCK);

	private final String label;
	private final PortLocation port;

	RouteEnd(String label, PortLocation port)
	{
		this.label = label;
		this.port = port;
	}

	/** The end port, or null for an open route. */
	public PortLocation port()
	{
		return port;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
