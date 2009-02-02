package com.google.code.sfvcheckftplet.service;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class CrcCacheTest {

	private CrcCache cache;
	
	@Before
	public void init(){
		cache = new CrcCache();
		cache.init();
	}
	
	@After
	public void shutdown(){
		cache.shutdown();
	}
	
	
	@Test
	public void testFileCrcStuff(){
		File test = new File("/a/b/c/junit.test.file");
		cache.putFileCrc(test, 12345L);
		Long value = cache.getFileCrc(test);
		Assert.assertEquals(Long.valueOf(12345), value);
		

		// simulate restart
		cache.shutdown();
		cache = new CrcCache();
		cache.init();
		
		value = cache.getFileCrc(test);
		Assert.assertEquals(Long.valueOf(12345), value);
		
		cache.removeFileCrc(test);
		Long valueDeleted = cache.getFileCrc(test);
		Assert.assertNull(valueDeleted);
	}
}
