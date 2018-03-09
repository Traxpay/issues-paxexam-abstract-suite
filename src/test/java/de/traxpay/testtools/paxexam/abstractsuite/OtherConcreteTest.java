package de.traxpay.testtools.paxexam.abstractsuite;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

/** This one is not a subclass, just a positive test case.
 *
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class OtherConcreteTest {

	@Test
	public void otherTest() {
		String message = getMessage();
		System.out.println("\nGot message: " + message + "\n");
	}

	protected String getMessage() {
		return "3";
	}
}
