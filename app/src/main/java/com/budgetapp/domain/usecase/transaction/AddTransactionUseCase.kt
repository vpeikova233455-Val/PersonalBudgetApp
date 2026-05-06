package com.budgetapp.domain.usecase.transaction

import com.budgetapp.domain.model.Transaction
import com.budgetapp.domain.repository.TransactionRepository
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(transaction: Transaction) {
        transactionRepository.insertTransaction(transaction)
    }
}
