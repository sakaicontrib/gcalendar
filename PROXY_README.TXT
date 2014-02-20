Proxy information:
==================

The gcalendar project uses a cross domain proxy for browser communication with the Google API's.
The cross domain proxy is now built as part of the gcalendar war file instead of being its own separate project as this simplifies maintenance.

See gcalendar/gcalendar-tool/tool/pom.xml for information on the build dependencies for the proxy servlet.

See gcalendar-tool/tool/src/webapp/WEB-INF/web.xml for details on the proxy servlet definition, servlet mapping and logging setting.

Proxy URL format:
----------------
sakai-gcalendar-tool is the war file name and context path; gcalendar is the servlet name; these two together (sakai-gcalendar-tool/gcalendar) will be translated to the targetUri: https://www.googleapis.com

Verify proxy configuration is working
-------------------------------------

If you wish to reach the target url https://www.googleapis.com/calendar/v3/calendars/abc and the proxy is correctly configured, you will get the same result using the url http://localhost:8080//sakai-gcalendar-tool/gcalendar/calendar/v3/calendars/abc. These particular url's are just an example and do not return data. You will get a "Login Required" message showing that you are connecting to the Google API service. 

