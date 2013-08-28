package com.orangelabs.rcs.core.ims.service.ipcall;

import com.orangelabs.rcs.core.ims.service.ImsServiceError;
import com.orangelabs.rcs.core.ims.service.ImsSessionBasedServiceError;

/**
 * IPCall error
 * 
 * @author opob7414
 */
public class IPCallError extends ImsSessionBasedServiceError {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Video renderer is not initialized
	 */
	public final static int VIDEO_RENDERER_NOT_INITIALIZED = IPCALL_ERROR_CODES + 1;
	
	/**
	 * Video player has failed (e.g. video player failure)
	 */
	public final static int VIDEO_STREAMING_FAILED = IPCALL_ERROR_CODES + 2;
	
	/**
	 * Unsupported video type (e.g. codec not supported)
	 */
	public final static int UNSUPPORTED_VIDEO_TYPE = IPCALL_ERROR_CODES + 3;
	
	/**
	 * Audio renderer is not initialized
	 */
	public final static int AUDIO_RENDERER_NOT_INITIALIZED = IPCALL_ERROR_CODES + 4;
	
	/**
	 * Audio player has failed (e.g. audio player failure)
	 */
	public final static int AUDIO_STREAMING_FAILED = IPCALL_ERROR_CODES + 5;
	
	/**
	 * Unsupported audio type (e.g. codec not supported)
	 */
	public final static int UNSUPPORTED_AUDIO_TYPE = IPCALL_ERROR_CODES + 6;
	
	/**
	 * Audio player is not initialized
	 */
	public final static int AUDIO_PLAYER_NOT_INITIALIZED = IPCALL_ERROR_CODES + 7;
	
	/**
	 * Video player is not initialized
	 */
	public final static int VIDEO_PLAYER_NOT_INITIALIZED = IPCALL_ERROR_CODES + 8;

	/**
     * Constructor
     *
     * @param error Error
     */
	public IPCallError(ImsServiceError error) {
		super(error);
	}
	
	/**
	 * Constructor
	 * 
	 * @param code Error code
	 */
	public IPCallError(int code) {
		super(code);
	}
	
	/**
	 * Constructor
	 * 
	 * @param code Error code
	 * @param msg Detail message 
	 */
	public IPCallError(int code, String msg) {
		super(code, msg);
	}
}
