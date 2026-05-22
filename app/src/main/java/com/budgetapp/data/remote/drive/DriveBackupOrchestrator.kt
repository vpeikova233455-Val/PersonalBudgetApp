package com.budgetapp.data.remote.drive

import android.accounts.Account
import android.content.Context
import com.budgetapp.core.constants.Constants.DRIVE_BACKUP_FOLDER
import com.budgetapp.core.constants.Constants.DRIVE_SCOPE
import com.budgetapp.core.constants.Constants.KEY_DRIVE_ACCOUNT_EMAIL
import com.budgetapp.core.constants.Constants.KEY_DRIVE_ACCOUNT_TYPE
import com.budgetapp.core.constants.Constants.KEY_DRIVE_FOLDER_ID
import com.budgetapp.core.constants.Constants.KEY_DRIVE_LAST_BACKUP
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.PensionAccountDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.CategoryEntity
import com.budgetapp.data.local.entity.PensionAccountEntity
import com.budgetapp.data.local.entity.TransactionEntity
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.data.export.ExportService
import com.budgetapp.domain.repository.AuthRepository
import com.google.android.gms.auth.GoogleAuthUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DriveBackupOrchestrator"

sealed class BackupResult {
    object Success : BackupResult()
    data class Error(val message: String) : BackupResult()
    object NotConfigured : BackupResult()
    object NeedsReauth : BackupResult()
}

@Singleton
class DriveBackupOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveService: GoogleDriveService,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val pensionAccountDao: PensionAccountDao,
    private val exportService: ExportService,
    private val authRepository: AuthRepository
) {

    suspend fun performBackup(): BackupResult {
        val email = EncryptionManager.getString(context, KEY_DRIVE_ACCOUNT_EMAIL).orEmpty()
        val accountType = EncryptionManager.getString(context, KEY_DRIVE_ACCOUNT_TYPE).orEmpty()
        if (email.isBlank()) {
            AppLogger.w(TAG, "Drive not connected — skipping backup")
            return BackupResult.NotConfigured
        }

        return try {
            val token = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(context, Account(email, accountType.ifBlank { "com.google" }), DRIVE_SCOPE)
            }
            uploadAllFiles(token)
            EncryptionManager.saveString(context, KEY_DRIVE_LAST_BACKUP, System.currentTimeMillis().toString())
            AppLogger.i(TAG, "Drive backup completed successfully")
            BackupResult.Success
        } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
            AppLogger.w(TAG, "Drive token expired — user needs to re-authorize")
            BackupResult.NeedsReauth
        } catch (e: com.google.android.gms.auth.GoogleAuthException) {
            AppLogger.e(TAG, "Drive auth error", e)
            BackupResult.Error("Authentication error: ${e.message}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Drive backup failed", e)
            BackupResult.Error(e.message ?: "Backup failed")
        }
    }

    private suspend fun uploadAllFiles(token: String) {
        val folderId = getOrCacheFolderId(token)
        val userId = authRepository.getCurrentUserId() ?: "local_user"

        val transactions = transactionDao.getAllTransactions(userId).first()
        val categories = categoryDao.getAllCategoriesSync()
        val savings = pensionAccountDao.getAllPensionAccounts(userId).first()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val files = mapOf(
            "Transactions_$today.xlsx" to buildTransactionsExcel(transactions),
            "Categories_$today.xlsx"   to buildCategoriesExcel(categories),
            "Savings_$today.xlsx"      to buildSavingsExcel(savings),
            "MonthlyReport_$today.xlsx" to buildMonthlyReportExcel(transactions)
        )

        files.forEach { (name, bytes) ->
            driveService.uploadOrReplaceFile(token, folderId, name, bytes)
            AppLogger.d(TAG, "Uploaded $name (${bytes.size} bytes)")
        }
    }

    private suspend fun getOrCacheFolderId(token: String): String {
        val cached = EncryptionManager.getString(context, KEY_DRIVE_FOLDER_ID).orEmpty()
        if (cached.isNotBlank()) return cached
        val id = driveService.getOrCreateFolder(token, DRIVE_BACKUP_FOLDER)
        EncryptionManager.saveString(context, KEY_DRIVE_FOLDER_ID, id)
        return id
    }

    // ── Excel builders ────────────────────────────────────────────────────────

    private fun buildTransactionsExcel(entities: List<TransactionEntity>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Transactions")
        val bold = workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true }) }

        val headers = listOf("Date", "Description", "Type", "Amount", "Category ID", "Bank", "Recurring")
        val header = sheet.createRow(0)
        headers.forEachIndexed { i, h -> header.createCell(i).also { it.setCellValue(h); it.cellStyle = bold } }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        entities.forEachIndexed { idx, t ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(dateFmt.format(Date(t.date)))
            row.createCell(1).setCellValue(t.description)
            row.createCell(2).setCellValue(t.type.name)
            row.createCell(3).setCellValue(t.amount)
            row.createCell(4).setCellValue(t.categoryId.toDouble())
            row.createCell(5).setCellValue(t.bankName ?: "")
            row.createCell(6).setCellValue(if (t.isRecurring) "Yes" else "No")
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
        return workbook.toBytes()
    }

    private fun buildCategoriesExcel(entities: List<CategoryEntity>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Categories")
        val bold = workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true }) }

        val headers = listOf("ID", "Name", "Icon", "Color", "Type")
        val header = sheet.createRow(0)
        headers.forEachIndexed { i, h -> header.createCell(i).also { it.setCellValue(h); it.cellStyle = bold } }

        entities.forEachIndexed { idx, c ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(c.id.toDouble())
            row.createCell(1).setCellValue(c.name)
            row.createCell(2).setCellValue(c.icon)
            row.createCell(3).setCellValue(c.color)
            row.createCell(4).setCellValue(if (c.isCustom) "Custom" else "Built-in")
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
        return workbook.toBytes()
    }

    private fun buildSavingsExcel(entities: List<PensionAccountEntity>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Savings & Investments")
        val bold = workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true }) }

        val headers = listOf("Name", "Type", "Provider", "Current Value", "Monthly Contribution", "Employer Contribution", "Frequency", "Notes")
        val header = sheet.createRow(0)
        headers.forEachIndexed { i, h -> header.createCell(i).also { it.setCellValue(h); it.cellStyle = bold } }

        entities.forEachIndexed { idx, a ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(a.accountName)
            row.createCell(1).setCellValue(a.accountType.name)
            row.createCell(2).setCellValue(a.provider)
            row.createCell(3).setCellValue(a.currentValue)
            row.createCell(4).setCellValue(a.contributionAmount)
            row.createCell(5).setCellValue(a.employerContribution ?: 0.0)
            row.createCell(6).setCellValue(a.contributionFrequency.name)
            row.createCell(7).setCellValue(a.notes)
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
        return workbook.toBytes()
    }

    private fun buildMonthlyReportExcel(entities: List<TransactionEntity>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Monthly Report")
        val bold = workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true }) }

        val headers = listOf("Month", "Income", "Expenses", "Net")
        val header = sheet.createRow(0)
        headers.forEachIndexed { i, h -> header.createCell(i).also { it.setCellValue(h); it.cellStyle = bold } }

        val cal = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.US)
        val grouped = entities.groupBy { t ->
            val c = Calendar.getInstance().also { it.timeInMillis = t.date }
            c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
        }

        grouped.entries.sortedByDescending { it.key }.take(24).forEachIndexed { idx, (key, txns) ->
            cal.set(key / 100, key % 100, 1)
            val income   = txns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expenses = txns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(monthFmt.format(cal.time))
            row.createCell(1).setCellValue(income)
            row.createCell(2).setCellValue(expenses)
            row.createCell(3).setCellValue(income - expenses)
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
        return workbook.toBytes()
    }

    private fun XSSFWorkbook.toBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        write(bos)
        close()
        return bos.toByteArray()
    }
}
