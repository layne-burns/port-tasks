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
package com.nucleon.porttasks.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

import com.nucleon.porttasks.BountyTask;
import com.nucleon.porttasks.CourierTask;
import com.nucleon.porttasks.PortTasksConfig;
import com.nucleon.porttasks.PortTasksPlugin;
import com.nucleon.porttasks.Task;
import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.routing.BagSize;
import com.nucleon.porttasks.routing.BoardScorer;
import com.nucleon.porttasks.ui.adapters.ReloadPortTasks;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;


public class PortTasksPluginPanel extends PluginPanel
{
		private static final ImageIcon RELOAD_ICON;
		private final PluginErrorPanel errorPanel = new PluginErrorPanel();
		public final PortTasksPlugin plugin;
		private final PortTasksConfig config;
		private final JPanel markerView = new JPanel();
		// Routing extension: the last notice board's offered tasks, ranked.
		private final JPanel boardView = new JPanel();
		// Routing extension: one box per bag size, mirroring the bag-filter config toggles.
		private final Map<BagSize, JCheckBox> bagBoxes = new EnumMap<>(BagSize.class);
		// Routing extension: which task rows are open ("c"/"b" + task dbrow), kept across rebuilds.
		private final Set<String> openRows = new HashSet<>();
		private final Map<Integer, BountyRow> bountyRows = new HashMap<>();
		private ClientThread clientThread;
		private ItemManager itemManager;
		private Client client;

		static
		{
			final BufferedImage addIcon = ImageUtil.loadImageResource(PortTasksPlugin.class, "reload.png");
			RELOAD_ICON = new ImageIcon(addIcon);
		}

		public PortTasksPluginPanel(PortTasksPlugin plugin, ClientThread clientThread, ItemManager itemManager, Client client, PortTasksConfig config)
		{
			this.plugin = plugin;
			this.config = config;
			this.clientThread = clientThread;
			this.itemManager = itemManager;
			this.client = client;
			setLayout(new BorderLayout());
			setBorder(new EmptyBorder(10, 10, 10, 10));
			setupErrorPanel(true);

			// title panel
			JPanel northPanel = new JPanel(new BorderLayout());
			northPanel.setBorder(new EmptyBorder(1, 0, 10, 0));

			JPanel titlePanel = new JPanel(new BorderLayout());
			titlePanel.setBorder(new EmptyBorder(1, 3, 10, 7));

			JLabel title = new JLabel("Port Tasks", SwingConstants.CENTER);
			title.setHorizontalAlignment(SwingConstants.CENTER);
			title.setForeground(Color.WHITE);

			JLabel markerAdd = new JLabel(RELOAD_ICON);
			markerAdd.setToolTipText("reload");
			markerAdd.addMouseListener(new ReloadPortTasks(markerAdd, plugin, clientThread, this::addMarker));

			JPanel markerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 3));
			markerButtons.add(markerAdd);

			titlePanel.add(title, BorderLayout.WEST);
			titlePanel.add(markerButtons, BorderLayout.EAST);
			northPanel.add(titlePanel, BorderLayout.NORTH);

			// T S M L H: which bag sizes to offer; the others are dimmed on the board and not ranked.
			JPanel bagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			JLabel bagLabel = new JLabel("Bags ");
			bagLabel.setToolTipText("Courier tasks to offer, by bag size");
			bagRow.add(bagLabel);
			for (BagSize size : BagSize.values())
			{
				JCheckBox box = new JCheckBox(size.wikiName().substring(0, 1), BoardScorer.bagEnabled(config, size));
				box.setToolTipText(size.wikiName() + " bag tasks");
				box.setFocusable(false);
				box.addActionListener(e -> plugin.setBagEnabled(size, box.isSelected()));
				bagBoxes.put(size, box);
				bagRow.add(box);
			}
			northPanel.add(bagRow, BorderLayout.SOUTH);

			// marker view panels, these are dynamically added in rebuild()
			JPanel centerPanel = new JPanel(new BorderLayout());
			centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

			markerView.setLayout(new BoxLayout(markerView, BoxLayout.Y_AXIS));
			markerView.setBackground(ColorScheme.DARK_GRAY_COLOR);
			markerView.add(errorPanel);

			centerPanel.add(markerView, BorderLayout.NORTH);

			boardView.setLayout(new BoxLayout(boardView, BoxLayout.Y_AXIS));
			boardView.setBackground(ColorScheme.DARK_GRAY_COLOR);
			centerPanel.add(boardView, BorderLayout.CENTER);

			// setup panels border layout
			add(northPanel, BorderLayout.NORTH);
			add(centerPanel, BorderLayout.CENTER);
		}

//		public void rebuild()
//		{
//			markerView.removeAll();
//			List<CourierTask> courierTasks = plugin.getCourierTasks();
//			for (CourierTask courierTask : courierTasks)
//			{
//				markerView.add(new CourierTaskPanel(plugin, courierTask, clientThread, itemManager, courierTask.getSlot()));
//				markerView.add(Box.createRigidArea(new Dimension(0, 10)));
//			}
//			List<BountyTask> bountyTasks = plugin.getBountyTasks();
//
//			for (BountyTask bountyTask : bountyTasks)
//			{
//				markerView.add(new BountyTaskPanel(plugin, bountyTask, clientThread, itemManager, bountyTask.getSlot()));
//				markerView.add(Box.createRigidArea(new Dimension(0, 10)));
//			}
//
//			if (courierTasks.isEmpty() || bountyTasks.isEmpty())
//			{
//				setupErrorPanel(true);
//			}
//			repaint();
//			revalidate();
//		}

		public void rebuild()
		{
			markerView.removeAll();
			bountyRows.clear();
			List<Task> allTasks = new ArrayList<>();
			allTasks.addAll(plugin.getCourierTasks());
			allTasks.addAll(plugin.getBountyTasks());
			allTasks.sort(Comparator.comparingInt(Task::getSlot));
			for (Task task : allTasks)
			{
				// Routing extension: each task is one line; clicking it opens the original panel below.
				TaskRow row = null;
				if (task instanceof CourierTask)
				{
					CourierTask courier = (CourierTask) task;
					CourierTaskPanel full = new CourierTaskPanel(plugin, courier, clientThread, itemManager, courier.getSlot());
					row = taskRow(full, courier::getOverlayColor, "c" + courier.getData().getDbrow());
					courierSummary(row, courier);
				}
				else if (task instanceof BountyTask)
				{
					BountyTask bounty = (BountyTask) task;
					BountyTaskPanel full = new BountyTaskPanel(plugin, bounty, clientThread, itemManager, client, bounty.getSlot());
					row = taskRow(full, bounty::getOverlayColor, "b" + bounty.getData().getDbrow());
					bountySummary(row, bounty);
					bountyRows.put(bounty.getSlot(), new BountyRow(full, row));
				}
				if (row != null)
				{
					markerView.add(row);
					markerView.add(Box.createRigidArea(new Dimension(0, 2)));
				}
			}
			if (allTasks.isEmpty())
			{
				setupErrorPanel(true);
			}
			repaint();
			revalidate();
		}

		/** Routing extension: one offered task on the last board, as the side list shows it. */
		public static final class BoardRow
		{
			final int rank;
			final PortLocation pickup;
			final PortLocation delivery;
			final String name;
			final String metric;
			/** Items it can give from the wanted list; empty if none. */
			final String wanted;
			/** The board tint for how much sailing it adds. */
			final Color detour;

			public BoardRow(int rank, PortLocation pickup, PortLocation delivery, String name, String metric, String wanted, Color detour)
			{
				this.rank = rank;
				this.pickup = pickup;
				this.delivery = delivery;
				this.name = name;
				this.metric = metric;
				this.wanted = wanted;
				this.detour = detour;
			}
		}

		/**
		 * Routing extension: the offered tasks of the last notice board, ranked, one line each: rank, route in
		 * the board's detour colour, the ranking metric's value, and a star if it can give a wanted item (the
		 * task's name and the items are in the tooltip). Swing thread only.
		 */
		public void showBoard(PortLocation board, String metricName, List<BoardRow> rows)
		{
			boardView.removeAll();
			if (!rows.isEmpty())
			{
				FitLabel header = new FitLabel();
				header.setFont(FontManager.getRunescapeSmallFont());
				header.setForeground(Color.WHITE);
				header.setBorder(new EmptyBorder(8, 0, 2, 0));
				header.setVersions(PortNames.full(board) + " board \u00B7 " + metricName,
					PortNames.abbreviation(board) + " \u00B7 " + metricName, PortNames.abbreviation(board));
				boardView.add(header);
				for (BoardRow r : rows)
				{
					boardView.add(boardLine(r, config.routingLegColor()));
				}
			}
			boardView.revalidate();
			boardView.repaint();
		}

		static JPanel boardLine(BoardRow r, Color best)
		{
			JPanel line = new JPanel(new BorderLayout(4, 0));
			line.setBorder(new EmptyBorder(1, 0, 1, 0));
			line.setToolTipText("<html>" + r.name + (r.wanted.isEmpty() ? "" : "<br>Wanted: " + r.wanted) + "</html>");

			JLabel rank = new JLabel("#" + r.rank);
			rank.setForeground(r.rank == 1 ? best : Color.GRAY);
			FitLabel route = new FitLabel();
			route.setVersions(PortNames.route(r.wanted.isEmpty() ? "" : "\u2605 ", r.pickup, r.delivery));
			route.setForeground(r.detour);
			JLabel metric = new JLabel(r.metric);
			metric.setForeground(Color.GRAY);
			for (JLabel l : new JLabel[]{rank, route, metric})
			{
				l.setFont(FontManager.getRunescapeSmallFont());
			}
			line.add(rank, BorderLayout.WEST);
			line.add(route, BorderLayout.CENTER);
			line.add(metric, BorderLayout.EAST);
			return line;
		}

		/** Routing extension: keeps a bag-size box in step when its toggle is changed in the config. Swing thread only. */
		public void setBagEnabled(BagSize size, boolean on)
		{
			bagBoxes.get(size).setSelected(on);
		}

		public void updateBountyPanel(BountyTask task) // avoid rebuilding the entire JPanel lol
		{
			// Looked up by slot: markerView's children alternate rows and spacers, so its index isn't the slot.
			BountyRow r = bountyRows.get(task.getSlot());
			if (r != null)
			{
				r.full.refresh();
				bountySummary(r.row, task);
			}
		}

		private TaskRow taskRow(JPanel full, Supplier<Color> colour, String key)
		{
			return new TaskRow(full, colour, openRows.contains(key), open ->
			{
				if (open)
				{
					openRows.add(key);
				}
				else
				{
					openRows.remove(key);
				}
			});
		}

		private static void courierSummary(TaskRow row, CourierTask t)
		{
			int taken = t.getCargoTaken();
			int delivered = t.getDelivered();
			int required = t.getData().getCargoAmount();
			String[] route = PortNames.route("", t.getData().getCargoLocation(), t.getData().getDeliveryLocation());
			String tip = "<html>" + t.getData().taskName
				+ "<br><font color='red'>red</font>: crates picked up / needed; white: delivered / needed</html>";
			if (delivered >= required)
			{
				row.setSummary(route, tip, "Claim", Color.GREEN);
			}
			else if (taken < required)
			{
				row.setSummary(route, tip, taken + "/" + required, Color.RED);
			}
			else
			{
				row.setSummary(route, tip, delivered + "/" + required, Color.WHITE);
			}
		}

		private static void bountySummary(TaskRow row, BountyTask t)
		{
			int looted = t.getItemsCollected();
			int required = t.getData().itemQuantity;
			row.setSummary(new String[]{t.getData().taskName}, t.getData().taskName, looted + "/" + required,
				looted < required ? Color.RED : Color.GREEN);
		}

		/** A bounty's full panel and its row, for updating both without a rebuild. */
		private static final class BountyRow
		{
			final BountyTaskPanel full;
			final TaskRow row;

			BountyRow(BountyTaskPanel full, TaskRow row)
			{
				this.full = full;
				this.row = row;
			}
		}



	private void addMarker()
		{
			setupErrorPanel(false);
		}

		private void setupErrorPanel(boolean enabled)
		{
			PluginErrorPanel errorPanel = this.errorPanel;
			errorPanel.setVisible(enabled);
			if (enabled)
			{
				errorPanel.setContent("Port Tasks", "Click the 'reload' button to read the Port Task client data.");
				markerView.removeAll();
				markerView.setLayout(new BoxLayout(markerView, BoxLayout.Y_AXIS));
				markerView.setBackground(ColorScheme.DARK_GRAY_COLOR);
				markerView.add(errorPanel);
			}
		}

}