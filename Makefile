.PHONY: clean build-sass build-js uberjar test

run:
	foreman start

install:
	npm install

clean:
	@echo cleaning...
	rm -rf resources/public/css/*
	rm -rf resources/public/js/*
	rm -rf target/classes
	rm -rf target/cljs-test

build: install
	@echo building scss
	sass --style=compressed resources/scss/main.scss resources/public/css/main.css
	@echo building cljs
	clojure -A:shadow -M -m shadow.cljs.devtools.cli compile ui

uberjar: clean build
	clojure -T:build uber

build-test: install
	clojure -A:shadow:test -M -m shadow.cljs.devtools.cli compile test

test: clean build build-test
	clojure -M:test -m brainard.test.runner
	HEADLESS=true SCREENSHOT=true clojure -M:test -m kaocha.runner --focus-meta :focus
