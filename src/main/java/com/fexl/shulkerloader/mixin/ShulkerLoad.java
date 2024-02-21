package com.fexl.shulkerloader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

@Mixin(ItemEntity.class)
public class ShulkerLoad {	
	@Inject(method = "Lnet/minecraft/world/entity/item/ItemEntity;playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z", ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
	private void itemPickupEvent(Player player, CallbackInfo event) {
		ItemEntity itemEntity = (ItemEntity)(Object) this;
		Inventory playerInv = player.getInventory();
		ItemStack offhand_item = playerInv.offhand.get(0);
		ItemStack pickup_item = itemEntity.getItem();
		
		//Check the player is carrying a shulker box AND the item picked up is NOT a shulker box
		if(!isShulkerBox(offhand_item) || isShulkerBox(pickup_item)) {
			return;
		}
		
		//Check the offhand shulker box doesn't have a stack size greater than 1
		if(offhand_item.getCount() > 1) {
			return;
		}
		
		//Stores the shulker box contents for processing
		NonNullList<ItemStack> shulkerInv = unloadShulker(offhand_item, (new ShulkerBoxBlockEntity(BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState())).getContainerSize());
		
		//Count after processing
		int processed_count = checkSlots(pickup_item, shulkerInv);
		
		//Count before processing
		int previous_count = pickup_item.copy().getCount();
		
		//If at least one quantity of the pickup item wasn't deposited into the offhand shulker box
		if(!(previous_count > processed_count)) {
			return;
		}
		
		//Grant advancements associated with picking up the item
		CriteriaTriggers.INVENTORY_CHANGED.trigger((ServerPlayer)player, playerInv, pickup_item);
				
		//Update player statistics with the item
		player.awardStat(Stats.ITEM_PICKED_UP.get(pickup_item.getItem()), pickup_item.getCount());
		
		//Set the count of the pickup item
		pickup_item.setCount(processed_count);
		
		//Cancel the event so it isn't processed by the inventory
		event.cancel();
		
		//Shows the player pickup animation
		player.take(itemEntity, previous_count-processed_count);
		
		if(pickup_item.getCount() == 0) { 
			//Kill the ItemEntity
			itemEntity.kill();
		}
		
		//Set the shulker box tag equal to the new one
		offhand_item.setTag(loadShulker(offhand_item, shulkerInv));
	}
	
	//Shulker unloading
	private NonNullList<ItemStack> unloadShulker(ItemStack shulkerBox, int shulkerSize) {
		NonNullList<ItemStack> shulkerInv = NonNullList.withSize(shulkerSize, ItemStack.EMPTY);
			
		//Iterate through all the items in the shulker box and place them in an ItemStack list so they are easier to work with
		if(shulkerBox.hasTag()) {
			//Add the existing tag to the resulting tag
			if(shulkerBox.getTag().contains("BlockEntityTag")) {
				if(shulkerBox.getTag().getCompound("BlockEntityTag").contains("Items")) {
					//Get the items in the shulker box
					ListTag items = shulkerBox.getTag().getCompound("BlockEntityTag").getList("Items", 10);
									
					//Iterate through the items and place them in shulker_inv
					for(int i=0; i<items.size(); i++) {
						//Get the next item
						CompoundTag selected_item = items.getCompound(i);
											
						//Turn the item nbt into an ItemStack
						ItemStack base_istack = ItemStack.of(selected_item);
										
						//Insert it into a ItemStack list for future processing
						shulkerInv.set(selected_item.getByte("Slot"), base_istack);
					}
				}
			}
		}
			
		return shulkerInv;
	}
		
	//Shulker loading
	private CompoundTag loadShulker(ItemStack shulkerBox, NonNullList<ItemStack> shulkerInv) {
			
		//Stores items as nbt until they are processed into the shulker box
		ListTag item_list = new ListTag();
							
		//Reinsert all items into the shulker box
		for(int i = 0; i < shulkerInv.size(); i++) {
			//Get the working item
			ItemStack current_item = shulkerInv.get(i);
								
			//Skip over empty slots so they aren't inserted into the shulker box nbt data
			if(!(current_item==ItemStack.EMPTY)) {
				//Get item count
				ByteTag item_count = ByteTag.valueOf((byte) current_item.getCount());
									
				//Select slot based on position in shulker_inv
				ByteTag item_slot = ByteTag.valueOf((byte) i);

				//Get the id based on the item's registry name and item path
				StringTag item_id = StringTag.valueOf(Registry.ITEM.getKey(current_item.getItem()).getNamespace().toString() + ":" + Registry.ITEM.getKey(current_item.getItem()).getPath().toString());
							
				//Add the item attributes to an item attributes container
				CompoundTag item_attributes = new CompoundTag();
				item_attributes.put("Count", item_count);
				item_attributes.put("Slot", item_slot);
				item_attributes.put("id", item_id);
									
				//Add the pickup item tag to the resulting shulker box item if it has one
				if(current_item.hasTag()) {
					item_attributes.put("tag", current_item.getTag());
				}
									
				//Add the resulting item to the list of items
				item_list.add(item_attributes);
			}
		}
		//Holds future shulker box tag while it's being constructed
		CompoundTag shulker_tag = new CompoundTag();
							
		if(shulkerBox.hasTag()) {
			//Add the existing tag to the resulting tag
			shulker_tag = shulkerBox.getTag();
		}
							
		//Add the base block tag and id if they don't already exist
		if(!shulker_tag.contains("BlockEntityTag")) {
			shulker_tag.put("BlockEntityTag", new CompoundTag());
			shulker_tag.getCompound("BlockEntityTag").putString("id", "minecraft:shulker_box");
		}
							
		//Add to or override "Items" in "BlockEntityTag"
		shulker_tag.getCompound("BlockEntityTag").put("Items", item_list);
			
		return shulker_tag;
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

	//Check if shulker
	private Boolean isShulkerBox(Item item) {
		//Get item registry name without namespace
		String item_name = Registry.ITEM.getKey(item).getPath().toString();
		
		if(item_name.endsWith("shulker_box"))
			return true;
		return false;
	}
}