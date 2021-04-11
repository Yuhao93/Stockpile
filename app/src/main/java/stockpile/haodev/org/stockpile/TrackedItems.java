package stockpile.haodev.org.stockpile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class TrackedItems {
  private final String TRACKED_ITEMS_KEY = "TRACKED_ITEMS";
  private final Map<String, Set<Item>> nameMap = new HashMap<>();
  private final Map<String, Item> idMap = new HashMap<>();

  private SharedPreferences settings;

  TrackedItems(Context context) {
    settings = context.getSharedPreferences("TRACKED_ITEMS", 0);
    String[] items = settings.getString(TRACKED_ITEMS_KEY, "").split(",");
    if (items.length == 1 && items[0].isEmpty()) {
      return;
    }
    for (String item : items) {
      registerItem(Item.fromString(item));
    }
  }

  void addItem(Item item) {
    registerItem(item);
    updatePrefs();
  }

  void setActive(String id, boolean active) {
    idMap.get(id).active = active;
    updatePrefs();
  }

  void remove(String id) {
    Item item = itemById(id);
    idMap.remove(item.id);
    nameMap.get(item.name).remove(item);
    updatePrefs();
  }

  void toggleActive(String id) {
    setActive(id, !itemById(id).active);
    updatePrefs();
  }

  private void updatePrefs() {
    settings.edit().putString(TRACKED_ITEMS_KEY, toString()).commit();
  }

  private void registerItem(Item item) {
    idMap.put(item.id, item);
    if (!nameMap.containsKey(item.name)) {
      nameMap.put(item.name, new HashSet<Item>());
    }
    nameMap.get(item.name).add(item);
  }

  Set<String> activeIds() {
    Set<String> activeIds = new HashSet<>();
    for (Map.Entry<String, Item> entry : idMap.entrySet()) {
      if (entry.getValue().active) {
        activeIds.add(entry.getValue().id);
      }
    }
    return activeIds;
  }

  Set<Item> itemsByName(String name) {
    return nameMap.get(name);
  }

  Collection<Item> items() {
    return idMap.values();
  }

  Item itemById(String id) {
    return idMap.get(id);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    boolean hasAppended = false;
    for (Item item : idMap.values()) {
      if (hasAppended) {
        builder.append(',');
      } else {
        hasAppended = true;
      }
      builder.append(item.toString());
    }
    return builder.toString();
  }

  static class Item {
    public String id;
    public String name;
    public boolean active;

    static Item fromString(String s) {
      String[] parts = s.split(":");
      Item i = new Item();
      i.id = parts[0];
      i.name = parts[1];
      i.active = parts[2].equals("true");
      return i;
    }

    @Override
    public String toString() {
      return String.format("%s:%s:%s", id, name, active);
    }
  }
}
