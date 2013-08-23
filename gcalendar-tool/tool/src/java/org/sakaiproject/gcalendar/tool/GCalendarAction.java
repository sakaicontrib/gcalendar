package org.sakaiproject.gcalendar.tool;

import java.util.List;

import lombok.Setter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.cheftool.Context;
import org.sakaiproject.cheftool.JetspeedRunData;
import org.sakaiproject.cheftool.PagedResourceActionII;
import org.sakaiproject.cheftool.RunData;
import org.sakaiproject.cheftool.VelocityPortlet;
import org.sakaiproject.event.api.SessionState;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.gcalendar.cover.SakaiGCalendarService;
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
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.authz.cover.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.component.cover.ComponentManager;

/**
 * <p>
 * GCalendarAction
 * </p>
 */
public class GCalendarAction extends PagedResourceActionII
{
	private static final long serialVersionUID = -5477742481219305334L;
	
	// These belong elsewhere - like in API
	public static final String GCAL_VIEW = "gcal.view";
	public static final String GCAL_VIEW_ALL = "gcal.view.all";
	public static final String GCAL_EDIT = "gcal.edit";
	public static final String GCAL_ADMIN = "gcal.admin";
	
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
		template = buildDelegateAccessContext(context);

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
	private String buildDelegateAccessContext(Context context) {
		
		String siteId = ToolManager.getCurrentPlacement().getContext();
		Site site = null;
		String accessToken = null;
		boolean editAllowed = false;
		boolean hasGoogleAccount = true;
		String permission = GCAL_VIEW;
		
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
			
			// If current user is the site creator or has gcal.edit or gcal.admin, they can edit
			User currentUser = UserDirectoryService.getCurrentUser();
	    	String currentUserId = currentUser.getId();
	    	String siteServiceString = SiteService.siteReference(siteId);
	    	
	    	// This is a hierarchical permission structure for Google Calendar permissions
	    	// Since these are all check boxes, this sets the permissions to the highest level
	    	// and the lower levels are suppressed (i.e. admin overrides Edit, etc).
			if(securityService.unlock(currentUserId, GCAL_ADMIN, siteServiceString  ) ) { 
				editAllowed = true;
				permission = GCAL_ADMIN;
			}
			else if ( securityService.unlock(currentUserId, GCAL_EDIT, siteServiceString)) {
				editAllowed = true;
				permission = GCAL_EDIT;
			}
			else if ( securityService.unlock(currentUserId, GCAL_VIEW, siteServiceString)) {
				editAllowed = false;
				permission = GCAL_VIEW;
			}
			else if ( securityService.unlock(currentUserId, GCAL_VIEW_ALL, siteServiceString)) {
				editAllowed = false;
				permission = GCAL_VIEW_ALL;
			}
			
			// Creator?
			if (site.getCreatedBy().getEid().equalsIgnoreCase(UserDirectoryService.getCurrentUser().getEid()) ) { 
				M_log.debug("User is the creator:" + site.getCreatedBy() );
				editAllowed = true;
			}
			
			// If the current user is NOT the site creator AND they have a good Google Email Account
			// Adding user to google calendar acl (access control list)
			if (!site.getCreatedBy().getEid().equalsIgnoreCase(UserDirectoryService.getCurrentUser().getEid()) && hasGoogleAccount) {
				SakaiGCalendarService.addUserToAccessControlList(site, permission);
			}
			       
		    context.put("accesstoken", accessToken);
	        context.put("gcalid", site.getProperties().getProperty("gcalid"));
	        this.saved_gcalid = site.getProperties().getProperty("gcalid");
	        context.put("editAllowed", editAllowed);
	        
	        return "_delegateaccess";
			
		} catch (IdUnusedException e) {
			M_log.warn("buildDelegateAccessContext: " + e.getMessage());
			return null;
		} catch (Exception e) {
			M_log.warn("buildDelegateAccessContext: " + e.getMessage());
			return null;
		}

	}

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
