/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/umich/gcalendar/gcalendar-api/api/src/java/org/sakaiproject/gcalendar/cover/SakaiGCalendarService.java $
 * $Id: SakaiGCalendarService.java 82630 2013-02-07 14:15:50Z wanghlxr@umich.edu $
 ***********************************************************************************
 *
 * Copyright (c) 2013 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.gcalendar.cover;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.site.api.Site;

public class SakaiGCalendarService    {
	private static org.sakaiproject.gcalendar.api.SakaiGCalendarService m_instance = null;

	/**
	 * Access the component instance: special cover only method.
	 * @return the component instance.
	 */
	public static org.sakaiproject.gcalendar.api.SakaiGCalendarService getInstance() {
		if (ComponentManager.CACHE_COMPONENTS) {
			if (m_instance == null) m_instance = (org.sakaiproject.gcalendar.api.SakaiGCalendarService) ComponentManager.get(org.sakaiproject.gcalendar.api.SakaiGCalendarService.class);
			return m_instance;
		} else {
			return (org.sakaiproject.gcalendar.api.SakaiGCalendarService) ComponentManager.get(org.sakaiproject.gcalendar.api.SakaiGCalendarService.class);
		}
	}
    
	/**
	 * save the Google Calendar Id to the site property and return the Access Token
	 * 
	 * @param Site
	 * @return String
	 *        
	 */
    public static String saveGoogleCalendarInfo(Site site) {
    	org.sakaiproject.gcalendar.api.SakaiGCalendarService service = getInstance();
		if (service == null) {
			return null;
		}
		
		return service.saveGoogleCalendarInfo(site);
    }
    
    /**
	 * get the Google Calendar Access Token
	 * 
	 * @param String
	 * @return String
	 *        
	 */
    public static String getGCalendarAccessToken(String gcalid) {
    	org.sakaiproject.gcalendar.api.SakaiGCalendarService service = getInstance();
		if (service == null) {
			return null;
		}
		
		return service.getGCalendarAccessToken(gcalid);
    }

    /**
	 * get the Google Calendar Access Token
	 * 
	 * @param String
	 * @param String
	 * @return String
	 *        
	 */
    public static String getGCalendarAccessToken(String gcalid, String emailId) {
    	org.sakaiproject.gcalendar.api.SakaiGCalendarService service = getInstance();
		if (service == null) {
			return null;
		}
		
		return service.getGCalendarAccessToken(gcalid, emailId);
    }
    
    /**
	 * Adding user to google calendar acl (access control list)
	 * 
	 * @param Site
	 *        
	 */
	public static void addUserToAccessControlList(Site site, String perm) {
		org.sakaiproject.gcalendar.api.SakaiGCalendarService service = getInstance();
		if (service == null) {
			return;
		}
		
		service.addUserToAccessControlList(site, perm);
	}
	
}
