# Movie Recommender App - Documentation Index

**Last Updated:** 2026-01-19  
**Version:** 1.0  
**Project:** Movie Recommender Android/Fire TV App

---

## 📚 Documentation Navigation

This directory contains **SOURCE-OF-TRUTH** documentation for the entire project. All claims are traceable to code with file paths and line numbers.

### Core Documentation (Start Here)

| Document | Purpose | When to Read |
|----------|---------|--------------|
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | System architecture, MVVM structure, module boundaries | Before making any architectural changes |
| **[WORKFLOW.md](WORKFLOW.md)** | User journeys, navigation flows, error handling | Understanding user experience |
| **[FEATURES.md](FEATURES.md)** | Complete feature inventory with code paths | Before adding/modifying features |

### Technical Deep Dives

| Document | Purpose | When to Read |
|----------|---------|--------------|
| **[API_INTEGRATIONS.md](API_INTEGRATIONS.md)** | All 6 external APIs: TMDB, OpenAI, YTS, Popcorn, OMDb, IMDB | Modifying API calls or adding new services |
| **[RECOMMENDATION_ENGINE.md](RECOMMENDATION_ENGINE.md)** | LLM pipeline, validation logic, fallback algorithm | Changing recommendation logic |
| **[DATA_STORAGE.md](DATA_STORAGE.md)** | Room v2 schema, DataStore preferences, cache management | Database schema changes or preference updates |
| **[MEDIA_PLAYBACK.md](MEDIA_PLAYBACK.md)** | Trailer + torrent streaming architecture | Modifying playback features |
| **[TV_SUPPORT.md](TV_SUPPORT.md)** | Fire TV specifics: DPAD, focus, leanback | Fire TV development or certification |

### Reference Materials

| Document | Purpose | When to Read |
|----------|---------|--------------|
| **[CONFIG_REGISTRY.md](CONFIG_REGISTRY.md)** | Complete config wiring map (local.properties → runtime) | Adding new config values |
| **[KNOWN_ISSUES.md](KNOWN_ISSUES.md)** | 20 documented issues with risk levels and fixes | Bug fixes, technical debt planning |
| **[INTEGRITY_AUDIT.md](INTEGRITY_AUDIT.md)** | Documentation verification report (98% alignment) | Validating documentation accuracy |
| **[TESTING.md](TESTING.md)** | Testing strategy and guidelines | Writing tests (currently 0% coverage) |
| **[DEPLOYMENT.md](DEPLOYMENT.md)** | Build, release, and deployment instructions | Preparing releases |
| **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** | Common problems and solutions | Debugging issues |

---

## 🚀 Quick Start

**New to the project?** Read in this order:

1. [ARCHITECTURE.md](ARCHITECTURE.md) - Understand the system structure
2. [WORKFLOW.md](WORKFLOW.md) - Learn user flows
3. [FEATURES.md](FEATURES.md) - See what the app does
4. [KNOWN_ISSUES.md](KNOWN_ISSUES.md) - Know the current limitations

**Making changes?** Always:

1. Read relevant documentation first
2. Make code changes
3. Update documentation to reflect changes
4. Verify documentation accuracy

---

## 📊 Documentation Stats

- **Total Files:** 14
- **Total Lines:** ~6000
- **Code References:** 200+
- **Alignment:** 98% (verified 2026-01-19)
- **Coverage:** 100% (all features documented)

---

## 🔍 How to Use This Documentation

### Finding Information

**By Topic:**
- Architecture/Design → [ARCHITECTURE.md](ARCHITECTURE.md)
- User Features → [FEATURES.md](FEATURES.md)
- APIs → [API_INTEGRATIONS.md](API_INTEGRATIONS.md)
- Database → [DATA_STORAGE.md](DATA_STORAGE.md)
- Fire TV → [TV_SUPPORT.md](TV_SUPPORT.md)

**By Task:**
- Adding a feature → [FEATURES.md](FEATURES.md) + [ARCHITECTURE.md](ARCHITECTURE.md)
- Fixing a bug → [KNOWN_ISSUES.md](KNOWN_ISSUES.md) + [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- Changing recommendations → [RECOMMENDATION_ENGINE.md](RECOMMENDATION_ENGINE.md)
- Modifying UI → [WORKFLOW.md](WORKFLOW.md) + [TV_SUPPORT.md](TV_SUPPORT.md)

**By Component:**
- ViewModel → [ARCHITECTURE.md](ARCHITECTURE.md) + [WORKFLOW.md](WORKFLOW.md)
- Repository → [ARCHITECTURE.md](ARCHITECTURE.md) + [API_INTEGRATIONS.md](API_INTEGRATIONS.md)
- Room Database → [DATA_STORAGE.md](DATA_STORAGE.md)
- Streaming → [MEDIA_PLAYBACK.md](MEDIA_PLAYBACK.md)

---

## ⚠️ Critical Information

### Before Making Changes

**Always check:**
1. [KNOWN_ISSUES.md](KNOWN_ISSUES.md) - Avoid breaking existing workarounds
2. [CONFIG_REGISTRY.md](CONFIG_REGISTRY.md) - Verify config dependencies
3. [ARCHITECTURE.md](ARCHITECTURE.md) - Understand data flow

### After Making Changes

**Always update:**
1. Relevant documentation files
2. Code references (file paths + line numbers)
3. [KNOWN_ISSUES.md](KNOWN_ISSUES.md) if fixing issues

---

## 🎯 Documentation Principles

1. **Accuracy:** Every claim traced to code (file path + line number)
2. **Completeness:** No unknown gaps or undocumented features
3. **Consistency:** Cross-referenced and validated
4. **Maintainability:** Update docs when code changes
5. **Traceability:** All statements verifiable

---

## 📝 Key Concepts

### Product Flavors
- **mobile:** Touch UI for phones/tablets
- **firestick:** DPAD UI for Fire TV
- **Shared data layer:** No runtime conditionals

### Architecture Patterns
- **MVVM:** UI → ViewModel → Repository → Data Sources
- **StateFlow:** Reactive UI state management
- **Resource Wrapper:** Success/Error/Loading states
- **Repository Pattern:** Single source of truth for data

### Critical Components
- **Room v2:** Local database (with destructive migration issue)
- **DataStore:** 16 user preferences
- **OpenAI GPT-4o-mini:** Recommendation engine
- **TMDB API:** Movie metadata
- **TorrentStreamService:** Streaming via torrents

---

## 🔧 Common Tasks

### Adding a New Feature
1. Read [FEATURES.md](FEATURES.md) and [ARCHITECTURE.md](ARCHITECTURE.md)
2. Plan implementation (data layer → repository → ViewModel → UI)
3. Update [FEATURES.md](FEATURES.md) with new feature
4. Add to [WORKFLOW.md](WORKFLOW.md) if it affects user flow

### Fixing a Known Issue
1. Find issue in [KNOWN_ISSUES.md](KNOWN_ISSUES.md)
2. Read recommended fix
3. Implement solution
4. Update [KNOWN_ISSUES.md](KNOWN_ISSUES.md) (mark as fixed)
5. Update relevant technical docs

### Changing API Integration
1. Read [API_INTEGRATIONS.md](API_INTEGRATIONS.md) for current implementation
2. Make changes
3. Update [API_INTEGRATIONS.md](API_INTEGRATIONS.md) with new behavior
4. Update [CONFIG_REGISTRY.md](CONFIG_REGISTRY.md) if config changed

### Modifying Database Schema
1. **CRITICAL:** Read [DATA_STORAGE.md](DATA_STORAGE.md) migration section
2. Implement proper Room migration (not destructive!)
3. Update [DATA_STORAGE.md](DATA_STORAGE.md) with new schema
4. Test migration thoroughly

---

## 🚨 High-Priority Issues

From [KNOWN_ISSUES.md](KNOWN_ISSUES.md), these are **critical:**

1. **Destructive Migration** - Users lose favorites on app update
2. **No TMDB Rate Limiting** - API failures possible
3. **Debug SSL Insecurity** - MITM vulnerability
4. **Missing User Consent** - GDPR/CCPA compliance risk

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for fixes.

---

## 📚 Related Documentation

**In Project Root:**
- `README.md` - Project overview and setup
- `.github/copilot-instructions.md` - AI agent instructions
- `GETTING_STARTED.md` - First-time setup
- `LLM_INTEGRATION.md` - LLM implementation details

**In Project:**
- Build configs: `build.gradle.kts`, `settings.gradle.kts`
- Manifests: `app/src/{mobile,firestick}/AndroidManifest.xml`
- Source: `app/src/{main,mobile,firestick}/java/`

---

## 🔄 Documentation Updates

**Last Full Audit:** 2026-01-19  
**Next Audit Due:** When schema version changes, major refactoring, or new features added

**Update Frequency:**
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) - After every bug fix
- [FEATURES.md](FEATURES.md) - After every feature addition
- [ARCHITECTURE.md](ARCHITECTURE.md) - After architectural changes
- [CONFIG_REGISTRY.md](CONFIG_REGISTRY.md) - After config changes
- [INTEGRITY_AUDIT.md](INTEGRITY_AUDIT.md) - Quarterly or before releases

---

## 📞 Documentation Questions

If documentation is unclear or outdated:
1. Check [INTEGRITY_AUDIT.md](INTEGRITY_AUDIT.md) for known discrepancies
2. Verify claim against code (file paths provided)
3. Update documentation if code has changed
4. Mark as "needs verification" if unsure

---

**Remember:** This documentation is only valuable if kept up-to-date. Every code change should trigger a documentation review.
