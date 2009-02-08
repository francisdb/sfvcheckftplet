/*
 * Copyright 2009 Francis De Brabandere
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.code.sfvcheckftplet.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.CRC32;

import org.apache.ftpserver.ftplet.FtpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.sfvcheckftplet.SessionWriter;
import com.google.code.sfvcheckftplet.Status;


public class CrcService {
	
	private static final Logger logger = LoggerFactory.getLogger(CrcService.class);
	
	private CrcCache crcCache;

	public CrcService() {
		this.crcCache = new CrcCache();
	}
	
	public void init(){
		crcCache.init();
	}
	
	public void printStatus(SessionWriter writer) throws IllegalStateException, FtpException{
		crcCache.printStatus(writer);
	}
	
	public void shutdown(){
		crcCache.shutdown();
	}
	
	public Status checkNewFile(File file) throws IOException{
		Status status;
		//logger.debug("Testing file using " + sfv.getName());
		Map<String, String> files = crcCache.getCrcInfo(file.getParentFile());
		if(files != null) {
			String sfvHex = files.get(file.getName());
			if (sfvHex != null) {
				long checksum = checksum(file, false);
				if (hexToLong(sfvHex) == checksum) {
					status = Status.OK;
					// session.write(new
					// DefaultFtpReply(replyCode,"OK "+file.getName() + " " +
					// sfvHex));
				} else {
					logger.warn("FAIL " + longToHex(checksum) + " != " + sfvHex);
					status = Status.FAIL;
					// session.write(new
					// DefaultFtpReply(replyCode,"FAIL "+file.getName()));
				}
			} else {
				status = Status.UNKNOWN;
				// session.write(new DefaultFtpReply(replyCode,
				// "File not in sfv: "+file.getName()));
			}
		}else{
			// nos sfv file
			logger.debug("No sfv file for "+file.getParentFile().getAbsolutePath());
			status = Status.UNKNOWN;
		}
		return status;
	}
	
	public Status rescanFile(SessionWriter writer, File file, String sfvHex, boolean forced)
			throws IOException, FtpException {
		Status status = null;
		if (file.exists()) {
			long checksum = checksum(file, forced);
			if (hexToLong(sfvHex) == checksum) {
				status = Status.OK;
				writer.println("File: " + file.getName() + " " + sfvHex);
			} else {
				logger.warn("FAIL " + longToHex(checksum) + " != " + sfvHex);
				status = Status.FAIL;
				writer.println("FAIL " + file.getName());
				File renamed = new File(file.getParentFile(), file.getName() + ".bad");
				if (renamed.exists()) {
					renamed.delete();
				}
				file.renameTo(renamed);
			}
		} else {
			status = Status.MISSING;
		}
		return status;
	}
	
	/**
	 * Performs a checksum operation on a file
	 * @param file
	 * @param force force a rescan of the file (skip cache)
	 * @return the crc checksum
	 * @throws IOException
	 */
	public long checksum(File file, boolean force) throws IOException {
		Long checksum = null;
		if(!force){
			checksum = crcCache.getFileCrc(file);
		}else{
			logger.debug("Forced (re)calculation of checksum");
		}
		if(checksum == null){
			long startTime = System.currentTimeMillis();
			InputStream fis = null;
			try {
				// Compute CRC32 checksum
				fis = new FileInputStream(file);
				CRC32 crc32 = new CRC32();
				byte[] buf = new byte[2048];
				int len;
				while ((len = fis.read(buf)) >= 0) {
					crc32.update(buf, 0, len);
				}
				checksum = crc32.getValue();
			} finally {
				if (fis != null) {
					fis.close();
				}
			}
			long elapse = System.currentTimeMillis() - startTime;
			logger.debug("Calculating crc for "+file.getName()+" took "+elapse+" msec");
			crcCache.putFileCrc(file, checksum);
		}
		
		return checksum;
	}
	
	public Map<String, String> getCrcInfo(File folder){
		return crcCache.getCrcInfo(folder);
	}
	
	public Map<String, String> parseSfv(File sfvFile, boolean force) throws IOException {
		Map<String, String> files = null;
		if(!force){
			files = crcCache.getCrcInfo(sfvFile.getParentFile());
		}else{
			logger.debug("Forced (re)parse of sfv");
		}
		
		if(files == null){
			files = new LinkedHashMap<String, String>();
			Scanner scanner = null;
			try {
				scanner = new Scanner(sfvFile);
				String strLine;
				while (scanner.hasNextLine()) {
					strLine = scanner.nextLine();
					if (!strLine.startsWith(";") && !(strLine.length() == 0)) {
						int last = strLine.lastIndexOf(' ');
						String file = strLine.substring(0, last);
						String hex = strLine.substring(last + 1);
						files.put(file, hex);
					}
				}
			} finally {
				if (scanner != null) {
					scanner.close();
				}
			}
			crcCache.putCrcInfo(sfvFile.getParentFile(), files);
		}
		return files;
	}
	
	public void clearData(File file){
		if(FileTools.isSfv(file)){
			crcCache.removeCrcInfo(file.getParentFile());
		}
		crcCache.removeFileCrc(file);
	}
	
	
	private long hexToLong(String hex){
		return Long.valueOf(hex, 16);
	}
	
	private String longToHex(Long hex){
		String val = Long.toHexString(hex);
		// TOOD find faster method, compare speed with numberformat?
		if(val.length()<8){
			StringBuilder str = new  StringBuilder();
			for(int i=0;i<8-val.length();i++){
				str.append("0");
			}
			str.append(val);
			val = str.toString();
		}
		return val;
	}
}
