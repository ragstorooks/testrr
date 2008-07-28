/*
Copyright 2008 Raghav Ramesh

This file is part of TestRR.

TestRR is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

TestRR is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with TestRR.  If not, see <http://www.gnu.org/licenses/>.
*/    	
    	
package com.ragstorooks.testrr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import com.ragstorooks.testrr.Runner;
import com.ragstorooks.testrr.ScenarioBase;
import com.ragstorooks.testrr.ScenarioResult;


public class RunnerTest {
	private static final String FAILED_THE_MOD_6_TEST = "Failed the mod 6 test";

	private Runner runner;
	private MyRPScenario scenario1;
	private MyRPScenario scenario2;
	private MyRPScenario scenario3;
	private Map<ScenarioBase, Integer> scenarioWeightings;
	private ScheduledExecutorService executorService;
	private Collection<String> successes;
	private Collection<String> failures;

	private class MyRPScenario extends ScenarioBase {
		private int runCount = 0;
		
		@Override
		public void run(String scenarioId) {
			runCount++;
			
			if ((runCount%6) == 0) {
				failures.add(scenarioId);
				getScenarioListener().scenarioFailure(scenarioId, FAILED_THE_MOD_6_TEST);
			}
			else {
				successes.add(scenarioId);
				getScenarioListener().scenarioSuccess(scenarioId);
			}
		}

		public int getRunCount() {
			return runCount;
		}
	}
	
	@Before
	public void before() {
		scenario1 = new MyRPScenario();
		scenario2 = new MyRPScenario();
		scenario3 = new MyRPScenario();
		successes = new ArrayList<String>();
		failures = new ArrayList<String>();
		
		scenarioWeightings = new HashMap<ScenarioBase, Integer>();
		scenarioWeightings.put(scenario1, 1);
		scenarioWeightings.put(scenario2, 1);
		scenarioWeightings.put(scenario3, 1);
		
		executorService = EasyMock.createMock(ScheduledExecutorService.class);
		executorService.execute(EasyMock.isA(Runnable.class));
		EasyMock.expectLastCall().andStubAnswer(new IAnswer<Object>() {
			public Object answer() throws Throwable {
				((Runnable)EasyMock.getCurrentArguments()[0]).run();
				return null;
			}
		});
		EasyMock.replay(executorService);
		
		runner = new Runner();
		runner.setNumberOfConcurrentStarts(1);
		runner.setNumberOfRuns(100);
		runner.setScenarioWeightings(scenarioWeightings);
		runner.setScheduledExecutorService(executorService);
	}
	
	@Test
	public void testNumberOfRuns() throws Exception {
		// setup
		
		// act
		runner.run();
		
		// assert
		assertEquals(100, scenario1.getRunCount() + scenario2.getRunCount() + scenario3.getRunCount());
	}
	
	@Test
	public void testWeightingsEqual() throws Exception {
		// setup
		
		// act
		runner.run();
		
		// assert
		assertEquals(100, scenario1.getRunCount() + scenario2.getRunCount() + scenario3.getRunCount());
		assertTrue(isWeightingApproximatelyOk(100, scenario1.getRunCount(), 33.3));
		assertTrue(isWeightingApproximatelyOk(100, scenario2.getRunCount(), 33.3));
		assertTrue(isWeightingApproximatelyOk(100, scenario3.getRunCount(), 33.3));
	}

	@Test
	public void testWeightingsUnequal() throws Exception {
		// setup
		scenarioWeightings.put(scenario1, 10);
		scenarioWeightings.put(scenario2, 6);
		
		// act
		runner.run();
		
		// assert
		assertEquals(100, scenario1.getRunCount() + scenario2.getRunCount() + scenario3.getRunCount());
		assertTrue(isWeightingApproximatelyOk(100, scenario1.getRunCount(), 59));
		assertTrue(isWeightingApproximatelyOk(100, scenario2.getRunCount(), 35));
		assertTrue(isWeightingApproximatelyOk(100, scenario3.getRunCount(), 6));
	}
	
	@Test
	public void testScenariosAreRunUsingExecutorService() throws Exception {
		// setup
		executorService = EasyMock.createMock(ScheduledExecutorService.class);
		executorService.execute(EasyMock.isA(Runnable.class));
		EasyMock.expectLastCall().times(100);
		EasyMock.replay(executorService);
		runner.setScheduledExecutorService(executorService);
		
		// act
		runner.run();
		
		// assert
		EasyMock.verify(executorService);
	}
	
	@Test
	public void testScenarioSuccesses() throws Exception {
		// setup
		
		// act
		runner.run();
		
		// assert
		assertTrue(Math.abs(82.3 - runner.getSuccessRate()) < 10);
		for (String scenarioId : runner.getScenarioSuccesses().keySet()) {
			assertTrue(String.format("Unexpected scenario %s was succesful", scenarioId), successes.contains(scenarioId));
			successes.remove(scenarioId);
		}
		assertEquals(0, successes.size());
	}
	
	@Test
	public void testScenarioFailures() throws Exception {
		// setup
		
		// act
		runner.run();
		
		// assert
		assertTrue(Math.abs(82.3 - runner.getSuccessRate()) < 10);
		for (String scenarioId : runner.getScenarioFailures().keySet()) {
			assertTrue(String.format("Unexpected scenario %s failed", scenarioId), failures.contains(scenarioId));
			failures.remove(scenarioId);
		}
		assertEquals(0, failures.size());
	}
	
	@Test
	public void testFailureMessage() throws Exception {
		// setup
		
		// act
		runner.run();
		
		// assert
		for (ScenarioResult result : runner.getScenarioFailures().values()) {
			assertEquals(FAILED_THE_MOD_6_TEST, result.getMessage());
		}
	}
	
	@Test
	public void testCoolDownPeriod() throws Exception {
		// setup
		runner.setNumberOfRuns(0);
		runner.setCoolDownPeriod(500);
		long startTime = System.currentTimeMillis();
		
		// act
		runner.run();
		
		// assert
		assertTrue(System.currentTimeMillis() - startTime >= 500);
	}
	
	@Test
	public void testTotalTimeTaken() throws Exception {
		// setup
		runner.setCoolDownPeriod(0);
		
		// act
		runner.run();
		
		// assert
		assertTrue(runner.getTotalRunTimeMilliSeconds() > 0);
	}

	private boolean isWeightingApproximatelyOk(int totalRuns, int scenarioRuns, double weighting) {
		double trueWeighting = 100.0*scenarioRuns/totalRuns;
		return Math.abs(trueWeighting - weighting) <= 10;
	}
}
