package org.eclipse.smarthome.core.dynamic.internal.link;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.smarthome.core.common.registry.ProviderChangeListener;
import org.eclipse.smarthome.core.dynamic.link.DynamicItemChannelLinker;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.link.ItemChannelLink;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * This ItemChannelLinkProvider dynamically provides ItemChannelLinks when they are demanded. Methods link() and unlink() of DynamicItemChannelLinker are provided as service.
 * 
 * @author Mathias Runge (initial contribution)
 *
 */
public class DynamicItemChannelLinkProvider implements ItemChannelLinkProvider, DynamicItemChannelLinker {

	private static Logger logger = LoggerFactory.getLogger(DynamicItemChannelLinkProvider.class);
	
	List<ProviderChangeListener<ItemChannelLink>> listeners;
	List<ItemChannelLink> links;
	
	public DynamicItemChannelLinkProvider() {
		listeners = new ArrayList<>();
		links = new ArrayList<>();
	}
	
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void addProviderChangeListener(ProviderChangeListener<ItemChannelLink> listener) {
		synchronized(listeners){
			listeners.add(listener);
		}
		
	}

	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void removeProviderChangeListener(ProviderChangeListener<ItemChannelLink> listener) {
		synchronized(listeners){
			listeners.remove(listener);
		}
		
	}


	/**
	 * {@inheritedDoc}
	 */
	@Override
	public Collection<ItemChannelLink> getAll() {
		return links;
	}

	/**
	 * Searches for a link, that links the given channel with the given item.
	 * @param channel Channel that is linked
	 * @param item Item that is linked
	 * @return ItemChannelLink that links the given channel with the given item.
	 */
	private ItemChannelLink getLink(Channel channel, Item item){
		synchronized(links){
			for (ItemChannelLink link : links){
				if (link.getItemName().equals(item.getName())){
					if (link.getUID().equals(channel.getUID())){
						return link;
					}
				}
			}
		}
		return null;
	}
	
	/**
	 * returns all links related the the given channel. 
	 * @param channel 
	 * @return list of ItemChannelLinks related the the given channel
	 */
	private List<ItemChannelLink> getLinks(Channel channel){
		List<ItemChannelLink> searchedLinks = new ArrayList<>();
		synchronized(links){
			for (ItemChannelLink link : links){
				if (link.getUID().equals(channel.getUID())){
					searchedLinks.add(link);
				}
			}
		}
		return searchedLinks;
	}

	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void link(Channel channel, Item item) {	
		ItemChannelLink link = new ItemChannelLink(item.getName(),channel.getUID());
		links.add(link);
		synchronized(listeners){
			for (ProviderChangeListener<ItemChannelLink> listener : listeners){
				listener.added(this,link);
			}
		}
		
	}
	
	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void unlink(Channel channel, Item item) {
	
		ItemChannelLink link = getLink(channel, item);
		if (link == null) {
			logger.warn("No link found for channel {} and item {}",channel.getUID(), item.getName());
			return;
		}
		
		// remove link from internal cache
		synchronized(links){
			links.remove(link);
		}
		
		// inform ProvierChangeListener about removed link
		synchronized(listeners){
			for (ProviderChangeListener<ItemChannelLink> listener : listeners){
				listener.removed(this,link);
			}
		}
	}
	
	
	public void activate(){
		// not used yet
	}
	
	
	public void deactivate(){
		// not used yet
	}

	/**
	 * {@inheritedDoc}
	 */
	@Override
	public void unlinkAll(Channel channel) {
		// search links assigned to given channel
		List<ItemChannelLink> linksToRemove = getLinks(channel);
		if (linksToRemove.isEmpty()){
			logger.warn("no links found for given channel {}",channel.getUID());
			return;
		}
		synchronized(links){
			for (ItemChannelLink link : linksToRemove){
				links.remove(link);
				synchronized(listeners){
					for (ProviderChangeListener<ItemChannelLink> listener : listeners){
						listener.removed(this,link);
					}
				}
			}
		}
	}

	

}
