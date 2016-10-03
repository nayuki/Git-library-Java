/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.IOException;


public final class GitFormatException extends IOException {
	
	public GitFormatException() {
		super();
	}
	
	public GitFormatException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public GitFormatException(String message) {
		super(message);
	}
	
	public GitFormatException(Throwable cause) {
		super(cause);
	}
	
}
