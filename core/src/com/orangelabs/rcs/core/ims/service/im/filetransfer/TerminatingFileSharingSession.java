/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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

package com.orangelabs.rcs.core.ims.service.im.filetransfer;

import java.io.IOException;
import java.util.Vector;

import com.orangelabs.rcs.core.content.ContentManager;
import com.orangelabs.rcs.core.ims.network.sip.SipMessageFactory;
import com.orangelabs.rcs.core.ims.network.sip.SipUtils;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpConstants;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpEventListener;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpManager;
import com.orangelabs.rcs.core.ims.protocol.msrp.MsrpSession;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaAttribute;
import com.orangelabs.rcs.core.ims.protocol.sdp.MediaDescription;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpParser;
import com.orangelabs.rcs.core.ims.protocol.sdp.SdpUtils;
import com.orangelabs.rcs.core.ims.protocol.sip.SipRequest;
import com.orangelabs.rcs.core.ims.protocol.sip.SipResponse;
import com.orangelabs.rcs.core.ims.protocol.sip.SipTransactionContext;
import com.orangelabs.rcs.core.ims.service.ImsService;
import com.orangelabs.rcs.core.ims.service.ImsServiceSession;
import com.orangelabs.rcs.core.ims.service.SessionTimerManager;
import com.orangelabs.rcs.core.ims.service.im.InstantMessagingService;
import com.orangelabs.rcs.core.ims.service.im.chat.ChatUtils;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.utils.NetworkRessourceManager;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Terminating file transfer session
 * 
 * @author jexa7410
 */
public class TerminatingFileSharingSession extends ImsFileSharingSession implements MsrpEventListener {
	/**
	 * MSRP manager
	 */
	private MsrpManager msrpMgr = null;

	/**
     * The logger
     */
    private static Logger logger = Logger.getLogger(TerminatingFileSharingSession.class.getSimpleName());

    /**
     * Constructor
     * 
	 * @param parent IMS service
	 * @param invite Initial INVITE request
	 * @param thumbnail Thumbnail
	 */
	public TerminatingFileSharingSession(ImsService parent, SipRequest invite) {		
		super(parent, ContentManager.createMmContentFromSdp(invite), SipUtils.getAssertedIdentity(invite), ChatUtils.extractFileThumbnail(invite));

		// Create dialog path
		createTerminatingDialogPath(invite);
		
		// Set contribution ID
		String id = ChatUtils.getContributionId(invite);
		setContributionID(id);		
	}
	
	/**
	 * Background processing
	 */
	public void run() {
		try {
	    	if (logger.isActivated()) {
	    		logger.info("Initiate a new file transfer session as terminating");
	    	}
			
            if (RcsSettings.getInstance().isFileTransferAutoAccepted()) {
				if (logger.isActivated()) {
					logger.debug("Auto accept file transfer invitation");
				}
			} else {
				if (logger.isActivated()) {
					logger.debug("Accept manually file transfer invitation");
				}

				// Send a 180 Ringing response
				send180Ringing(getDialogPath().getInvite(), getDialogPath().getLocalTag());

				// Wait invitation answer
				int answer = waitInvitationAnswer();
				if (answer == ImsServiceSession.INVITATION_REJECTED) {
					if (logger.isActivated()) {
						logger.debug("Session has been rejected by user");
					}

					// Remove the current session
					getImsService().removeSession(this);

					// Notify listeners
					for (int i = 0; i < getListeners().size(); i++) {
						getListeners().get(i).handleSessionAborted(ImsServiceSession.TERMINATION_BY_USER);
					}
					return;
				} else if (answer == ImsServiceSession.INVITATION_NOT_ANSWERED) {
					if (logger.isActivated()) {
						logger.debug("Session has been rejected on timeout");
					}
					// Ringing period timeout
					send486Busy(getDialogPath().getInvite(), getDialogPath().getLocalTag());

					// Remove the current session
					getImsService().removeSession(this);

					// Notify listeners
					for (int j = 0; j < getListeners().size(); j++) {
						getListeners().get(j).handleSessionAborted(ImsServiceSession.TERMINATION_BY_TIMEOUT);
					}
					return;
				} else if (answer == ImsServiceSession.INVITATION_CANCELED) {
					if (logger.isActivated()) {
						logger.debug("Session has been canceled");
					}
					return;
				}
			}
            
            // FT should be rejected by user if file is too big or size exceeds device storage capacity.
            // This control should be done at UI level. However if user accepts invitation, the stack replies 403 Forbidden.
            FileSharingError error = FileSharingSession.isFileCapacityAcceptable(getContent().getSize());
            if (error != null) {
            	// Extract of GSMA specification:
				// If the file is bigger than FT MAX SIZE, a warning message is displayed when trying to
				// send or receive a file larger than the mentioned limit and the transfer will be cancelled
				// (that is at protocol level, the SIP INVITE request will never be sent or an automatic
				// rejection response SIP 403 Forbidden with a Warning header set to �133 Size
				// exceeded� will be sent by the entity that detects that the file size is too big to the other
				// end depending on the scenario).
                send403Forbidden(getDialogPath().getInvite(), getDialogPath().getLocalTag(),"133 Size exceeded");
                // Close session
                handleError(error);
                return;
            }

			// Parse the remote SDP part
			String remoteSdp = getDialogPath().getInvite().getSdpContent();
        	SdpParser parser = new SdpParser(remoteSdp.getBytes());
    		Vector<MediaDescription> media = parser.getMediaDescriptions();
			MediaDescription mediaDesc = media.elementAt(0);
            String protocol = mediaDesc.protocol;
            boolean isSecured = false;
            if (protocol != null) {
                isSecured = protocol.equalsIgnoreCase(MsrpConstants.SOCKET_MSRP_SECURED_PROTOCOL);
            }
            // Changed by Deutsche Telekom
            String fileSelector = mediaDesc.getMediaAttribute("file-selector").getValue();
            String fileTransferId = mediaDesc.getMediaAttribute("file-transfer-id").getValue();
			MediaAttribute attr3 = mediaDesc.getMediaAttribute("path");
            String remotePath = attr3.getValue();
            String remoteHost = SdpUtils.extractRemoteHost(parser.sessionDescription, mediaDesc);
    		int remotePort = mediaDesc.port;
			
    		// Changed by Deutsche Telekom
    		String fingerprint = SdpUtils.extractFingerprint(parser, mediaDesc);

            // Extract the "setup" parameter
            String remoteSetup = "passive";
			MediaAttribute attr4 = mediaDesc.getMediaAttribute("setup");
			if (attr4 != null) {
				remoteSetup = attr4.getValue();
			}
            if (logger.isActivated()){
				logger.debug("Remote setup attribute is " + remoteSetup);
			}
            
    		// Set setup mode
            String localSetup = createSetupAnswer(remoteSetup);
            if (logger.isActivated()){
				logger.debug("Local setup attribute is " + localSetup);
			}

    		// Set local port
	    	int localMsrpPort;
	    	if (localSetup.equals("active")) {
		    	localMsrpPort = 9; // See RFC4145, Page 4
	    	} else {
				localMsrpPort = NetworkRessourceManager.generateLocalMsrpPort();
	    	}            
            
	        // Create the MSRP manager
			String localIpAddress = getImsService().getImsModule().getCurrentNetworkInterface().getNetworkAccess().getIpAddress();
			msrpMgr = new MsrpManager(localIpAddress, localMsrpPort, getImsService());
            msrpMgr.setSecured(isSecured);

			// Build SDP part
	    	String ipAddress = getDialogPath().getSipStack().getLocalIpAddress();
	    	int maxSize = ImsFileSharingSession.getMaxFileSharingSize();
	    	String sdp = SdpUtils.buildFileSDP(ipAddress, localMsrpPort,
                    msrpMgr.getLocalSocketProtocol(), getContent().getEncoding(), fileTransferId,
                    fileSelector, null, localSetup, msrpMgr.getLocalMsrpPath(),
                    SdpUtils.DIRECTION_RECVONLY, maxSize);

	    	// Set the local SDP part in the dialog path
	        getDialogPath().setLocalContent(sdp);

    		// Create the MSRP server session
            if (localSetup.equals("passive")) {
            	// Passive mode: client wait a connection
            	 // Changed by Deutsche Telekom
                MsrpSession session = msrpMgr.createMsrpServerSession(remotePath, this);
                // Do not use right now the mapping to do not increase memory and cpu consumption
                session.setMapMsgIdFromTransationId(false);
            	
    			// Open the connection
    			Thread thread = new Thread(){
    				public void run(){
    					try {
							// Open the MSRP session
    						msrpMgr.openMsrpSession(ImsFileSharingSession.DEFAULT_SO_TIMEOUT);
    						
			    	        // Send an empty packet
			            	sendEmptyDataChunk();
						} catch (IOException e) {
							if (logger.isActivated()) {
				        		logger.error("Can't create the MSRP server session", e);
				        	}
						}		
    				}
    			};
    			thread.start();
            }

            // Create a 200 OK response
        	if (logger.isActivated()) {
        		logger.info("Send 200 OK");
        	}
            SipResponse resp = SipMessageFactory.create200OkInviteResponse(getDialogPath(),
            		InstantMessagingService.FT_FEATURE_TAGS, sdp);

            // The signalisation is established
            getDialogPath().sigEstablished();

            // Send response
            SipTransactionContext ctx = getImsService().getImsModule().getSipManager().sendSipMessageAndWait(resp);

            // Analyze the received response 
            if (ctx.isSipAck()) {
    	        // ACK received
    			if (logger.isActivated()) {
    				logger.info("ACK request received");
    			}

                // Notify listeners
                for(int j=0; j < getListeners().size(); j++) {
                    getListeners().get(j).handleSessionStarted();
                }

        		// Create the MSRP client session
                if (localSetup.equals("active")) {
                	// Active mode: client should connect
                	// Changed by Deutsche Telekom
                    MsrpSession session = msrpMgr.createMsrpClientSession(remoteHost, remotePort, remotePath, this, fingerprint);
                    session.setMapMsgIdFromTransationId(false);

					// Open the connection
					Thread thread = new Thread() {
						public void run() {
							try {
								// Open the MSRP session
								msrpMgr.openMsrpSession(ImsFileSharingSession.DEFAULT_SO_TIMEOUT);

								// Send an empty packet
								sendEmptyDataChunk();
							} catch (IOException e) {
								if (logger.isActivated()) {
									logger.error("Can't create the MSRP server session", e);
								}
							}
						}
					};
					thread.start();
                }

                // The session is established
    	        getDialogPath().sessionEstablished();

            	// Start session timer
            	if (getSessionTimerManager().isSessionTimerActivated(resp)) {        	
            		getSessionTimerManager().start(SessionTimerManager.UAS_ROLE, getDialogPath().getSessionExpireTime());
            	}
            } else {
        		if (logger.isActivated()) {
            		logger.debug("No ACK received for INVITE");
            	}

        		// No response received: timeout
            	handleError(new FileSharingError(FileSharingError.SESSION_INITIATION_FAILED));
            }
		} catch(Exception e) {
        	if (logger.isActivated()) {
        		logger.error("Session initiation has failed", e);
        	}

        	// Unexpected error
			handleError(new FileSharingError(FileSharingError.UNEXPECTED_EXCEPTION,
					e.getMessage()));
		}
		
		if (logger.isActivated()) {
    		logger.debug("End of thread");
    	}
	}

	/**
	 * Send an empty data chunk
	 */
	public void sendEmptyDataChunk() {
		try {
			msrpMgr.sendEmptyChunk();
		} catch(Exception e) {
	   		if (logger.isActivated()) {
	   			logger.error("Problem while sending empty data chunk", e);
	   		}
		}
	}	

	/**
	 * Data has been transfered
	 * 
	 * @param msgId Message ID
	 */
	public void msrpDataTransfered(String msgId) {
		// Not used in terminating side
	}
	
	/**
	 * Data transfer has been received
	 * 
	 * @param msgId Message ID
	 * @param data Received data
	 * @param mimeType Data mime-type 
	 */
	public void msrpDataReceived(String msgId, byte[] data, String mimeType) {
    	if (logger.isActivated()) {
    		logger.info("Data received");
    	}
    	
    	// File has been transfered
    	fileTransfered();
	
    	try {
        	// Close content with received data
            getContent().writeData2File(data);
            getContent().closeFile();

	    	// Notify listeners
	    	for(int j=0; j < getListeners().size(); j++) {
	    		((FileSharingSessionListener)getListeners().get(j)).handleFileTransfered(getContent().getUrl());
	        }
	   	} catch(IOException e) {
	   		// Delete the temp file
            deleteFile();

	   		// Notify listeners
	    	for(int j=0; j < getListeners().size(); j++) {
	    		((FileSharingSessionListener)getListeners().get(j)).handleTransferError(new FileSharingError(FileSharingError.MEDIA_SAVING_FAILED));
	    	}
	   	} catch(Exception e) {
	   		// Delete the temp file
            deleteFile();

            // Notify listeners
	    	for(int j=0; j < getListeners().size(); j++) {
	    		((FileSharingSessionListener)getListeners().get(j)).handleTransferError(new FileSharingError(FileSharingError.MEDIA_TRANSFER_FAILED));
	    	}
	   	}
	}
    
	/**
	 * Data transfer in progress
	 * 
	 * @param currentSize Current transfered size in bytes
	 * @param totalSize Total size in bytes
	 */
	public void msrpTransferProgress(long currentSize, long totalSize) {
        // Not used
	}

    /**
     * Data transfer in progress
     *
     * @param currentSize Current transfered size in bytes
     * @param totalSize Total size in bytes
     * @param data received data chunk
     */
    public boolean msrpTransferProgress(long currentSize, long totalSize, byte[] data) {
		if (isSessionInterrupted() || isInterrupted()) {
			return true;
		}

        try {
        	// Update content with received data
            getContent().writeData2File(data);
            
			// Notify listeners
			for (int j = 0; j < getListeners().size(); j++) {
				((FileSharingSessionListener) getListeners().get(j)).handleTransferProgress(currentSize, totalSize);
			}
        } catch(Exception e) {
	   		// Delete the temp file
            deleteFile();

            // Notify listeners
            for (int j = 0; j < getListeners().size(); j++) {
                ((FileSharingSessionListener) getListeners().get(j)).handleTransferError(new FileSharingError(
                        FileSharingError.MEDIA_SAVING_FAILED, e.getMessage()));
            }
        }
        return true;
	}	

	/**
	 * Data transfer has been aborted
	 */
	public void msrpTransferAborted() {
    	if (logger.isActivated()) {
    		logger.info("Data transfer aborted");
    	}
    	
        if (!isFileTransfered()) {
	   		// Delete the temp file
            deleteFile();
        }
	}	

    /**
     * Prepare media session
     * 
     * @throws Exception 
     */
    public void prepareMediaSession() throws Exception {
        // Nothing to do in terminating side
    }

    /**
     * Start media session
     * 
     * @throws Exception 
     */
    public void startMediaSession() throws Exception {
        // Nothing to do in terminating side
    }

    /**
     * Close media session
     */
    public void closeMediaSession() {
        // Close MSRP session
        if (msrpMgr != null) {
            msrpMgr.closeSession();
            if (logger.isActivated()) {
                logger.debug("MSRP session has been closed");
            }
        }
        if (!isFileTransfered()) {
	   		// Delete the temp file
            deleteFile();
        }
    }

    /**
     * Delete file
     */
    private void deleteFile() {
        if (logger.isActivated()) {
            logger.debug("Delete incomplete received file");
        }
        try {
            getContent().deleteFile();
        } catch (IOException e) {
            if (logger.isActivated()) {
                logger.error("Can't delete received file", e);
            }
        }
    }
}
