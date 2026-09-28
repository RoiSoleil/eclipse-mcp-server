package com.vogella.eclipse.mcp.core.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vogella.eclipse.mcp.core.internal.SubstituteBundleTool;

/**
 * What the framework has actually loaded, which is the field a caller is meant
 * to trust over bundles.info.
 * <p>
 * It used to be unreadable: the answer came from this bundle's own
 * BundleContext, and this bundle declares no activator and no lazy activation,
 * so it never leaves RESOLVED and the context is null for the life of the IDE.
 * Every caller was told the server was still starting and to ask again.
 */
class SubstituteBundleToolTest {

	@Test
	void reportsWhatTheFrameworkHasLoadedRatherThanRefusingToLook() throws Exception {
		Map<String, Object> running = TestFixture
				.parse(SubstituteBundleTool.running("org.eclipse.core.runtime").toString());

		assertEquals(Boolean.TRUE, running.get("known"),
				"a bundle that is certainly there has to be readable, got " + running);
		assertNotNull(running.get("version"), "got " + running);
		assertNotNull(running.get("state"), "the state is what tells a resolved bundle from a broken one");
		assertEquals(Boolean.FALSE, running.get("isSubstitutedJar"),
				"nothing is substituted in the test IDE, got " + running);
	}

	@Test
	void aPackedLineThatLosesToAHigherVersionIsSupersededAndNothingElseIs() throws Exception {
		// what an update leaves behind: the framework installs the old packed copy,
		// resolves the higher one, and reported the unloaded copy as running
		Path installation = Files.createTempDirectory("mcp-superseded");
		Path configuration = Files.createDirectories(installation.resolve("configuration"));
		Files.createDirectories(installation.resolve("plugins"));
		Files.createDirectories(configuration.resolve("mcp-substituted"));
		Files.writeString(installation.resolve("plugins/a_2.0.0.jar"), "");
		Files.writeString(configuration.resolve("mcp-substituted/a_1.0.0.jar"), "");
		Files.writeString(configuration.resolve("mcp-substituted/b_2.0.0.jar"), "");
		Files.writeString(configuration.resolve("mcp-substituted/c_1.0.0.jar"), "");
		String stale = "a,1.0.0," + configuration.resolve("mcp-substituted/a_1.0.0.jar").toUri() + ",4,false";
		String winner = "a,2.0.0,plugins/a_2.0.0.jar,4,false";
		// b's packed jar is the higher version, so it is the one running
		String inForce = "b,2.0.0," + configuration.resolve("mcp-substituted/b_2.0.0.jar").toUri() + ",4,false";
		// c's higher line names a jar that is not there, so dropping the packed one would leave none
		String onlyCopy = "c,1.0.0," + configuration.resolve("mcp-substituted/c_1.0.0.jar").toUri() + ",4,false";
		try {
			Map<Integer, String> superseded = SubstituteBundleTool.superseded(configuration,
					List.of("#version=1", stale, winner, inForce, "b,1.0.0,plugins/b_1.0.0.jar,4,false", onlyCopy,
							"c,3.0.0,plugins/c_3.0.0.jar,4,false"));

			assertEquals(Map.of(Integer.valueOf(1), winner), superseded);
		} finally {
			try (var walk = Files.walk(installation)) {
				for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Test
	void aBundleThatIsNotThereIsSaidToBeAbsentRatherThanUnreadable() throws Exception {
		Map<String, Object> running = TestFixture
				.parse(SubstituteBundleTool.running("com.example.no.such.bundle").toString());

		assertEquals(Boolean.FALSE, running.get("known"));
		assertTrue(String.valueOf(running.get("reason")).contains("no bundle called"),
				"got " + running.get("reason"));
	}
}
