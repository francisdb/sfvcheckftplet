package com.google.code.sfvcheckftplet;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SfvCheckFtpLet extends DefaultFtplet {

	private static final Logger logger = LoggerFactory.getLogger(SfvCheckFtpLet.class);
	
	private static final String SFV_EXT = ".sfv";
	
	private static final int SITE_RESPONSE = 200;
	private static final int TRANSFER_COMPLETE_RESPONSE = 226;
	
	private CacheManager manager;
	private Cache cache;
	
	@Override
	public void init(FtpletContext ftpletContext) throws FtpException {
		// TODO set shutdown hook property, see manager.shutdown()
		URL url = getClass().getResource("/ehcache-crc.xml");
		CacheManager manager = new CacheManager(url);

		manager.addCache("crcCache");
		cache = manager.getCache("crcCache");
		String[] cacheNames = manager.getCacheNames();
		
		for(String name:cacheNames){
			Cache cache = manager.getCache(name);
			System.out.println(cache.getName()+": "+cache.getSize()+" "+cache.getMemoryStoreSize()+" "+cache.getDiskStoreSize());
			List<?> keys = cache.getKeys();
			for(Object key:keys){
				System.out.println("  - "+key.toString());
			}
		}
	}
	
	@Override
	public void destroy() {
		manager.shutdown();
	}
	
	
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
		rescan(session, SITE_RESPONSE, work, true);
		return FtpletResult.SKIP;
	}
	
	private File realFile(FtpSession session, FtpFile ftpFile) throws FtpException{
		String filePath = session.getUser().getHomeDirectory() + ftpFile.getAbsolutePath();
		return new File(filePath);
	}
	
	private FtpFile ftpFile(FtpSession session, FtpRequest request) throws FtpException{
		return session.getFileSystemView().getFile(request.getArgument());
	}
	
	@Override
	public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		FtpFile ftpFile = ftpFile(session, request);
		File file = realFile(session, ftpFile);

		if(file.getName().endsWith(SFV_EXT)){
			rescan(session, TRANSFER_COMPLETE_RESPONSE, file.getParentFile(), false);
		}else{
			// TODO find a better place to handle this where we can generate output
			handleFile(session, TRANSFER_COMPLETE_RESPONSE, file);
		}
		return super.onUploadEnd(session, request);
	}
	
	@Override
	public FtpletResult onDeleteEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		cache.remove(session.getFileSystemView().getFile(request.getArgument()).getAbsolutePath());
		return super.onDeleteEnd(session, request);
	}
	
	private void rescan(FtpSession session, int replyCode, File folder, boolean forced) throws IOException, FtpException{
		File sfv = findSfv(folder);
		if (sfv != null) {
			session.write(new DefaultFtpReply(SITE_RESPONSE, "Rescanning files..."));
			session.write(new DefaultFtpReply(SITE_RESPONSE, ""));
			Map<String, String> filesToCheck = parseSfv(sfv);
			int count = filesToCheck.size();
			int found = 0;
			int failed = 0; 
			long totalSize = 0;
			for(Entry<String,String> entry:filesToCheck.entrySet()){
				File toCheck = new File(folder, entry.getKey());
				Status status = handleFileNew(session, replyCode, toCheck, entry.getValue(), forced);
				if(status == Status.OK){
					found++;
					totalSize += toCheck.length();
				}else if(status == Status.FAIL){
					failed++;
				}
			}
			float percentage = (float)found/count;
			int percentageInt = (int) Math.floor(percentage * 100.0f);
			removeProgressFiles(folder);
			if(percentageInt == 100){
				// TODO get data from id3 tag?
				totalSize = totalSize / (1024*1024);
				File file = new File(folder, "[SFV] - ( "+totalSize+"M "+count+"F - COMPLETE - Beat 2006 ) - [SFV]");
				file.createNewFile();
			}else{
				File file = new File(folder, progressBar(percentage)+" - "+percentageInt+"% Complete - [SFV]");
				file.createNewFile();
			}
			
			session.write(new DefaultFtpReply(SITE_RESPONSE, ""));
			session.write(new DefaultFtpReply(SITE_RESPONSE, ""));
			session.write(new DefaultFtpReply(SITE_RESPONSE, " Passed : "+found));
			session.write(new DefaultFtpReply(SITE_RESPONSE, " Failed : "+failed));
			session.write(new DefaultFtpReply(SITE_RESPONSE, " Missing: "+(count-failed-found)));
			session.write(new DefaultFtpReply(SITE_RESPONSE, "  Total : "+count));
			session.write(new DefaultFtpReply(SITE_RESPONSE, "Command Successful."));
		}else{
			session.write(new DefaultFtpReply(SITE_RESPONSE, "No sfv file found."));
		}

	}
	
	private String progressBar(float percentage) {
		int count = (int) Math.floor(percentage * 14.0f);
		StringBuilder builder = new StringBuilder("[");
		for (int i = 0; i < 14; i++) {
			if (i < count) {
				builder.append('#');
			} else {
				builder.append(':');
			}
		}
		builder.append(']');
		return builder.toString();
	}
	
	private Status handleFileNew(FtpSession session, int replyCode, File file, String sfvHex, boolean forced) throws IOException,
			FtpException {
		Status status= null;
		if (file.exists()) {
			long checksum = doChecksum(file, forced);
			if (hexToLong(sfvHex) == checksum) {
				status = Status.OK;
				removeMissingFile(file);
				session.write(new DefaultFtpReply(replyCode, "File: " + file.getName() + " " + sfvHex));
			} else {
				logger.warn("FAIL "+longToHex(checksum)+" != "+sfvHex);
				status = Status.FAIL;
				session.write(new DefaultFtpReply(replyCode, "FAIL " + file.getName()));
				File renamed = new File(file.getParentFile(), file.getName() + ".bad");
				if (renamed.exists()) {
					renamed.delete();
				}
				file.renameTo(renamed);
			}
		} else {
			status = Status.MISSING;
			createMissingFile(file);
		}
		return status;
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
	
	private void removeProgressFiles(File folder) throws IOException{
		File[] files = folder.listFiles(new ProgressFileFilter());
		if(files != null){
			for (File curFile : files) {
				curFile.delete();
			}
		}
	}
	
	private void createMissingFile(File file) throws IOException{
		File missingFile = new File(file.getParent(), file.getName()+"-missing");
		missingFile.createNewFile();
	}
	
	private void removeMissingFile(File file){
		File missingFile = new File(file.getParent(), file.getName()+"-missing");
		missingFile.delete();
	}
	
	
	private Status handleFile(FtpSession session, int replyCode, File file) throws IOException, FtpException{
		Status status= null;
		if(file.exists()){
			File sfv = findSfv(file.getParentFile());
			if (sfv != null) {
				logger.debug("Testing file using " + sfv.getName());
				Map<String, String> files = parseSfv(sfv);
				String sfvHex = files.get(file.getName());
				if(sfvHex != null){
					long checksum = doChecksum(file, true);
					if(hexToLong(sfvHex) == checksum){
						status = Status.OK;
						removeMissingFile(file);
						//session.write(new DefaultFtpReply(replyCode,"OK "+file.getName() + " " + sfvHex));
					}else{
						logger.warn("FAIL "+longToHex(checksum)+" != "+sfvHex);
						status = Status.FAIL;
						//session.write(new DefaultFtpReply(replyCode,"FAIL "+file.getName()));
						File renamed = new File(file.getParentFile(), file.getName()+".bad");
						if(renamed.exists()){
							renamed.delete();
						}
						file.renameTo(renamed);
					}
				}else{
					status = Status.UNKNOWN;
					//session.write(new DefaultFtpReply(replyCode, "File not in sfv: "+file.getName()));
				}
			}
		}else{
			status = Status.MISSING;
			createMissingFile(file);
		}
		return status;
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
		Scanner scanner = null;
		try {
			scanner = new Scanner(sfvFile);
			String strLine;
			while (scanner.hasNextLine()) {
				strLine = scanner.nextLine();
				if (!strLine.startsWith(";") && !strLine.isEmpty()) {
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
		return files;
	}

	private long doChecksum(File file, boolean force) throws IOException {
		Long checksum = null;
		if(!force){
			checksum = (Long) cache.get(file.getAbsolutePath()).getValue();
		}
		if(checksum == null){
			CheckedInputStream cis = null;
			InputStream fis = null;
			try {
				// Compute CRC32 checksum
				fis = new FileInputStream(file);
				cis = new CheckedInputStream(fis, new CRC32());
				byte[] buf = new byte[2048];
				while (cis.read(buf) >= 0) {
					// nothing to do, just read
				}
				checksum = cis.getChecksum().getValue();
			} finally {
				if (fis != null) {
					fis.close();
				}
			}
			cache.put(new Element(file.getAbsolutePath(), checksum));
			cache.flush();
		}
		return checksum;
	}
	
	private static enum Status{
		MISSING,
		FAIL,
		OK,
		UNKNOWN
	}
	
	private static final class NoFolderOrSfvFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && !pathname.getName().endsWith(SFV_EXT);
		}
	}
	
	private static final class SfvFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && pathname.getName().endsWith(SFV_EXT);
		}
	}
	
	private static final class ProgressFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && pathname.getName().endsWith("[SFV]");
		}
	}
	
}