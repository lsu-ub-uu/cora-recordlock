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

import se.uu.ub.cora.spider.recordlock.RecordLock;
import se.uu.ub.cora.spider.recordlock.RecordLockException;
import se.uu.ub.cora.sqldatabase.SqlConflictException;
import se.uu.ub.cora.sqldatabase.SqlDatabaseFactory;
import se.uu.ub.cora.sqldatabase.table.TableFacade;
import se.uu.ub.cora.sqldatabase.table.TableQuery;

/**
 * RecordLockImp is a database implementation of {@link RecordLock} that stores locks in the
 * recordlock table in the database.
 */
public class RecordLockImp implements RecordLock {
	private static final long RETRY_INTERVAL_MILLIS = 50;
	private static final long MAX_WAIT_MILLIS = 60_000;

	private final SqlDatabaseFactory databaseFactory;
	private final Sleeper sleeper;
	private final TimeSource timeSource;
	private final String recordType;
	private final String recordId;
	private TableQuery tableQuery;

	public static RecordLockImp usingDatabaseFactory(SqlDatabaseFactory databaseFactory,
			String recordType, String recordId) {
		return new RecordLockImp(databaseFactory, new ThreadSleeper(), new SystemTimeSource(),
				recordType, recordId);
	}

	static RecordLockImp usingDatabaseFacadeAndSleeperAndTimeSource(
			SqlDatabaseFactory databaseFactory, Sleeper sleeper, TimeSource timeSource,
			String recordType, String recordId) {
		return new RecordLockImp(databaseFactory, sleeper, timeSource, recordType, recordId);
	}

	private RecordLockImp(SqlDatabaseFactory databaseFactory, Sleeper sleeper,
			TimeSource timeSource, String recordType, String recordId) {
		this.databaseFactory = databaseFactory;
		this.sleeper = sleeper;
		this.timeSource = timeSource;
		this.recordType = recordType;
		this.recordId = recordId;
		tableQuery = databaseFactory.factorTableQuery("recordlock");
		tableQuery.addParameter("type", recordType);
		tableQuery.addParameter("id", recordId);
		// SPIKE
		tableQuery.addCondition("type", recordType);
		tableQuery.addCondition("id", recordId);
		// SPIKE

		acquireLock();
	}

	private void acquireLock() {
		try (TableFacade tableFacade = databaseFactory.factorTableFacade()) {
			acquireUntilLockIsInserted(tableFacade);
		} catch (RuntimeException e) {
			throw asRecordLockException(e, "acquire");
		}
	}

	private void acquireUntilLockIsInserted(TableFacade tableFacade) {
		long deadline = timeSource.currentTimeMillis() + MAX_WAIT_MILLIS;
		while (!tryToInsertLock(tableFacade, deadline)) {
			sleeper.sleepMilliseconds(RETRY_INTERVAL_MILLIS);
		}
	}

	private boolean tryToInsertLock(TableFacade tableFacade, long deadline) {
		try {
			insertLock(tableFacade);
			return true;
		} catch (SqlConflictException e) {
			throwIfTimeLimitReached("acquire", deadline, e);
			return false;
		}
	}

	private void insertLock(TableFacade tableFacade) {
		tableFacade.insertRowUsingQuery(tableQuery);
	}

	private void throwIfTimeLimitReached(String type, long deadline,
			SqlConflictException conflict) {
		if (timeSource.currentTimeMillis() >= deadline) {
			throw RecordLockException.withMessageAndException(
					"Could not " + type + " lock for type: " + recordType + " and id: " + recordId
							+ " within " + MAX_WAIT_MILLIS + " ms.",
					conflict);
		}
	}

	@Override
	public void close() {
		try (TableFacade tableFacade = databaseFactory.factorTableFacade()) {
			releaseUntilLockIsDeleted(tableFacade);
		} catch (RuntimeException e) {
			throw asRecordLockException(e, "release");
		}
	}

	private void releaseUntilLockIsDeleted(TableFacade tableFacade) {
		long deadline = timeSource.currentTimeMillis() + MAX_WAIT_MILLIS;
		while (!tryToReleaseLock(tableFacade, deadline)) {
			sleeper.sleepMilliseconds(RETRY_INTERVAL_MILLIS);
		}
	}

	private boolean tryToReleaseLock(TableFacade tableFacade, long deadline) {
		try {
			releaseLock(tableFacade);
			return true;
		} catch (SqlConflictException e) {
			throwIfTimeLimitReached("release", deadline, e);
			return false;
		}
	}

	private void releaseLock(TableFacade tableFacade) {
		tableFacade.deleteRowsForQuery(tableQuery);
	}

	private RecordLockException asRecordLockException(RuntimeException failure, String operation) {
		if (failure instanceof RecordLockException recordLockEx) {
			return recordLockEx;
		}
		return RecordLockException.withMessageAndException("Could not " + operation
				+ " lock for type: " + recordType + " and id: " + recordId + ".", failure);
	}

	public SqlDatabaseFactory onlyForTestGetDatabaseFactory() {
		return databaseFactory;
	}

	public Sleeper onlyForTestGetSleeper() {
		return sleeper;
	}

	public TimeSource onlyForTestGetTimeSource() {
		return timeSource;
	}
}
