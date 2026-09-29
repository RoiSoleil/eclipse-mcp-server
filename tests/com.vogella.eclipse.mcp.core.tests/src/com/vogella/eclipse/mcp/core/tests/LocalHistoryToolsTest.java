package com.vogella.eclipse.mcp.core.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.vogella.eclipse.mcp.core.McpToolResult;

class LocalHistoryToolsTest {

	private static final String GET = "eclipse_get_local_history";

	private static final String RESTORE = "eclipse_restore_local_history";

	private static final String PROJECT = "mcp-history-test";

	private final TestFixture fixture = new TestFixture();

	@AfterEach
	void deleteTestProjects() throws Exception {
		fixture.dispose();
	}

	@Test
	void versionsAreListedNewestFirstAndReadable() throws Exception {
		IFile file = withVersions("one\n", "two\n", "three\n");

		Map<String, Object> listing = TestFixture.callAndParse(GET, Map.of("path", path(file)));

		List<Map<String, Object>> versions = versions(listing);
		assertEquals(2, versions.size(), listing.toString());
		assertEquals(Boolean.FALSE, listing.get("truncated"));
		assertEquals("two\n", content(file, versions.get(0)));
		assertEquals("one\n", content(file, versions.get(1)));
	}

	@Test
	void restoringWithoutATimestampUndoesTheLastWriteAndKeepsWhatItReplaced() throws Exception {
		IFile file = withVersions("one\n", "two\n");

		Map<String, Object> result = TestFixture.callAndParse(RESTORE, Map.of("path", path(file)));

		assertEquals(Boolean.TRUE, result.get("written"));
		assertEquals("one\n", TestFixture.read(file));
		List<Map<String, Object>> versions = versions(TestFixture.callAndParse(GET, Map.of("path", path(file))));
		assertEquals("two\n", content(file, versions.get(0)), "the replaced content is itself recoverable");
	}

	@Test
	void anOlderVersionIsRestoredByItsTimestamp() throws Exception {
		IFile file = withVersions("one\n", "two\n", "three\n");
		List<Map<String, Object>> versions = versions(TestFixture.callAndParse(GET, Map.of("path", path(file))));

		TestFixture.callAndParse(RESTORE, Map.of("path", path(file), "timestamp", versions.get(1).get("timestamp")));

		assertEquals("one\n", TestFixture.read(file));
	}

	@Test
	void aDryRunWritesNothing() throws Exception {
		IFile file = withVersions("one\n", "two\n");

		Map<String, Object> result = TestFixture.callAndParse(RESTORE, Map.of("path", path(file), "dryRun", Boolean.TRUE));

		assertEquals(Boolean.FALSE, result.get("written"));
		assertEquals("two\n", TestFixture.read(file));
	}

	@Test
	void aDeletedFileIsRecreatedFromItsHistory() throws Exception {
		IFile file = withVersions("one\n", "two\n");
		file.delete(IResource.KEEP_HISTORY, null);
		assertFalse(file.exists());

		Map<String, Object> result = TestFixture.callAndParse(RESTORE, Map.of("path", path(file)));

		assertEquals(Boolean.TRUE, result.get("recreated"));
		assertEquals("two\n", TestFixture.read(file));
	}

	@Test
	void anUnknownTimestampIsRefused() throws Exception {
		IFile file = withVersions("one\n", "two\n");

		McpToolResult result = TestFixture.call(RESTORE, Map.of("path", path(file), "timestamp", Long.valueOf(42)));

		assertTrue(result.isError());
		assertTrue(result.text().contains(GET), result.text());
		assertEquals("two\n", TestFixture.read(file));
	}

	/** Writes each content in turn, with distinct modification times so the versions have distinct timestamps. */
	private IFile withVersions(String... contents) throws Exception {
		IProject project = fixture.createProject(PROJECT);
		IFile file = project.getFile("notes.txt");
		long time = System.currentTimeMillis() - 60_000;
		for (int i = 0; i < contents.length; i++) {
			TestFixture.callAndParse("eclipse_write_file",
					Map.of("path", path(file), "content", contents[i], "overwrite", Boolean.valueOf(i > 0)));
			file.setLocalTimeStamp(time + i * 1000L);
		}
		return file;
	}

	private static String content(IFile file, Map<String, Object> version) throws Exception {
		Map<String, Object> read = TestFixture.callAndParse(GET,
				Map.of("path", path(file), "timestamp", version.get("timestamp")));
		assertEquals(Boolean.TRUE, read.get("read"), read.toString());
		return (String) read.get("content");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> versions(Map<String, Object> listing) {
		return (List<Map<String, Object>>) listing.get("versions");
	}

	private static String path(IFile file) {
		return file.getFullPath().toString();
	}
}
