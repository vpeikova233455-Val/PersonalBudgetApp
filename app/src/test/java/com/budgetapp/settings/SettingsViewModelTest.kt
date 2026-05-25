package com.budgetapp.settings

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.data.remote.github.GitHubService
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.repository.SyncStatus
import com.budgetapp.presentation.settings.BugReportStatus
import com.budgetapp.presentation.settings.SettingsViewModel
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
class SettingsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var gitHubService: GitHubService
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        authRepository = mockk()
        syncRepository = mockk()
        gitHubService  = mockk()
        context        = mockk(relaxed = true)

        mockkObject(EncryptionManager)
        every { EncryptionManager.getString(any(), any(), any()) } returns null
        every { EncryptionManager.saveString(any(), any(), any()) } just Runs

        every { syncRepository.getSyncStatus() } returns flowOf(SyncStatus())

        viewModel = SettingsViewModel(
            authRepository = authRepository,
            syncRepository = syncRepository,
            gitHubService  = gitHubService,
            context        = context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(EncryptionManager)
    }

    // ── GitHub settings ───────────────────────────────────────────────────────

    @Test
    fun `saveGitHubSettings persists all three values and updates uiState`() {
        viewModel.saveGitHubSettings("tok123", "owner1", "repo1")

        val state = viewModel.uiState.value
        assertEquals("tok123", state.githubToken)
        assertEquals("owner1", state.githubOwner)
        assertEquals("repo1",  state.githubRepo)
        verify { EncryptionManager.saveString(any(), any(), "tok123") }
        verify { EncryptionManager.saveString(any(), any(), "owner1") }
        verify { EncryptionManager.saveString(any(), any(), "repo1")  }
    }

    @Test
    fun `saveGitHubSettings with empty values still persists`() {
        viewModel.saveGitHubSettings("", "", "")

        val state = viewModel.uiState.value
        assertEquals("", state.githubToken)
        assertEquals("", state.githubOwner)
        assertEquals("", state.githubRepo)
    }

    // ── Bug report ────────────────────────────────────────────────────────────

    @Test
    fun `submitBugReport with blank credentials sets Error without network call`() {
        every { EncryptionManager.getString(any(), any()) } returns ""

        viewModel.submitBugReport("Title", "Description")

        val status = viewModel.uiState.value.bugReportStatus
        assertTrue(status is BugReportStatus.Error)
        coVerify(exactly = 0) { gitHubService.createIssue(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `submitBugReport error message mentions GitHub settings`() {
        every { EncryptionManager.getString(any(), any()) } returns ""

        viewModel.submitBugReport("T", "D")

        val msg = (viewModel.uiState.value.bugReportStatus as BugReportStatus.Error).message
        assertTrue(msg.contains("GitHub", ignoreCase = true))
    }

    @Test
    fun `clearBugReportStatus resets to Idle`() {
        every { EncryptionManager.getString(any(), any()) } returns ""
        viewModel.submitBugReport("T", "D")

        viewModel.clearBugReportStatus()

        assertTrue(viewModel.uiState.value.bugReportStatus is BugReportStatus.Idle)
    }

    // ── Language ──────────────────────────────────────────────────────────────

    @Test
    fun `setLanguage updates currentLanguage in uiState`() {
        viewModel.setLanguage("he")
        assertEquals("he", viewModel.uiState.value.currentLanguage)
    }

    @Test
    fun `setLanguage to en resets to empty locale list`() {
        viewModel.setLanguage("ru")
        viewModel.setLanguage("en")
        assertEquals("en", viewModel.uiState.value.currentLanguage)
    }
}
