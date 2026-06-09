SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.PHONY: help check-deps run install clean build-sass build-cljs build build-test uberjar test lint coverage
.DEFAULT_GOAL := help

# Tools (can be overridden in the environment)
FOREMAN ?= foreman
NPM ?= npm
CLJ ?= clojure
NPX ?= npx
SASS ?= sass
LCOV ?= lcov
GENHTML ?= genhtml

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort \
	| awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

check-deps: ## Verify required CLI tools are available (with install hints)
	@echo "Checking required tools..."; \
	missing=""; \
	if ! command -v $(CLJ) >/dev/null 2>&1; then missing="$$missing $(CLJ)"; fi; \
	if ! command -v $(FOREMAN) >/dev/null 2>&1 && ! command -v $(NPX) >/dev/null 2>&1; then missing="$$missing $(FOREMAN) (or npx)"; fi; \
	if ! command -v $(SASS) >/dev/null 2>&1 && ! command -v $(NPX) >/dev/null 2>&1; then missing="$$missing $(SASS) (or npx)"; fi; \
	if ! command -v $(LCOV) >/dev/null 2>&1; then missing="$$missing $(LCOV)"; fi; \
	if ! command -v $(GENHTML) >/dev/null 2>&1; then missing="$$missing $(GENHTML)"; fi; \
	if [ -n "$$missing" ]; then \
		echo "Missing required tools:$$missing"; \
		echo ""; \
		echo "Install hints:"; \
		echo "  - Node & npm: https://nodejs.org/"; \
		echo "  - clojure: https://clojure.org/guides/getting_started"; \
		echo "  - foreman: gem install foreman OR 'npx foreman' (if you have npm)"; \
		echo "  - sass: npm i -g sass OR 'npx sass'"; \
		echo "  - lcov (genhtml): apt-get install lcov  OR brew install lcov"; \
		echo ""; \
		exit 1; \
	fi; \
	echo "All required tools available (or npx fallbacks present)."

run: check-deps ## Run the app (uses foreman or npx foreman)
	@killall -15 node java clj sass || true;
	@echo "Starting app..."
	@if command -v $(FOREMAN) >/dev/null 2>&1; then \
		$(FOREMAN) start; \
	else \
		$(NPX) foreman start; \
	fi

install: ## Install JS dependencies (uses npm)
	@echo "Installing JS dependencies..."
	@if command -v $(NPM) >/dev/null 2>&1; then $(NPM) install; \
	else \
		echo "No npm found; please install Node.js and npm (https://nodejs.org/)"; exit 1; \
	fi

clean: ## Remove generated assets and build dirs
	@echo "cleaning..."
	@rm -rf resources/public/css/* resources/public/js/* target/* || true
	@echo "clean complete"

build-sass: ## Compile SCSS to resources/public/css/main.css
	@echo "building scss..."
	@if command -v $(SASS) >/dev/null 2>&1; then $(SASS) --style=compressed resources/scss/main.scss resources/public/css/main.css; \
	elif command -v $(NPX) >/dev/null 2>&1; then $(NPX) sass --style=compressed resources/scss/main.scss resources/public/css/main.css; \
	else echo "sass not found (needed to compile SCSS). Install 'sass' or ensure 'npx' is available."; exit 1; fi

build-cljs: ## Build ClojureScript via shadow-cljs
	@echo "building cljs..."
	@$(CLJ) -A:shadow -M -m shadow.cljs.devtools.cli compile ui

build: install build-sass build-cljs ## install + build-sass + build-cljs
	@echo "build complete"

build-test: install ## Build test CLJS
	@echo "building cljs tests..."
	@$(CLJ) -A:shadow:test -M -m shadow.cljs.devtools.cli compile test ui-test

uberjar: clean build ## Build standalone uberjar
	@echo "building uberjar"
	@$(CLJ) -T:build uber

test: check-deps lint clean build-sass build-test ## Run CLJS, server, and UI tests (requires clojure)
	@if [ "$(INCLUDE_CLJS_TESTS)" = "true" ]; then \
		echo "running CLJS tests..."; \
		$(CLJ) -M:test -m brainard.test.runner; \
	fi
	@echo "running kaocha tests..."
	@HEADLESS=true SCREENSHOT=true $(CLJ) -M:test -m kaocha.runner

lint: ## Check codebase for linting errors
	@$(CLJ) -M:lint

coverage: clean check-deps build-sass build-test ## Run unit/integration/UI test suites with coverage instrumentation and merge results
	@echo "Running coverage for unit and integration suites..."
	@$(CLJ) -M:test -m kaocha.runner --plugin cloverage --cov-output target/coverage/unit --lcov \
		--cov-ns-exclude-regex 'brainard\..*\.(multipart-params|routes\.ui)' \
		--cov-ns-exclude-regex 'brainard\..*\.views.*' :api :infra
	@$(CLJ) -M:test -m kaocha.runner --plugin cloverage --cov-output target/coverage/integration --lcov \
		--cov-ns-exclude-regex 'brainard\..*\.(multipart-params|routes\.ui)' \
		--cov-ns-exclude-regex 'brainard\..*\.views.*' :integration
	@echo "Building and instrumenting CLJS with source maps for coverage..."
	@$(CLJ) -A:shadow:test:tools -M -m shadow.cljs.devtools.cli compile ui-cov
	@echo "Running UI coverage..."
	@HEADLESS=true JS_COVERAGE=true $(CLJ) -M:test -m kaocha.runner \
		--plugin cloverage --cov-output target/coverage/driver --lcov \
		--cov-ns-regex 'brainard\..*\.db' \
		--cov-ns-regex 'brainard\.*\.api\..*' \
		--cov-ns-regex 'brainard\.*\.system\..*' \
		--cov-ns-regex 'brainard\.infra\.store\..*' \
		--cov-ns-regex 'brainard\..*\.(multipart-params|routes\.ui)' :ui-core :ui-notes
	@echo "Generating JS coverage report..."
	@node tools/nyc-report.js
	@$(CLJ) -M:tools -m brainard.tools.coverage.normalize-js
	@echo "Merging lcov files..."
	@mkdir -p target/coverage/merged
	@$(CLJ) -M:tools -m brainard.tools.coverage.merge-lcov target/coverage target/coverage/merged
