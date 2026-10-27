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

	private SqlDatabaseFactorySpy dbFactory;
	private DatabaseFacadeSpy dbFacadeAquire;
	private DatabaseFacadeSpy dbFacadeRelease;
	private SleeperSpy sleeper;
	private TimeSourceSpy timeSource;
	private RecordLock recordLock;

	@BeforeMethod
	private void beforeMethod() {
		dbFactory = new SqlDatabaseFactorySpy();
		dbFacadeAquire = new DatabaseFacadeSpy();
		dbFacadeRelease = new DatabaseFacadeSpy();
		dbFactory.MRV.setReturnValues("factorDatabaseFacade",
				List.of(dbFacadeAquire, dbFacadeRelease));
		sleeper = new SleeperSpy();
		timeSource = new TimeSourceSpy();
	}

	@Test
	public void testRecordLockFactory() {
		RecordLockImp recordLockImp = RecordLockImp.usingDatabaseFactory(dbFactory, SOME_TYPE,
				SOME_ID);

		assertTrue(recordLockImp instanceof RecordLock);
		assertSame(recordLockImp.onlyForTestGetDatabaseFactory(), dbFactory);
		assertTrue(recordLockImp.onlyForTestGetSleeper() instanceof ThreadSleeper);
		assertTrue(recordLockImp.onlyForTestGetTimeSource() instanceof SystemTimeSource);
	}

	@Test
	public void testRecordLockFactorySendingSleeperTimeSource() {
		RecordLockImp recordLockImp = createRecordLock();

		assertTrue(recordLockImp instanceof RecordLock);
		assertSame(recordLockImp.onlyForTestGetDatabaseFactory(), dbFactory);
		assertSame(recordLockImp.onlyForTestGetSleeper(), sleeper);
		assertSame(recordLockImp.onlyForTestGetTimeSource(), timeSource);
	}

	@Test
	public void testRecordLockImlementsRecordLock() {
		recordLock = createRecordLock();

		assertTrue(recordLock instanceof RecordLock);
	}

	private RecordLockImp createRecordLock() {
		return RecordLockImp.usingDatabaseFacadeAndSleeperAndTimeSource(dbFactory, sleeper,
				timeSource, SOME_TYPE, SOME_ID);
	}

	@Test
	public void testErrorOnCreate() {
		RuntimeException exception = new RuntimeException("some error!");
		dbFactory.MRV.setAlwaysThrowException("factorDatabaseFacade", exception);

		try {
			createRecordLock();
			fail("should have thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(),
					"Could not acquire lock for type: someType and id: someId.");
			assertSame(e.getCause(), exception);
		}
	}

	@Test
	public void testGettingRecordLockAcquiresLock() {
		recordLock = createRecordLock();

		dbFacadeAquire.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 1);
		assertAquireExecutedSqlWithTypeAndId(0, INSERT_SQL);
		dbFacadeAquire.MCR.assertMethodNotCalled("readUsingSqlAndValues");
		dbFacadeAquire.MCR.assertMethodNotCalled("readOneRowOrFailUsingSqlAndValues");
		sleeper.MCR.assertMethodNotCalled("sleepMilliseconds");
		dbFacadeAquire.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testGettingRecordLockRetriesEvery50msUntilLockIsInserted() {
		throwConflictForNumberOfInserts(3);

		recordLock = createRecordLock();

		dbFacadeAquire.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 4);
		for (int call = 0; call < 4; call++) {
			assertAquireExecutedSqlWithTypeAndId(call, INSERT_SQL);
		}
		sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 3);
		for (int call = 0; call < 3; call++) {
			sleeper.MCR.assertParameters("sleepMilliseconds", call, RETRY_INTERVAL_MILLIS);
		}
	}

	@Test
	public void testGettingRecordLockReadsStartTimeOnceAndTimeAfterEachConflict() {
		throwConflictForNumberOfInserts(2);

		recordLock = createRecordLock();

		timeSource.MCR.assertNumberOfCallsToMethod("currentTimeMillis", 3);
	}

	@Test
	public void testGettingRecordLockStillRetriesJustBeforeTimeLimit() {
		throwConflictForNumberOfInserts(1);
		timeSource.MRV.setReturnValues("currentTimeMillis",
				List.of(1_000L, 1_000L + MAX_WAIT_MILLIS - 1));

		recordLock = createRecordLock();

		dbFacadeAquire.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 2);
		sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 1);
	}

	@Test
	public void testGettingRecordLockClosesFacadeWhenTimeLimitIsReached() {
		SqlConflictException conflict = SqlConflictException.withMessage("duplicate key");
		dbFacadeAquire.MRV.setAlwaysThrowException("executeSqlWithValues", conflict);
		timeSource.MRV.setReturnValues("currentTimeMillis",
				List.of(1_000L, 1_010L, 1_000L + MAX_WAIT_MILLIS));

		try {
			createRecordLock();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(), "Could not acquire lock for type: " + SOME_TYPE
					+ " and id: " + SOME_ID + " within " + MAX_WAIT_MILLIS + " ms.");
			assertSame(e.getCause(), conflict);
		}
		dbFacadeAquire.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 2);
		dbFacadeAquire.MCR.assertMethodWasCalled("close");
		sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 1);
	}

	@Test
	public void testGettingRecordLockWrapsOtherDatabaseErrorsAndClosesFacade() {
		SqlDatabaseException error = SqlDatabaseException.withMessage("some error");
		dbFacadeAquire.MRV.setAlwaysThrowException("executeSqlWithValues", error);

		try {
			createRecordLock();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertSame(e.getCause(), error);
		}
		dbFacadeAquire.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 1);
		dbFacadeAquire.MCR.assertMethodWasCalled("close");
		sleeper.MCR.assertMethodNotCalled("sleepMilliseconds");
	}

	@Test
	public void testGettingRecordLockPreservesAcquisitionAndCloseFailures() {
		SqlDatabaseException acquisitionError = SqlDatabaseException.withMessage("some error");
		SqlDatabaseException closeError = SqlDatabaseException.withMessage("close failed");
		dbFacadeAquire.MRV.setAlwaysThrowException("executeSqlWithValues", acquisitionError);
		dbFacadeAquire.MRV.setAlwaysThrowException("close", closeError);

		try {
			createRecordLock();
			fail("RecordLockException should have been thrown");
		} catch (RecordLockException e) {
			assertSame(e.getCause(), acquisitionError);
			assertSame(e.getCause().getSuppressed()[0], closeError);
		}
		dbFacadeAquire.MCR.assertMethodWasCalled("close");
	}

	private void assertAquireExecutedSqlWithTypeAndId(int callNumber, String sql) {
		dbFacadeAquire.MCR.assertParameter("executeSqlWithValues", callNumber, "sql", sql);
		dbFacadeAquire.MCR.assertParameterAsEqual("executeSqlWithValues", callNumber, "values",
				List.of(SOME_TYPE, SOME_ID));
	}

	private void throwConflictForNumberOfInserts(int numberOfConflicts) {
		AtomicInteger calls = new AtomicInteger();
		dbFacadeAquire.MRV.setDefaultReturnValuesSupplier("executeSqlWithValues", () -> {
			if (calls.getAndIncrement() < numberOfConflicts) {
				throw SqlConflictException.withMessage("duplicate key");
			}
			return 1;
		});
	}

	@Test
	public void testCloseReleaseLockAndCloseDatabaseFacade() {
		recordLock = createRecordLock();

		recordLock.close();

		dbFacadeRelease.MCR.assertNumberOfCallsToMethod("executeSqlWithValues", 1);
		assertReleaseExecutedSqlWithTypeAndId(0, DELETE_SQL);
		dbFacadeRelease.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testCloseClosesFacadeCreatesRecordLockExceptionWithErrorAsCauseWhenReleaseFails() {
		SqlDatabaseException error = SqlDatabaseException.withMessage("release failed");
		dbFacadeRelease.MRV.setThrowException("executeSqlWithValues", error, DELETE_SQL,
				List.of(SOME_TYPE, SOME_ID));
		recordLock = createRecordLock();

		try {
			recordLock.close();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(),
					"Could not release lock for type: someType and id: someId.");
			assertSame(e.getCause(), error);
		}
		dbFacadeRelease.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testCloseErrorOnCloseFacade() {
		SqlDatabaseException error = SqlDatabaseException.withMessage("close failed");
		dbFacadeRelease.MRV.setAlwaysThrowException("close", error);
		recordLock = createRecordLock();

		try {
			recordLock.close();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(),
					"Could not release lock for type: someType and id: someId.");
			assertSame(e.getCause(), error);
		}
		dbFacadeRelease.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testCloseErrorOnReleaseAndCloseFacade() {
		SqlDatabaseException errorRelease = SqlDatabaseException.withMessage("release failed");
		dbFacadeRelease.MRV.setThrowException("executeSqlWithValues", errorRelease, DELETE_SQL,
				List.of(SOME_TYPE, SOME_ID));
		SqlDatabaseException errorClose = SqlDatabaseException.withMessage("close failed");
		dbFacadeRelease.MRV.setAlwaysThrowException("close", errorClose);
		recordLock = createRecordLock();

		try {
			recordLock.close();
			fail("RecordLockException should have been thrown");
		} catch (Exception e) {
			assertTrue(e instanceof RecordLockException);
			assertEquals(e.getMessage(),
					"Could not release lock for type: someType and id: someId.");
			assertSame(e.getCause(), errorRelease);
			assertSame(e.getCause().getSuppressed()[0], errorClose);
		}
		dbFacadeRelease.MCR.assertMethodWasCalled("close");
	}

	@Test
	public void testOnlyForTestGetters() {
		recordLock = createRecordLock();
		RecordLockImp recordLockImp = (RecordLockImp) recordLock;

		assertSame(recordLockImp.onlyForTestGetDatabaseFactory(), dbFactory);
		assertSame(recordLockImp.onlyForTestGetSleeper(), sleeper);
		assertSame(recordLockImp.onlyForTestGetTimeSource(), timeSource);
	}

	private void assertReleaseExecutedSqlWithTypeAndId(int callNumber, String sql) {
		dbFacadeRelease.MCR.assertParameter("executeSqlWithValues", callNumber, "sql", sql);
		dbFacadeRelease.MCR.assertParameterAsEqual("executeSqlWithValues", callNumber, "values",
				List.of(SOME_TYPE, SOME_ID));
	}
}
