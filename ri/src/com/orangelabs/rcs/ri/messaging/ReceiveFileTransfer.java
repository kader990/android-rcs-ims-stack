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

package com.orangelabs.rcs.ri.messaging;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.orangelabs.rcs.core.ims.service.im.filetransfer.FileSharingError;
import com.orangelabs.rcs.platform.AndroidFactory;
import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.ri.R;
import com.orangelabs.rcs.ri.utils.Utils;
import com.orangelabs.rcs.service.api.client.ClientApiListener;
import com.orangelabs.rcs.service.api.client.ImsEventListener;
import com.orangelabs.rcs.service.api.client.messaging.IFileTransferEventListener;
import com.orangelabs.rcs.service.api.client.messaging.IFileTransferSession;
import com.orangelabs.rcs.service.api.client.messaging.MessagingApi;
import com.orangelabs.rcs.utils.MimeManager;
import com.orangelabs.rcs.utils.StorageUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Received file transfer
 * 
 * @author jexa7410
 */
public class ReceiveFileTransfer extends Activity implements ClientApiListener, ImsEventListener {
	/** The logger */
	private final static Logger logger = Logger.getLogger(ReceiveFileTransfer.class.getSimpleName());

	/**
	 * UI handler
	 */
	private final Handler handler = new Handler();

	/**
	 * Messaging API
	 */
	private MessagingApi messagingApi;

	/**
	 * Session ID
	 */
	private String sessionId;

	/**
	 * Remote Contact
	 */
	private String remoteContact;

	/**
	 * File size
	 */
	private long fileSize;

	/**
	 * File name
	 */
	private String filename;

	/**
	 * File type
	 */
	private String fileType;

	/**
	 * Auto accept
	 */
	private boolean autoAccept = false;

	/**
	 * File thumbnail
	 */
	private byte[] thumbnail;
	
	AlertDialog alertDialog;
	
	/**
	 * fired a boolean value updated atomically to quit only once
	 */
	private AtomicBoolean fired = new AtomicBoolean(false);


	/**
	 * File transfer session
	 */
	private IFileTransferSession transferSession = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (logger.isActivated()) {
			logger.info("onCreate");
		}
		super.onCreate(savedInstanceState);
		// Instantiate settings
		RcsSettings.createInstance(this);
		
		// Set layout
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.messaging_receive_filetransfer);

		Button pauseBtn = (Button) findViewById(R.id.pause_btn);
		pauseBtn.setOnClickListener(btnPauseListener);
		pauseBtn.setEnabled(true);
		Button resumeBtn = (Button) findViewById(R.id.resume_btn);
		resumeBtn.setOnClickListener(btnResumeListener);
		resumeBtn.setEnabled(false);

		// Get invitation info
		sessionId = getIntent().getStringExtra("sessionId");
		remoteContact = getIntent().getStringExtra("contact");
		fileSize = getIntent().getLongExtra("filesize", -1);
		filename = getIntent().getStringExtra("filename");
		fileType = getIntent().getStringExtra("filetype");
		thumbnail = getIntent().getByteArrayExtra("thumbnail");
		autoAccept = getIntent().getBooleanExtra("autoAccept", false);

		if (logger.isActivated()) {
			logger.info("onCreate (sessionId=" + sessionId + ") (remoteContact=" + remoteContact + ") (filename=" + filename
					+ ") (fileSize=" + fileSize + ") (fileType=" + fileType + ") (autoAccept=" + autoAccept + ")");
		}
		// Remove the notification
		ReceiveFileTransfer.removeFileTransferNotification(this, sessionId);

		// Instantiate messaging API
		messagingApi = new MessagingApi(getApplicationContext());
		messagingApi.addApiEventListener(this);
		messagingApi.addImsEventListener(this);
		messagingApi.connectApi();
	}

	@Override
	protected void onDestroy() {
		if (alertDialog != null && alertDialog.isShowing()) {
			alertDialog.dismiss();
		}
		super.onDestroy();
		// Disconnect messaging API
		messagingApi.removeImsEventListener(this);
		messagingApi.removeApiEventListener(this);
		messagingApi.disconnectApi();
	}

	/**
	 * Accept invitation
	 */
	private void acceptInvitation() {
		new Thread() {
			public void run() {
				try {
					// Accept the invitation
					transferSession.acceptSession();
				} catch (Exception e) {
					if (logger.isActivated()) {
						logger.error("Exception occurred", e);
					}
					showDialogAndFinish(R.string.label_invitation_failed);
				}
			}
		}.start();
	}

	/**
	 * Reject invitation
	 */
	private void rejectInvitation() {
		new Thread() {
			public void run() {
				try {
					// Reject the invitation
					transferSession.removeSessionListener(fileTransferSessionListener);
					transferSession.rejectSession();
				} catch (Exception e) {
					if (logger.isActivated()) {
						logger.error("Exception occurred", e);
					}
				}
			}
		}.start();
	}

	/**
	 * API disabled
	 */
	public void handleApiDisabled() {
		showDialogAndFinish(R.string.label_api_disabled);
	}

	/**
	 * Check whether file size exceeds the limit
	 * 
	 * @param size
	 *            of file
	 * @return {@code true} if file size limit is exceeded, otherwise {@code false}
	 */
	private static boolean isFileSizeExceeded(long size) {
		int maxSize = RcsSettings.getInstance().getMaxFileTransferSize() * 1024;
		return (maxSize > 0 && size > maxSize);
	}

	/**
	 * Check if file capacity is acceptable
	 * 
	 * @param fileSize
	 * @return FileSharingError or null if file capacity is acceptable
	 */
	private static FileSharingError isFileCapacityAcceptable(long fileSize) {
		boolean fileIsToBig = isFileSizeExceeded(fileSize);
		boolean storageIsTooSmall = fileSize > StorageUtils.getStorageFreeSpace(AndroidFactory.getApplicationContext());
		if (fileIsToBig) {
			if (logger.isActivated())
				logger.warn("File is too big, reject the File Transfer");
			return new FileSharingError(FileSharingError.MEDIA_SIZE_TOO_BIG);
		} else {
			if (storageIsTooSmall) {
				if (logger.isActivated())
					logger.warn("Not enough storage capacity, reject the File Transfer");
				return new FileSharingError(FileSharingError.NOT_ENOUGH_STORAGE_SPACE);
			}
		}
		return null;
	}

	/**
	 * Check if file size is less than maximum or then free space on disk
	 * 
	 * @param fileSize
	 * @return boolean
	 */
	private boolean isCapacityOk(long fileSize) {
		FileSharingError error = isFileCapacityAcceptable(fileSize);
		if (error != null) {
			switch (error.getErrorCode()) {
			case FileSharingError.MEDIA_SIZE_TOO_BIG:
				showDialogAndFinish(R.string.label_transfer_failed_too_big);
				break;
			case FileSharingError.NOT_ENOUGH_STORAGE_SPACE:
			default:
				showDialogAndFinish(R.string.label_transfer_failed_capacity_too_small);
			}
			return false;
		}
		return true;
	}

	/**
	 * API connected
	 */
	public void handleApiConnected() {
		try {
			// Get the file transfer session
			transferSession = messagingApi.getFileTransferSession(sessionId);
			if (transferSession == null) {
				// Session not found or expired
				showDialogAndFinish(R.string.label_session_has_expired);
				return;
			}
			transferSession.addSessionListener(fileTransferSessionListener);

			String size;
			if (fileSize != -1) {
				size = getString(R.string.label_file_size, " " + (fileSize / 1024), " Kb");
			} else {
				size = getString(R.string.label_file_size_unknown);
			}

			// Display transfer infos
			setTitle(R.string.title_recv_file_transfer);
			TextView from = (TextView) findViewById(R.id.from);
			from.setText(getString(R.string.label_from) + " " + remoteContact);
			TextView sizeTxt = (TextView) findViewById(R.id.image_size);
			sizeTxt.setText(size);
			TextView filenameTxt = (TextView) findViewById(R.id.filename);
			filenameTxt.setText(getString(R.string.label_file_name, filename));

			if (autoAccept) {
				// Auto accept. Check capacity
				isCapacityOk(fileSize);
				// Do not reject: already done by the stack
			} else {
				// @formatter:off

// The following code is intentionally commented to test the CORE.
// UI should check the file size to cancel if it is too big.
//				if (isCapacityOk(ReceiveFileTransfer.this, fileSize) == false) {
//					rejectInvitation();
//					return;
//				}
				
				// @formatter:on

				// Manual accept
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.title_recv_file_transfer);
				builder.setMessage(getString(R.string.label_from) + remoteContact + "\n" + size);
				builder.setCancelable(false);
				if (thumbnail != null) {
					Bitmap bitmap = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length);
					builder.setIcon(new BitmapDrawable(bitmap));
				} else if (fileType.equals("text/vcard")) {
					builder.setIcon(R.drawable.ri_contact_card_icon);
				} else {
					builder.setIcon(R.drawable.ri_file_transfer_icon);
				}
				builder.setPositiveButton(getString(R.string.label_accept), acceptBtnListener);
				builder.setNegativeButton(getString(R.string.label_decline), declineBtnListener);
				alertDialog = builder.show();
			}
		} catch (Exception e) {
			if (logger.isActivated()) {
				logger.error("Exception occurred", e);
			}
			showDialogAndFinish(R.string.label_api_failed);
		}
	}

	private void showDialogAndFinish(int msg) {
		showDialogAndFinish(getString(msg));
	}
	
	private void showDialogAndFinish(String msg) {
		if (fired.compareAndSet(false, true)) {
			Utils.ShowDialogAndFinish(ReceiveFileTransfer.this, msg);
		}
	}
	
	/**
	 * API disconnected
	 */
	public void handleApiDisconnected() {
		// Service has been disconnected
		showDialogAndFinish(R.string.label_api_disconnected);
	}

	/**
	 * Client is connected to the IMS
	 */
	public void handleImsConnected() {
	}

	/**
	 * Client is disconnected from the IMS
	 * 
	 * @param reason
	 *            Disconnection reason
	 */
	public void handleImsDisconnected(int reason) {
		// IMS has been disconnected
		showDialogAndFinish(R.string.label_ims_disconnected);
	}

	/**
	 * Accept button listener
	 */
	private OnClickListener acceptBtnListener = new OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			// Accept invitation
			acceptInvitation();
		}
	};

	/**
	 * Reject button listener
	 */
	private OnClickListener declineBtnListener = new OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			// Reject invitation
			rejectInvitation();

			// Exit activity
			finish();
		}
	};

	/**
	 * File transfer session event listener
	 */
	private IFileTransferEventListener fileTransferSessionListener = new IFileTransferEventListener.Stub() {
		// Session is started
		public void handleSessionStarted() {
			handler.post(new Runnable() {
				public void run() {
					TextView statusView = (TextView) findViewById(R.id.progress_status);
					statusView.setText("started");
				}
			});
		}

		// Session has been aborted
		public void handleSessionAborted(int reason) {
			showDialogAndFinish(R.string.label_sharing_aborted);
		}

		// Session has been terminated by remote
		public void handleSessionTerminatedByRemote() {
			showDialogAndFinish(R.string.label_sharing_terminated_by_remote);
		}

		// File transfer progress
		public void handleTransferProgress(final long currentSize, final long totalSize) {
			handler.post(new Runnable() {
				public void run() {
					updateProgressBar(currentSize, totalSize);
				}
			});
		}

		// File transfer error
		public void handleTransferError(final int error) {
			if (logger.isActivated()) {
				logger.warn("handleTransferError error=" + error);
			}
			switch (error) {
			case FileSharingError.MEDIA_SIZE_TOO_BIG:
				showDialogAndFinish(R.string.label_transfer_failed_too_big);
				break;
			case FileSharingError.NOT_ENOUGH_STORAGE_SPACE:
				showDialogAndFinish(R.string.label_transfer_failed_capacity_too_small);
				break;
			default:
				showDialogAndFinish(getString(R.string.label_transfer_failed, error));
				break;
			}
		}

		// File has been transferred
		public void handleFileTransfered(final String filename) {
			handler.post(new Runnable() {
				public void run() {
					TextView statusView = (TextView) findViewById(R.id.progress_status);
					statusView.setText("transferred");
					TextView filenameTxt = (TextView) findViewById(R.id.filename);
					filenameTxt.setText(getString(R.string.label_file_name, filename));
					// Make sure progress bar is at the end
					ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
					progressBar.setProgress(progressBar.getMax());

					if (MimeManager.isVCardType(fileType)) {
						// Show the transferred vCard
						File file = new File(filename);
						Uri uri = Uri.fromFile(file);
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(uri, "text/x-vcard");
						startActivity(intent);
					} else {
						if (MimeManager.isImageType(fileType)) {
							// Show the transferred image
							Utils.showPictureAndExit(ReceiveFileTransfer.this, filename);
						}
					}
				}
			});
		}

		public void handleFileTransferPaused() throws RemoteException {
			TextView statusView = (TextView) findViewById(R.id.progress_status);
			statusView.setText("Paused");
		}

		@Override
		public void handleFileTransferResumed() throws RemoteException {
			TextView statusView = (TextView) findViewById(R.id.progress_status);
			statusView.setText("Resumed");
		}
	};

	/**
	 * Show the transfer progress
	 * 
	 * @param currentSize
	 *            Current size transferred
	 * @param totalSize
	 *            Total size to be transferred
	 */
	private void updateProgressBar(long currentSize, long totalSize) {
		TextView statusView = (TextView) findViewById(R.id.progress_status);
		ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);

		String value = "" + (currentSize / 1024);
		if (totalSize != 0) {
			value += "/" + (totalSize / 1024);
		}
		value += " Kb";
		statusView.setText(value);

		if (currentSize != 0) {
			double position = ((double) currentSize / (double) totalSize) * 100.0;
			progressBar.setProgress((int) position);
		} else {
			progressBar.setProgress(0);
		}
	}

	/**
	 * Add file transfer notification
	 * 
	 * @param context
	 *            Context
	 * @param invitation
	 *            Intent invitation
	 */
	public static void addFileTransferInvitationNotification(Context context, Intent invitation) {
		// Instantiate settings
		RcsSettings.createInstance(context);

		// Create notification
		Intent intent = new Intent(invitation);
		intent.setClass(context, ReceiveFileTransfer.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Notification notif = new Notification(R.drawable.ri_notif_file_transfer_icon,
				context.getString(R.string.title_recv_file_transfer), System.currentTimeMillis());
		notif.flags = Notification.FLAG_AUTO_CANCEL;
		notif.setLatestEventInfo(context, context.getString(R.string.title_recv_file_transfer),
				context.getString(R.string.label_from) + " " + Utils.formatCallerId(invitation), contentIntent);

		// Set ringtone
		String ringtone = RcsSettings.getInstance().getFileTransferInvitationRingtone();
		if (!TextUtils.isEmpty(ringtone)) {
			notif.sound = Uri.parse(ringtone);
		}

		// Set vibration
		if (RcsSettings.getInstance().isPhoneVibrateForFileTransferInvitation()) {
			notif.defaults |= Notification.DEFAULT_VIBRATE;
		}

		// Send notification
		String sessionId = invitation.getStringExtra("sessionId");
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(sessionId, Utils.NOTIF_ID_FT, notif);
	}

	/**
	 * Remove file transfer notification
	 * 
	 * @param context
	 *            Context
	 * @param sessionId
	 *            Session ID
	 */
	public static void removeFileTransferNotification(Context context, String sessionId) {
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(sessionId, Utils.NOTIF_ID_FT);
	}

	/**
	 * Quit the session
	 */
	private void quitSession() {
		if (fired.compareAndSet(false, true)) {
			// Stop session
			new Thread() {
				public void run() {
					try {
						if (transferSession != null) {
							transferSession.removeSessionListener(fileTransferSessionListener);
							transferSession.cancelSession();
						}
					} catch (Exception e) {
					}
					transferSession = null;
				}
			}.start();
			if (alertDialog != null && alertDialog.isShowing()) {
				alertDialog.dismiss();
				alertDialog = null;
			}
			// Exit activity
			finish();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			// Quit the session
			quitSession();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = new MenuInflater(getApplicationContext());
		inflater.inflate(R.menu.menu_ft, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_close_session:
			// Quit the session
			quitSession();
			break;
		}
		return true;
	}

	/**
	 * Pause button listener
	 */
	private android.view.View.OnClickListener btnPauseListener = new android.view.View.OnClickListener() {
		@Override
		public void onClick(View arg0) {
			Button resumeBtn = (Button) findViewById(R.id.resume_btn);
			resumeBtn.setEnabled(true);
			Button pauseBtn = (Button) findViewById(R.id.pause_btn);
			pauseBtn.setEnabled(false);

			try {
				transferSession.pauseSession();
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Exception occurred", e);
				}
				showDialogAndFinish(R.string.label_invitation_failed);
			}
		}
	};

	/**
	 * Resume button listener
	 */
	private android.view.View.OnClickListener btnResumeListener = new android.view.View.OnClickListener() {
		@Override
		public void onClick(View arg0) {
			Button resumeBtn = (Button) findViewById(R.id.resume_btn);
			resumeBtn.setEnabled(false);
			Button pauseBtn = (Button) findViewById(R.id.pause_btn);
			pauseBtn.setEnabled(true);

			try {
				transferSession.resumeSession();
			} catch (RemoteException e) {
				if (logger.isActivated()) {
					logger.error("Exception occurred", e);
				}
				showDialogAndFinish(R.string.label_invitation_failed);
			}
		}
	};
}
