/**********************************************************************************
 * $URL: https://source.sakaiproject.org/contrib/umich/gcalendar/gcalendar-impl/api/src/java/org/sakaiproject/gcalendar/impl/SakaiGCalendarEventImpl.java $
 * $Id: SakaiGCalendarEventImpl.java 82630 2013-02-07 14:15:50Z reggiejr@umich.edu $
 ***********************************************************************************
 *
 * Copyright (c) 2014 The Sakai Foundation
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEventEdit;
import org.sakaiproject.calendar.api.RecurrenceRule;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.time.api.TimeRange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SakaiGCalendarEventImpl implements CalendarEventEdit{
	
	private String id;
	private TimeRange range;
	private String description;
	private String displayName;
	private String location;
	private Map<String,String> fieldName = new HashMap<String,String>();
	private Collection groups;

	@Override
	public String getUrl() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getReference() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUrl(String rootProperty) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getReference(String rootProperty) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return id;
	}

	@Override
	public ResourceProperties getProperties() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Element toXml(Document doc, Stack<Element> stack) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int compareTo(Object arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<Reference> getAttachments() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TimeRange getRange() {
		return range;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getDescriptionFormatted() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getLocation() {
		return location;
	}

	@Override
	public String getField(String name) {
		return fieldName.get(name);
	}

	@Override
	public String getCalendarReference() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RecurrenceRule getRecurrenceRule() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RecurrenceRule getExclusionRule() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getCreator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isUserOwner() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getModifiedBy() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSiteName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection getGroups() {
		return groups;
	}

	@Override
	public Collection getGroupObjects() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupRangeForDisplay(Calendar calendar) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EventAccess getAccess() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void setDescription(String value){
		description = value;
	}

	public void setId(String value){
		id = value;
	}
	
	public void setRange(TimeRange value){
		range = value;
	}

	@Override
	public boolean isActiveEdit() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ResourcePropertiesEdit getPropertiesEdit() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addAttachment(Reference ref) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeAttachment(Reference ref) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void replaceAttachments(List<Reference> attachments) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void clearAttachments() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDisplayName(String name) {
		displayName = name;
	}

	@Override
	public void setDescriptionFormatted(String description) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setType(String type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setLocation(String location) {
		this.location = location;
	}

	@Override
	public void setField(String name, String value) {
		fieldName.put(name, value);
		
	}

	@Override
	public void setRecurrenceRule(RecurrenceRule rule) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setExclusionRule(RecurrenceRule rule) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setGroupAccess(Collection groups, boolean own)
			throws PermissionException {
		this.groups = groups;
		
	}

	@Override
	public void clearGroupAccess() throws PermissionException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setCreator() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setModifiedBy() {
		// TODO Auto-generated method stub
		
	}
}
