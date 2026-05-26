package com.budgetapp.history

import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.ChangeLogDao
import com.budgetapp.data.local.database.dao.PensionAccountDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.*
import com.budgetapp.data.repository.ChangeLogRepositoryImpl
import com.budgetapp.domain.model.ChangeLogEntry
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeLogRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var changeLogDao: ChangeLogDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var pensionAccountDao: PensionAccountDao
    private lateinit var repo: ChangeLogRepositoryImpl

    @Before
    fun setUp() {
        changeLogDao      = mockk(relaxed = true)
        transactionDao    = mockk(relaxed = true)
        categoryDao       = mockk(relaxed = true)
        pensionAccountDao = mockk(relaxed = true)

        every { changeLogDao.getAll() } returns flowOf(emptyList())
        every { changeLogDao.getByType(any()) } returns flowOf(emptyList())
        coEvery { changeLogDao.count() } returns 10  // below trim threshold

        repo = ChangeLogRepositoryImpl(changeLogDao, transactionDao, categoryDao, pensionAccountDao)
    }

    @After
    fun tearDown() = clearAllMocks()

    // ── log ───────────────────────────────────────────────────────────────────

    @Test
    fun `log inserts entity with correct fields`() = runTest(testDispatcher) {
        repo.log(ChangeAction.CREATE, HistoryEntityType.TRANSACTION, "id-1", "Coffee — 12.50", "{}")

        coVerify {
            changeLogDao.insert(match { entry ->
                entry.action == "CREATE" &&
                entry.entityType == "TRANSACTION" &&
                entry.entityId == "id-1" &&
                entry.displayName == "Coffee — 12.50"
            })
        }
    }

    @Test
    fun `log trims when count exceeds threshold`() = runTest(testDispatcher) {
        coEvery { changeLogDao.count() } returns 650

        repo.log(ChangeAction.DELETE, HistoryEntityType.TRANSACTION, "id-1", "Test", "{}")

        coVerify { changeLogDao.trimToLimit(500) }
    }

    @Test
    fun `log does not trim when count is below threshold`() = runTest(testDispatcher) {
        coEvery { changeLogDao.count() } returns 100

        repo.log(ChangeAction.CREATE, HistoryEntityType.CATEGORY, "5", "Food", "{}")

        coVerify(exactly = 0) { changeLogDao.trimToLimit(any()) }
    }

    // ── restore TRANSACTION ───────────────────────────────────────────────────

    @Test
    fun `restore DELETE transaction inserts entity via transactionDao`() = runTest(testDispatcher) {
        val snapshot = buildTransactionSnapshot("tx-1", "user-1")
        val entry = makeEntry(ChangeAction.DELETE, HistoryEntityType.TRANSACTION, "tx-1", snapshot)

        repo.restore(entry)

        coVerify { transactionDao.insertTransaction(match { it.id == "tx-1" && it.userId == "user-1" }) }
    }

    @Test
    fun `restore UPDATE transaction uses old snapshot`() = runTest(testDispatcher) {
        val oldSnapshot = buildTransactionSnapshot("tx-2", "user-1", description = "Old Desc")
        val newSnapshot = buildTransactionSnapshot("tx-2", "user-1", description = "New Desc")
        val compound = JSONObject().put("old", oldSnapshot).put("new", newSnapshot).toString()
        val entry = makeEntry(ChangeAction.UPDATE, HistoryEntityType.TRANSACTION, "tx-2", compound)

        repo.restore(entry)

        coVerify { transactionDao.insertTransaction(match { it.description == "Old Desc" }) }
    }

    // ── restore SAVINGS ───────────────────────────────────────────────────────

    @Test
    fun `restore DELETE savings inserts account via pensionAccountDao`() = runTest(testDispatcher) {
        val snapshot = buildSavingsSnapshot(id = 5L, userId = "user-1", name = "My Savings")
        val entry = makeEntry(ChangeAction.DELETE, HistoryEntityType.SAVINGS, "5", snapshot)

        repo.restore(entry)

        coVerify { pensionAccountDao.insertPensionAccount(match { it.id == 5L && it.accountName == "My Savings" }) }
    }

    // ── restore CATEGORY ──────────────────────────────────────────────────────

    @Test
    fun `restore DELETE category inserts via categoryDao`() = runTest(testDispatcher) {
        val snapshot = buildCategorySnapshot(id = 3L, name = "Food")
        val entry = makeEntry(ChangeAction.DELETE, HistoryEntityType.CATEGORY, "3", snapshot)

        repo.restore(entry)

        coVerify { categoryDao.insertCategory(match { it.id == 3L && it.name == "Food" }) }
    }

    // ── restore logs a follow-up CREATE ───────────────────────────────────────

    @Test
    fun `restore logs a CREATE entry for the restored item`() = runTest(testDispatcher) {
        val snapshot = buildTransactionSnapshot("tx-99", "user-1")
        val entry = makeEntry(ChangeAction.DELETE, HistoryEntityType.TRANSACTION, "tx-99", snapshot)

        repo.restore(entry)

        coVerify {
            changeLogDao.insert(match { log ->
                log.action == "CREATE" && log.displayName.contains("restored")
            })
        }
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Test
    fun `clearAll delegates to changeLogDao`() = runTest(testDispatcher) {
        repo.clearAll()
        coVerify { changeLogDao.clearAll() }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeEntry(action: ChangeAction, type: HistoryEntityType, id: String, snapshot: String) =
        ChangeLogEntry(id = 1L, timestamp = System.currentTimeMillis(),
            action = action, entityType = type, entityId = id, displayName = "Test", snapshot = snapshot)

    private fun buildTransactionSnapshot(id: String, userId: String, description: String = "Coffee") =
        JSONObject().apply {
            put("id", id); put("userId", userId); put("type", "EXPENSE"); put("amount", 12.50)
            put("description", description); put("categoryId", 1L); put("date", 1748304000000L)
            put("isRecurring", false); put("deviceId", "device-1")
        }.toString()

    private fun buildSavingsSnapshot(id: Long, userId: String, name: String) =
        JSONObject().apply {
            put("id", id); put("userId", userId); put("accountName", name); put("provider", "Bank")
            put("currentValue", 10000.0); put("contributionAmount", 500.0)
            put("contributionFrequency", "MONTHLY"); put("accountType", "SAVINGS"); put("notes", "")
        }.toString()

    private fun buildCategorySnapshot(id: Long, name: String) =
        JSONObject().apply {
            put("id", id); put("name", name); put("icon", "🍽️"); put("color", "#FF9800")
            put("isCustom", false)
        }.toString()
}
