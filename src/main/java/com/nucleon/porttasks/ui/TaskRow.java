package com.nucleon.porttasks.ui;

import com.nucleon.porttasks.PortTasksPlugin;
import com.nucleon.porttasks.Task;
import com.nucleon.porttasks.enums.PortLocation;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * Routing extension: a held task as one line (arrow, overlay colour, short route or name, progress, show/hide
 * eye). Clicking the line opens the original full panel underneath. Swing thread only.
 */
class TaskRow extends JPanel
{
	private static final ImageIcon VISIBLE_ICON;
	private static final ImageIcon INVISIBLE_ICON;

	static
	{
		BufferedImage visible = ImageUtil.loadImageResource(PortTasksPlugin.class, "visible_icon.png");
		VISIBLE_ICON = new ImageIcon(visible);
		BufferedImage invisible = ImageUtil.loadImageResource(PortTasksPlugin.class, "invisible_icon.png");
		INVISIBLE_ICON = new ImageIcon(invisible);
	}

	private final JLabel arrow = new JLabel();
	private final JLabel title = new JLabel();
	private final JLabel progress = new JLabel();
	private final JLabel eye = new JLabel();
	private final Component full;
	private final Task task;

	/**
	 * @param full      the original panel, shown when open; it must implement TaskPanel so its own eye
	 *                  icon stays in step with this row's
	 * @param fullEye   the full panel's own show/hide eye, so a click there updates this row's eye too
	 * @param colour    the task's overlay colour, read at paint time so a colour picked in the full panel shows
	 * @param open      whether the row starts open
	 * @param onToggle  told the new open state when the row is clicked
	 */
	TaskRow(JPanel full, JLabel fullEye, Task task, Supplier<Color> colour, boolean open, Consumer<Boolean> onToggle,
		PortTasksPlugin plugin)
	{
		this.full = full;
		this.task = task;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(3, 4, 3, 4));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		left.setOpaque(false);
		arrow.setForeground(Color.GRAY);
		left.add(arrow);
		left.add(new JLabel(new Swatch(colour)));

		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(Color.WHITE);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		right.setOpaque(false);
		progress.setFont(FontManager.getRunescapeSmallFont());
		right.add(progress);
		right.add(eye);

		header.add(left, BorderLayout.WEST);
		header.add(title, BorderLayout.CENTER);
		header.add(right, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);
		add(full, BorderLayout.CENTER);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				setOpen(!TaskRow.this.full.isVisible());
				onToggle.accept(TaskRow.this.full.isVisible());
			}
		});
		eye.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				task.setTracking(!task.isTracking());
				((TaskPanel) full).updateVisibility();
				updateEye();
				plugin.saveSlotSettings();
				e.consume();
			}
		});
		fullEye.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				// After the full panel's own listener has flipped the task.
				SwingUtilities.invokeLater(TaskRow.this::updateEye);
			}
		});
		setOpen(open);
		updateEye();
	}

	/** Sets the one-line text: what the task is, and how far along it is. */
	void setSummary(String text, String tooltip, String progressText, Color progressColour)
	{
		title.setText(text);
		title.setToolTipText(tooltip);
		progress.setToolTipText(tooltip);
		progress.setText(progressText);
		progress.setForeground(progressColour);
	}

	/** Keeps this row's eye in step after the full panel's eye was clicked. */
	void updateEye()
	{
		eye.setIcon(task.isTracking() ? VISIBLE_ICON : INVISIBLE_ICON);
		eye.setToolTipText(task.isTracking() ? "Hide task" : "Show task");
	}

	private void setOpen(boolean open)
	{
		full.setVisible(open);
		arrow.setText(open ? "▾" : "▸");
		revalidate();
		repaint();
	}

	/**
	 * Port names cut to the word players use (Sarim, Summer, Void...), so two fit on one line of the side
	 * panel next to the progress and eye.
	 */
	static String shortName(PortLocation port)
	{
		switch (port)
		{
			case CIVITAS_ILLA_FORTIS:
				return "Civitas";
			case VOID_KNIGHTS_OUTPOST:
				return "Void";
			case RUINS_OF_UNKAH:
				return "Unkah";
			case SUMMER_SHORE:
			case DEEPFIN_POINT:
			case SUNSET_COAST:
			case CORSAIR_COVE:
			case MUSA_POINT:
			case CAIRN_ISLE:
			case LUNAR_ISLE:
				return port.getName().replaceFirst("^The ", "").split(" ")[0];
			default:
				return port.getName().replaceFirst("^(The|Port) ", "");
		}
	}

	/** A small square in the task's overlay colour. */
	private static final class Swatch implements Icon
	{
		private static final int SIZE = 8;
		private final Supplier<Color> colour;

		Swatch(Supplier<Color> colour)
		{
			this.colour = colour;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Color col = colour.get();
			g.setColor(col == null ? Color.RED : col);
			g.fillRect(x, y, SIZE, SIZE);
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
