package com.google.code.sfvcheckftplet;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.sfvcheckftplet.service.CrcService;
import com.google.code.sfvcheckftplet.service.FileTools;
import com.google.code.sfvcheckftplet.service.SystemTools;

/**
 * TODO use http://java.sun.com/javase/5/docs/api/java/util/Formatter.html#syntax
 * @author francisdb
 *
 */
public class SfvCheckFtpLet extends DefaultFtplet {

	private static final Logger logger = LoggerFactory.getLogger(SfvCheckFtpLet.class);
	
	private static final int SITE_RESPONSE = 200;
	private static final int TRANSFER_COMPLETE_RESPONSE = 226;
	private static final int REQUESTED_FILE_ACTION_OK = 250;
	

	private final CrcService crcService;
	
	public SfvCheckFtpLet() {
		this.crcService = new CrcService();
	}
	
	@Override
	public void init(FtpletContext ftpletContext) throws FtpException {
		crcService.init();
	}
	
	@Override
	public void destroy() {
		crcService.shutdown();
	}
	
	@Override
	public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		// TODO find a better place to handle this where we can generate output (neg value = no output)
		SessionWriter writer = new DefaultSessionWriter(session, -TRANSFER_COMPLETE_RESPONSE);
		FtpFile ftpFile = ftpFile(session, request);
		File file = realFile(session, ftpFile);
		crcService.checksum(file, true);
		if(!FileTools.isSfv(file)){
			handleFile(writer, file);
		}
		rescan(writer, file.getParentFile(), false);
		return super.onUploadEnd(session, request);
	}
	
	@Override
	public FtpletResult onDeleteEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
		SessionWriter writer = new DefaultSessionWriter(session, -REQUESTED_FILE_ACTION_OK);
		FtpFile ftpFile = ftpFile(session, request);
		File file = realFile(session, ftpFile);
		// TODO find a cleaner way, do not delere -missing files that are not ours
		if(FileTools.isSfv(file)){
			cleanUp(file.getParentFile());
			removeParentIncompleteFile(file.getParentFile());
		}
		crcService.clearData(file);
		rescan(writer, file.getParentFile(), false);
		return super.onDeleteEnd(session, request);
	}

	
	@Override
	public FtpletResult onSite(FtpSession session, FtpRequest request) throws FtpException, IOException {
		String argument = request.getArgument().toUpperCase();
		if("RESCAN".equals(argument)){
			return onSiteRescan(session, request);
		}else if("STATUS".equals(argument)){
			return onSiteStatus(session, request);
		}
		return super.onSite(session, request);
	}
	
	public FtpletResult onSiteRescan(FtpSession session, FtpRequest request) throws FtpException, IOException {
		String filePath = session.getUser().getHomeDirectory()
		+ session.getFileSystemView().getWorkingDirectory().getAbsolutePath();
		File work = new File(filePath);
		SessionWriter writer = new DefaultSessionWriter(session, SITE_RESPONSE);
		rescan(writer, work, true);
		return FtpletResult.SKIP;
	}
	
	public FtpletResult onSiteStatus(FtpSession session, FtpRequest request) throws IllegalStateException, FtpException{
		crcService.printStatus(new DefaultSessionWriter(session, SITE_RESPONSE));
		return FtpletResult.SKIP;
	}

	
	
	private File realFile(FtpSession session, FtpFile ftpFile) throws FtpException{
		String filePath = session.getUser().getHomeDirectory() + ftpFile.getAbsolutePath();
		return new File(filePath);
	}
	
	private FtpFile ftpFile(FtpSession session, FtpRequest request) throws FtpException{
		return session.getFileSystemView().getFile(request.getArgument());
	}
	
	
	/**
	 * Removes -missing and ...[CRC] files
	 * @param folder
	 */
	private void cleanUp(File folder){
		File[] files = folder.listFiles(new ProgressMissingFileFilter());
		if(files != null){
			for (File curFile : files) {
				curFile.delete();
			}
		}
	}
	
	private void rescan(SessionWriter writer, File folder, boolean forced) throws IOException, FtpException{
		File sfv = findSfv(folder);
		if (sfv != null) {
			writer.println("Rescanning files...");
			writer.println();
			Map<String, String> filesToCheck = crcService.parseSfv(sfv, forced);
			int count = filesToCheck.size();
			int found = 0;
			int failed = 0; 
			long totalSize = 0;
			for(Entry<String,String> entry:filesToCheck.entrySet()){
				File toCheck = new File(folder, entry.getKey());
				Status status = crcService.rescanFile(writer, toCheck, entry.getValue(), forced);
				if(status == Status.OK){
					removeMissingFile(toCheck);
					found++;
					totalSize += toCheck.length();
				}else if(status == Status.FAIL){
					failed++;
				}else if(status == Status.MISSING){
					createMissingFile(toCheck);
				}
			}
			float percentage = (float)found/count;
			int percentageInt = (int) Math.floor(percentage * 100.0f);
			removeProgressFiles(folder);
			if(percentageInt == 100){
				removeParentIncompleteFile(folder);
				// TODO get data from id3 tag?
				totalSize = totalSize / (1024*1024);
				String genre = ""; // "- Beat 2006 "
				File file = new File(folder, "[SFV] - ( "+totalSize+"M "+count+"F - COMPLETE "+genre+") - [SFV]");
				file.createNewFile();
			}else{
				createParentIncompleteFileIfNeeded(folder);
				File file = new File(folder, progressBar(percentage)+" - "+percentageInt+"% Complete - [SFV]");
				file.createNewFile();
			}
			
			writer.println();
			writer.println();
			writer.println(" Passed : "+found);
			writer.println(" Failed : "+failed);
			writer.println(" Missing: "+(count-failed-found));
			writer.println("  Total : "+count);
			writer.println("Command Successful.");
		}else{
			writer.println("No sfv file found.");
		}

	}
	
	private void createParentIncompleteFileIfNeeded(File folder) throws IOException{
		File file = new File(folder.getParent(), "(incomplete)-"+folder.getName());
		if(!file.exists()){
			// crerate symbolic link in linux
			if(SystemTools.osSupportsLinking()){
				FileTools.createSymbolicLink(folder, file );
			}else{
				file.createNewFile();
			}
		}
	}
	
	private void removeParentIncompleteFile(File folder){
		File file = new File(folder.getParent(), "(incomplete)-"+folder.getName());
		if(file.exists()){
			file.delete();
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
	
	
	private Status handleFile(SessionWriter writer, File file) throws IOException, FtpException {
		
		// get crc for this file
		Status status = crcService.checkNewFile(file);
		if(status == Status.OK){
			removeMissingFile(file);
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
	
	private static final class SfvFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && FileTools.isSfv(pathname);
		}
	}
	
	private static final class ProgressFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && pathname.getName().endsWith("[SFV]");
		}
	}
	
	private static final class ProgressMissingFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && (pathname.getName().endsWith("[SFV]") || pathname.getName().endsWith("-missing"));
		}
	}
	
}