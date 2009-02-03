package com.google.code.sfvcheckftplet.service;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.ftpserver.ftplet.FtpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.sfvcheckftplet.SessionWriter;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 * EHCache wrapper for easy sfv related caches
 * 
 * @author francisdb
 * 
 */
public class CrcCache {

	private static final Logger logger = LoggerFactory.getLogger(CrcCache.class);

	private static final String FILE_PREFIX = "file:";
	private static final String SFV_PREFIX = "sfv:";

	private CacheManager manager;
	private Cache cache;

	public CrcCache() {
		super();
	}

	public void init() {
		URL url = getClass().getResource("/ehcache-crc.xml");
		manager = new CacheManager(url);
		manager.addCache("crcCache");
		cache = manager.getCache("crcCache");
		logger.debug("started cache.");
	}

	public void shutdown() {
		manager.shutdown();
		logger.debug("shut down cache.");
	}

	public void printStatus(SessionWriter writer) throws IllegalStateException, FtpException {
		String[] cacheNames = manager.getCacheNames();
		for (String name : cacheNames) {
			Cache cache = manager.getCache(name);
			writer.println(cache.getName() + ": " + cache.getSize() + " " + cache.getMemoryStoreSize() + " "
					+ cache.getDiskStoreSize());
			List<?> keys = cache.getKeys();
			for (Object key : keys) {
				writer.println("  " + cache.get(key));
			}
		}
	}

	public void putFileCrc(File file, Long checksum) {
		logger.debug("Adding crc info ("+checksum+") for file " + file.getAbsolutePath());
		cache.put(new Element(FILE_PREFIX + file.getAbsolutePath(), checksum));
		cache.flush();
	}

	public Long getFileCrc(File file) {
		logger.trace("Getting crc info for file " + file.getAbsolutePath());
		Element element = cache.get(FILE_PREFIX + file.getAbsolutePath());
		if (element == null) {
			return null;
		} else {
			return (Long) element.getValue();
		}
	}

	public void removeFileCrc(File file) {
		logger.debug("Removing crc info for file " + file.getAbsolutePath());
		cache.remove(FILE_PREFIX + file.getAbsolutePath());
	}

	public void putCrcInfo(File folderContainingSfv, Map<String, String> checksums) {
		logger.debug("Adding sfv for path " + folderContainingSfv.getAbsolutePath());
		cache.put(new Element(SFV_PREFIX + folderContainingSfv.getAbsolutePath(), checksums));
		cache.flush();
	}

	@SuppressWarnings("unchecked")
	public Map<String, String> getCrcInfo(File folderContainingSfv) {
		logger.trace("Getting sfv for path " + folderContainingSfv.getAbsolutePath());
		Element element = cache.get(SFV_PREFIX + folderContainingSfv.getAbsolutePath());
		if (element == null) {
			return null;
		} else {
			return (Map<String, String>) element.getValue();
		}
	}

	public void removeCrcInfo(File folderContainingSfv) {
		logger.debug("Removing sfv for path " + folderContainingSfv.getAbsolutePath());
		cache.remove(SFV_PREFIX + folderContainingSfv.getAbsolutePath());
	}

}
