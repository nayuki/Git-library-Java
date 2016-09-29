package io.nayuki.git;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;


public final class Sha1Test {
	
	@Test public void testHashFunction() {
		char[] temp = new char[1000000];
		Arrays.fill(temp, 'a');
		String MILLION_AS = new String(temp);
		String[][] cases = {
			{"da39a3ee5e6b4b0d3255bfef95601890afd80709", ""},
			{"86f7e437faa5a7fce15d1ddcb9eaeaea377667b8", "a"},
			{"a9993e364706816aba3e25717850c26c9cd0d89d", "abc"},
			{"c12252ceda8be8994d5fa0290a47231c1d16aae3", "message digest"},
			{"32d10c7b8cf96570ca04ce37f2a19d84240d3a89", "abcdefghijklmnopqrstuvwxyz"},
			{"84983e441c3bd26ebaae4aa1f95129e5e54670f1", "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"},
			{"a49b2446a02c645bf419f995b67091253a04a259", "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"},
			{"34aa973cd4c4daa4f61eeb2bdbad27316534016f", MILLION_AS},
		};
		
		for (String[] cs : cases) {
			byte[] msg = cs[1].getBytes(StandardCharsets.US_ASCII);
			byte[] hash = Sha1.getHash(msg);
			Assert.assertEquals(cs[0], new RawId(hash).hexString);
		}
	}
	
}
