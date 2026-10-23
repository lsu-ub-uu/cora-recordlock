
/**
 * The sqldatabase module provides interfaces and classes to use a sql database in a Cora based
 * system.
 * <p>
 * It also provides a database backed implementation of RecordLock, from the recordlock module,
 * through {@link RecordLockInstanceProvider}.
 */
module se.uu.ub.cora.sqldatabase {
	// requires transitive java.naming;
	// requires java.sql;
	requires se.uu.ub.cora.logger;
	// requires org.postgresql.jdbc;
	requires se.uu.ub.cora.initialize;

	// exports se.uu.ub.cora.sqldatabase;
	// exports se.uu.ub.cora.sqldatabase.table;
	// exports se.uu.ub.cora.sqldatabase.sequence;
	//
	// provides RecordLockInstanceProvider with RecordLockInstanceProviderImp;
}
