package com.nucleon.porttasks.ui;

import com.nucleon.porttasks.enums.PortLocation;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PortNamesTest
{
	/** A held task's route has 106 px in the side panel and " > " takes 12, so each abbreviation gets 47. */
	private static final int MAX_ABBREVIATION_PX = 47;

	@Test
	public void everyAbbreviationFitsHalfATaskLine()
	{
		FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics()
			.getFontMetrics(FontManager.getRunescapeSmallFont());
		for (PortLocation p : PortLocation.values())
		{
			if (p == PortLocation.EMPTY)
			{
				continue;
			}
			String a = PortNames.abbreviation(p);
			assertTrue(p + " -> '" + a + "' is " + fm.stringWidth(a) + " px", fm.stringWidth(a) <= MAX_ABBREVIATION_PX);
			assertTrue(p + " abbreviation longer than its name", a.length() <= PortNames.full(p).length());
		}
	}
}
