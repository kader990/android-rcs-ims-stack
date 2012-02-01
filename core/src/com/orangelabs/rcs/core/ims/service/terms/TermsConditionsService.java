/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright © 2010 France Telecom S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.orangelabs.rcs.core.ims.service.terms;

import java.io.ByteArrayInputStream;

import org.xml.sax.InputSource;

import com.orangelabs.rcs.core.CoreException;
import com.orangelabs.rcs.core.ims.ImsModule;
import com.orangelabs.rcs.core.ims.network.sip.SipManager;
import com.orangelabs.rcs.core.ims.network.sip.SipMessageFactory;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.core.ims.service.ImsService;
import com.orangelabs.rcs.core.ims.service.SessionAuthenticationAgent;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Terms & conditions service
 * 
 * @author jexa7410
 */
public class TermsConditionsService extends ImsService {
	/**
	 * Request MIME type
	 */
	private final static String REQUEST_MIME_TYPE = "application/end-user-confirmation-request+xml";
	
	/**
	 * Request MIME type
	 */
	private final static String ACK_MIME_TYPE = "application/end-user-confirmation-ack+xml";
	
	/**
	 * Response MIME type
	 */
	private final static String RESPONSE_MIME_TYPE = "application/end-user-confirmation-response+xml";
	
	/**
	 * Accept response
	 */
	private final static String ACCEPT_RESPONSE = "accept";

	/**
	 * Decline response
	 */
	private final static String DECLINE_RESPONSE = "decline";

	/**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

	/**
     * Constructor
     * 
     * @param parent IMS module
     * @throws CoreException
     */
	public TermsConditionsService(ImsModule parent) throws CoreException {
        super(parent, true);
	}

	/**
	 * Start the IMS service
	 */
	public synchronized void start() {
		if (isServiceStarted()) {
			// Already started
			return;
		}
		setServiceStarted(true);
	}

    /**
     * Stop the IMS service
     */
	public synchronized void stop() {
		if (!isServiceStarted()) {
			// Already stopped
			return;
		}
		setServiceStarted(false);
	}

	/**
     * Check the IMS service
     */
	public void check() {
	}

	/**
     * Receive a message
     * 
     * @param message Received message
     */
    public void receiveMessage(SipRequest message) {
    	if (logger.isActivated()) {
    		logger.debug("Receive end user confirmation message");
    	}
    	
    	try {
	    	if (message.getContentType().equals(REQUEST_MIME_TYPE)) {
		    	// Parse content
				InputSource input = new InputSource(new ByteArrayInputStream(message.getContentBytes()));
				TermsRequestParser parser = new TermsRequestParser(input);

				// Notify listener
	    		getImsModule().getCore().getListener().handleUserConfirmationRequest(
	    				getRemoteIdentity(message),
	    				parser.getId(),
	    				parser.getType(),
	    				parser.getPin(),
	    				parser.getSubject(),
	    				parser.getText());
	    	} else
	    	if (message.getContentType().equals(ACK_MIME_TYPE)) {
		    	// Parse content
				InputSource input = new InputSource(new ByteArrayInputStream(message.getContentBytes()));
				TermsAckParser parser = new TermsAckParser(input);

				// Notify listener
	    		getImsModule().getCore().getListener().handleUserConfirmationAck(
	    				getRemoteIdentity(message),
	    				parser.getId(),
	    				parser.getStatus(),
	    				parser.getSubject(),
	    				parser.getText());
	    	} else {
	    		if (logger.isActivated()) {
	    			logger.debug("Unknown user confirmation request");
	    		}
	    	}
    	} catch(Exception e) {
    		if (logger.isActivated()) {
    			logger.error("Can't parse user confirmation message", e);
    		}
    	}
    }

    /**
	 * Accept terms
	 * 
	 * @param id Request ID
	 * @param pin Response value
	 * @return Boolean result
	 */
	public boolean acceptTerms(String remote, String id, String pin) {
		if (logger.isActivated()) {
			logger.debug("Send response for request " + id);
		}
		return sendSipMessage(remote, id, ACCEPT_RESPONSE, pin);
	}

	/**
	 * Reject terms
	 * 
	 * @param id Request ID
	 * @param pin Response value
	 * @return Boolean result
	 */
	public boolean rejectTerms(String remote, String id, String pin) {
		if (logger.isActivated()) {
			logger.debug("Send response for request " + id);
		}
		return sendSipMessage(remote, id, DECLINE_RESPONSE, pin);
	}
	
	/**
	 * Send SIP MESSAGE
	 * 
	 * @param remote Remote server
	 * @param id Request ID
	 * @param value Response value
	 * @param pin Response value
	 * @return Boolean result
	 */
	private boolean sendSipMessage(String remote, String id, String value, String pin) {
		boolean result = false;
		try {
			if (logger.isActivated()) {
       			logger.debug("Send SIP response");
       		}

			// Build response
			String responseTag = "<EndUserConfirmationResponse id=\"" + id + "\" value=\"" + value + "\"";
			if (pin != null) {
				responseTag += " pin=\"";
			}
			responseTag += "/>";
			String response =
				"<?xml version=\"1.0\" standalone=\"yes\"?>" +
				"<NewDataSet>" + responseTag + "</NewDataSet>";				
			
		    // Create authentication agent 
       		SessionAuthenticationAgent authenticationAgent = new SessionAuthenticationAgent();
       		
       		// Create a dialog path
        	SipDialogPath dialogPath = new SipDialogPath(
        			getImsModule().getSipManager().getSipStack(),
        			getImsModule().getSipManager().getSipStack().generateCallId(),
    				1,
    				remote,
    				ImsModule.IMS_USER_PROFILE.getPublicUri(),
    				remote,
    				getImsModule().getSipManager().getSipStack().getServiceRoutePath());        	
        	
	        // Create MESSAGE request
        	if (logger.isActivated()) {
        		logger.info("Send first MESSAGE");
        	}
	        SipRequest msg = SipMessageFactory.createMessage(dialogPath, RESPONSE_MIME_TYPE, response);
	        
	        // Send MESSAGE request
	        SipTransactionContext ctx = getImsModule().getSipManager().sendSipMessageAndWait(msg);
	
	        // Wait response
        	if (logger.isActivated()) {
        		logger.info("Wait response");
        	}
	        ctx.waitResponse(SipManager.TIMEOUT);
	
	        // Analyze received message
            if (ctx.getStatusCode() == 407) {
                // 407 response received
            	if (logger.isActivated()) {
            		logger.info("407 response received");
            	}

    	        // Set the Proxy-Authorization header
            	authenticationAgent.readProxyAuthenticateHeader(ctx.getSipResponse());

                // Increment the Cseq number of the dialog path
                dialogPath.incrementCseq();

                // Create a second MESSAGE request with the right token
                if (logger.isActivated()) {
                	logger.info("Send second MESSAGE");
                }
    	        msg = SipMessageFactory.createMessage(dialogPath, RESPONSE_MIME_TYPE, response);
    	        
    	        // Set the Authorization header
    	        authenticationAgent.setProxyAuthorizationHeader(msg);
                
                // Send MESSAGE request
    	        ctx = getImsModule().getSipManager().sendSipMessageAndWait(msg);

                // Wait response
                if (logger.isActivated()) {
                	logger.info("Wait response");
                }
                ctx.waitResponse(SipManager.TIMEOUT);

                // Analyze received message
                if ((ctx.getStatusCode() == 200) || (ctx.getStatusCode() == 202)) {
                    // 200 OK response
                	if (logger.isActivated()) {
                		logger.info("20x OK response received");
                	}
                	result = true;
                } else {
                    // Error
                	if (logger.isActivated()) {
                		logger.info("Delivery report has failed: " + ctx.getStatusCode()
    	                    + " response received");
                	}
                }
            } else
            if ((ctx.getStatusCode() == 200) || (ctx.getStatusCode() == 202)) {
	            // 200 OK received
            	if (logger.isActivated()) {
            		logger.info("20x OK response received");
            	}
            	result = true;
	        } else {
	            // Error responses
            	if (logger.isActivated()) {
            		logger.info("Delivery report has failed: " + ctx.getStatusCode()
	                    + " response received");
            	}
	        }
        } catch(Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Delivery report has failed", e);
        	}
        }
        return result;
	}	

	/**
	 * Get remote identity of the incoming request
	 * 
     * @param request Request
     * @return ID
	 */
	private String getRemoteIdentity(SipRequest request) {
		String referredBy = SipUtils.getReferredByHeader(request);
		if (referredBy != null) {
			// Use the Referred-By header
			return referredBy;
		} else {
			// Use the From header
			return request.getFromUri();
		}
	}		
	/**
	 * Is a terms & conditions request
	 * 
     * @param request Request
     * @return Boolean
	 */
	public static boolean isTermsRequest(SipRequest request) {
    	String contentType = request.getContentType();
    	if ((contentType != null) &&
    			contentType.startsWith("application/end-user-confirmation")) {
    		return true;
    	} else {
    		return false;
    	}
	}
}
