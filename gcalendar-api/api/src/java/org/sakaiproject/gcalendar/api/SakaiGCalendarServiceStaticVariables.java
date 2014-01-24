package org.sakaiproject.gcalendar.api;

import org.sakaiproject.entity.api.Entity;

public class SakaiGCalendarServiceStaticVariables {
	/** This string starts the references to resources in this service. */
	public static final String GCALENDAR = "gcalendar";
	public static final String GCALID = "gcalid";
	public static final String REFERENCE_ROOT = Entity.SEPARATOR + GCALENDAR;
	
	/** These strings are for mapping Sakai Permissions to Google Calendar Permissions */
	public static final String SECURE_GCAL_VIEW = "gcal.view";
	public static final String SECURE_GCAL_VIEW_ALL = "gcal.view.all";
	public static final String SECURE_GCAL_EDIT = "gcal.edit";
	
	public static final String RULE_ROLE_READER = "reader"; // Provides read access to the calendar. Private events will appear to users with reader access, but event details will be hidden.
	public static final String RULE_ROLE_OWNER = "owner"; // Provides ownership of the calendar. This role has all of the permissions of the writer role with the additional ability to see and manipulate ACLs.
	public static final String RULE_ROLE_WRITER = "writer"; // Provides read and write access to the calendar. Private events will appear to users with writer access, and event details will be visible.
	public static final String RULE_ROLE_FREEBUSYREADER = "freeBusyReader"; // Provides read access to free/busy information.
	public static final String RULE_ROLE_NONE = "none"; // Provides no access
	
	public static final String RULE_SCOPE_TYPE_USER = "user"; //  Limits the scope to a single user.
	
	public static final String ASSIGNMENT_DUEDATE_GCAL_EVENT_ID = "assignment_duedate_gcal_event_id";
}
