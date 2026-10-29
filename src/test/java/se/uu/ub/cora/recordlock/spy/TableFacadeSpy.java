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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import se.uu.ub.cora.sqldatabase.Row;
import se.uu.ub.cora.sqldatabase.SqlConflictException;
import se.uu.ub.cora.sqldatabase.SqlTransactionException;
import se.uu.ub.cora.sqldatabase.table.TableFacade;
import se.uu.ub.cora.sqldatabase.table.TableQuery;
import se.uu.ub.cora.testutils.mcr.MethodCallRecorder;
import se.uu.ub.cora.testutils.mrv.MethodReturnValues;

public class TableFacadeSpy implements TableFacade {
	public MethodCallRecorder MCR = new MethodCallRecorder();
	public MethodReturnValues MRV = new MethodReturnValues();
	public AtomicInteger noOfConflictErrorsToThrowForInsertRow = new AtomicInteger(0);
	public AtomicInteger noOfTransactionErrorsToThrowForInsertRow = new AtomicInteger(0);

	public TableFacadeSpy() {
		MCR.useMRV(MRV);
		MRV.setDefaultReturnValuesSupplier("readRowsForQuery", Collections::emptyList);
		MRV.setDefaultReturnValuesSupplier("readOneRowForQuery", RowSpy::new);
		MRV.setDefaultReturnValuesSupplier("readNumberOfRows", () -> 0L);
		MRV.setDefaultReturnValuesSupplier("updateRowsUsingQuery", () -> 0);
		MRV.setDefaultReturnValuesSupplier("deleteRowsForQuery", () -> 0);
		MRV.setDefaultReturnValuesSupplier("nextValueFromSequence", () -> 0L);
	}

	@Override
	public void insertRowUsingQuery(TableQuery tableQuery) {
		MCR.addCall("tableQuery", tableQuery);
		if (noOfConflictErrorsToThrowForInsertRow.getAndDecrement() > 0) {
			throw SqlConflictException.withMessage("duplicate key");
		}
		if (noOfTransactionErrorsToThrowForInsertRow.getAndDecrement() > 0) {
			throw SqlTransactionException.withMessage("transaction failed");
		}
	}

	@Override
	public List<Row> readRowsForQuery(TableQuery tableQuery) {
		return (List<Row>) MCR.addCallAndReturnFromMRV("tableQuery", tableQuery);
	}

	@Override
	public Row readOneRowForQuery(TableQuery tableQuery) {
		return (Row) MCR.addCallAndReturnFromMRV("tableQuery", tableQuery);
	}

	@Override
	public long readNumberOfRows(TableQuery tableQuery) {
		return (long) MCR.addCallAndReturnFromMRV("tableQuery", tableQuery);
	}

	@Override
	public int updateRowsUsingQuery(TableQuery tableQuery) {
		return (int) MCR.addCallAndReturnFromMRV("tableQuery", tableQuery);
	}

	@Override
	public int deleteRowsForQuery(TableQuery tableQuery) {
		return (int) MCR.addCallAndReturnFromMRV("tableQuery", tableQuery);
	}

	@Override
	public long nextValueFromSequence(String sequenceName) {
		return (long) MCR.addCallAndReturnFromMRV("sequenceName", sequenceName);
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
