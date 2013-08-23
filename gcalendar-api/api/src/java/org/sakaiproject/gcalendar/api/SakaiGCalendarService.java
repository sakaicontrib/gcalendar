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
	/** This string starts the references to resources in this service. */
	public static final String REFERENCE_ROOT = Entity.SEPARATOR + "gcalendar";
	
	public static final String GCAL_VIEW = "gcal.view";
	public static final String GCAL_VIEW_ALL = "gcal.view.all";
	public static final String GCAL_EDIT = "gcal.edit";
	public static final String GCAL_ADMIN = "gcal.admin";
	
	public static final String RULE_ROLE_READER = "reader"; // Provides read access to the calendar. Private events will appear to users with reader access, but event details will be hidden.
	public static final String RULE_ROLE_OWNER = "owner"; // Provides ownership of the calendar. This role has all of the permissions of the writer role with the additional ability to see and manipulate ACLs.
	public static final String RULE_ROLE_WRITER = "writer"; // Provides read and write access to the calendar. Private events will appear to users with writer access, and event details will be visible.
	public static final String RULE_ROLE_FREEBUSYREADER = "freeBusyReader"; // Provides read access to free/busy information.
	public static final String RULE_ROLE_NONE = "none"; // Provides no access
	
	public static String RULE_SCOPE_TYPE_USER = "user"; //  Limits the scope to a single user.
	/**
	 * save the Google Calendar Id to the site property and return the Access Token
	 * 
	 * @param Site
	 * @return String
	 *        
	 */
	public String saveGoogleCalendarInfo(Site site);
	
	/**
	 * get the Google Calendar Access Token
	 * 
	 * @param String
	 * @return String
	 *        
	 */
	public String getGCalendarAccessToken(String gcalid);
	
	/**
	 * get the Google Calendar Access Token
	 * 
	 * @param String
	 * @param String
	 * @return String
	 *        
	 */
	public String getGCalendarAccessToken(String gcalid, String emailId);
	
	/**
	 * Adding user to google calendar acl (access control list)
	 * 
	 * @param Site
	 *        
	 */
	public void addUserToAccessControlList(Site site, String perm);
}
