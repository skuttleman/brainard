.PHONY: clean build-sass build-js uberjar test

clean:
	@echo cleaning...
	rm -rf resources/public/css/*
	rm -rf resources/public/js/*
	rm -rf target/classes
	rm -rf target/cljs-test

build-sass:
	@echo building scss
	sass --style=compressed resources/scss/main.scss resources/public/css/main.css

build-js:
	@echo building cljs
	npm install
	clojure -A:shadow -M -m shadow.cljs.devtools.cli compile ui

build-test-cljs:
	@echo building clojurescript tests
	npm install
	clojure -A:shadow:test -M -m shadow.cljs.devtools.cli compile test

uberjar: clean build-sass build-js
	clojure -T:build uber

test: clean build-sass build-js build-test-cljs
	clojure -M:test -m brainard.test.runner
	HEADLESS=true clojure -M:test -m kaocha.runner --focus-meta :focus
