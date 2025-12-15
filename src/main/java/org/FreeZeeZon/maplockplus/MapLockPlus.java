package org.FreeZeeZon.maplockplus;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapLockPlus extends JavaPlugin implements Listener, TabCompleter {

    private static final NamespacedKey LOCKED_MAP_KEY = new NamespacedKey("maplock", "locked");
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("maplock", "owner");
    private static final NamespacedKey ANONYMOUS_KEY = new NamespacedKey("maplock", "anonymous");

    // Messages from config
    private String msgPlayersOnly;
    private String msgMustHoldMap;
    private String msgNotOwner;
    private String msgUnlocked;
    private String msgLocked;
    private String msgOnlyYouUnlock;
    private String msgCannotCopy;
    private String msgConfigReloaded;
    private String msgNoPermission;

    // Lore for locked maps
    private List<String> lockedMapLore;
    private List<String> anonymousMapLore;
    private boolean showOwnerInLore;
    private String ownerFormat;

    // Crafter support (1.21+)
    private boolean crafterSupported = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMessages();
        checkCrafterSupport();
        getServer().getPluginManager().registerEvents(this, this);

        // Register TabCompleter
        if (getCommand("maplock") != null) {
            getCommand("maplock").setTabCompleter(this);
        }

        getLogger().info("MapLockPlus enabled!");
    }

    private void checkCrafterSupport() {
        try {
            InventoryType.valueOf("CRAFTER");
            crafterSupported = true;
            getLogger().info("Crafter supported (1.21+)");
        } catch (IllegalArgumentException e) {
            crafterSupported = false;
            getLogger().info("Crafter not supported (version < 1.21)");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MapLockPlus disabled!");
    }

    private void loadMessages() {
        msgPlayersOnly = colorize(getConfig().getString("messages.players-only", "&cThis command can only be used by players!"));
        msgMustHoldMap = colorize(getConfig().getString("messages.must-hold-map", "&cYou must hold a filled map to use this command!"));
        msgNotOwner = colorize(getConfig().getString("messages.not-owner", "&cYou are not the owner of this map! Only the owner can unlock it."));
        msgUnlocked = colorize(getConfig().getString("messages.unlocked", "&aMap unlocked! It can now be copied."));
        msgLocked = colorize(getConfig().getString("messages.locked", "&aMap locked! It can no longer be copied."));
        msgOnlyYouUnlock = colorize(getConfig().getString("messages.only-you-unlock", "&7Only you can unlock this map."));
        msgCannotCopy = colorize(getConfig().getString("messages.cannot-copy", "&cYou cannot copy a locked map!"));
        msgConfigReloaded = colorize(getConfig().getString("messages.config-reloaded", "&aConfig successfully reloaded!"));
        msgNoPermission = colorize(getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!"));

        showOwnerInLore = getConfig().getBoolean("lore.show-owner", true);
        ownerFormat = colorize(getConfig().getString("lore.owner-format", "&7Owner: &f%owner%"));

        lockedMapLore = new ArrayList<>();
        List<String> loreFromConfig = getConfig().getStringList("lore.locked-map");
        for (String line : loreFromConfig) {
            lockedMapLore.add(colorize(line));
        }

        anonymousMapLore = new ArrayList<>();
        List<String> anonymousLoreFromConfig = getConfig().getStringList("lore.anonymous-map");
        for (String line : anonymousLoreFromConfig) {
            anonymousMapLore.add(colorize(line));
        }
    }

    private String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // ============ TAB COMPLETER ============

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("maplock")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            // Add "anon" option
            if ("anon".startsWith(input) || "anonim".startsWith(input) || "anonymous".startsWith(input)) {
                completions.add("anon");
            }

            // Add "reload" option only if player has permission
            if (sender.hasPermission("maplock.reload") && "reload".startsWith(input)) {
                completions.add("reload");
            }

            return completions;
        }

        // Return empty list to prevent player name suggestions
        return Collections.emptyList();
    }

    // ============ COMMAND HANDLER ============

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Reload command
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("maplock.reload")) {
                sender.sendMessage(msgNoPermission);
                return true;
            }
            reloadConfig();
            loadMessages();
            sender.sendMessage(msgConfigReloaded);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(msgPlayersOnly);
            return true;
        }

        Player player = (Player) sender;

        // Permission check
        if (!player.hasPermission("maplock.use")) {
            player.sendMessage(msgNoPermission);
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() != Material.FILLED_MAP) {
            player.sendMessage(msgMustHoldMap);
            return true;
        }

        // Check for anonymous lock
        boolean anonymous = false;
        if (args.length > 0 && (args[0].equalsIgnoreCase("anonim") ||
                args[0].equalsIgnoreCase("anonymous") ||
                args[0].equalsIgnoreCase("anon"))) {
            anonymous = true;
        }

        ItemStack modifiedItem = itemInHand.clone();
        modifiedItem.setAmount(1);

        ItemMeta meta = modifiedItem.getItemMeta();
        if (meta == null) return true;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        String playerUUID = player.getUniqueId().toString();

        if (container.has(LOCKED_MAP_KEY, PersistentDataType.BYTE)) {
            // UNLOCK
            String ownerUUID = container.get(OWNER_KEY, PersistentDataType.STRING);
            if (ownerUUID != null && !ownerUUID.equals(playerUUID)) {
                player.sendMessage(msgNotOwner);
                return true;
            }

            container.remove(LOCKED_MAP_KEY);
            container.remove(OWNER_KEY);
            container.remove(ANONYMOUS_KEY);

            meta.setLore(null);

            player.sendMessage(msgUnlocked);
        } else {
            // LOCK
            container.set(LOCKED_MAP_KEY, PersistentDataType.BYTE, (byte) 1);
            container.set(OWNER_KEY, PersistentDataType.STRING, playerUUID);

            List<String> lore;

            if (anonymous) {
                container.set(ANONYMOUS_KEY, PersistentDataType.BYTE, (byte) 1);
                lore = new ArrayList<>(anonymousMapLore);
            } else {
                lore = new ArrayList<>(lockedMapLore);

                if (showOwnerInLore) {
                    String ownerLine = ownerFormat.replace("%owner%", player.getName());
                    lore.add(ownerLine);
                }
            }

            meta.setLore(lore);
            player.sendMessage(msgLocked);
            player.sendMessage(msgOnlyYouUnlock);
        }

        modifiedItem.setItemMeta(meta);

        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
            player.getInventory().addItem(modifiedItem);
        } else {
            player.getInventory().setItemInMainHand(modifiedItem);
        }
        return true;
    }

    // ============ EVENT HANDLERS ============

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isMapLocked(item)) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItem(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isMapLocked(item)) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                if (event.getWhoClicked() instanceof Player) {
                    event.getWhoClicked().sendMessage(msgCannotCopy);
                }
                return;
            }
        }

        ItemStack result = event.getRecipe().getResult();
        if (result != null && result.getType() == Material.FILLED_MAP) {
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item != null && item.getType() == Material.FILLED_MAP) {
                    if (isMapLocked(item)) {
                        event.setCancelled(true);
                        event.setResult(Event.Result.DENY);
                        if (event.getWhoClicked() instanceof Player) {
                            event.getWhoClicked().sendMessage(msgCannotCopy);
                        }
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!crafterSupported) return;

        if (isCrafterInventory(event.getDestination().getType())) {
            if (isMapLocked(event.getItem())) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isCrafterInventory(InventoryType type) {
        if (!crafterSupported) return false;
        return type.name().equals("CRAFTER");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        InventoryType type = event.getView().getType();

        boolean isProtectedInventory = type == InventoryType.CARTOGRAPHY ||
                isCrafterInventory(type);

        if (!isProtectedInventory) {
            return;
        }

        if (event.isShiftClick() && event.getCurrentItem() != null) {
            if (isMapLocked(event.getCurrentItem())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                if (event.getWhoClicked() instanceof Player) {
                    event.getWhoClicked().sendMessage(msgCannotCopy);
                }
                return;
            }
        }

        if (event.getCursor() != null && isMapLocked(event.getCursor())) {
            if (event.getClickedInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                if (event.getWhoClicked() instanceof Player) {
                    event.getWhoClicked().sendMessage(msgCannotCopy);
                }
                return;
            }
        }

        if (event.getClickedInventory().getType() != InventoryType.PLAYER) {
            if (event.getCurrentItem() != null && isMapLocked(event.getCurrentItem())) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                if (event.getWhoClicked() instanceof Player) {
                    event.getWhoClicked().sendMessage(msgCannotCopy);
                }
                return;
            }
        }
    }

    private boolean isMapLocked(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(LOCKED_MAP_KEY, PersistentDataType.BYTE);
    }
}