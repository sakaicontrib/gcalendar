/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/umich/gcalendar/gcalendar-api/api/src/java/org/sakaiproject/gcalendar/impl/SakaiGCalendarServiceImpl.java $
 * $Id: SakaiGCalendarServiceImpl.java 82630 2013-02-07 14:15:50Z wanghlxr@umich.edu $
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

package org.sakaiproject.gcalendar.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.calendar.api.CalendarEdit;
import org.sakaiproject.calendar.api.CalendarEvent;
import org.sakaiproject.calendar.api.CalendarEvent.EventAccess;
import org.sakaiproject.calendar.api.CalendarEventEdit;
import org.sakaiproject.calendar.api.CalendarEventVector;
import org.sakaiproject.calendar.api.CalendarService;
import org.sakaiproject.calendar.api.RecurrenceRule;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entity.api.ContextObserver;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.gcalendar.api.SakaiGCalendarService;
import org.sakaiproject.google.impl.SakaiGoogleAuthServiceImpl;
import org.sakaiproject.gcalendar.api.SakaiGCalendarServiceStaticVariables;
import org.sakaiproject.javax.Filter;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesEdit;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeRange;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.tool.cover.ToolManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Lists;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.AclRule;
import com.google.api.services.calendar.model.AclRule.Scope;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

public class SakaiGCalendarServiceImpl implements SakaiGCalendarService, ContextObserver {
	
	/** Our logger. */
	private static Log M_log = LogFactory.getLog(SakaiGCalendarServiceImpl.class);
	
	/** Dependency: SiteService. */
	protected SiteService m_siteService = null;
	
	/** Dependency: EntityManager */
	protected EntityManager m_entityManager = null;
	
	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService m_serverConfigurationService = null;
	
	/** Dependency: PreferencesService */
	protected PreferencesService m_preferencesService = null;
	
	/** The string that Google Calendar uses for preferences */
	private static final String GOOGLE_CALENDAR_PREFS = "sakai:google:calendar";
	
	/** The string for Google-CalendarSample/1.0, used for Google API calls */
	private static final String GOOGLE_CALENDAR_SAMPLE_1_0 = "Google-CalendarSample/1.0";
	
	/** Global instance of the HTTP transport. */
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();

	static final java.util.List<Calendar> addedCalendarsUsingBatch = Lists.newArrayList();

	/** Service account e-mail address */
	private static String SERVICE_ACCOUNT_EMAIL = null;
	/** Service account private key */
	private static String PRIVATE_KEY = null;
	
	/** Strings for setting Google ACL's */
	private static final String DOMAIN = "domain";
	private static final String COLON = ":";
	private static final String ACL_NONE = "none";
	private static final String ACL_DEFAULT = "default";
	
	private FunctionManager functionManager;
	public void setFunctionManager(FunctionManager functionManager) {
		this.functionManager = functionManager;
	}
	
	protected SecurityService securityService;
	public void setSecurityService(SecurityService securityService) {
		this.securityService = securityService;
	}
	/** Authorizes the service account to access user's protected data. */
	private GoogleCredential getGoogleCredential(String userid) {		
		
		try { 
			// get the service account e-mail address and service account private key from property file
			// this email account and key file are specific for your environment
			if (SERVICE_ACCOUNT_EMAIL == null) {
				SERVICE_ACCOUNT_EMAIL = m_serverConfigurationService.getString("google.service.account.email", "");
			}
			if (PRIVATE_KEY == null) {
				PRIVATE_KEY = m_serverConfigurationService.getString("google.private.key", "");
			}
			GoogleCredential credential = SakaiGoogleAuthServiceImpl.authorize(userid, SERVICE_ACCOUNT_EMAIL, PRIVATE_KEY, CalendarScopes.CALENDAR);
			
			return credential;

		} catch (Exception e) {
			// return null if catch exception so we will not create a google calendar
			M_log.error("getGoogleCredential: " + e.getMessage());
			return null;
		}
	}
	
	public boolean isValidGoogleUser( String emailID ) {
		
		Calendar client;
		
		GoogleCredential cred = getGoogleCredential( emailID);
		if ( cred == null )
			return false;
		
		// At this point credential has no Access Token
		client = getGoogleClient( cred );

		// get the primary Google calendar
		// This line of code fill in the Access Token
		try {
			com.google.api.services.calendar.model.Calendar calendar = client.calendars().get("primary").execute();
			// if the user has not primary google calendar then they are not a Google user
			if ( calendar == null )
				return false;
			
		} catch (IOException e) {
			M_log.debug("isValidGoogleUser - IOException: " + e.getMessage());
			return false;
		}				
		// user is a valid Google user		
		return true;
	}

	@Override
	public void contextCreated(String context, boolean toolPlacement) {

		contextHelper(context, toolPlacement);
	}

	@Override
	public void contextUpdated(String context, boolean toolPlacement) {

		contextHelper(context, toolPlacement);
	}
	
	private void contextHelper(String context, boolean toolPlacement ) {
	}

	@Override
	public void contextDeleted(String context, boolean toolPlacement) {

		// Google calendars should never be deleted from Sakai 
		// Deletion should be done deliberately via the Google UI
	}

	/**
	 * Provide the string array of tool ids, for tools that we need context preperation for.
	 * Get a list of all tools that support the import (transfer copy) copy
	 * 
	 * @return tool IDs
	 */
	@Override
	public String[] myToolIds() {
		String[] toolIds = { "sakai.gcalendar" };
		return toolIds;
	}
		

	/**
	 * Get the Google Calendar ID from Google
	 * 
	 * @param Site site
	 * @return String Google Calendar ID or null, if not found
	 * 
	 */
	private String getGoogleCalendarID(Site site) {

		String gcalid = null;
		
		// get the Google Calendar ID and use it if found
		gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);
		
		if ( gcalid != null && !gcalid.isEmpty() )
			return gcalid;
		
		return null;
	}
	
	/**
	 * Create a calendar for the site.
	 * 
	 * @param Site to which a Google calendar will be added
	 * @return String Google calendar Id
	 */
	public String enableCalendar(Site site){
		com.google.api.services.calendar.model.Calendar siteGCalendar = null;
		
		Calendar client;
		
		GoogleCredential credential = getGoogleCredential(site.getCreatedBy().getEmail());
		if (credential == null) {
			return null; // user not authorized
		}
		
		client = getGoogleClient( credential );

		siteGCalendar = createGoogleCalendar(site, client);
		if (siteGCalendar != null){
			return siteGCalendar.getId();
		}
		else {
			return null;
		}
	}
	/**
	 * Add new calendar for a Sakai site.
	 * 
	 * @param Site
	 * @param client - the Calendar client
	 * @return com.google.api.services.calendar.model.Calendar
	 * 
	 */

	private com.google.api.services.calendar.model.Calendar createGoogleCalendar(Site site, Calendar client) {

		String gcalID;
		
		com.google.api.services.calendar.model.Calendar calendar = new com.google.api.services.calendar.model.Calendar();

		calendar.setSummary(site.getTitle());
		calendar.setTimeZone( TimeService.getLocalTimeZone().getID() );
		calendar.setKind("calendar#calendar"); // this string is a special string and is defined by Google. There are other kind strings.

		com.google.api.services.calendar.model.Calendar createdCalendar = null;
		try {
			createdCalendar = client.calendars().insert(calendar).execute();
		} catch (IOException e) {
			M_log.warn( "createGoogleCalendar() failed. User may not have a valid google account: " + site.getCreatedBy().getEmail());
		}
		
		if ( createdCalendar == null )
			return null;
		
		// Set permissions on the calendar for global sharing
		// First retrieve the access rule from the API.
		// Insert a new one if it exists (updating did not work well)
		gcalID = createdCalendar.getId();
		
		// Now that we have a Google Calendar Id, add it to the site properties.
		addGcalendarIdToSite(site, gcalID);   
		
		try {
			AclRule ruleDefault = client.acl().get(gcalID, ACL_DEFAULT).execute();
			M_log.debug(" ruleDefault : " + ruleDefault.toPrettyString());
			
			
			// check to see if we need to update the default ACL
			if ( ruleDefault != null && !ruleDefault.getRole().isEmpty()) {
				Scope scopeDefault = new Scope();
		
				scopeDefault.setType(ACL_DEFAULT);
				scopeDefault.setValue(ACL_NONE);
				
				ruleDefault = new AclRule();
				ruleDefault.setScope(scopeDefault);
				ruleDefault.setRole(ACL_NONE); 

				AclRule rule = client.acl().insert(gcalID, ruleDefault).execute();
				if ( rule == null )
					M_log.error( "Setting Default Google Calendar ACL failed for Calendar ID: " + gcalID);
			}	   
		} catch (IOException e ){
			// If the exception is a 404 - page not found, then that means that the permission is not set in Google
			int pos404 = e.getMessage().indexOf("404");
			// make sure that 404 is early in the message 
			if(pos404 >= 0 && pos404 <= 20 ) {
				// okay to continue - Google does not have the permission set
			} 
			else {
				M_log.error("Error setting the default Calendar ACL: " + e.getMessage());
			}
		} catch ( Exception eee) {
			M_log.error("Setting default Calendar ACL: " + eee.getMessage());
		}
		
		
		// and for the domain
		try {
			String emailAddress = site.getCreatedBy().getEmail();
			if ( null != emailAddress) {
				String emailDomain = emailAddress.substring(emailAddress.indexOf('@') + 1 );
				String aclDomainString = DOMAIN + COLON + emailDomain; // domain:emaildomain
				AclRule ruleDomain = client.acl().get(gcalID, aclDomainString).execute();
				
				// Check to see if we need to update the domain ACL
				if ( ruleDomain != null && !ruleDomain.getRole().isEmpty() ) {
					Scope scopeDomain = new Scope();
			
					scopeDomain.setType(DOMAIN);
					scopeDomain.setValue(emailDomain);
					
					AclRule ruleDefault = new AclRule();
					ruleDefault.setScope(scopeDomain);
					ruleDefault.setRole(ACL_NONE); 
			
					//AclRule updatedDomain = client.acl().update(gcalID, ruleDefault.getId(), ruleDefault).execute();
					AclRule rule = client.acl().insert(gcalID, ruleDefault).execute();
					if ( null == rule || rule.isEmpty() ) {
						M_log.error("Failed to update ACL for domain associated with Calendar ID:" + gcalID);
					}
				}
				else {
					M_log.error("Google Calendar Creator does not have an associated email address for Calendar ID" + gcalID);
				}
			}
		} catch (IOException e ){
			// If the exception is a 404 - page not found, then that means that the permission is not set in Google
			int pos404 = e.getMessage().indexOf("404");
			// make sure that 404 is early in the message 
			if(pos404 >= 0 && pos404 <= 20 ) {
				// okay to continue - Google does not have the permission set
			} 
			else {
				M_log.error("Error setting the domain Calendar ACL: " + e.getMessage());
			}
		} catch ( Exception eee ) {
			M_log.error("Error setting the Google domain permissions:" + eee.getMessage());
		}
		return createdCalendar;
	}

	/**
	 * get google calendar access token for a Sakai site.
	 * 
	 * @param String google calendar id
	 * @param String user's Email ID
	 * @return String google calendar access token
	 * 
	 */
	public String getGCalendarAccessToken( String gcalid, String emailID) {
		
		Calendar client;
		
		GoogleCredential credential = getGoogleCredential(emailID);
		if (credential == null) {
			return null; // user not authorized
		}
		// At this point credential has no Access Token
		client = getGoogleClient( credential );

		// get the calendar using gcalid stored in the site property from sakai db
		// This line of code fill in the Access Token - it can not be refactored out
		try {
			com.google.api.services.calendar.model.Calendar calendar = client.calendars().get(gcalid).execute();
		} catch (IOException e) {
			M_log.error("getGCalendarAccessToken - IOException: " + e.getMessage());
			return null;
		}
		return credential.getAccessToken();
	}
    
    /**
	 * Adding user to google calendar acl (access control list)
	 * 
	 * @param Site
	 *        
	 */
    public void addUserToAccessControlList(Site site, String permission) {
    	
    	String gcalid = getGoogleCalendarID( site);
    	if (gcalid == null){
    		M_log.error("Google calendar id not found in site properties for site: " + site.getId());
    		return;
    	}
    	
    	String currentUserId = getCurrentUserId();
    	
    	Calendar client;
    	
    	Preferences prefs = (PreferencesEdit)m_preferencesService.getPreferences(currentUserId);
		ResourceProperties props = prefs.getProperties(GOOGLE_CALENDAR_PREFS);
		String googleCalendarPrefPropValue = props.getProperty(gcalid);
		boolean isSuper = securityService.isSuperUser(currentUserId);
    	
		// if the current user is a valid user in the site or a superuser
		// and if the current user is not in google calendar acl (access control list)
		// OR if their permissions have changed
		// add the current user to the google calendar acl (access control list)		
		if (site.getMember(currentUserId) != null 
				&& ( googleCalendarPrefPropValue == null || !googleCalendarPrefPropValue.equals(permission) )
				|| isSuper ) {
			
			// Site creator 
			String siteCreatorEmailAddress = site.getCreatedBy().getEmail();
			
			// no email associated with the site owner results in the call to Google to fail.
			if ( null == siteCreatorEmailAddress || siteCreatorEmailAddress.isEmpty() ) {
				// TODO: error message to the user here?
				M_log.error("Missing email address for site creator - unable to create google calender. Site " + site.getTitle() + " creator " + site.getCreatedBy().getSortName() );
				return;
			}
			
			// Create ACL using site creator email
			GoogleCredential credential = getGoogleCredential(siteCreatorEmailAddress);
			
			if (credential == null) {
				M_log.error("addUserToAccessControlList: bad credentials for " + siteCreatorEmailAddress);
				return; // user not authorized
			}

			client = getGoogleClient( credential );
				
			String currentUserEmailAddress = getUserEmailAddress();
			AclRule rule = new AclRule();
			Scope scope = new Scope();

			scope.setType(SakaiGCalendarServiceStaticVariables.RULE_SCOPE_TYPE_USER);
			scope.setValue(currentUserEmailAddress);
			rule.setScope(scope);
			
			// Determine Google calendar permissions based on Sakai permissions
			if ( permission.equals(org.sakaiproject.site.api.SiteService.SECURE_UPDATE_SITE_MEMBERSHIP)) {
				rule.setRole(SakaiGCalendarServiceStaticVariables.RULE_ROLE_OWNER);
			}
			else if ( permission.equals(SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT)) {
				rule.setRole(SakaiGCalendarServiceStaticVariables.RULE_ROLE_WRITER);
			}
			else if ( permission.equals(SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW_ALL) || isSuper) {
				rule.setRole(SakaiGCalendarServiceStaticVariables.RULE_ROLE_READER);
			}
			else if ( permission.equals(SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW)) {
				rule.setRole(SakaiGCalendarServiceStaticVariables.RULE_ROLE_FREEBUSYREADER);
			}
			else {
				rule.setRole(SakaiGCalendarServiceStaticVariables.RULE_ROLE_FREEBUSYREADER); // should never fall through
			}
			// TODO: although this seems to work, we should update the role if it already exists AND we should test the return
			try {
				AclRule createdRule = client.acl().insert(gcalid, rule).execute();
			} catch (IOException e) {
				M_log.error("addUserToAccessControlList - IOException: " + e.getMessage());
				return;
			}
			
			// Save gcalid in user preferences (so we know the user has been added to the google calendar ACL)		
			saveGCalProperty(currentUserId, gcalid, permission);
    	}
    }
    
    /**
     * Retrieves current user id.
     * @return
     */
	private String getCurrentUserId() {
		User currentUser = UserDirectoryService.getCurrentUser();
    	String currentUserId = currentUser.getId();
		return currentUserId;
	}
    
    /**
	 * Set editing mode on for user and add user if not existing
	 */
	private PreferencesEdit getPreferencesEdit(String userId) {

		PreferencesEdit m_edit = null;
		try {
			m_edit = m_preferencesService.edit(userId);
		} catch (IdUnusedException e) {
			try {
				m_edit = m_preferencesService.add(userId);
			} catch (Exception ee) {
				M_log.error("getPreferencesEdit: " + e.getMessage());
				return null;
			}
		} catch (InUseException e) {
			M_log.error("getPreferencesEdit: " + e.getMessage());
			return null;
		} catch (PermissionException e) {
			M_log.error("getPreferencesEdit: " + e.getMessage());
			return null;
		}
		
		return m_edit;
	}
	
	/**
	 * Save google calendar id in user preferences
	 */
	private void saveGCalProperty(String currentUserId, String gcalid, String perm) {
		PreferencesEdit m_edit = getPreferencesEdit(currentUserId);
		
		ResourcePropertiesEdit props = m_edit.getPropertiesEdit( GOOGLE_CALENDAR_PREFS );
		props.addProperty(gcalid, perm); // Save the permission to see if it changes the next time they sign in
		
		m_preferencesService.commit(m_edit);
	}

	/**
	 * get current user email address.
	 * 
	 * @return String
	 * 
	 */
	private String getUserEmailAddress() {
		User user = UserDirectoryService.getCurrentUser();
		String emailAddress = user.getEmail();

		return emailAddress;
	}
	
	/**
	 * get the client
	 * 
	 * @param Google Credentials
	 * 
	 */
	private Calendar getGoogleClient( GoogleCredential credentials ) {
		
			return new com.google.api.services.calendar.Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
				.setApplicationName(GOOGLE_CALENDAR_SAMPLE_1_0)
				.build();
		
	}

	/**
	 * Creates service object to interact with Google API
	 * @param email
	 * @return Calendar client used to call api's
	 */
	private Calendar getGoogleClient(String email)	{
		GoogleCredential credential = getGoogleCredential(email);
		Calendar client = getGoogleClient(credential);
		return client;
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.entity.api.EntityProducer#getLabel()
	 */
	public String getLabel() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.entity.api.EntityProducer#willArchiveMerge()
	 */
	public boolean willArchiveMerge() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.entity.api.EntityProducer#archive(java.lang.String,
	 * org.w3c.dom.Document, java.util.Stack, java.lang.String, java.util.List)
	 */
	public String archive(String siteId, Document doc, Stack<Element> stack,
			String archivePath, List<Reference> attachments) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.entity.api.EntityProducer#merge(java.lang.String,
	 * org.w3c.dom.Element, java.lang.String, java.lang.String, java.util.Map,
	 * java.util.Map, java.util.Set)
	 */
	public String merge(String siteId, Element root, String archivePath,
			String fromSiteId, Map<String, String> attachmentNames,
			Map<String, String> userIdTrans, Set<String> userListAllowImport) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sakaiproject.entity.api.EntityProducer#parseEntityReference(java.
	 * lang.String, org.sakaiproject.entity.api.Reference)
	 */
	public boolean parseEntityReference(String reference, Reference ref) {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.entity.api.EntityProducer#getEntityDescription(org.
	 * sakaiproject.entity.api.Reference)
	 */
	public String getEntityDescription(Reference ref) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sakaiproject.entity.api.EntityProducer#getEntityResourceProperties
	 * (org.sakaiproject.entity.api.Reference)
	 */
	public ResourceProperties getEntityResourceProperties(Reference ref) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sakaiproject.entity.api.EntityProducer#getEntity(org.sakaiproject
	 * .entity.api.Reference)
	 */
	public Entity getEntity(Reference ref) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sakaiproject.entity.api.EntityProducer#getEntityUrl(org.sakaiproject
	 * .entity.api.Reference)
	 */
	public String getEntityUrl(Reference ref) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.entity.api.EntityProducer#getEntityAuthzGroups(org.
	 * sakaiproject.entity.api.Reference, java.lang.String)
	 */
	public Collection<String> getEntityAuthzGroups(Reference ref, String userId) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sakaiproject.entity.api.EntityProducer#getHttpAccess()
	 */
	public HttpAccess getHttpAccess() {
		return null;
	}

	/**
	 * Dependency: EntityManager.
	 * 
	 * @param service
	 *            The EntityManager.
	 */
	public void setEntityManager(EntityManager service) {
		m_entityManager = service;
	}

	/**
	 * Dependency: SiteService.
	 * 
	 * @param service
	 *            The SiteService.
	 */
	public void setSiteService(SiteService service) {
		m_siteService = service;
	}

	/**
	 * Dependency: SecurityService.
	 * 
	 * @param service
	 *            The SecurityService.
	 */
	//public void setSecurityService(SecurityService securityService) {
	//	this.securityService = securityService;
		//super.setSecurityService(securityService);
	//}
	/**
	 * Dependency: ServerConfigurationService.
	 * 
	 * @param service
	 *            The ServerConfigurationService.
	 */
	public void setServerConfigurationService(ServerConfigurationService service) {
		m_serverConfigurationService = service;
	}
	
	/**
	 * Dependency: PreferencesService.
	 * 
	 * @param service
	 *            The PreferencesService.
	 */
	public void setPreferencesService(PreferencesService service) {
		m_preferencesService = service;
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init() {
		M_log.info("initialization...");
		
		// register as an entity producer
		m_entityManager.registerEntityProducer(this, SakaiGCalendarServiceStaticVariables.REFERENCE_ROOT);
		
		// register functions
		functionManager.registerFunction(SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW);
		functionManager.registerFunction(SakaiGCalendarServiceStaticVariables.SECURE_GCAL_VIEW_ALL);
		functionManager.registerFunction(SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT);
	}

	/**
	 * destroy.
	 */
	public void destroy() {
		M_log.warn("destroy...");
	}	
		
	/**
	 * Persist the Google Calendar id to the site properties.
	 * @param Sakai site
	 * @param Google Calendar Id
	 */
	private void addGcalendarIdToSite(Site site, String calendarId){
		if ( calendarId != null) {
			// add gcalid to the Sakai site property
			site.getPropertiesEdit().addProperty(SakaiGCalendarServiceStaticVariables.GCALID, calendarId);

			try {
				// save the site
				m_siteService.save(site);
			} catch (IdUnusedException e) {
				M_log.error("addGcalendarIdToSite: " + e.getMessage());
			} catch (Exception e) {
				M_log.error("addGcalendarIdToSite: " + e.getMessage());
			}
		}
	}
	@Override
	public String getContext() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public boolean getExportEnabled() {
		// This method is not supported.
		return false;
	}
	@Override
	public void setExportEnabled(boolean enable) {
		// This method is not supported.
		
	}
	@Override
	public Time getModified() {
		// This method is not supported.
		return null;
	}
	@Override
	public void setModified() {
		// This method is not supported.
		
	}
	@Override
	public boolean allowGetEvents() {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public boolean allowGetEvent(String eventId) {
		// TODO Auto-generated method stub
		return false;
	}
	/**
	 * Retrieves a list of events from a calendar based on a date range.
	 * The filter parameter is not used.
	 */
	@Override
	public List<CalendarEvent> getEvents(TimeRange range, Filter filter)
			throws PermissionException {
		
		Site site = getSite();
		// Retrieve gcalendar id from site properties
		String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);	
		if (gcalid == null){
			M_log.error("Calendar Id not found on site properties. Cannot retrieve events!!!");
			return new ArrayList<CalendarEvent>();
		}
		
		Events calendarEvents = null;
		
		// Map the Calendar range date values to values that can be used in the
		// Google API call.
		Time startTime = range.firstTime();
		Time endTime = range.lastTime();
		
		Date startDate = new Date(startTime.getTime());
		Date endDate = new Date(endTime.getTime());
		
		DateTime start = new DateTime(startDate);
		DateTime end = new DateTime(endDate);
		
		// Create client object to interact with Google API
		Calendar client = getGoogleClient(site.getCreatedBy().getEmail());

		// Call Google API
		try {
			calendarEvents = client.events().list(gcalid).setTimeMin(start).setTimeMax(end).execute();
			M_log.debug("Found " + calendarEvents.getItems().size() + " events for Google Calendar");
		} catch (IOException e) {
			M_log.error("Failed to query Google calendar for events." + e.getMessage());
		}

		// Add Google calendar events to list
		List<CalendarEvent> calendarEventList = new ArrayList<CalendarEvent>();
		
		for (Event evt : calendarEvents.getItems()){
			calendarEventList.add(createEventFromGoogleCalendarEvent(evt));			
		}
		
		return calendarEventList;
	}
	/**
	 * Retrieve a specific event from the Google Calendar.
	 */
	@Override
	public CalendarEvent getEvent(String eventId) throws IdUnusedException,
			PermissionException {
		// Retrieve Google event from Google calendar
		Event gEvent = getGoogleCalendarEvent(eventId);
		// Convert the Google event to a calendar event
		CalendarEvent calEvent = (CalendarEvent)createEventFromGoogleCalendarEvent(gEvent);
		return calEvent;
	}
	
	@Override
	public String getEventFields() {
		M_log.warn("This method is not implemented");
		return null;
	}
	
	/**
	 * Checks if current user has permission to add an event to the Google calendar.
	 */
	@Override
	public boolean allowAddEvent() {
		String currentUserId = getCurrentUserId();
		
		String siteId = ToolManager.getCurrentPlacement().getContext();
		String siteServiceString = org.sakaiproject.site.cover.SiteService.siteReference(siteId);
		if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, siteServiceString)){
			return true;
		}
		else{
			return false;
		}
	}
	
	@Override
	public boolean allowAddCalendarEvent() {
		String currentUserId = getCurrentUserId();
		
		String siteId = ToolManager.getCurrentPlacement().getContext();
		String siteServiceString = org.sakaiproject.site.cover.SiteService.siteReference(siteId);
		if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, siteServiceString)){
			return true;
		}
		else{
			return false;
		}
	}
	/**
	 * Adds an event to the Google Calendar.
	 */
	@Override
	public CalendarEvent addEvent(TimeRange range, String displayName,
			String description, String type, String location,
			EventAccess access, Collection groups, List attachments)
			throws PermissionException {
		
		CalendarEvent calEvent = null;
		// Verify user has permission to add an event.
		if (allowAddEvent()){
			Site site = getSite();
			// Retrieve Google calendar id from site properties
			String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);	
			if (gcalid == null){
				M_log.error("Calendar Id not found on site properties. Cannot add event!!!");
				return null;
			}
			// Create client object to interact with Google api
			// We use the site's creator e-mail because the site's Google calendar 
			// was created under this account.
			Calendar client = getGoogleClient(site.getCreatedBy().getEmail());
			
			// Create a Google event object with the event details
			Event event = createGoogleCalendarEvent(range, displayName, description, type, location, access, groups, attachments);
			
			// Call Google API to add the event to the calendar
			Event createdEvent = null;
			try {
				createdEvent = client.events().insert(gcalid, event).execute();
				
				M_log.debug("Google Calendar Event Created:  " + createdEvent.getId());
			} catch (IOException e) {
				M_log.error("Error adding event to Google Calendar " + e.getMessage());
			}
			// Map the values from the Google Calendar event to a Calendar Event
			calEvent = createEventFromGoogleCalendarEvent(createdEvent);
			return calEvent;
		}
		else{
			String currentUserId = getCurrentUserId();
			// There is no reference id to pass to the PermissionException constructor so we pass in a "".
			throw new PermissionException(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, "");
		}
	}
	
	/**
	 * Adds an event to the Google Calendar.
	 */
	@Override
	public CalendarEvent addEvent(TimeRange range, String displayName,
			String description, String type, String location, List attachments)
			throws PermissionException {
		CalendarEvent calEvent = addEvent(range,displayName,description,type,location,attachments);
		return calEvent;
	}

	/**
	 * This method is not supported in Google Calendar.
	 */
	@Override
	public CalendarEventEdit addEvent() throws PermissionException {
		M_log.warn("This method is not supported in Google Calendar.");
		throw new PermissionException(getCurrentUserId(), SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, "");
	}

	/**
	 * Checks if current user has permission to modify an event on the Google calendar.
	 */
	@Override
	public boolean allowEditEvent(String eventId) {
		String currentUserId = getCurrentUserId();
		
		String siteId = ToolManager.getCurrentPlacement().getContext();
		String siteServiceString = org.sakaiproject.site.cover.SiteService.siteReference(siteId);
		if ( securityService.unlock(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, siteServiceString)){
			return true;
		}
		else{
			return false;
		}
	}
	
	// Retrieves event for editing. 
	// I don't think the parameter editType is applicable to this implementation.
	@Override
	public CalendarEventEdit getEditEvent(String eventId, String editType)
			throws IdUnusedException, PermissionException, InUseException {

		// Retrieve Google event from Google calendar
		Event gEvent = getGoogleCalendarEvent(eventId);
		// Convert the Google event to an editable calendar event
		CalendarEventEdit calEvent = createEventFromGoogleCalendarEvent(gEvent);
		return calEvent;
	}
	
	@Override
	public void commitEvent(CalendarEventEdit edit, int intention) {
		// There is nothing to be done here.
	}
	
	@Override
	public void commitEvent(CalendarEventEdit edit) {
		// There is nothing to be done here.
	}
	
	@Override
	public void cancelEvent(CalendarEventEdit edit) {
		// There is nothing to be done here.
	}
	/**
	 * This method is not supported.
	 */
	@Override
	public CalendarEventEdit mergeEvent(Element el) throws PermissionException,
			IdUsedException {
		M_log.info("This functionality is not supported.");
		return null;
	}
	
	/**
	 * Checks permission to remove an event from the Google calendar.
	 */
	@Override
	public boolean allowRemoveEvent(CalendarEvent event) {
		
		return allowEditEvent(event.getId());
	}
	
	//TODO: Implement logic to handle intention.
	@Override
	public void removeEvent(CalendarEventEdit edit, int intention)
			throws PermissionException {
		// Check user has permission to remove event from the Google calendar
		if(allowRemoveEvent(edit)){
			removeEvent(edit);
		}
		else{
			String currentUserId = getCurrentUserId();
			// Using the GCal event id as the last parameter of the PermissionException constructor.
			throw new PermissionException(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, edit.getId());
		}
	}

	/**
	 * Removes an event from the Google calendar.
	 */
	@Override
	public void removeEvent(CalendarEventEdit edit) throws PermissionException {
		// Check user has permission to remove event from the Google calendar
		if (allowRemoveEvent(edit)){
			Site site = getSite();
			// Retrieve Google calendar id from site properties
			String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);	
			if (gcalid == null){
				M_log.error("Calendar Id not found on site properties. Cannot delete event!!!");
				return;
			}
			// Create client object to interact with Google API
			Calendar client = getGoogleClient(site.getCreatedBy().getEmail());
			
			// Call Google API
			try {
				client.events().delete(gcalid, edit.getId()).execute();
				M_log.debug("Google Calendar Event Successfully Deleted..." + edit.getId());
			} catch (IOException e) {
				M_log.error("Error deleting event from Google Calendar. " + e.getMessage());
			}
		}
		else{
			String currentUserId = getCurrentUserId();
			// Using the GCal event id as the last parameter of the PermissionException constructor.
			throw new PermissionException(currentUserId, SakaiGCalendarServiceStaticVariables.SECURE_GCAL_EDIT, edit.getId());
		}
	}
	
	@Override
	public Collection getGroupsAllowAddEvent() {
		// Groups are not supported
		return null;
	}
	@Override
	public Collection getGroupsAllowGetEvent() {
		// Groups are not supported
		return null;
	}
	@Override
	public Collection getGroupsAllowRemoveEvent(boolean own) {
		// Groups are not supported
		return null;
	}
	/**
	 * Returns the id of the Google calendar.
	 */
	@Override
	public String getId() {
		Site site = getSite();
		// Retrieve Google calendar id from site properties
		String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);	
		return gcalid;
	}
	@Override
	public ResourceProperties getProperties() {
		// This method is not supported.
		return null;
	}
	@Override
	public String getReference() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String getReference(String arg0) {
		// This method is not supported.
		return null;
	}
	@Override
	public String getUrl() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String getUrl(String arg0) {
		// This method is not supported.
		return null;
	}
	@Override
	public Element toXml(Document arg0, Stack<Element> arg1) {
		// This method is not supported.
		return null;
	}
	
	// Retrieve site information
	private Site getSite(){
		String siteId = ToolManager.getCurrentPlacement().getContext();
		Site site = null;
		try {
			site = this.m_siteService.getSite(siteId);
		} catch (IdUnusedException e) {
			M_log.error("Error retrieving site information: " + e.getMessage());
		}
		return site;
	}
	
	// Creates a Google calendar event with the values provided.
	// The return Event is used to call the Google API
	// The type parameter is ignored and access, groups and attachments are not supported.
	private Event createGoogleCalendarEvent(TimeRange range, String displayName,
									String description, String type, String location,
									EventAccess access, Collection groups, List attachments) {
		// Log warnings if unsupported parameters are provided.
		if (access != null){
			M_log.warn("access parameter is not supported");
		}
		if (groups != null){
			M_log.warn("groups parameter is not supported");
		}
		if (attachments != null){
			M_log.warn("attachments parameter is not supported");
		}
		Event event = new Event();

		event.setSummary(displayName);
		event.setDescription(description);
		event.setLocation(location);
		
		// Handle event time details
		Time startTime = range.firstTime();
		Date startDate = new Date(startTime.getTime());
		Time endTime = range.lastTime();
		Date endDate = new Date(endTime.getTime());
		DateTime start = new DateTime(startDate);
		event.setStart(new EventDateTime().setDateTime(start));
		DateTime end = new DateTime(endDate);
		event.setEnd(new EventDateTime().setDateTime(end));
		
		return event;
	}
	
	/**
	 * Maps some of the Google event fields to a Calendar Event.
	 * @param event
	 * @return
	 */
	private CalendarEventEdit createEventFromGoogleCalendarEvent(Event event){
		SakaiGCalendarEventImpl calEvent = new SakaiGCalendarEventImpl();
		calEvent.setId(event.getId());
		calEvent.setDescription(event.getDescription());
		calEvent.setDisplayName(event.getSummary());
		calEvent.setLocation(event.getLocation());
		// Handle event start and end times.
		try{
			calEvent.setRange(TimeService.newTimeRange(TimeService.newTime(event.getStart().getDateTime().getValue()), TimeService.newTime(event.getEnd().getDateTime().getValue())));
		}
		catch (NullPointerException ex){
			M_log.error("Unexpected error with Google Calendar event. " + ex.getMessage());
		}
		
		return calEvent;
	}
	
	/**
	 * Retrieves an event from the Google calendar by calling the Google API
	 * @param eventId
	 * @return a Google calendar event
	 */
	private Event getGoogleCalendarEvent(String eventId){
		if (eventId == null){ // Should have an id before calling the API.
			M_log.error("Failed to get Google calendar event. EventId cannot be null!!!");
			return null;
		}
		Site site = getSite();
		// Retrieve Google calendar id from site properties
		String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);	
		if (gcalid == null){
			M_log.error("Calendar Id not found on site properties. Cannot add event!!!");
			return null;
		}
		// Create client object to interact with Google API
		Calendar client = getGoogleClient(site.getCreatedBy().getEmail());

		Event event = null;
		// Call Google API
		try {
			event = client.events().get(gcalid, eventId).execute();
		} catch (IOException e) {
			M_log.error("Error retrieving event from Google Calendar. " + e.getMessage());
		}
		return event;
	}
	@Override
	public List getCalendars() {
		// This method is not supported.
		return null;
	}
	@Override
	public CalendarEdit addCalendar(String ref) throws IdUsedException,
			IdInvalidException, PermissionException {
		// This method is not supported.
		return null;
	}
	@Override
	public boolean allowGetCalendar(String ref) {
		//TODO Auto-generated method stub
		return false;
	}
	@Override
	public org.sakaiproject.calendar.api.Calendar getCalendar(String ref)
			throws IdUnusedException, PermissionException {
		org.sakaiproject.calendar.api.Calendar gCalService = (org.sakaiproject.calendar.api.Calendar) org.sakaiproject.gcalendar.cover.SakaiGCalendarService.getInstance();
		return gCalService;
	}
	@Override
	public boolean allowImportCalendar(String ref) {
		// This method is not supported.
		return false;
	}
	@Override
	public boolean allowSubscribeCalendar(String ref) {
		// This method is not supported.
		return false;
	}
	@Override
	public boolean allowEditCalendar(String ref) {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public boolean allowMergeCalendar(String ref) {
		// This method is not supported.
		return false;
	}
	@Override
	public boolean allowSubscribeThisCalendar(String arg0) {
		// This method is not supported.
		return false;
	}
	@Override
	public CalendarEdit editCalendar(String ref) throws IdUnusedException,
			PermissionException, InUseException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void commitCalendar(CalendarEdit edit) {
		// This method is not supported.
		
	}
	@Override
	public void removeCalendar(CalendarEdit edit) throws PermissionException {
		// This method is not supported.
		
	}
	@Override
	public String calendarICalReference(Reference ref) {
		// This method is not supported.
		return null;
	}
	@Override
	public String calendarOpaqueUrlReference(Reference arg0) {
		// This method is not supported.
		return null;
	}
	@Override
	public void cancelCalendar(CalendarEdit edit) {
		// This method is not supported.
		
	}
	// Return the Google calendar id
	@Override
	public String calendarReference(String context, String id) {
		String gcalId = getId(); // Just call the existing getId() method.
		return gcalId;
	}
	@Override
	public String calendarPdfReference(String context, String id,
			int scheduleType, String timeRangeString, String userName,
			TimeRange dailyTimeRange) {
		// This method is not supported.
		return null;
	}
	@Override
	public String calendarSubscriptionReference(String context, String id) {
		// This method is not supported.
		return null;
	}
	@Override
	public boolean getExportEnabled(String ref) {
		// This method is not supported.
		return false;
	}
	@Override
	public void setExportEnabled(String ref, boolean enable) {
		// This method is not supported.
		
	}
	@Override
	public String eventReference(String context, String calendarId, String id) {
		// This method is not supported.
		return null;
	}
	@Override
	public String eventSubscriptionReference(String context, String calendarId,
			String id) {
		// This method is not supported.
		return null;
	}
	@Override
	public CalendarEventVector getEvents(List references, TimeRange range) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval,
			int count) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval,
			Time until) {
		// TODO Auto-generated method stub
		return null;
	}
}
