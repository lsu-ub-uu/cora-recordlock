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

import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import se.uu.ub.cora.initialize.SettingsProvider;
import se.uu.ub.cora.logger.LoggerFactory;
import se.uu.ub.cora.logger.LoggerProvider;
import se.uu.ub.cora.logger.spies.LoggerFactorySpy;
import se.uu.ub.cora.sqldatabase.SqlDatabaseFactoryImp;

public class RecordLockInstanceProviderImpTest {

	RecordLockInstanceProviderImp provider;

	@BeforeMethod
	void beforeMethod() {
		LoggerFactory loggerFactory = new LoggerFactorySpy();
		LoggerProvider.setLoggerFactory(loggerFactory);

		setUpSettingsProvider();

		provider = new RecordLockInstanceProviderImp();
	}

	private void setUpSettingsProvider() {
		Map<String, String> settings = new HashMap<>();
		settings.put("coraDatabaseLookupName", "someDatabase");
		SettingsProvider.setSettings(settings);
	}

	@Test
	public void testGetOrderToSelectImplementationsByIsZero() {
		assertEquals(provider.getOrderToSelectImplementionsBy(), 0);
	}

	@Test
	public void testGetRecordLock_DatabaseFactory() {
		SqlDatabaseFactoryImp sqlDatabaseFactory = (SqlDatabaseFactoryImp) provider
				.onlyForTestGetSqlDatabaseFactory();
		assertEquals(sqlDatabaseFactory.onlyForTestGetLookupName(), "someDatabase");
	}

	@Test
	public void testGetRecordLock() {
		SqlDatabaseFactoryImp sqlDatabaseFactory = (SqlDatabaseFactoryImp) provider
				.onlyForTestGetSqlDatabaseFactory();
		RecordLockImp recordLock = (RecordLockImp) provider.getRecordLock();

		assertEquals(recordLock.onlyForTestGetDatabaseFactory(), sqlDatabaseFactory);
	}

}
