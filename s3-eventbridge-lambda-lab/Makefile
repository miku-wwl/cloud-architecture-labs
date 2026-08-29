.PHONY: start build test deploy verify destroy reset all

start:
	pwsh -NoProfile -File scripts/check-localstack.ps1

build:
	bash scripts/build.sh

test:
	bash scripts/test.sh

deploy:
	bash scripts/deploy.sh

verify:
	bash scripts/verify.sh

destroy:
	bash scripts/destroy.sh

reset:
	bash scripts/reset.sh

all:
	bash scripts/verify.sh
