/*  Excellent plugin for fixing IE support for CORS: https://github.com/tlianza/ajaxHooks/blob/master/src/ajax/xdr.js  */
(function( jQuery ) {
//alert( " in new jQuery function " + window.XDomainRequest);
if ( window.XDomainRequest ) {
	jQuery.ajaxTransport("+*", function( s ) {
		//alert( "in new ajax transport");
		if ( s.crossDomain && s.async ) {
			if ( s.timeout ) {
				s.xdrTimeout = s.timeout;
				delete s.timeout;
			}
			var xdr = new XDomainRequest();

			return {
				send: function( _, complete ) {
					function callback( status, statusText, responses, responseHeaders ) {
						xdr.onload = xdr.onerror = xdr.ontimeout = xdr.onprogress = jQuery.noop;
						xdr = undefined;
						complete( status, statusText, responses, responseHeaders );
					}
					//xdr = new XDomainRequest();
					xdr.open( s.type, s.url );
					// xdr.contentType = "text/plain"; // can't set contentType - "does not support this action"
					xdr.onload = function() {
						callback( 200, "OK", { text: xdr.responseText }, "Content-Type: " + xdr.contentType );
					};
					//xdr.setRequestHeader("Access-Control-Allow-Origin","https://googleapis.com"); // not supported here

					xdr.onerror = function(e) {
						callback( 404, "Not Found" );
					};
					xdr.onprogress = function() {};
					if ( s.xdrTimeout ) {
						xdr.ontimeout = function() {
							callback( 0, "timeout" );
						};
						xdr.timeout = s.xdrTimeout;
					}
					xdr.ontimeout = function () {
	                    alert('xdr ontimeout');
	                };
					//xdr.send( ( s.hasContent && s.data ) || null );
					setTimeout(function () {
						//alert( "s.hasContent && s.data: " + s.hasContent && s.data );
						xdr.send( ( s.hasContent && s.data ) || null );
		            }, 100);
				},
				abort: function() {
					if ( xdr ) {
						xdr.onerror = jQuery.noop();
						xdr.abort();
					}
				}
			};
		}
	});
}
})( jQuery );