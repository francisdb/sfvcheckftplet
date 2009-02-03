/**
 * 
 */
package com.google.code.sfvcheckftplet.service;

import java.io.File;
import java.io.IOException;

import org.apache.ftpserver.ftplet.FtpFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author francisdb
 *
 */
public class FileTools {
	
	private static final Logger logger = LoggerFactory.getLogger(FileTools.class);
	
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
	public static boolean createSymbolicLink(File source, File destination) throws IOException{
		boolean succes = true;
		Process process = Runtime.getRuntime().exec( new String[] { "ln", "-s", source.getAbsolutePath(), destination.getAbsolutePath() } );
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			succes = false;
			logger.error(e.getMessage(), e);
		}
		process.destroy();
		return succes;
	}

}
