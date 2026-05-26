package com.budgetapp.history

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.budgetapp.data.local.entity.ChangeAction
import com.budgetapp.data.local.entity.HistoryEntityType
import com.budgetapp.domain.model.ChangeLogEntry
import com.budgetapp.domain.repository.ChangeLogRepository
import com.budgetapp.presentation.history.HistoryFilter
import com.budgetapp.presentation.history.HistoryViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: ChangeLogRepository
    private lateinit var viewModel: HistoryViewModel

    private val sampleEntries = listOf(
        makeEntry(1L, ChangeAction.CREATE,  HistoryEntityType.TRANSACTION, "Coffee — 12.50"),
        makeEntry(2L, ChangeAction.UPDATE,  HistoryEntityType.SAVINGS,     "My Savings"),
        makeEntry(3L, ChangeAction.DELETE,  HistoryEntityType.CATEGORY,    "Food"),
        makeEntry(4L, ChangeAction.DELETE,  HistoryEntityType.TRANSACTION, "Rent — 3000.00"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk()
        every { repo.getAll() } returns flowOf(sampleEntries)
        every { repo.getByType(any()) } returns flowOf(emptyList())
        viewModel = HistoryViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ── initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial filter is ALL and shows all entries`() {
        assertEquals(HistoryFilter.ALL, viewModel.uiState.value.filter)
        assertEquals(4, viewModel.uiState.value.entries.size)
    }

    // ── filter ────────────────────────────────────────────────────────────────

    @Test
    fun `setFilter TRANSACTIONS shows only transaction entries`() {
        viewModel.setFilter(HistoryFilter.TRANSACTIONS)

        val entries = viewModel.uiState.value.entries
        assertTrue(entries.all { it.entityType == HistoryEntityType.TRANSACTION })
        assertEquals(2, entries.size)
    }

    @Test
    fun `setFilter SAVINGS shows only savings entries`() {
        viewModel.setFilter(HistoryFilter.SAVINGS)

        val entries = viewModel.uiState.value.entries
        assertTrue(entries.all { it.entityType == HistoryEntityType.SAVINGS })
        assertEquals(1, entries.size)
    }

    @Test
    fun `setFilter CATEGORIES shows only category entries`() {
        viewModel.setFilter(HistoryFilter.CATEGORIES)

        val entries = viewModel.uiState.value.entries
        assertTrue(entries.all { it.entityType == HistoryEntityType.CATEGORY })
        assertEquals(1, entries.size)
    }

    @Test
    fun `setFilter ALL restores full list`() {
        viewModel.setFilter(HistoryFilter.TRANSACTIONS)
        viewModel.setFilter(HistoryFilter.ALL)

        assertEquals(4, viewModel.uiState.value.entries.size)
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @Test
    fun `restore calls repository and sets success message`() = runTest(testDispatcher) {
        coEvery { repo.restore(any()) } just Runs
        val entry = sampleEntries.first { it.action == ChangeAction.DELETE }

        viewModel.restore(entry)

        coVerify { repo.restore(entry) }
        assertNotNull(viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.message!!.contains("restored", ignoreCase = true))
    }

    @Test
    fun `restore on UPDATE entry shows revert message`() = runTest(testDispatcher) {
        coEvery { repo.restore(any()) } just Runs
        val entry = sampleEntries.first { it.action == ChangeAction.UPDATE }

        viewModel.restore(entry)

        assertTrue(viewModel.uiState.value.message!!.contains("reverted", ignoreCase = true))
    }

    @Test
    fun `restore on failure sets error message`() = runTest(testDispatcher) {
        coEvery { repo.restore(any()) } throws RuntimeException("DB error")

        viewModel.restore(sampleEntries.first())

        assertTrue(viewModel.uiState.value.message!!.contains("failed", ignoreCase = true))
    }

    @Test
    fun `clearMessage resets message to null`() = runTest(testDispatcher) {
        coEvery { repo.restore(any()) } just Runs
        viewModel.restore(sampleEntries.first())

        viewModel.clearMessage()

        assertNull(viewModel.uiState.value.message)
    }

    // ── clear history ─────────────────────────────────────────────────────────

    @Test
    fun `clearHistory calls repository clearAll`() = runTest(testDispatcher) {
        coEvery { repo.clearAll() } just Runs

        viewModel.clearHistory()

        coVerify { repo.clearAll() }
    }

    @Test
    fun `clearHistory sets confirmation message`() = runTest(testDispatcher) {
        coEvery { repo.clearAll() } just Runs

        viewModel.clearHistory()

        assertNotNull(viewModel.uiState.value.message)
    }

    // ── isRestoring ───────────────────────────────────────────────────────────

    @Test
    fun `isRestoring is false after successful restore`() = runTest(testDispatcher) {
        coEvery { repo.restore(any()) } just Runs

        viewModel.restore(sampleEntries.first())

        assertFalse(viewModel.uiState.value.isRestoring)
    }

    @Test
    fun `isRestoring is false after failed restore`() = runTest(testDispatcher) {
        coEvery { repo.restore(any()) } throws RuntimeException("fail")

        viewModel.restore(sampleEntries.first())

        assertFalse(viewModel.uiState.value.isRestoring)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeEntry(id: Long, action: ChangeAction, type: HistoryEntityType, displayName: String) =
        ChangeLogEntry(id = id, timestamp = System.currentTimeMillis(),
            action = action, entityType = type, entityId = id.toString(),
            displayName = displayName, snapshot = "{}")
}
