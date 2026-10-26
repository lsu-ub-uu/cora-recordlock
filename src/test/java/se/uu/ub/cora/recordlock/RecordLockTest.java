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
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import se.uu.ub.cora.recordlock.spy.DatabaseFacadeSpy;
import se.uu.ub.cora.recordlock.spy.SqlDatabaseFactorySpy;
import se.uu.ub.cora.spider.recordlock.RecordLock;
import se.uu.ub.cora.spider.recordlock.RecordLockException;
import se.uu.ub.cora.sqldatabase.SqlConflictException;
import se.uu.ub.cora.sqldatabase.SqlDatabaseException;

public class RecordLockTest {
	private static final String INSERT_SQL = "insert into recordlock (type, id) values (?, ?);";
	private static final String DELETE_SQL = "delete from recordlock where type = ? and id = ?;";
	private static final String SOME_TYPE = "someType";
	private static final String SOME_ID = "someId";
	private static final long RETRY_INTERVAL_MILLIS = 50;
	private static final long MAX_WAIT_MILLIS = 60_000;

	private SqlDatabaseFactorySpy databaseFactory;
	private DatabaseFacadeSpy databaseFacade;
	private SleeperSpy sleeper;
	private TimeSourceSpy timeSource;
	private RecordLock recordLock;

	@BeforeMethod
	private void beforeMethod() {
		databaseFactory = new SqlDatabaseFactorySpy();
		databaseFacade = new DatabaseFacadeSpy();
		databaseFactory.MRV.setDefaultReturnValuesSupplier("factorDatabaseFacade",
				() -> databaseFacade);
		sleeper = new SleeperSpy();
		timeSource = new TimeSourceSpy();
	}

	@Test
	public void testRecordLockImlementsRecordLock() {
		recordLock = getRecordLock();

		assertTrue(recordLock instanceof RecordLock);
	}

	@Test
	public void testErrorOnCreate() {
		RuntimeException exception = new RuntimeException("some error!");
		databaseFactory.MRV.setAlwaysThrowException("factorDatabaseFacade", exception);

		try {
			getRecordLock();
			fail("should have thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(),
					"Could not factor facade lock for type: someType and id: someId.");
			assertSame(e.getCause(), exception);
		}
	}

	@Test
	public void testCloseCallsCloseInDatabaseFacade() {
		recordLock = getRecordLock();

		recordLock.close();

		databaseFacade.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 2);
		assertExecutedSqlWithTypeAndId(1, DELETE_SQL);
		databaseFacade.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testOnlyForTestGetters() {
		recordLock = getRecordLock();
		RecordLockImp recordLockImp = (RecordLockImp) recordLock;

		assertSame(recordLockImp.onlyForTestGetDatabaseFactory(), databaseFactory);
		assertSame(recordLockImp.onlyForTestGetSleeper(), sleeper);
		assertSame(recordLockImp.onlyForTestGetTimeSource(), timeSource);
	}

	@Test
	public void testGettingRecordLockAcquiresLockWithoutCheckingIfItExists() {
		recordLock = getRecordLock();

		databaseFacade.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 1);
		assertExecutedSqlWithTypeAndId(0, INSERT_SQL);
		databaseFacade.MCR.assertMethodNotCalled("readUsingSqlAndValues");
		databaseFacade.MCR.assertMethodNotCalled("readOneRowOrFailUsingSqlAndValues");
		sleeper.MCR.assertMethodNotCalled("sleepMilliseconds");
	}

	@Test
	public void testGettingRecordLockRetriesEvery50msUntilLockIsInserted() {
		throwConflictForNumberOfInserts(3);

		recordLock = getRecordLock();

		databaseFacade.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 4);
		for (int call = 0; call < 4; call++) {
			assertExecutedSqlWithTypeAndId(call, INSERT_SQL);
		}
		sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 3);
		for (int call = 0; call < 3; call++) {
			sleeper.MCR.assertParameters("sleepMilliseconds", call, RETRY_INTERVAL_MILLIS);
		}
	}

	@Test
	public void testGettingRecordLockReadsStartTimeOnceAndTimeAfterEachConflict() {
		throwConflictForNumberOfInserts(2);

		recordLock = getRecordLock();

		timeSource.MCR.assertNumberOfCallsToMethod("currentTimeMillis", 3);
	}

	@Test
	public void testGettingRecordLockStillRetriesJustBeforeTimeLimit() {
		throwConflictForNumberOfInserts(1);
		timeSource.MRV.setReturnValues("currentTimeMillis",
				List.of(1_000L, 1_000L + MAX_WAIT_MILLIS - 1));

		recordLock = getRecordLock();

		databaseFacade.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 2);
		sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 1);
	}

	@Test
	public void testGettingRecordLockClosesFacadeWhenTimeLimitIsReached() {
		SqlConflictException conflict = SqlConflictException.withMessage("duplicate key");
		databaseFacade.MRV.setAlwaysThrowException("executeSqlWithValues", conflict);
		timeSource.MRV.setReturnValues("currentTimeMillis",
				List.of(1_000L, 1_010L, 1_000L + MAX_WAIT_MILLIS));

		try {
			getRecordLock();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(), "Could not acquire lock for type: " + SOME_TYPE
					+ " and id: " + SOME_ID + " within " + MAX_WAIT_MILLIS + " ms.");
			assertSame(e.getCause(), conflict);
		}
		databaseFacade.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 2);
		databaseFacade.MCR.assertMethodWasCalled("close");
		sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 1);
	}

	@Test
	public void testGettingRecordLockWrapsOtherDatabaseErrorsAndClosesFacade() {
		SqlDatabaseException error = SqlDatabaseException.withMessage("some error");
		databaseFacade.MRV.setAlwaysThrowException("executeSqlWithValues", error);

		try {
			getRecordLock();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertSame(e.getCause(), error);
		}
		databaseFacade.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 1);
		databaseFacade.MCR.assertMethodWasCalled("close");
		sleeper.MCR.assertMethodNotCalled("sleepMilliseconds");
	}

	@Test
	public void testGettingRecordLockPreservesAcquisitionAndCloseFailures() {
		SqlDatabaseException acquisitionError = SqlDatabaseException.withMessage("some error");
		SqlDatabaseException closeError = SqlDatabaseException.withMessage("close failed");
		databaseFacade.MRV.setAlwaysThrowException("executeSqlWithValues", acquisitionError);
		databaseFacade.MRV.setAlwaysThrowException("close", closeError);

		try {
			getRecordLock();
			fail("RecordLockException should have been thrown");
		} catch (RecordLockException e) {
			assertSame(e.getCause(), acquisitionError);
			assertSame(e.getSuppressed()[0], closeError);
		}
		databaseFacade.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testCloseClosesFacadeEvenWhenReleaseFails() {
		SqlDatabaseException error = SqlDatabaseException.withMessage("release failed");
		AtomicInteger calls = new AtomicInteger();
		databaseFacade.MRV.setDefaultReturnValuesSupplier("executeSqlWithValues", () -> {
			if (calls.getAndIncrement() == 1) {
				throw error;
			}
			return 1;
		});
		recordLock = getRecordLock();

		try {
			recordLock.close();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertSame(e.getCause(), error);
		}
		databaseFacade.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testCloseWithError() {
		SqlDatabaseException error = SqlDatabaseException.withMessage("close failed");
		databaseFacade.MRV.setAlwaysThrowException("close", error);
		recordLock = getRecordLock();

		try {
			recordLock.close();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertSame(e.getCause(), error);
		}
		databaseFacade.MCR.assertMethodWasCalled("close");
	}

	private RecordLock getRecordLock() {
		return RecordLockImp.usingDatabaseFacadeAndSleeperAndTimeSource(databaseFactory, sleeper,
				timeSource, SOME_TYPE, SOME_ID);
	}

	private void assertExecutedSqlWithTypeAndId(int callNumber, String sql) {
		databaseFacade.MCR.assertParameter("executeSqlWithValues", callNumber, "sql", sql);
		databaseFacade.MCR.assertParameterAsEqual("executeSqlWithValues", callNumber, "values",
				List.of(SOME_TYPE, SOME_ID));
	}

	private void throwConflictForNumberOfInserts(int numberOfConflicts) {
		AtomicInteger calls = new AtomicInteger();
		databaseFacade.MRV.setDefaultReturnValuesSupplier("executeSqlWithValues", () -> {
			if (calls.getAndIncrement() < numberOfConflicts) {
				throw SqlConflictException.withMessage("duplicate key");
			}
			return 1;
		});
	}
}
