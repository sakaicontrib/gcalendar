/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/umich/google/gcalendar/gcalendar-api/api/src/java/org/sakaiproject/gcalendar/api/SakaiGCalendarService.java $
 * $Id: SakaiGCalendarService.java 82961 2013-03-08 20:22:38Z wanghlxr@umich.edu $
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
package org.sakaiproject.gcalendar.api;

import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.site.api.Site;


public interface SakaiGCalendarService extends EntityProducer {
	
	/**
	 * save the Google Calendar Id to the site property and return the Access Token
	 * 
	 * @param Site
	 * @return String Access Token
	 *        
	 */
	public String saveGoogleCalendarInfo(Site site);
	
	/**
	 * get the Google Calendar Access Token
	 * 
	 * @param String Google Calendar ID
	 * @return String Access Token
	 *        
	 */
	public String getGCalendarAccessToken(String gcalid);
	
	/**
	 * get the Google Calendar Access Token
	 * 
	 * @param String Google Calendar ID
	 * @param String Owner's email address
	 * @return String Access Token
	 *        
	 */
	public String getGCalendarAccessToken(String gcalid, String emailId);
	
	/**
	 * Adding user to google calendar acl (access control list)
	 * 
	 * @param Site
	 * @param Permission (gcal.view, gcal.view.all, gcalview.edit, or site.upd.site.mbrshp )
	 *        
	 */
	public void addUserToAccessControlList(Site site, String perm);
}
