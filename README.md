# Personal Budget App - Android

A native Android application for personal budget management with AI-powered transaction import and intelligent categorization.

## Architecture

- **Clean Architecture** with MVVM pattern
- **Offline-First** approach with Room as single source of truth
- **Jetpack Compose** for modern declarative UI
- **Firebase** for authentication and cloud sync
- **Gemini AI** for smart transaction import and categorization

## Current Implementation Status

### ✅ Phase 1 - Foundation (In Progress)

**Completed:**
- [x] Project structure setup
- [x] Gradle configuration with all dependencies
- [x] Room database schema (all entities and DAOs)
- [x] Domain layer (models, repository interfaces, use cases)
- [x] Data layer (repositories, mappers)
- [x] Hilt dependency injection setup
- [x] Firebase authentication repository
- [x] Security configuration (network security, ProGuard)
- [x] Background sync worker skeleton

**TODO (Remaining Phase 1 Tasks):**
- [ ] Task #3: Implement Firebase Authentication UI (Login, Sign Up, Email Verification)
- [ ] Task #6: Create Dashboard screen with Jetpack Compose
- [ ] Task #7: Implement Add/Edit Transaction screens
- [ ] Task #8: Build Categories management UI

### 📋 Future Phases

- **Phase 2:** Cloud Sync (Offline-first sync with Firebase)
- **Phase 3:** AI Integration (Gemini OCR, categorization, learning)
- **Phase 4:** Advanced Features (Recurring transactions, budgets, pension, analytics)
- **Phase 5:** Polish & Launch (Testing, security hardening, Play Store)

## Setup Instructions

### Prerequisites

1. **Android Studio** (latest stable version)
2. **JDK 17** or higher
3. **Firebase Project** (see setup below)
4. **Gemini API Key** (for Phase 3)

### Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or select an existing one
3. Add an Android app with package name: `com.budgetapp`
4. Download `google-services.json`
5. Replace the placeholder file at `app/google-services.json` with your downloaded file
6. Enable **Email/Password Authentication** in Firebase Console
7. Enable **Cloud Firestore** and **Cloud Storage**

### Running the App

1. Clone or download this project
2. Open the project in Android Studio
3. Setup Firebase (see above)
4. Sync Gradle dependencies
5. Run the app on an emulator or physical device

## Project Structure

```
app/src/main/java/com/budgetapp/
├── core/
│   ├── di/              # Hilt dependency injection modules
│   ├── security/        # Encryption utilities
│   ├── util/            # Extensions and helpers
│   └── constants/       # App-wide constants
├── data/
│   ├── local/
│   │   ├── database/    # Room database and DAOs
│   │   └── entity/      # Room entities
│   ├── remote/
│   │   ├── firebase/    # Firebase services
│   │   └── gemini/      # Gemini AI service (Phase 3)
│   ├── repository/      # Repository implementations
│   └── mapper/          # Data mappers
├── domain/
│   ├── model/           # Domain models
│   ├── repository/      # Repository interfaces
│   └── usecase/         # Business logic use cases
├── presentation/
│   ├── theme/           # Compose theme
│   ├── components/      # Reusable composables
│   ├── auth/            # Login/Sign up screens
│   ├── dashboard/       # Dashboard screen
│   ├── transaction/     # Transaction screens
│   ├── import/          # AI import screens (Phase 3)
│   ├── budget/          # Budget screens
│   └── settings/        # Settings screens
├── navigation/          # Navigation graph
└── worker/              # Background sync worker
```

## Database Schema

### Key Entities

- **TransactionEntity** - Income and expense transactions
- **CategoryEntity** - Built-in and custom categories
- **BudgetEntity** - Monthly budget limits per category
- **RecurringTransactionEntity** - Recurring transactions (salary, rent, etc.)
- **PendingTransactionEntity** - AI-imported transactions pending user approval
- **UserCategoryPreference** - Learning data for AI categorization
- **PensionAccountEntity** - Pension account tracking

## Key Features (Planned)

### Phase 1: Foundation
- User authentication with email/password
- Manual transaction entry (income/expense)
- Category management
- Dashboard with totals and charts

### Phase 2: Cloud Sync
- Offline-first architecture
- Background sync every 15 minutes
- Conflict resolution
- Multi-device support

### Phase 3: AI Integration
- Screenshot OCR for bank statements
- Excel/CSV file parsing
- Intelligent categorization
- Learning from user corrections
- Interactive AI that asks for help when unsure

### Phase 4: Advanced Features
- Recurring transactions
- Budget tracking with alerts
- Pension account management
- Analytics and trends
- Export to Excel/CSV

### Phase 5: Production
- Comprehensive testing
- Security hardening
- Performance optimization
- Play Store deployment

## Development Notes

### Critical Design Principles

1. **Never Invent Data** - AI asks questions when confidence < 0.7, never guesses
2. **Offline-First** - Room database is single source of truth
3. **Learn from Users** - Every correction improves future categorization
4. **Security by Default** - Encryption, TLS 1.3, code obfuscation

### Built-in Categories

The app comes with 14 pre-seeded categories:
- Income: Salary, Freelance, Investment
- Expenses: Housing, Food & Dining, Transportation, Shopping, Entertainment, Healthcare, Utilities, Insurance, Education, Savings, Other

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest
```

## License

Private project - All rights reserved

## Next Steps

1. Complete Task #3: Implement authentication screens
2. Complete Task #6: Build dashboard with charts
3. Complete Task #7: Create transaction management UI
4. Complete Task #8: Build category management
5. Test Phase 1 functionality
6. Move to Phase 2 (Cloud Sync)

---

**Note:** This is Phase 1 implementation. Many features are placeholders that will be implemented in future phases according to the implementation plan.
