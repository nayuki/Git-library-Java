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


public class TreeId extends ObjectId {
	
	public TreeId(String hexStr, WeakReference<Repository> srcRepo) {
		super(hexStr, srcRepo);
	}
	
	
	public TreeId(byte[] bytes, WeakReference<Repository> srcRepo) {
		super(bytes, srcRepo);
	}
	
	
	public TreeId(byte[] bytes, int off, WeakReference<Repository> srcRepo) {
		super(bytes, off, srcRepo);
	}
	
	
	
	public TreeObject read() throws IOException, DataFormatException {
		return (TreeObject)getSourceRepository().readObject(this);
	}
	
}
