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
import java.util.Set;

import se.uu.ub.cora.sqldatabase.Row;
import se.uu.ub.cora.testutils.mcr.MethodCallRecorder;
import se.uu.ub.cora.testutils.mrv.MethodReturnValues;

public class RowSpy implements Row {

	public MethodCallRecorder MCR = new MethodCallRecorder();
	public MethodReturnValues MRV = new MethodReturnValues();

	public RowSpy() {
		MCR.useMRV(MRV);
		MRV.setDefaultReturnValuesSupplier("getValueByColumn", Object::new);
		MRV.setDefaultReturnValuesSupplier("columnSet", Collections::emptySet);
		MRV.setDefaultReturnValuesSupplier("hasColumn", () -> false);
		MRV.setDefaultReturnValuesSupplier("hasColumnWithNonEmptyValue", () -> false);
	}

	@Override
	public Object getValueByColumn(String columnName) {
		return MCR.addCallAndReturnFromMRV("columnName", columnName);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> columnSet() {
		return (Set<String>) MCR.addCallAndReturnFromMRV();
	}

	@Override
	public boolean hasColumn(String columnName) {
		return (boolean) MCR.addCallAndReturnFromMRV("columnName", columnName);
	}

	@Override
	public boolean hasColumnWithNonEmptyValue(String columnName) {
		return (boolean) MCR.addCallAndReturnFromMRV("columnName", columnName);
	}

}