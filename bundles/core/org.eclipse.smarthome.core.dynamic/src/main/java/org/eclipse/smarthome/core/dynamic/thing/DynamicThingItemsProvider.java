package org.eclipse.smarthome.core.dynamic.thing;

import org.eclipse.smarthome.core.thing.Thing;

/**
 * Provides dynamically items for all channels of a thing as well as the related links.  
 * 
 * @author Mathias Runge
 *
 */
public interface DynamicThingItemsProvider {
	/**
	 * Provides items for each channel of the given thing.
	 * @param thing 
	 */
	public void provideItems(Thing thing);
	
	/**
	 * Automatically provides items for each thing that belongs to the given binding ID. 
	 * @param bindingId
	 */
	public void provideItems(String bindingId);
	
	/**
	 * Remove all items and links related to the given thing
	 * @param thing
	 */
	public void removeItems(Thing thing);
	
	/**
	 * Remove all items for each thing that is related to the given binding id.
	 * No item or link will be automatically provided afterwards until method provideItems(String bindingId) is called again.
	 * @param bindingId
	 */
	public void removeItems(String bindingId);
	
}	
