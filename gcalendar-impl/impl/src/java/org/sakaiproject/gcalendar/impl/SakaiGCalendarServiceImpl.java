/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/umich/gcalendar/gcalendar-api/api/src/java/org/sakaiproject/gcalendar/cover/SakaiGCalendarServiceImpl.java $
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.entity.api.ContextObserver;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.gcalendar.api.SakaiGCalendarService;
import org.sakaiproject.google.impl.SakaiGoogleAuthServiceImpl;
import org.sakaiproject.gcalendar.api.SakaiGCalendarServiceStaticVariables;

import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesEdit;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.cover.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Lists;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Acl;
import com.google.api.services.calendar.model.AclRule;
import com.google.api.services.calendar.model.AclRule.Scope;
import com.google.api.services.calendar.model.CalendarListEntry;

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
		if (toolPlacement) {
			String emailAddress = getUserEmailAddress();
			boolean okToCreate = okToCreateGoogleCalendar(context, emailAddress);
			if (okToCreate) {
				enableGCalendar(context, emailAddress);
			}
		}
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
	 * Is it okay to create the google calendar?
	 * 
	 * @param context site identifier
	 * @return true if it is okay to create the google calendar
	 * 
	 */
	private boolean okToCreateGoogleCalendar(String context, String emailAddress) {
		
		Site site = null;
		String siteTitle = null;
		String gcalid = null;
		
		// get site and site title. We can not get here unless the site has been published.
		try {
			site = m_siteService.getSite(context);
			siteTitle = site.getTitle();
		} catch (IdUnusedException e) {
			M_log.warn(e.getMessage());
			return false; // site should not be created with out this information
		}

		// if the Google Calendar has already been created and the Google Calendar Id has already been saved in the site property
		// return true
		// else, create the calendar in google.
		if (site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID) != null) { 
			return false;
		} else {		
			GoogleCredential credential = getGoogleCredential(emailAddress);
			if (credential == null) {
				M_log.warn(this + " okToCreateGoogleCalendar - Not Authorized");
				return false; // user not authorized - do not create calendar
			}
			gcalid = getGoogleCalendarID(siteTitle, credential);
			
			if (null != gcalid)
				return false;
		}
		// return true - it is okay to create the google calendar
		return true;
	}

	/**
	 * Save the Google Calendar Id as a site Property ("gcalid")
	 * 
	 * @param Site current site
	 * @return String Google calendar access token
	 * 
	 */
	public String saveGoogleCalendarInfo(Site site) {

		String siteTitle = site.getTitle();
		String gcalid = null;
		String emailAddress = getUserEmailAddress();

		GoogleCredential credential = getGoogleCredential(emailAddress);
		if (credential == null) {
			return null; // user not authorized - do not return access token
		}
		
		gcalid = getGoogleCalendarID(siteTitle, credential);

		if ( null != gcalid ) {
			// add gcalid as the Sakai site property
			site.getPropertiesEdit().addProperty(SakaiGCalendarServiceStaticVariables.GCALID, gcalid);

			try {
				// save the site
				m_siteService.save(site);
			} catch (IdUnusedException e) {
				M_log.error("saveGoogleCalendarInfo: " + e.getMessage());
				return null;
			} catch (Exception e) {
				M_log.error("saveGoogleCalendarInfo: " + e.getMessage());
				return null;
			}
			return credential.getAccessToken();
		}
		return null; // Google calendar does not exist
	}

	/**
	 * Get the Google Calendar ID from Google
	 * 
	 * @param String Site Title
	 * @param String Email ID
	 * @param String Google Credentials
	 * @return String Google Calendar ID or null, if not found
	 * 
	 */
	private String getGoogleCalendarID(String siteTitle, GoogleCredential credential) {

		String gcalid = null;
		Calendar client;
		
		try {
			
			client = getGoogleClient( credential );

			com.google.api.services.calendar.model.CalendarList feed = client.calendarList().list().execute();
			String summary = null;

			if (feed.getItems() != null) {
				List<CalendarListEntry> calendarList = feed.getItems();
				for (CalendarListEntry calendarEntry : calendarList) {
					// Note: this is an issue if there is more than one Google Calendar with the same name
					// created by the same user. This will find the first one returned.
					summary = (String) calendarEntry.get("summary");
					if (siteTitle.equals(summary)) {
						gcalid = calendarEntry.getId();
						return gcalid;
					}
				}
			} else {
				return null;
			}

		} catch (IOException e) {
			M_log.error("getGoogleCalendarInfo: " + e.getMessage());
			return null;
		} catch (Exception e) {
			M_log.error("getGoogleCalendarInfo: " + e.getMessage());
			return null;
		}
		
		return null;
	}
	
	/**
	 * Create a calendar for the site.
	 * 
	 * @param context site identifier
	 * 
	 */
	private void enableGCalendar(String context, String emailAddress ) {

		com.google.api.services.calendar.model.Calendar siteGCalendar = null;
		
		Calendar client;
		
		GoogleCredential credential = getGoogleCredential(emailAddress);
		if (credential == null) {
			return; // user not authorized
		}
		
		client = getGoogleClient( credential );

		// get site
		Site site = null;
		try {
			site = m_siteService.getSite(context);
			siteGCalendar = createGoogleCalendar(site, client);
		} catch (IdUnusedException e) {
			M_log.error("enableGCalendar: " + e.getMessage());
		} catch (IOException e) {
			M_log.error("enableGCalendar: " + e.getMessage());
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

	private com.google.api.services.calendar.model.Calendar createGoogleCalendar(Site site, Calendar client) throws IOException {

		String gcalID;
		
		com.google.api.services.calendar.model.Calendar calendar = new com.google.api.services.calendar.model.Calendar();

		calendar.setSummary(site.getTitle());
		calendar.setTimeZone( TimeService.getLocalTimeZone().getID() );
		calendar.setKind("calendar#calendar"); // this string is a special string and is defined by Google. There are other kind strings.

		com.google.api.services.calendar.model.Calendar createdCalendar = client.calendars().insert(calendar).execute();
		
		if ( createdCalendar == null )
			return null;
		
		// Set permissions on the calendar for global sharing
		// First retrieve the access rule from the API.
		// Insert a new one if it exists (updating did not work well)
		gcalID = createdCalendar.getId();
		
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
			String emailAddress = getUserEmailAddress();
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
    	
    	String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);
    	User currentUser = UserDirectoryService.getCurrentUser();
    	String currentUserId = currentUser.getId();
    	
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
		
}
