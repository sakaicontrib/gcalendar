 /**********************************************************************************
 * $URL$
 * $Id$
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

package org.sakaiproject.gcalendar.tool;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lombok.Setter;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.cheftool.Context;
import org.sakaiproject.cheftool.ControllerState;
import org.sakaiproject.cheftool.JetspeedRunData;
import org.sakaiproject.cheftool.PagedResourceActionII;
import org.sakaiproject.cheftool.RunData;
import org.sakaiproject.cheftool.VelocityPortlet;
import org.sakaiproject.cheftool.api.Menu;
import org.sakaiproject.cheftool.api.MenuItem;
import org.sakaiproject.cheftool.menu.MenuDivider;
import org.sakaiproject.cheftool.menu.MenuEntry;
import org.sakaiproject.cheftool.menu.MenuImpl;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.event.api.SessionState;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.gcalendar.api.SakaiGCalendarServiceStaticVariables;
import org.sakaiproject.gcalendar.cover.SakaiGCalendarService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.user.api.ContextualUserDisplayService;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesEdit;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.PermissionsHelper;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.authz.cover.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.exception.PermissionException;

/**
 * <p>
 * GCalendarAction
 * </p>
 */
public class GCalendarAction extends PagedResourceActionII
{
	// This property is used in object serialization.
	private static final long serialVersionUID = -5477742481219305334L;
	
	private static final String PERMISSIONS_BUTTON_HANDLER = "doPermissions";
	private static final String UPDATE_PERMISSIONS = "realm.upd";
	private static final String EMBEDED_GOOGLE_CALENDAR_URL = "http://www.google.com/calendar/embed?src=";
	private static final String TIMEZONE_TAG = "&ctz=";
	
	/** Our logger. */
	private static Log M_log = LogFactory.getLog(GCalendarAction.class);
	
	/** Resource bundle using current language locale */
	private static ResourceLoader rb = new ResourceLoader(SakaiGCalendarServiceStaticVariables.GCALENDAR);

	protected SecurityService securityService = null;
	
	protected void initState(SessionState state, VelocityPortlet portlet, JetspeedRunData rundata) {
		super.initState(state, portlet, rundata);
		
		// get the SecurityService from the ComponentManager instead of injection
		securityService = (SecurityService) ComponentManager.get("org.sakaiproject.authz.api.SecurityService");
	}

	/**
	 * Setup the velocity context and choose the template for the response
	 * 
	 * @param VelocityPortlet
	 * @param Context
	 * @param RunData
	 * @param SessionState
	 * @return String
	 *     
	 */
	public String buildMainPanelContext(VelocityPortlet portlet, Context context, RunData rundata, SessionState state) {
		context.put("tlang", rb);
		
		// Build the DelegateAccessContext template for showing google calendar
		String template = null;
		template = buildDelegateAccessContext(context, portlet, rundata, state);

		// Get proxy base url from sakai.property file otherwise go directly to the google api's.
		String baseUrl = org.sakaiproject.component.cover.ServerConfigurationService.getString("proxy.base.url");
		if (!StringUtils.isEmpty(baseUrl)){
			context.put("baseUrl", baseUrl);
		}
		else{
			context.put("baseUrl", "https://www.googleapis.com");
		}
		// Get proxy name from sakai.property file.
		String proxyName = org.sakaiproject.component.cover.ServerConfigurationService.getString("proxy.name");
		if (!StringUtils.isEmpty(proxyName)){
			context.put("proxyName", proxyName);
		}
		else{
			context.put("proxyName", ""); // Set this to an empty string so the url will be formatted correctly.
		}
		// Set user's Sakai timezone so it can be used in the Google calendar API calls
		context.put("userTimeZone", TimeService.getLocalTimeZone().getID());

		String prefix = (String) getContext(rundata).get("template");
		return prefix + template;
	}

	/**
	 * Build the DelegateAccessContext template for showing google calendar
	 * 
	 * @param Context
	 * @return String
	 */
	private String buildDelegateAccessContext(Context context, VelocityPortlet portlet, RunData rundata, SessionState sstate) {
		
		String siteId = ToolManager.getCurrentPlacement().getContext();
		Site site = null;
		String accessToken = null;
		boolean viewDetailsAllowed = false;
		boolean createEventsAllowed = false;
		boolean gcalview = false;
		boolean hasGoogleAccount = false;
		String permission = null; // no default, we should not get this far if no permissions are set
		
		try {			
			site = SiteService.getSite(siteId);
			String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);
			// we need the gcalid to continue
			if (gcalid == null) {
				return "_nocalendar";
			}
			
			User currentUser = UserDirectoryService.getCurrentUser();
	    	String currentUserId = currentUser.getId();
	    	String siteServiceString = SiteService.siteReference(siteId);

			String emailAddress = currentUser.getEmail();
	    	
	    	// This is a hierarchical permission structure for Google Calendar permissions
	    	// Since these are all check boxes, this sets the permissions to the highest level
	    	// and the lower levels are suppressed (i.e. superuser/site update overrides eEdit overrides view.all overrides view).
	    	// These flags are used to control access in Full Calendar. 
	    	// Permissions set in Google Calendar are used to control access in Google Calendar.
	    	//
	    	// viewDetailsAllowed = the user can see the details of an event ~ gcal.view.all
	    	// createEventsAllowed = the user can see the details and create events ~ gcal.edit and site.upd.site.mbrshp
	    	// gcalview = the user can only view events, see only free/busy ~ gcal.view
	    	
	    	boolean isSuper = securityService.isSuperUser(currentUserId);

	    	// If the user IsSuperUser or has gcal.edit, they can edit
	    	if(isSuper || securityService.unlock(currentUserId, org.sakaiproject.site.api.SiteService.SECURE_UPDATE_SITE_MEMBERSHIP, siteServiceString  ) ) { 
				viewDetailsAllowed = true;
				createEventsAllowed = true;
				gcalview = true;
				permission = org.sakaiproject.site.api.SiteService.SECURE_UPDATE_SITE_MEMBERSHIP;
			}
			else if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, siteServiceString)) {
				viewDetailsAllowed = true;
				createEventsAllowed = true;
				gcalview = true;
				permission = SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT;
			}
			else if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW_ALL, siteServiceString)) {
				viewDetailsAllowed = true;
				createEventsAllowed = false;
				gcalview = true;
				permission = SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW_ALL;
			}
			else if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW, siteServiceString)) {
				viewDetailsAllowed = false;
				createEventsAllowed = false;
				gcalview = false;
				permission = SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW;
			}
			
	    	// Get the access token for the calendar/user
	    	accessToken = SakaiGCalendarService.getGCalendarAccessToken(gcalid, emailAddress );
	    	// If we can not get the access token, that means that the user does not have access to the calendar, yet
	    	if (accessToken == null ) {
	    		// Check to see if the user is a valid user (i.e. they have a google calendar of their own)	    	
		    	hasGoogleAccount = SakaiGCalendarService.isValidGoogleUser(emailAddress);
		   	}
	    	else {
	    		hasGoogleAccount = true;
	    	}
	    	
			
			// If the current user is NOT the site creator AND they have a good Google Email Account AND they are not a super user
			// 		Add or update the user's Google Calendar permissions acl (access control list).
	    	// We do not want to update super users permissions in Google because that would lead to support staff being added many 
	    	// Google Calendars when they view the calendar and are only helping users with issues.
	    	// Note that the Site creator permissions cannot be updated in Google
			if (!site.getCreatedBy().getEid().equalsIgnoreCase(UserDirectoryService.getCurrentUser().getEid()) && hasGoogleAccount && !isSuper) {
				SakaiGCalendarService.addUserToAccessControlList(site, permission);
			}
			
			// if no google account - then use the permissions set above to control access to the calendar in Sakai.
			if ( !hasGoogleAccount ) {
				M_log.warn( "User has no google account: " + currentUser.getEid());
			}
			
			// first time going into the gcalendar tool or no google account
			// Get the access token for the user or the delegated access of the site creator (make into a method)
			if ( accessToken == null ) {
				// save the Google Calendar Id to the site property and return the Access Token
				// get the Google Calendar Access Token if we have a valid google user and they are not a super user
				if ( hasGoogleAccount && !isSuper ) {
					accessToken = SakaiGCalendarService.getGCalendarAccessToken(gcalid, emailAddress);
				}
				else { // no Google account or they are a super user
					M_log.warn("buildDelegateAccessContext: " + "getGCalendar failed first try");
					// The user is not an authorized Google user (i.e. does not have a google email account in your service domain)
					// Use the owner's email because the site creator is the owner in Google and will always have access to the calendar.
					String ownerEmailId = site.getCreatedBy().getEmail();
					accessToken = SakaiGCalendarService.getGCalendarAccessToken(gcalid, ownerEmailId);
					if ( accessToken == null ) {
						M_log.error("buildDelegateAccessContext: " + "getGCalendar failed second try with owner email id " + ownerEmailId );
						return "_noaccess";
					}
				}
	    	}	
			
			// Override context values for super user and not google account folks
			if ( isSuper || !hasGoogleAccount ) {
				viewDetailsAllowed = false;
				createEventsAllowed = false;
			}
			// Allow admin users to create events on their own sites only.
			if (isSuper && currentUser.getEmail().equals(site.getCreatedBy().getEmail())){
				viewDetailsAllowed = true;
				createEventsAllowed = true;
			}
			
			// build the menu
			buildMenu(portlet, context, rundata, this.isOkToShowPermissionsButton(currentUserId, siteServiceString));
			
		    context.put("accesstoken", accessToken);
	        context.put(SakaiGCalendarServiceStaticVariables.GCALID, site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID));
	        context.put("viewDetailsAllowed", viewDetailsAllowed);
	        context.put("createEventsAllowed", createEventsAllowed);
	        context.put("gcalview", gcalview);
	        context.put("menu", this.isOkToShowPermissionsButton(currentUserId, siteServiceString) );
	        
	        // Build the calendar url link.
	        // Example of a link: http://www.google.com/calendar/embed?src=collab.its.umich.edu_un8adj8phhfpi0ssvp8rcbsjgc@group.calendar.google.com&ctz=America/New_York
	        StringBuilder buffer = new StringBuilder(EMBEDED_GOOGLE_CALENDAR_URL);
			buffer.append(gcalid);
			buffer.append(TIMEZONE_TAG);
			buffer.append( TimeService.getLocalTimeZone().getID() );
			context.put("googlelink",buffer );
			
	        return "_delegateaccess";
			
		} catch (IdUnusedException e) {
			M_log.warn("buildDelegateAccessContext: " + e.getMessage());
			return null;
		} catch (Exception e) {
			M_log.warn("buildDelegateAccessContext: " + e.getMessage());
			return null;
		}

	}

	/**
	 * Returns true if it is okay to show the permissions button in the menu.
	 */
	private boolean isOkToShowPermissionsButton(String currentUserId, String siteServiceString)
	{
		//if (SiteService.allowUpdateSite(ToolManager.getCurrentPlacement().getContext())) // this did not work - always returned true
		boolean isOkToShowPermissions = securityService.unlock(currentUserId, UPDATE_PERMISSIONS, siteServiceString  );
		return isOkToShowPermissions;
	}
	
	/**
	 * Build the menu.
	 */
	private void buildMenu(VelocityPortlet portlet, Context context, RunData rundata, 
			boolean menu_permissions )
	{
		if ( menu_permissions ) {
			Menu bar = new MenuImpl(portlet, rundata, "GCalendarAction");
			
			if (menu_permissions)
			{
				bar.add(new MenuEntry(rb.getString("java.permissions"), PERMISSIONS_BUTTON_HANDLER));
				//???stateForMenus.setAttribute(MenuItem.STATE_MENU, bar);
		        context.put(Menu.CONTEXT_MENU, bar);
			}
		}
	} 
	
	/**
	 * Fire up the permissions editor
	 */
	public void doPermissions(RunData data, Context context) {
		// get into helper mode with this helper tool
		startHelper(data.getRequest(), "sakai.permissions.helper");

		SessionState state = ((JetspeedRunData) data)
				.getPortletSessionState(((JetspeedRunData) data).getJs_peid());

		String contextString = ToolManager.getCurrentPlacement().getContext();
		String siteRef = SiteService.siteReference(contextString);

		// setup for editing the permissions of the site for this tool, using
		// the roles of this site, too
		state.setAttribute(PermissionsHelper.TARGET_REF, siteRef);

		// ... with this description
		state.setAttribute(PermissionsHelper.DESCRIPTION, rb
				.getString("setperfor")
				+ " " + SiteService.getSiteDisplay(contextString));

		// ... showing only locks that are prefixed with this
		state.setAttribute(PermissionsHelper.PREFIX, "gcal.");

	} // doPermissions
	
	@Override
	protected List readResourcesPage(SessionState state, int first, int last) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected int sizeResources(SessionState state) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Setter
	private SiteService siteService;

}
