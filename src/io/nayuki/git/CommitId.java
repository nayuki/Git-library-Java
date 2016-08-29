/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.lang.ref.WeakReference;


public final class CommitId extends ObjectId {
	
	public CommitId(String hexStr, WeakReference<Repository> srcRepo) {
		super(hexStr, srcRepo);
	}
	
	
	public CommitId(byte[] bytes, WeakReference<Repository> srcRepo) {
		super(bytes, srcRepo);
	}
	
	
	public CommitId(byte[] bytes, int off, WeakReference<Repository> srcRepo) {
		super(bytes, off, srcRepo);
	}
	
}
