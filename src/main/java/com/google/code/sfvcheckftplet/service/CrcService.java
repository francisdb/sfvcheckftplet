package com.google.code.sfvcheckftplet.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

public class CrcService {
	
	
	private CacheManager manager;
	private Cache cache;
	
	public CrcService() {
		// TODO Auto-generated constructor stub
	}
	
	public void init(){
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
	
	public void shutdown(){
		manager.shutdown();
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
	
	public void clearData(String file){
		cache.remove(file);
	}
}
