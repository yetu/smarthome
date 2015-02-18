package org.eclipse.smarthome.core.dynamic.link;

import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.thing.Channel;

/**
 * 
 * The DynamicItemChannelLinker provides ItemChannelLinks dynamically for given channels and items.
 * 
 * @author Mathias Runge (initial contribution)
 *
 */
public interface DynamicItemChannelLinker {
	/**
	 * Dynamically links the given channel with the given item.  
	 * @param channel channel that should be linked to given item
	 * @param item item that should be linked to given channel 
	 */
	public void link(Channel channel, Item item);
	
	/**
	 * Dynamically unlinks the given channel with the given item
	 * @param channel that is linked
	 * @param item that is linked to the channel
	 */
	public void unlink(Channel channel, Item item);
	
	/**
	 * Dynamically removes all links that include the given channel
	 * @param channel that should be unlinked with all items
	 */
	public void unlinkAll(Channel channel);
}
