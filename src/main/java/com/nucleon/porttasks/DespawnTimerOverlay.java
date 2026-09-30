package com.nucleon.porttasks;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

@Slf4j
public class DespawnTimerOverlay extends Overlay
{
	private final Client client;
	private final PortTasksPlugin plugin;
	private final PortTasksConfig config;

	@Inject
	private DespawnTimerOverlay(Client client, PortTasksPlugin plugin, PortTasksConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		renderDeadNpcTimer(graphics);
		return null;
	}

	private boolean isOnTask(int id)
	{
		return plugin.getBountyTasks().stream()
			.anyMatch(task -> task.getData().getDeadNpcId() == id);
	}

	private void renderDeadNpcTimer(Graphics2D graphics)
	{
		Instant now = Instant.now();
		final int tickCount = client.getTickCount();
		for (BountyCorpse tracker : plugin.bountyCorpses)
		{
			if (tracker.getNpc() == null)
			{
				continue;
			}

			if (config.corpseOverlayNpcs() == PortTasksConfig.Npcs.TASK && !isOnTask(tracker.getNpc().getId()))
			{
				continue;
			}

			if (config.corpseOverlay() == PortTasksConfig.Despawn.PIE)
			{
				float percent = ((float)tracker.getDespawnTime() - (now.toEpochMilli() - tracker.getStartTime().toEpochMilli())) / ((float)tracker.getDespawnTime());
				// Routing extension: drawn above the model's head, not at its feet, where the corpse's model covered it.
				Point point = tracker.getNpc().getCanvasTextLocation(graphics, "", aboveModel(tracker));

				if (point == null || percent > 1.0f)
				{
					continue;
				}

				Color lerpedColor = lerpColor(Color.RED, Color.YELLOW, percent);
				ProgressPieComponent ppc = new ProgressPieComponent();
				ppc.setBorderColor(lerpedColor.darker());
				ppc.setFill(lerpedColor);
				ppc.setPosition(point);
				ppc.setProgress(percent);
				ppc.render(graphics);
			}
			else if (config.corpseOverlay() == PortTasksConfig.Despawn.TICKS)
			{
				// Routing extension: counts from the corpse's own lifetime (200 ticks), not a fixed 300.
				int lifeTicks = tracker.getDespawnTime() / Constants.GAME_TICK_LENGTH;
				int ticksRemaining = lifeTicks - (tickCount - tracker.getTickCount());
				if (ticksRemaining < 0 || ticksRemaining > lifeTicks)
				{
					continue;
				}

				Point point = tracker.getNpc().getCanvasTextLocation(graphics, String.valueOf(ticksRemaining), aboveModel(tracker));

				if (point == null)
				{
					continue;
				}

				OverlayUtil.renderTextLocation(graphics, point, String.valueOf(ticksRemaining), ticksRemaining > 30 ? Color.WHITE : Color.RED);
			}
		}
	}

	/** Height to draw at: just above the corpse's model (its logical height, plus a margin). */
	private static int aboveModel(BountyCorpse tracker)
	{
		return tracker.getNpc().getLogicalHeight() + 40;
	}

	private Color lerpColor(Color start, Color end, float percent)
	{
		return new Color(
			lerp(start.getRed(), end.getRed(), percent),
			lerp(start.getGreen(), end.getGreen(), percent),
			lerp(start.getBlue(), end.getBlue(), percent),
			lerp(start.getAlpha(), end.getAlpha(), percent)
		);
	}

	private int lerp(int start, int end, float percent)
	{
		float clamped = Math.max(0f, Math.min(1f, percent));
		return (int) Math.round(start + (end - start) * clamped);
	}
}
