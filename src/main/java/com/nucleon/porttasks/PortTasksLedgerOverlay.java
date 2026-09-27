/*
 * Copyright (c) 2025, nucleon <https://github.com/nucleon>
 * Copyright (c) 2025, Cooper Morris <https://github.com/coopermor>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nucleon.porttasks;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Dock ledger tables used by a held task: the tile is outlined in the colours of the tasks with work left
 * there, with "Cargo: taken/needed" (pickup) or "Delivered: n/needed" (delivery) per task, stacked. What to
 * show is worked out in PortView when tasks change; only the positions are computed here, since the camera
 * moves every frame.
 */
class PortTasksLedgerOverlay extends Overlay
{
	private static final Color FILL = new Color(0, 0, 0, 50);
	private static final Stroke EDGE = new BasicStroke(2);

	private final Client client;
	private final PortTasksPlugin plugin;

	@Inject
	private PortTasksLedgerOverlay(Client client, PortTasksPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		PortView view = plugin.view();
		if (view.ledgerLabels.isEmpty())
		{
			return null;
		}
		for (GameObject ledger : plugin.getLedgers())
		{
			List<PortView.LedgerLabel> labels = view.ledgerLabels.get(ledger.getId());
			if (labels == null)
			{
				continue;
			}
			ObjectComposition comp = client.getObjectDefinition(ledger.getId());
			Polygon poly = Perspective.getCanvasTileAreaPoly(client, ledger.getLocalLocation(), comp.getSizeX());
			if (poly != null)
			{
				renderMultiColoredSquare(g, poly, labels);
			}
			for (int i = 0; i < labels.size(); i++)
			{
				String text = labels.get(i).text;
				Point at = Perspective.getCanvasTextLocation(client, g, ledger.getLocalLocation(), text, 0);
				if (at != null)
				{
					OverlayUtil.renderTextLocation(g, new Point(at.getX(), at.getY() - 15 * i), text, Color.WHITE);
				}
			}
		}
		return null;
	}

	/** Fills the tile lightly and splits its edges between the tasks' colours. */
	private static void renderMultiColoredSquare(Graphics2D g, Polygon poly, List<PortView.LedgerLabel> labels)
	{
		if (poly.npoints < 2)
		{
			return;
		}
		g.setColor(FILL);
		g.fillPolygon(poly);
		g.setStroke(EDGE);
		int n = poly.npoints;
		int perColour = n / labels.size();
		int extra = n % labels.size();
		int edge = 0;
		for (int c = 0; c < labels.size(); c++)
		{
			g.setColor(labels.get(c).colour);
			int count = perColour + (c < extra ? 1 : 0);
			for (int i = 0; i < count; i++, edge++)
			{
				int a = edge % n;
				int b = (edge + 1) % n;
				g.drawLine(poly.xpoints[a], poly.ypoints[a], poly.xpoints[b], poly.ypoints[b]);
			}
		}
	}
}
