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

import se.uu.ub.cora.initialize.SettingsProvider;
import se.uu.ub.cora.spider.recordlock.RecordLock;
import se.uu.ub.cora.spider.recordlock.RecordLockInstanceProvider;
import se.uu.ub.cora.sqldatabase.SqlDatabaseFactory;
import se.uu.ub.cora.sqldatabase.SqlDatabaseFactoryImp;

public class RecordLockInstanceProviderImp implements RecordLockInstanceProvider {
	private final SqlDatabaseFactory databaseFactory;

	public RecordLockInstanceProviderImp() {
		this(startStorage());
	}

	RecordLockInstanceProviderImp(SqlDatabaseFactory databaseFactory) {
		this.databaseFactory = databaseFactory;
	}

	private static SqlDatabaseFactory startStorage() {
		String databaseLookupValue = SettingsProvider.getSetting("coraDatabaseLookupName");
		return SqlDatabaseFactoryImp.usingLookupNameFromContext(databaseLookupValue);
	}

	@Override
	public int getOrderToSelectImplementionsBy() {
		return 0;
	}

	@Override
	public RecordLock getRecordLock(String recordType, String recordId) {
		return RecordLockImp.usingDatabaseFactory(databaseFactory, recordType, recordId);
	}

	public SqlDatabaseFactory onlyForTestGetSqlDatabaseFactory() {
		return databaseFactory;
	}
}