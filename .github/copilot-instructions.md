# Copilot instructions — Brainard

A concise, project-specific reference for Copilot sessions working on Brainard. Covers build/test/lint commands, high-level architecture, and repo-specific conventions that are important for edits to tests, coverage, and CI.

---

## General Instructions

- never `git commit` anything

---

## Commands

### Run dev app
```bash
make run
```

### Run tests
```bash
# All server + UI tests (CLJS unit tests opt-in):
INCLUDE_CLJS_TESTS=true make test

# Run a specific kaocha suite:
clojure -M:test -m kaocha.runner :api               # API unit tests
clojure -M:test -m kaocha.runner :infra             # Infra unit tests
clojure -M:test -m kaocha.runner :integration

# Webdriver tests require frontend build
make build-cljs build-sass
clojure -M:test -m kaocha.runner :ui-core :ui-notes # Etaoin WebDriver tests

# Focus a single test (uses :focus metadata or --focus flag):
clojure -M:test -m kaocha.runner :api --focus brainard.api.validations-test/some-test

# ClojureScript unit tests (requires build-test first):
make build-test
clojure -M:test -m brainard.test.runner
```

### Lint
```bash
make lint
# or directly:
clojure -M:lint
```

### Coverage
```bash
make coverage
open target/coverage/merged/index.html
```

### Build uberjar
```bash
make uberjar
```

---

## Architecture

### Module Structure

```
modules/api/   — Pure domain logic (portable .cljc). Malli specs, storage protocols/multimethods, domain APIs
modules/infra/ — Infrastructure: Datomic DB adapters, Ring HTTP routes, ClojureScript UI views/components, defacto store
src/brainard/  — App entry points (app.cljs for frontend, main.clj for backend)
test/          — Integration tests (in-memory Datomic) and UI tests (Etaoin/Chromedriver)
tools/         — Build helpers, coverage normalization/merge tools
resources/duct/— Duct/Integrant system configs (.edn)
```

### Backend
- **Immutant** HTTP server (Ring-compatible)
- **Datomic Local** for persistence
- **Duct/Integrant** for dependency injection; configs in `resources/duct/*.edn` and `dev/resources/duct/dev.edn`
- **bidi** for URL routing

### Frontend
- **ClojureScript** compiled via **shadow-cljs** (`:dev`, `:ui`, `:ui-test`, `:ui-cov` builds)
- **`defacto`** (transitive via the local `whet` dep) for state management
- **`whet`** (local path `../../../whet`) provides routing, HTTP, navigation utilities
- **React 17** as the rendering target

---

## Key Conventions

### Storage Interface Pattern

Storage is abstracted in `modules/api/src/brainard/api/storage/`:
- Protocols: `IRead` and `IWrite` (`:extend-via-metadata true`)
- `->input` multimethod: converts domain params (keyed by `::storage/type`) to storage-layer operations
- Domain DB modules extend `->input` for their specific operations, e.g.:

```clojure
;; brainard.notes.infra.db
(defmethod istorage/->input ::api.notes/create! [note] ...)
(defmethod istorage/->input ::api.notes/get-notes [params] ...)
```

- All reads go through `storage/query`, all writes through `storage/execute!` (from `brainard.api.storage.core`)

### API Dispatch

`brainard.api.core` dispatches on keyword API types via `invoke-api*` multimethod:

```clojure
(defmethod invoke-api* :api.notes/create! [_ apis note] ...)
```

`invoke-api` (the public fn) validates input against Malli specs before dispatch and optionally validates output after.

### HTTP Routing

Route tokens are keywords (`:routes.api/notes`, `:routes.ui/home`). A Clojure hierarchy derives specializations from parent classes (`:routes/api`, `:routes/ui`). Two multimethods dispatch on `[request-method route-token]`:
- `iroutes/handler` — returns a Ring response
- `iroutes/req->input` — coerces the request into API input

Route-to-API-handler mappings live in `brainard.infra.routes.interfaces/route->handler`.

### Frontend Store (defacto)

Three extension points, all via `defmethod`:

| multimethod | purpose |
|---|---|
| `defacto/command-handler` | Side effects; receives `store`, `command`, and `emit-cb` |
| `defacto/event-reducer` | Pure `[db event] → db` state reducer |
| `defacto/query-responder` | Derived state; pure `[db query] → value` |

API resource requests are defined via `res/->request-spec` multimethods in `brainard.infra.store.specs`. Resource lifecycle uses `res/ensure!` + `res/?:resource` subscription. Use `store/res-sub`, `store/form-sub`, `store/form+-sub` helpers for common patterns.

### Malli Specs

All domain specs live in `**/api/specs.cljc` files. Input/output validation maps for the API layer are in `brainard.api.validations`. Use `valid/->validator` for reusable validators and `valid/select-spec-keys` to filter maps to spec-defined keys.

### Naming

- Namespaces: `brainard.<domain>.<layer>.<concern>` — e.g., `brainard.notes.api.core`, `brainard.notes.infra.db`
- Route tokens: `:routes.api/<resource>` and `:routes.ui/<page>`
- API action keywords: `:api.<domain>/<verb>!` (mutations) and `:api.<domain>/<verb>` (reads)
- Storage dispatch keys: `::api.<domain>/<operation>` (namespaced to the domain's API ns)
- Test namespaces end in `-test`
- `.cljc` files use reader conditionals (`#?(:clj … :cljs …)`) for Clj/Cljs portability

### Test Harness

**Integration tests** use `tsys/with-app` macro (spins up a Duct system with an in-memory Datomic db):
```clojure
(tsys/with-app [{::b/keys [storage apis]} nil]
  ...)
```

**UI/WebDriver tests** use `usys/with-webdriver` (full system + Etaoin Chrome driver):
```clojure
(usys/with-webdriver [driver base-url {db-sym "seed/base"}]
  ...)
```

Seed data lives in `test/resources/fixtures/seed/*.edn` and is transacted via `transact-multi!`.

Set `HEADLESS=true` to run Chrome headlessly; `SCREENSHOT=true` to screenshot on failure; `JS_COVERAGE=true` for JS coverage collection.

### Focus Metadata

Add `^:focus` meta to a `deftest` to run only that test when using the `:kaocha.filter/focus-meta [:focus]` setting (already configured in `tests.edn`):

```clojure
(deftest ^:focus my-test ...)
```
