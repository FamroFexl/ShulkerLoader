package com.fexl.shulkerloader;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ShulkerLoad{
	
	@SubscribeEvent
	public void itemPickupEvent(EntityItemPickupEvent event) {
		Player player = event.getPlayer();
		Inventory playerInv = player.getInventory();
		ItemStack offhand_item = playerInv.offhand.get(0);
		ItemStack pickup_item = event.getItem().getItem();
		
		//Check the player is carrying a shulker box AND the item picked up is NOT a shulker box
		if(!isShulkerBox(offhand_item) || isShulkerBox(pickup_item)) {
			return;
		}
		
		//Stores the shulker box contents for processing
		NonNullList<ItemStack> shulker_inv = NonNullList.withSize(27, ItemStack.EMPTY);

		//Iterate through all the items in the shulker box and place them in an ItemStack list so they are easier to work with
		if(offhand_item.hasTag()) {
			//Get the items in the shulker box
			ListTag items = offhand_item.getTag().getCompound("BlockEntityTag").getList("Items", 10);
			
			for(int i=0; i<items.size(); i++) {
				CompoundTag selected_item = items.getCompound(i);
				ItemStack base_istack = ItemStack.of(selected_item);
				shulker_inv.set(selected_item.getByte("Slot"), base_istack);
			}
		}
		
		Boolean item_transferred = false;
		ItemStack pickup_item_copy = pickup_item.copy();
		int stack_avaliable = getSlotWithRemainingSpace(pickup_item_copy, shulker_inv);
		while(stack_avaliable != -1) {
			ItemStack shulker_item = shulker_inv.get(stack_avaliable).copy();
			int combined_stack = shulker_item.getCount() + pickup_item_copy.getCount();
			//If pickup item fits in selected stack
			if(combined_stack <= shulker_item.getMaxStackSize()) {
				shulker_item.setCount(combined_stack);
				pickup_item_copy.setCount(0);
				shulker_inv.set(stack_avaliable, shulker_item);
				item_transferred = true;
				break;
			}
			int stack_left = shulker_item.getMaxStackSize() - shulker_item.getCount();
			//If pickup item doesn't fit in selected stack
			if(pickup_item_copy.getCount() > stack_left) {
				pickup_item_copy.setCount(pickup_item_copy.getCount()-stack_left);
				shulker_item.setCount(shulker_item.getMaxStackSize());
				shulker_inv.set(stack_avaliable, shulker_item);
				stack_avaliable = getSlotWithRemainingSpace(pickup_item_copy, shulker_inv);
				continue;
			}		
		}

		//Check if the shulker has any free slots
		int slot_avaliable = getFreeSlot(shulker_inv);
		if(slot_avaliable != -1 && !item_transferred) {
			shulker_inv.set(slot_avaliable, pickup_item_copy);
			item_transferred = true;
		}
		
		//***
		ListTag item_list = new ListTag();
		//Reinsert all items into the shulker box
		for(int i=0; i<shulker_inv.size(); i++) {
			//Get the working item
			ItemStack current_item = shulker_inv.get(i);
			//Skip over empty slots so they aren't inserted into the shulker box nbt data
			if(!(current_item==ItemStack.EMPTY)) {
				{
					ByteTag item_count = ByteTag.valueOf((byte)current_item.getCount());
						
					//Select slot based on position in shulker_inv
					ByteTag item_slot = ByteTag.valueOf((byte) i);
						
					//Item id queuing returns the count and id, so it has to be split so just the id can be retrieved
					String[] current_item_id = current_item.toString().split(" ");
					StringTag item_id = StringTag.valueOf("minecraft:" + current_item_id[1]);
						
					//Add the item attributes to a CompoundTag container
					CompoundTag item_attributes = new CompoundTag();
					item_attributes.put("Count", item_count);
					item_attributes.put("Slot", item_slot);
					item_attributes.put("id", item_id);
						
					//Add the pickup item tag to the resulting shulker box item if it has one
					if(current_item.hasTag()) {
						item_attributes.put("tag", current_item.getTag());
					}
					
					//Storage tag for the item
					item_list.add(item_attributes);
				}
					
			}
		}
		//Get the shulker color for the id tag
		String[] offhand_id = offhand_item.toString().split(" ");
		StringTag shulker_id = StringTag.valueOf("minecraft:" + offhand_id[1]);
			
		//Interior tags
		CompoundTag items_tag = new CompoundTag();
		items_tag.put("Items", item_list);
		items_tag.put("id", shulker_id);
		
			
		//The base block tag
		CompoundTag shulker_tag = new CompoundTag();
		shulker_tag.put("BlockEntityTag", items_tag);
		
		//If the shulker box has a custom name, re-add it
		CompoundTag customName = offhand_item.getTag().getCompound("display");
		if(!customName.isEmpty()) {
			shulker_tag.put("display", customName);
		}
		
		//Set the shulker box tag equal to the new one
		offhand_item.setTag(shulker_tag);
		
		//Grant advancements associated with picking up the item
		CriteriaTriggers.INVENTORY_CHANGED.trigger((ServerPlayer)player, playerInv, pickup_item);
		
		//Update player statistics with the item
		player.awardStat(Stats.ITEM_PICKED_UP.get(pickup_item.getItem()), pickup_item.getCount());
		
		//Cancel the event so it isn't processed by the inventory
		event.setCanceled(true);
			
		//Shows the player pickup animation
		player.take(event.getItem(), pickup_item.getCount());
			
		//Adds the remaining ItemEntity that couldn't fit in the shulker box into the player's inventory
		if(!item_transferred) {
			pickup_item.setCount(pickup_item_copy.getCount());
			playerInv.add(pickup_item);
		}
			
		//Kill the ItemEntity
		event.getItem().kill();
	}
	
	//Ported from Inventory.class
	private int getFreeSlot(NonNullList<ItemStack> items) {
		for(int i = 0; i < items.size(); ++i) {
			if (items.get(i).isEmpty()) {
				return i;
		    }
		}

		return -1;
	}
	
	//Ported from Inventory.class
	private boolean hasRemainingSpaceForItem(ItemStack item1, ItemStack item2) {
	      return !item1.isEmpty() && ItemStack.isSameItemSameTags(item1, item2) && item1.isStackable() && item1.getCount() < item1.getMaxStackSize() && item1.getCount() < 64;
	}
	   
	//Ported from Inventory.class
	private int getSlotWithRemainingSpace(ItemStack item, NonNullList<ItemStack> items) {
		for(int i = 0; i < items.size(); ++i) {
			if (this.hasRemainingSpaceForItem(items.get(i), item)) {
				return i;
	        }
		}
	    return -1;
	}

	//Avoiding the use of registries for portability
	private Boolean isShulkerBox(ItemStack item) {
		String[] item_name = item.toString().split(" ");
		switch(item_name[1]) {
		case ("shulker_box"):
			break;
		case ("white_shulker_box"):
			break;
		case ("orange_shulker_box"):
			break;
		case ("magenta_shulker_box"):
			break;
		case ("light_blue_shulker_box"):
			break;
		case ("yellow_shulker_box"):
			break;
		case ("lime_shulker_box"):
			break;
		case ("pink_shulker_box"):
			break;
		case ("gray_shulker_box"):
			break;
		case ("light_gray_shulker_box"):
			break;
		case ("cyan_shulker_box"):
			break;
		case ("purple_shulker_box"):
			break;
		case ("blue_shulker_box"):
			break;
		case ("brown_shulker_box"):
			break;
		case ("green_shulker_box"):
			break;
		case ("red_shulker_box"):
			break;
		case ("black_shulker_box"):
			break;
		default:
			return false;
		}
		return true;
	}
}
