## Follow-up Tasks

### 🔴 CRITICAL: Remove Hard-coded Sensitive Credentials

**File:** `cloud-agent/service/server/src/main/resources/application.conf`  
**Lines:** 322-330

**Issue:**
The configuration file contains hard-coded sensitive development values that pose security risks:

```hocon
walletMnemonic = "mimic candy diamond virus hospital dragon culture price emotion tell update give faint resist faculty soup demand window dignity capital bullet purity practice fossil"
walletPassphrase = "super_secret"
didPrism = "did:prism:e3b675023ef13a2bd1b015a6b1c88d2bfbfbb09bed5b675598397aec361f0d6e"
vdrPrivateKey = "d3ed47189d10509305494d89e8f08d139beed1e8ed18c3cf6b38bc897078c052"
stateDir = "/home/pat/Desktop/workspace/iog/cloud-agent/indexer-state"
```

**Recommendations:**

#### Option 1: Remove Default Values Entirely (Recommended)
Remove the default values and rely solely on environment variables:
```hocon
walletMnemonic = ${?VDR_PRISM_DRIVER_WALLET_MNEMONIC}
walletPassphrase = ${?VDR_PRISM_DRIVER_WALLET_PASSPHRASE}
didPrism = ${?VDR_PRISM_DRIVER_DID_PRISM}
vdrPrivateKey = ${?VDR_PRISM_DRIVER_VDR_PRIVATE_KEY}
stateDir = ${?VDR_PRISM_DRIVER_VDR_STATE_DIR}
```
**Pros:** Forces users to provide their own values, no security risk  
**Cons:** Requires environment variables for all deployments

#### Option 2: Use Obviously Fake Placeholder Values with Documentation
Replace with clearly fake/placeholder values and add documentation:
```hocon
# IMPORTANT: These are placeholder values for development only.
# You MUST override these with your own secure values via environment variables.
# DO NOT use these values in any production or test environment.
walletMnemonic = "REPLACE_WITH_YOUR_24_WORD_MNEMONIC"
walletPassphrase = "REPLACE_WITH_YOUR_SECURE_PASSPHRASE"
didPrism = "did:prism:REPLACE_WITH_YOUR_DID"
vdrPrivateKey = "REPLACE_WITH_YOUR_PRIVATE_KEY_HEX"
stateDir = "/path/to/indexer-state"
```
**Pros:** Provides example structure, fails fast if not configured  
**Cons:** Still contains placeholder values in repository

#### Option 3: Move to Example Configuration File
Create a separate `application.conf.example` file with placeholders:
- Keep `application.conf` with no default values (Option 1)
- Add `application.conf.example` with documented placeholder values
- Update README/documentation to explain the setup process

**Pros:** Best of both worlds - secure defaults + documentation  
**Cons:** Requires documentation updates and user setup steps

**Recommendation:** Use **Option 1** (no defaults) for security, or **Option 3** for better developer experience.

---

### 📝 Additional Suggestions

#### 1. Add Configuration Validation at Startup
**Consideration:** The `VdrConfig.validate` method already validates mutual exclusivity of blockfrost options, but consider adding startup validation that:
- Checks if sensitive values look like real credentials (not empty, proper format)
- Warns if using default/development values in non-development environments
- Validates that `stateDir` path is writable before starting the service

#### 2. Document VDR Configuration in README
**Missing Documentation:**
- How to configure VDR with different drivers (in-memory, database, PRISM)
- Environment variable reference for PRISM driver configuration
- Example docker-compose or deployment configurations
- Security best practices for managing wallet mnemonics and private keys

#### 3. Consider Using Secret Management
**Enhancement Suggestion:**
Since the codebase already has Vault integration for `SecretStorageConfig`, consider:
- Extending Vault integration to fetch VDR credentials at runtime
- Adding support for other secret managers (AWS Secrets Manager, Azure Key Vault)
- Document how to use external secret management for VDR configuration

#### 4. Test Coverage for Configuration Loading
**Current State:** Tests validate configuration validation logic  
**Enhancement:** Add integration tests that verify:
- Configuration loading from environment variables
- Proper error messages when required config is missing
- Behavior when PRISM driver is disabled vs enabled

#### 5. Personal Path in Configuration
**Issue:** Line 330 contains personal filesystem path: `/home/pat/Desktop/workspace/iog/cloud-agent/indexer-state`  
**Fix:** Change to a more generic default like:
- `./indexer-state` (relative to working directory)
- `/var/lib/identus/indexer-state` (standard system location)
- Or remove default entirely (see Option 1 above)

---

## Implementation Priority

1. **HIGH PRIORITY:** Remove redundant comment (this task) ✅
2. **CRITICAL:** Address hard-coded credentials in `application.conf`
3. **HIGH:** Fix personal path in `stateDir` default
4. **MEDIUM:** Add documentation for VDR configuration
5. **LOW:** Consider secret management integration
6. **LOW:** Add configuration loading integration tests

---

## Notes

- The configuration comments in `application.conf` (lines 311-316) explaining mutual exclusivity are valuable and should be kept.
- No other development comments (TODO, FIXME, HACK, etc.) were found in the diff.
- The code changes follow good practices with proper validation and clear configuration structure.
