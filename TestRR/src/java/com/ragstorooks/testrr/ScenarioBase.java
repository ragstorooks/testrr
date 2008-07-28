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

import java.util.concurrent.CountDownLatch;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class ScenarioBase {
	private static final Log log = LogFactory.getLog(ScenarioBase.class);

	private ScenarioListener scenarioListener;
	private CountDownLatch startLatch = null;

	public ScenarioListener getScenarioListener() {
		return scenarioListener;
	}

	public void setScenarioListener(ScenarioListener scenarioListener) {
		this.scenarioListener = scenarioListener;
	}
	
	public void setStartLatch(CountDownLatch latch) {
		this.startLatch = latch;
	}
	
	private void awaitBarrier(String scenarioId) {
		try {
			startLatch.await();
		} catch (InterruptedException e) {
			log.error(String.format("Scenario %s interrupted while waiting for latch", scenarioId), e);
		}
	}
	
	public void runSynchronized(String scenarioId) {
		awaitBarrier(scenarioId);
		run(scenarioId);
	}

	public abstract void run(String scenarioId);
}
