/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.lang.ref.WeakReference;


public final class RawId extends ObjectId {
	
	public RawId(String hexStr, WeakReference<Repository> srcRepo) {
		super(hexStr, srcRepo);
	}
	
	
	public RawId(byte[] bytes, WeakReference<Repository> srcRepo) {
		super(bytes, srcRepo);
	}
	
	
	public RawId(byte[] bytes, int off, WeakReference<Repository> srcRepo) {
		super(bytes, off, srcRepo);
	}
	
}
