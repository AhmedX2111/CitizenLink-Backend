# US-48 — Release Regression Smoke Checklist (Manual)

- **Purpose:** Manual, repeatable smoke pass over the most important flows for each
  role, run against **staging** before a release is approved. The automated regression
  suite (`RoleBasedSmokeTests` + the per-area integration tests) runs in CI and
  blocks the release while red; this checklist is the human confirmation of UI-level
  behavior, masking, and Arabic/English parity that the API tests cannot fully assert.
- **How to use:** one pass per role, top to bottom. Tick each box. Any **fail** blocks
  the release (see *Release-approval rule* at the bottom). Record date, environment,
  and tester below.
- **Companion evidence:** `docs/US-43-US-45-acceptance.md` (automated AC trace) and the
  two repo CI pipelines (`.github/workflows/ci.yml`) that run the automated suite.

Run record

| Field | Value |
|-------|-------|
| Environment | `_TBD_` (staging URL) |
| Build / tag | `_TBD_` |
| Date | `_TBD_` |
| Tester | `_TBD_` |
| CI backend run (commit SHA) | `_TBD_` (must be green) |
| CI frontend run (commit SHA) | `_TBD_` (must be green) |

---

## Shared preconditions (once)

- [ ] Staging backend reachable; landing page (`/`) loads with no console errors.
- [ ] Staging frontend reachable; login page (`/login`) loads.
- [ ] All four role accounts exist and are active (AGENT, HANDLER, SUPERVISOR, ADMIN).
- [ ] At least one real citizen record and one case exist per role fixture (see per-role notes).
- [ ] Language toggle available; test the two language-dependent checks (marked **AR/EN**) in both Arabic and English.

---

## AGENT journey

Fixture: an AGENT who can create a case for a known citizen.

- [ ] Login as AGENT → lands on Dashboard.
- [ ] Open a citizen via Call Center search; the citizen 360 screen opens.
- [ ] Search results show **masked** national ID and phone number (never full values in the list).
- [ ] Create a case from the citizen (New Case) → case detail opens, case ID shown.
- [ ] Add an internal note and upload an attachment to that case; both appear on the case detail.
- [ ] Open **another AGENT's** case by its ID in the URL → **404** (not 403); the case is invisible.
- [ ] **NEGATIVE — workflow:** the AGENT sees **no** workflow action buttons (Start/Assign/etc.) on their own case, and triggering a transition is refused (**403**).
- [ ] **NEGATIVE — admin surface:** navigating to User Administration (/app/users) → **403 Forbidden** page.

## HANDLER journey

Fixture: a HANDLER with one assigned case in the inbox and one unassigned case that exists.

- [ ] Login as HANDLER → Inbox (`/inbox`) opens with the handler's **assigned** cases only.
- [ ] Quick filters present: Overdue, Due today, Urgent, Awaiting information, Newly assigned; each shows a count badge.
- [ ] Sorting selector present (SMART default, due-date, priority, newest); re-sorting reorders rows.
- [ ] Filters + sort + page are preserved in the URL; a **full browser refresh** restores the same view.
- [ ] **AR/EN:** repeat the inbox with Arabic active — layout mirrors, labels translate, filters still work.
- [ ] Open a row → lands on the case detail.
- [ ] Start the assigned case (workflow action) → status becomes **IN_PROGRESS**.
- [ ] **NEGATIVE — visibility:** the unassigned case does **not** appear in the handler's inbox; opening it by ID → **404**.
- [ ] **NEGATIVE — note edit:** the handler cannot edit another author's note (refused, **403**).
- [ ] **NEGATIVE — admin surface:** navigating to /app/users → **403 Forbidden**.

## SUPERVISOR journey

Fixture: a SUPERVISOR with an agent-created case and one or more active HANDLERs.

- [ ] Login as SUPERVISOR → Dashboard loads KPIs and the cases-by-status chart.
- [ ] Open an AGENT-created case → **visible** (cross-visibility) with full detail.
- [ ] Assign / reassign that case to an active HANDLER via the case detail workflow action → status **ASSIGNED**.
- [ ] The handlers directory is available for the reassign picker (active HANDLERs only).
- [ ] Volume report (`/reports`) loads for the selected date range.
- [ ] Reference data (`/admin/reference-data`) is reachable (read/manage).
- [ ] **NEGATIVE — admin surface:** navigating to full User Administration (/app/users) → **403 Forbidden** (supervisor is not admin).

## ADMIN journey

Fixture: an ADMIN; a non-admin user (e.g. AGENT) that can be deactivated/reactivated.

- [ ] Login as ADMIN → User Administration (/app/users) lists users (paginated).
- [ ] Open any case (any owner) → **visible** with full detail and PII per the permission matrix.
- [ ] Deactivate the non-admin user → their next login is refused (**401 ACCOUNT_DISABLED**).
- [ ] Reactivate the same user → login succeeds again.
- [ ] **NEGATIVE — self lock-out:** attempt to deactivate the ADMIN's **own** account → refused (**400 BAD_REQUEST**), account still active; the admin can still perform admin actions afterwards.
- [ ] **NEGATIVE — last-admin guard:** attempt to deactivate the **last remaining active ADMIN** → refused (**400 BAD_REQUEST**).
- [ ] **NEGATIVE — auth required:** an unauthenticated call to /api/v1/users → **401** with the standard `{code,message}` envelope (`UNAUTHORIZED`).

---

## Cross-cutting security & i18n checks

- [ ] **Error envelope:** every refused action above returns the standard `{code, message, details}` body — never a stack trace or a Java exception class name.
- [ ] **Masking parity:** masked national ID/phone in search results are identical in Arabic and English.
- [ ] **AR/EN:** the login, dashboard, inbox, and case-detail screens render correctly mirrored in Arabic (right-to-left) and in English; no truncated or un-translated keys.
- [ ] **No PII leakage:** no full sensitive identifier appears in a list/search context for any role not approved in the permission matrix; CSV exports respect the same rule.

---

## Release-approval rule (US-48 AC5)

A release (or merge to `master`) **cannot be approved** while any of the following is true:

1. The backend CI check (`.github/workflows/ci.yml` in the backend repo) is red.
2. The frontend CI check (`.github/workflows/ci.yml` in the frontend repo) is red.
3. Any **Critical** regression test in the automated suite is failing.
4. Any item in this checklist for the release's affected roles is an unresolved **fail**.

**Enforcement:** enable GitHub **branch protection → required status checks** for the two
`ci` checks on the `master` branch of each repo, so a red pipeline cannot be merged.
The CI pipelines upload their test reports as artifacts (backend: `target/surefire-reports`;
frontend: the vitest report) so the result is stored with the pull request (US-48 AC4).