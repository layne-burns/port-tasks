package com.nucleon.porttasks.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Routing extension: a held task as one line (arrow, overlay colour, route or name, progress). Clicking the
 * line opens the original full panel underneath. Swing thread only.
 */
class TaskRow extends JPanel
{
	private final JLabel arrow = new JLabel();
	private final FitLabel title = new FitLabel();
	private final JLabel progress = new JLabel();
	private final Component full;

	/**
	 * @param full      the original panel, shown when open
	 * @param colour    the task's overlay colour, read at paint time so a colour picked in the full panel shows
	 * @param open      whether the row starts open
	 * @param onToggle  told the new open state when the row is clicked
	 */
	TaskRow(JPanel full, Supplier<Color> colour, boolean open, Consumer<Boolean> onToggle)
	{
		this.full = full;
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
		setOpen(open);
	}

	/**
	 * Sets the one-line text: what the task is (the longest of the given versions that fits), and how far
	 * along it is.
	 */
	void setSummary(String[] text, String tooltip, String progressText, Color progressColour)
	{
		title.setVersions(text);
		title.setToolTipText(tooltip);
		progress.setToolTipText(tooltip);
		progress.setText(progressText);
		progress.setForeground(progressColour);
	}

	private void setOpen(boolean open)
	{
		full.setVisible(open);
		arrow.setText(open ? "▾" : "▸");
		revalidate();
		repaint();
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
