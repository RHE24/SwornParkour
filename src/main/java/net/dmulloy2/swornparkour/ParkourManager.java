package net.dmulloy2.swornparkour;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.dmulloy2.swornparkour.parkour.ParkourGame;
import net.dmulloy2.swornparkour.parkour.objects.ParkourCreator;
import net.dmulloy2.swornparkour.parkour.objects.ParkourKickReason;
import net.dmulloy2.swornparkour.parkour.objects.ParkourPlayer;
import net.dmulloy2.swornparkour.parkour.objects.ParkourZone;
import net.dmulloy2.swornparkour.parkour.objects.SavedParkourPlayer;
import net.dmulloy2.swornparkour.util.InventoryWorkaround;
import net.dmulloy2.swornparkour.util.Util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * @author dmulloy2
 */

public class ParkourManager 
{
	public List<ParkourGame> parkourGames = new ArrayList<ParkourGame>();
	public List<ParkourCreator> creators = new ArrayList<ParkourCreator>();
	public HashMap<String, List<ItemStack>> redemption = new HashMap<String, List<ItemStack>>();
	
	public SwornParkour plugin;
	public ParkourManager(SwornParkour plugin)
	{
		this.plugin = plugin;
	}
	
	public void joinParkour(Player player, int gameId)
	{
		if (redemption.containsKey(player.getName()))
			redemption.remove(player.getName());
		
		ParkourPlayer pPlayer = newParkourPlayer(player, gameId);
		ParkourGame pGame = newParkourGame(pPlayer, gameId);
		
		pGame.join();
	}
	
	public ParkourPlayer newParkourPlayer(Player player, int gameId)
	{
		ParkourPlayer parkourPlayer = new ParkourPlayer(plugin, player, gameId, player.getLocation());
		return parkourPlayer;
	}
	
	public ParkourGame newParkourGame(ParkourPlayer player, int gameId)
	{
		ParkourZone zone = plugin.getParkourZone(gameId);
		ParkourGame parkourGame = new ParkourGame(plugin, player, zone, gameId);
		parkourGames.add(parkourGame);
		
		return parkourGame;
	}
	
	public boolean isInParkour(Player player)
	{
		for (ParkourGame parkourGame : parkourGames)
		{
			if (parkourGame.getParkourPlayer().getPlayer().getName().equals(player.getName()))
			{
				return true;
			}
		}
		return false;
	}
	
	public ParkourPlayer getParkourPlayer(Player player)
	{
		for (ParkourGame parkourGame : parkourGames)
		{
			ParkourPlayer parkourPlayer = parkourGame.getParkourPlayer();
			if (parkourPlayer.getPlayer().getName().equals(player.getName()))
			{
				return parkourPlayer;
			}
		}
		return null;
	}
	
	public void createNewParkourGame(Player player)
	{
		ParkourCreator creator = new ParkourCreator(plugin, player, plugin.loadedArenas.size() + 1);
		creator.start();
		
		creators.add(creator);
	}
	
	public ParkourCreator getParkourCreator(Player player)
	{
		for (ParkourCreator creator : creators)
		{
			if (creator.player.getName().equals(player.getName()))
			{
				return creator;
			}
		}
		return null;
	}
	
	public boolean isCreatingArena(Player player)
	{
		for (ParkourCreator creator : creators)
		{
			if (creator.player.getName().equals(player.getName()))
			{
				return true;
			}
		}
		return false;
	}
	
	public ParkourGame getParkourGame(Player player)
	{
		if (getParkourPlayer(player) != null)
		{
			return getParkourGame(getParkourPlayer(player));
		}
		return null;
	}
	
	public ParkourGame getParkourGame(ParkourPlayer player)
	{
		for (ParkourGame game : parkourGames)
		{
			if (game.getParkourPlayer() == player)
			{
				return game;
			}
		}
		return null;
	}
	
	public void onShutdown()
	{
		for (ParkourGame game : parkourGames)
		{
			game.kick(ParkourKickReason.SHUTDOWN);
		}
		
		redemption.clear();
		parkourGames.clear();
		creators.clear();
	}
	
	public boolean deleteArena(int gameId)
	{
		for (ParkourGame game : parkourGames)
		{
			if (game.getId() == gameId)
			{
				game.kick(ParkourKickReason.FORCE_KICK);
				parkourGames.remove(game);
			}
		}
		
		plugin.loadedArenas.remove(plugin.getParkourZone(gameId));
		
		return (plugin.getFileHelper().deleteFile(gameId));
	}
	
	public void normalizeSavedPlayer(SavedParkourPlayer savedPlayer)
	{
		String name = savedPlayer.getName();
		Location spawnback = savedPlayer.getSpawnBack();
		List<ItemStack> itemContents = savedPlayer.getItems();
		List<ItemStack> armorContents = savedPlayer.getArmor();
		
		Player player = Util.matchPlayer(name);
		PlayerInventory inv = player.getInventory();
		for (ItemStack itemStack : itemContents)
		{
			InventoryWorkaround.addItems(inv, itemStack);
		}
		
		for (ItemStack armor : armorContents)
		{
			String type = armor.getType().toString().toLowerCase();
			if (type.contains("helmet"))
			{
				inv.setHelmet(armor);
			}
			
			if (type.contains("chestplate"))
			{
				inv.setChestplate(armor);
			}
			
			if (type.contains("leggings"))
			{
				inv.setLeggings(armor);
			}
			
			if (type.contains("boots"))
			{
				inv.setBoots(armor);
			}
		}
		
		player.teleport(spawnback);
		
		plugin.getFileHelper().deleteSavedPlayer(name);
	}
	
	public boolean inventoryHasRoom(Player player, ItemStack item)
	{
		final int maxStackSize = (item.getMaxStackSize() == -1) ? player.getInventory().getMaxStackSize() : item.getMaxStackSize();
		int amount = item.getAmount();
		
		for (ItemStack stack : player.getInventory().getContents())
		{
			if (stack == null || stack.getType().equals(Material.AIR))
				amount -= maxStackSize;			
			else if (stack.getTypeId() == item.getTypeId() && 
					stack.getDurability() == item.getDurability() &&
					(stack.getEnchantments().size() == 0 ? item.getEnchantments().size() == 0 :
						stack.getEnchantments().equals(item.getEnchantments())))
				amount -= maxStackSize - stack.getAmount();
			
			if (amount <= 0)
				return true;
		}
		return false;
	}
}