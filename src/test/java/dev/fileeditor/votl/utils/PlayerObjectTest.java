package dev.fileeditor.votl.utils;

import dev.fileeditor.votl.BaseTest;
import dev.fileeditor.votl.utils.level.PlayerObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerObjectTest extends BaseTest {

	@Test
	public void testPlayerObject() {
		PlayerObject playerObject = new PlayerObject(12345L, 67890L);

		assertEquals("12345:67890", playerObject.asKey());
		assertEquals(playerObject, PlayerObject.fromKey("12345:67890"));
	}

}
