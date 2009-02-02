/**
 * 
 */
package com.google.code.sfvcheckftplet.service;

import java.io.File;

import org.apache.ftpserver.ftplet.FtpFile;

/**
 * @author francisdb
 *
 */
public class FileTools {
	
	private static final String SFV_EXT = ".sfv";

	private FileTools() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	public static boolean isSfv(File file){
		return file.getName().toLowerCase().endsWith(SFV_EXT);
	}
	
	public static boolean isSfv(FtpFile file){
		return file.getName().toLowerCase().endsWith(SFV_EXT);
	}
}
