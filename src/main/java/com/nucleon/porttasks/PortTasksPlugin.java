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

import com.google.common.base.MoreObjects;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.nucleon.porttasks.routing.BagCounter;
import com.nucleon.porttasks.routing.BagSize;
import com.nucleon.porttasks.routing.BoardScorer;
import com.nucleon.porttasks.routing.BoatLocator;
import com.nucleon.porttasks.routing.BountyHunt;
import com.nucleon.porttasks.routing.BountyWikiData;
import com.nucleon.porttasks.routing.CourierWikiData;
import com.nucleon.porttasks.routing.DepositGuard;
import com.nucleon.porttasks.routing.DockGuard;
import com.nucleon.porttasks.routing.LoopBoards;
import com.nucleon.porttasks.routing.LoopPorts;
import com.nucleon.porttasks.routing.LoopStatus;
import com.nucleon.porttasks.routing.LoopSuggester;
import com.nucleon.porttasks.routing.NoticeBoardTiles;
import com.nucleon.porttasks.routing.RewardValuer;
import com.nucleon.porttasks.routing.RoutingDiagnostics;
import com.nucleon.porttasks.routing.RoutingService;
import com.nucleon.porttasks.routing.WantedItems;
import com.nucleon.porttasks.routing.XpLearner;
import com.google.inject.Provides;
import com.nucleon.porttasks.enums.BountyTaskData;
import com.nucleon.porttasks.enums.PortLocation;
import com.nucleon.porttasks.overlay.NoticeBoardTooltip;
import java.awt.Color;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import net.runelite.api.coords.WorldPoint;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import com.nucleon.porttasks.enums.PortTaskTrigger;
import com.nucleon.porttasks.overlay.TaskHighlight;
import com.nucleon.porttasks.enums.TaskReward;
import com.nucleon.porttasks.ui.PortTasksPluginPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import com.nucleon.porttasks.routing.SubsetChooser;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Port Tasks",
	description = "Provides navigation and overlays for sailing cargo and bounty tasks",
	tags = {"sailing", "port", "bounty", "cargo", "tasks"}
)
public class PortTasksPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private PortTasksConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Gson gson;
	@Getter
	@Inject
	private ColorPickerManager colorPickerManager;
	@Inject
	ChatMessageManager chatMessageManager;
	@Inject
	private PortTasksLedgerOverlay portTasksLedgerOverlay;
	@Inject
	private PortTaskModelRenderer portTaskModelRenderer;
	@Inject
	private TaskHighlight taskHighlight;
	@Inject
	private DespawnTimerOverlay despawnTimerOverlay;
	@Inject
	NoticeBoardTooltip noticeBoardTooltip;
	@Getter
	List<CourierTask> courierTasks = new ArrayList<>();
	// Routing extension (SPEC-routing.md).
	private RoutingDiagnostics routingDiagnostics;
	private BoatLocator boatLocator;
	private XpLearner xpLearner;
	// Routing extension: loop mode's memory of the loop boards, and where loop mode stands (SPEC-routing.md §2.4.2).
	private LoopBoards loopBoards;
	private volatile LoopStatus loopStatus = LoopStatus.OFF;
	// Routing extension: the bounty hunt (SPEC-routing.md §2.5).
	private BountyWikiData bountyWiki;
	private RewardValuer rewardValuer;
	private volatile BountyHunt bountyHunt = BountyHunt.NONE;
	// Whether each port-gating quest is finished; absent until read this login.
	private final Map<Quest, Boolean> questsDone = new HashMap<>();
	private boolean questCheckPending;
	RoutingService routingService;
	BagCounter bagCounter;
	private DepositGuard depositGuard;
	private DockGuard dockGuard;
	/** A short-lived warning to show above the player (e.g. a blocked dock), and the tick it expires. */
	private String overheadWarning;
	/** Routing extension: what the port overlays show; rebuilt by rebuildView() on the events that change it. */
	private volatile PortView view = PortView.EMPTY;
	private int overheadWarningUntil;
	private final WantedItems wantedItems = new WantedItems();
	private BoardScorer boardScorer;
	/** Scores of the last notice board's offered courier tasks, by dbrow (routing extension). */
	private volatile Map<Integer, BoardScorer.Score> boardScores = new HashMap<>();
	/** How TaskHighlight marks each offered task; rebuilt by boardChanged(). */
	@Getter
	private volatile Map<Integer, TaskHighlight.Mark> boardMarks = Collections.emptyMap();
	/** Goes up whenever anything shown about the board changes, so the tooltip knows to redo its text. */
	private volatile int boardVersion;
	/** Best XP per tile over all courier tasks, for the tooltip's colour scale; see updateBestXpPerTile(). */
	private double bestXpPerTile;
	/**
	 * Routing extension: the best set of offered tasks to take (see SubsetChooser), or null while a search runs
	 * or when off. Searches run on their own thread; bestSetGeneration drops results that a newer search
	 * replaced, and bestSetKey skips searching again when nothing it depends on changed.
	 */
	private volatile SubsetChooser.Result bestSet;
	private String bestSetKey;
	private int bestSetGeneration;
	private ExecutorService setSearchExecutor;
	/** The last board's ranking and port, to re-show the side list when the best set arrives. */
	private List<BoardScorer.Score> lastRanked = Collections.emptyList();
	private PortLocation lastBoard;
	@Inject
	private RoutingNextStopOverlay routingNextStopOverlay;
	@Inject
	private RoutingCargoReminderOverlay routingCargoReminderOverlay;
	@Inject
	private RoutingBoardOverlay routingBoardOverlay;
	@Inject
	private RoutingCargoHoldOverlay routingCargoHoldOverlay;
	@Getter
	List<BountyTask> bountyTasks = new ArrayList<>();
	@Getter
	Set<GameObject> gangplanks = new HashSet<>();
	@Getter
	Set<GameObject> noticeboards = new HashSet<>();
	@Getter
	Set<GameObject> ledgers = new HashSet<>();
	@Getter
	Set<BountyCorpse> bountyCorpses = new HashSet<>();
	@Getter
	private final Set<GameObject> helms = new HashSet<>();
	@Getter
	private final Set<GameObject> cargoHolds = new HashSet<>();
	@Getter
	Map<Integer, OfferedTaskData> offeredTasks = new HashMap<>();
	@Getter
	private final Set<WidgetTag> widgetTags = new HashSet<>();
	@Getter
	private boolean lockedIn = false;
	@Getter
	private int sailingLevel;
	@Getter
	private boolean noticeBoardHideIncompletable;
	@Getter
	private boolean noticeBoardHideBounty;
	@Getter
	private boolean noticeBoardHideCourier;
	@Getter
	private boolean noticeBoardHideUntagged;
	@Getter
	private boolean highlightGangplanks;
	@Getter
	private Color highlightGangplanksColor;
	@Getter
	private boolean highlightNoticeboards;
	@Getter
	private boolean highlightCargoHolds;
	@Getter
	private Color highlightCargoHoldsColor;
	@Getter
	private boolean highlightHelmMissingCargo;
	@Getter
	private Color highlightNoticeboardsColor;
	@Getter
	private int noticeBoardHideOpactity;
	@Getter
	private Color minColor;
	@Getter
	private Color maxColor;
	@Getter
	private boolean highlightTaskConflicts;
	@Getter
	private Color taskConflictColor;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ItemManager itemManager;
	@Inject
	private EventBus eventBus;
	private int[] varPlayers;
	private PortTasksPluginPanel pluginPanel;
	private NavigationButton navigationButton;
	private Item[] previousInventory;
	private static final String PLUGIN_NAME = "Port Tasks";
	private static final String ICON_FILE = "icon.png";
	public static final String CONFIG_GROUP = "porttasks";
	private static final String CONFIG_KEY_TASK_COLOURS = "taskColours";
	/** Settings (outside the routing ones) that change how the board is marked or its tooltip. */
	private static final Set<String> BOARD_KEYS = Set.of("noticeBoardHideOpacity", "noticeBoardHideIncompletable",
		"noticeBoardHideBounty", "noticeBoardHideCourier", "noticeBoardHideUntagged", "highlightTaskConflicts",
		"taskConflictColor", "minColor", "maxColor");
	/** Task -> colour picked for it in the side panel (RGB); see taskColourKey. Kept in the profile. */
	private static final class TaskColours extends HashMap<String, Integer>
	{
	}
	private final TaskColours taskColours = new TaskColours();
	private static final String CONFIG_KEY_TAGS = "task_tags";
	private static final String CONFIG_KEY_TASKS_COMPLETED = "tasks_completed";
	private static final String CONFIG_KEY_LAST_TASK_COMPLETED = "last_task_completed";

	private static final String MARK = "Mark task";
	private static final String UNMARK = "Unmark task";

	private static final Set<Integer> SAILING_BOAT_CARGO_HOLDS = Set.of(
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_RAFT_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_RAFT_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_RAFT_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_RAFT_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_RAFT_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_2X5_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_2X5_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_2X5_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_2X5_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_2X5_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_2X5_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_2X5_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_REGULAR_LARGE_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_LARGE_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_TEAK_LARGE_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_MAHOGANY_LARGE_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_CAMPHOR_LARGE_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_IRONWOOD_LARGE_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_NO_CARGO,
		ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE_CARGO
	);

	@Override
	protected void startUp()
	{
		log.info("Starting plugin Port Tasks");

		boatLocator = new BoatLocator(client);
		setSearchExecutor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "port-tasks-best-set");
			t.setDaemon(true);
			return t;
		});
		routingService = new RoutingService(config, boatLocator, eventBus);
		clientThread.invokeLater(() ->
		{
			if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
			{
				return false;
			}

			try
			{
				CourierTaskData.loadFromCache(client);
			}
			catch (Exception e)
			{
				log.warn("Failed to load courier task data", e);
			}

			try
			{
				BountyTaskData.loadFromCache(client);
			}
			catch (Exception e)
			{
				log.warn("Failed to load bounty task data", e);
			}

			try
			{
				boatLocator.load();
			}
			catch (Exception e)
			{
				log.warn("Failed to load sailing dock data", e);
			}
			return true;
		});

		migrateOnlyBigBags();
		CourierWikiData courierWikiData = CourierWikiData.load(gson);
		xpLearner = new XpLearner(configManager, CONFIG_GROUP, gson, courierWikiData);
		loopBoards = new LoopBoards(configManager, CONFIG_GROUP, gson);
		loadTaskColours();
		bagCounter = new BagCounter(courierWikiData);
		depositGuard = new DepositGuard(client);
		dockGuard = new DockGuard(client);
		wantedItems.parse(config.routingWantedItems());
		RewardValuer rewardValuer = new RewardValuer(courierWikiData, itemManager, wantedItems);
		this.rewardValuer = rewardValuer;
		bountyWiki = BountyWikiData.load(gson);
		boardScorer = new BoardScorer(routingService.graph(), courierWikiData, xpLearner, rewardValuer, wantedItems, config);
		updateBestXpPerTile();
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				// Otherwise only set by the next Sailing XP drop: until then "Hide incompletable" hid every task.
				sailingLevel = client.getRealSkillLevel(Skill.SAILING);
			}
		});
		routingDiagnostics = new RoutingDiagnostics(client, courierWikiData, rewardValuer, boatLocator, xpLearner);

		pluginPanel = new PortTasksPluginPanel(this, clientThread, itemManager, client, config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), ICON_FILE);
		navigationButton = NavigationButton.builder()
				.tooltip(PLUGIN_NAME)
				.icon(icon)
				.priority(5)
				.panel(pluginPanel)
				.build();

		clientToolbar.addNavigation(navigationButton);
		registerOverlays();
		refreshPanel();

		loadWidgetTags();
		overlayManager.add(taskHighlight);
		overlayManager.add(routingNextStopOverlay);
		overlayManager.add(routingCargoReminderOverlay);
		overlayManager.add(routingBoardOverlay);
		overlayManager.add(routingCargoHoldOverlay);

		migrateConfiguration();
		highlightGangplanks = config.highlightGangplanks();
		highlightGangplanksColor = config.highlightGangplanksColor();
		highlightCargoHolds = config.highlightCargoHolds();
		highlightCargoHoldsColor = config.highlightCargoHoldsColor();
		highlightNoticeboards = config.highlightNoticeboards();
		highlightNoticeboardsColor = config.highlightNoticeboardsColor();
		highlightHelmMissingCargo = config.highlightHelmMissingCargo();
		noticeBoardHideOpactity = mapOpacity(config.noticeBoardHideOpacity());
		noticeBoardHideIncompletable = config.noticeBoardHideIncompletable();
		noticeBoardHideBounty = config.noticeBoardHideBounty();
		noticeBoardHideCourier = config.noticeBoardHideCourier();
		noticeBoardHideUntagged = config.noticeBoardHideUntagged();
		minColor = config.minColor();
		maxColor = config.maxColor();
		highlightTaskConflicts = config.highlightTaskConflicts();
		taskConflictColor = config.taskConflictColor();
	}

	@Override
	protected void shutDown()
	{
		log.info("Stopping Port Tasks");
		clientToolbar.removeNavigation(navigationButton);
		pluginPanel = null;
		navigationButton = null;
		gangplanks.clear();
		noticeboards.clear();
		ledgers.clear();
		helms.clear();
		cargoHolds.clear();
		bountyCorpses.clear();

		overlayManager.remove(portTasksLedgerOverlay);
		overlayManager.remove(portTaskModelRenderer);
		overlayManager.remove(noticeBoardTooltip);
		overlayManager.remove(taskHighlight);
		overlayManager.remove(routingNextStopOverlay);
		overlayManager.remove(routingCargoReminderOverlay);
		overlayManager.remove(routingBoardOverlay);
		overlayManager.remove(routingCargoHoldOverlay);
		overlayManager.remove(despawnTimerOverlay);
		setSearchExecutor.shutdownNow();
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onConfigChanged(final ConfigChanged event)
	{
		if (!event.getGroup().equals(PortTasksConfig.CONFIG_GROUP))
			return;
		if (event.getKey().startsWith("routing"))
		{
			if ("routingWantedItems".equals(event.getKey()))
			{
				wantedItems.parse(config.routingWantedItems());
			}
			if ("routingLoop".equals(event.getKey()))
			{
				String loop = config.routingLoop();
				SwingUtilities.invokeLater(() -> pluginPanel.showLoop(loop));
			}
			BagSize bag = BagSize.forFilterKey(event.getKey());
			if (bag != null)
			{
				boolean on = BoardScorer.bagEnabled(config, bag);
				SwingUtilities.invokeLater(() -> pluginPanel.setBagEnabled(bag, on));
			}
			clientThread.invokeLater(() ->
			{
				routingService.replan(courierTasks);
				if (!offeredTasks.isEmpty())
				{
					rescoreBoard();
				}
				rebuildView();
			});
		}
		if (BOARD_KEYS.contains(event.getKey()))
		{
			clientThread.invokeLater(this::boardChanged);
		}
		switch (event.getKey())
		{
			case "noticeBoardTooltip":
				if (event.getNewValue().contains("true"))
				{
					overlayManager.add(noticeBoardTooltip);
				}
				if (event.getNewValue().contains("false"))
				{
					overlayManager.remove(noticeBoardTooltip);
				}
				return;
			case "corpseOverlay":
				if (event.getNewValue().contains("NONE"))
				{
					overlayManager.remove(despawnTimerOverlay);
				}
				else
				{
					overlayManager.add(despawnTimerOverlay);
				}
				return;
			case "highlightGangplanks":
				highlightGangplanks = config.highlightGangplanks();
				return;
			case "highlightGangplanksColor":
				highlightGangplanksColor = config.highlightGangplanksColor();
				return;
			case "highlightNoticeboards":
				highlightNoticeboards = config.highlightNoticeboards();
				return;
			case "highlightNoticeboardsColor":
				highlightNoticeboardsColor = config.highlightNoticeboardsColor();
				return;
			case "highlightHelmMissingCargo":
				highlightHelmMissingCargo = config.highlightHelmMissingCargo();
				return;
			case "highlightCargoHolds":
				highlightCargoHolds = config.highlightCargoHolds();
				return;
			case "highlightCargoHoldsColor":
				highlightCargoHoldsColor = config.highlightCargoHoldsColor();
				return;
			case "noticeBoardHideOpacity":
				noticeBoardHideOpactity = mapOpacity(config.noticeBoardHideOpacity());
				return;
			case "noticeBoardHideIncompletable":
				noticeBoardHideIncompletable = config.noticeBoardHideIncompletable();
				return;
			case "noticeBoardHideBounty":
				noticeBoardHideBounty = config.noticeBoardHideBounty();
				return;
			case "noticeBoardHideCourier":
				noticeBoardHideCourier = config.noticeBoardHideCourier();
				return;
			case "noticeBoardHideUntagged":
				noticeBoardHideUntagged = config.noticeBoardHideUntagged();
				return;
			case "minColor":
				minColor = config.minColor();
				return;
			case "maxColor":
				maxColor = config.maxColor();
				return;
			case "highlightTaskConflicts":
				highlightTaskConflicts = config.highlightTaskConflicts();
				return;
			case "taskConflictColor":
				taskConflictColor = config.taskConflictColor();
				return;
			case "noticeBoardState":
				configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_TASKS_COMPLETED, config.noticeBoardState());
				configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_LAST_TASK_COMPLETED, Instant.now().getEpochSecond());
				return;
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onVarbitChanged(final VarbitChanged event)
	{
		final int varbitId = event.getVarbitId();
		if (PortTaskTrigger.contains(varbitId))
		{
			PortTaskTrigger varbit = PortTaskTrigger.fromId(event.getVarbitId());
			int value = event.getValue();
			handlePortTaskTrigger(varbit, value);
			routingService.replan(courierTasks);
			if (!offeredTasks.isEmpty())
			{
				rescoreBoard();
			}
			rebuildView();
		}
		else if (varbitId == VarbitID.SAILING_BOAT_FACILITY_LOCKEDIN)
		{
			lockedIn = event.getValue() != 0;
		}
		else if (varbitId == VarbitID.SAILING_SIDEPANEL_BOAT_MOVE_MODE || varbitId == VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT)
		{
			// Boarding the boat ends loop mode's gather for this reset cycle, even with boards unseen: from then
			// on it's deliveries (SPEC-routing.md §2.4.2).
			if (varbitId == VarbitID.SAILING_PLAYER_IS_ON_PLAYER_BOAT && event.getValue() == 1
				&& loopStatus.phase == LoopStatus.Phase.GATHER)
			{
				loopBoards.endGather();
			}
			rebuildView();
		}
		else if (RoutingDiagnostics.BOAT_VARBITS.containsKey(varbitId))
		{
			routingDiagnostics.logBoatVarbit(varbitId, event.getValue());
			routingService.replan(courierTasks);
			rebuildView();
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onGameObjectSpawned(final GameObjectSpawned event)
	{
		final GameObject gameObject = event.getGameObject();
		final int id = gameObject.getId();

		if (id == ObjectID.SAILING_GANGPLANK_PROXY || PortLocation.isGangplank(id))
		{
			gangplanks.add(gameObject);
		}
		else if (PortLocation.isNoticeboard(id))
		{
			noticeboards.add(gameObject);
		}
		else if (PortLocation.isLedger(id))
		{
			ledgers.add(gameObject);
		}
		else if (isInHelmRange(id))
		{
			helms.add(gameObject);
		}
		else if (isInCargoHoldRange(id))
		{
			cargoHolds.add(gameObject);
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	public void onGameObjectDespawned(final GameObjectDespawned event)
	{
		final GameObject gameObject = event.getGameObject();
		final int id = gameObject.getId();
		final int worldViewId = gameObject.getWorldView().getId();

		if (id == ObjectID.SAILING_GANGPLANK_PROXY || PortLocation.isGangplank(id))
		{
			gangplanks.remove(gameObject);
		}
		else if (PortLocation.isNoticeboard(id))
		{
			noticeboards.remove(gameObject);
		}
		else if (PortLocation.isLedger(id))
		{
			ledgers.remove(gameObject);
		}
		else if (isInCargoHoldRange(id))
		{
			cargoHolds.remove(gameObject);
		}
		else if (isInHelmRange(id))
		{
			helms.remove(gameObject);
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		helms.removeIf(o -> o.getWorldView() == event.getWorldView());
		cargoHolds.removeIf(o -> o.getWorldView() == event.getWorldView());
	}

	@SuppressWarnings("unused")
	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || state == GameState.LOGGING_IN || state == GameState.LOGIN_SCREEN)
		{
			// Shortest Path may drop its path here; make the next plan re-send the leg.
			routingService.resetShortestPath();
		}
		if (state == GameState.LOGIN_SCREEN)
		{
			questsDone.clear(); // another account may log in
		}
		switch (state)
		{
			case HOPPING:
			case LOADING:
			case LOGGING_IN:
				gangplanks.clear();
				noticeboards.clear();
				ledgers.clear();
				bountyCorpses.clear();
				break;
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onWidgetLoaded(final WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.PORT_TASK_BOARD)
		{
			return;
		}
		offeredTasks.clear();
		clientThread.invokeLater(this::scanPortTaskBoard);
	}

	/**
	 * The board closed: stop re-scoring it on every crate moved. The side list keeps showing its last ranking
	 * until another board is opened.
	 */
	@SuppressWarnings("unused")
	@Subscribe
	private void onWidgetClosed(final WidgetClosed event)
	{
		if (event.getGroupId() != InterfaceID.PORT_TASK_BOARD)
		{
			return;
		}
		offeredTasks.clear();
		boardScores = new HashMap<>();
		boardChanged();
	}

	/** Recomputes the board marks, and tells the tooltip its text may be stale. Client thread. */
	private void boardChanged()
	{
		boardMarks = TaskHighlight.marks(this);
		boardVersion++;
	}

	/** Increases whenever what is shown about the board changes (scores, marks, settings). */
	public int boardVersion()
	{
		return boardVersion;
	}

	/**
	 * A courier task's XP per tile of its own route (pickup to delivery), with the planner's XP (learned where
	 * known) and the drawn-path distance. 0 if unknown.
	 */
	public double xpPerTile(CourierTaskData d)
	{
		Integer xp = xpLearner.xp(d.getId());
		double xpValue = xp != null ? xp : TaskReward.getIntRewardForTask(d.getDbrow());
		double tiles = taskTiles(d);
		return tiles > 0 && Double.isFinite(tiles) ? xpValue / tiles : 0;
	}

	/** Sailing distance of a courier task's own route, pickup to delivery, over the drawn paths. */
	public double taskTiles(CourierTaskData d)
	{
		return routingService.graph().distance(d.getCargoLocation(), d.getDeliveryLocation());
	}

	/** This task's XP per tile as a share of the best task's (0-1), for the tooltip's colour. */
	public double xpPerTileShare(CourierTaskData d)
	{
		return bestXpPerTile > 0 ? Math.min(1, xpPerTile(d) / bestXpPerTile) : 0;
	}

	/** Computed at start-up and when a board opens (about 400 tasks; cheap, but not per frame). */
	private void updateBestXpPerTile()
	{
		double best = 0;
		for (CourierTaskData d : CourierTaskData.all())
		{
			best = Math.max(best, xpPerTile(d));
		}
		bestXpPerTile = best;
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onMenuEntryAdded(final MenuEntryAdded event)
	{
		if (event.getType() != MenuAction.CC_OP.getId() || !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}
		MenuEntry baseEntry = event.getMenuEntry();
		Widget widget = baseEntry.getWidget();
		if (widget == null)
		{
			return;
		}
		if (widget.getId() != InterfaceID.PortTaskBoard.CONTAINER)
		{
			return;
		}
		Integer dbrow = getDbrowFromWidget(widget);
		if (dbrow == null)
		{
			return;
		}
		WidgetTag existing = getTagForDbrow(dbrow);

		client.createMenuEntry(-1)
			.setOption(existing == null ? MARK : UNMARK)
			.setTarget(event.getTarget())
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setType(MenuAction.RUNELITE_WIDGET)
			.onClick(this::markTask);

		if (existing != null)
		{
			createTaskColorMenu(baseEntry.getTarget(), widget, existing);
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onStatChanged(final StatChanged event)
	{
		if (event.getSkill() != Skill.SAILING)
		{
			return;
		}
		xpLearner.onSailingXp(event.getXp(), client.getTickCount());
		final int sailingLevel = client.getRealSkillLevel(Skill.SAILING);
		if (sailingLevel != this.sailingLevel)
		{
			this.sailingLevel = sailingLevel;
			if (!offeredTasks.isEmpty())
			{
				rescoreBoard();
			}
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onChatMessage(final ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (event.getMessage().contains("You have finished the "))
		{
			handleTaskCompleted();
			return;
		}
	}

	/** Routing extension: block depositing a crate at the wrong port's ledger (see DepositGuard). */
	@SuppressWarnings("unused")
	@Subscribe
	private void onMenuOptionClicked(final MenuOptionClicked event)
	{
		if (config.routingBlockWrongDeposit())
		{
			String reason = depositGuard.check(event.getMenuOption(), event.getId(), courierTasks);
			if (reason != null)
			{
				event.consume();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", reason, null);
				log.info("[routing] {}", reason);
				return;
			}
		}
		if (config.routingBlockWrongDock() && carryingCargo()
			&& dockGuard.isDockClick(event.getMenuOption(), event.getMenuTarget(), event.getId()))
		{
			PortLocation wrong = dockGuard.wrongPort(routingService.plan(), gangplanks);
			if (wrong != null)
			{
				event.consume();
				PortLocation next = routingService.nextStop();
				String nextName = next == null ? "?" : next.getName();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Blocked docking at " + wrong.getName()
					+ ": nothing in your plan there. Next stop is " + nextName + ". Shift-click to dock anyway.", null);
				overheadWarning = "Wrong port - next stop: " + nextName;
				overheadWarningUntil = client.getTickCount() + 8;
				rebuildView();
				log.info("[routing] blocked docking at {} (next stop {})", wrong.getName(), nextName);
			}
		}
		if (config.routingBlockMissingCargo() && isSetSail(event.getMenuOption()) && boatLocator.dockedOnBoat()
			&& !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			// Setting sail from a port where a held task still has crates waiting: easy to miss with 9 crates.
			PortLocation port = boatLocator.dockedPort();
			String left = PortView.reminder(courierTasks, port, this::cargoName);
			if (left != null)
			{
				event.consume();
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Blocked setting sail from " + port.getName()
					+ ": " + left + ". Shift-click to sail anyway.", null);
				overheadWarning = left;
				overheadWarningUntil = client.getTickCount() + 8;
				rebuildView();
				log.info("[routing] blocked setting sail from {}: {}", port.getName(), left);
			}
		}
	}

	/**
	 * A click that can set sail: the helm's "Set heading" (the target is empty, the id the direction; the first one
	 * while docked sets sail) or "Set sail".
	 */
	private static boolean isSetSail(String option)
	{
		String o = option == null ? "" : Text.removeTags(option).trim();
		return "set heading".equalsIgnoreCase(o) || "set sail".equalsIgnoreCase(o);
	}

	/**
	 * True while the player is holding a courier crate in hand (not merely cargo in the hold). The wrong-port
	 * guard only applies then: with empty hands, docking anywhere (notice boards, grabbing another task
	 * mid-route, banking) is fine.
	 */
	private boolean carryingCargo()
	{
		return depositGuard.holdsCourierCrate(courierTasks);
	}

	/** Routing extension: what the port overlays show (see PortView). */
	PortView view()
	{
		return view;
	}

	/**
	 * Routing extension: recomputes what the port overlays show. Called on the events that change it (task
	 * progress, the plan, the inventory, the port the player is at, settings), never per frame.
	 */
	private void rebuildView()
	{
		updateLoopStatus();
		PortLocation docked = boatLocator.dockedPort();
		view = PortView.build(courierTasks, routingService, docked, config, overheadWarning(docked), this::cargoName,
			loopStatus, freeSlots());
	}

	/**
	 * Routing extension: works out where loop mode stands from the remembered loop boards (SPEC-routing.md
	 * §2.4.2), and points Shortest Path at the next unseen board while gathering on foot. Called from
	 * rebuildView, so on the same events.
	 */
	private void updateLoopStatus()
	{
		LoopPorts loop = boardScorer.loop();
		Set<Integer> held = new HashSet<>();
		for (CourierTask t : courierTasks)
		{
			held.add(t.getData().getId());
		}
		LoopStatus status = LoopStatus.of(loop, LoopPorts.parse(config.routingLoopSeaOnly()), unusableBoards(), loopBoards, dbrow ->
		{
			CourierTaskData d = CourierTaskData.getByDbrow(dbrow);
			return d != null && !held.contains(d.getId()) && loop.holds(d.getCargoLocation(), d.getDeliveryLocation())
				&& boardScorer.passesBagFilter(d) && !(sailingLevel > 0 && d.getLevelRequired() > sailingLevel);
		}, tasksToReset());
		boolean phaseChanged = status.phase != loopStatus.phase;
		boolean changed = !status.key().equals(loopStatus.key());
		loopStatus = status;

		BountyHunt hunt = huntStatus();
		boolean huntChanged = !hunt.key().equals(bountyHunt.key());
		bountyHunt = hunt;

		// Off the boat, Shortest Path goes to the loop's next unseen board while gathering, else to the hunt's next
		// board (the two are rarely wanted at once; the gather wins).
		PortLocation next = boatLocator.onBoat() ? null
			: status.phase == LoopStatus.Phase.GATHER ? status.nextUnseen() : hunt.next;
		// A land tile only: the port's navigation tile is at sea, and a land path to it can't be found.
		WorldPoint tile = next == null ? null : loopBoards.tile(next) != null ? loopBoards.tile(next) : NoticeBoardTiles.of(next);
		routingService.setLandTarget(tile);
		if (phaseChanged)
		{
			log.debug("[loop] {}", status.phase);
			if (!offeredTasks.isEmpty())
			{
				rescoreBoard(); // a dry loop ranks fillers
			}
		}
		if (changed)
		{
			SwingUtilities.invokeLater(() -> pluginPanel.showLoopStatus(status));
		}
		if (huntChanged)
		{
			SwingUtilities.invokeLater(() -> pluginPanel.showBountyHunt(hunt));
		}
	}

	/**
	 * Ports behind a quest (wiki, 30 September 2026): Prifddinas needs Song of the Elves; Port Tyras needs
	 * Regicide to dock (and an adamant keel, not checked).
	 */
	private static final Map<PortLocation, Quest> PORT_QUESTS = Map.of(
		PortLocation.PRIFDDINAS, Quest.SONG_OF_THE_ELVES,
		PortLocation.PORT_TYRAS, Quest.REGICIDE);

	/**
	 * Notice boards behind a quest, beyond their port's: Port Tyras' board can't be used before Song of the Elves
	 * even though the port can be docked at with Regicide (seen in game, 30 September 2026).
	 */
	private static final Map<PortLocation, Quest> BOARD_QUESTS = Map.of(
		PortLocation.PRIFDDINAS, Quest.SONG_OF_THE_ELVES,
		PortLocation.PORT_TYRAS, Quest.SONG_OF_THE_ELVES);

	/**
	 * Routing extension: ports the player can't sail to: above their Sailing level, or behind a quest they
	 * haven't finished (PORT_QUESTS). Client thread only.
	 */
	private Set<PortLocation> unreachablePorts()
	{
		Set<PortLocation> out = new HashSet<>();
		int level = sailingLevel;
		for (PortLocation p : PortLocation.values())
		{
			if (level > 0 && p.getSailingLevelRequired() != null && p.getSailingLevelRequired() > level)
			{
				out.add(p);
			}
		}
		addQuestLocked(out, PORT_QUESTS);
		return out;
	}

	/** Routing extension: notice boards the player can't use: at unreachable ports, or behind BOARD_QUESTS. */
	private Set<PortLocation> unusableBoards()
	{
		Set<PortLocation> out = unreachablePorts();
		addQuestLocked(out, BOARD_QUESTS);
		return out;
	}

	/** Adds the ports whose quest isn't known to be finished; a quest not read yet this login counts as unfinished. */
	private void addQuestLocked(Set<PortLocation> out, Map<PortLocation, Quest> quests)
	{
		boolean unknown = false;
		for (Map.Entry<PortLocation, Quest> e : quests.entrySet())
		{
			Boolean done = questsDone.get(e.getValue());
			unknown |= done == null;
			if (!Boolean.TRUE.equals(done))
			{
				out.add(e.getKey());
			}
		}
		if (unknown)
		{
			refreshQuests();
		}
	}

	/**
	 * Reads the gating quests' states once, later on the client thread: it runs a game script, which can't be
	 * done from inside every event handler. Until the answer is in, their ports count as unreachable.
	 */
	private void refreshQuests()
	{
		if (questCheckPending || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		questCheckPending = true;
		clientThread.invokeLater(() ->
		{
			questCheckPending = false;
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			boolean changed = false;
			Set<Quest> quests = new HashSet<>(PORT_QUESTS.values());
			quests.addAll(BOARD_QUESTS.values());
			for (Quest q : quests)
			{
				boolean done = q.getState(client) == QuestState.FINISHED;
				changed |= !Boolean.valueOf(done).equals(questsDone.put(q, done));
			}
			if (changed)
			{
				log.debug("[routing] port quests finished: {}", questsDone);
				rebuildView();
			}
		});
	}

	/**
	 * Routing extension: the bounty hunt for the picked monsters (SPEC-routing.md §2.5), from the boards
	 * remembered this reset cycle and the bounty tasks held.
	 */
	private BountyHunt huntStatus()
	{
		List<String> monsters = bountyWiki.parseMonsters(config.routingBountyHunt());
		if (monsters.isEmpty())
		{
			return BountyHunt.NONE;
		}
		Set<String> heldItems = new HashSet<>();
		for (BountyTask t : bountyTasks)
		{
			BountyWikiData.Task w = bountyWiki.task(t.getData().getId());
			if (w != null)
			{
				heldItems.add(w.item);
			}
		}
		Player player = client.getLocalPlayer();
		return BountyHunt.of(bountyWiki, monsters, unusableBoards(), loopBoards, taskId ->
			{
				BountyTaskData d = BountyTaskData.fromId(taskId);
				return d == null ? -1 : d.getDbrow();
			}, heldItems, sailingLevel, player == null || boatLocator.onBoat() ? null : player.getWorldLocation(),
			t -> t.bag == null ? 0 : rewardValuer.expectedBountyValue(t.bag));
	}

	/** Routing extension: true if this offered task is a bounty for a part the hunt is after (and not held). */
	public boolean hunted(int dbrow)
	{
		BountyTaskData d = BountyTaskData.getByDbrow(dbrow);
		BountyWikiData.Task w = d == null ? null : bountyWiki.task(d.getId());
		if (w == null)
		{
			return false;
		}
		for (BountyHunt.Part p : bountyHunt.parts)
		{
			if (!p.held && p.item.equals(w.item))
			{
				return true;
			}
		}
		return false;
	}

	/** Routing extension: the side panel's monster boxes write the hunt setting through here. */
	public void setHunted(List<String> monsters)
	{
		configManager.setConfiguration(CONFIG_GROUP, "routingBountyHunt", String.join(", ", monsters));
	}

	/** Routing extension: every monster with a bounty, for the side panel. */
	public List<String> bountyMonsters()
	{
		return bountyWiki.monsters();
	}

	/** Port tasks still to complete before the boards reset (the reset tracker's count, today's only). */
	private int tasksToReset()
	{
		String lastStr = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_LAST_TASK_COMPLETED);
		String countStr = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_TASKS_COMPLETED);
		long midnightTodayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
		try
		{
			long last = lastStr == null ? 0 : Long.parseLong(lastStr);
			int count = countStr == null ? 0 : Integer.parseInt(countStr);
			return last < midnightTodayUtc ? 8 : 8 - count;
		}
		catch (NumberFormatException e)
		{
			return 8;
		}
	}

	private int freeSlots()
	{
		return Math.max(0, taskSlots() - courierTasks.size() - bountyTasks.size());
	}

	/** "Crate of lead" -> "lead". Client thread only. */
	private String cargoName(int itemId)
	{
		String name = itemManager.getItemComposition(itemId).getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return "cargo";
		}
		return name.startsWith("Crate of ") ? name.substring("Crate of ".length()) : name;
	}

	/**
	 * Routing extension: the warning to show above the player at this port, if any: a recently blocked dock; a
	 * standing wrong-port reminder (docked, holding a crate, nothing planned here); or a wrong-crate reminder
	 * (docked where deliveries are due, holding a crate for another port).
	 */
	private String overheadWarning(PortLocation docked)
	{
		if (overheadWarning != null)
		{
			return overheadWarning;
		}
		if (config.routingBlockWrongDock() && carryingCargo() && routingService.plan() != null && docked != null
			&& !routingService.hasWorkAt(docked))
		{
			PortLocation next = routingService.nextStop();
			return "Wrong port - next stop: " + (next == null ? "?" : next.getName());
		}
		if (config.routingBlockWrongDeposit())
		{
			return depositGuard.wrongCrateWarning(docked, courierTasks);
		}
		return null;
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onItemContainerChanged(final ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			bagCounter.onInventoryChanged(event.getItemContainer(), client.getTickCount());
		}
		if ((event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN) && !courierTasks.isEmpty())
		{
			rebuildView();
		}
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onGameTick(GameTick event)
	{
		// Whether the player is at a port on foot depends on where they stand, which has no event: one cheap check
		// per tick while tasks are held (or a loop is set: its board reminder), and the view is only rebuilt when
		// the answer (or a timed warning) changes.
		if (!courierTasks.isEmpty() || overheadWarning != null || loopStatus.phase != LoopStatus.Phase.OFF)
		{
			boolean warningOver = overheadWarning != null && client.getTickCount() > overheadWarningUntil;
			if (warningOver)
			{
				overheadWarning = null;
			}
			if (warningOver || boatLocator.dockedPort() != view.dockedPort)
			{
				rebuildView();
			}
		}
		// prune tracked objects that have passed their timer
		bountyCorpses.removeIf(corpse -> Instant.now().toEpochMilli() > corpse.getStartTime().toEpochMilli() + corpse.getDespawnTime());
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onNpcSpawned(NpcSpawned event)
	{
		final int npcId = event.getNpc().getId();
		if (!BountyTaskData.isBountyNpc(npcId))
		{
			return;
		}

		NPC corpseNpc = (NPC) event.getNpc();
		BountyCorpse corpse = new BountyCorpse(corpseNpc, Instant.now(), client.getTickCount(), 300 * Constants.GAME_TICK_LENGTH);
		bountyCorpses.add(corpse);
	}

	@SuppressWarnings("unused")
	@Subscribe
	private void onNpcDespawned(NpcDespawned event)
	{
		bountyCorpses.removeIf(corpse -> corpse.getNpc().equals(event.getNpc()));
	}

	private void handleTaskCompleted()
	{
		String lastStr = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_LAST_TASK_COMPLETED);
		String countStr = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_TASKS_COMPLETED);

		long lastTaskCompleted = lastStr != null ? Long.parseLong(lastStr) : 0L;
		int tasksCompleted = countStr != null ? Integer.parseInt(countStr) : 0;

		long now = Instant.now().getEpochSecond();
		long midnightTodayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
		if (lastTaskCompleted < midnightTodayUtc)
		{
			tasksCompleted = 1;
		}
		else
		{
			tasksCompleted = (tasksCompleted + 1) % 8;
		}
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_TASKS_COMPLETED, tasksCompleted);
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_LAST_TASK_COMPLETED, now);
		if (tasksCompleted == 0)
		{
			// Routing extension: the boards reroll now, so loop mode's memory of them is stale (the daily
			// reset is caught by the memory's own date check).
			loopBoards.reset("8 tasks");
		}
		rebuildView();
		if (config.noticeBoardResetTracker())
		{
			final String message;
			if (tasksCompleted == 0)
			{
				message = "Notice boards have reset.";
			}
			else
			{
				message = String.format(
					"You have completed %d task%s with %d more task%s until board reset.",
					tasksCompleted,
					tasksCompleted == 1 ? "" : "s",
					8 - tasksCompleted,
					(8 - tasksCompleted) == 1 ? "" : "s"
				);
			}
			sendMessage(message);
		}
	}

	private void sendMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage(new ChatMessageBuilder().append(message).build())
			.build());
	}

	private void markTask(MenuEntry entry)
	{
		Widget taskToTag = entry.getWidget();
		Integer dbrow = getDbrowFromWidget(taskToTag);
		if (dbrow == null)
		{
			return;
		}

		WidgetTag existing = getTagForDbrow(dbrow);

		if (existing != null)
		{
			widgetTags.remove(existing);
		}
		else
		{
			WidgetTag tag = new WidgetTag(dbrow, Color.YELLOW);
			widgetTags.add(tag);
		}
		saveWidgetTags();
	}

	private void createTaskColorMenu(String target, Widget widget, WidgetTag tag)
	{
		List<Color> colors = getUsedTagColors();

		for (Color defaultColor : new Color[]{
			Color.YELLOW, Color.RED, Color.GREEN, Color.ORANGE, Color.BLUE
		})
		{
			if (colors.size() < 5 && ! colors.contains(defaultColor))
			{
				colors.add(defaultColor);
			}
		}
		MenuEntry parent = client.createMenuEntry(-2)
			.setOption("Task color")
			.setTarget(target)
			.setType(MenuAction.RUNELITE);

		Menu subMenu = parent.createSubMenu();

		for (final Color c : colors)
		{
			subMenu.createMenuEntry(0)
				.setOption(ColorUtil.prependColorTag("Set color", c))
				.setType(MenuAction.RUNELITE)
				.onClick(
					e -> clientThread.invokeLater(() -> updateWidgetTagColor(tag.getDbrow(), c))
				);
		}

		subMenu.createMenuEntry(0)
			.setOption("Pick color")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> SwingUtilities.invokeLater(() ->
			{
				Color initial = MoreObjects.firstNonNull(tag.getColor(), Color.YELLOW);
				RuneliteColorPicker colorPicker = colorPickerManager.create(
					client,
					initial,
					"Task tag color",
					false
				);

				colorPicker.setOnClose(c ->
					clientThread.invokeLater(() -> updateWidgetTagColor(tag.getDbrow(), c))
				);

				colorPicker.setVisible(true);
			}));
	}

	private WidgetTag getTagForDbrow(int dbrow)
	{
		return widgetTags.stream()
			.filter(t -> t.getDbrow() == dbrow)
			.findFirst()
			.orElse(null);
	}

	private void updateWidgetTagColor(int dbrow, Color color)
	{
		WidgetTag tag = getTagForDbrow(dbrow);
		if (tag != null)
		{
			tag.setColor(color);
		}
		else
		{
			tag = new WidgetTag(dbrow, color);
			widgetTags.add(tag);
		}
		saveWidgetTags();
	}

	private List<Color> getUsedTagColors()
	{
		return widgetTags.stream()
			.map(WidgetTag::getColor)
			.filter(Objects::nonNull)
			.distinct()
			.collect(Collectors.toList());
	}

	private void saveWidgetTags()
	{
		boardChanged();
		if (widgetTags.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_TAGS);
		}
		else
		{
			final String json = gson.toJson(widgetTags);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_TAGS, json);
		}
	}

	private void loadWidgetTags()
	{
		final String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_TAGS);
		if (json == null || json.isEmpty())
		{
			return;
		}

		//CHECKSTYLE:OFF
		Type type = new TypeToken<Set<WidgetTag>>() {}.getType();
		//CHECKSTYLE:ON

		try
		{
			Set<WidgetTag> loaded = gson.fromJson(json, type);
			if (loaded != null)
			{
				widgetTags.clear();
				widgetTags.addAll(loaded);
			}
		}
		catch (Exception e)
		{
			log.info("Failed to load widget tags");
		}
	}

	private void scanPortTaskBoard()
	{
		final Widget widget = client.getWidget(InterfaceID.PortTaskBoard.CONTAINER);
		if (widget == null)
		{
			return;
		}
		Widget[] children = widget.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			Integer dbrow = getDbrowFromWidget(child);
			if (dbrow == null)
			{
				continue;
			}
			int levelRequired = getLevelByDbrow(dbrow);

			offeredTasks.put(dbrow, new OfferedTaskData(child, levelRequired));
		}
		rememberBoard();
		updateBestXpPerTile();
		rebuildView();
		rescoreBoard();
	}

	/**
	 * Routing extension: loop mode remembers this board's courier offers and where it stands (SPEC-routing.md
	 * §2.4.2). Offers that weren't there last time are logged: boards should only change at a reset, and this
	 * checks that in play.
	 */
	private void rememberBoard()
	{
		// Courier and bounty offers both: loop mode reads the courier ones, the bounty hunt the bounty ones.
		PortLocation board = null;
		List<Integer> tasks = new ArrayList<>();
		for (Integer dbrow : offeredTasks.keySet())
		{
			CourierTaskData d = CourierTaskData.getByDbrow(dbrow);
			BountyTaskData b = d == null ? BountyTaskData.getByDbrow(dbrow) : null;
			if (d != null || b != null)
			{
				tasks.add(dbrow);
				board = d != null ? d.getNoticeBoard() : b.getBountyLocation();
			}
		}
		if (board == null)
		{
			return;
		}
		Player player = client.getLocalPlayer();
		boolean seenBefore = loopBoards.seen(board);
		Set<Integer> added = loopBoards.opened(board, tasks, player == null ? null : player.getWorldLocation());
		if (seenBefore && !added.isEmpty())
		{
			log.info("[loop] {} board has {} new offers without a reset ({} tasks to reset): {}", board.getName(),
				added.size(), tasksToReset(), added);
		}
	}

	/**
	 * Routing extension: scores the offered courier tasks against the held ones (SPEC-routing.md §2.2) and
	 * updates the side list. Starts from the boat's dock, or the board's port if the boat's is unknown.
	 */
	private void rescoreBoard()
	{
		List<CourierTaskData> offered = new ArrayList<>();
		for (Integer dbrow : offeredTasks.keySet())
		{
			CourierTaskData d = CourierTaskData.getByDbrow(dbrow);
			if (d != null)
			{
				offered.add(d);
			}
		}
		if (offered.isEmpty())
		{
			boardScores = new HashMap<>();
			boardChanged();
			return;
		}
		PortLocation board = offered.get(0).getNoticeBoard();
		PortLocation start = routingService.boatPort() != null ? routingService.boatPort() : board;
		List<BoardScorer.Score> ranked = boardScorer.score(courierTasks, start, offered, sailingLevel, loopDry());
		Map<Integer, BoardScorer.Score> byDbrow = new HashMap<>();
		for (BoardScorer.Score s : ranked)
		{
			byDbrow.put(s.dbrow, s);
		}
		boardScores = byDbrow;
		lastRanked = ranked;
		lastBoard = board;
		searchBestSet(start, offered);
		boardChanged();
		publishBoard();
	}

	/**
	 * Routing extension: starts a best-set search for this board on its own thread (up to ~0.2 s for a big board
	 * with five free slots), unless nothing it depends on changed since the last one. The result is applied on
	 * the client thread.
	 */
	private void searchBestSet(PortLocation start, List<CourierTaskData> offered)
	{
		if (!config.routingBestSet() || loopDry())
		{
			// A dry loop wants the quickest fillers, not the best rate: no set is suggested.
			bestSet = null;
			bestSetKey = null;
			return;
		}
		int free = Math.max(0, taskSlots() - courierTasks.size() - bountyTasks.size());
		BoardScorer.SetSearch search = boardScorer.setSearch(courierTasks, start, offered, sailingLevel, free);
		if (search.key.equals(bestSetKey))
		{
			return;
		}
		log.debug("[routing] best set: {} task slots at level {} (extra-slots varbit {}), {} free",
			taskSlots(), sailingLevel, client.getVarbitValue(VarbitID.PORT_TASK_EXTRA_SLOTS_UNLOCKED), free);
		bestSetKey = search.key;
		bestSet = null;
		int generation = ++bestSetGeneration;
		setSearchExecutor.execute(() ->
		{
			SubsetChooser.Result result;
			try
			{
				result = search.run();
			}
			catch (RuntimeException e)
			{
				log.warn("[routing] best set search failed", e);
				return;
			}
			clientThread.invokeLater(() ->
			{
				if (generation != bestSetGeneration)
				{
					return; // a newer search replaced this one
				}
				bestSet = result;
				log.debug("[routing] best set {}: {}/tile (now {}), {} sets, exact {}", result.dbrows,
					result.rate, result.heldRate, result.evaluated, result.exact);
				boardChanged();
				publishBoard();
			});
		});
	}

	/** Task slots at the player's Sailing level: 1, plus one each at 7, 28, 56 and 84 (wiki, Port task). */
	private int taskSlots()
	{
		int level = sailingLevel;
		return level >= 84 ? 5 : level >= 56 ? 4 : level >= 28 ? 3 : level >= 7 ? 2 : 1;
	}

	/** Routing extension: true if the best set includes this offered task. */
	public boolean inBestSet(int dbrow)
	{
		SubsetChooser.Result r = bestSet;
		return r != null && config.routingBestSet() && r.dbrows.contains(dbrow);
	}

	/** Routing extension: shows the last board's ranking, and the best set, in the side list. */
	private void publishBoard()
	{
		if (lastBoard == null)
		{
			return;
		}
		BoardScorer.RankBy by = config.routingRankBy();
		boolean dry = loopDry();
		List<PortTasksPluginPanel.BoardRow> rows = new ArrayList<>();
		for (BoardScorer.Score s : lastRanked)
		{
			CourierTaskData d = CourierTaskData.getByDbrow(s.dbrow);
			rows.add(new PortTasksPluginPanel.BoardRow(s.rank, d.getCargoLocation(), d.getDeliveryLocation(), s.name,
				dry ? String.format("+%.0f", s.addedCost) : rankValue(s, by), String.join(", ", s.wantedDrops), s.detourColor,
				inBestSet(s.dbrow), s.offLoop));
		}
		String summary = dry ? "Loop dry: " + loopStatus.tasksToReset + " tasks to board reset" : bestSetSummary();
		String metric = dry ? "Fillers, quickest" : by.toString();
		PortLocation board = lastBoard;
		SwingUtilities.invokeLater(() -> pluginPanel.showBoard(board, metric, rows, summary));
	}

	/** Routing extension: loop mode says the loop boards have nothing worth taking left (fillers until the reset). */
	private boolean loopDry()
	{
		return loopStatus.phase == LoopStatus.Phase.DRY;
	}

	/** One line about the best set for the side list, or null if the feature is off. */
	private String bestSetSummary()
	{
		if (!config.routingBestSet())
		{
			return null;
		}
		if (taskSlots() - courierTasks.size() - bountyTasks.size() <= 0)
		{
			return "Task slots full";
		}
		SubsetChooser.Result r = bestSet;
		if (r == null)
		{
			return "Finding the best set...";
		}
		String unit = config.routingBestSetBy() == BoardScorer.SetObjective.VALUE ? "gp/tile" : "xp/tile";
		String approx = r.exact ? "" : " (top tasks only)";
		if (r.dbrows.isEmpty())
		{
			return String.format("Best: take none, %.1f %s now%s", r.heldRate, unit, approx);
		}
		return String.format("Best set: %d task%s, %.1f %s (now %.1f)%s", r.dbrows.size(), r.dbrows.size() == 1 ? "" : "s",
			r.rate, unit, r.heldRate, approx);
	}

	private static String rankValue(BoardScorer.Score s, BoardScorer.RankBy by)
	{
		switch (by)
		{
			case VALUE_PER_ADDED_TILE:
				return String.format("%.0f gp", s.valuePerAddedTile);
			case ROUTE_FIT:
				return String.format("%.0f%%", s.routeFit * 100);
			case PLAN_RATE_AFTER:
				return String.format("%.1f", s.planRateAfter);
			default:
				return String.format("%.1f", s.xpPerAddedTile);
		}
	}

	/** Routing extension: the score of an offered task on the last board, or null. */
	public BoardScorer.Score boardScore(int dbrow)
	{
		return boardScores.get(dbrow);
	}

	/**
	 * Routing extension (SPEC-routing.md §2.4.1): works out the best loops for the player's level, reachable
	 * ports and allowed bag sizes, and hands them to {@code done} on the Swing thread. The pool is gathered on
	 * the client thread (level, quest state, learned XP); the search runs on the best-set thread. Only on
	 * request from the side panel.
	 */
	public void suggestLoops(Consumer<List<LoopSuggester.Suggestion>> done)
	{
		clientThread.invokeLater(() ->
		{
			int level = sailingLevel;
			Set<PortLocation> usable = LoopSuggester.allPorts();
			usable.removeAll(LoopPorts.parse(config.routingLoopExclude()).ports());
			usable.removeAll(unreachablePorts());
			// A port can be sailed to without its board being usable (Port Tyras before Song of the Elves): its
			// tasks then aren't on offer, though it can still be a pickup or delivery.
			Set<PortLocation> noBoard = unusableBoards();
			List<LoopSuggester.Candidate> pool = new ArrayList<>();
			for (CourierTaskData d : CourierTaskData.all())
			{
				Integer xp = xpLearner.xp(d.getId());
				if (xp == null || noBoard.contains(d.getNoticeBoard()) || level > 0 && d.getLevelRequired() > level || !BoardScorer.bagEnabled(config, BagSize.forXp(xp)))
				{
					continue;
				}
				pool.add(new LoopSuggester.Candidate(d.getNoticeBoard(), d.getCargoLocation(), d.getDeliveryLocation(), xp));
			}
			log.debug("[routing] loop suggestions: {} pool tasks, {} usable ports, level {}", pool.size(), usable.size(), level);
			setSearchExecutor.execute(() ->
			{
				List<LoopSuggester.Suggestion> best = new LoopSuggester(routingService.graph()::distance).suggest(pool, usable, 6);
				SwingUtilities.invokeLater(() -> done.accept(best));
			});
		});
	}

	/** Routing extension: sets the loop (the side panel's suggestions write it through here). */
	public void setLoop(List<PortLocation> ports)
	{
		configManager.setConfiguration(CONFIG_GROUP, "routingLoop",
			ports.stream().map(PortLocation::getName).collect(Collectors.joining(", ")));
	}

	/** Routing extension: the side panel's bag-size boxes write the config through here. */
	public void setBagEnabled(BagSize size, boolean on)
	{
		configManager.setConfiguration(CONFIG_GROUP, size.filterKey(), on);
	}

	/** The single "only Large/Huge bags" toggle became one toggle per size; carry an old "on" across once. */
	private void migrateOnlyBigBags()
	{
		String old = configManager.getConfiguration(CONFIG_GROUP, "routingOnlyBigBags");
		if (old == null)
		{
			return;
		}
		if (Boolean.parseBoolean(old))
		{
			for (BagSize s : new BagSize[]{BagSize.TINY, BagSize.SMALL, BagSize.MEDIUM})
			{
				configManager.setConfiguration(CONFIG_GROUP, s.filterKey(), false);
			}
		}
		configManager.unsetConfiguration(CONFIG_GROUP, "routingOnlyBigBags");
	}

	/** Routing extension: true if the "only Large/Huge bags" filter rules out this offered courier task. */
	public boolean bagFilterHides(CourierTaskData d)
	{
		// A dry loop wants any task that gets the boards to their reset, so nothing is dimmed then.
		return !loopDry() && !boardScorer.passesBagFilter(d);
	}

	/** Routing extension: base XP for a task (learned from play, else the wiki), or null. */
	public Integer taskXp(int taskId)
	{
		return xpLearner.xp(taskId);
	}

	public PortTasksConfig routingConfig()
	{
		return config;
	}

	public Integer getDbrowFromWidget(Widget widget)
	{
		if (widget == null)
		{
			return null;
		}
		Object[] ops = widget.getOnOpListener();
		if (ops == null || ops.length < 4)
		{
			return null;
		}
		return (Integer) ops[3];

	}

	private boolean isInHelmRange(int id)
	{
		return id >= ObjectID.SAILING_BOAT_STEERING_KANDARIN_1X3_WOOD && id <= ObjectID.SAILING_INTRO_HELM_NOT_IN_USE;
	}

	private boolean isInCargoHoldRange(int id)
	{
		return SAILING_BOAT_CARGO_HOLDS.contains(id);
	}

	@SuppressWarnings("unused")
	@Provides
	PortTasksConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PortTasksConfig.class);
	}

	private void handlePortTaskTrigger(PortTaskTrigger trigger, int value)
	{
		int slot = trigger.getSlot();
		switch (trigger.getType())
		{
			case ID:
				log.debug("Changed: {} (value {})", trigger, value);
				// One task per slot: a new id replaces whatever the slot held (the id can change without passing
				// through 0, e.g. when varbits are re-sent at login).
				removeTasksForSlot(slot);
				if (value == 0)
				{
					refreshPanel();
					return;
				}
				CourierTaskData courrierData = CourierTaskData.fromId(value);
				if (courrierData != null)
				{
					// Progress varbits may arrive before the id at login, when there was no task to apply them to.
					courierTasks.add(new CourierTask(courrierData, slot, false, slotVarbit(slot, PortTaskTrigger.TaskType.DELIVERED),
						true, taskColour(slot, courrierData.getDbrow()), slotVarbit(slot, PortTaskTrigger.TaskType.TAKEN)));
					routingDiagnostics.logTask(slot, courrierData);
					refreshPanel();
					return;
				}

				BountyTaskData bountyData = BountyTaskData.fromId(value);
				if (bountyData != null)
				{
					bountyTasks.add(new BountyTask(bountyData, slot, false, 0, true, taskColour(slot, bountyData.getDbrow()), 0));
					refreshPanel();
				}
				return;

			case TAKEN:
				for (CourierTask task : courierTasks)
				{
					if (task.getSlot() == slot)
					{
						task.setCargoTaken(value);
						refreshPanel();
						return;
					}
				}
				return;

			case DELIVERED:
				for (CourierTask task : courierTasks)
				{
					if (task.getSlot() == slot)
					{
						task.setDelivered(value);
						if (value >= task.getData().cargoAmount)
						{
							xpLearner.expectCompletion(task.getData().getId(), client.getTickCount());
							bagCounter.onTaskCompleted(client.getTickCount());
						}
						refreshPanel();
						return;
					}
				}
				return;

			case COUNT:
				for (BountyTask task : bountyTasks)
				{
					if (task.getSlot() == slot)
					{
						int required = task.getData().itemQuantity;
						int remaining = value;
						int collected = Math.max(0, Math.min(required, required - remaining));

						task.setItemsCollected(collected);
						SwingUtilities.invokeLater(() -> pluginPanel.updateBountyPanel(task));
						return;
					}
				}
				return;
		}
	}

	/** The current value of one of a slot's task varbits. */
	private int slotVarbit(int slot, PortTaskTrigger.TaskType type)
	{
		for (PortTaskTrigger t : PortTaskTrigger.values())
		{
			if (t.getSlot() == slot && t.getType() == type)
			{
				return client.getVarbitValue(t.getId());
			}
		}
		return 0;
	}

	/**
	 * Rebuilds the side panel on the Swing thread (task events arrive on the client thread, and Swing must only
	 * be touched from its own), from a copy of the task lists so the client thread can keep changing them.
	 */
	private void refreshPanel()
	{
		List<Task> tasks = new ArrayList<>(courierTasks);
		tasks.addAll(bountyTasks);
		SwingUtilities.invokeLater(() -> pluginPanel.rebuild(tasks));
	}

	private void removeTasksForSlot(int slot)
	{
		courierTasks.removeIf(t -> t.getSlot() == slot);
		bountyTasks.removeIf(t -> t.getSlot() == slot);
	}

	public void readPortDataFromClientVarps()
	{
		assert client.getVarps() != null : "client.getVarps() is null";
		varPlayers = client.getVarps().clone();
		clearTasksForReload();
		for (PortTaskTrigger varbit : PortTaskTrigger.values())
		{
			int value = client.getVarbitValue(varPlayers, varbit.getId());
			handlePortTaskTrigger(varbit, value);
		}
		// Plans are otherwise made on varbit changes, and a reload (e.g. after a plugin restart) changes none.
		routingService.replan(courierTasks);
		if (!offeredTasks.isEmpty())
		{
			rescoreBoard();
		}
		rebuildView();
	}

	private void clearTasksForReload()
	{
		courierTasks.clear();
		bountyTasks.clear();
	}

	private void registerOverlays()
	{
		if (config.noticeBoardTooltip())
		{
			overlayManager.add(noticeBoardTooltip);
		}
		if (config.corpseOverlay() != PortTasksConfig.Despawn.NONE)
		{
			overlayManager.add(despawnTimerOverlay);
		}
		overlayManager.add(portTasksLedgerOverlay);
		overlayManager.add(portTaskModelRenderer);
	}

	/**
	 * Saves the colours of the tasks held now (called when a colour is picked in the side panel). Only held
	 * tasks are kept, so a finished task's colour doesn't outlive it.
	 */
	public void saveSlotSettings()
	{
		taskColours.clear();
		for (CourierTask t : courierTasks)
		{
			putTaskColour(t.getSlot(), t.getData().getDbrow(), t.getOverlayColor());
		}
		for (BountyTask t : bountyTasks)
		{
			putTaskColour(t.getSlot(), t.getData().getDbrow(), t.getOverlayColor());
		}
		if (taskColours.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_TASK_COLOURS);
		}
		else
		{
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_TASK_COLOURS, gson.toJson(taskColours));
		}
		clientThread.invokeLater(this::rebuildView);
	}

	private void putTaskColour(int slot, int dbrow, Color colour)
	{
		// Only colours that differ from the slot's default need remembering.
		if (colour != null && !colour.equals(getNavColorForSlot(slot)))
		{
			taskColours.put(taskColourKey(slot, dbrow), colour.getRGB());
		}
	}

	private void loadTaskColours()
	{
		taskColours.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_TASK_COLOURS);
		if (json == null)
		{
			return;
		}
		try
		{
			TaskColours stored = gson.fromJson(json, TaskColours.class);
			if (stored != null)
			{
				taskColours.putAll(stored);
			}
		}
		catch (RuntimeException e)
		{
			log.debug("task colours unreadable; using slot colours", e);
		}
	}

	/** A task's colour: the one picked for this task in this slot, else the slot's default. */
	private Color taskColour(int slot, int dbrow)
	{
		Integer rgb = taskColours.get(taskColourKey(slot, dbrow));
		return rgb != null ? new Color(rgb, true) : getNavColorForSlot(slot);
	}

	/** Slot and task together: a new task taken into the slot starts from the slot's colour again. */
	private static String taskColourKey(int slot, int dbrow)
	{
		return slot + ":" + dbrow;
	}

	private Color getNavColorForSlot(int slot)
	{
		switch (slot)
		{
			case 0: return config.getNavColor();
			case 1: return config.getNavColor2();
			case 2: return config.getNavColor3();
			case 3: return config.getNavColor4();
			case 4: return config.getNavColor5();
			default: return Color.GREEN;
		}
	}

	int getInventoryItemCount(int itemId)
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return 0;
		}
		int total = 0;
		for (Item item : inv.getItems())
		{
			if (item.getId() == itemId)
			{
				total += item.getQuantity();
			}
		}
		return total;
	}

	private int getCount(Item[] items, int itemId)
	{
		int amt = 0;
		for (Item item : items)
		{
			if (item.getId() == itemId)
			{
				amt += item.getQuantity();
			}
		}
		return amt;
	}

	/**
	 * Settings of features this fork removed (the per-task path lines and their tracer, the task-item
	 * outlines, and the routing on/off, Shortest Path and western-ports switches, now always on or gone).
	 * Cleared so they don't linger in the profile.
	 */
	private static final String[] REMOVED_KEYS = {
		"drawOverlay", "pathOffset", "pathDrawDistance",
		"enableTracer", "tracerSpeed", "tracerIntensity", "highlightTaskItems", "routingEnabled", "routingUseShortestPath",
		"routingWestOnly", "porttaskslots", "routingLegCounter",
		"routingLearnLegs", "routingLegEstimate", "routingForgetLegs", "routingLearnedLegs",
	};

	private void migrateConfiguration()
	{
		for (String key : REMOVED_KEYS)
		{
			if (configManager.getConfiguration(CONFIG_GROUP, key) != null)
			{
				configManager.unsetConfiguration(CONFIG_GROUP, key);
			}
		}
	}

	public static int getLevelByDbrow(int dbrow)
	{
		CourierTaskData courier = CourierTaskData.getByDbrow(dbrow);
		if (courier != null)
		{
			return courier.getLevelRequired();
		}

		BountyTaskData bounty = BountyTaskData.getByDbrow(dbrow);
		if (bounty != null)
		{
			return bounty.getLevelRequired();
		}

		return -1;
	}

	private int mapOpacity(int configValue)
	{
		return 0 + (configValue - 0) * (255 - 0) / (100 - 0);
	}
}
