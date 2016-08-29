/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.util.Arrays;


// Computes SHA-1 hashes of binary data
public final class Sha1 {
	
	/* Convenience methods */
	
	public static byte[] getHash(byte[] b) {
		return getHash(b, 0, b.length);
	}
	
	
	public static byte[] getHash(byte[] b, int off, int len) {
		Sha1 hasher = new Sha1();
		hasher.update(b, off, len);
		return hasher.getHash();
	}
	
	
	
	/* Stateful streaming hasher */
	
	private byte[] block;
	private int blockFilled;
	private long length;
	private int[] state;
	
	
	
	public Sha1() {
		block = new byte[64];
		blockFilled = 0;
		length = 0;
		state = new int[]{0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
	}
	
	
	
	public void update(byte[] b) {
		update(b, 0, b.length);
	}
	
	
	public void update(byte[] b, int off, int len) {
		int blockLen = block.length;
		length += len;
		if (blockFilled > 0) {
			int n = Math.min(blockLen - blockFilled, len);
			System.arraycopy(b, off, block, blockFilled, n);
			blockFilled += n;
			if (blockFilled == blockLen) {
				compress(block, 0, blockLen);
				off += n;
				len -= n;
			} else
				return;
		}
		if (len >= blockLen) {
			int n = len / blockLen * blockLen;
			compress(b, off, n);
			off += n;
			len -= n;
		}
		System.arraycopy(b, off, block, 0, len);
		blockFilled = len;
	}
	
	
	private void compress(byte[] msg, int off, int len) {
		if (len % block.length != 0)
			throw new IllegalArgumentException();
		
		int a = state[0];
		int b = state[1];
		int c = state[2];
		int d = state[3];
		int e = state[4];
		int[] schedule = new int[80];
		for (int i = off, end = off + len; i < end; ) {
			for (int j = 0; j < 16; j++, i += 4) {
				schedule[j] =
					  (msg[i + 0] & 0xFF) << 24
					| (msg[i + 1] & 0xFF) << 16
					| (msg[i + 2] & 0xFF) <<  8
					| (msg[i + 3] & 0xFF) <<  0;
			}
			for (int j = 16; j < 80; j++) {
				int temp = schedule[j - 3] ^ schedule[j - 8] ^ schedule[j - 14] ^ schedule[j - 16];
				temp = Integer.rotateLeft(temp, 1);
				schedule[j] = temp;
			}
			for (int j = 0; j < 80; j++) {
				int f;
				if      (j < 20) f = (b & c) | (~b & d);
				else if (j < 40) f = b ^ c ^ d;
				else if (j < 60) f = (b & c) ^ (b & d) ^ (c & d);
				else             f = b ^ c ^ d;
				int temp = Integer.rotateLeft(a, 5) + f + e + K[j / 20] + schedule[j];
				e = d;
				d = c;
				c = Integer.rotateLeft(b, 30);
				b = a;
				a = temp;
			}
			a = state[0] += a;
			b = state[1] += b;
			c = state[2] += c;
			d = state[3] += d;
			e = state[4] += e;
		}
	}
	
	private static final int[] K = {0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC, 0xCA62C1D6};
	
	
	public byte[] getHash() {
		Sha1 copy = new Sha1();
		copy.block = block.clone();
		copy.blockFilled = blockFilled;
		copy.length = length;
		copy.state = state.clone();
		return copy.getHashDestructively();
	}
	
	
	private byte[] getHashDestructively() {
		block[blockFilled] = (byte)0x80;
		blockFilled++;
		Arrays.fill(block, blockFilled, block.length, (byte)0);
		if (blockFilled + 8 > block.length) {
			compress(block, 0, block.length);
			Arrays.fill(block, (byte)0);
		}
		length = length << 3;
		for (int i = 0; i < 8; i++)
			block[block.length - 1 - i] = (byte)(length >>> (i * 8));
		compress(block, 0, block.length);
		
		byte[] hash = new byte[state.length * 4];
		for (int i = 0; i < hash.length; i++)
			hash[i] = (byte)(state[i / 4] >>> (24 - i % 4 * 8));
		return hash;
	}
	
	
	public String toString() {
		return String.format("Sha1(length=%d, state=[%08x,%08x,%08x,%08x,%08x])", length, state[0], state[1], state[2], state[3], state[4]);
	}
	
}
