.PHONY: compile test preflight build up demo verify down check research

compile:
	mkdir -p build
	javac --release 17 -d build app/LabServer.java tools/RuntimeProbe.java

test:
	bash tests/smoke.sh

preflight:
	bash scripts/preflight.sh

build:
	docker compose build --pull

up:
	docker compose up --detach --build

demo:
	bash scripts/demo.sh

verify:
	bash scripts/verify.sh

down:
	docker compose down --remove-orphans

check:
	bash scripts/check.sh

research:
	python3 tools/crawl_public_repos.py --limit 100
