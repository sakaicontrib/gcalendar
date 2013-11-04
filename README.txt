Sakai configuration:
====================
1. Compile the google-auth-impl dependency:
   $ svn co https://source.sakaiproject.org/contrib/umich/google/google-auth-impl/
   $ cd google-auth-impl
   $ mvn install
   
2. Compile this gcalendar project
   $ cd gcalendar
   $ mvn install sakai:deploy   
   
3. Setup library resources for FullCalendar:
   a. cd reference
   b. svn merge -c 129326  https://source.sakaiproject.org/svn/reference/trunk
   c. mvn install sakai:deploy

4. Define the following in sakai.properties:
   google.service.account.email=
   google.private.key=

   if using a proxy server to access the google api's add the following two properties:
   proxy.base.url=../../   
   proxy.name=crossdomain/gcalendar 

   if not using a proxy server to access the goolge api's simply add the following:
   proxy.base.url=https://www.googleapis.com

5. Deploy a proxy defined for gcalendar, for example:
   a. svn co https://source.sakaiproject.org/contrib/umich/proxy-crossdomain/trunk/ proxy-crossdomain
   b. cd proxy-crossdomain
   c. mvn install sakai:deploy

6. For testing, make sure the Sakai user account has an email which matches the Google account
   
Google configuration:
=====================

      * Authorization: Server- Server Based Authorization
         * Google Documentation: https://developers.google.com/accounts/docs/OAuth2ServiceAccount#libraries
         * Service account Creation:
            - Create a Google account for Sakai admin
            - Go to Services in Google Console (https://code.google.com/apis/console), and enable Calendar API
            - Create Service Account in Google Console for Sakai admin user, by going to API Access
               -- Click "Create another client ID", select "Service account", click "Create client ID"
            - Download the private key and save it in a secure place.
         * Configure Google Apps for education to trust the Service account created above, and define the scope of access. Related documentation is at http://support.google.com/a/bin/answer.py?hl=en&answer=162106. This will give the service account the power to become any user of Google Apps for education domain, without user's input.


Implementation Notes:
=====================

      * Reason for choosing Google Calendar API v3 over Google Calendar API v2:
         * Google claims Google Calendar API v3 to be fairly stable
         * v2 uses Java 1.5 and v3 uses Java 1.6
         * v2 depreciated in November of 2014
         * v2 does not support Server-Server based OAuth 2.0 authorization

      * Server side: 
         * Google Calendar API v3 has been used, to create Sakai APIs, for authorization, and to create and get site Google calendars. 
         * Google Calendar Id is stored as a Sakai site property, which maps the Sakai site to its appropriate Google calendar.
         * Authorization: successfully setup Server-Server based OAuth 2.0 authorization with Google and authorized it to talk to test Google Apps for education admin domain “collab.its.umich.edu”. More details below.

      * User Interface: 
         * Full Calendar JQuery plugin has been used to make direct AJAX calls to Google, to do get/post Google event operations. Doing this directly n the front-end would is more efficient and faster, than to process it in the back-end. However, at the server level, it will still be responsible to perform various other operations like authorization, creating a new Calendar for the site and others.

         * The original source of fullcalendar-1.5.3 library was downloaded from http://arshaw.com/fullcalendar/download/
