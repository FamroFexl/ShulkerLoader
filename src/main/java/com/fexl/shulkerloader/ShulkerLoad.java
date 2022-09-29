package com.fexl.shulkerloader;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.NonNullList;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.stats.StatList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ShulkerLoad{
	
	@SubscribeEvent
	public void itemPickupEvent(EntityItemPickupEvent event) {
		//Common Variables
		EntityPlayer player = event.getEntityPlayer();
		InventoryPlayer playerInv = player.inventory;
		ItemStack offhand_item = playerInv.offHandInventory.get(0);
		ItemStack pickup_item = event.getItem().getItem();
		
		//Check the player is carrying a shulker box AND the item picked up is NOT a shulker box
		if(!isShulkerBox(offhand_item) || isShulkerBox(pickup_item)) {
			return;
		}
	
		//Stores the shulker box contents for processing
		NonNullList<ItemStack> shulker_inv = NonNullList.withSize(27, ItemStack.EMPTY);
		
	//--------------------------------SHULKER UNLOADING----------------------------\\
		//Iterate through all the items in the shulker box and place them in an ItemStack list so they are easier to work with
		if(offhand_item.hasTagCompound()) {
			//Add the existing tag to the resulting tag
			if(offhand_item.getTagCompound().hasKey("BlockEntityTag")) {
				if(offhand_item.getTagCompound().getCompoundTag("BlockEntityTag").hasKey("Items")) {
					//Get the items in the shulker box
					NBTTagList items = offhand_item.getTagCompound().getCompoundTag("BlockEntityTag").getTagList("Items", 10);
					//Iterate through the items and place them in shulker_inv
					for(int i=0; i<items.tagCount(); i++) {
						//Get the next item
						NBTTagCompound selected_item = items.getCompoundTagAt(i);
								
						//Turn the item nbt into an ItemStack
						ItemStack base_istack = new ItemStack(selected_item);
								
						//Insert it into a ItemStack list for future processing
						shulker_inv.set(selected_item.getByte("Slot"), base_istack);
					}
				}
			}
		}
		
	//--------------------------------SHULKER TESTING & DERIVATIVE FACTORS-------------------------\\
		//Count after processing
		int processed_count = checkSlots(pickup_item, shulker_inv);

		//Count before processing
		int previous_count = pickup_item.copy().getCount();

		//If at least one quantity of the pickup item wasn't deposited into the offhand shulker box
		if(!(previous_count > processed_count)) {
			return;
		}

		//Grant advancements associated with picking up the item [1.12.2 only]
		{
			//Store current stack in player slot
			final ItemStack current_stack = player.inventory.getStackInSlot(0);
			
			//Place the pickup item in the player slot
			player.inventory.setInventorySlotContents(0, pickup_item);
			
			//Trigger the inventory changed parameter while the pickup item is in the player slot so the trigger notices it and grants advancements associated with the pickup item
			CriteriaTriggers.INVENTORY_CHANGED.trigger((EntityPlayerMP)player, playerInv);
			
			//Replace the previous stack into player slot
			player.inventory.setInventorySlotContents(0, current_stack);
		}
		
		//Update player statistics with the item
		player.addStat(StatList.getObjectsPickedUpStats(pickup_item.getItem()), previous_count-processed_count);
		
		//Set the count of the pickup item
		pickup_item.setCount(processed_count);
						
		//Cancel the event so it isn't processed by the inventory
		event.setCanceled(true);
		
		//Shows the player pickup animation
		playerInv.addItemStackToInventory(pickup_item);
		
		if(pickup_item.getCount() == 0) {
			//Kill the ItemEntity
			event.getItem().setDead();
		}
		
	//--------------------------------SHULKER RELOADING----------------------------\\
		//Stores items as nbt until they are processed into the shulker box
		NBTTagList item_list = new NBTTagList();
				
		//Reinsert all items into the shulker box
		for(int i = 0; i < shulker_inv.size(); i++) {
			//Get the working item
			ItemStack current_item = shulker_inv.get(i);
					
			//Skip over empty slots so they aren't inserted into the shulker box nbt data
			if(!(current_item==ItemStack.EMPTY)) {
				//Get item count
				NBTTagByte item_count = new NBTTagByte((byte) current_item.getCount());
						
				//Select slot based on position in shulker_inv
				NBTTagByte item_slot = new NBTTagByte((byte) i);
				
				//[1.12.2 and below only] adds the item damage (aka variant) to the item nbt
				NBTTagShort item_type = new NBTTagShort((short) current_item.getItemDamage());

				//Get the id based on the item's registry name
				NBTTagString item_id = new NBTTagString(current_item.getItem().getRegistryName().getResourcePath().toString());
						
				//Add the item attributes to an item attributes container
				NBTTagCompound item_attributes = new NBTTagCompound();
				item_attributes.setTag("Count", item_count);
				item_attributes.setTag("Slot", item_slot);
				item_attributes.setTag("id", item_id);
				item_attributes.setTag("Damage", item_type);

				//Add the pickup item tag to the resulting shulker box item if it has one
				if(current_item.hasTagCompound()) {
					item_attributes.setTag("tag", current_item.getTagCompound());
				}
						
				//Add the resulting item to the list of items
				item_list.appendTag(item_attributes);
			}
		}
		//Holds future shulker box tag while it's being constructed
		NBTTagCompound shulker_tag = new NBTTagCompound();
				
		if(offhand_item.hasTagCompound()) {
			//Add the existing tag to the resulting tag
			shulker_tag = offhand_item.getTagCompound();
		}
			
		//Add the base block tag and id if they don't already exist
		if(!shulker_tag.hasKey("BlockEntityTag")) {
			shulker_tag.setTag("BlockEntityTag", new NBTTagCompound());
			shulker_tag.getCompoundTag("BlockEntityTag").setString("id", "minecraft:shulker_box");
		}
		
		//Add to or override "Items" in "BlockEntityTag"
		shulker_tag.getCompoundTag("BlockEntityTag").setTag("Items", item_list);

		//Set the shulker box tag equal to the new one
		offhand_item.setTagCompound(shulker_tag);
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
	
	//Ported from Inventory.class (modified for [1.12.2])
	private boolean hasRemainingSpaceForItem(ItemStack item1, ItemStack item2) {
		return !item1.isEmpty() && stackEqualExact(item1, item2) && item1.isStackable() && item1.getCount() < item1.getMaxStackSize() && item1.getCount() < 64;
	}
	
	//Ported from InventoryPlayer.class [1.12.2]
	private boolean stackEqualExact(ItemStack stack1, ItemStack stack2) {
		return stack1.getItem() == stack2.getItem() && (!stack1.getHasSubtypes() || stack1.getMetadata() == stack2.getMetadata()) && ItemStack.areItemStackTagsEqual(stack1, stack2);
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
	
	//Distribute an ItemStack into the components of an ItemStack list and return the remainder
	private int checkSlots(ItemStack item, NonNullList<ItemStack> items) {
		//True if the stack has been fully transferred into items
		Boolean item_transferred = false;
			
		//Copy item for processing
		ItemStack pickup_item_copy = item.copy();

		//Get the next avaliable slot that has remaining space for item
		int stack_avaliable = this.getSlotWithRemainingSpace(pickup_item_copy, items);
			
		//Iterate until no more stacks with remaining space are available
		while(stack_avaliable != -1) {
			ItemStack shulker_item = items.get(stack_avaliable).copy();
			int combined_stack = shulker_item.getCount() + pickup_item_copy.getCount();
				
			//If pickup item fits in selected stack
			if(combined_stack <= shulker_item.getMaxStackSize()) {
				shulker_item.setCount(combined_stack);
				pickup_item_copy.setCount(0);
				items.set(stack_avaliable, shulker_item);
				item_transferred = true;
				break;
			}
			//How much stack capacity is left
			int stack_left = shulker_item.getMaxStackSize() - shulker_item.getCount();
				
			//If pickup item doesn't fit in selected stack
			if(pickup_item_copy.getCount() > stack_left) {
				pickup_item_copy.setCount(pickup_item_copy.getCount()-stack_left);
				shulker_item.setCount(shulker_item.getMaxStackSize());
				items.set(stack_avaliable, shulker_item);
				stack_avaliable = this.getSlotWithRemainingSpace(pickup_item_copy, items);
				continue;
			}	
		}
		
		//Check if the shulker has any free slots
		int slot_avaliable = getFreeSlot(items);
		if(slot_avaliable != -1 && !item_transferred) {
			items.set(slot_avaliable, pickup_item_copy.copy());
			pickup_item_copy.setCount(0);
		}
		
		//Return the remainder of item
		return pickup_item_copy.getCount();
	}

	//Overloaded with ItemStack
	private Boolean isShulkerBox(ItemStack item) {
		return this.isShulkerBox(item.getItem());
	}
	
	//Avoiding the use of registries for portability
	private Boolean isShulkerBox(Item item) {
		String item_name = item.getRegistryName().getResourcePath().toString();
		switch(item_name) {
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
		case ("silver_shulker_box"):
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
