/**
* SwornParkour - a bukkit plugin
* Copyright (C) 2013 - 2014 dmulloy2
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package net.dmulloy2.swornparkour;
 
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.MissingResourceException;
import java.util.logging.Level;

import lombok.Getter;
import net.dmulloy2.swornparkour.commands.CmdAbandon;
import net.dmulloy2.swornparkour.commands.CmdClaim;
import net.dmulloy2.swornparkour.commands.CmdCreate;
import net.dmulloy2.swornparkour.commands.CmdDelete;
import net.dmulloy2.swornparkour.commands.CmdHelp;
import net.dmulloy2.swornparkour.commands.CmdJoin;
import net.dmulloy2.swornparkour.commands.CmdKick;
import net.dmulloy2.swornparkour.commands.CmdLeave;
import net.dmulloy2.swornparkour.commands.CmdList;
import net.dmulloy2.swornparkour.commands.CmdReload;
import net.dmulloy2.swornparkour.commands.CmdSetPoint;
import net.dmulloy2.swornparkour.commands.CmdSpawn;
import net.dmulloy2.swornparkour.commands.CmdVersion;
import net.dmulloy2.swornparkour.handlers.CommandHandler;
import net.dmulloy2.swornparkour.handlers.FileHandler;
import net.dmulloy2.swornparkour.handlers.LogHandler;
import net.dmulloy2.swornparkour.handlers.ParkourHandler;
import net.dmulloy2.swornparkour.handlers.PermissionHandler;
import net.dmulloy2.swornparkour.handlers.ResourceHandler;
import net.dmulloy2.swornparkour.listeners.BlockListener;
import net.dmulloy2.swornparkour.listeners.PlayerListener;
import net.dmulloy2.swornparkour.tasks.ParkourJoinTask;
import net.dmulloy2.swornparkour.types.ParkourReward;
import net.dmulloy2.swornparkour.types.ParkourSign;
import net.dmulloy2.swornparkour.types.ParkourZone;
import net.dmulloy2.swornparkour.util.Util;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;

/**
 * @author dmulloy2
 */

public class SwornParkour extends JavaPlugin
{	
	/** Handlers **/
	private @Getter PermissionHandler permissionHandler;
	private @Getter ResourceHandler resourceHandler;
	private @Getter CommandHandler commandHandler;
	private @Getter ParkourHandler parkourHandler;
	private @Getter FileHandler fileHandler;
	private @Getter LogHandler logHandler;

	/** Integration **/
	private @Getter WorldEditPlugin worldEdit;
	private @Getter Economy economy;

	/** Lists **/
	private @Getter List<ParkourSign> signs = new ArrayList<ParkourSign>();
	private @Getter List<ParkourZone> loadedArenas = new ArrayList<ParkourZone>();

	/** HashMaps **/
	private @Getter HashMap<Player, ParkourJoinTask> waiting = new HashMap<Player, ParkourJoinTask>();
	private @Getter HashMap<Integer, ParkourReward> parkourRewards = new HashMap<Integer, ParkourReward>();

	/** Configuration **/
	private @Getter int teleportTimer, cashRewardMultiplier;
	private @Getter boolean cumulativeRewards, itemRewardsEnabled, cashRewardsEnabled, debug;

	/** Global Prefix **/
	private @Getter String prefix = ChatColor.GOLD + "[Parkour] ";

	@Override
	public void onEnable()
	{
		long start = System.currentTimeMillis();

		/** Register Handlers **/
		logHandler = new LogHandler(this);
		commandHandler = new CommandHandler(this);
		permissionHandler = new PermissionHandler();

		saveResource("messages.properties", true);
		resourceHandler = new ResourceHandler(this, this.getClassLoader());

		parkourHandler = new ParkourHandler(this);
		fileHandler = new FileHandler(this);

		/** Configuration **/
		createDirectories();
		saveDefaultConfig();
		loadConfig();

		updateRewards();
		loadGames();

		fileHandler.loadSigns();

		/** Register Events **/
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(new PlayerListener(this), this);
		pm.registerEvents(new BlockListener(this), this);

		/** Integration **/
		setupWorldEditIntegration();
		setupVaultIntegration();

		/** Register Commands **/
		commandHandler.setCommandPrefix("parkour");
		commandHandler.registerCommand(new CmdAbandon(this));
		commandHandler.registerCommand(new CmdClaim(this));
		commandHandler.registerCommand(new CmdCreate(this));
		commandHandler.registerCommand(new CmdDelete(this));
		commandHandler.registerCommand(new CmdHelp(this));
		commandHandler.registerCommand(new CmdJoin(this));
		commandHandler.registerCommand(new CmdKick(this));
		commandHandler.registerCommand(new CmdLeave(this));
		commandHandler.registerCommand(new CmdList(this));
		commandHandler.registerCommand(new CmdReload(this));
		commandHandler.registerCommand(new CmdSetPoint(this));
		commandHandler.registerCommand(new CmdSpawn(this));
		commandHandler.registerCommand(new CmdVersion(this));

		long finish = System.currentTimeMillis();

		outConsole("{0} has been enabled ({1}ms)", getDescription().getFullName(), finish - start);
	}

	private void createDirectories()
	{
		File games = new File(getDataFolder(), "games");
		if (!games.exists())
		{
			games.mkdir();
		}

		File players = new File(getDataFolder(), "players");
		if (players.exists())
		{
			File[] children = players.listFiles();
			if (children != null && children.length > 0)
			{
				for (File child : children)
				{
					child.delete();
				}
			}

			players.delete();
		}
	}

	@Override
	public void onDisable()
	{
		long start = System.currentTimeMillis();

		getServer().getServicesManager().unregisterAll(this);
		getServer().getScheduler().cancelTasks(this);

		for (ParkourZone zone : loadedArenas)
		{
			fileHandler.save(zone);
		}

		clearMemory();

		long finish = System.currentTimeMillis();

		outConsole("{0} has been disabled ({1}ms)", getDescription().getFullName(), finish - start);
	}

	public void loadGames()
	{
		File folder = new File(getDataFolder(), "games");
		File[] children = folder.listFiles();
		for (File file : children)
		{
			fileHandler.load(file);
		}
	}

	public void updateSigns(int gameId)
	{
		for (ParkourSign sign : signs)
		{
			if (sign.getZone().getGameId() == gameId)
			{
				sign.update();
			}
		}
	}

	public ParkourSign getParkourSign(Location loc)
	{
		for (ParkourSign sign : signs)
		{
			if (Util.checkLocation(sign.getLocation(), loc))
				return sign;
		}

		return null;
	}

	private final void loadConfig()
	{
		teleportTimer = getConfig().getInt("teleport-timer");
		cumulativeRewards = getConfig().getBoolean("item-rewards-cumulative");
		itemRewardsEnabled = getConfig().getBoolean("item-rewards-enabled");
		cashRewardsEnabled = getConfig().getBoolean("cash-reward.enabled");
		cashRewardMultiplier = getConfig().getInt("cash-reward.multiplier");
		debug = getConfig().getBoolean("debug");
	}

	public void reload()
	{
		reloadConfig();
		updateRewards();
		loadConfig();
	}

	/** Console Logging **/
	public void outConsole(String string, Object... objects)
	{
		logHandler.log(string, objects);
	}

	public void outConsole(Level level, String string, Object... objects)
	{
		logHandler.log(level, string, objects);
	}

	public void debug(String string, Object... objects)
	{
		if (debug)
			outConsole("[Debug] " + string, objects);
	}

	/** Messages **/
	public String getMessage(String string)
	{
		try
		{
			// TODO: Move all the messages to messages.properties
			return resourceHandler.getMessages().getString(string);
		}
		catch (MissingResourceException ex)
		{
			outConsole(Level.WARNING, "Messages locale is missing key for: {0}", string);
			return null;
		}
	}

	/** Update Rewards Table **/
	public void updateRewards()
	{
		parkourRewards.clear();

		for (int i = 0; i < 25; i++)
		{
			String reward = getConfig().getString("item-rewards." + i);

			fileHandler.readReward(i, reward);
		}

		outConsole("Loaded all rewards!");
	}

	public ParkourZone getParkourZone(int gameId)
	{
		for (ParkourZone zone : loadedArenas)
		{
			if (zone.getGameId() == gameId)
			{
				return zone;
			}
		}

		return null;
	}

	/**
	 * Vault {@link Economy} integration
	 */
	private final void setupVaultIntegration()
	{
		PluginManager pm = getServer().getPluginManager();
		if (pm.isPluginEnabled("Vault"))
		{
			RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
			if (economyProvider != null)
			{
				economy = economyProvider.getProvider();

				outConsole("Enabled economy through {0}!", economy.getName());
			}
			else
			{
				outConsole("Failed to hook into Vault economy.");
			}
		}
	}

	/**
	 * Sets up integration with WorldEdit
	 */
	private final void setupWorldEditIntegration()
	{
		PluginManager pm = getServer().getPluginManager();
		if (pm.isPluginEnabled("WorldEdit"))
		{
			try
			{
				Plugin plugin = pm.getPlugin("WorldEdit");
				if (plugin instanceof WorldEditPlugin)
				{
					worldEdit = (WorldEditPlugin) plugin;

					outConsole("Integration with WorldEdit successful!");
					return;
				}
			}
			catch (Throwable ex)
			{
				//
			}

			outConsole(Level.WARNING, "Could not hook into WorldEdit!");
		}
	}

	private void clearMemory()
	{
		parkourHandler.onShutdown();

		parkourRewards.clear();
		loadedArenas.clear();
		waiting.clear();
	}

	public void removeSign(ParkourSign sign)
	{
		signs.remove(sign);
		fileHandler.updateSignSave();
	}
}