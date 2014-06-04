/*
* Licensed to The Apereo Foundation under one or more contributor license
* agreements. See the NOTICE file distributed with this work for
* additional information regarding copyright ownership.
*
* The Apereo Foundation licenses this file to you under the Educational 
* Community License, Version 2.0 (the "License"); you may not use this file 
* except in compliance with the License. You may obtain a copy of the 
* License at:
*
* http://opensource.org/licenses/ecl2.txt
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.sakaiproject.gcalendar.impl;

import java.util.Observable;
import java.util.Observer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.gcalendar.api.SakaiGCalendarServiceStaticVariables;
import org.sakaiproject.site.api.Site;

/**
 * This observer is notified when a site duplication is finished so it can remove the gcalid for the original site
 *  from the new site properties.
 */
public class GcalEventWatcher implements Observer{

	private static Log log = LogFactory.getLog(GcalEventWatcher.class);
	
	/** Dependency: SiteService */
	protected SiteService m_siteService = null;
	
	/** Dependency: event tracking service */
	protected EventTrackingService m_eventTrackingService = null;
	
	/**
	 * Dependency: SiteService.
	 * @param service The SiteService.
	 */
	public void setSiteService(SiteService service)
	{
		m_siteService = service;
	}
	/**
	 * Dependency: event tracking service.
	 * @param service The event tracking service.
	 */
	public void setEventTrackingService(EventTrackingService service)
	{
		m_eventTrackingService = service;
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		try
		{
			log.info(this +".init()");
			
			// start watching the events - only those generated on this server, not those from elsewhere
			m_eventTrackingService.addLocalObserver(this);
		}
		catch (Throwable t)
		{
			log.warn(this +".init(): ", t);
		}
	}
	
	@Override
	public void update(Observable o, Object arg) {
		if (!(arg instanceof Event)){
			return;
		}
		Event event = (Event) arg;
		String function = event.getEvent();
		// After a site is duplicated we want to remove the gcalid property if present.
		// This will force the site to create its own new Google calendar.
		if (function.equals(SiteService.EVENT_SITE_DUPLICATE_END)){
			log.debug("Site duplication ended. Going to remove gcalid property");
			String newSiteId = event.getContext();

			try {
				Site site = m_siteService.getSite(newSiteId);
				log.debug("Retrieved Site: " + site.getTitle());
				// Remove gcalid property
				site.getPropertiesEdit().removeProperty(SakaiGCalendarServiceStaticVariables.GCALID);
				
				m_siteService.save(site);
					
			} catch (IdUnusedException e) {
				log.error("Problem retrieving site" + e.getMessage());
			} catch (PermissionException e) {
				log.error("Permission problem saving site" + e.getMessage());
			}
		}
	}

	/**
	* Returns to uninitialized state.
	*/
	public void destroy()
	{
		// done with event watching
		m_eventTrackingService.deleteObserver(this);

		log.info(this +".destroy()");
	}

}
