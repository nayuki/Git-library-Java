/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/git-library-java
 */

package io.nayuki.git;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import org.junit.Test;


/**
 * Tests the functionality of class {@link ObjectId}.
 */
public final class ObjectIdTest {
	
	@Test public void testHexadecimal() {
		var id = new ObjectId("0123456789abcdef0123456789abcdef01234567");
		assertEquals("0123456789abcdef0123456789abcdef01234567", id.toHexadecimal());
		assertEquals((byte)0x01, id.getByte( 0));
		assertEquals((byte)0x23, id.getByte( 1));
		assertEquals((byte)0xEF, id.getByte( 7));
		assertEquals((byte)0x67, id.getByte(19));
		
		id = new ObjectId("0123456789AbcdeF0123456789ABCDEF01234567");
		assertEquals("0123456789abcdef0123456789abcdef01234567", id.toHexadecimal());
		assertArrayEquals(bytes(
			0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x01, 0x23,
			0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x01, 0x23, 0x45, 0x67), id.getBytes());
	}
	
	
	@Test public void testHexadecimalInvalid() {
		assertThrows(NullPointerException.class,
			() -> new ObjectId((String)null));
		
		String[] cases = {
			"",
			"a",
			"12",
			"000000000000000000000000000000000000000g",
			"+000000000000000000000000000000000000000",
			"-000000000000000000000000000000000000000",
			"00000000000000000000000000000000000000000",
			"000000000000000000000000000000000000000000",
		};
		for (String cs : cases) {
			assertThrows(IllegalArgumentException.class,
				() -> new ObjectId(cs));
		}
	}
	
	
	@Test public void testByteArray() {
		var id = new ObjectId(new byte[20]);
		assertEquals("0000000000000000000000000000000000000000", id.toHexadecimal());
		
		id = new ObjectId(bytes(
			0xFF, 0x7F, 0x00, 0x80, 0x31, 0x25, 0x07, 0x64, 0xCC, 0x2D,
			0xA1, 0xFF, 0xFE, 0xDC, 0xBA, 0x98, 0x76, 0x54, 0x32, 0x10));
		assertEquals("ff7f008031250764cc2da1fffedcba9876543210", id.toHexadecimal());
	}
	
	
	@Test public void testByteArrayInvalid() {
		assertThrows(NullPointerException.class, () -> new ObjectId((byte[])null));
		assertThrows(IllegalArgumentException.class, () -> new ObjectId(new byte[0]));
		assertThrows(IllegalArgumentException.class, () -> new ObjectId(new byte[19]));
		assertThrows(IllegalArgumentException.class, () -> new ObjectId(new byte[21]));
	}
	
	
	@Test public void testByteArrayOffset() {
		byte[] b = bytes(
			0x4B, 0xCE, 0x96, 0xFB, 0x24, 0x8A, 0x95, 0x77, 0x56, 0x6A,
			0x88, 0xFD, 0x55, 0xFA, 0x83, 0x25, 0x93, 0xF6, 0x04, 0x32,
			0xD0, 0x41, 0x35, 0xAB, 0xBA, 0xF5, 0x18, 0xA8, 0x2B, 0x8A,
			0xD3, 0x74, 0x4A, 0xCE, 0x64, 0xCC, 0x05, 0x9E, 0x4C, 0x62);
		assertEquals("4bce96fb248a9577566a88fd55fa832593f60432", new ObjectId(b,  0).toHexadecimal());
		assertEquals("8a9577566a88fd55fa832593f60432d04135abba", new ObjectId(b,  5).toHexadecimal());
		assertEquals("d04135abbaf518a82b8ad3744ace64cc059e4c62", new ObjectId(b, 20).toHexadecimal());
	}
	
	
	@Test public void testByteArrayOffsetInvalid() {
		assertThrows(NullPointerException.class,
			() -> new ObjectId(null, 0));
		
		int[][] cases = {
			{0, 0},
			{0, -5},
			{0, 2},
			{19, 0},
			{19, -1},
			{20, -2},
			{20, 1},
			{21, 3},
		};
		for (int[] cs : cases) {
			assertThrows(IndexOutOfBoundsException.class,
				() -> new ObjectId(new byte[cs[0]], cs[1]));
		}
	}
	
	
	@Test public void testEquals() {
		var id = new ObjectId("0123456789abcdef0123456789abcdef01234567");
		
		assertEquals(id, id);
		assertEquals(id, new ObjectId("0123456789abcdef0123456789abcdef01234567"));
		assertEquals(id, new ObjectId("0123456789ABCDEF0123456789ABCDEF01234567"));
		
		assertEquals(id, new ObjectId(bytes(
			0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x01, 0x23,
			0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x01, 0x23, 0x45, 0x67)));
		
		assertEquals(id, new ObjectId(bytes(
			0, 0, 0,
			0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x01, 0x23,
			0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x01, 0x23, 0x45, 0x67), 3));
	}
	
	
	@Test public void testCompareTo() {
		Object[][] cases = {
			{"0123456789abcdef0123456789abcdef01234567", "0123456789abcdef0123456789abcdef01234567",  0},
			{"0000000000000000000000000000000000000000", "0000000000000000000000000000000000000000",  0},
			{"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", "ffffffffffffffffffffffffffffffffffffffff",  0},
			{"0000000000000000000000000000000000000000", "ffffffffffffffffffffffffffffffffffffffff", -1},
			{"ffffffffffffffffffffffffffffffffffffffff", "8000000000000000000000000000000000000000", +1},
			{"6f62190f6aa0db573f2e010f0cf517ccea497d23", "66bec5a96e5ba79572ee4584465be376c562eb31", +1},
			{"faf7283f5131e4434df081637cd25327a2babb5c", "840e11a039902dece114e56de1871d3abceb8b2e", +1},
			{"126e2a3bdaaa985d32f1a35e35c80d4c8cd86a8e", "14d0d669c56be2ddf53243094b74ebe1e4fe6653", -1},
			{"7d9a21feb6a3fe4414f4ee28e9bf15e434fab7eb", "7d9a21fef6a3fe4414f4ee28e9bf15e434fab7eb", -1},
		};
		for (Object[] cs : cases) {
			var x = new ObjectId((String)cs[0]);
			var y = new ObjectId((String)cs[1]);
			assertEquals((int)cs[2], Integer.signum(x.compareTo(y)));
		}
	}
	
	
	private static byte[] bytes(int... x) {
		var b = new byte[x.length];
		for (int i = 0; i < b.length; i++)
			b[i] = (byte)x[i];
		return b;
	}
	
}
