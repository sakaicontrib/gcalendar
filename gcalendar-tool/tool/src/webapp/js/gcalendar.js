/*
 getGoogleCalendar
 Notes: The Math.floor((Math.random()*100)+1) in the ajax calls are to make sure that Internet Explorer 8 and 9
 do not return the cached values for similar calls. In theory, using cached:false should take care of this issue,
 but, it did not in some of my earlier testing so I left it in.
 */

//The default colors are set in library/src/webapp/fullcalendar/fullcalendar.css
var WHITE = '#FFFFFF';
var EVENT_BACKGROUND_COLOR = '#3366CC'; // a light-ish blue to be compatible with the previous version

var eventArray = []; // move globally

var editable;
var createEvents;

var busy = "busy";

var eventTimeValueArray = ["00:00", "00:30", "01:00", "01:30", "02:00", "02:30", "03:00", "03:30", "04:00", "04:30", "05:00", "05:30", 
                           "06:00", "06:30", "07:00", "07:30", "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", 
                           "12:00", "12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00", "17:30", 
                           "18:00", "18:30", "19:00", "19:30", "20:00", "20:30", "21:00", "21:30", "22:00", "22:30", "23:00", "23:30"];
                                
var eventTimeTextArray = ["12:00am", "12:30am", "1:00am", "1:30am", "2:00am", "2:30am", "3:00am", "3:30am", "4:00am", "4:30am", "5:00am", "5:30am", 
                          "6:00am", "6:30am", "7:00am", "7:30am", "8:00am", "8:30am", "9:00am", "9:30am", "10:00am", "10:30am", "11:00am", "11:30am", 
                          "12:00pm", "12:30pm", "1:00pm", "1:30pm", "2:00pm", "2:30pm", "3:00pm", "3:30pm", "4:00pm", "4:30pm", "5:00pm", "5:30pm", 
                          "6:00pm", "6:30pm", "7:00pm", "7:30pm", "8:00pm", "8:30pm", "9:00pm", "9:30pm", "10:00pm", "10:30pm", "11:00pm", "11:30pm"];

getGoogleCalendar = function(accesstoken, gcalid) {
	// viewDetailsAllowed is a String
	editable = true;
	if ( viewDetailsAllowed == "false")
		editable = false;
	
	// createEventsAllowed is a String
	createEvents = true;
	if ( createEventsAllowed == "false")
		createEvents = false;
	
	// gcalview is a String (permission gcal.view - show no details)
	viewbusyOnly = false;
	if ( gcalview == "false")
		viewbusyOnly = true;
	
    $('#calendar').fullCalendar({

        theme : true,       
        editable : editable, // allows or prohibits drag and drop (not event edit)
        header : {
            left : 'today prev,next',
            center : 'title',
            right : 'month,agendaWeek,agendaDay'
        },

        // get all the events in the given time range from google calendar
        events : function(start, end, callback) { 
        	refreshCalendarItems( start, end, callback );
        },
        
        eventBackgroundColor : EVENT_BACKGROUND_COLOR,
        
        eventTextColor : WHITE,
        
        // drop an existing event in full calendar (NOTE: does not work in IE9)
        eventDrop: function(event,dayDelta,minuteDelta,allDay,revertFunc) {

            var index = findFullCalendarEvent( event );
            var data2;
            var starttime;
            var endtime;
            
            if ( editable == false )
            	return;
            	
            if ( index >= 0 ) {
                var sequence = eventArray[index].sequence;
                
            	if (event.allDay) {
            		starttime = event.start.yyyymmdd();
            		endtime = event.start.yyyymmdd();
            		// need to update the sequence
            		data2 = "{'end': {'date': '" + endtime + "'},'start': {'date': '" + starttime + "'},'sequence':'" + sequence +"','summary': '" + event.title  + "'}";
            	} else {
            		starttime = event.start.yyyymmdd();
            		endtime = event.end.yyyymmdd();
            		
            		var eventStartTimeValue = event.start.toRFC3339().substring(11, 16);
                    var eventEndTimeValue = event.end.toRFC3339().substring(11, 16);
                    
            		starttime = starttime + "T" + eventStartTimeValue + ":00";
                    endtime = endtime + "T" + eventEndTimeValue + ":00";
            		data2 = "{'end': {'dateTime': '" + endtime + "', 'timeZone':'"+ userTimeZone +"'},'start': {'dateTime': '" + starttime + "', 'timeZone':'"+ userTimeZone +"'},'sequence':'" + sequence +"','summary': '" + event.title  + "'}";
            	}
            	var eventID = event.id;

            	// PUT https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events/{eventId} // from google cal docs
            	jQuery.ajax({
                    type : "PUT",
                    contentType : "application/json",
                    data : data2,
                    url : baseUrl + '/' + proxyName + '/calendar/v3/calendars/' + gcalid + '/events/' + eventID + '/?access_token=' + accesstoken + '&amp;' + Math.floor((Math.random()*100)+1),
                    dataType : "json",
                    async: true,
                    cache: false,

                    // if ajax call success
                    success : function(datain) {
                    	
                    },
                    
            		error : function(XMLHttpRequest, textStatus, errorThrown) {
                        alert( "Updating calendar event failed " + textStatus + " " + errorThrown );
                        revertFunc();
            		}
            	});
            } else {
            	alert( "Error - could not find the event");
            }
           
        },
        
        // resize event i.e. change the duration of an event that has time (NOTE: Does not work in IE9)
        eventResize: function(event,dayDelta,minuteDelta,revertFunc) {

            var index = findFullCalendarEvent( event );
            var data2;
            var starttime;
            var endtime;
            
            if ( editable == false )
            	return false;
            
            // if the event is a recurring event,
            if ( event.recurrence || event.recurringEventId ) {
            	alert( "Recurring events can be edited in the full version of Google Calendar");
            	// revert to the original timeframe
            	revertFunc();
            	// opens events in a popup window
                window.open(event.url, 'gcalevent', 'width=700, height=600');
                return false;
            }
            if ( index >= 0 ) {
                var sequence = eventArray[index].sequence;
                
            	if (event.allDay) {
            		starttime = event.start.yyyymmdd();
            		endtime = event.start.yyyymmdd();
            		// Changing an event requires a new sequence (done in findFullCalendarEvent)
            		data2 = "{'end': {'date': '" + endtime + "'},'start': {'date': '" + starttime + "'},'sequence':'" + sequence +"','summary': '" + event.title  + "'}";
            	} else {
            		starttime = event.start.yyyymmdd();
            		endtime = event.end.yyyymmdd();
            		
            		var eventStartTimeValue = event.start.toRFC3339().substring(11, 16);
                    var eventEndTimeValue = event.end.toRFC3339().substring(11, 16);
                    
            		starttime = starttime + "T" + eventStartTimeValue + ":00";
                    endtime = endtime + "T" + eventEndTimeValue + ":00";
            		data2 = "{'end': {'dateTime': '" + endtime + "', 'timeZone':'"+ userTimeZone +"'},'start': {'dateTime': '" + starttime + "', 'timeZone':'"+ userTimeZone +"'},'sequence':'" + sequence +"','summary': '" + event.title  + "'}";
            	}
            	var eventID = event.id;

            	// PUT https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events/{eventId} // from Google cal docs
            	jQuery.ajax({
                    type : "PUT",
                    contentType : "application/json",
                    data : data2,
                    url : baseUrl + '/' + proxyName + '/calendar/v3/calendars/' + gcalid + '/events/' + eventID + '/?access_token=' + accesstoken + '&amp;' + Math.floor((Math.random()*100)+1),
                    dataType : "json",
                    async: false,
                    cache: false,

                    success : function(datain) {
                    	//alert( "event updated" );
                    },
                    
            		error : function(XMLHttpRequest, textStatus, errorThrown) {
                        alert( "Updating calendar event failed " + textStatus + " " + errorThrown );
            		}
            	});
            } else {
            	alert( "Error - could not find the event");
            }
        },
   
        // click the existing event in google calendar
        eventClick : function(event) {
        	if ( editable == false )
            	return false;
        	
            // opens events in a popup window
            window.open(event.url, 'gcalevent', 'width=700, height=600');
            return false;
        },

        // create an event in a day
        dayClick : function(date, allDay, jsEvent, view) {
        	
        	if ( editable == false || createEvents == false )
            	return false;
        	
            $(function() {               
                var $dialogDiv = $('<div id="newEvent"></div>').appendTo(document.body);
                $dialogDiv.html($('.newEventTemplate').html());               
                
                // need to remove the event so there are not two fields with the same name on the page (for accessibility)
                var temp = $('.newEventTemplate').html();
                $('.newEventTemplate').html("");

                $dialogDiv.dialog({
                    width : 580,
                    height : 400,
                    position : "center",
                    autoOpen : true,
                    modal : true,
                    draggable : true,
                    resizable : false,
                    title : "New Event",
                    close : function(event, ui) {
                        // Clean up: remove dialog, its children, and the events from DOM
                        $('#newEvent').remove(); 
                        // when we are all done, put the template back
                        $('.newEventTemplate').html(temp);
                    }
                });
                
                $("#newEvent .newEventDate").text(date.yyyymmdd());          
               
                if (allDay) {
                    $("#newEvent .newEventAllDay").attr("checked", true);
                    $("#newEvent .newEventTimeClass").hide();                 
                } else {
                    $("#newEvent .newEventAllDay").attr("checked", false);
                    $("#newEvent .newEventTimeClass").show();
                                     
                    var eventStartTimeValue = date.toRFC3339().substring(11, 16);
                    var eventEndTimeValue = date.addHours(1).toRFC3339().substring(11, 16);
                    
                    var eventStartTimeText = getEventTimeText(eventStartTimeValue);
                    var eventEndTimeText = getEventTimeText(eventEndTimeValue);
                    
                    // Initialize the timepickers.
                    $('#newEventStartTime').timepicker({'minTime': eventStartTimeText});
                    $('#newEventEndTime').timepicker({'minTime': eventStartTimeText, 'showDuration': true});
                    
                    // Update the entry fields on the page with the time values.
                    $('#newEventStartTime').val(eventStartTimeText);
                    $('#newEventEndTime').val(eventEndTimeText);
                }
                
                // Event handler for changes in start time; need to update the end time picker
                $("#newEventStartTime").on("change", function(){
                	var startTimeValue = $(this).val(); 
                	$('#newEventEndTime').timepicker('option', {'minTime' : startTimeValue, 'showDuration': true}); 
                });
                
                // event handler for checkbox
                $("#newEvent .newEventAllDay").live("click", function(e) {                                                       
                    if ($("#newEvent .newEventAllDay:checked").length) {
                        $("#newEvent .newEventTimeClass").hide();
                    } else {
                        $("#newEvent .newEventTimeClass").show();
                        // Initialize the time pickers.
                        $('#newEventStartTime').timepicker({'scrollDefaultNow':true}); 
                        $('#newEventEndTime').timepicker({'scrollDefaultNow':true}); 
                    }                    
                });
                
                // event handler for create event button
                $("#newEvent .newEventSave").live("click", function(e) {
                    var eventSummary = $("#newEvent .newEventTitle").val();
                    var tmpEventStartTimeValue = $("#newEventStartTime").val();
                    var tmpEventEndTimeValue = $("#newEventEndTime").val(); 
                    
                    allDay = false;
                    if ($("#newEvent .newEventAllDay:checked").length) {
                    	allDay = true;
                    }
                    
                    if ( !allDay ) {
                    	var eventEndTimeValue = getEventTimeValue(tmpEventEndTimeValue );    
	                    if ( eventEndTimeValue < 0 ) {
	                    	$("#newEvent .messageValidation").remove();
	                        $("#newEvent").prepend("<p class=\"messageValidation\" style=\"height:20px\" >Sorry, unable to create the event. Invalid End Time format.</p>");
	                        return;
	                    }
	                    var eventStartTimeValue = getEventTimeValue(tmpEventStartTimeValue );
	                    if ( eventStartTimeValue < 0 ) {
	                    	$("#newEvent .messageValidation").remove();
	                        $("#newEvent").prepend("<p class=\"messageValidation\" style=\"height:20px\" >Sorry, unable to create the event. Invalid Start Time format.</p>");
	                        return;
	                    }
                    }
                    // make sure end is after start
                    if ( ( eventEndTimeValue < eventStartTimeValue ) && ( !allDay ) ) {
                    	$("#newEvent .messageValidation").remove();
                        $("#newEvent").prepend("<p class=\"messageValidation\" style=\"height:20px\" >Start Time must be before End Time.</p>");
                    } else {
	                    var tempString = eventSummary.replace(/^\s+|\s+$/g, ""); // trim
	                    var eventSummary1 = tempString.replace(/'/g, "\\'"); // escape '

	                    if (eventSummary1 == null || eventSummary1 === "") {
	                        eventSummary1 = "No title";
	                    }
	                    processSave(eventSummary1, eventStartTimeValue, eventEndTimeValue, userTimeZone);
                    }
                });
            });

            // function to save the created event making use of the timezone
            processSave = function(eventSummaryValue, eventStartTimeValue, eventEndTimeValue, userTimeZone) {	   
                var data2;
                var starttime;
                var endtime;
                var json;
                var jsonContext;
                
                // if it is all day event
            	if ($("#newEvent .newEventAllDay:checked").length) {
            		starttime = date.yyyymmdd();
            		endtime = date.addHours(24).yyyymmdd();		
            		data2 = "{'end': {'date': '" + endtime + "'},'start': {'date': '" + starttime + "'},'summary': '" + eventSummaryValue + "'}";
            	} else {
            		starttime = date.yyyymmdd();
            		endtime = date.yyyymmdd();
            		starttime = starttime + "T" + eventStartTimeValue + ":00";
                    endtime = endtime + "T" + eventEndTimeValue + ":00";
            		data2 = "{'end': {'dateTime': '" + endtime + "', 'timeZone':'"+ userTimeZone + "'},'start': {'dateTime': '" + starttime + "', 'timeZone':'"+ userTimeZone + "'},'summary': '" + eventSummaryValue + "'}";
            	}               	

            	json = "json";
        		jsonContext = "application/json";
            	
                jQuery.ajax({
                    type : "POST",
                    contentType : jsonContext,
                    data : data2,
                    url : baseUrl + '/' + proxyName + '/calendar/v3/calendars/' + gcalid + '/events?access_token=' + accesstoken +'&amp;' + Math.floor((Math.random()*100)+1),
                    dataType : json,
                    async: false, // when true, you can get multiple events created
                    cache: false,
                    timeout: 5000,

                    // if ajax call success
                    success : function(datain) {
                        // close dialog
                        $("#newEvent").dialog("close"); 
                        var startdate;
                        var enddate;
                        var allday;

                        if (null != datain.start ) { 
                        	if ( null != datain.start.dateTime) { 
	                            startdate = datain.start.dateTime;
	                            enddate = datain.end.dateTime;
	                            allday = false;
	                        } else {
	                            startdate = datain.start.date;
	                            //enddate = new Date(datain.end.date); // Do not set the enddate because it causes repeating events
	                            allday = true; 
	                        }
                    	} else { 
                        	allday = true;
                    	} 
 
                        $('#calendar').fullCalendar('renderEvent', {
                            id : datain.id,
                            title : datain.summary,
                            start : startdate,
                            end : enddate,
                            url : datain.htmlLink,
                            description : datain.description,
                            location : datain.location,
                            allDay : allday
                        }, false // 'stick' flag
                        );
                        
                        // load info into eventArray
                        eventArray.push({
	                        id : datain.id,
	                        title : datain.summary,
	                        start : startdate,
	                        end : enddate,
	                        url : datain.htmlLink,
	                        description : datain.description,
	                        location : datain.location,
	                        allDay : allday,
	                        sequence : datain.sequence,
	                        recurrence : datain.recurrence,
	                        recurringEventId : datain.recurringEventId
	                    });
                    },

                    // if ajax call failed
                    error : function(XMLHttpRequest, textStatus, errorThrown) {
                        $("#newEvent .messageValidation").remove();
                        $("#newEvent").prepend("<p class=\"messageValidation\" style=\"height:20px\" >Sorry, unable to create the event.</p>");
                        //alert( "creating event failed " + textStatus + " " + errorThrown ); // TODO: distinguish between end before start
                        // and AJAX error
                    }
                });  
            };
        }
    });
};

/**
 * Returns date & time in ISO format, adjusting to current time zone if caller
 * wants that (Google timeMin/timeMax use current time zone instead of UTC)
 */
function getTimeIso(dt, useLocalTimezone) {
	// From: http://stackoverflow.com/questions/2573521/how-do-i-output-an-iso-8601-formatted-string-in-javascript
	if (!Date.prototype.toISOString) {
		Date.prototype.toISOString = function() {
			function pad(n) { return n < 10 ? '0' + n : n }
			// NOTE: removed newline after "return" to fix parsing the return in IE8 (MacBook Pro VM)
			return this.getUTCFullYear() + '-'
					+ pad(this.getUTCMonth() + 1) + '-'
					+ pad(this.getUTCDate()) + 'T'
					+ pad(this.getUTCHours()) + ':'
					+ pad(this.getUTCMinutes()) + ':'
					+ pad(this.getUTCSeconds()) + 'Z';
		};
	}
	// Modify to current timezone. Note: this will not react to Sakai's user's timezone change.
	if (useLocalTimezone) {
		dt = new Date(dt.getTime() - (dt.getTimezoneOffset() * 60 * 1000));
	}
	var result = dt.toISOString();
	return result;
}

//prototype yyyymmdd format of the date
Date.prototype.yyyymmdd = function() {
    var yyyy = this.getFullYear().toString();
    var mm = (this.getMonth() + 1).toString();
    var dd = this.getDate().toString();
    return yyyy + "-" + (mm[1] ? mm : "0" + mm[0]) + "-" + (dd[1] ? dd : "0" + dd[0]);
};

//generate the RFC 3339 timestamp
Date.prototype.toRFC3339 = function() {
    function pad(n) {
        return (n < 10 ? '0' + n : n);
    };

    var offset = (this.getTimezoneOffset() / 60) * (-1);

    if (offset > 0) {
        offset = offset < 10 ? '+0' + offset : '+' + offset;
    } else if (offset < 0) {
        offset = offset > -10 ? '-0' + Math.abs(offset) : offset;
    }

    offset = offset + ':00';
    return (this.getFullYear() + '-' + pad(this.getMonth() + 1) + '-' + pad(this.getDate()) + 'T' + pad(this.getHours()) + ':' + pad(this.getMinutes()) + ':' + pad(this.getSeconds()) + offset);
};

//add hours to Date object
Date.prototype.addHours = function(h) {
    this.setHours(this.getHours() + h);
    return this;
};

//function to get event text (6:30pm) from event value (18:30)
getEventTimeText = function(eventTimeValue) {
	// arrays are now global                      
    var i = eventTimeValueArray.indexOf(eventTimeValue);
    
    var eventTimeText = eventTimeTextArray[i];
    
    return eventTimeText;                                     
};

//function to get event value (18:30) from event value (6:30pm)
getEventTimeValue = function(eventTimeText) {
	try{
		var hours = Number(eventTimeText.match(/^(\d+)/)[1]);
		var minutes = Number(eventTimeText.match(/:(\d+)/)[1]);
		var AMPM = eventTimeText.match(/(..)$/)[1];
	}
	catch(err) { // Handle regex errors if bad values entered by the user
		return -1;
	}
	// Perform some basic range validation
	if (hours < 1 || hours > 12){
		return -1;
	}
	else if (minutes < 0 || minutes > 59){
		return -1;
	}
	else if (AMPM != "am" && AMPM != "pm"){
		return -1;
	}
	// Format time to a 24-hour format.
	if(AMPM == "pm" && hours<12) hours = hours+12;
	if(AMPM == "am" && hours==12) hours = hours-12;
	var sHours = hours.toString();
	var sMinutes = minutes.toString();
	if(hours<10) sHours = "0" + sHours;
	if(minutes<10) sMinutes = "0" + sMinutes;
	eventTimeValue = sHours + ":" + sMinutes;
    
    return eventTimeValue;                                     
};

// function to find the event in the full calendar event array and update the sequence
findFullCalendarEvent = function( event ) {
	for ( var i = 0; i < eventArray.length; i++ ) {
		if ( event.id === eventArray[i].id) {
			// update the sequence
			eventArray[i].sequence++;
			return i;
		}
	}
	return -1; // did not find it
};

// refresh calendar items
refreshCalendarItems = function( start, end, callback ) {
	// use Math function to make sure this request is unique (caching issues can happen in ie - may be refactored out later)
	jQuery.ajax({
    	type : "GET",
    	url : baseUrl + '/' + proxyName + '/calendar/v3/calendars/' + gcalid + '/events?access_token=' + accesstoken +'&amp;' + Math.floor((Math.random()*100)+1),
        dataType : 'json',
        
        data : {
            'timeMin' : getTimeIso(start, false),
            'timeMax' : getTimeIso(end, false),
            'singleEvents' : true,
            'showDeleted'  : false,
            'timeZone'	   : userTimeZone,
        },
        cache : false,
        async : false,
        
        // if ajax call success
        success : function(data) {
  
        	eventArray.length = 0; // Clear out the array
            var itemsize = data.items.length; 

            if ( itemsize > 0 ) {
		        jQuery.each(data.items, function(i, item) {
		            var startdate;
		            var enddate;
		            var allday; 
		            
		            if (item.start.dateTime) {
		                startdate = item.start.dateTime;
		                enddate = item.end.dateTime;
		                allday = false;
		            } else {
		                startdate = item.start.date;
		                // enddate = new Date(item.end.date); // IE does not like the enddate being set
		                allday = true;
		            }
		            
		            // TODO: not the long-term solution
		            if ( viewbusyOnly ) {
		            	titleString = busy;
		            	descriptionString = busy;
		            	locationString = busy;
		            } else {
		            	titleString = item.summary;
		            	descriptionString = item.description;
		            	locationString = item.location
		            }
		             
                    eventArray.push({
                        id : item.id,
                        title : titleString,
                        start : startdate,
                        end : enddate,
                        url : item.htmlLink,
                        description : descriptionString,
                        location : locationString,
                        allDay : allday,
                        sequence : item.sequence,
                        recurrence : item.recurrence,
                        recurringEventId : item.recurringEventId
                    });

		        });
            } // close if
        },
        // if ajax call failed 
        error : function(XMLHttpRequest, textStatus, errorThrown) {
            $("#newEvent .messageValidation").remove();
            $("#newEvent").prepend("<p class=\"messageValidation\" style=\"height:20px\" >Sorry, cannot get information from the Google calendar!</p>");
        	//if (null !== console ) { console.log( "AJAX call failed getting info from Google calendar " + textStatus + " " + errorThrown ); } 
        },
        
        complete : function () {
        	callback(eventArray); // return callback here to pick up all events
        }
    });
};

// from http://stackoverflow.com/questions/3629183/why-doesnt-indexof-work-on-an-array-ie8
// ie8 does not support indexOf!
if (!Array.prototype.indexOf)
{
  Array.prototype.indexOf = function(elt /*, from*/)
  {
    var len = this.length >>> 0;

    var from = Number(arguments[1]) || 0;
    from = (from < 0)
         ? Math.ceil(from)
         : Math.floor(from);
    if (from < 0)
      from += len;

    for (; from < len; from++)
    {
      if (from in this &&
          this[from] === elt)
        return from;
    }
    return -1;
  };
}