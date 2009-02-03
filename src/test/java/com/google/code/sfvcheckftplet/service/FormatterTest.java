package com.google.code.sfvcheckftplet.service;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

import com.google.code.sfvcheckftplet.Formatter;

public class FormatterTest {

	@Test
	public void testProgressBar() {
		Formatter formatter = new Formatter();
		Random random = new Random();
		int length = random.nextInt(50)+3;
		String bar = formatter.progressBar(Math.random(), length);
		assertEquals(length, bar.length());
		System.out.println(bar);
		
		
		bar = formatter.progressBar(0, length);
		assertFalse(bar.contains("#"));
		assertTrue(bar.contains(":"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testProgressBarFail() {
		Formatter formatter = new Formatter();
		formatter.progressBar(0.5, 2);
	}

}
