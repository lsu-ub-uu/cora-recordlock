
/**
 * The recordlock module provides an implementation of recordLock backed by a database table
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
