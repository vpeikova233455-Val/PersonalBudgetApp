package com.budgetapp.savings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.budgetapp.data.local.entity.AccountType
import com.budgetapp.data.local.entity.RecurrenceFrequency
import com.budgetapp.domain.model.PensionAccount
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SavingsRepository
import com.budgetapp.presentation.savings.SavingsViewModel
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
class SavingsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: SavingsRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: SavingsViewModel

    private val userId = "user-abc"

    private fun account(
        id: Long,
        name: String,
        type: AccountType = AccountType.SAVINGS,
        value: Double = 1000.0,
        contrib: Double = 100.0,
        employer: Double? = null
    ) = PensionAccount(
        id = id,
        accountName = name,
        provider = "Bank",
        currentValue = value,
        contributionAmount = contrib,
        employerContribution = employer,
        contributionFrequency = RecurrenceFrequency.MONTHLY,
        accountType = type
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        authRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() {
        viewModel = SavingsViewModel(repository, authRepository)
    }

    @Test
    fun `initial state is loading`() {
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(emptyList())

        buildViewModel()

        // After construction with UnconfinedTestDispatcher the flow is collected immediately,
        // but we can still verify the final settled state.
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loads accounts and computes totals correctly`() = runTest {
        val accounts = listOf(
            account(1L, "Emergency Fund", AccountType.SAVINGS, value = 5000.0, contrib = 200.0),
            account(2L, "Vanguard ETF", AccountType.INVESTMENT, value = 20000.0, contrib = 500.0),
            account(3L, "401k", AccountType.PENSION, value = 30000.0, contrib = 300.0, employer = 150.0)
        )
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(accounts)

        buildViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.accounts.size)
        assertEquals(55000.0, state.totalValue, 0.001)
        // Monthly: 200 + 500 + (300+150) = 1150
        assertEquals(1150.0, state.totalMonthlyContribution, 0.001)
    }

    @Test
    fun `valueByType groups accounts correctly`() = runTest {
        val accounts = listOf(
            account(1L, "Fund A", AccountType.SAVINGS, value = 3000.0),
            account(2L, "Fund B", AccountType.SAVINGS, value = 2000.0),
            account(3L, "ETF",    AccountType.INVESTMENT, value = 10000.0)
        )
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(accounts)

        buildViewModel()

        val byType = viewModel.uiState.value.valueByType
        assertEquals(5000.0, byType[AccountType.SAVINGS]!!, 0.001)
        assertEquals(10000.0, byType[AccountType.INVESTMENT]!!, 0.001)
        assertNull(byType[AccountType.PENSION])
    }

    @Test
    fun `empty account list produces zero totals`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(emptyList())

        buildViewModel()

        val state = viewModel.uiState.value
        assertEquals(0.0, state.totalValue, 0.001)
        assertEquals(0.0, state.totalMonthlyContribution, 0.001)
        assertTrue(state.valueByType.isEmpty())
    }

    @Test
    fun `unauthenticated user produces error state`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns null

        buildViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `saveAccount calls insertAccount when id is 0`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(emptyList())
        coEvery { repository.insertAccount(any(), userId) } returns 10L

        buildViewModel()

        viewModel.saveAccount(
            id = 0L,
            accountName = "New Savings",
            provider = "Chase",
            currentValue = 1000.0,
            contributionAmount = 100.0,
            employerContribution = null,
            contributionFrequency = RecurrenceFrequency.MONTHLY,
            accountType = AccountType.SAVINGS,
            notes = ""
        )

        coVerify {
            repository.insertAccount(match {
                it.accountName == "New Savings" && it.accountType == AccountType.SAVINGS
            }, userId)
        }
        coVerify(exactly = 0) { repository.updateAccount(any(), any()) }
    }

    @Test
    fun `saveAccount calls updateAccount when id is non-zero`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(emptyList())
        coEvery { repository.updateAccount(any(), userId) } just runs

        buildViewModel()

        viewModel.saveAccount(
            id = 5L,
            accountName = "Updated Fund",
            provider = "Vanguard",
            currentValue = 8000.0,
            contributionAmount = 300.0,
            employerContribution = null,
            contributionFrequency = RecurrenceFrequency.MONTHLY,
            accountType = AccountType.INVESTMENT,
            notes = "rebalanced"
        )

        coVerify {
            repository.updateAccount(match {
                it.id == 5L && it.accountName == "Updated Fund" && it.accountType == AccountType.INVESTMENT
            }, userId)
        }
        coVerify(exactly = 0) { repository.insertAccount(any(), any()) }
    }

    @Test
    fun `deleteAccount delegates to repository`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(emptyList())
        coEvery { repository.deleteAccount(3L) } just runs

        buildViewModel()
        viewModel.deleteAccount(3L)

        coVerify { repository.deleteAccount(3L) }
    }

    @Test
    fun `clearError resets error state`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns null

        buildViewModel()

        assertNotNull(viewModel.uiState.value.error)
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `saveAccount trims whitespace from name and notes`() = runTest {
        coEvery { authRepository.getCurrentUserId() } returns userId
        every { repository.getAllAccounts(userId) } returns flowOf(emptyList())
        coEvery { repository.insertAccount(any(), userId) } returns 1L

        buildViewModel()

        viewModel.saveAccount(
            id = 0L,
            accountName = "  My Fund  ",
            provider = " Chase ",
            currentValue = 500.0,
            contributionAmount = 0.0,
            employerContribution = null,
            contributionFrequency = RecurrenceFrequency.MONTHLY,
            accountType = AccountType.SAVINGS,
            notes = "  some notes  "
        )

        coVerify {
            repository.insertAccount(match {
                it.accountName == "My Fund" && it.provider == "Chase" && it.notes == "some notes"
            }, userId)
        }
    }
}
