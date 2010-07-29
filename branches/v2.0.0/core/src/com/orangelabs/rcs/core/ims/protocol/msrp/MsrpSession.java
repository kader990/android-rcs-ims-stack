/*******************************************************************************
 * Software Name : RCS IMS Stack
 * Version : 2.0.0
 * 
 * Copyright � 2010 France Telecom S.A.
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
package com.orangelabs.rcs.core.ims.protocol.msrp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

import com.orangelabs.rcs.utils.logger.Logger;

/**
 * MSRP session
 * 
 * @author jexa7410
 */
public class MsrpSession {
	/**
	 * Failure report option
	 */
	private boolean failureReportOption = true;

	/**
	 * Success report option
	 */
	private boolean successReportOption = false;

	/**
	 * MSRP connection
	 */
	private MsrpConnection connection = null;
	
	/**
	 * From path
	 */
	private String from = null;
	
	/**
	 * To path
	 */
	private String to = null;
	
	/**
	 * Cancel transfer flag
	 */
	private boolean cancelTransfer = false;

	/**
	 * Semaphore object used to wait a response
	 */
	private Object respSemaphore = new Object();

	/**
	 * Semaphore object used to wait a report
	 */
	private Object reportSemaphore = new Object();

	/**
	 * Received chunks
	 */
	private DataChunks receivedChunks = new DataChunks();	
	
    /**
     * MSRP event listener
     */
    private MsrpEventListener msrpEventListener = null;

    /**
	 * The logger
	 */
	private Logger logger = Logger.getLogger(this.getClass().getName());

	/**
	 * Constructor
	 */
	public MsrpSession() {
	}
	
	/**
	 * Is failure report requested
	 * 
	 * @return Boolean
	 */
	public boolean isFailureReportRequested() {
		return failureReportOption;
	}

	/**
	 * Set the failure report option
	 * 
	 * @param failureReportOption Boolean flag
	 */
	public void setFailureReportOption(boolean failureReportOption) {
		this.failureReportOption = failureReportOption;
	}

	/**
	 * Is success report requested
	 * 
	 * @return Boolean
	 */
	public boolean isSuccessReportRequested() {
		return successReportOption;
	}

	/**
	 * Set the success report option
	 * 
	 * @param failureReportOption Boolean flag
	 */
	public void setSuccessReportOption(boolean successReportOption) {
		this.successReportOption = successReportOption;
	}	

	/**
	 * Set the MSRP connection
	 * 
	 * @param connection MSRP connection
	 */
	public void setConnection(MsrpConnection connection) {
		this.connection = connection;
	}

	/**
	 * Returns the MSRP connection
	 * 
	 * @return MSRP connection
	 */
	public MsrpConnection getConnection() {
		return connection;
	}
	
	/**
	 * Get the MSRP event listener
	 * 
	 * @return Listener 
	 */
	public MsrpEventListener getMsrpEventListener() {
		return msrpEventListener;
	}
	
	/**
	 * Add a MSRP event listener
	 * 
	 * @param listener Listener 
	 */
	public void addMsrpEventListener(MsrpEventListener listener) {
		this.msrpEventListener = listener;		
	}
	
	/**
	 * Returns the From path
	 * 
	 * @return From path
	 */
	public String getFrom() {
		return from;
	}

	/**
	 * Set the From path
	 * 
	 * @param from From path
	 */
	public void setFrom(String from) {
		this.from = from;
	}

	/**
	 * Returns the To path
	 *  
	 * @return To path
	 */
	public String getTo() {
		return to;
	}

	/**
	 * Set the To path
	 * 
	 * @param to To path
	 */
	public void setTo(String to) {
		this.to = to;
	}

	/**
	 * Close the session
	 */
	public void close() {
		if (logger.isActivated()) {
			logger.debug("Close session");
		}

		// Cancel transfer
		cancelTransfer = true;

		// Close the connection
		if (connection != null) {
			connection.close();
		}
		
		// Unblock wait response
		synchronized(respSemaphore) {
			respSemaphore.notify();
		}

		// Unblock wait report
		synchronized(reportSemaphore) {
			reportSemaphore.notify();
		}
	}

	/**
	 * Send chunks
	 * 
	 * @param inputStream Input stream
	 * @param contentType Content type to be sent
	 * @param totalSize Total size of content
	 * @throws MsrpException
	 */
	public void sendChunks(InputStream inputStream, String contentType, long totalSize) throws MsrpException {
		if (logger.isActivated()) {
			logger.info("Send content (" + contentType + ")");
		}
		
		if (from == null) {
			throw new MsrpException("From not set");
		}
		
		if (to == null) {
			throw new MsrpException("To not set");
		}
		
		if (connection == null) {
			throw new MsrpException("No connection set");
		}
		
		// Set common MSRP headers
		Hashtable<String, String> headers = new Hashtable<String, String>();
		headers.put(MsrpConstants.HEADER_FROM_PATH, from);
		headers.put(MsrpConstants.HEADER_TO_PATH, to);
		headers.put(MsrpConstants.HEADER_MESSAGE_ID, generateId());

		// Reset cancel transfer flag 
		cancelTransfer = false;
		
		// Send content over MSRP 
		try {
			byte data[] = new byte[MsrpConstants.CHUNK_MAX_SIZE];
			long firstByte = 1;
			long lastByte = 0;
			
			// Send data chunk by chunk
			for (int i = inputStream.read(data);(!cancelTransfer) & (i>-1); i=inputStream.read(data)) {
				// Update upper byte range
				lastByte += i;

				// Send a chunk
				sendMsrpSendRequest(generateId(), headers, contentType, i, data, firstByte, lastByte, totalSize);

				// Update lower byte range
				firstByte += i;
				
				if (!cancelTransfer) {
					// Notify event listener
					msrpEventListener.msrpTransferProgress(lastByte, totalSize);
				}
			}
			
			if (cancelTransfer) {
				// Transfer has been aborted
				return;
			}

			// Test if waiting report is needed
			if (successReportOption) {
				if (logger.isActivated()) {
					logger.debug("Wait final report");
				}
				
				// Wait report
				synchronized(reportSemaphore) {
					try {
						reportSemaphore.wait();
					} catch (InterruptedException e) {}
				}
				
				// TODO : test the status of the received report
			}
			
			// Notify event listener
			msrpEventListener.msrpDataTransfered();
		} catch(Exception e) {
			throw new MsrpException(e.getMessage());
		}
	}

	/**
	 * Generate a unique ID
	 * 
	 * @return ID
	 */
	private String generateId() {
		Random r = new Random();
		int i = r.nextInt();
		return Integer.toHexString(i);
	}
	
	/**
	 * Send MSRP SEND request
	 * 
	 * @param transactionId Transaction ID
	 * @param headers MSRP headers
	 * @param contentType Content type 
	 * @param dataSize Data chunk size
	 * @param data Data chunk
	 * @param firstByte First byte range
	 * @param lastByte Last byte range
	 * @param totalSize Total size
	 * @throws IOException
	 */
	private void sendMsrpSendRequest(String txId, Hashtable<String, String> headers, String contentType,
			int dataSize, byte data[], long firstByte, long lastByte, long totalSize) throws IOException {

		boolean isLastChunk = (lastByte == totalSize);
		
		// Create request
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(4000);
		buffer.reset();
		buffer.write(MsrpConstants.MSRP_PROTOCOL.getBytes());
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write(txId.getBytes());
		buffer.write((" " + MsrpConstants.METHOD_SEND).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		for (Enumeration<String> elt = headers.keys(); elt.hasMoreElements(); buffer.write(MsrpConstants.NEW_LINE.getBytes())) {
			String key = elt.nextElement();
			String value = headers.get(key);
			String header = key + ": " + value;
			buffer.write(header.getBytes());
		}
		
		// Write byte range
		String byteRange = MsrpConstants.HEADER_BYTE_RANGE + ": " + firstByte + "-" + lastByte + "/" + totalSize + MsrpConstants.NEW_LINE;
		buffer.write(byteRange.getBytes());
		
		// Write content type
		String content = MsrpConstants.HEADER_CONTENT_TYPE + ": " + contentType + MsrpConstants.NEW_LINE; 
		buffer.write(content.getBytes());
		
		// Write optional MSRP headers
		if (!failureReportOption) {
			String header = MsrpConstants.HEADER_FAILURE_REPORT + ": no" + MsrpConstants.NEW_LINE;
			buffer.write(header.getBytes());
		}
		if (successReportOption) {
			String header = MsrpConstants.HEADER_SUCCESS_REPORT + ": yes" + MsrpConstants.NEW_LINE;
			buffer.write(header.getBytes());
		}

		// Write data
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		buffer.write(data, 0, dataSize);
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		buffer.write(MsrpConstants.END_MSRP_MSG.getBytes());
		buffer.write(txId.getBytes());
		if (isLastChunk) {
			// '$' -> last chunk
			buffer.write(MsrpConstants.FLAG_LAST_CHUNK);
		} else {
			// '+' -> more chunk
			buffer.write(MsrpConstants.FLAG_MORE_CHUNK);
		}
		buffer.write(MsrpConstants.NEW_LINE.getBytes());

		// Send chunk
		connection.sendChunk(buffer.toByteArray());
		
		// Test if waiting response is needed
		if (failureReportOption) {
			if (logger.isActivated()) {
				logger.debug("Wait request response");
			}

			synchronized(respSemaphore) {
				try {
					respSemaphore.wait(MsrpManager.TIMEOUT * 1000);
				} catch (InterruptedException e) {}
			}
		}
	}

	/**
	 * Send MSRP response
	 * 
	 * @param code Response code
	 * @param txId Transaction ID
	 * @param headers MSRP headers
	 * @throws IOException
	 */
	private void sendMsrpResponse(String code, String txId, Hashtable<String, String> headers) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(4000);
		buffer.write(MsrpConstants.MSRP_PROTOCOL .getBytes());
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write(txId.getBytes());
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write(code.getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		buffer.write(MsrpConstants.HEADER_FROM_PATH.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write((headers.get(MsrpConstants.HEADER_TO_PATH)).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		buffer.write(MsrpConstants.HEADER_TO_PATH.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write((headers.get(MsrpConstants.HEADER_FROM_PATH)).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		buffer.write(MsrpConstants.HEADER_BYTE_RANGE.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write((headers.get(MsrpConstants.HEADER_BYTE_RANGE)).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		buffer.write(MsrpConstants.END_MSRP_MSG.getBytes());
		buffer.write(txId.getBytes());
		buffer.write(MsrpConstants.FLAG_LAST_CHUNK);
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		connection.sendChunk(buffer.toByteArray());
		buffer.close();
	}

	/**
	 * Send MSRP REPORT request
	 * 
	 * @param txId Transaction ID
	 * @param headers MSRP headers
	 * @throws IOException
	 */
	private void sendMsrpReportRequest(String txId, Hashtable<String, String> headers, long lastByte, long totalSize) throws IOException {
		// Create request
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(4000);
		buffer.reset();
		buffer.write(MsrpConstants.MSRP_PROTOCOL.getBytes());
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write(txId.getBytes());
		buffer.write((" " + MsrpConstants.METHOD_REPORT).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());

		buffer.write(MsrpConstants.HEADER_MESSAGE_ID.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write((headers.get(MsrpConstants.HEADER_MESSAGE_ID)).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		buffer.write(MsrpConstants.HEADER_FROM_PATH.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write((headers.get(MsrpConstants.HEADER_TO_PATH)).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		buffer.write(MsrpConstants.HEADER_TO_PATH.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		buffer.write((headers.get(MsrpConstants.HEADER_FROM_PATH)).getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());

		buffer.write(MsrpConstants.HEADER_BYTE_RANGE.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		String byteRange = "1-" + lastByte + "/" + totalSize;
		buffer.write(byteRange.getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());
		
		buffer.write(MsrpConstants.HEADER_STATUS.getBytes());
		buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
		buffer.write(MsrpConstants.CHAR_SP);
		String status = "000 200 OK";
		buffer.write(status.getBytes());
		buffer.write(MsrpConstants.NEW_LINE.getBytes());

		buffer.write(MsrpConstants.END_MSRP_MSG.getBytes());
		buffer.write(txId.getBytes());
		buffer.write(MsrpConstants.FLAG_LAST_CHUNK);
		buffer.write(MsrpConstants.NEW_LINE.getBytes());

		// Send request
		connection.sendChunk(buffer.toByteArray());
		buffer.close();
		
		// Wait a little before closing the connection 
		try {
			Thread.sleep(1000);
		} catch(Exception e) {}
	}
	
	/**
	 * Receive MSRP SEND request
	 * 
	 * @param txId Transaction ID
	 * @param headers Request headers
	 * @param flag Continuation flag
	 * @param data Received data
	 * @param totalSize Total size of the content
	 * @throws IOException
	 */
	public void receiveMsrpSend(String txId, Hashtable<String, String> headers, int flag, byte[] data, long totalSize) throws IOException {
		// Receive a SEND request
		if (logger.isActivated()) {
			logger.debug("SEND request received (flag=" + flag + ", transaction=" + txId + ", data size=" + data.length + ")");
		}
		
		// Test if a failure report is needed
		boolean failureReportNeeded = true;
		String failureHeader = headers.get(MsrpConstants.HEADER_FAILURE_REPORT);
		if ((failureHeader != null) && failureHeader.equalsIgnoreCase("no")) {
			failureReportNeeded = false;
		}
		
		// Send MSRP response if requested
		if (failureReportNeeded) {
			sendMsrpResponse(MsrpConstants.RESPONSE_OK + " " + MsrpConstants.COMMENT_OK, txId, headers);
		}
		
		// Save received data chunk
		receivedChunks.addChunk(data);
		
		// Check the continuation flag
		if (flag == MsrpConstants.FLAG_LAST_CHUNK) {
			// Transfer terminated
			if (logger.isActivated()) {
				logger.info("Transfer terminated");
			}

			// Test if a success report is needed
			boolean successReportNeeded = false;
			String reportHeader = headers.get(MsrpConstants.HEADER_SUCCESS_REPORT);
			if ((reportHeader != null) && reportHeader.equalsIgnoreCase("yes")) {
				successReportNeeded = true;
			}
			
			// Read the received content
			byte[] dataContent = receivedChunks.getReceivedData();
			receivedChunks.resetCache();
			
			// Send MSRP report if requested
			if (successReportNeeded) {
				sendMsrpReportRequest(txId, headers, dataContent.length, totalSize);
			}
			
			// Read content type
			String contentTypeHeader = headers.get(MsrpConstants.HEADER_CONTENT_TYPE);
			
			// Notify event listener
			msrpEventListener.msrpDataReceived(dataContent, contentTypeHeader);
		} else
		if (flag == MsrpConstants.FLAG_ABORT) {
			// Transfer aborted
			if (logger.isActivated()) {
				logger.info("Transfer aborted");
			}

			// Notify event listener
			msrpEventListener.msrpTransferAborted();			
		} else
		if (flag == MsrpConstants.FLAG_MORE_CHUNK) {
			// Transfer in progress
			if (logger.isActivated()) {
				logger.info("Transfer in progress...");
			}

			// Notify event listener
			msrpEventListener.msrpTransferProgress(receivedChunks.getCurrentSize(), totalSize);
		}
	}

	/**
	 * Receive MSRP response
	 * 
	 * @param code Response code
	 * @param txId Transaction ID
	 * @param headers MSRP headers
	 */
	public void receiveMsrpResponse(int code, String txId, Hashtable<String, String> headers) {
		if (logger.isActivated()) {
			logger.info("Response received (code=" + code + ", transaction=" + txId + ")");
		}
		
		// Unblock wait response
		synchronized(respSemaphore) {
			respSemaphore.notify();
		}
		
		if (code != 200) {
            // Notify event listener
			msrpEventListener.msrpTransferError(code + " response received");
		}
	}
	
	/**
	 * Receive MSRP REPORT request
	 * 
	 * @param txId Transaction ID
	 * @param headers MSRP headers
	 * @throws IOException
	 */
	public void receiveMsrpReport(String txId, Hashtable<String, String> headers) throws IOException {
		if (logger.isActivated()) {
			logger.info("REPORT request received (transaction=" + txId + ")");
		}
		
		// Unblock wait report
		synchronized(reportSemaphore) {
			reportSemaphore.notify();
		}
	}
}
