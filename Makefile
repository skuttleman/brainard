SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.PHONY: help check-deps run install clean build build-sass build-cljs build-test uberjar test ci
.DEFAULT_GOAL := help

# Tools (can be overridden in the environment)
FOREMAN ?= foreman
NPM ?= npm
CLJ ?= clojure
NPX ?= npx
SASS ?= sass

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort \
	| awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

check-deps: ## Verify required CLI tools are available (with install hints)
	@echo "Checking required tools..."
	@missing=""
	@if ! command -v $(CLJ) >/dev/null 2>&1; then missing="$$missing $(CLJ)"; fi
	@if ! command -v $(FOREMAN) >/dev/null 2>&1 && ! command -v $(NPX) >/dev/null 2>&1; then missing="$$missing $(FOREMAN) (or npx)"; fi
	@if ! command -v $(SASS) >/dev/null 2>&1 && ! command -v $(NPX) >/dev/null 2>&1; then missing="$$missing $(SASS) (or npx)"; fi
	@if [ -n "$$missing" ]; then \
		echo "Missing required tools:$$missing"; \
		echo ""; \
		echo "Install hints:"; \
		echo "  - Node & npm: https://nodejs.org/"; \
		echo "  - clojure: https://clojure.org/guides/getting_started"; \
		echo "  - foreman: gem install foreman OR 'npx foreman' (if you have npm)"; \
		echo "  - sass: npm i -g sass OR 'npx sass'"; \
		echo ""; \
		exit 1; \
	fi
	@echo "All required tools available (or npx fallbacks present)."

run: check-deps ## Run the app (uses foreman or npx foreman)
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
	@rm -rf resources/public/css/* resources/public/js/* target/classes target/cljs-test || true
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

uberjar: clean build ## Build standalone uberjar
	@echo "building uberjar"
	@$(CLJ) -T:build uber

build-test: install ## Build test CLJS
	@echo "building cljs tests..."
	@$(CLJ) -A:shadow:test -M -m shadow.cljs.devtools.cli compile test

test: check-deps clean build build-test ## Run CLJS, server, and UI tests (requires clojure)
	@echo "running CLJS tests..."
	@$(CLJ) -M:test -m brainard.test.runner
	@echo "running kaocha tests..."
	@HEADLESS=true SCREENSHOT=true $(CLJ) -M:test -m kaocha.runner --focus-meta :focus
