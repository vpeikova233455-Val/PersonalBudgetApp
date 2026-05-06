# Changelog

All notable changes to the Personal Budget App will be documented in this file.

## [Phase 2] - 2026-05-06

### Added - Cloud Sync
- Bidirectional sync between Room and Firestore
- Offline-first architecture with conflict resolution
- Background sync worker (runs every 15 minutes)
- Manual sync trigger with pull-to-refresh on Dashboard
- Settings screen with sync controls and logout
- Sync status indicators throughout UI
- Firestore security rules for data protection
- User data isolation in Firestore
- Last-write-wins conflict resolution strategy

### Changed
- DashboardViewModel now includes sync repository
- DashboardScreen now has pull-to-refresh functionality
- Navigation updated to include Settings screen
- All entities support sync status tracking

### Technical Details
- 15 new files created
- 5 files updated
- ~1,500 lines of code added
- 82 total Kotlin files in project

## [Phase 1] - 2026-05-06

### Added - Foundation
- Firebase Authentication (email/password, email verification)
- Room database with 7 core entities
- Complete MVVM architecture with Clean Architecture
- Jetpack Compose UI for all screens
- Splash, Login, Sign Up screens
- Dashboard with income/expense/balance summary
- Add/Edit Transaction screens
- Category management (14 built-in + custom categories)
- Hilt dependency injection
- Security configuration (TLS 1.3, ProGuard, encrypted storage)

### Technical Details
- 72 Kotlin files created
- ~5,000 lines of code
- Complete project structure
- All Phase 1 requirements met

## Upcoming

### [Phase 3] - AI Integration (Planned)
- Gemini AI for OCR and categorization
- Screenshot upload and parsing
- Excel/CSV file import
- Smart categorization with learning
- Review & Approve workflow
- User preference learning

### [Phase 4] - Advanced Features (Planned)
- Recurring transactions
- Budget tracking with alerts
- Pension account management
- Analytics dashboard
- Export to Excel/CSV
- Push notifications

### [Phase 5] - Production (Planned)
- Comprehensive testing
- Security hardening
- Performance optimization
- Play Store deployment
- Beta testing program
