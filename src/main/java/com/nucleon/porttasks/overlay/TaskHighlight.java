package com.nucleon.porttasks.overlay;

import com.nucleon.porttasks.CourierTaskData;
import com.nucleon.porttasks.OfferedTaskData;
import com.nucleon.porttasks.PortTasksPlugin;
import com.nucleon.porttasks.WidgetTag;
import com.nucleon.porttasks.enums.BountyTaskData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Marks the tasks on an open notice board: a border for tagged tasks, and a fill that dims hidden ones
 * (untagged, too high a level, bounty or courier, bag size filtered out) or colours bounty tasks you can't
 * take alongside one you hold. Which task gets what is worked out by {@link #marks} when the board, your
 * tasks, tags or settings change; only the drawing happens per frame.
 */
public class TaskHighlight extends Overlay
{
	/** How one offered task is marked. Immutable. */
	public static final class Mark
	{
		/** Tag border colour, or null. */
		final Color border;
		/** Fill (with its alpha), or null. */
		final Color fill;

		Mark(Color border, Color fill)
		{
			this.border = border;
			this.fill = fill;
		}
	}

	private static final Stroke TAG_STROKE = new BasicStroke(2);

	private final Client client;
	private final PortTasksPlugin plugin;

	@Inject
	private TaskHighlight(Client client, PortTasksPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;

		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** The marks for the tasks on the board now (dbrow -> mark; unmarked tasks are left out). Client thread. */
	public static Map<Integer, Mark> marks(PortTasksPlugin plugin)
	{
		Map<Integer, OfferedTaskData> offered = plugin.getOfferedTasks();
		if (offered.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<Integer, Color> tagColors = new HashMap<>();
		for (WidgetTag tag : plugin.getWidgetTags())
		{
			if (tag != null)
			{
				tagColors.put(tag.getDbrow(), tag.getColor());
			}
		}
		int alpha = plugin.getNoticeBoardHideOpactity();
		Color dim = new Color(0, 0, 0, alpha);
		Color conflictColor = plugin.getTaskConflictColor();
		Color conflict = new Color(conflictColor.getRed(), conflictColor.getGreen(), conflictColor.getBlue(), alpha);

		Map<Integer, Mark> marks = new HashMap<>();
		for (Map.Entry<Integer, OfferedTaskData> entry : offered.entrySet())
		{
			int dbrow = entry.getKey();
			BountyTaskData bounty = BountyTaskData.getByDbrow(dbrow);
			CourierTaskData courier = CourierTaskData.getByDbrow(dbrow);
			// You can't hold two bounty tasks for the same item. (Two couriers with the same cargo are fine.)
			boolean conflicts = bounty != null && plugin.getBountyTasks().stream()
				.anyMatch(task -> task.getData().getItemId() == bounty.getItemId());

			Color border = tagColors.get(dbrow);
			Color fill = null;
			if (border == null && plugin.isNoticeBoardHideUntagged())
			{
				fill = dim;
			}
			else if (plugin.isNoticeBoardHideIncompletable() && plugin.getSailingLevel() < entry.getValue().getLevelRequired())
			{
				fill = dim;
			}
			else if (plugin.isNoticeBoardHideBounty() && bounty != null)
			{
				fill = dim;
			}
			else if (courier != null && (plugin.isNoticeBoardHideCourier() || plugin.bagFilterHides(courier)))
			{
				fill = dim;
			}
			else if (plugin.isHighlightTaskConflicts() && conflicts)
			{
				fill = conflict;
			}
			if (border != null || fill != null)
			{
				marks.put(dbrow, new Mark(border, fill));
			}
		}
		return marks;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<Integer, Mark> marks = plugin.getBoardMarks();
		if (marks.isEmpty())
		{
			return null;
		}
		Widget taskBoard = client.getWidget(InterfaceID.PortTaskBoard.CONTAINER);
		if (taskBoard == null || taskBoard.isHidden())
		{
			return null;
		}
		Widget worldMap = client.getWidget(InterfaceID.Worldmap.CONTENT);
		Widget taskInfo = client.getWidget(InterfaceID.PortTaskInfo.WINDOW);
		if (worldMap != null && !worldMap.isHidden() || taskInfo != null && !taskInfo.isHidden())
		{
			return null;
		}
		for (Map.Entry<Integer, OfferedTaskData> entry : plugin.getOfferedTasks().entrySet())
		{
			Mark mark = marks.get(entry.getKey());
			Widget widget = entry.getValue().getTaskWidget();
			if (mark == null || widget == null || widget.isHidden())
			{
				continue;
			}
			Rectangle r = widget.getBounds();
			if (mark.fill != null)
			{
				graphics.setColor(mark.fill);
				graphics.fill(r);
			}
			if (mark.border != null)
			{
				graphics.setColor(mark.border);
				graphics.setStroke(TAG_STROKE);
				graphics.draw(r);
			}
		}
		return null;
	}
}
