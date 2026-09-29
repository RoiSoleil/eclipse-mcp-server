package com.vogella.eclipse.mcp.core.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vogella.eclipse.mcp.core.ToolArguments;

class ToolArgumentsTest {

	@Test
	void textKeepsTheWhitespaceThatStringTrims() {
		ToolArguments args = ToolArguments.of(Map.of("text", "\t  indented \n"));

		assertEquals("\t  indented \n", args.getText("text"));
		assertEquals("indented", args.getString("text"));
		assertNull(args.getText("missing"));
	}

	@Test
	void textKeepsAnArgumentThatIsOnlyWhitespace() {
		ToolArguments args = ToolArguments.of(Map.of("text", " "));

		assertEquals(" ", args.getText("text"));
		assertNull(args.getString("text"));
	}
}
