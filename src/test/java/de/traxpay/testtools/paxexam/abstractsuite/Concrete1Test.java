package de.traxpay.testtools.paxexam.abstractsuite;

import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class Concrete1Test extends AbstractClass {

	@Override
	protected String getMessage() {
		return "1";
	}
}
