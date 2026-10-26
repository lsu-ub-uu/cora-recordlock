
/**
 * The recordlock module provides interfaces and classes to use a record lock in a Cora based
 * system.
 * <p>
 * 
 */

import se.uu.ub.cora.recordlock.RecordLockInstanceProviderImp;
import se.uu.ub.cora.spider.recordlock.RecordLockInstanceProvider;

module se.uu.ub.cora.recordlock {
	requires se.uu.ub.cora.spider;
	requires se.uu.ub.cora.sqldatabase;
	requires se.uu.ub.cora.initialize;

	provides RecordLockInstanceProvider with RecordLockInstanceProviderImp;
}
