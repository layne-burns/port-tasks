package com.nucleon.porttasks.ui;

import com.nucleon.porttasks.PortTasksConfig;
import com.nucleon.porttasks.PortTasksPlugin;
import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.routing.LoopPorts;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Routing extension: the loop settings as tick boxes, one row per port and one column per setting (in the loop,
 * gather by sea only, left out of loop suggestions), so port names never have to be typed. The settings stay
 * comma-separated port names in the config, which the boxes write through the plugin; loop suggestions write the
 * same setting, and {@link #sync()} keeps the boxes in step. Swing thread only.
 */
class LoopPortEditor extends JPanel
{
	/** One column: the config key it writes, its header and tooltip, and the setting's current value. */
	enum Column
	{
		LOOP("routingLoop", "Loop", "Ports you sail between. Board tasks that stay inside the loop rank first,"
			+ " and the best set only takes them. Boards are gathered in the order you tick them", PortTasksConfig::routingLoop),
		SEA("routingLoopSeaOnly", "Sea", "Gather by sea only: this loop board isn't teleported to while gathering;"
			+ " you look at it when the route docks there (e.g. Lunar Isle)", PortTasksConfig::routingLoopSeaOnly),
		SKIP("routingLoopExclude", "Skip", "Leave this port out of loop suggestions (e.g. one you can't reach yet)."
			+ " Ports above your Sailing level or behind an unfinished quest are left out anyway", PortTasksConfig::routingLoopExclude);

		final String key;
		final String header;
		final String tip;
		final Function<PortTasksConfig, String> value;

		Column(String key, String header, String tip, Function<PortTasksConfig, String> value)
		{
			this.key = key;
			this.header = header;
			this.tip = tip;
			this.value = value;
		}
	}

	private final PortTasksConfig config;
	private final Map<Column, Map<PortLocation, JCheckBox>> boxes = new EnumMap<>(Column.class);

	LoopPortEditor(PortTasksPlugin plugin, PortTasksConfig config)
	{
		this.config = config;
		setLayout(new GridBagLayout());
		setBorder(new EmptyBorder(2, 0, 4, 0));
		setAlignmentX(LEFT_ALIGNMENT);

		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.insets = new Insets(0, 0, 1, 0);
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		add(small(new JLabel("Port"), Color.GRAY), c);
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		for (Column col : Column.values())
		{
			c.gridx++;
			JLabel header = small(new JLabel(col.header, SwingConstants.CENTER), Color.GRAY);
			header.setToolTipText("<html><p width=200>" + col.tip + "</p></html>");
			header.setBorder(new EmptyBorder(0, 3, 0, 3));
			add(header, c);
			boxes.put(col, new EnumMap<>(PortLocation.class));
		}

		List<PortLocation> ports = new ArrayList<>();
		for (PortLocation p : PortLocation.values())
		{
			if (p != PortLocation.EMPTY)
			{
				ports.add(p);
			}
		}
		ports.sort(Comparator.comparing(PortNames::full));
		for (PortLocation p : ports)
		{
			c.gridy++;
			c.gridx = 0;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			boolean board = p.getNoticeboardObject() != -1;
			FitLabel name = new FitLabel();
			small(name, Color.WHITE);
			name.setVersions(PortNames.full(p), PortNames.abbreviation(p));
			name.setToolTipText(p.getName() + (board ? "" : " (no notice board)"));
			add(name, c);
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			for (Column col : Column.values())
			{
				c.gridx++;
				JCheckBox box = new JCheckBox();
				box.setFocusable(false);
				box.setBorder(new EmptyBorder(0, 0, 0, 0));
				box.setToolTipText(col.header + ": " + PortNames.full(p));
				if (col == Column.SEA && !board)
				{
					// Nothing to gather at a port without a notice board.
					box.setEnabled(false);
					box.setToolTipText("No notice board here");
				}
				box.addActionListener(e -> plugin.setLoopPort(col.key, p, box.isSelected()));
				boxes.get(col).put(p, box);
				add(box, c);
			}
		}
		sync();
	}

	/** Ticks the boxes from the settings (setSelected fires no action, so nothing is written back). */
	void sync()
	{
		for (Column col : Column.values())
		{
			LoopPorts set = LoopPorts.parse(col.value.apply(config));
			for (Map.Entry<PortLocation, JCheckBox> e : boxes.get(col).entrySet())
			{
				e.getValue().setSelected(set.contains(e.getKey()));
			}
		}
	}

	private static <T extends JLabel> T small(T label, Color colour)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}
}
