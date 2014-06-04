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

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.calendar.api.CalendarEdit;
import org.sakaiproject.calendar.api.CalendarEventVector;
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
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
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
import com.google.api.client.util.Lists;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.AclRule;
import com.google.api.services.calendar.model.AclRule.Scope;

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
	
	private MemoryService memoryService; // Used to create a cache to store GoogleCredentials
	public void setMemoryService(MemoryService memoryService) {
		this.memoryService = memoryService;
	}

	private Cache cache;  // Initialized in the init() method.
	private final String CACHE_NAME = "org.sakaiproject.gcalendar.credentials.cache";	

	/** Authorizes the service account to access user's protected data. 
	 	A null value will be returned if there is a problem */
	private GoogleCredential getGoogleCredential(String userId) {		
		
		try { 
			// get the service account e-mail address and service account private key from property file
			// this email account and key file are specific for your environment
			if (SERVICE_ACCOUNT_EMAIL == null) {
				SERVICE_ACCOUNT_EMAIL = m_serverConfigurationService.getString("google.service.account.email", "");
			}
			if (PRIVATE_KEY == null) {
				PRIVATE_KEY = m_serverConfigurationService.getString("google.private.key", "");
			}
			
			GoogleCredential credential = getCredentialFromCredentialCache(userId);
			return credential;

		} catch (Exception e) {
			// return null if an exception occurs while communicating with Google.
			M_log.error("Error creating a GoogleCredential object or requesting access token: " + e.getMessage());
			return null;
		}
	}
	
	// Check if there is a credential object in the cache for this user. If not in cache we create a new one.
	private GoogleCredential getCredentialFromCredentialCache(final String userId) throws IOException{
		GoogleCredential credential = (GoogleCredential)cache.get(userId);
		if (credential != null){
			M_log.debug("Fetching credential from cache for user: " + userId);
			return credential;
		}
		else{ // Need to create credential and create access token.
			credential = SakaiGoogleAuthServiceImpl.authorize(userId, SERVICE_ACCOUNT_EMAIL, PRIVATE_KEY, CalendarScopes.CALENDAR);
			if (credential != null){
				credential.refreshToken(); // Populates credential with access token
				addCredentialToCache(userId, credential);
			}
			return credential;
		}
	}
	
  	/**
  	 * Add user credential to the cache
  	 * @param k	key
  	 * @param v value
  	 */
  	private void addCredentialToCache(String k, GoogleCredential v){
		cache.put(k, v);
		M_log.debug("Added entry to cache, key: " + k +", value: " + v);
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

		// Nothing done here.
		// We tried using this method to store the Google calendar id in the site properties.
		// But we found that we could not invoke "m_siteService.save(site)" to save the property
		// as the site save operation was already in progress as this method is called.
	}

	@Override
	public void contextUpdated(String context, boolean toolPlacement) {

		// Nothing done here.
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
		
		GoogleCredential credential = getGoogleCredential(emailID);
		if (credential == null) {
			return null; // user not authorized
		}
		else{
			return credential.getAccessToken();
		}
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
	
	// Returns the Google calendar tool id string.
	public String getToolId(){
		return "sakai.gcalendar";		
	}
	// Returns true if the Google calendar has been created in Google. Currently the GCalendar tool can be
	// added to a site but the calendar won't be created in Google until the site owner actually uses the tool.
	public boolean isCalendarToolInitialized(String siteId){
		Site site = getSite(siteId);
		if (getGoogleCalendarID(site) != null){
			return true;
		}
		else{
			return false;
		}
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
		
  		//Setup cache. This will create a cache using the default configuration used in the memory service.
  		cache = memoryService.newCache(CACHE_NAME);
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
	
	// Retrieve site given a site id.
	private Site getSite(String siteId){
		Site site = null;
		try {
			site = this.m_siteService.getSite(siteId);
		} catch (IdUnusedException e) {
			M_log.error("Error retrieving site information: " + e.getMessage());
		}
		return site;
	}
	
	@Override
	public CalendarEdit addCalendar(String ref) throws IdUsedException,
			IdInvalidException, PermissionException {
		// This method is not supported.
		return null;
	}
	@Override
	public boolean allowGetCalendar(String ref) {
		// This method is not supported.
		return false;
	}
	// Create a Calendar instance with a reference to the Google client so it can invoke the Google API's
	@Override
	public org.sakaiproject.calendar.api.Calendar getCalendar(String ref)
			throws IdUnusedException, PermissionException {
		Site site = null;
		if (ref == null){
			site = getSite();
		}
		else{
			site = getSite(ref);
		}
		// We use the e-mail id of the site creator since the Google calendar is created under this id.
		Calendar googleClient = getGoogleClient(site.getCreatedBy().getEmail());
		return new SakaiGCalendarImpl(googleClient);
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
		// This method is not supported.
		return false;
	}
	@Override
	public boolean allowMergeCalendar(String ref) {
		// This method is not supported.
		return false;
	}
	public boolean allowSubscribeThisCalendar(String arg0) {
		// This method is not supported.
		return false;
	}
	@Override
	public CalendarEdit editCalendar(String ref) throws IdUnusedException,
			PermissionException, InUseException {
		// This method is not supported.
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
		Site site = getSite();
		// Retrieve Google calendar id from site properties
		String gcalid = site.getProperties().getProperty(SakaiGCalendarServiceStaticVariables.GCALID);	
		return gcalid;
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
		// This method is not supported.
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency) {
		// This method is not supported.
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval) {
		// This method is not supported.
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval,
			int count) {
		// This method is not supported.
		return null;
	}
	@Override
	public RecurrenceRule newRecurrence(String frequency, int interval,
			Time until) {
		// This method is not supported.
		return null;
	}
}
