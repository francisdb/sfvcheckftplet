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

import java.io.IOException;

import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;

/**
 * Denies files ending with [sfv]
 * 
 * @author francisdb
 *
 */
public class FileNameCheckFtplet extends DefaultFtplet {

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
	
	private boolean denied(String fileName){
		return 
			fileName.contains("[")
			|| fileName.contains("]")
			|| fileName.equalsIgnoreCase("thumbs.db");
	}
}
