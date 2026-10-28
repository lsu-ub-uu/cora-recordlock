///*
// * Copyright 2026 Uppsala University Library
// *
// * This file is part of Cora.
// *
// * Cora is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * Cora is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with Cora. If not, see <http://www.gnu.org/licenses/>.
// */
// package se.uu.ub.cora.recordlock;
//
// import static org.testng.Assert.assertEquals;
// import static org.testng.Assert.assertSame;
// import static org.testng.Assert.assertTrue;
// import static org.testng.Assert.fail;
//
// import java.util.List;
// import java.util.concurrent.atomic.AtomicInteger;
//
// import org.testng.annotations.BeforeMethod;
// import org.testng.annotations.Test;
//
// import se.uu.ub.cora.recordlock.spy.SqlDatabaseFactorySpy;
// import se.uu.ub.cora.recordlock.spy.TableFacadeSpy;
// import se.uu.ub.cora.recordlock.spy.TableQuerySpy;
// import se.uu.ub.cora.spider.recordlock.RecordLock;
// import se.uu.ub.cora.spider.recordlock.RecordLockException;
// import se.uu.ub.cora.sqldatabase.SqlConflictException;
// import se.uu.ub.cora.sqldatabase.SqlDatabaseException;
//
// public class RecordLockTest {
// private static final String SOME_TYPE = "someType";
// private static final String SOME_ID = "someId";
// private static final long RETRY_INTERVAL_MILLIS = 50;
// private static final long MAX_WAIT_MILLIS = 60_000;
//
// private SqlDatabaseFactorySpy dbFactory;
// private TableFacadeSpy tableFacadeAquire;
// private TableFacadeSpy tableFacadeRelease;
// private TableQuerySpy tableQuery;
// private SleeperSpy sleeper;
// private TimeSourceSpy timeSource;
// private RecordLock recordLock;
//
// @BeforeMethod
// private void beforeMethod() {
// dbFactory = new SqlDatabaseFactorySpy();
//
// tableFacadeAquire = new TableFacadeSpy();
// tableFacadeRelease = new TableFacadeSpy();
// dbFactory.MRV.setReturnValues("factorTableFacade",
// List.of(tableFacadeAquire, tableFacadeRelease));
// tableQuery = new TableQuerySpy();
// dbFactory.MRV.setReturnValues("factorTableQuery", List.of(tableQuery), "recordlock");
//
// sleeper = new SleeperSpy();
// timeSource = new TimeSourceSpy();
// }
//
// @Test
// public void testRecordLockFactory() {
// RecordLockImp recordLockImp = RecordLockImp.usingDatabaseFactory(dbFactory, SOME_TYPE,
// SOME_ID);
//
// assertTrue(recordLockImp instanceof RecordLock);
// assertSame(recordLockImp.onlyForTestGetDatabaseFactory(), dbFactory);
// assertTrue(recordLockImp.onlyForTestGetSleeper() instanceof ThreadSleeper);
// assertTrue(recordLockImp.onlyForTestGetTimeSource() instanceof SystemTimeSource);
// }
//
// @Test
// public void testRecordLockFactorySendingSleeperTimeSource() {
// RecordLockImp recordLockImp = createRecordLock();
//
// assertTrue(recordLockImp instanceof RecordLock);
// assertSame(recordLockImp.onlyForTestGetDatabaseFactory(), dbFactory);
// assertSame(recordLockImp.onlyForTestGetSleeper(), sleeper);
// assertSame(recordLockImp.onlyForTestGetTimeSource(), timeSource);
// }
//
// private RecordLockImp createRecordLock() {
// return RecordLockImp.usingDatabaseFacadeAndSleeperAndTimeSource(dbFactory, sleeper,
// timeSource, SOME_TYPE, SOME_ID);
// }
//
// @Test
// public void testRecordLockImlementsRecordLock() {
// recordLock = createRecordLock();
//
// assertTrue(recordLock instanceof RecordLock);
// }
//
// @Test
// public void testErrorOnCreate() {
// RuntimeException exception = new RuntimeException("some error!");
// dbFactory.MRV.setAlwaysThrowException("factorTableFacade", exception);
//
// try {
// createRecordLock();
// fail("should have thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertEquals(e.getMessage(),
// "Could not acquire lock for type: someType and id: someId.");
// assertSame(e.getCause(), exception);
// }
// }
//
// @Test
// public void testGettingRecordLockAcquiresLock() {
// recordLock = createRecordLock();
//
// tableFacadeAquire.MCR.assertNumberOfCallsToMethod("insertRowUsingQuery", 1);
// tableQuery.MCR.assertNumberOfCallsToMethod("addParameter", 2);
// tableQuery.MCR.assertCalledParameters("addParameter", "type", "someType");
// tableQuery.MCR.assertCalledParameters("addParameter", "id", "someId");
// tableFacadeAquire.MCR.assertCalledParameters("insertRowUsingQuery", tableQuery);
// sleeper.MCR.assertMethodNotCalled("sleepMilliseconds");
// tableFacadeAquire.MCR.assertMethodWasCalled("close");
// }
//
// @Test
// public void testGettingRecordLockRetriesEvery50msUntilLockIsInserted() {
// throwConflictForNumberOfInserts(3);
//
// recordLock = createRecordLock();
//
// tableFacadeAquire.MCR.assertNumberOfCallsToMethod("insertRowUsingQuery", 4);
// sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 3);
// for (int call = 0; call < 3; call++) {
// sleeper.MCR.assertParameters("sleepMilliseconds", call, RETRY_INTERVAL_MILLIS);
// }
// }
//
// @Test
// public void testGettingRecordLockReadsStartTimeOnceAndTimeAfterEachConflict() {
// throwConflictForNumberOfInserts(2);
//
// recordLock = createRecordLock();
//
// timeSource.MCR.assertNumberOfCallsToMethod("currentTimeMillis", 3);
// }
//
// @Test
// public void testGettingRecordLockStillRetriesJustBeforeTimeLimit() {
// throwConflictForNumberOfInserts(1);
//
// timeSource.MRV.setReturnValues("currentTimeMillis",
// List.of(1_000L, 1_000L + MAX_WAIT_MILLIS - 1));
//
// recordLock = createRecordLock();
//
// tableFacadeAquire.MCR.assertNumberOfCallsToMethod("insertRowUsingQuery", 2);
// sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 1);
// }
//
// @Test
// public void testGettingRecordLockClosesFacadeWhenTimeLimitIsReached() {
// SqlConflictException conflict = SqlConflictException.withMessage("duplicate key");
// tableFacadeAquire.MRV.setAlwaysThrowException("insertRowUsingQuery", conflict);
// timeSource.MRV.setReturnValues("currentTimeMillis",
// List.of(1_000L, 1_010L, 1_000L + MAX_WAIT_MILLIS));
//
// try {
// createRecordLock();
// fail("RecordLockException should have been thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertEquals(e.getMessage(), "Could not acquire lock for type: " + SOME_TYPE
// + " and id: " + SOME_ID + " within " + MAX_WAIT_MILLIS + " ms.");
// assertSame(e.getCause(), conflict);
// }
// tableFacadeAquire.MCR.assertNumberOfCallsToMethod("insertRowUsingQuery", 2);
// tableFacadeAquire.MCR.assertMethodWasCalled("close");
// sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 1);
// }
//
// ///
// @Test
// public void testGettingRecordLockWrapsOtherDatabaseErrorsAndClosesFacade() {
// SqlDatabaseException error = SqlDatabaseException.withMessage("some error");
// tableFacadeAquire.MRV.setAlwaysThrowException("insertRowUsingQuery", error);
//
// try {
// createRecordLock();
// fail("RecordLockException should have been thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertSame(e.getCause(), error);
// }
// tableFacadeAquire.MCR.assertNumberOfCallsToMethod("insertRowUsingQuery", 1);
// tableFacadeAquire.MCR.assertMethodWasCalled("close");
// sleeper.MCR.assertMethodNotCalled("sleepMilliseconds");
// }
//
// @Test
// public void testGettingRecordLockPreservesAcquisitionAndCloseFailures() {
// SqlDatabaseException acquisitionError = SqlDatabaseException.withMessage("some error");
// SqlDatabaseException closeError = SqlDatabaseException.withMessage("close failed");
// tableFacadeAquire.MRV.setAlwaysThrowException("insertRowUsingQuery", acquisitionError);
// tableFacadeAquire.MRV.setAlwaysThrowException("close", closeError);
//
// try {
// createRecordLock();
// fail("RecordLockException should have been thrown");
// } catch (RecordLockException e) {
// assertSame(e.getCause(), acquisitionError);
// assertSame(e.getCause().getSuppressed()[0], closeError);
// }
// tableFacadeAquire.MCR.assertMethodWasCalled("close");
// }
//
// private void throwConflictForNumberOfInserts(int numberOfConflicts) {
// tableFacadeAquire.noOfErrorsToThrowForInsertRow = new AtomicInteger(numberOfConflicts);
// }
//
// @Test
// public void testErrorOnClose() {
// dbFactory = new SqlDatabaseFactorySpy();
// dbFactory.MRV.setReturnValues("factorTableQuery", List.of(tableQuery), "recordlock");
// RuntimeException exception = new RuntimeException("some error!");
// AtomicInteger calls = new AtomicInteger();
// dbFactory.MRV.setDefaultReturnValuesSupplier("factorTableFacade", () -> {
// if (calls.getAndIncrement() > 0) {
// throw exception;
// }
// return tableFacadeAquire;
// });
//
// recordLock = createRecordLock();
//
// try {
// recordLock.close();
// fail("should have thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertEquals(e.getMessage(),
// "Could not release lock for type: someType and id: someId.");
// assertSame(e.getCause(), exception);
// }
// }
//
// @Test
// public void testCloseReleaseLockAndCloseDatabaseFacade() {
// recordLock = createRecordLock();
//
// recordLock.close();
//
// tableFacadeRelease.MCR.assertNumberOfCallsToMethod("deleteRowsForQuery", 1);
// tableFacadeRelease.MCR.assertCalledParameters("deleteRowsForQuery", tableQuery);
// tableFacadeRelease.MCR.assertMethodWasCalled("close");
// }
//
// @Test
// public void testCloseRecordLockRetriesEvery50msUntilLockIsInserted() {
// RuntimeException exception = SqlConflictException.withMessage("duplicate key");
// AtomicInteger calls = new AtomicInteger();
// tableFacadeRelease.MRV.setDefaultReturnValuesSupplier("deleteRowsForQuery", () -> {
// if (calls.getAndIncrement() < 3) {
// throw exception;
// }
// return 1;
// });
// recordLock = createRecordLock();
// recordLock.close();
// tableFacadeRelease.MCR.assertNumberOfCallsToMethod("deleteRowsForQuery", 4);
// sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 3);
// for (int call = 0; call < 3; call++) {
// sleeper.MCR.assertParameters("sleepMilliseconds", call, RETRY_INTERVAL_MILLIS);
// }
// }
//
// @Test
// public void testCloseRecordLockReadsStartTimeOnceAndTimeAfterEachConflict() {
// RuntimeException exception = SqlConflictException.withMessage("duplicate key");
// AtomicInteger calls = new AtomicInteger();
// tableFacadeRelease.MRV.setDefaultReturnValuesSupplier("deleteRowsForQuery", () -> {
// if (calls.getAndIncrement() < 1) {
// throw exception;
// }
// return 1;
// });
//
// recordLock = createRecordLock();
// recordLock.close();
//
// timeSource.MCR.assertNumberOfCallsToMethod("currentTimeMillis", 3);
// }
//
// @Test
// public void testCloseRecordLockStillRetriesJustBeforeTimeLimit() {
// RuntimeException exception = SqlConflictException.withMessage("duplicate key");
// AtomicInteger calls = new AtomicInteger();
// tableFacadeRelease.MRV.setDefaultReturnValuesSupplier("deleteRowsForQuery", () -> {
// if (calls.getAndIncrement() < 3) {
// throw exception;
// }
// return 1;
// });
// timeSource.MRV.setReturnValues("currentTimeMillis",
// List.of(1_000L, 1_000L + MAX_WAIT_MILLIS - 1));
//
// recordLock = createRecordLock();
// recordLock.close();
//
// tableFacadeRelease.MCR.assertNumberOfCallsToMethod("deleteRowsForQuery", 4);
// sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 3);
// }
//
// @Test
// public void testCloseRecordLockClosesFacadeWhenTimeLimitIsReached() {
// SqlConflictException conflict = SqlConflictException.withMessage("duplicate key");
// tableFacadeRelease.MRV.setAlwaysThrowException("deleteRowsForQuery", conflict);
// timeSource.MRV.setReturnValues("currentTimeMillis",
// List.of(0L, 0L, 0L, 1_000L, 1_010L, 1_000L + MAX_WAIT_MILLIS));
//
// try {
// recordLock = createRecordLock();
// recordLock.close();
// fail("RecordLockException should have been thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertEquals(e.getMessage(), "Could not release lock for type: " + SOME_TYPE
// + " and id: " + SOME_ID + " within " + MAX_WAIT_MILLIS + " ms.");
// assertSame(e.getCause(), conflict);
// }
// tableFacadeRelease.MCR.assertNumberOfCallsToMethod("deleteRowsForQuery", 4);
// tableFacadeRelease.MCR.assertMethodWasCalled("close");
// sleeper.MCR.assertNumberOfCallsToMethod("sleepMilliseconds", 3);
// }
//
// @Test
// public void testCloseClosesFacadeCreatesRecordLockExceptionWithErrorAsCauseWhenReleaseFails() {
// SqlDatabaseException error = SqlDatabaseException.withMessage("release failed");
// tableFacadeRelease.MRV.setThrowException("deleteRowsForQuery", error, tableQuery);
// recordLock = createRecordLock();
//
// try {
// recordLock.close();
// fail("RecordLockException should have been thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertEquals(e.getMessage(),
// "Could not release lock for type: someType and id: someId.");
// assertSame(e.getCause(), error);
// }
// tableFacadeRelease.MCR.assertMethodWasCalled("close");
// }
//
// @Test
// public void testCloseErrorOnCloseFacade() {
// SqlDatabaseException error = SqlDatabaseException.withMessage("close failed");
// tableFacadeRelease.MRV.setAlwaysThrowException("close", error);
// recordLock = createRecordLock();
//
// try {
// recordLock.close();
// fail("RecordLockException should have been thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertEquals(e.getMessage(),
// "Could not release lock for type: someType and id: someId.");
// assertSame(e.getCause(), error);
// }
// tableFacadeRelease.MCR.assertMethodWasCalled("close");
// }
//
// @Test
// public void testCloseErrorOnReleaseAndCloseFacade() {
// SqlDatabaseException errorRelease = SqlDatabaseException.withMessage("release failed");
// tableFacadeRelease.MRV.setThrowException("deleteRowsForQuery", errorRelease, tableQuery);
// SqlDatabaseException errorClose = SqlDatabaseException.withMessage("close failed");
// tableFacadeRelease.MRV.setAlwaysThrowException("close", errorClose);
// recordLock = createRecordLock();
//
// try {
// recordLock.close();
// fail("RecordLockException should have been thrown");
// } catch (Exception e) {
// assertTrue(e instanceof RecordLockException);
// assertEquals(e.getMessage(),
// "Could not release lock for type: someType and id: someId.");
// assertSame(e.getCause(), errorRelease);
// assertSame(e.getCause().getSuppressed()[0], errorClose);
// }
// tableFacadeRelease.MCR.assertMethodWasCalled("close");
// }
//
// @Test
// public void testOnlyForTestGetters() {
// recordLock = createRecordLock();
// RecordLockImp recordLockImp = (RecordLockImp) recordLock;
//
// assertSame(recordLockImp.onlyForTestGetDatabaseFactory(), dbFactory);
// assertSame(recordLockImp.onlyForTestGetSleeper(), sleeper);
// assertSame(recordLockImp.onlyForTestGetTimeSource(), timeSource);
// }
//
// }
