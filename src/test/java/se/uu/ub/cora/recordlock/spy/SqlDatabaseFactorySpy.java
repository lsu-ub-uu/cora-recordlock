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
package se.uu.ub.cora.recordlock.spy;

import se.uu.ub.cora.sqldatabase.DatabaseFacade;
import se.uu.ub.cora.sqldatabase.SqlDatabaseFactory;
import se.uu.ub.cora.sqldatabase.sequence.Sequence;
import se.uu.ub.cora.sqldatabase.table.TableFacade;
import se.uu.ub.cora.sqldatabase.table.TableQuery;
import se.uu.ub.cora.testutils.mcr.MethodCallRecorder;
import se.uu.ub.cora.testutils.mrv.MethodReturnValues;

public class SqlDatabaseFactorySpy implements SqlDatabaseFactory {
	public MethodCallRecorder MCR = new MethodCallRecorder();
	public MethodReturnValues MRV = new MethodReturnValues();

	public SqlDatabaseFactorySpy() {
		MCR.useMRV(MRV);
		MRV.setDefaultReturnValuesSupplier("factorDatabaseFacade", DatabaseFacadeSpy::new);
		MRV.setDefaultReturnValuesSupplier("factorTableFacade", TableFacadeSpy::new);
		MRV.setDefaultReturnValuesSupplier("factorTableQuery", TableQuerySpy::new);
	}

	@Override
	public DatabaseFacade factorDatabaseFacade() {
		return (DatabaseFacade) MCR.addCallAndReturnFromMRV();
	}

	@Override
	public TableFacade factorTableFacade() {
		return (TableFacade) MCR.addCallAndReturnFromMRV();
	}

	@Override
	public TableQuery factorTableQuery(String tableName) {
		return (TableQuery) MCR.addCallAndReturnFromMRV("tableName", tableName);
	}

	@Override
	public Sequence factorSequence() {
		return null;
	}

}
