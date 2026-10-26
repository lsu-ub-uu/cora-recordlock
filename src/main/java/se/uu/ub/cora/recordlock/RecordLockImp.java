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

import java.util.List;

import se.uu.ub.cora.spider.recordlock.RecordLock;
import se.uu.ub.cora.spider.recordlock.RecordLockException;
import se.uu.ub.cora.sqldatabase.DatabaseFacade;
import se.uu.ub.cora.sqldatabase.SqlConflictException;
import se.uu.ub.cora.sqldatabase.SqlDatabaseFactory;

/**
 * RecordLockImp is a database implementation of {@link RecordLock} that stores locks in the
 * recordlock table in the database.
 */
public class RecordLockImp implements RecordLock {
	private static final String INSERT_SQL = "insert into recordlock (type, id) values (?, ?);";
	private static final String DELETE_SQL = "delete from recordlock where type = ? and id = ?;";
	private static final long RETRY_INTERVAL_MILLIS = 50;
	private static final long MAX_WAIT_MILLIS = 60_000;

	private final SqlDatabaseFactory databaseFactory;
	private final Sleeper sleeper;
	private final TimeSource timeSource;
	private final DatabaseFacade databaseFacade;
	private final String recordType;
	private final String recordId;

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
		databaseFacade = factorDatabaseFacade();
		acquire();
	}

	private DatabaseFacade factorDatabaseFacade() {
		try {
			return databaseFactory.factorDatabaseFacade();
		} catch (RuntimeException e) {
			throw asRecordLockException(e, "factor facade");
		}
	}

	private void acquire() {
		try {
			acquireUntilLockIsInserted();
		} catch (RuntimeException e) {
			RecordLockException failure = asRecordLockException(e, "acquire");
			closeFacadeAfterAcquisitionFailure(failure);
			throw failure;
		}
	}

	private void acquireUntilLockIsInserted() {
		long deadline = timeSource.currentTimeMillis() + MAX_WAIT_MILLIS;
		while (!tryToInsertLock(deadline)) {
			sleeper.sleepMilliseconds(RETRY_INTERVAL_MILLIS);
		}
	}

	private boolean tryToInsertLock(long deadline) {
		try {
			insertLock();
			return true;
		} catch (SqlConflictException e) {
			throwIfTimeLimitReached(deadline, e);
			return false;
		}
	}

	private void insertLock() {
		databaseFacade.executeSqlWithValues(INSERT_SQL, List.of(recordType, recordId));
	}

	private void throwIfTimeLimitReached(long deadline, SqlConflictException conflict) {
		if (timeSource.currentTimeMillis() >= deadline) {
			throw RecordLockException.withMessageAndException("Could not acquire lock for type: "
					+ recordType + " and id: " + recordId + " within " + MAX_WAIT_MILLIS + " ms.",
					conflict);
		}
	}

	private void closeFacadeAfterAcquisitionFailure(RecordLockException acquisitionFailure) {
		try {
			databaseFacade.close();
		} catch (RuntimeException closeFailure) {
			acquisitionFailure.addSuppressed(closeFailure);
		}
	}

	@Override
	public void close() {
		RecordLockException releaseFailure = null;
		try {
			releaseLock();
		} catch (RuntimeException e) {
			releaseFailure = asRecordLockException(e, "release");
		} finally {
			closeFacade(releaseFailure);
		}
		if (releaseFailure != null) {
			throw releaseFailure;
		}
	}

	private void releaseLock() {
		databaseFacade.executeSqlWithValues(DELETE_SQL, List.of(recordType, recordId));
	}

	private void closeFacade(RecordLockException releaseFailure) {
		try {
			databaseFacade.close();
		} catch (RuntimeException closeFailure) {
			if (releaseFailure == null) {
				throw asRecordLockException(closeFailure, "release");
			}
			releaseFailure.addSuppressed(closeFailure);
		}
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
