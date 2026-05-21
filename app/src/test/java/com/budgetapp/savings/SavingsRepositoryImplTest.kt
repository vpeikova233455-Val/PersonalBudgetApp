package com.budgetapp.savings

import com.budgetapp.data.local.database.dao.PensionAccountDao
import com.budgetapp.data.local.entity.AccountType
import com.budgetapp.data.local.entity.PensionAccountEntity
import com.budgetapp.data.local.entity.RecurrenceFrequency
import com.budgetapp.data.local.entity.SyncStatus
import com.budgetapp.data.repository.SavingsRepositoryImpl
import com.budgetapp.domain.model.PensionAccount
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SavingsRepositoryImplTest {

    private lateinit var dao: PensionAccountDao
    private lateinit var repo: SavingsRepositoryImpl

    private val userId = "user-123"

    private fun entity(
        id: Long = 1L,
        name: String = "Emergency Fund",
        provider: String = "Chase",
        value: Double = 5000.0,
        contrib: Double = 200.0,
        employer: Double? = null,
        freq: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        type: AccountType = AccountType.SAVINGS,
        notes: String = ""
    ) = PensionAccountEntity(
        id = id,
        userId = userId,
        accountName = name,
        provider = provider,
        currentValue = value,
        contributionAmount = contrib,
        employerContribution = employer,
        contributionFrequency = freq,
        accountType = type,
        notes = notes,
        syncStatus = SyncStatus.PENDING
    )

    private fun domain(
        id: Long = 1L,
        name: String = "Emergency Fund",
        provider: String = "Chase",
        value: Double = 5000.0,
        contrib: Double = 200.0,
        employer: Double? = null,
        freq: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        type: AccountType = AccountType.SAVINGS,
        notes: String = ""
    ) = PensionAccount(
        id = id,
        accountName = name,
        provider = provider,
        currentValue = value,
        contributionAmount = contrib,
        employerContribution = employer,
        contributionFrequency = freq,
        accountType = type,
        notes = notes
    )

    @Before
    fun setUp() {
        dao = mockk()
        repo = SavingsRepositoryImpl(dao)
    }

    @Test
    fun `getAllAccounts maps entities to domain models`() = runTest {
        val entities = listOf(
            entity(id = 1L, name = "Emergency Fund", type = AccountType.SAVINGS, value = 5000.0),
            entity(id = 2L, name = "Vanguard ETF", type = AccountType.INVESTMENT, value = 20000.0)
        )
        every { dao.getAllPensionAccounts(userId) } returns flowOf(entities)

        val result = repo.getAllAccounts(userId).first()

        assertEquals(2, result.size)
        assertEquals("Emergency Fund", result[0].accountName)
        assertEquals(AccountType.SAVINGS, result[0].accountType)
        assertEquals(5000.0, result[0].currentValue, 0.001)
        assertEquals("Vanguard ETF", result[1].accountName)
        assertEquals(AccountType.INVESTMENT, result[1].accountType)
        assertEquals(20000.0, result[1].currentValue, 0.001)
    }

    @Test
    fun `getAllAccounts returns empty list when no accounts`() = runTest {
        every { dao.getAllPensionAccounts(userId) } returns flowOf(emptyList())

        val result = repo.getAllAccounts(userId).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAccountById returns domain model when found`() = runTest {
        coEvery { dao.getPensionAccountById(1L) } returns entity(id = 1L)

        val result = repo.getAccountById(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals("Emergency Fund", result.accountName)
    }

    @Test
    fun `getAccountById returns null when not found`() = runTest {
        coEvery { dao.getPensionAccountById(99L) } returns null

        val result = repo.getAccountById(99L)

        assertNull(result)
    }

    @Test
    fun `insertAccount calls dao with correct entity`() = runTest {
        val account = domain(id = 0L, name = "401k", type = AccountType.PENSION, contrib = 500.0, employer = 250.0)
        coEvery { dao.insertPensionAccount(any()) } returns 5L

        val newId = repo.insertAccount(account, userId)

        assertEquals(5L, newId)
        coVerify {
            dao.insertPensionAccount(match {
                it.accountName == "401k" &&
                it.accountType == AccountType.PENSION &&
                it.userId == userId &&
                it.contributionAmount == 500.0 &&
                it.employerContribution == 250.0
            })
        }
    }

    @Test
    fun `updateAccount calls dao update`() = runTest {
        val account = domain(id = 3L, name = "Updated Fund", value = 7500.0)
        coEvery { dao.updatePensionAccount(any()) } just runs

        repo.updateAccount(account, userId)

        coVerify {
            dao.updatePensionAccount(match {
                it.id == 3L && it.accountName == "Updated Fund" && it.currentValue == 7500.0
            })
        }
    }

    @Test
    fun `deleteAccount calls dao deleteById`() = runTest {
        coEvery { dao.deletePensionAccountById(2L) } just runs

        repo.deleteAccount(2L)

        coVerify { dao.deletePensionAccountById(2L) }
    }

    @Test
    fun `totalMonthlyContribution includes employer contribution`() {
        val account = domain(contrib = 300.0, employer = 150.0)
        assertEquals(450.0, account.totalMonthlyContribution, 0.001)
    }

    @Test
    fun `totalMonthlyContribution works without employer contribution`() {
        val account = domain(contrib = 300.0, employer = null)
        assertEquals(300.0, account.totalMonthlyContribution, 0.001)
    }

    @Test
    fun `getAllAccounts preserves notes field`() = runTest {
        val entities = listOf(entity(notes = "Max out by end of year"))
        every { dao.getAllPensionAccounts(userId) } returns flowOf(entities)

        val result = repo.getAllAccounts(userId).first()

        assertEquals("Max out by end of year", result[0].notes)
    }
}
