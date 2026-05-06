# Firestore Setup Instructions

## Deploy Security Rules

1. **Install Firebase CLI** (if not already installed):
   ```bash
   npm install -g firebase-tools
   ```

2. **Login to Firebase:**
   ```bash
   firebase login
   ```

3. **Initialize Firebase in project:**
   ```bash
   cd PersonalBudgetApp
   firebase init firestore
   ```
   - Select your Firebase project
   - Accept default file names
   - Replace `firestore.rules` with the provided rules

4. **Deploy security rules:**
   ```bash
   firebase deploy --only firestore:rules
   ```

## Manual Setup (Alternative)

If you prefer not to use Firebase CLI:

1. Go to Firebase Console: https://console.firebase.google.com/
2. Select your project
3. Go to "Firestore Database" → "Rules"
4. Copy the contents of `firestore.rules` file
5. Paste into the rules editor
6. Click "Publish"

## Firestore Data Structure

```
users/{userId}/
  ├── transactions/{transactionId}
  │   ├── id: string
  │   ├── userId: string
  │   ├── type: string ("INCOME" or "EXPENSE")
  │   ├── amount: number
  │   ├── description: string
  │   ├── categoryId: number
  │   ├── date: timestamp
  │   ├── isRecurring: boolean
  │   ├── recurringId: string?
  │   ├── lastModifiedTimestamp: timestamp
  │   └── deviceId: string
  │
  ├── categories/{categoryId}
  │   ├── id: number
  │   ├── name: string
  │   ├── icon: string
  │   ├── color: string
  │   ├── isCustom: boolean
  │   ├── userId: string?
  │   └── lastModifiedTimestamp: timestamp
  │
  ├── budgets/{budgetId}
  │   ├── id: number
  │   ├── userId: string
  │   ├── categoryId: number
  │   ├── monthlyLimit: number
  │   ├── alertThreshold: number
  │   └── lastModifiedTimestamp: timestamp
  │
  ├── recurring_transactions/{recurringId}
  │   └── (similar to transactions)
  │
  └── pension_accounts/{pensionId}
      └── (pension account data)
```

## Security Rules Overview

- **Authentication Required:** All operations require user authentication
- **User Isolation:** Users can only access their own data
- **Validation:** Data validation ensures correct types and values
- **Custom Categories Only:** Users can only modify/delete custom categories, not built-in ones
- **Timestamps Required:** All documents must have valid timestamps for conflict resolution

## Testing Security Rules

You can test the rules in Firebase Console:
1. Go to Firestore Database → Rules
2. Click "Rules Playground"
3. Test read/write operations with different scenarios

## Indexes

Firestore will automatically suggest creating indexes when needed. Common queries that may need indexes:

```
// Transactions by date range
Collection: users/{userId}/transactions
Fields: date (Ascending/Descending), lastModifiedTimestamp (Ascending)

// Transactions by category
Collection: users/{userId}/transactions
Fields: categoryId (Ascending), date (Descending)
```

Create indexes when prompted by error messages in the app logs.
