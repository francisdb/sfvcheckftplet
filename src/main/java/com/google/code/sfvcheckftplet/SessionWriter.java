package com.google.code.sfvcheckftplet;

import org.apache.ftpserver.ftplet.FtpException;

/**
 * Facilitates writing to the session
 * @author francisdb
 *
 */
public interface SessionWriter {

	public abstract void println() throws FtpException;

	public abstract void println(String message) throws FtpException;

	public abstract void println(String[] messages) throws FtpException;

}