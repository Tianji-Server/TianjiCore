package org.tianjiserver.tianjicore.fixer;

import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * 禁止末影人在任意世界搬动方块。
 */
public class EndermanBlockMoveBlocker implements Listener {

  // 同时拦截末影人拾取和放置方块的事件。
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEndermanChangeBlock(EntityChangeBlockEvent event) {
    if (event.getEntity() instanceof Enderman) {
      event.setCancelled(true);
    }
  }
}
