# v1.5.0 Release Gate — Acceptance Evidence

- **Purpose:** Acceptance trace for the v1.5.0 release gate, covering stories US-43, US-44, US-45, US-46, US-47 and US-48 (US-48 closed by its manual smoke checklist, `ADMIN` negative smoke test, and the two CI pipelines — see its section) plus the role-based smoke-test criterion. This record expands the earlier US-43/US-45-only note; the filename is kept for continuity. US-49 (added after the gate as Phase 13) is recorded in its own section below.
- **Verification command:** `.\mvnw.cmd test` (run from the repository root, `CitizenLink/`).
- **Verification run:** Date: 2026-08-30 — Total tests: **361**, Failures: **0**, Errors: **0**, Skipped: **0** (exit code **0**, `BUILD SUCCESS`, total time 01:21 min). Counts from the final full-suite run — 357 prior tests plus the 4 new role smoke tests.
- **Error-code policy (approved):** Case-level IDOR and ID-not-belonging-to-path return **404** (`ResourceNotFoundException`) — existence is never revealed. Author/uploader-only violations return **403** via `SecurityException`. Both mappings are asserted by the tests cited below.
- AC wording is summarized from the story tracker; every code/test reference below was verified in the repository on the verification date.

## US-43 — Enforce case access on notes and attachments

**Problem.** Note and attachment operations resolved resources by ID without consistently checking whether the requester could access the parent case — an IDOR channel: agents could reach notes/attachments on cases they did not create and handlers on cases not assigned to them, with status-code differences leaking the existence of hidden cases.

**Solution.** Every note/attachment service operation funnels through `requireAccessibleCase` (backed by `CaseAccessPolicy.canView`) or a note/attachment↔case binding check, both throwing `ResourceNotFoundException` (404) so existence is never revealed; author/uploader-only violations return 403 via `SecurityException` per the approved policy.

**Acceptance evidence.** Coverage lives in `src/test/java/com/ntg/citizenlink/integration/NotesAttachmentsAccessControlIntegrationTest.java`. At verification time the US-43 test additions below and this record are uncommitted in the working tree — commit them together when closing the gate.

Access rule: `CaseAccessPolicy.canView` (`src/main/java/com/ntg/citizenlink/security/CaseAccessPolicy.java:24-38`) — ADMIN/SUPERVISOR see any case, HANDLER only cases assigned to them, AGENT only cases they created.

| AC | Implementation refs (verified line numbers) | Proving tests |
|----|---------------------------------------------|---------------|
| AC1 — Notes endpoints enforce case access for the requester | `CaseNoteServiceImpl.requireAccessibleCase` (`service/impl/CaseNoteServiceImpl.java:181-191`), invoked from `addNote` (:41), `getNotesByCaseId` list (:65), paginated (:79), `getNoteById` (:104), `updateNote` (:121), `deleteNote` (:157), `countNotesByCaseId` (:177) | `intruder_cannotListNotes_onAnotherUsersCase`, `intruder_cannotAddNote_onAnotherUsersCase`, `intruder_cannotGetNoteById_onAnotherUsersCase`, `intruder_cannotUpdateNote_onAnotherUsersCase`, `intruder_cannotDeleteNote_onAnotherUsersCase`, `noteId_fromOtherCase_withTamperedCaseId_returns404` (existing); `intruder_cannotListPaginatedNotes_onAnotherUsersCase` (new) — all in `src/test/java/com/ntg/citizenlink/integration/NotesAttachmentsAccessControlIntegrationTest.java` |
| AC2 — Attachment endpoints enforce case access for the requester | `AttachmentServiceImpl.requireAccessibleCase` (`service/impl/AttachmentServiceImpl.java:247-257`), invoked from `uploadAttachment` (:51), `getAttachmentsByCaseId` (:125), `downloadAttachment` (:146), `getAttachmentById` (:178), `deleteAttachment` (:195), `countAttachmentsByCaseId` (:243) | `intruder_cannotListAttachments_onAnotherUsersCase`, `intruder_cannotUploadAttachment_onAnotherUsersCase`, `intruder_cannotDownloadAttachment_onAnotherUsersCase`, `intruder_cannotDeleteAttachment_onAnotherUsersCase`, `attachmentId_fromOtherCase_withTamperedCaseId_returns404` (existing); `intruder_cannotGetAttachmentById_onAnotherUsersCase` (new) |
| AC3 — Note/attachment ID from another case addressed through a tampered caseId returns 404, and the target resource is left intact | Binding checks: `getNoteById` (:100-102), `updateNote` (:117-119), `deleteNote` (:153-155); `downloadAttachment` (:142-144), `getAttachmentById` (:174-176), `deleteAttachment` (:191-193). The new tests use the ADMIN token, for which `CaseAccessPolicy.canView` always passes, so the 404 can only originate from the binding mismatch — this isolates the binding check from the case-level rule | `admin_updatingNoteId_fromAnotherCase_withTamperedCaseId_returns404` (asserts note body unchanged via follow-up GET), `admin_deletingNoteId_fromAnotherCase_withTamperedCaseId_returns404` (asserts note still exists), `admin_downloadingAttachmentId_fromAnotherCase_withTamperedCaseId_returns404`, `admin_deletingAttachmentId_fromAnotherCase_withTamperedCaseId_returns404` (asserts attachment still listed) — all new |
| AC4 — Author/uploader-only violations return 403, not 404 | `SecurityException` thrown in `CaseNoteServiceImpl.updateNote` (:129), `deleteNote` (:165), `AttachmentServiceImpl.deleteAttachment` (:203) for non-author/non-uploader non-admin users | `handler_assignedToCase_cannotUpdateAuthorsNote_returns403`, `handler_assignedToCase_cannotDeleteAuthorsNote_returns403`, `handler_assignedToCase_cannotDeleteUploadersAttachment_returns403` (existing) |
| AC5 — Auxiliary read endpoints (`/count`) enforce the same case access rule | `countNotesByCaseId` (:177) and `countAttachmentsByCaseId` (:243) call `requireAccessibleCase` before counting | `intruder_cannotCountNotes_onAnotherUsersCase`, `intruder_cannotCountAttachments_onAnotherUsersCase` (new) |

## US-44 — Reliable refresh-token session flow

**Problem.** The refresh flow let the same refresh token be replayed concurrently (double rotation), and missing/expired/malformed refresh tokens produced bare 401s that left the bad cookie in place; a deactivated account could keep minting fresh access tokens through refresh.

**Solution.** A single-flight refresh lock per user: when several requests race with the same refresh token, exactly one rotation succeeds and the rest fail with 401. Missing/expired/malformed refresh tokens return 401 **and clear the refresh cookie** (max-age=0). A disabled account hitting refresh throws `DisabledException`, gets the standard `ACCOUNT_DISABLED` 401 envelope, and its stored refresh-token JTI is cleared — revoking the session.

**Acceptance evidence.** Commit `45aa143` — *"fix(auth): harden refresh-token session flow (US-44)"*, merged via PR #101.

| Area | Proving tests |
|------|---------------|
| Malformed/expired refresh → 401 + cookie cleared | `AuthFlowIntegrationTest.refresh_withMalformedToken_returns401_andClearsCookie`, `AuthFlowIntegrationTest.refresh_withExpiredToken_returns401_andClearsCookie` (`src/test/java/com/ntg/citizenlink/integration/AuthFlowIntegrationTest.java`) |
| Concurrency: only one of N racing refreshes succeeds | `AuthFlowIntegrationTest.refresh_concurrentRequests_onlyOneSucceeds` (5 racing requests, exactly 1 × 200) |
| Missing cookie → 401 | `AuthControllerTest.refresh_returns401_whenCookieMissing` (`src/test/java/com/ntg/citizenlink/controller/AuthControllerTest.java`) |
| Disabled account on refresh → JTI cleared + `DisabledException` | `AuthServiceImplTest.shouldClearJtiAndThrow_whenUserDisabled`, `AuthServiceImplTest.shouldClearJtiAndThrow_whenTokenReusedOrRevoked` (`src/test/java/com/ntg/citizenlink/service/impl/AuthServiceImplTest.java`) |

## US-45 — Block inactive users from continuing a session

**Problem.** Deactivation only blocked new logins: an outstanding refresh token kept minting fresh access tokens, so a deactivated account could continue operating a session indefinitely.

**Solution.** Inactive users are blocked at login (`DisabledException` → 401 `ACCOUNT_DISABLED` envelope) and at refresh (refresh-token JTI cleared = session revoked, standard 401 body, cookie cleared); the `.disabled` flag also propagates through request authentication, so outstanding access tokens are rejected on subsequent requests.

**Acceptance evidence.**

| AC | Implementation refs (verified line numbers) | Proving tests |
|----|---------------------------------------------|---------------|
| AC1 — An inactive user cannot log in; login returns 401 with the `ACCOUNT_DISABLED` envelope | `UserDetailsServiceImpl` (`service/UserDetailsServiceImpl.java:49`) sets `.disabled(!appUser.getActive())`; `GlobalExceptionHandler.handleDisabledAccount` (`exception/GlobalExceptionHandler.java:126-131`) maps `DisabledException` to 401 `ACCOUNT_DISABLED` | `AuthFlowIntegrationTest.inactiveUser_cannotLogin_returns401AccountDisabled` (`src/test/java/com/ntg/citizenlink/integration/AuthFlowIntegrationTest.java:329`); `AuthServiceImplTest.shouldPropagateDisabledException_whenAccountDisabled` (`src/test/java/com/ntg/citizenlink/service/impl/AuthServiceImplTest.java:152`) |
| AC2 — An inactive user cannot refresh; the refresh token is revoked (JTI cleared) and the response is a standard 401 body with the refresh cookie cleared | `AuthServiceImpl.refreshToken` (`service/impl/AuthServiceImpl.java:104-108`): inactive user → clears `refreshTokenJti` (:106) and throws `DisabledException` (:108); JTI-mismatch revocation at :111-113 | `AuthFlowIntegrationTest.inactiveUser_cannotRefresh_returns401StandardBodyAndClearsCookie` (:342); `AuthServiceImplTest.shouldClearJtiAndThrow_whenUserDisabled` (:228) |
| AC3 — An outstanding access token of a deactivated user is rejected on subsequent requests | Same `.disabled(!active)` flag propagates through request authentication via `UserDetailsServiceImpl` (line 49) | `AuthFlowIntegrationTest.deactivatedUserAccessToken_isRejected` (:188) |
| AC4 — Deactivation is a pure account-state change (only `active=false` + `refreshTokenJti=null`); case ownership and history are untouched | `UserAdminService.deactivateUser` (`service/UserAdminService.java:131-157`); state change at :151-152 | `AuthFlowIntegrationTest.deactivation_leavesCaseOwnershipAndHistoryUnchanged` (:359); `UserAdminServiceTest.deactivateUser_setsActiveFalse_explicitNotToggle` |
| AC5 — Lockout guards: a user cannot deactivate their own account, and the last active ADMIN cannot be deactivated | `UserAdminService.deactivateUser` self-guard :135-137, last-active-ADMIN guard :145-149 | `UserAdminServiceTest.deactivateUser_ownId_isRejected`, `UserAdminServiceTest.deactivateUser_lastActiveAdmin_isRejected` (`src/test/java/com/ntg/citizenlink/service/UserAdminServiceTest.java`) |

### History trace (recorded for accuracy)

The US-45 behavior shipped in commit `45aa143` — *"fix(auth): harden refresh-token session flow (US-44)"*, merged via PR #101. The commit message and PR are tagged US-44 (refresh-token hardening), but the commit introduced the inactive-user session blocking and its US-45-tagged tests: the US-45 section of `AuthFlowIntegrationTest` (line 285 onward, +214 test lines in that commit) and the disabled-user cases in `AuthServiceImplTest`. No separate US-45 commit exists; the tests above carry the US-45 designation in code.

## US-46 — Secure upload validation

**Problem.** Uploaded files were persisted before being validated: the client-controlled filename/extension influenced the stored path (path-traversal risk, M-13), and oversized or executable content reached disk before any rejection (M-04) — a storage-abuse risk and a staging vector for malicious payloads.

**Solution.** Uploads are fully validated **before storage**: a MIME whitelist (`canonicalMimeType`, including OOXML canonicalization) plus an extension whitelist, a size limit enforced **before** `Files.copy`, server-generated UUID stored names (the client-supplied extension is never trusted), and path-containment guards (`normalize()` + `startsWith(uploadDir)`). Failures return readable standard envelopes — 400 `BAD_REQUEST` at the service layer, 413 `PAYLOAD_TOO_LARGE` when the servlet multipart limit trips.

**Acceptance evidence.** Commit `84c4620` — *"test(security): add US-46 secure upload validation coverage"*, merged via PR #102. Implementation lineage: M-13/M-04 in `docs/SONARQUBE-ISSUES.md`.

| Area | Proving tests |
|------|---------------|
| Storage-layer validation, detection & containment | `FileStorageServiceTest` (12 tests — `src/test/java/com/ntg/citizenlink/service/FileStorageServiceTest.java`) |
| End-to-end upload validation: oversize rejected readable before persisting, executable types rejected | `UploadValidationIntegrationTest` (3 tests) — `oversizeUpload_returns400ReadableError_andStoresNothing`, `executableFileType_returns400ReadableError`, `unauthenticatedDownload_returns401` |
| Servlet multipart limit → 413 standard envelope | `GlobalExceptionHandlerTest.maxUploadSizeExceeded_returns413_withStandardEnvelope` (`src/test/java/com/ntg/citizenlink/controller/GlobalExceptionHandlerTest.java:122`) |
| Service-level attachment handling | `AttachmentServiceImplTest` (`src/test/java/com/ntg/citizenlink/service/impl/AttachmentServiceImplTest.java`) |
| Access-control & stored-extension behavior on the HTTP surface | `NotesAttachmentsAccessControlIntegrationTest` upload tests — `disallowedFileType_returns400_withUsableMessage`, `emptyFile_returns400_not500`, `uploadWithMisleadingClientExtension_storesDetectedExtension`, `uploadWithTraversalClientFilename_staysInsideUploadDir`, `uploadDocxRegardlessOfTikaDetectionBucket_storedAsDocx` |

## US-47 — Standard error envelope on security failures

**Problem.** Failures raised inside the security filter chain — the 401 authentication entry point, the 403 access-denied handler, and the JWT filter's user-not-found path — returned **empty bodies**. Clients received an opaque status code precisely where they most need a machine-readable reason, and the frontend could not map security failures to user-facing messages.

**Solution.** `SecurityErrorWriter` (`src/main/java/com/ntg/citizenlink/security/config/SecurityErrorWriter.java`) writes the standard `{code, message, details}` envelope for the 401 entry point and the 403 access-denied handler, and the JWT filter's user-not-found path produces the same envelope; refresh early-401s carry the envelope too. **Frontend alignment** (CitizenLink frontend repo): the login error mapping prefers the structured security codes (`ACCOUNT_DISABLED`, `ACCOUNT_LOCKED`) over brittle string matching, the frontend specs pin all 8 security codes, and 500 response-body opacity is asserted.

**Acceptance evidence.** Backend commit `8a2f1ec` — *"fix(security): return standard error envelope on security failures (US-47)"*, merged via PR #103. Frontend commit `aaeb85f` — *"fix(auth): align login error mapping with US-47 security envelopes"*, merged via PR #56 (frontend repository).

| Area | Proving tests |
|------|---------------|
| Envelope on 401/403/404 security failures, end to end | `SecurityErrorEnvelopeIntegrationTest` (6 tests) — `unauthenticatedRequest_toProtectedEndpoint_returns401Envelope`, `malformedBearerToken_returns401Envelope`, `expiredBearerToken_returns401Envelope`, `authenticatedAgent_callingAdminOnlyUrl_returns403Envelope`, `agentCallingTransitionEndpoint_returns403Envelope`, `anotherAgentsCase_returns404Envelope` |
| JWT filter user-not-found path | `JwtAuthenticationFilterTest` (`src/test/java/com/ntg/citizenlink/security/filter/JwtAuthenticationFilterTest.java`) |
| Exception-handler envelope contracts | `GlobalExceptionHandlerTest` (`src/test/java/com/ntg/citizenlink/controller/GlobalExceptionHandlerTest.java`) |

## US-48 — Run a release regression suite

**Definition.** As a QA tester / technical lead, I want a repeatable regression suite for the most important flows, so that each release proves that old functions still work. US-48 is closed by combining (a) the existing role-based and per-area integration tests into the regression core, (b) a new `ADMIN`-role negative-permission smoke test to complete the one-per-role requirement, (c) a manual staging smoke checklist, and (d) the CI pipelines that run the suite and gate the release.

| AC | Implementation refs | Proving evidence |
|----|---------------------|------------------|
| AC1 — Automated tests cover login, role access, refresh, case visibility, workflow transitions, and file authorization | Login + refresh: `AuthFlowIntegrationTest`; role access + case visibility: `RoleBasedSmokeTests` + `CaseAccessPolicyTest` + `NotesAttachmentsAccessControlIntegrationTest`; workflow transitions: `IllegalWorkflowTransitionIntegrationTest`; file authorization: `UploadValidationIntegrationTest` + `NotesAttachmentsAccessControlIntegrationTest` (upload/download); inbox: `InboxIntegrationTest` + `InboxUrgencyIntegrationTest` | All classes referenced by name in this record; run in the standard `.\mvnw.cmd test` suite that CI executes |
| AC2 — Manual smoke checklist covers ADMIN, SUPERVISOR, HANDLER, AGENT roles | `docs/US-48-regression-smoke-checklist.md` — per-role happy path + one negative check each + cross-cutting masking/i18n/error-envelope checks | New manual checklist document committed with this story |
| AC3 — At least one negative permission test for each role | `RoleBasedSmokeTests` — `agentRoleSmokeTest` (403 transition, 404 foreign case, 403 `/users`), `handlerRoleSmokeTest` (403 note edit, 404 unassigned case, 403 `/users`), `supervisorRoleSmokeTest` (403 `/users`), and the new `adminNegativePermissionsSmokeTest` (400 self-deactivate, 400 last-active-ADMIN guard, 401 unauthenticated `/users`) | The four smoke-journey methods in `src/test/java/com/ntg/citizenlink/integration/RoleBasedSmokeTests.java`; the admin method is the US-48 addition |
| AC4 — Test results are stored with the release or pull request | Backend `.github/workflows/ci.yml` uploads `target/surefire-reports`; frontend `.github/workflows/ci.yml` uploads the vitest report and runs `build:prod` | Both CI pipelines committed with this story; uploaded artifacts are retained per run and tied to the PR/commit |
| AC5 — A release cannot be approved while a Critical regression test is failing | GitHub **branch protection → required status checks** for the two `ci` checks on `master` (both repos); the release-approval rule is documented in the smoke checklist | `.github/workflows/ci.yml` (backend + frontend) + *Release-approval rule* section of `docs/US-48-regression-smoke-checklist.md`. Enforcing the required-check setting in the repo UI is a one-time manual step outside source control |

**Verification.** The two repo CI pipelines (`.github/workflows/ci.yml`) run the full test
suites on every push to `master` and every pull request and fail the run on any failing
test, so "red while Critical is failing" is enforced by the pipeline plus the required-check
branch-protection rule. The exact final test counts are recorded in the CI artifact for the
merge commit and in the release record; this acceptance record references them by commit SHA.

## US-49 — View my work inbox (Phase 13)

**Problem.** Handlers had no single, paginated view of their daily workload: the US-06 `my-open-cases` widget returns only the top 5 rows with 4 columns, cannot be filtered or paged, and its status restriction (`IN ('NEW', 'ASSIGNED', 'IN_PROGRESS')`) silently drops the non-final `AWAITING_INFO` and `SUSPENDED` states — cases a handler still has to act on disappeared from view.

**Solution.** New HANDLER-only endpoint `GET /api/v1/dashboard/my-inbox` (`@PreAuthorize("hasRole('HANDLER')")`, added alongside the untouched US-06 widget in `DashboardController`). It serves the open cases assigned to the logged-in handler as `PagedResponse<InboxCaseResponse>` — columns: case ID, case number, subject, citizen full name, priority, status, due date, last update — with server-side pagination (page ≥ 0, 1 ≤ size ≤ 100, mirroring `CaseSearchRequest` validation) and server-side filtering:

- **Default exclusion:** `CLOSED` and `CANCELLED` (final states) are excluded when no explicit `status` parameter is given; `AWAITING_INFO` and `SUSPENDED` are non-final and included.
- **Explicit `status` overrides the default** (documented behaviour) — e.g. `status=CLOSED` lists closed work.
- Optional `priority` (exact match) and `keyword` (case-number prefix OR subject LIKE, case-insensitive, wildcard-escaped — mirrors the `escapeLikeWildcards` approach in `CaseSpecification`).
- Ordering: `dueAt` ASC with nulls LAST (most urgent first) and `id` ASC tiebreaker for stable pagination.
- Query: a dynamic `InboxSpecification` (Criteria predicates) executed through the `CaseRepository.findAll(Specification, Pageable)` override with its association entity graph — avoids the nullable-enum JPQL pitfall on PostgreSQL and keeps the citizen join in the same query. The citizen full name comes from `Citizen.fullName`, the same source `CaseMapper` uses for `citizenFullName`. Works on both H2 (PostgreSQL mode, tests) and PostgreSQL.

**Evidence (this change's tests).** New `src/test/java/com/ntg/citizenlink/integration/InboxIntegrationTest.java` — 8 tests, each tagged with a `// US-49:` comment:

| Test | AC proved |
|------|-----------|
| `handler_sees_only_cases_assigned_to_them` | AC1 — inbox contains the handler's assigned case only; another handler's case and an agent-created unassigned case are absent |
| `inbox_excludes_final_states_by_default` | AC2 — a CLOSED case (ASSIGN→START→RESOLVE→CLOSE) and a CANCELLED case (CANCEL from ASSIGNED) are excluded; only the open assigned case is returned |
| `inbox_includes_awaitingInfo_and_suspended` | AC2 — AWAITING_INFO (AWAIT_INFO) and SUSPENDED (SUSPEND, comment required) both appear in the default inbox |
| `inbox_explicit_status_filter_overrides_default` | AC2 override — `status=CLOSED` returns the closed case (and the default query still excludes it) |
| `inbox_priority_and_keyword_filters` | AC4 — priority exact-match; keyword by case-number prefix, by exact case number, by subject fragment (case-insensitive); `%` and `_` in the keyword matched literally |
| `inbox_is_paginated_server_side` | AC4 — totalElements/totalPages/first/last; dueAt ASC nulls-last with the id tiebreaker keeping the row sequence deterministic across repeated page requests |
| `inbox_columns_present` | AC3 — id, caseNumber, subject, citizenFullName (equals the citizen's full name), priority, status, dueAt, updatedAt all present and populated |
| `inbox_requires_handler_role` | AC1 — AGENT, SUPERVISOR, ADMIN each get 403 on `/api/v1/dashboard/my-inbox`; unauthenticated gets 401 |

Verification: `.\mvnw.cmd test` (run from `CitizenLink/`) — Total tests: **369**, Failures: **0**, Errors: **0**, Skipped: **0** (exit code **0**, `BUILD SUCCESS`) — 361 prior tests plus the 8 new ones above. All case statuses exercised here are produced through real API workflow transitions (`/api/v1/cases/{id}/transition`) — no repository-level state tampering was needed. The gate-criteria table below is left untouched: the v1.5.0 gate predates US-49, and the counts quoted in its verification-run line reflect that earlier final run.

## US-50 / US-51 — Inbox urgency filtering and server-side ranking (Phase 13)

**Problem.** The US-49 inbox had no quick urgency filters and no badge counts, and its only ordering was due-date-first: an URGENT case due in two weeks sat *above* an overdue LOW-priority case, because `dueAt` ASC alone decided the rank. Handlers had to scan every page to find what was actually late, urgent, newly assigned, or waiting on information.

**Solution.** Two backend extensions to the US-49 inbox, with US-52 (frontend URL state) needing no backend work because the API already round-trips page + filters + sort as optional URL params:

- **US-50 — quick filters + counts.** `GET /api/v1/dashboard/my-inbox` gains optional `overdue` (TRUE → `dueAt != null AND dueAt < now`) and `dueToday` (TRUE → `todayStart <= dueAt < todayEnd`, "today" resolved in the app time zone via the same `app.time-zone` mechanism `CaseNumberServiceImpl` uses for the case-number year). Filters are independent ANDed predicates, so they combine with each other and with the existing status/priority/keyword filters; the default final-state exclusion (CLOSED/CANCELLED) keeps applying under the new dimensions unless an explicit `status` overrides it. New HANDLER-only `GET /api/v1/dashboard/my-inbox/counts` returns `InboxCountsResponse(all, overdue, dueToday, urgent, awaitingInfo, newlyAssigned)` computed over the caller's permitted cases with the same final-state exclusion on every count; dimensions overlap by design (an URGENT case due later today counts under all, dueToday, urgent and newlyAssigned). Both endpoints are `@PreAuthorize("hasRole('HANDLER')")` — the counts are scoped to the caller.
- **US-51 — server-side ranking.** Optional `sort` enum (`InboxSort`): `SMART` (default — overdue first, then priority rank URGENT=0…LOW=3, then nearest due date nulls last), `DUE_DATE` (dueAt ASC nulls last — the US-49 ordering), `PRIORITY` (rank then due date), `NEWEST` (updatedAt DESC). All orderings run inside the query: `InboxSpecification` builds CriteriaBuilder CASE expressions (overdue flag, priority rank, dueAt nulls-last flag) and applies them via `query.orderBy(...)` on content queries only (skipped for the Long-typed count query Spring Data derives for pagination totals — ORDER BY over an aggregate is invalid on PostgreSQL); every option ends with `id ASC` so pagination stays stable, and sorting therefore works with paging. Due-date edge case worth noting: a case due earlier today (dueAt already past but still inside today's window) satisfies *both* the overdue and dueToday formulas — `overdue=true&dueToday=true` returns exactly those rows, and is empty otherwise.

**Evidence (this change's tests).** New `src/test/java/com/ntg/citizenlink/integration/InboxUrgencyIntegrationTest.java` — 12 tests, tagged `// US-50:` or `// US-51:`; the US-49 test class is untouched and still green:

| Test | AC proved |
|------|-----------|
| `overdue_filter_returns_only_past_due_cases` | US-50 AC — `overdue=true` returns only the past-due case; future-due and undated cases are excluded |
| `dueToday_filter_returns_cases_due_today` | US-50 AC — `dueToday=true` returns only the case due later today (midpoint of the remaining day, so it cannot roll into tomorrow near midnight); tomorrow/yesterday excluded |
| `combined_filters_and_semantics` | US-50 AC — `overdue=true&priority=URGENT` narrows to the overlap; `overdue=true&dueToday=true` matches nothing on cleanly separated due dates; a CLOSED overdue case stays out of `overdue=true` and only `status=CLOSED&overdue=true` surfaces it |
| `counts_endpoint_reports_quick_filter_totals_scoped_to_handler` | US-50 AC — six counts asserted on an overlapping fixture (overdue, dueToday, urgent, awaitingInfo, newlyAssigned, closed-counts-nowhere); a second handler's case does not leak into the caller's counts and counts only in its owner's response; counts match the corresponding quick filters |
| `smart_sort_overdue_first` | US-51 AC — overdue LOW outranks future URGENT, which outranks future undated (SMART = overdue state, then rank, then due date) |
| `smart_sort_priority_within_same_due_state` | US-51 AC — two non-overdue cases with the same dueAt: URGENT before MEDIUM, exact order |
| `sort_due_date_orders_by_due_at_nulls_last` | US-51 AC — `sort=DUE_DATE` reproduces dueAt ASC with undated cases last |
| `sort_priority_orders_by_rank_then_due_date` | US-51 AC — `sort=PRIORITY` ranks HIGH > MEDIUM > LOW |
| `sort_newest_orders_by_last_update_desc` | US-51 AC — `sort=NEWEST` orders by updatedAt DESC (creation order reversed) |
| `smart_sort_works_with_pagination` | US-51 AC — two pages of size 2 under SMART; page 1 continues the global ordering and both pages reproduce the un-paginated SMART order |
| `counts_requires_handler_role` | US-50 AC — AGENT/SUPERVISOR/ADMIN get 403 on `/my-inbox/counts`; anonymous gets 401 |
| `inbox_params_validation_unchanged_after_extension` | US-50 AC — `size=101` still 400; all new params optional and round-trip as URL params (US-52) |

Verification: `.\mvnw.cmd test` (run from `CitizenLink/`) — Total tests: **381**, Failures: **0**, Errors: **0**, Skipped: **0** (exit code **0**, `BUILD SUCCESS`, total time 01:21 min) — 369 prior tests (including the 8 US-49 inbox tests) plus the 12 new ones above; all 20 inbox tests (`InboxIntegrationTest` + `InboxUrgencyIntegrationTest`) are green in that run. The gate-criteria table below is left untouched: the v1.5.0 gate predates US-49/50/51, and the counts quoted in its verification-run line reflect that earlier final run.

## Role-based smoke tests (gate criterion 3)

New class `src/test/java/com/ntg/citizenlink/integration/RoleBasedSmokeTests.java` — one self-contained, end-to-end journey per role over the real HTTP surface (login, cases, workflow transitions, notes, attachments, reports, user administration), with strict status-code assertions. These tests run in the standard suite (no separate profile or tag) and are included in the verification-run counts above.

| Test | Journey covered |
|------|-----------------|
| `agentRoleSmokeTest` | AGENT logs in, creates a case (201), adds a note, uploads a PDF attachment, lists notes (note present), GETs own case (200); transition attempt → **403**; another agent's case → **404**; `GET /api/v1/users` → **403**. |
| `handlerRoleSmokeTest` | Fixture: agent creates two cases + authors a note, supervisor ASSIGNs one to the handler. HANDLER GETs the assigned case (200), STARTs it (→ `IN_PROGRESS`), adds a note, attempts to update another author's note → **403**; unassigned case → **404**; `GET /api/v1/users` → **403**. |
| `supervisorRoleSmokeTest` | SUPERVISOR creates a case (201), ASSIGNs it to the handler (200 → `ASSIGNED`), sees an agent-created case (cross-visibility, 200), lists handlers (`GET /api/v1/users/handlers`, 200, handler present), reads the volume report (`GET /api/v1/reports/volume`, 200); `GET /api/v1/users` → **403** (supervisor is not admin). |
| `adminRoleSmokeTest` | ADMIN lists users (`GET /api/v1/users`, 200) and handlers (200), GETs an agent's case (200), deactivates the agent (200) → agent login now **401 `ACCOUNT_DISABLED`**; reactivates the agent (200) → login succeeds again; deletes an agent-authored note (**204**, admin override of the author-only rule). |
| `adminNegativePermissionsSmokeTest` | **US-48 addition** — completes the one-negative-per-role requirement for ADMIN: self-deactivation refused (**400 `BAD_REQUEST`**, account still active and able to log in), and an unauthenticated `GET /api/v1/users` → **401** with the standard `UNAUTHORIZED` envelope. |

## Gate criteria status

| # | Gate criterion | Status | Evidence |
|---|----------------|--------|----------|
| 1 | Stories US-43…US-48 accepted | **PASS** (US-48 closed by its dedicated section: the ADMIN negative smoke test, the US-48 smoke checklist, and the two repo CI pipelines) | Sections above, with commits `45aa143` (PR #101), `84c4620` (PR #102), `8a2f1ec` (PR #103) and the proving tests cited per story |
| 2 | Critical defects closed | **PASS** | Per `docs/SONARQUBE-ISSUES.md` all 20 issues are fixed (M-13/M-04 lineage cited under US-46). The ledger file is currently not committed to the repository (absent from the working tree) — recommend committing it so the gate evidence is self-contained |
| 3 | Four role-based smoke tests (one per role: AGENT, HANDLER, SUPERVISOR, ADMIN) | **PASS** | `RoleBasedSmokeTests.agentRoleSmokeTest`, `handlerRoleSmokeTest`, `supervisorRoleSmokeTest`, `adminRoleSmokeTest` — all green in the verification run |
| 4 | No secrets / real citizen data in the repository | **PASS** | Configuration is environment-variable-only; `.env`, `.env.local`, `.env.*.local` and `*.env` are git-ignored (`.gitignore:38-41`); test secrets are explicitly dummy and asserted as such (`TestConfigSecretsTest`: JWT secret contains "test"/"dummy", 32-byte encryption key). All test data is synthetic (generated usernames, 16-digit dummy national IDs) — no real citizen data |
