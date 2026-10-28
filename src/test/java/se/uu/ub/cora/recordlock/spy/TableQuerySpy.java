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

import java.util.List;

import se.uu.ub.cora.sqldatabase.table.TableQuery;
import se.uu.ub.cora.testutils.mcr.MethodCallRecorder;

public class TableQuerySpy implements TableQuery {
	public MethodCallRecorder MCR = new MethodCallRecorder();

	@Override
	public void addParameter(String name, Object value) {
		MCR.addCall("name", name, "value", value);
	}

	@Override
	public void addCondition(String name, Object value) {
		MCR.addCall("name", name, "value", value);
	}

	@Override
	public void setFromNo(Long fromNo) {
		MCR.addCall("fromNo", fromNo);
	}

	@Override
	public void setToNo(Long toNo) {
		MCR.addCall("toNo", toNo);
	}

	@Override
	public void addOrderByAsc(String column) {
		MCR.addCall("column", column);
	}

	@Override
	public void addOrderByDesc(String column) {
		MCR.addCall("column", column);
	}

	@Override
	public String assembleCreateSql() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String assembleReadSql() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String assembleUpdateSql() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String assembleDeleteSql() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Object> getQueryValues() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String assembleCountSql() {
		// TODO Auto-generated method stub
		return null;
	}

}
