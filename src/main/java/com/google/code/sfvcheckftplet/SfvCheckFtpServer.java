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

import java.util.ArrayList;
import java.util.List;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.SaltedPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SfvCheckFtpServer {

	private static final Logger logger = LoggerFactory.getLogger(SfvCheckFtpServer.class);

	private static final int DEFAULT_PORT = 2221;
	
	public static void main(String[] args) {
		try {
			new SfvCheckFtpServer().start();
		} catch (FtpException e) {
			e.printStackTrace();
		}
	}

	void start() throws FtpException {

		ListenerFactory factory = new ListenerFactory();
		// set the port of the listener
		factory.setPort(DEFAULT_PORT);

		PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
		// userManagerFactory.setFile(new File("myusers.properties"));
		userManagerFactory.setPasswordEncryptor(new SaltedPasswordEncryptor());
		userManagerFactory.setAdminName("test");
		UserManager um = userManagerFactory.createUserManager();

		BaseUser user = new BaseUser();
		user.setName("test");
		user.setPassword("test");
		user.setHomeDirectory("ftproot");
		List<Authority> auths = new ArrayList<Authority>();
		Authority auth = new WritePermission();
		auths.add(auth);
		user.setAuthorities(auths);
		um.save(user);

		FtpServerFactory serverFactory = new FtpServerFactory();
		// replace the default listener
		serverFactory.addListener("default", factory.createListener());
		serverFactory.getFtplets().put("SfvCheckFtpLet", new SfvCheckFtpLet());
		serverFactory.setUserManager(um);

		FtpServer server = serverFactory.createServer();
		// start the server
		server.start();
		logger.info("Try connecting to localhost on port "+DEFAULT_PORT);
	
	}
}
