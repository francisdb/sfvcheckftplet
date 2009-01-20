package com.google.code.sfvcheckftplet;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SfvCheckFtpLet extends DefaultFtplet {

	private static final Logger logger = LoggerFactory.getLogger(SfvCheckFtpLet.class);
	
	private static final String SFV_EXT = ".sfv";
	
	private static final int SITE_RESPONSE = 200;
	private static final int UPLOAD_RESPONSE = 226;
	
	
	@Override
	public FtpletResult onSite(FtpSession session, FtpRequest request) throws FtpException, IOException {
		String argument = request.getArgument().toUpperCase();
		if("RESCAN".equals(argument)){
			return onSiteRescan(session, request);
		}
		return super.onSite(session, request);
	}
	
	public FtpletResult onSiteRescan(FtpSession session, FtpRequest request) throws FtpException, IOException {
		String filePath = session.getUser().getHomeDirectory()
		+ session.getFileSystemView().getWorkingDirectory().getAbsolutePath();
		File work = new File(filePath);
		rescan(session, SITE_RESPONSE, work);
		return FtpletResult.SKIP;
	}
	
	@Override
	public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		logger.trace(request.getCommand());
		logger.trace(request.getArgument());
		String filePath = session.getUser().getHomeDirectory()
				+ session.getFileSystemView().getFile(request.getArgument()).getAbsolutePath();

		File file = new File(filePath);
		if(file.getName().endsWith(SFV_EXT)){
			rescan(session, UPLOAD_RESPONSE, file.getParentFile());
		}else{
			handleFile(session, UPLOAD_RESPONSE, file);
		}
		return super.onUploadEnd(session, request);
	}
	
	private void rescan(FtpSession session, int replyCode, File folder) throws IOException, FtpException{
		session.write(new DefaultFtpReply(SITE_RESPONSE, "Rescanning files..."));
		session.write(new DefaultFtpReply(SITE_RESPONSE, ""));
		File sfv = findSfv(folder);
		if (sfv != null) {
			Map<String, String> filesToCheck = parseSfv(sfv);
			for(Entry<String,String> entry:filesToCheck.entrySet()){
				File toCheck = new File(folder, entry.getKey());
				handleFileNew(session, replyCode, toCheck, entry.getValue());
			}
		}
		// TODO implement
		session.write(new DefaultFtpReply(SITE_RESPONSE, ""));
		session.write(new DefaultFtpReply(SITE_RESPONSE, ""));
		session.write(new DefaultFtpReply(SITE_RESPONSE, " Passed : 2"));
		session.write(new DefaultFtpReply(SITE_RESPONSE, " Failed : 0"));
		session.write(new DefaultFtpReply(SITE_RESPONSE, " Missing: 0"));
		session.write(new DefaultFtpReply(SITE_RESPONSE, "  Total : 2"));
		session.write(new DefaultFtpReply(SITE_RESPONSE, "Command Successful."));
	}
	
	private void handleFileNew(FtpSession session, int replyCode, File file, String expectedHex) throws IOException,
			FtpException {
		if (file.exists()) {

			long checksum = doChecksum(file);
			String hex = Long.toString(checksum, 16);
			if (expectedHex.equals(hex)) {
				session.write(new DefaultFtpReply(replyCode, "File: " + file.getName() + " " + hex));
			} else {
				session.write(new DefaultFtpReply(replyCode, "FAIL " + file.getName()));
				File renamed = new File(file.getParentFile(), file.getName() + ".bad");
				if (renamed.exists()) {
					renamed.delete();
				}
				file.renameTo(renamed);
			}

		} else {
			session.write(new DefaultFtpReply(replyCode, "File not found: " + file.getName()));
		}
	}
	
	private void handleFile(FtpSession session, int replyCode, File file) throws IOException, FtpException{
		if(file.exists()){
			File sfv = findSfv(file.getParentFile());
			if (sfv != null) {
				logger.debug("Testing file using " + sfv.getName());
				Map<String, String> files = parseSfv(sfv);
				String sfvHex = files.get(file.getName());
				if(sfvHex != null){
					long checksum = doChecksum(file);
					String hex = Long.toString(checksum, 16);
					if(sfvHex.equals(hex)){
						session.write(new DefaultFtpReply(replyCode,"OK "+file.getName() + " " + hex));
					}else{
						session.write(new DefaultFtpReply(replyCode,"FAIL "+file.getName()));
						File renamed = new File(file.getParentFile(), file.getName()+".bad");
						if(renamed.exists()){
							renamed.delete();
						}
						file.renameTo(renamed);
					}
				}else{
					session.write(new DefaultFtpReply(replyCode, "File not in sfv: "+file.getName()));
				}
			}
		}else{
			session.write(new DefaultFtpReply(replyCode, "File not found: "+file.getName()));
		}
	}

	private File findSfv(File folder) {
		File sfv = null;
		File[] files = folder.listFiles(new SfvFileFilter());
		if(files != null){
			for (File curFile : files) {
				// TODO handle more than one sfv?
				sfv = curFile;
			}
		}
		return sfv;
	}

	private Map<String, String> parseSfv(File sfvFile) throws IOException {
		Map<String, String> files = new LinkedHashMap<String, String>();
		FileInputStream fstream = null;
		try {
			// Open the file that is the first
			// command line parameter
			fstream = new FileInputStream(sfvFile);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				if(!strLine.startsWith(";") && !strLine.isEmpty()){
					int last = strLine.lastIndexOf(' ');
					String file = strLine.substring(0, last);
					String hex = strLine.substring(last+1);
					files.put(file, hex);
				}
			}
		} finally {
			if(fstream != null){
				fstream.close();
			}
		}
		return files;
	}

	private long doChecksum(File file) throws IOException {
		long checksum = -1;
		CheckedInputStream cis = null;
		InputStream fis = null;
		try {
			// Computer CRC32 checksum
			fis = new FileInputStream(file);
			cis = new CheckedInputStream(fis, new CRC32());
			byte[] buf = new byte[128];
			while (cis.read(buf) >= 0) {
				// nothing to do, just read
			}
			checksum = cis.getChecksum().getValue();
		} finally {
			if (fis != null) {
				fis.close();
			}
		}
		return checksum;
	}
	
	private static final class NoFolderOrSfvFileFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isFile() && !pathname.getName().endsWith(SFV_EXT);
		}
	}
	
	private static final class SfvFileFilter implements FileFilter {
		@Override
		public boolean accept(File pathname) {
			return pathname.isFile() && pathname.getName().endsWith(SFV_EXT);
		}
	}
	
}