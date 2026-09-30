package com.nucleon.porttasks;

import com.nucleon.porttasks.routing.BountySpawns;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Routing extension (SPEC-routing.md §2.5.2): outlines the tiles of the bounty safespot being sailed to, on the
 * sea (in the top-level world view, not the boat's). The safespot is chosen on events; this only draws it, and
 * only the few tiles of one rectangle, when it is in the scene.
 */
class RoutingSafespotOverlay extends Overlay
{
	private static final Color COLOUR = new Color(0, 220, 255);

	private final Client client;
	private final PortTasksPlugin plugin;

	@Inject
	private RoutingSafespotOverlay(Client client, PortTasksPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		BountySpawns.Area area = plugin.seaArea();
		if (area == null || area.safespot == null)
		{
			return null;
		}
		WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return null;
		}
		int[] r = area.safespot;
		graphics.setStroke(new BasicStroke(2));
		LocalPoint label = null;
		for (int x = r[0]; x < r[0] + r[2]; x++)
		{
			for (int y = r[1]; y < r[1] + r[3]; y++)
			{
				LocalPoint lp = LocalPoint.fromWorld(top, new WorldPoint(x, y, top.getPlane()));
				if (lp == null)
				{
					continue;
				}
				Polygon poly = Perspective.getCanvasTilePoly(client, lp);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(graphics, poly, COLOUR);
					label = label == null ? lp : label;
				}
			}
		}
		if (label != null)
		{
			net.runelite.api.Point p = Perspective.getCanvasTextLocation(client, graphics, label, "Safespot", 0);
			if (p != null)
			{
				OverlayUtil.renderTextLocation(graphics, p, "Safespot", COLOUR);
			}
		}
		return null;
	}
}
