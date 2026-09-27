package com.nucleon.porttasks.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import javax.swing.JLabel;

/**
 * Routing extension: a label with several versions of its text (e.g. full and abbreviated port names) that
 * shows the longest one fitting its width. The choice is made when the label is laid out (resized), not
 * when it is painted. It asks only for the width of its shortest version, so it never widens the panel.
 */
class FitLabel extends JLabel
{
	private String[] versions = {""};

	FitLabel()
	{
		// Same as the JPanels it's stacked with; a left-aligned label in a vertical BoxLayout of centred panels
		// is placed from the middle and only gets half the width. The text itself stays left-aligned.
		setAlignmentX(CENTER_ALIGNMENT);
	}

	void setVersions(String... texts)
	{
		versions = texts;
		choose(getWidth());
	}

	@Override
	public void setBounds(int x, int y, int width, int height)
	{
		super.setBounds(x, y, width, height);
		choose(width);
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension d = super.getPreferredSize();
		FontMetrics fm = getFontMetrics(getFont());
		Insets in = getInsets();
		int shortest = Integer.MAX_VALUE;
		for (String v : versions)
		{
			shortest = Math.min(shortest, fm.stringWidth(v));
		}
		return new Dimension(shortest + in.left + in.right, d.height);
	}

	/** May stretch across the panel (a vertical BoxLayout never widens a component past its maximum). */
	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private void choose(int width)
	{
		FontMetrics fm = getFontMetrics(getFont());
		Insets in = getInsets();
		int room = width - in.left - in.right;
		String best = null;
		int bestWidth = -1;
		String shortest = versions[0];
		int shortestWidth = Integer.MAX_VALUE;
		for (String v : versions)
		{
			int w = fm.stringWidth(v);
			if (w <= room && w > bestWidth)
			{
				best = v;
				bestWidth = w;
			}
			if (w < shortestWidth)
			{
				shortest = v;
				shortestWidth = w;
			}
		}
		String text = best != null ? best : shortest;
		// setText re-lays out the panel, which comes back here with the same width and changes nothing.
		if (!text.equals(getText()))
		{
			setText(text);
		}
	}
}
