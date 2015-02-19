package org.eclipse.smarthome.core.dynamic.internal.thing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.core.common.registry.Provider;
import org.eclipse.smarthome.core.common.registry.ProviderChangeListener;
import org.eclipse.smarthome.core.dynamic.link.DynamicItemChannelLinker;
import org.eclipse.smarthome.core.dynamic.thing.DynamicThingItemsProvider;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.ItemProvider;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.library.items.NumberItem;
import org.eclipse.smarthome.core.library.items.SwitchItem;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ManagedThingProvider;
import org.eclipse.smarthome.core.thing.Thing;
import org.osgi.service.cm.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The DynamicThingItemsProviderImpl implements the interface DynamicThingItemsProvider {@link DynamicThingItemsProvider}. 
 * 
 * @author Mathias Runge (initial contribution)
 */
public class DynamicThingItemsProviderImpl implements ItemProvider, ProviderChangeListener<Thing>, DynamicThingItemsProvider{

	protected static Logger logger = LoggerFactory.getLogger(DynamicThingItemsProviderImpl.class);
	
	private Set<ProviderChangeListener<Item>> listeners = new HashSet<>();

	private ItemRegistry itemRegistry;				   // will be injected
	private ManagedThingProvider managedThingProvider; // will be injected	
	private DynamicItemChannelLinker linker; 		   // will be injected
	

	private boolean enabled;
	private Set<Item> allItems; 					// list of all items that were generated by this instance
	private List<String> bindingIdsForAutoProvide; 	// list of bindingIds this instance should generate items automatically when a new thing was generated
	private List<Thing> providedThings; 			// list of things for which items were generated
	
	
	public DynamicThingItemsProviderImpl() {
		enabled = false;
		itemRegistry = null;
		managedThingProvider = null;
		linker = null;		
		allItems = new HashSet<Item>();
		bindingIdsForAutoProvide = new ArrayList<>();
		providedThings = new ArrayList<>();
	}
	
	
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public Collection<Item> getAll() {		
		return allItems;

	}	
		
	/**
	 * Only returns the item, if it was also generated. For searching an item over all items, make use of ItemRegistry.
	 * @param itemName the Name of the item that has to be return
	 * @return null if no item was found, the item with the given name
	 */
	public Item getItem(String itemName){
		synchronized (allItems){
			for (Item item : allItems){
				if (item.getName().equalsIgnoreCase(itemName)){
					return item;
				}
			}
		}
		return null;
	}
	
	/**
	 * Checks the item registry whether an item with the given name exists or not
	 * @param itemName name of the item
	 * @return true if items exists, false if not exists or item registry was not injected yet
	 */
	private boolean itemExists(String itemName){
		if (itemRegistry == null){
			return false;
		}
		
		return itemRegistry.get(itemName) != null;
	}
	

	
	
	// +++++++++++++++++++++++++++++ Add Items ++++++++++++++++++++++++++++++++++
	
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void provideItems(Thing thing) {
		addItems(thing);		
	}
	
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void provideItems(String bindingId) {
		if (!bindingIdsForAutoProvide.contains(bindingId)){
			bindingIdsForAutoProvide.add(bindingId);
			autoAddAllItems();
		}		
		
	}
	
	/**
	 * All items of a thing will be added automatically due to call autoAddItems(Thing thing) method.
	 */
	protected void autoAddAllItems(){
		for (Thing thing : this.managedThingProvider.getAll()){
			autoAddItems(thing);
		}	
	}
	
	/**
	 * For each thing, that includes a bindingId in its thingUID which was already registered before, all items will be generated by calling the method addItems(Thing thing);
	 * @param thing 
	 */
	protected void autoAddItems(Thing thing){
		for (String bindingId : bindingIdsForAutoProvide){
			if (bindingId.equals(thing.getThingTypeUID().getBindingId())){
				addItems(thing);	
			}
		}
	}
	
	/**
	 * All items of the given thing will be generated by calling the method generateItems(). Afterwards, all ProviderChangeListener will be informed.
	 * @param thing
	 */
	protected void addItems(Thing thing){
		if (providedThings.contains(thing)){
			logger.debug("stopped item generation for thing {} because items should be already added.",thing.getUID());
			return;
		}
		List<Item> items = generateItems(thing);
		allItems.addAll(items);
		providedThings.add(thing);
		for(ProviderChangeListener<Item> listener : listeners) {
			for(Item item : items) {
				listener.added(this, item);
			}
		}		
 	}
	
	/**
	 * Generates a bunch of items out of a list of things
	 * @param things
	 * @return
	 */
	protected List<Item> generateItems(Collection<Thing> things){
		List<Item> items = new ArrayList<>();
		for (Thing thing : things){
			items.addAll(generateItems(thing));
		}
		return items;
	}
	
	/**
	 * Generates items related to the given thing. Method calls generateItem(Channel channel) for each channel of the given thing. 
	 * @param thing with channels
	 * @return a list of generated Items
	 */
	protected List<Item> generateItems(Thing thing){
		
		List<Item> items = new ArrayList<>();
		
		logger.debug("generating items for thing {}",thing.getUID());
		Item tmp;
		for (Channel channel : thing.getChannels()){
			tmp = generateItem(channel);
			if (tmp != null){				
					linkChannelWithItem(channel,tmp);
					items.add(tmp);
			}
		}
		return items;
	}
	
	/**
	 * Generates an item out of the given channel. The name is generated by replacing ':' and '#' with '_' from the channelUID.
	 * @param channel 
	 * @return generate item.
	 */
	protected Item generateItem(Channel channel){
		Item item = null;
		
		String itemName = generateItemName(channel);
		
		if (itemExists(itemName)){
			// TODO: WTF! What now? Use this item? -> It has a name that equals the channelUID, thus it must be related to the given channel			
			item = itemRegistry.get(itemName);
			// FIXME: when item was already existing, do not delete this item when thing was removed.
		}
		
		String itemType = channel.getAcceptedItemType();
		
		if (item == null){
			
			//	TODO: make use of CoreItemFactory. Do not know how to access it from here
			//	      otherwise complete this for all other item types
			if ("Number".equalsIgnoreCase(itemType)){
				item = new NumberItem(itemName);
			}
			if ("Switch".equalsIgnoreCase(itemType)){
				item = new SwitchItem(itemName);
			}
		}
		
		if (item == null){
			logger.warn("Unable to generate item. Maybe the item type is not supported yet. Method is still under construction and not completely finished");
		}
		return item;
	}
	
	/**
	 * Calls the DynamicItemChannelLinkProvider to generate a link between given item and given channel.
	 * @param channel that should be linked with given item
	 * @param item that should be linked with given channel
	 */
	protected void linkChannelWithItem(Channel channel, Item item){
		if (linker != null){
			linker.link(channel,item);			
		}
	}
	
	
	// ----------------------------- Add Items ------------------------------------------
	// +++++++++++++++++++++++++++ Remove Items +++++++++++++++++++++++++++++++++++++++++
	
	
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void removeItems(Thing thing) {
		if (!providedThings.contains(thing)){
			autoRemoveItems(thing);
		}
	}

	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void removeItems(String bindingId) {
		bindingIdsForAutoProvide.remove(bindingId);		
	}
	
	/**
	 * Removes all generated items related to the given thing. 
	 * @param thing 
	 */
	protected void autoRemoveItems(Thing thing){
		for (String bindingId : bindingIdsForAutoProvide){
			if (bindingId.equals(thing.getThingTypeUID().getBindingId())){
				for (Channel channel: thing.getChannels()){
					removeChannel(channel);
				}
			}
		}
	}
	
	/**
	 * Removes the ItemChannelLink related to this channel and a link, generated by this instance.
	 * @param channel 
	 */
	protected void removeChannel(Channel channel){		
		String itemName = generateItemName(channel);
				
		Item item = getItem(itemName);
		if (item != null){
			unlinkChannelWithItem(channel, item);
			removeItem(item);
		}
		
	}
	
	/**
	 * Removes the given item by informing all ProviderChangeListener. 
	 * @param item that should be removed
	 */
	protected void removeItem(Item item){
		allItems.remove(item);
		for(ProviderChangeListener<Item> listener : listeners) {
			listener.removed(this, item);			
		}			
	}
	

	/**
	 * Removes the ItemChannelLink by calling the function unlink from DynamicItemChannelLinkProvider {@link DynamicItemChannelLinkProvider}
	 * @param channel that is linked to the given item
	 * @param item that is linked to the given channel
	 */
	protected void unlinkChannelWithItem(Channel channel, Item item){
		if (linker != null) {
			linker.unlink(channel, item);
		}
	}
	
	
	// --------------------------- Remove Items -----------------------------------
	
	

	/**
	 * Method will be called by OSGi framework to start the bundle
	 * @param properties 
	 * @throws ConfigurationException 
	 */
	protected void activate(Map<String, Object> properties) throws ConfigurationException {
		this.enabled = true;
		for(ProviderChangeListener<Item> listener : listeners) {
			for(Item item : getAll()) {
				listener.added(this, item);
			}
		}
	}
	
	/**
	 * Returns whether the activate method was called or not.
	 * @return
	 */
	public boolean isEnabled() {
		return enabled;
	}

	
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void addProviderChangeListener(ProviderChangeListener<Item> listener) {
		listeners.add(listener);
		for(Item item : getAll()) {
			listener.added(this, item);
		}
	}

	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void removeProviderChangeListener(ProviderChangeListener<Item> listener) {
		listeners.remove(listener);
		
	}
	
		
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void added(Provider<Thing> provider, Thing element) {
		logger.debug("added called with {} and element {}", provider.getClass().getName(), element.getUID());
		autoAddItems(element);
	}

	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void removed(Provider<Thing> provider, Thing element) {		
		logger.debug("removed called with {} and element {}", provider.getClass().getName(), element.getUID());
		autoRemoveItems(element);
	}	

	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void updated(Provider<Thing> provider, Thing oldelement,	Thing element) {
		// TODO Auto-generated method stub
		
	}

	
	
	// ++++++++++++++++++++++++++ Component Injection +++++++++++++++++++++++++++++++++
	
	/**
	 * Sets the DynamicItemChannelLinker which will be called for providing a link between a given channel and an item
	 * @param linker that will be injected by system
	 */
	public void setChannelLinker(DynamicItemChannelLinker linker){		
		this.linker = linker;
		logger.debug("DynamicItemChannelLinker was set");
	}
	
	
	/**
	 * removes the DynamicItemChannelLinker
	 * @param linker that will be removed by system 
	 */
	public void unsetChannelLinker(DynamicItemChannelLinker linker){
		this.linker = null;
		logger.debug("DynamicItemChannelLinker was unset");
	}
	
	
	/**
	 * Injects the MangedThingProvider by OSGi
	 * @param managedThingProvider
	 */
	public void setManagedThingProvider(ManagedThingProvider managedThingProvider){
		logger.debug("ManagedThingProvider {} added",managedThingProvider.getClass().getName());
		this.managedThingProvider = managedThingProvider;		
		this.managedThingProvider.addProviderChangeListener(this);
		autoAddAllItems();
	}
	
	/**
	 * Injects the ItemRegistry. The registry is needed to make sure not to provide an item with an item name that already exists
	 * @param itemRegistry
	 */
	protected void setItemRegistry(ItemRegistry itemRegistry) {
		logger.debug("set item registry");
		this.itemRegistry = itemRegistry;
		
	}

	/**
	 * Removes the item registry.
	 * @param itemRegistry
	 */
	protected void unsetItemRegistry(ItemRegistry itemRegistry) {
		logger.debug("unset item registry");
	
		this.itemRegistry = null;
	}
	
	// -------------------------- Component Injection ---------------------------------
	
	// ++++++++++++++++++++++++++++++ utils +++++++++++++++++++++++++++++++++++++++++++
	
	/**
	 * Generates an item name from UID of the given channel by replacing ':' and '#' through '_'
	 * @param channel
	 * @return generated item name
	 */
	protected String generateItemName(Channel channel){
		String itemName = channel.getUID().toString();
		itemName = itemName.replace(':','_');
		itemName = itemName.replace('#', '_');
		return itemName;
	}
	

}