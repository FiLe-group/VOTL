package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import dev.fileeditor.votl.utils.encoding.Base62;
import dev.fileeditor.votl.utils.encoding.MurmurHash3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EncodingTest extends BaseTest {

	@Test
	public void testBase62Encode() {
		assertEquals("8M0kX", Base62.encode(123456789L));
		assertEquals("Z3WbxDVB", Base62.encode(123456789012345L));
	}

	@Test
	public void testBase62Decode() {
		assertEquals(123456789L, Base62.decode("8M0kX"));
		assertEquals(123456789012345L, Base62.decode("Z3WbxDVB"));
	}

	@Test
	public void testMurmurHash3Encode() {
		// Test values from https://github.com/PeterScott/murmur3/blob/master/test.c
		assertEquals("faf6cdb3", hashString32("Hello, world!", 1234));
		assertEquals("bf505788", hashString32("Hello, world!", 4321));
		assertEquals("8905ac28", hashString32("xxxxxxxxxxxxxxxxxxxxxxxxxxxx", 1234));
		assertEquals("0f2cc00b", hashString32("", 1234));

		assertEquals("421c8c738743acadf19732fdd373c3f5", hashString128("Hello, world!", 123));
		assertEquals("ca47f42bf86d400479200aeeb9546c79", hashString128("Hello, world!", 321));
		assertEquals("dbcf7463becf7e04f66e73e07751664e", hashString128("xxxxxxxxxxxxxxxxxxxxxxxxxxxx", 123));
		assertEquals("81679d1a4cd959704bace33dbd92f878", hashString128("", 123));
	}

	private String hashString32(final String value, final int seed) {
		return String.format("%08x", MurmurHash3.hash32x86(value.getBytes(), 0, value.length(), seed));
	}

	private String hashString128(final String value, final int seed) {
		long[] hash = MurmurHash3.hash128x64(value.getBytes(), 0, value.length(), seed);
		return String.format("%016x%016x", hash[0], hash[1]);
	}

}
