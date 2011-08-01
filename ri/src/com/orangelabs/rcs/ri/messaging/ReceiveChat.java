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

package com.orangelabs.rcs.ri.messaging;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.text.TextUtils;

import com.orangelabs.rcs.core.ims.service.im.chat.ChatError;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.ri.R;
import com.orangelabs.rcs.ri.utils.Utils;
import com.orangelabs.rcs.service.api.client.ClientApiListener;
import com.orangelabs.rcs.service.api.client.ImsEventListener;
import com.orangelabs.rcs.service.api.client.messaging.IChatEventListener;
import com.orangelabs.rcs.service.api.client.messaging.IChatSession;
import com.orangelabs.rcs.service.api.client.messaging.InstantMessage;
import com.orangelabs.rcs.service.api.client.messaging.MessagingApi;

/**
 * Receive chat
 */
public class ReceiveChat extends Activity implements ClientApiListener, ImsEventListener  {
	/**
     * UI handler
     */
    private Handler handler = new Handler();
    
    /**
	 * Messaging API 
	 */
	private MessagingApi messagingApi;
    
	/**
	 * Session ID
	 */
	private String sessionId;
	
	/**
	 * Contact
	 */
	private String remoteContact;
	
	/**
	 * Subject
	 */
	private String subject;
	
	/**
     * Chat session
     */
    private IChatSession chatSession = null;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set title
        setTitle(R.string.title_recv_chat);
        
		// Get invitation info
        sessionId = getIntent().getStringExtra("sessionId");
		remoteContact = getIntent().getStringExtra("contact");
		subject = getIntent().getStringExtra("subject");
        
		// Remove the notification
		ReceiveChat.removeChatNotification(this, sessionId);

		// Instanciate messaging API
		messagingApi = new MessagingApi(getApplicationContext());
		messagingApi.addApiEventListener(this);
        messagingApi.addImsEventListener(this);
		messagingApi.connectApi();
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();

        // Remove the listener and disconnect from the API
		messagingApi.removeApiEventListener(this);
		messagingApi.disconnectApi();
	}
	
    /**
     * API disabled
     */
    public void handleApiDisabled() {
		handler.post(new Runnable() { 
			public void run() {
				Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_api_disabled));
			}
		});
    }
    
    /**
     * API connected
     */
    public void handleApiConnected() {
		try{
			// Get the chat session
			chatSession = messagingApi.getChatSession(sessionId);
			if (chatSession == null) {
				// Session not found or expired
				Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_session_has_expired));
				return;
			}
			chatSession.addSessionListener(chatSessionListener);
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.title_recv_chat);
			builder.setMessage(getString(R.string.label_from) + " " + remoteContact + "\n" +
					getString(R.string.label_subject) + " " + subject);
			builder.setCancelable(false);
			builder.setIcon(R.drawable.ri_notif_chat_icon);
			builder.setPositiveButton(getString(R.string.label_accept), acceptBtnListener);
			builder.setNegativeButton(getString(R.string.label_decline), declineBtnListener);
			builder.show();
		} catch(Exception e) {
			handler.post(new Runnable(){
				public void run(){
					Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_api_failed));
				}
			});
		}
    }

    /**
     * API disconnected
     */
    public void handleApiDisconnected() {
		handler.post(new Runnable(){
			public void run(){
				Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_api_disconnected));
			}
		});
    }
    
    /**
     * IMS connected
     */
	public void handleImsConnected() {
	}

    /**
     * IMS disconnected
     */
	public void handleImsDisconnected() {
    	// IMS has been disconnected
		handler.post(new Runnable(){
			public void run(){
				Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_ims_disconnected));
			}
		});
	}    
    
    /**
     * Accept button listener
     */
    private OnClickListener acceptBtnListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            Thread thread = new Thread() {
            	public void run() {
                	try {
                		// Accept the invitation
            			chatSession.acceptSession();
	            	} catch(Exception e) {
	        			handler.post(new Runnable() { 
	        				public void run() {
	        					Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_invitation_failed));
	        				}
	        			});
	            	}
            	}
            };
            thread.start();
        }
    };

    /**
     * Reject button listener
     */
    private OnClickListener declineBtnListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            Thread thread = new Thread() {
            	public void run() {
                	try {
                		// Reject the invitation
            			chatSession.rejectSession();
	            	} catch(Exception e) {
	            	}
            	}
            };
            thread.start();

            // Exit activity
			finish();
        }
    };

	/**
	 * Chat session event listener
	 */
	private IChatEventListener chatSessionListener = new IChatEventListener.Stub() {
		// Session is started
		public void handleSessionStarted() {
			try {
				// Display chat view
	        	Intent intent = new Intent(ReceiveChat.this, ChatView.class);
	        	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
	        	intent.putExtra("sessionId", chatSession.getSessionID());
	        	intent.putExtra("subject", subject);
	        	intent.putExtra("originating", false);
	        	intent.putExtra("contact", remoteContact);
	        	startActivity(intent);

	        	// Exit activity
	        	finish();     
			} catch(Exception e) {
				handler.post(new Runnable(){
					public void run(){
						Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_invitation_failed));
					}
				});
			}
		}
	
		// Session has been aborted
		public void handleSessionAborted() {
			handler.post(new Runnable(){
				public void run(){
					Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_invitation_declined));
				}
			});
		}
	    
		// Session has been terminated by remote
		public void handleSessionTerminatedByRemote() {
			handler.post(new Runnable(){
				public void run(){
					Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_sharing_terminated_by_remote));
				}
			});
		}
		
		// Chat error
		public void handleImError(final int error) {
			handler.post(new Runnable(){
				public void run(){
					if (error == ChatError.SESSION_INITIATION_DECLINED) {
						Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_invitation_declined));
					} else {
						Utils.showMessageAndExit(ReceiveChat.this, getString(R.string.label_invitation_failed));
					}
				}
			});
		}

		// Receive a message
		public void handleReceiveMessage(InstantMessage msg) throws RemoteException {
		}
		
		// Is composing event
		public void handleIsComposingEvent(String contact, boolean isComposing) {
		}
		
		// Conference event
	    public void handleConferenceEvent(String contact, String contactDisplayname, String state) {
		}
	    
		// Message delivery status
		public void handleMessageDeliveryStatus(String msgId, String contact, String status) {
		}
		
		// Request to add participant is successful
		public void handleAddParticipantSuccessful() {
		}
	    
		// Request to add participant has failed
		public void handleAddParticipantFailed(String reason) {
		}
	};
    
    /**
     * Add chat notification
     * 
     * @param context Context
     * @param invitation Intent invitation
     */
    public static void addChatInvitationNotification(Context context, Intent invitation) {
    	// Instanciate settings
        RcsSettings.createInstance(context);

        // Create notification
		Intent intent = new Intent(invitation);
		intent.setClass(context, ReceiveChat.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        String notifTitle = context.getString(R.string.title_recv_chat);
        Notification notif = new Notification(R.drawable.ri_notif_chat_icon,
        		notifTitle,
        		System.currentTimeMillis());
        notif.flags = Notification.FLAG_AUTO_CANCEL;
        notif.setLatestEventInfo(context,
        		notifTitle,
        		context.getString(R.string.label_from) + " " + Utils.formatCallerId(invitation),
        		contentIntent);
        
        // Set ringtone
        String ringtone = RcsSettings.getInstance().getChatInvitationRingtone();
        if (!TextUtils.isEmpty(ringtone)) {
			notif.sound = Uri.parse(ringtone);
        }
        
        // Set vibration
        if (RcsSettings.getInstance().isPhoneVibrateForChatInvitation()) {
        	notif.defaults |= Notification.DEFAULT_VIBRATE;
        }
        
        // Send notification
		String sessionId = invitation.getStringExtra("sessionId");
		NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int)Long.parseLong(sessionId), notif);
    }
    
    /**
     * Remove chat notification
     * 
     * @param context Context
     * @param sessionId Session ID
     */
    public static void removeChatNotification(Context context, String sessionId) {
		NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel((int)Long.parseLong(sessionId));
    }
}

