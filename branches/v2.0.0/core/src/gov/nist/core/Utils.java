/*******************************************************************************
 * Conditions Of Use
 * 
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), an agency of the Federal Government.
 * Pursuant to title 15 Untied States Code Section 105, works of NIST
 * employees are not subject to copyright protection in the United States
 * and are considered to be in the public domain.  As a result, a formal
 * license is not needed to use the software.
 * 
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof, including but
 * not limited to the correctness, accuracy, reliability or usefulness of
 * the software.
 * 
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement
 ******************************************************************************/
package gov.nist.core;

/**
 * A set of utilities
 */
public class Utils {
	/**
	 * Compares two strings lexicographically, ignoring case considerations
	 * 
	 * @param s1 String to compare
	 * @param s2 String to compare.
	 * @return 1,-1,0 as in compareTo method
	 */
	public static int compareToIgnoreCase(String s1, String s2) {
		String su1 = s1.toUpperCase();
		String su2 = s2.toUpperCase();
		return su1.compareTo(su2);
	}

	/**
	 * Is two string equals, ignoring case considerations
	 * 
	 * @param s1 String to compare
	 * @param s2 String to compare.
	 * @return Boolean
	 */
	public static boolean equalsIgnoreCase(String s1, String s2) {
		return s1.toLowerCase().equals(s2.toLowerCase());
	}
}
