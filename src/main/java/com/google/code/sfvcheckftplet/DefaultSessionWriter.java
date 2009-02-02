package com.google.code.sfvcheckftplet;

import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpSession;

class DefaultSessionWriter implements SessionWriter {
	private final FtpSession session;
	private final int defaultReplyCode;
	
	public DefaultSessionWriter(final FtpSession session, final int defaultReplyCode) {
		this.session = session;
		this.defaultReplyCode = defaultReplyCode; 
	}
	
	/* (non-Javadoc)
	 * @see com.google.code.sfvcheckftplet.SessionWriter#println()
	 */
	public void println() throws FtpException{
		if(defaultReplyCode > 0){
			session.write(new DefaultFtpReply(defaultReplyCode, ""));
		}
	}
	
	/* (non-Javadoc)
	 * @see com.google.code.sfvcheckftplet.SessionWriter#println(java.lang.String)
	 */
	public void println(String message) throws FtpException{
		if(defaultReplyCode > 0){
			session.write(new DefaultFtpReply(defaultReplyCode, message));
		}
	}
	
	/* (non-Javadoc)
	 * @see com.google.code.sfvcheckftplet.SessionWriter#println(java.lang.String[])
	 */
	public void println(String[] messages) throws FtpException{
		if(defaultReplyCode > 0){
			session.write(new DefaultFtpReply(defaultReplyCode, messages));
		}
	}
}