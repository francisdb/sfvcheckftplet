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
package com.google.code.sfvcheckftplet;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

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

import com.google.code.sfvcheckftplet.service.CrcService;
import com.google.code.sfvcheckftplet.service.FileTools;
import com.google.code.sfvcheckftplet.service.SystemTools;

/**
 * Ftplet that crc checks incoming files
 *  
 * TODO use http://java.sun.com/j2se/1.5.0/docs/api/java/util/Formatter.html
 * 
 * @author francisdb
 *
 */
public class SfvCheckFtpLet extends DefaultFtplet {

	private static final Logger logger = LoggerFactory.getLogger(SfvCheckFtpLet.class);
	
	private static final int SITE_RESPONSE = 200;
	private static final int TRANSFER_COMPLETE_RESPONSE = 226;
	private static final int REQUESTED_FILE_ACTION_OK = 250;
	

	private final CrcService crcService;
	private final Formatter formatter;
	
	public SfvCheckFtpLet() {
		this.crcService = new CrcService();
		this.formatter = new Formatter();
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
	public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
		String fileName = request.getArgument();
		if(denied(fileName)){
			session.write(new DefaultFtpReply(553, fileName+": path-filter denied permission. (Filename accept)"));
			return FtpletResult.SKIP;
		}else{
			return super.onUploadStart(session, request);
		}
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
		}else if("CACHE".equals(argument)){
			return onSiteCache(session, request);
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
	
	public FtpletResult onSiteCache(FtpSession session, FtpRequest request) throws IllegalStateException, FtpException{
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
		Map<String,String> files = crcService.getCrcInfo(folder);
		for(String file:files.keySet()){
			File toDelete = new File(folder, file+"-missing");
			if(toDelete.exists()){
				toDelete.delete();
			}
		}
		
		// TODO find better way to select these files (regex?)
		File[] indicatorFiles = folder.listFiles(new ProgressMissingFileFilter());
		if(files != null){
			for (File curFile : indicatorFiles) {
				curFile.delete();
			}
		}
	}
	
	private void rescan(SessionWriter writer, File folder, boolean forced) throws IOException, FtpException{
		File sfv = FileTools.findSfv(folder);
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
				File file = new File(folder, formatter.progressBar(percentage, 16)+" - "+percentageInt+"% Complete - [SFV]");
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
	
	private File parentIncompleteFile(File folder){
		File parent = folder.getParentFile();
		File folderToPlaceFile = folder.getParentFile();
		String extra = "";
		String problemFolder = folder.getName();
		// TODO better check using regex?
		if(folder.getName().toLowerCase().startsWith("cd")){
			folderToPlaceFile = parent.getParentFile();
			extra = "("+folder.getName()+")-";
			problemFolder = parent.getName();
		}
		File file = new File(folderToPlaceFile, "(incomplete)-" + extra + problemFolder);
		return file;
	}
	
	private void createParentIncompleteFileIfNeeded(File folder) throws IOException{
		File file = parentIncompleteFile(folder);
		if(!file.exists()){
			if(SystemTools.osSupportsLinking()){
				FileTools.createSymbolicLink(folder, file );
			}else{
				file.createNewFile();
			}
		}
	}
	
	private void removeParentIncompleteFile(File folder){
		File file = parentIncompleteFile(folder);
		if(file.exists()){
			file.delete();
		}
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
		switch(status){
		case OK:
			removeMissingFile(file);
			break;
		case FAIL:
			// TODO make sure we want this
			File renamed = new File(file.getParentFile(), file.getName() + "-bad");
			if (renamed.exists()) {
				renamed.delete();
			}
			file.renameTo(renamed);
			break;
		}

		return status;
	}
	
	
	private boolean denied(String fileName){
		return 
			fileName.contains("[")
			|| fileName.contains("]")
			|| fileName.endsWith("-missing")
			|| fileName.equalsIgnoreCase("thumbs.db");
	}
	
	private static final class ProgressFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && pathname.getName().endsWith("[SFV]");
		}
	}
	
	private static final class ProgressMissingFileFilter implements FileFilter {
		public boolean accept(File pathname) {
			return pathname.isFile() && (pathname.getName().endsWith("[SFV]"));
		}
	}
	
}