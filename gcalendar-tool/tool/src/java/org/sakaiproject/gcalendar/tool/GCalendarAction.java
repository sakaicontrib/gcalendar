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
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.event.api.SessionState;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.gcalendar.api.SakaiGCalendarServiceStaticVariables;
import org.sakaiproject.gcalendar.cover.SakaiGCalendarService;
import org.sakaiproject.gcalendar.api.SakaiGCalendarServiceStaticVariables;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.user.api.ContextualUserDisplayService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
//? import org.sakaiproject.announcement.tool.AnnouncementActionState;
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
	private static final long serialVersionUID = -5477742481219305334L;
	
	private static final String PERMISSIONS_BUTTON_HANDLER = "doPermissions";
	private static final String UPDATE_PERMISSIONS = "site.upd";
	
	private String saved_gcalid = null;
	
	/** Our logger. */
	private static Log M_log = LogFactory.getLog(GCalendarAction.class);
	
	/** Resource bundle using current language locale */
	private static ResourceLoader rb = new ResourceLoader("gcalendar");
	
	// private static final String INSTRUCTOR_ROLE = "Instructor";

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

		StringBuilder buffer = null;
		
		if ( null != saved_gcalid && !saved_gcalid.isEmpty() ) {
			// example of a link: 
			//http://www.google.com/calendar/embed?src=collab.its.umich.edu_un8adj8phhfpi0ssvp8rcbsjgc@group.calendar.google.com&ctz=America/New_York
			buffer = new StringBuilder("http://www.google.com/calendar/embed?src=");
			buffer.append(saved_gcalid);
			buffer.append("&ctz=");
			buffer.append( TimeService.getLocalTimeZone().getID() );
		} else {
			template = "_nocalendar";
		}
		context.put("googlelink",buffer );
		
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
		boolean editAllowed = false;
		boolean gcalview = false;
		boolean hasGoogleAccount = true;
		String permission = null; // no default, we should not get this far if no permissions are set
		
		try {			
			site = SiteService.getSite(siteId);
			String gcalid = site.getProperties().getProperty("gcalid");
			
			if (gcalid == null) {
				// save the Google Calendar Id to the site property and return the Access Token
				accessToken = SakaiGCalendarService.saveGoogleCalendarInfo(site);
				if (accessToken == null) {
					M_log.warn("buildDelegateAccessContext: " + "saveGoogleCalendarInfo failed");
					return "_noaccess";
				}
			} else {
				// get the Google Calendar Access Token
				accessToken = SakaiGCalendarService.getGCalendarAccessToken(gcalid);
				if (accessToken == null) {
					M_log.warn("buildDelegateAccessContext: " + "getGCalendar failed first try");
					
					// Use the owner's email and put into context "editAllowed = false"
					String ownerEmailId = site.getCreatedBy().getEmail();
					M_log.debug("buildDelegateAccessContext: owner's email " + ownerEmailId );
					accessToken = SakaiGCalendarService.getGCalendarAccessToken(gcalid, ownerEmailId);
					if ( accessToken == null ) {
						M_log.warn("buildDelegateAccessContext: " + "getGCalendar failed second try with owner email id " + ownerEmailId );
						return "_noaccess";
					}
					editAllowed = false;
					hasGoogleAccount = false;
				}
			}
			
			// If the user IsSuperUser or has gcal.edit, they can edit
			User currentUser = UserDirectoryService.getCurrentUser();
	    	String currentUserId = currentUser.getId();
	    	String siteServiceString = SiteService.siteReference(siteId);
	    	
	    	// This is a hierarchical permission structure for Google Calendar permissions
	    	// Since these are all check boxes, this sets the permissions to the highest level
	    	// and the lower levels are suppressed (i.e. superuser/site update overrides eEdit overrides view.all overrides view).
	    	boolean isSuper = securityService.isSuperUser(currentUserId);
	    	
	    	if(isSuper || securityService.unlock(currentUserId, org.sakaiproject.site.api.SiteService.SECURE_UPDATE_SITE_MEMBERSHIP, siteServiceString  ) ) { 
				editAllowed = true;
				permission = org.sakaiproject.site.api.SiteService.SECURE_UPDATE_SITE_MEMBERSHIP;
				if (isSuper)
					hasGoogleAccount = true;
			}
			else if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, siteServiceString)) {
				editAllowed = true;
				permission = SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT;
			}
			else if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW_ALL, siteServiceString)) {
				editAllowed = false;
				permission = SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW_ALL;
			}
			else if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW, siteServiceString)) {
				editAllowed = false;
				gcalview = true;
				permission = SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW;
			}
			
			// If the current user is NOT the site creator AND they have a good Google Email Account
			// Adding user to google calendar acl (access control list)
	    	// Site creator permissions can not be updated in Google
			if (!site.getCreatedBy().getEid().equalsIgnoreCase(UserDirectoryService.getCurrentUser().getEid()) && hasGoogleAccount) {
				SakaiGCalendarService.addUserToAccessControlList(site, permission);
			}
			
			// if no google account - then read only in Sakai (i.e. no access to google calendar)
			// override any previous permission values
			if ( !hasGoogleAccount ) {
				editAllowed = false;
				M_log.warn( "User has no google account: " + currentUser );
			}
						
			// build the menu
			buildMenu(portlet, context, rundata, this.isOkToShowPermissionsButton(currentUserId, siteServiceString));
			
		    context.put("accesstoken", accessToken);
	        context.put("gcalid", site.getProperties().getProperty("gcalid"));
	        this.saved_gcalid = site.getProperties().getProperty("gcalid");
	        context.put("editAllowed", editAllowed);
	        context.put("gcalview", gcalview);
	        context.put("menu", this.isOkToShowPermissionsButton(currentUserId, siteServiceString) );
	        
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
		boolean isOkToShowPermissions = securityService.unlock(currentUserId, "realm.upd", siteServiceString  );
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
	
	/**
	 * Get the proper state for this instance (if portlet id is known).
	 * 
	 * @param peid
	 *        The portlet id.
	 * @param rundata
	 *        The Jetspeed (Turbine) rundata associated with the request.
	 * @param stateClass
	 *        The Class of the ControllerState to find / create.
	 * @return The proper state object for this instance.
	 */
	protected ControllerState getState(String peid, RunData rundata, Class stateClass)
	{
		if (peid == null)
		{
			M_log.warn(this + ".getState(): peid null");
			return null;
		}

		try
		{
			// get the PortletSessionState
			SessionState ss = ((JetspeedRunData) rundata).getPortletSessionState(peid);

			// get the state object
			ControllerState state = (ControllerState) ss.getAttribute("state");

			if (state != null) return state;

			// if there's no "state" object in there, make one
			state = (ControllerState) stateClass.newInstance();
			state.setId(peid);

			// remember it!
			ss.setAttribute("state", state);

			return state;
		}
		catch (Exception e)
		{
			M_log.warn(this+ ".getState", e);
		}

		return null;

	} // getState
	
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
