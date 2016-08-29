/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.zip.DataFormatException;


public final class BlobId extends ObjectId {
	
	public BlobId(String hexStr, WeakReference<Repository> srcRepo) {
		super(hexStr, srcRepo);
	}
	
	
	public BlobId(byte[] bytes, WeakReference<Repository> srcRepo) {
		super(bytes, srcRepo);
	}
	
	
	public BlobId(byte[] bytes, int off, WeakReference<Repository> srcRepo) {
		super(bytes, off, srcRepo);
	}
	
	
	
	public BlobObject read() throws IOException, DataFormatException {
		return (BlobObject)getSourceRepository().readObject(this);
	}
	
}
