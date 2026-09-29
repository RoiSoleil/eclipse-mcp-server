package com.vogella.eclipse.mcp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The text a tool produced, any images it returns to the model, and the flag that tells the model whether the call
 * failed.
 */
public record McpToolResult(String text, boolean isError, List<Image> images) {

	/** An image returned as image content, so the model sees the picture rather than an encoded string. */
	public record Image(byte[] data, String mimeType) {
	}

	public McpToolResult {
		images = List.copyOf(images);
	}

	public McpToolResult(String text, boolean isError) {
		this(text, isError, List.of());
	}

	public static McpToolResult of(String text) {
		return new McpToolResult(text, false);
	}

	public static McpToolResult error(String message) {
		return new McpToolResult(message, true);
	}

	/** This result with one more image after the text. */
	public McpToolResult withImage(byte[] data, String mimeType) {
		List<Image> all = new ArrayList<>(images);
		all.add(new Image(data, mimeType));
		return new McpToolResult(text, isError, all);
	}
}
