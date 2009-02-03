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

/**
 * String formatting
 * 
 * This class does not keep any state so it can be used in a multithreaded context
 * 
 * @author francisdb
 *
 */
public class Formatter {

	/**
	 * Creates a progress bar for the requested percentage
	 * 
	 * @param percentage the percentage to show (0.0-1.0)
	 * @param length the length of the resulting string (min 3)
	 * 
	 * @return the progress bar String
	 */
	public String progressBar(double percentage, int length) {
		if(length < 3){
			throw new IllegalArgumentException("Minimum size is 3");
		}
		int count = (int) Math.floor(percentage * (length - 2));
		StringBuilder builder = new StringBuilder("[");
		for (int i = 0; i < (length - 2); i++) {
			if (i < count) {
				builder.append('#');
			} else {
				builder.append(':');
			}
		}
		builder.append(']');
		return builder.toString();
	}
}
