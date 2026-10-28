# cora-sqldatabase

cora-sqldatabase provides interfaces and classes to use a sql database in a Cora based system.

## Record lock

cora-sqldatabase implements the `RecordLock` abstraction from
[cora-recordlock](https://github.com/lsu-ub-uu/cora-recordlock). cora-spider gets an already
acquired lock through `RecordLockInstanceProvider.getRecordLock(recordType, recordId)` and releases
it by closing the returned lock.

### Configuration

The setting `coraDatabaseLookupName` (read with `SettingsProvider` from cora-initialize) must
contain the JNDI lookup name of the database. The table `recordlock`, with primary key
`(type, id)`, must exist in the database.

### Discovery

`RecordLockInstanceProviderImp` is registered as a `RecordLockInstanceProvider` both in
`module-info.java` (`provides`) and in
`META-INF/services/se.uu.ub.cora.recordlock.RecordLockInstanceProvider`, so cora-recordlock finds
it through cora-initialize when this jar is on the classpath.

### Behaviour and lifecycle

* `getRecordLock(recordType, recordId)` acquires the lock before returning a new `RecordLock`.
  The returned lock represents that one record and is not intended to be shared between threads.
* Acquisition inserts a row into `recordlock`. If the row exists (`SqlConflictException`) it waits
  50 ms and retries for at most 60 s. Other errors are not retried.
* The acquired lock owns its `DatabaseFacade`. `close()` deletes the row and closes the facade,
  including when release fails. If acquisition fails, the allocated facade is closed before the
  failure is rethrown.
* Clients must release locks by closing them, preferably with try-with-resources.

### Errors

All errors are thrown as `RecordLockException` (from cora-recordlock):

* time limit reached: message `Could not acquire lock for type: <type> and id: <id> within 60000
  ms.`, cause is the last `SqlConflictException`.
* interrupted while waiting: message `Interrupted while waiting to retry.`, cause is the
  `InterruptedException` and the thread's interrupt flag is kept.
* any other failure (creating the facade, executing sql, closing the facade): message
  `Could not acquire|release lock for type: <type> and id: <id>.`, the original error as cause.
  If closing fails after another failure, the close error is added as suppressed to the original
  error.
* missing setting: `Getting RecordLock failed` with the `InitializationException` as cause.
