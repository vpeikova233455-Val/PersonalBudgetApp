package com.budgetapp.settings

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.data.remote.drive.BackupResult
import com.budgetapp.data.remote.drive.DriveBackupOrchestrator
import com.budgetapp.data.remote.github.GitHubService
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.repository.SyncStatus
import com.budgetapp.presentation.settings.BugReportStatus
import com.budgetapp.presentation.settings.DriveBackupStatus
import com.budgetapp.presentation.settings.SettingsViewModel
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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
    private lateinit var driveOrchestrator: DriveBackupOrchestrator
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        authRepository = mockk()
        syncRepository = mockk()
        gitHubService = mockk()
        driveOrchestrator = mockk()
        context = mockk(relaxed = true)

        mockkObject(EncryptionManager)
        every { EncryptionManager.getString(any(), any(), any()) } returns null
        every { EncryptionManager.saveString(any(), any(), any()) } just Runs

        every { syncRepository.getSyncStatus() } returns flowOf(SyncStatus())

        viewModel = SettingsViewModel(
            authRepository = authRepository,
            syncRepository = syncRepository,
            gitHubService = gitHubService,
            driveBackupOrchestrator = driveOrchestrator,
            context = context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(EncryptionManager)
    }

    // ── Drive sign-in ─────────────────────────────────────────────────────────

    @Test
    fun `onDriveSignInError sets driveBackupStatus to Error`() {
        viewModel.onDriveSignInError("Sign-in failed (code 12500): null")

        val status = viewModel.uiState.value.driveBackupStatus
        assertTrue(status is DriveBackupStatus.Error)
        assertEquals("Sign-in failed (code 12500): null", (status as DriveBackupStatus.Error).message)
    }

    @Test
    fun `onDriveSignInSuccess stores email and updates uiState`() {
        val account = mockk<GoogleSignInAccount>(relaxed = true) {
            every { email } returns "user@gmail.com"
        }

        viewModel.onDriveSignInSuccess(account)

        assertEquals("user@gmail.com", viewModel.uiState.value.driveEmail)
        verify { EncryptionManager.saveString(any(), any(), "user@gmail.com") }
    }

    @Test
    fun `onDriveSignInSuccess with null email does nothing`() {
        val account = mockk<GoogleSignInAccount>(relaxed = true) {
            every { email } returns null
        }

        viewModel.onDriveSignInSuccess(account)

        assertEquals("", viewModel.uiState.value.driveEmail)
    }

    // ── Drive disconnect ──────────────────────────────────────────────────────

    @Test
    fun `disconnectDrive clears driveEmail and resets status`() {
        viewModel.onDriveSignInSuccess(mockk(relaxed = true) {
            every { email } returns "user@gmail.com"
        })

        viewModel.disconnectDrive()

        val state = viewModel.uiState.value
        assertEquals("", state.driveEmail)
        assertEquals(0L, state.driveLastBackup)
        assertTrue(state.driveBackupStatus is DriveBackupStatus.Idle)
    }

    // ── Drive backup ──────────────────────────────────────────────────────────

    @Test
    fun `manualDriveBackup sets Running then Success on success`() = runTest {
        val now = System.currentTimeMillis()
        coEvery { driveOrchestrator.performBackup() } returns BackupResult.Success

        viewModel.manualDriveBackup()

        val status = viewModel.uiState.value.driveBackupStatus
        assertTrue(status is DriveBackupStatus.Success)
        assertTrue(viewModel.uiState.value.driveLastBackup >= now)
    }

    @Test
    fun `manualDriveBackup sets Error on failure`() = runTest {
        coEvery { driveOrchestrator.performBackup() } returns BackupResult.Error("network error")

        viewModel.manualDriveBackup()

        val status = viewModel.uiState.value.driveBackupStatus
        assertTrue(status is DriveBackupStatus.Error)
        assertEquals("network error", (status as DriveBackupStatus.Error).message)
    }

    @Test
    fun `manualDriveBackup sets NeedsReauth on reauth result`() = runTest {
        coEvery { driveOrchestrator.performBackup() } returns BackupResult.NeedsReauth

        viewModel.manualDriveBackup()

        assertTrue(viewModel.uiState.value.driveBackupStatus is DriveBackupStatus.NeedsReauth)
    }

    @Test
    fun `manualDriveBackup is ignored when already Running`() = runTest {
        coEvery { driveOrchestrator.performBackup() } coAnswers {
            kotlinx.coroutines.delay(1_000)
            BackupResult.Success
        }

        // first call puts it in Running state synchronously with UnconfinedTestDispatcher
        // second call should be ignored
        viewModel.manualDriveBackup()
        // at this point status is Running (delay not yet finished with unconfined dispatcher
        // because delay suspends — we check idempotency by verifying performBackup called once)
        viewModel.manualDriveBackup()

        coVerify(exactly = 1) { driveOrchestrator.performBackup() }
    }

    @Test
    fun `clearDriveStatus resets to Idle`() = runTest {
        coEvery { driveOrchestrator.performBackup() } returns BackupResult.Error("oops")
        viewModel.manualDriveBackup()

        viewModel.clearDriveStatus()

        assertTrue(viewModel.uiState.value.driveBackupStatus is DriveBackupStatus.Idle)
    }

    // ── GitHub settings ───────────────────────────────────────────────────────

    @Test
    fun `saveGitHubSettings persists and updates uiState`() {
        viewModel.saveGitHubSettings("tok123", "owner1", "repo1")

        val state = viewModel.uiState.value
        assertEquals("tok123", state.githubToken)
        assertEquals("owner1", state.githubOwner)
        assertEquals("repo1", state.githubRepo)
        verify { EncryptionManager.saveString(any(), any(), "tok123") }
    }

    @Test
    fun `submitBugReport with blank credentials sets Error without network call`() {
        every { EncryptionManager.getString(any(), any()) } returns ""

        viewModel.submitBugReport("Title", "Description")

        val status = viewModel.uiState.value.bugReportStatus
        assertTrue(status is BugReportStatus.Error)
        coVerify(exactly = 0) { gitHubService.createIssue(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `clearBugReportStatus resets to Idle`() = runTest {
        every { EncryptionManager.getString(any(), any()) } returns ""
        viewModel.submitBugReport("T", "D")

        viewModel.clearBugReportStatus()

        assertTrue(viewModel.uiState.value.bugReportStatus is BugReportStatus.Idle)
    }
}
