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
import com.nucleon.porttasks.routing.BountyHunt;
import com.nucleon.porttasks.routing.BountySpawns;
import com.nucleon.porttasks.routing.LoopPorts;
import com.nucleon.porttasks.routing.LoopStatus;
import com.nucleon.porttasks.routing.LoopSuggester;
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

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
		// Routing extension: the loop, and loop suggestions shown on request.
		private final JPanel loopView = new JPanel();
		private final FitLabel loopLabel = new FitLabel();
		private final JLabel suggestLink = new JLabel("Suggest loops");
		private final JPanel suggestionsView = new JPanel();
		private final JPanel statusView = new JPanel();
		// Routing extension: the bounty hunt.
		private final JPanel huntView = new JPanel();
		private final JLabel chooseLink = new JLabel("Choose monsters");
		private final JPanel monsterList = new JPanel();
		private final JPanel huntResults = new JPanel();
		private final FitLabel afkLabel = new FitLabel();
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
			// Loop: the current loop, and suggestions on request (SPEC-routing.md §2.4).
			loopView.setLayout(new BoxLayout(loopView, BoxLayout.Y_AXIS));
			loopView.setBorder(new EmptyBorder(4, 0, 0, 0));
			loopLabel.setFont(FontManager.getRunescapeSmallFont());
			loopLabel.setForeground(Color.WHITE);
			loopLabel.setAlignmentX(LEFT_ALIGNMENT);
			suggestLink.setFont(FontManager.getRunescapeSmallFont());
			suggestLink.setForeground(config.routingLegColor());
			suggestLink.setAlignmentX(LEFT_ALIGNMENT);
			suggestLink.setToolTipText("Rank loops for your level, the ports you can reach and the bag sizes ticked above");
			suggestLink.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					suggestLink.setText("Finding loops...");
					plugin.suggestLoops(PortTasksPluginPanel.this::showSuggestions);
				}
			});
			suggestionsView.setLayout(new BoxLayout(suggestionsView, BoxLayout.Y_AXIS));
			suggestionsView.setAlignmentX(LEFT_ALIGNMENT);
			statusView.setLayout(new BoxLayout(statusView, BoxLayout.Y_AXIS));
			statusView.setAlignmentX(LEFT_ALIGNMENT);
			loopView.add(loopLabel);
			loopView.add(statusView);
			loopView.add(suggestLink);
			loopView.add(suggestionsView);
			showLoop(config.routingLoop());

			JPanel south = new JPanel(new BorderLayout());
			south.add(bagRow, BorderLayout.NORTH);
			south.add(loopView, BorderLayout.CENTER);
			northPanel.add(south, BorderLayout.SOUTH);

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

			// Bounty hunt (SPEC-routing.md §2.5): monsters picked from a list shown on request, then where to look.
			huntView.setLayout(new BoxLayout(huntView, BoxLayout.Y_AXIS));
			huntView.setBackground(ColorScheme.DARK_GRAY_COLOR);
			huntView.setBorder(new EmptyBorder(8, 0, 0, 0));
			JLabel huntTitle = smallLabel("Bounty hunt", Color.WHITE);
			chooseLink.setFont(FontManager.getRunescapeSmallFont());
			chooseLink.setForeground(config.routingLegColor());
			chooseLink.setAlignmentX(LEFT_ALIGNMENT);
			chooseLink.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					toggleMonsterList();
				}
			});
			monsterList.setLayout(new BoxLayout(monsterList, BoxLayout.Y_AXIS));
			monsterList.setAlignmentX(LEFT_ALIGNMENT);
			monsterList.setVisible(false);
			huntResults.setLayout(new BoxLayout(huntResults, BoxLayout.Y_AXIS));
			huntResults.setAlignmentX(LEFT_ALIGNMENT);
			huntView.add(huntTitle);
			afkLabel.setFont(FontManager.getRunescapeSmallFont());
			afkLabel.setAlignmentX(LEFT_ALIGNMENT);
			afkLabel.setVisible(false);
			huntView.add(afkLabel);
			huntView.add(chooseLink);
			huntView.add(monsterList);
			huntView.add(huntResults);
			centerPanel.add(huntView, BorderLayout.SOUTH);

			// setup panels border layout
			add(northPanel, BorderLayout.NORTH);
			add(centerPanel, BorderLayout.CENTER);
		}

		/** Shows these tasks (a copy the caller made on the client thread). Swing thread only. */
		public void rebuild(List<Task> tasks)
		{
			markerView.removeAll();
			bountyRows.clear();
			List<Task> allTasks = new ArrayList<>(tasks);
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
			/** Part of the best set of tasks to take. */
			final boolean chosen;
			/** A loop is set and this task leaves it: listed after the in-loop tasks, dimmed. */
			final boolean offLoop;

			public BoardRow(int rank, PortLocation pickup, PortLocation delivery, String name, String metric, String wanted, Color detour,
				boolean chosen, boolean offLoop)
			{
				this.offLoop = offLoop;
				this.rank = rank;
				this.pickup = pickup;
				this.delivery = delivery;
				this.name = name;
				this.metric = metric;
				this.wanted = wanted;
				this.detour = detour;
				this.chosen = chosen;
			}
		}

		/**
		 * Routing extension: the offered tasks of the last notice board, ranked, one line each: rank (in the
		 * accent colour for the best set), route in the board's detour colour, the ranking metric's value, and a star if it can give
		 * a wanted item (the task's name and the items are in the tooltip). {@code summary} is a line about the
		 * best set, or null. Swing thread only.
		 */
		public void showBoard(PortLocation board, String metricName, List<BoardRow> rows, String summary)
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
				if (summary != null)
				{
					FitLabel line = new FitLabel();
					line.setFont(FontManager.getRunescapeSmallFont());
					line.setForeground(config.routingLegColor());
					line.setBorder(new EmptyBorder(0, 0, 2, 0));
					line.setVersions(summary);
					boardView.add(line);
				}
				boolean sets = summary != null;
				boolean offLoopShown = false;
				for (BoardRow r : rows)
				{
					if (r.offLoop && !offLoopShown)
					{
						// Rows come in-loop first, so one divider separates the two groups.
						offLoopShown = true;
						JLabel divider = new JLabel("Off loop");
						divider.setFont(FontManager.getRunescapeSmallFont());
						divider.setForeground(Color.GRAY);
						divider.setBorder(new EmptyBorder(4, 0, 1, 0));
						boardView.add(divider);
					}
					boardView.add(boardLine(r, config.routingLegColor(), sets));
				}
			}
			boardView.revalidate();
			boardView.repaint();
		}

		/** @param sets whether the best set is shown (then its tasks' ranks are in the accent colour, not rank 1's) */
		static JPanel boardLine(BoardRow r, Color best, boolean sets)
		{
			JPanel line = new JPanel(new BorderLayout(4, 0));
			line.setBorder(new EmptyBorder(1, 0, 1, 0));
			line.setToolTipText("<html>" + r.name + (r.wanted.isEmpty() ? "" : "<br>Wanted: " + r.wanted)
				+ (r.offLoop ? "<br>Leaves the loop" : "") + "</html>");

			JLabel rank = new JLabel("#" + r.rank);
			rank.setForeground(sets ? (r.chosen ? best : Color.GRAY) : (r.rank == 1 ? best : Color.GRAY));
			FitLabel route = new FitLabel();
			route.setVersions(PortNames.route(r.wanted.isEmpty() ? "" : "\u2605 ", r.pickup, r.delivery));
			route.setForeground(r.offLoop ? r.detour.darker().darker() : r.detour);
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

		/** Routing extension: shows the loop setting's ports (or that none is set, or names it didn't know). Swing thread only. */
		public void showLoop(String setting)
		{
			LoopPorts loop = LoopPorts.parse(setting);
			if (!loop.active())
			{
				loopLabel.setVersions("Loop: none");
			}
			else
			{
				List<String> full = new ArrayList<>();
				List<String> abbr = new ArrayList<>();
				for (PortLocation p : loop.ports())
				{
					full.add(PortNames.full(p));
					abbr.add(PortNames.abbreviation(p));
				}
				loopLabel.setVersions("Loop: " + String.join(", ", full), "Loop: " + String.join(", ", abbr),
					"Loop: " + loop.ports().size() + " ports");
			}
			loopLabel.setToolTipText(loop.unknown().isEmpty() ? null
				: "<html>Not a port (or more than one): " + String.join(", ", loop.unknown()) + "</html>");
			loopLabel.setForeground(loop.unknown().isEmpty() ? Color.WHITE : Color.ORANGE);
		}

		/**
		 * Routing extension: loop mode's phase and its boards (SPEC-routing.md §2.4.2): one line per loop board,
		 * "not seen" or how many worthwhile in-loop tasks it still offers. Swing thread only.
		 */
		public void showLoopStatus(LoopStatus status)
		{
			statusView.removeAll();
			if (status.phase != LoopStatus.Phase.OFF)
			{
				String phase;
				Color colour;
				switch (status.phase)
				{
					case GATHER:
						phase = "Gather: look at each board";
						colour = Color.YELLOW;
						break;
					case DRY:
						phase = "Dry: " + status.tasksToReset + " tasks to board reset";
						colour = Color.ORANGE;
						break;
					default:
						phase = "Sail: " + status.tasksToReset + " tasks to board reset";
						colour = Color.WHITE;
						break;
				}
				statusView.add(smallLabel(phase, colour));
				for (LoopStatus.Board b : status.boards)
				{
					FitLabel line = new FitLabel();
					line.setFont(FontManager.getRunescapeSmallFont());
					line.setAlignmentX(LEFT_ALIGNMENT);
					String what = !b.seen ? (b.seaOnly ? "by sea" : "not seen") : b.worthwhile == 0 ? "nothing" : b.worthwhile + " to take";
					line.setVersions("  " + PortNames.full(b.port) + ": " + what, "  " + PortNames.abbreviation(b.port) + ": " + what);
					line.setForeground(!b.seen ? (b.seaOnly ? Color.GRAY : Color.YELLOW) : b.worthwhile > 0 ? Color.WHITE : Color.GRAY);
					statusView.add(line);
				}
			}
			statusView.revalidate();
			statusView.repaint();
		}

		/** Routing extension: the Bounty AFK status line ("Great white shark · blacked out"), or null to hide it. */
		public void showAfk(String status)
		{
			afkLabel.setVisible(status != null);
			if (status != null)
			{
				afkLabel.setVersions("AFK: " + status, status);
				afkLabel.setForeground(status.contains("corpse") ? Color.ORANGE : Color.CYAN);
			}
			huntView.revalidate();
			huntView.repaint();
		}

		/** Shows or hides the monster boxes; built from the current setting each time it opens. Swing thread only. */
		private void toggleMonsterList()
		{
			boolean open = !monsterList.isVisible();
			monsterList.removeAll();
			if (open)
			{
				Set<String> picked = new HashSet<>();
				for (String s : config.routingBountyHunt().split(","))
				{
					picked.add(s.trim().toLowerCase());
				}
				List<JCheckBox> boxes = new ArrayList<>();
				for (String monster : plugin.bountyMonsters())
				{
					JCheckBox box = new JCheckBox(monster, picked.contains(monster.toLowerCase()));
					box.setFont(FontManager.getRunescapeSmallFont());
					box.setFocusable(false);
					box.setAlignmentX(LEFT_ALIGNMENT);
					boxes.add(box);
					box.addActionListener(e ->
					{
						List<String> chosen = new ArrayList<>();
						for (JCheckBox b : boxes)
						{
							if (b.isSelected())
							{
								chosen.add(b.getText());
							}
						}
						plugin.setHunted(chosen);
					});
					monsterList.add(box);
				}
			}
			monsterList.setVisible(open);
			chooseLink.setText(open ? "Done choosing" : "Choose monsters");
			huntView.revalidate();
			huntView.repaint();
		}

		/**
		 * Routing extension: the bounty hunt's parts and where to look (SPEC-routing.md §2.5). Per part, one line per
		 * board in search order: its state (offered now, always offered, to check, not this cycle), then the
		 * task's quantity, bag size and expected bag value at high-alchemy prices. Swing thread only.
		 */
		public void showBountyHunt(BountyHunt hunt, BountySpawns.Area sea, List<String> notes)
		{
			huntResults.removeAll();
			if (sea != null)
			{
				// At sea: the monster being sailed for, where, and the safespot tools (SPEC-routing.md §2.5.1-2).
				FitLabel at = new FitLabel();
				at.setFont(FontManager.getRunescapeSmallFont());
				at.setForeground(config.routingLegColor());
				at.setAlignmentX(LEFT_ALIGNMENT);
				at.setVersions("Sailing for " + sea.monster + ": " + sea.location, "Sailing for " + sea.monster, sea.monster);
				at.setToolTipText("<html>" + sea.monster + "<br>" + sea.location + "<br>Kept until the task's parts are in</html>");
				huntResults.add(at);
				if (!notes.isEmpty() && sea.safespot == null)
				{
					JLabel hint = new JLabel("<html><div style='width:180px'>Wiki: " + String.join(" ", notes) + "</div></html>");
					hint.setFont(FontManager.getRunescapeSmallFont());
					hint.setForeground(Color.GRAY);
					hint.setAlignmentX(LEFT_ALIGNMENT);
					huntResults.add(hint);
				}
				JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
				tools.setAlignmentX(LEFT_ALIGNMENT);
				tools.add(link("Save safespot here", "Records the boat's tile as a " + sea.monster
					+ " safespot; sailing for it then goes there", plugin::saveSafespot));
				tools.add(smallLabel("  ", Color.GRAY));
				tools.add(link("Forget saved", "Forgets your saved " + sea.monster + " safespots", plugin::clearSafespots));
				huntResults.add(tools);
			}
			if (hunt.next != null)
			{
				huntResults.add(smallLabel("Next: " + PortNames.full(hunt.next), config.routingLegColor()));
			}
			for (BountyHunt.Part p : hunt.parts)
			{
				JLabel part = smallLabel(p.item + (p.held ? " (held)" : ""), p.held ? Color.GRAY : Color.WHITE);
				part.setBorder(new EmptyBorder(4, 0, 1, 0));
				huntResults.add(part);
				if (p.held)
				{
					continue;
				}
				for (BountyHunt.Board b : p.boards)
				{
					huntResults.add(huntLine(b));
				}
			}
			huntResults.revalidate();
			huntResults.repaint();
		}

		private static JPanel huntLine(BountyHunt.Board b)
		{
			String state;
			Color colour;
			switch (b.state)
			{
				case OFFERED:
					state = "offered";
					colour = Color.GREEN;
					break;
				case ALWAYS:
					state = "always";
					colour = Color.CYAN;
					break;
				case UNCHECKED:
					state = "check";
					colour = Color.YELLOW;
					break;
				default:
					state = "not now";
					colour = Color.GRAY;
					break;
			}
			JPanel line = new JPanel(new BorderLayout(4, 0));
			line.setAlignmentX(LEFT_ALIGNMENT);
			FitLabel where = new FitLabel();
			where.setFont(FontManager.getRunescapeSmallFont());
			where.setVersions("  " + PortNames.full(b.port) + " · " + state, "  " + PortNames.abbreviation(b.port) + " · " + state);
			where.setForeground(colour);
			JLabel numbers = smallLabel(b.qty + " · " + (b.bag == null ? "?" : b.bag.wikiName().substring(0, 1))
				+ " · " + String.format("%.1fk", b.value / 1000), Color.GRAY);
			line.setToolTipText(String.format("<html>%s%s<br>%d parts, level %d<br>%s bag, about %,.0f gp expected (high alch)"
					+ "<br>Offered now: seen on the board this reset cycle. Always: the board's guaranteed bounty."
					+ "<br>Check: may be on the board. Not now: the board didn't have it this cycle</html>",
				b.port.getName(), b.guaranteed ? " (always offers it)" : "", b.qty, b.level,
				b.bag == null ? "Unknown" : b.bag.wikiName(), b.value));
			line.add(where, BorderLayout.CENTER);
			line.add(numbers, BorderLayout.EAST);
			return line;
		}

		/** A clickable small-font label in the accent colour. */
		private JLabel link(String text, String tip, Runnable action)
		{
			JLabel l = smallLabel(text, config.routingLegColor());
			l.setToolTipText(tip);
			l.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					action.run();
				}
			});
			return l;
		}

		private static JLabel smallLabel(String text, Color colour)
		{
			JLabel l = new JLabel(text);
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(colour);
			l.setAlignmentX(LEFT_ALIGNMENT);
			return l;
		}

		/**
		 * Routing extension: one line per suggested loop: its ports in sailing order, then pool tasks inside it
		 * and pool XP per 1,000 loop tiles. Clicking a line makes it the loop. Swing thread only.
		 */
		void showSuggestions(List<LoopSuggester.Suggestion> suggestions)
		{
			suggestLink.setText("Suggest loops");
			suggestionsView.removeAll();
			if (suggestions.isEmpty())
			{
				JLabel none = new JLabel("No loop has " + LoopSuggester.TASKS_PER_PORT + "+ tasks per port");
				none.setFont(FontManager.getRunescapeSmallFont());
				none.setForeground(Color.GRAY);
				suggestionsView.add(none);
			}
			for (LoopSuggester.Suggestion s : suggestions)
			{
				JPanel line = new JPanel(new BorderLayout(4, 0));
				line.setAlignmentX(LEFT_ALIGNMENT);
				line.setBorder(new EmptyBorder(1, 0, 1, 0));
				FitLabel ports = new FitLabel();
				List<String> full = new ArrayList<>();
				List<String> abbr = new ArrayList<>();
				for (PortLocation p : s.ports)
				{
					full.add(PortNames.full(p));
					abbr.add(PortNames.abbreviation(p));
				}
				ports.setVersions(String.join(", ", full), String.join(", ", abbr));
				JLabel numbers = new JLabel(s.tasks + " · " + String.format("%.1fk", s.density() / 1000));
				numbers.setForeground(Color.GRAY);
				for (JLabel l : new JLabel[]{ports, numbers})
				{
					l.setFont(FontManager.getRunescapeSmallFont());
				}
				line.setToolTipText(String.format("<html>%s<br>%d tasks in the pool stay inside it<br>%,.0f pool XP per 1,000 tiles"
						+ "<br>Loop: %,.0f tiles<br>Click to use this loop</html>",
					String.join(" > ", full), s.tasks, s.density(), s.loopTiles));
				line.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						plugin.setLoop(s.ports);
					}
				});
				line.add(ports, BorderLayout.CENTER);
				line.add(numbers, BorderLayout.EAST);
				suggestionsView.add(line);
			}
			suggestionsView.revalidate();
			suggestionsView.repaint();
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