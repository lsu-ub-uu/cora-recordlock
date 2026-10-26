/*
 * Copyright 2025 Uppsala University Library
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

import java.util.Collections;
import java.util.List;

import se.uu.ub.cora.sqldatabase.DatabaseFacade;
import se.uu.ub.cora.sqldatabase.Row;
import se.uu.ub.cora.testutils.mcr.MethodCallRecorder;
import se.uu.ub.cora.testutils.mrv.MethodReturnValues;

public class DatabaseFacadeSpy implements DatabaseFacade {
	public MethodCallRecorder MCR = new MethodCallRecorder();
	public MethodReturnValues MRV = new MethodReturnValues();

	public DatabaseFacadeSpy() {
		MCR.useMRV(MRV);
		MRV.setDefaultReturnValuesSupplier("readUsingSqlAndValues", Collections::emptyList);
		MRV.setDefaultReturnValuesSupplier("readOneRowOrFailUsingSqlAndValues", RowSpy::new);
		MRV.setDefaultReturnValuesSupplier("executeSqlWithValues", () -> 0);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Row> readUsingSqlAndValues(String sql, List<Object> values) {
		return (List<Row>) MCR.addCallAndReturnFromMRV("sql", sql, "values", values);
	}

	@Override
	public Row readOneRowOrFailUsingSqlAndValues(String sql, List<Object> values) {
		return (Row) MCR.addCallAndReturnFromMRV("sql", sql, "values", values);
	}

	@Override
	public int executeSqlWithValues(String sql, List<Object> values) {
		return (int) MCR.addCallAndReturnFromMRV("sql", sql, "values", values);
	}

	@Override
	public void executeSql(String sql) {
		MCR.addCall("sql", sql);
	}

	@Override
	public void startTransaction() {
		MCR.addCall();
	}

	@Override
	public void endTransaction() {
		MCR.addCall();
	}

	@Override
	public void rollback() {
		MCR.addCall();
	}

	@Override
	public void close() {
		MCR.addCall();
	}

}
