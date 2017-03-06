/**
 *
 */
package pcl.lc.irc.hooks;

import org.apache.commons.lang3.StringUtils;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;
import pcl.lc.irc.*;
import pcl.lc.utils.Helper;
import pcl.lc.utils.PasteUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * @author Caitlyn
 */
@SuppressWarnings("rawtypes")
public class Inventory extends AbstractListener {
  private Command local_command;
  private Command sub_command_list;
  private Command sub_command_add;
  private Command sub_command_remove;
  private Command sub_command_preserve;
  private Command sub_command_unpreserve;
  private static double favourite_chance = 0.01;

  static int ERROR_ITEM_IS_FAVOURITE = 1;
  static int ERROR_INVALID_STATEMENT = 2;
  static int ERROR_SQL_ERROR = 3;
  static int ERROR_NO_ROWS_RETURNED = 4;
  static int ERROR_ID_NOT_SET = 5;
  static int ERROR_ITEM_IS_PRESERVED = 6;

  @Override
  protected void initHook() {
    local_command = new Command("inventory", 0);
    local_command.registerAlias("inv");
    sub_command_list = new Command("list", 0, true);
    sub_command_add = new Command("add", 0, true);
    sub_command_remove = new Command("remove", 0, true);
    sub_command_preserve = new Command("preserve", 0, true);
    sub_command_preserve.registerAlias("pre");
    sub_command_unpreserve = new Command("unpreserve", 0, true);
    sub_command_unpreserve.registerAlias("unpre");
    local_command.registerSubCommand(sub_command_list);
    local_command.registerSubCommand(sub_command_add);
    local_command.registerSubCommand(sub_command_remove);
    local_command.registerSubCommand(sub_command_preserve);
    local_command.registerSubCommand(sub_command_unpreserve);
    IRCBot.registerCommand(local_command, "Interact with the bots inventory");
    Database.addStatement("CREATE TABLE IF NOT EXISTS Inventory(id INTEGER PRIMARY KEY, item_name, uses_left INTEGER)");
    Database.addUpdateQuery(2, "ALTER TABLE Inventory ADD added_by VARCHAR(255) DEFAULT '' NULL");
    Database.addUpdateQuery(2, "ALTER TABLE Inventory ADD added INT DEFAULT NULL NULL;");

    Database.addPreparedStatement("getItems", "SELECT id, item_name, uses_left, is_favourite FROM Inventory;");
    Database.addPreparedStatement("getItem", "SELECT id, item_name, uses_left FROM Inventory WHERE id = ?;");
    Database.addPreparedStatement("getItemByName", "SELECT id, item_name, uses_left, is_favourite FROM Inventory WHERE item_name = ?;");
    Database.addPreparedStatement("getRandomItem", "SELECT id, item_name, uses_left, is_favourite FROM Inventory ORDER BY Random() LIMIT 1");
    Database.addPreparedStatement("getRandomItemNonFavourite", "SELECT id, item_name, uses_left, is_favourite FROM Inventory WHERE is_favourite IS 0 ORDER BY Random() LIMIT 1");
    Database.addPreparedStatement("addItem", "INSERT INTO Inventory (id, item_name, is_favourite, added_by, added) VALUES (NULL, ?, ?, ?, ?)");
    Database.addPreparedStatement("removeItemId", "DELETE FROM Inventory WHERE id = ?");
    Database.addPreparedStatement("removeItemName", "DELETE FROM Inventory WHERE item_name = ?");
    Database.addPreparedStatement("decrementUses", "UPDATE Inventory SET uses_left = uses_left - 1 WHERE id = ?");
    Database.addPreparedStatement("clearFavourite", "UPDATE Inventory SET is_favourite = 0 WHERE is_favourite = 1");
    Database.addPreparedStatement("preserveItem", "UPDATE Inventory SET uses_left = -1 WHERE item_name = ?");
    Database.addPreparedStatement("unPreserveItem", "UPDATE Inventory SET uses_left = 5 WHERE item_name = ?");
  }

  static int removeItem(String id) {
    return removeItem(id, false, false);
  }

  /**
   * Will not remove the item marked as favourite unless override is true
   * Returns 0 on success or higher on failure
   *
   * @param id_or_name         String
   * @param override_favourite boolean
   * @return int
   */
  @SuppressWarnings("Duplicates")
  private static int removeItem(String id_or_name, boolean override_favourite, boolean override_preserved) {
    Integer id = null;
    Boolean id_is_string = true;
    PreparedStatement removeItem;
    try {
      id = Integer.valueOf(id_or_name);
      id_is_string = false;

      try {
        removeItem = Database.getPreparedStatement("removeItemId");
        removeItem.setString(1, id.toString());
      }
      catch (Exception e) {
        e.printStackTrace();
        return ERROR_INVALID_STATEMENT;
      }
    }
    catch (NumberFormatException e) {
      try {
        removeItem = Database.getPreparedStatement("removeItemName");
        removeItem.setString(1, id_or_name);
      }
      catch (Exception ex) {
        ex.printStackTrace();
        return ERROR_INVALID_STATEMENT;
      }
    }

    if (!override_favourite || !override_preserved) {
      PreparedStatement getItem;
      try {
        if (id_is_string) {
          getItem = Database.getPreparedStatement("getItemByName");
          getItem.setString(1, id_or_name);
        }
        else if (id != null) {
          getItem = Database.getPreparedStatement("getItem");
          getItem.setInt(1, id);
        }
        else
          return ERROR_ID_NOT_SET;

        ResultSet result = getItem.executeQuery();

        if (result.next()) {
          if (result.getBoolean(4) && !override_favourite)
            return ERROR_ITEM_IS_FAVOURITE;
          else if (result.getInt(3) == -1 && !override_preserved)
            return ERROR_ITEM_IS_PRESERVED;
        }
        else
          return ERROR_NO_ROWS_RETURNED;
      }
      catch (Exception e) {
        e.printStackTrace();
        return ERROR_INVALID_STATEMENT;
      }
    }

    try {
      if (removeItem.executeUpdate() > 0) {
        return 0;
      }
      else {
        return ERROR_NO_ROWS_RETURNED;
      }
    }
    catch (SQLException e) {
      e.printStackTrace();
      return ERROR_SQL_ERROR;
    }
  }

  private static String addItem(String item) {
    return addItem(item, null, false);
  }

  private static String addItem(String item, String added_by) {
    return addItem(item, added_by, false);
  }

  private static String addItem(String item, String added_by, boolean override_duplicate_check) {
    if (item.contains(IRCBot.ournick + "'s") || !item.contains(IRCBot.ournick)) {
      try {
        PreparedStatement getItemByName = Database.getPreparedStatement("getItemByName");
        getItemByName.setString(1, item);

        ResultSet result = getItemByName.executeQuery();
        if (!override_duplicate_check && result.next()) {
          return "I already have one of those.";
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      try {
        boolean favourite = false;
        int fav_roll = Helper.getRandomInt(0, 100);
        System.out.println("Favourite roll: " + fav_roll);
        if (fav_roll < (100 * favourite_chance)) {
          System.out.println("New favourite! Clearing old favourite.");
          favourite = true;
          PreparedStatement clearFavourite = Database.getPreparedStatement("clearFavourite");
          clearFavourite.executeUpdate();
        }
        System.out.println("Favourites cleared, adding item");
        PreparedStatement addItem = Database.getPreparedStatement("addItem");
        item = item.replaceAll(" ?\\(\\*\\)", ""); //Replace any (*) to prevent spoofing preserved item marks
        addItem.setString(1, item);
        addItem.setInt(2, (favourite) ? 1 : 0);
        if (added_by != null)
          addItem.setString(3, added_by);
        else
          addItem.setString(3, "");
        addItem.setLong(4, new Timestamp(System.currentTimeMillis()).getTime());
        if (addItem.executeUpdate() > 0) {
          if (favourite)
            return "Added '" + item + "' to inventory. I love this! This is my new favourite thing!";
          else
            return "Added '" + item + "' to inventory.";
        }
        else {
          return "Wrong things happened! (1)";
        }
      }
      catch (Exception e) {
        e.printStackTrace();
        return "Wrong things happened! (2)";
      }
    }
    else
      return "I can't put myself in my inventory silly.";
  }

  public String chan;
  public String target = null;

  @Override
  public void handleCommand(String sender, MessageEvent event, String command, String[] args) {
    if (local_command.shouldExecute(command) >= 0) {
      chan = event.getChannel().getName();
    }
  }

  @Override
  public void handleCommand(String nick, GenericMessageEvent event, String command, String[] copyOfRange) {
    long shouldExecute = local_command.shouldExecute(command);
    if (!event.getClass().getName().equals("org.pircbotx.hooks.events.MessageEvent")) {
      target = nick;
    } else {
      target = chan;
    }
    if (shouldExecute == 0) {
      String message = "";
      for (String aCopyOfRange : copyOfRange) {
        message = message + " " + aCopyOfRange;
      }
      String s = message.trim();

      String[] strings = s.split(" ");
      String sub_command = strings[0];
      String argument = "";
      for (int i = 1; i < strings.length; i++) {
        argument += strings[i] + " ";
      }
      argument = argument.trim();

      if (sub_command_add.shouldExecute(sub_command) == 0)
        Helper.sendMessage(target, addItem(argument, nick), nick);
      else if (sub_command_remove.shouldExecute(sub_command) == 0) {
        boolean hasPermission = Permissions.hasPermission(IRCBot.bot, (MessageEvent) event, 4);
        int removeResult = removeItem(argument, hasPermission, hasPermission);
        if (removeResult == 0)
          Helper.sendMessage(target, "Removed item from inventory", nick);
        else if (removeResult == ERROR_ITEM_IS_FAVOURITE)
          Helper.sendMessage(target, "This is my favourite thing. You can't make me get rid of it.", nick);
        else if (removeResult == ERROR_ITEM_IS_PRESERVED)
          Helper.sendMessage(target, "I've been told to preserve this. You can't remove it.", nick);
        else if (removeResult == ERROR_NO_ROWS_RETURNED)
          Helper.sendMessage(target, "No such item", nick);
        else
          Helper.sendMessage(target, "Wrong things happened! (" + removeResult + ")", nick);
      } else if (sub_command_list.shouldExecute(sub_command) == 0) {
        try {
          PreparedStatement statement = Database.getPreparedStatement("getItems");
          ResultSet resultSet = statement.executeQuery();
          String items = "";
          while (resultSet.next()) {
            items += "'" + resultSet.getString(2) + ((resultSet.getInt(3) == -1) ? " (*)" : "") + "', ";
          }
          items = StringUtils.strip(items, ", ");
          Helper.sendMessage(target, items, nick, PasteUtils.Formats.INVENTORY);
        } catch (Exception e) {
          e.printStackTrace();
          Helper.sendMessage(target, "Wrong things happened! (5)", nick);
        }
      } else if (sub_command_preserve.shouldExecute(sub_command) == 0) {
        if (Permissions.hasPermission(IRCBot.bot, (MessageEvent) event, 4)) {
        try {
          PreparedStatement preserveItem = Database.getPreparedStatement("preserveItem");
          preserveItem.setString(1, argument);
          preserveItem.executeUpdate();
          Helper.sendMessage(target, "Item preserved", nick);
        } catch (Exception e) {
          e.printStackTrace();
        }
        } else {
          Helper.sendMessage(target, "I'm afraid you don't have the power to preserve this item.", nick);
        }
      } else if (sub_command_unpreserve.shouldExecute(sub_command) == 0) {
        if (Permissions.hasPermission(IRCBot.bot, (MessageEvent) event, 4)) {
        try {
          PreparedStatement unPreserveItem = Database.getPreparedStatement("unPreserveItem");
          unPreserveItem.setString(1, argument);
          unPreserveItem.executeUpdate();
          Helper.sendMessage(target, "Item un-preserved", nick);
        } catch (Exception e) {
          e.printStackTrace();
        }
        } else {
          Helper.sendMessage(target, "I'm afraid you don't have the power to preserve this item.", nick);
        }
      } else {
        Helper.sendMessage(target, "Unknown sub-command '" + sub_command + "' (Try: " + local_command.getSubCommandsAsString(true) + ")", nick);
      }
    }
  }

  @Override
  public void handleMessage(String sender, MessageEvent event, String command, String[] args) {
    // TODO Auto-generated method stub

  }

  @Override
  public void handleMessage(String nick, GenericMessageEvent event, String command, String[] copyOfRange) {
    // TODO Auto-generated method stub

  }
}
