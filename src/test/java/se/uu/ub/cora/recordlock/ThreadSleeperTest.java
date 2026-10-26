/*
 * Copyright 2026 Uppsala University Library
 *
 * This file is part of Cora.
 *
 *     Cora is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Cora is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Cora.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.uu.ub.cora.recordlock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import se.uu.ub.cora.spider.recordlock.RecordLockException;

public class ThreadSleeperTest {
	private ThreadSleeper sleeper = new ThreadSleeper();

	@AfterMethod
	private void afterMethod() {
		Thread.interrupted();
	}

	@Test
	public void testSleepWaitsAtLeastGivenMilliseconds() {
		long start = System.nanoTime();

		sleeper.sleepMilliseconds(20);

		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
		assertTrue(elapsedMillis >= 20);
	}

	@Test
	public void testInterruptedSleepThrowsRecordLockExceptionAndKeepsInterruptFlag() {
		Thread.currentThread().interrupt();

		try {
			sleeper.sleepMilliseconds(1_000);
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(), "Interrupted while waiting to retry.");
			assertTrue(e.getCause() instanceof InterruptedException);
			assertTrue(Thread.currentThread().isInterrupted());
		}
	}
}
