package com.nucleon.porttasks.ui;

import com.nucleon.porttasks.enums.PortLocation;

/**
 * Routing extension: port names for the side panel. Each port has its full name and an abbreviation; a line
 * shows the longest combination that fits (see FitLabel). Every abbreviation is at most 47 px in the
 * RuneScape small font, so any two fit a held task's line (106 px, with " > " taking 12); PortNamesTest
 * checks this.
 */
final class PortNames
{
	private PortNames()
	{
	}

	/** The name as players say it: without "The", and the two long ones cut to their usual form. */
	static String full(PortLocation port)
	{
		switch (port)
		{
			case VOID_KNIGHTS_OUTPOST:
				return "Void Outpost";
			case CIVITAS_ILLA_FORTIS:
				return "Civitas";
			default:
				return port.getName().replaceFirst("^The ", "");
		}
	}

	static String abbreviation(PortLocation port)
	{
		switch (port)
		{
			case MUSA_POINT:
				return "Musa Pt";
			case PANDEMONIUM:
				return "Pandem.";
			case RUINS_OF_UNKAH:
				return "Unkah";
			case ARDOUGNE:
				return "Ardoug.";
			case BRIMHAVEN:
				return "Brimhvn";
			case CORSAIR_COVE:
				return "Corsair";
			case DEEPFIN_POINT:
				return "Deepfin";
			case SUNSET_COAST:
				return "Sunset";
			case SUMMER_SHORE:
				return "Summer";
			case VOID_KNIGHTS_OUTPOST:
				return "Void";
			case LANDS_END:
				return "Land's E.";
			case PORT_PISCARILIUS:
				return "Pisc.";
			case CAIRN_ISLE:
				return "Cairn";
			case PRIFDDINAS:
				return "Prif.";
			case PISCATORIS:
				return "Piscat.";
			case LUNAR_ISLE:
				return "Lunar";
			default:
				return full(port).replaceFirst("^Port ", "");
		}
	}

	/** "A > B" with full names, then with one or both abbreviated; FitLabel picks the longest that fits. */
	static String[] route(String prefix, PortLocation from, PortLocation to)
	{
		return new String[]{
			prefix + full(from) + " > " + full(to),
			prefix + abbreviation(from) + " > " + full(to),
			prefix + full(from) + " > " + abbreviation(to),
			prefix + abbreviation(from) + " > " + abbreviation(to),
		};
	}
}
