package com.orangelabs.rcs.service.api.client.messaging;

import com.orangelabs.rcs.service.api.client.messaging.IChatEventListener;
import com.orangelabs.rcs.service.api.client.messaging.InstantMessage;

/**
 * Chat session interface
 */
interface IChatSession {
	// Get session ID
	String getSessionID();

	// Get chat ID
	String getChatID();

	// Get remote contact
	String getRemoteContact();
	
	// Get session state
	int getSessionState();

	// Is chat group
	boolean isChatGroup();
	
	// Is Store & Forward
	boolean isStoreAndForward();

	// Get first message exchanged during the session
	InstantMessage getFirstMessage();

	// Accept the session invitation
	void acceptSession();

	// Reject the session invitation
	void rejectSession();

	// Cancel the session
	void cancelSession();

	// Get list of participants in the session
	List<String> getParticipants();

	// Add a participant to the session
	void addParticipant(in String participant);

	// Add a list of participants to the session
	void addParticipants(in List<String> participants);

	// Send a text message
	String sendMessage(in String text);

	// Set is composing status
	void setIsComposingStatus(in boolean status);

	// Set message delivery status
	void setMessageDeliveryStatus(in String msgId, in String status);

	// Add session listener
	void addSessionListener(in IChatEventListener listener);

	// Remove session listener
	void removeSessionListener(in IChatEventListener listener);	
}