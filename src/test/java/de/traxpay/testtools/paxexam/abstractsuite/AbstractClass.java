package de.traxpay.testtools.paxexam.abstractsuite;

import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

public abstract class AbstractClass {

	protected abstract String getMessage();

	private static List<String> messages = new ArrayList<>();

	@Test
	public void mainTest() {
		String message = getMessage();
		System.out.println("\nGot message: " + message + "\n");

		// Store invocation results from subclasses in a list.
		// If an item repeats, it means the same subclass has been invoked more than once.

		if(!messages.contains(message)) {
			messages.add(getMessage());
		} else {
			fail("Message " + message + " already exists!");
		}
	}

	// The stripped-down Karaf container
	@Configuration
	public Option[] config() {
		MavenArtifactUrlReference karafUrl = CoreOptions.maven("org.apache.karaf","apache-karaf")
		                                                .versionAsInProject().type("zip");

		return new Option[] {
				// KarafDistributionOption.debugConfiguration("5005", true),
				karafDistributionConfiguration()
						.frameworkUrl(karafUrl)
						.unpackDirectory(new File("target/paxexam/"))
						.useDeployFolder(false),
				keepRuntimeFolder(),
				configureConsole().ignoreLocalConsole()
		};
	}

}
