# issues-paxexam-abstract-suite
Test case for the PaxExam Abstract class PerSuite issue


This is a simple PaxExam Karaf based test for the PerSuite + Abstract test class bug.

AbstractClass executes a Test, and the 2 concrete classes return each a different string.

Expected result: Two different strings in abstract class from two different concrete test classes.

Actual result: One of the concrete classes is executed twice, the other not executed at all, so the same message appears in both test executions.

		
Run the test:
mvn clean install


Workaround:
An analysis of the bug and possible workaround can be found in TraxpayProbeRunner class (see fillChildren() method).

To apply the workaround, replace all occurrences of:

@RunWith(PaxExam.class)

with

@RunWith(TraxpayPaxExam.class)

All tests should pass then.