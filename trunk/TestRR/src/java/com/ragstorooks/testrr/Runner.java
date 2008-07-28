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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Runner implements ScenarioListener {
	private static final Log log = LogFactory.getLog(Runner.class);
	private static final int HUNDRED = 100;
	private static final Random randomGenerator = new Random();

	private int numberOfRuns;
    private int numberOfConcurrentStarts;
    private long coolDownPeriod;
    private boolean synchronizedScheduling;
    private int synchronizeWaitMilliSeconds = 1000;
    private long totalRunTimeMilliSeconds = 0;
    
    private Map<ScenarioBase, Integer> scenarioWeightings = null;
    private ScheduledExecutorService scheduledExecutorService = null;
    
    private ArrayList<ScenarioBase> weightedScenariosList;
    private Map<String, ScenarioResult> scenarioSuccesses = new HashMap<String, ScenarioResult>();
    private Map<String, ScenarioResult> scenarioFailures = new HashMap<String, ScenarioResult>();
    
    private CountDownLatch scenarioStartLatch;
    private CountDownLatch scenarioCompleteLatch;
    
    
    public void run() {
    	log.info(String.format("RPRunner(numberOfRuns=%d, numberOfConcurrentStarts=%d)", numberOfRuns, numberOfConcurrentStarts));
    	
    	normalizeWeightings();
    	
    	long startTime = System.currentTimeMillis();

    	if (synchronizedScheduling)
    		runWithSynchronizedScheduling();
    	else
    		runWithAdHocScheduling();
    	
    	waitForCoolDownPeriod();
    	
    	totalRunTimeMilliSeconds = System.currentTimeMillis() - startTime;

    }

    public double getSuccessRate() {
    	return 100.0*scenarioSuccesses.size()/numberOfRuns;
    }
    
    public Map<String, ScenarioResult> getScenarioSuccesses() {
    	return scenarioSuccesses;
    }
    
    public Map<String, ScenarioResult> getScenarioFailures() {
    	return scenarioFailures;
    }
    
    public void scenarioFailure(String scenarioId, String message) {
    	ScenarioResult result = scenarioFailures.get(scenarioId);
    	log.info(String.format("Scenario %s (%s) failed", scenarioId, result.getScenarioType().getSimpleName()));
    	setResults(result, message);
    	
    	notifyCountDownLatch();
    }

    public void scenarioSuccess(String scenarioId) {
    	ScenarioResult result = scenarioFailures.remove(scenarioId);
    	log.info(String.format("Scenario %s (%s) succeeded", scenarioId, result.getScenarioType().getSimpleName()));
    	setResults(result, null);
    	scenarioSuccesses.put(scenarioId, result);

    	notifyCountDownLatch();
    }

	private void setResults(ScenarioResult result, String message) {
		result.setEndTime(System.currentTimeMillis());
    	result.setMessage(message);
	}

	private void notifyCountDownLatch() {
		if (scenarioCompleteLatch != null)
    		scenarioCompleteLatch.countDown();
	}
    
	private void normalizeWeightings() {
		weightedScenariosList = new ArrayList<ScenarioBase>(HUNDRED);
    	int cumulativeWeights = 0;
    	for (Integer weight : scenarioWeightings.values())
    		cumulativeWeights += weight;
    	double normalizationFactor = 1.0 * HUNDRED / cumulativeWeights;
    	for (ScenarioBase scenario : scenarioWeightings.keySet()) {
    		double normalizedWeight = normalizationFactor * scenarioWeightings.get(scenario);
    		for (int i=0; i<normalizedWeight; i++)
    			weightedScenariosList.add(scenario);
    	}
	}
    
	private void runWithAdHocScheduling() {
		for (int i=0; i<numberOfRuns; i++) {
    		ScenarioBase scenario = getRandomScenario();
    		String scenarioId = getScenarioId(i, scenario);

    		scenarioFailures.get(scenarioId).setStartTime(System.currentTimeMillis());
    		runScenario(scenario, scenarioId);
    	}
	}

	private void runWithSynchronizedScheduling() {
		for (int i=0; i<numberOfRuns; i+=numberOfConcurrentStarts) {
			ScenarioBase scenario = null;
			String scenarioId = null;
			scenarioStartLatch = new CountDownLatch(numberOfConcurrentStarts);
			scenarioCompleteLatch = new CountDownLatch(numberOfConcurrentStarts);

			for (int j=0; j<numberOfConcurrentStarts; j++) {
				Map<String, ScenarioBase> concurrentStarts = new HashMap<String, ScenarioBase>(numberOfConcurrentStarts);
	    		scenario = getRandomScenario();
	    		scenarioId = getScenarioId(i+j, scenario);
	    		concurrentStarts.put(scenarioId, scenario);
	    		runSynchronizedScenario(scenario, scenarioId);
	    		scenarioStartLatch.countDown();
			}
			
			try {
				scenarioCompleteLatch.await(synchronizeWaitMilliSeconds, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				log.error(String.format("Interrupted while waiting for latch"), e);
			}
		}
	}

	private ScenarioBase getRandomScenario() {
		int random = randomGenerator.nextInt(HUNDRED);
		ScenarioBase scenario = weightedScenariosList.get(random); 
		scenario.setScenarioListener(this);
		scenario.setStartLatch(scenarioStartLatch);
		return scenario;
	}
	
	private String getScenarioId(int index, final ScenarioBase scenario) {
		String scenarioId = String.format("%d", index);
		scenarioFailures.put(scenarioId, new ScenarioResult(scenario.getClass(), "Failed to complete"));
		return scenarioId;
	}
	
	private void runScenario(final ScenarioBase scenario, final String scenarioId) {
		RunnableScenario runnableScenario = new RunnableScenario(scenario, scenarioId);
		scheduledExecutorService.execute(runnableScenario);
	}
	
	private void runSynchronizedScenario(final ScenarioBase scenario, final String scenarioId) {
		RunnableSynchronizedScenario runnableScenario = new RunnableSynchronizedScenario(scenario, scenarioId);
		scheduledExecutorService.execute(runnableScenario);
	}
	
	private void waitForCoolDownPeriod() {
		try {
			Thread.sleep(coolDownPeriod);
		} catch (InterruptedException e) {
			log.error("Interrupted while cooling down at the end", e);
		}
	}
	
	public void setNumberOfRuns(int numberOfRuns) {
		this.numberOfRuns = numberOfRuns;
	}
	
	public void setNumberOfConcurrentStarts(int numberOfConcurrentStarts) {
		this.numberOfConcurrentStarts = numberOfConcurrentStarts;
	}

	public void setScenarioWeightings(Map<ScenarioBase, Integer> scenarioWeightings) {
		this.scenarioWeightings = scenarioWeightings;
	}

	public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
		this.scheduledExecutorService = scheduledExecutorService;
	}

	public void setCoolDownPeriod(long coolDownPeriod) {
		this.coolDownPeriod = coolDownPeriod;
	}

	public void setSynchronizedScheduling(boolean synchronizedScheduling) {
		this.synchronizedScheduling = synchronizedScheduling;
	}

	public void setSynchronizeWaitMilliSeconds(int synchronizeWaitMilliSeconds) {
		this.synchronizeWaitMilliSeconds = synchronizeWaitMilliSeconds;
	}

	public long getTotalRunTimeMilliSeconds() {
		return totalRunTimeMilliSeconds;
	}
	
	private static class RunnableScenario implements Runnable {
		ScenarioBase scenario;
		String scenarioId;
		
		public RunnableScenario(ScenarioBase aScenario, String aScenarioId) {
			scenario = aScenario;
			scenarioId = aScenarioId;
		}
		
		public void run() {
			scenario.run(scenarioId);
		}
	}
	
	private static class RunnableSynchronizedScenario implements Runnable {
		ScenarioBase scenario;
		String scenarioId;
		
		public RunnableSynchronizedScenario(ScenarioBase aScenario, String aScenarioId) {
			scenario = aScenario;
			scenarioId = aScenarioId;
		}
		
		public void run() {
			scenario.runSynchronized(scenarioId);
		}
	}
}
