/*
 * Copyright 2010 - 2012 Toni Menzel, Harald Wellmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.exam.junit.impl;

import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.internal.runners.statements.Fail;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.ops4j.pax.exam.*;
import org.ops4j.pax.exam.spi.ExamReactor;
import org.ops4j.pax.exam.spi.StagedExamReactor;
import org.ops4j.pax.exam.spi.reactors.ReactorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;



/* Copy paste of the ProbeRunner (4.11.0), see abstract-suite test.
 * Implements a workaround in fillChildren() which fixes the Abstract class PerSuite bug.
 */

/**
 * Probe runner, executing tests via an invoker. Used for all operation modes except CDI.
 *
 * @author Toni Menzel
 * @author Harald Wellmann
 */
public class TraxpayProbeRunner extends BlockJUnit4ClassRunner {

    private static final Logger LOG = LoggerFactory.getLogger(TraxpayProbeRunner.class);

    /**
     * Reactor manager singleton.
     */
    private ReactorManager manager;

    /**
     * Staged reactor for this test class. This may actually be a reactor already staged for a
     * previous test class, depending on the reactor strategy.
     */
    private StagedExamReactor stagedReactor;

    private Map<FrameworkMethod, TestAddress> methodToTestAddressMap = new LinkedHashMap<FrameworkMethod, TestAddress>();

    public TraxpayProbeRunner(Class<?> klass) throws InitializationError {
        super(klass);
        LOG.info("creating PaxExam runner for {}", klass);
        try {
            Object testClassInstance = klass.newInstance();
            manager = ReactorManager.getInstance();
            ExamReactor examReactor = manager.prepareReactor(klass, testClassInstance);
            addTestsToReactor(examReactor, klass, testClassInstance);
            stagedReactor = manager.stageReactor();
        }
        catch (InstantiationException | IllegalAccessException | IOException | ExamConfigurationException exc) {
            throw new InitializationError(exc);
        }
    }

    /**
     * We decorate the super method by reactor setup and teardown. This method is called once per
     * class. Note that the given reactor strategy decides whether or not the setup and teardown
     * actually happens at this level.
     */
    @Override
    public void run(RunNotifier notifier) {
        LOG.info("running test class {}", getTestClass().getName());
        Class<?> testClass = getTestClass().getJavaClass();
        try {
            manager.beforeClass(stagedReactor, testClass);
            super.run(notifier);
        }
        // CHECKSTYLE:SKIP : catch all wanted
        catch (Throwable e) {
            // rethrowing the exception does not help, we have to use the notifier here
            Description description = Description.createSuiteDescription(testClass);
            notifier.fireTestFailure(new Failure(description, e));
        }
        finally {
            manager.afterClass(stagedReactor, testClass);
        }
    }

    /**
     * Override to avoid running BeforeClass and AfterClass by the driver. They shall only be run by
     * the container when using a probe invoker.
     */
    @Override
    protected Statement classBlock(final RunNotifier notifier) {
        Statement statement = childrenInvoker(notifier);
        return statement;
    }

    /**
     * Override to avoid running Before, After and Rule methods by the driver. They shall only be
     * run by the container when using a probe invoker.
     */
    @Override
    protected Statement methodBlock(FrameworkMethod method) {
        Object test;
        try {
            ReflectiveCallable reflectiveCallable = new ReflectiveCallable() {

                @Override
                // CHECKSTYLE:SKIP : Base class API
                protected Object runReflectiveCall() throws Throwable {
                    return createTest();
                }
            };
            test = reflectiveCallable.run();
        }
        // CHECKSTYLE:SKIP : ReflectiveCallable API
        catch (Throwable e) {
            return new Fail(e);
        }

        Statement statement = methodInvoker(method, test);
        return statement;
    }

    /**
     * When using a probe invoker, we replace the test methods of this class by a potentially larger
     * set of decorated test methods. Each original test method may give rise to multiple copies per
     * test container or configuration.
     */
    @Override
    protected List<FrameworkMethod> getChildren() {
        if (methodToTestAddressMap.isEmpty()) {
            fillChildren();
        }
        return new ArrayList<FrameworkMethod>(methodToTestAddressMap.keySet());
    }

    private void fillChildren() {
        Set<TestAddress> targets = stagedReactor.getTargets();
        TestDirectory testDirectory = TestDirectory.getInstance();
        boolean mangleMethodNames = manager.getNumConfigurations() > 1;
        for (TestAddress address : targets) {

	        ///////////////////////////////////////////////////////////////////////////////////////////////
        	// BEGIN MF 08.03.2018 PAX-EXAM NNN workaround
	        //
	        // Problem: When using PerSuite, the stagedReactor is an instance of SingletonStagedReactor.
	        // With each new test class, new probes will be added to ReactorManager, and the SingletonStagedReactor
	        // references those, so in the end getTargets() always returns everything (all addresses).
	        // So we cycle here through all addresses from all test classes.
	        //
	        // If an abstract class contains a @Test method, and current class is extending it without overriding the method,
	        // then the FrameworkMethod declaringClass will be the abstract superclass instead of the current test class.
	        // Thus, isAssignableFrom() check will always work for all subclasses.
	        //
	        // Consequence: methodToTestAddressMap.put() is executed in every iteration of this loop,
	        // resulting in last iteration address to always be used, while all others are overwritten and ignored.
	        // So only the "last" subclass will ever be invoked. This is difficult to notice, because the ProbeRunner
	        // still "thinks" it's executing a proper class (nothing can be inferred from logs etc.).
	        //
	        // Note that in case of PerClass, it's very similar: EagerSingleStagedReactor instances contain cumulatively
	        // all previous probes from already visited classes, only their "map" is restricting the getTargets() list,
	        // which is not the case for SingletonStagedReactor (which always sees all probes from reactor).
	        // So the similar problem exists there, but is only accidentally avoided, since the methodToTestAddressMap is always
	        // overwritten with the "correct" address, the last one that the stagedReactor knows about,
	        // which is accidentally its own class.
	        //
	        ///////////////////////////////////////////////////////////////////////////////////////////////
	        //
	        // WORKAROUND (not at all sure if correct - works in our particular case though):
	        //
	        // An address which is not pointing to our current class is not relevant, so skip it.
	        // We use the fact that this ProbeRunner is creating Addresses (in addTestsToReactor()) ,
	        // so we're pretty sure it should match.

	        if( ! address.caption().startsWith( getTestClass().getJavaClass().getName() ) )
	        	continue;

	        // END MF 08.03.2018 PAX-EXAM NNN workaround
	        ///////////////////////////////////////////////////////////////////////////////////////////////

            FrameworkMethod frameworkMethod = (FrameworkMethod) manager.lookupTestMethod(address
                .root());

            // The reactor may contain targets which do not belong to the current test class
            if (frameworkMethod == null) {
                continue;
            }
            Class<?> frameworkMethodClass = frameworkMethod.getMethod().getDeclaringClass();
            String className = getTestClass().getJavaClass().getName();
            String methodName = frameworkMethod.getName();

            if (frameworkMethodClass.isAssignableFrom(getTestClass().getJavaClass())) {
                FrameworkMethod method = mangleMethodNames ? new DecoratedFrameworkMethod(address,
                    frameworkMethod) : frameworkMethod;
                testDirectory.add(address, new TestInstantiationInstruction(className + ";"
                    + methodName));

                methodToTestAddressMap.put(method, address);
            }
        }
    }

    /**
     * Adds test methods to the reactor, mapping method names to test addresses which are used by
     * the probe invoker.
     * <p>
     * Note that when a collection of test classes is passed to an external JUnit runner like
     * Eclipse or Maven Surefire, this method is invoked (via the constructor of this runner) for
     * each class <em>before</em> the {@link #run(RunNotifier)} method is invoked for any class.
     * <p>
     * This way, we can register all test methods in the reactor before the actual test execution
     * starts.
     *
     * @param reactor
     * @param testClass
     * @param testClassInstance
     * @throws IOException
     * @throws ExamConfigurationException
     */
    private void addTestsToReactor(ExamReactor reactor, Class<?> testClass, Object testClassInstance)
        throws IOException, ExamConfigurationException {
        TestProbeBuilder probe = manager.createProbeBuilder(testClassInstance);

        // probe.setAnchor( testClass );
        for (FrameworkMethod s : super.getChildren()) {
            // record the method -> adress matching
            TestAddress address = delegateTest(testClassInstance, probe, s);
            if (address == null) {
                address = probe.addTest(testClass, s.getMethod().getName());
            }
            manager.storeTestMethod(address, s);
        }
        reactor.addProbe(probe);
    }

    /**
     * FIXME What is this doing, and what is the use case? Parameterized methods break JUnit's
     * default behaviour, and most of these non-standard signatures introduced in 2.0.0 have been
     * dropped since 2.3.0.
     *
     * @param testClassInstance
     * @param probe
     * @param s
     * @return test address
     */
    private TestAddress delegateTest(Object testClassInstance, TestProbeBuilder probe,
        FrameworkMethod s) {
        try {
            Class<?>[] types = s.getMethod().getParameterTypes();
            if (types.length == 1 && types[0].isAssignableFrom(TestProbeBuilder.class)) {
                // do some backtracking:
                return (TestAddress) s.getMethod().invoke(testClassInstance, probe);

            }
            else {
                return null;
            }
        }
        // CHECKSTYLE:SKIP : catch all wanted
        catch (Throwable e) {
            throw new TestContainerException("Problem delegating to test.", e);
        }
    }

    /**
     * When using a probe invoker, we replace the super method and invoke the test method indirectly
     * via the reactor.
     */
    @Override
    protected synchronized Statement methodInvoker(final FrameworkMethod method, final Object test) {
        return new Statement() {

            @Override
            // CHECKSTYLE:SKIP : Statement API
            public void evaluate() throws Throwable {
                TestAddress address = methodToTestAddressMap.get(method);
                TestAddress root = address.root();

                LOG.debug("Invoke " + method.getName() + " @ " + address + " Arguments: "
                    + root.arguments());
                try {
                    stagedReactor.invoke(address);
                }
                // CHECKSTYLE:SKIP : StagedExamReactor API
                catch (Exception e) {
                    Throwable t = ExceptionHelper.unwind(e);
                    throw t;
                }
            }
        };
    }
}
