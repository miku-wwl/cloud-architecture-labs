.PHONY: localstack infra apps up down reset test smoke demo-healthy demo-error demo-slow verify

localstack:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/wait-localstack.ps1

infra: localstack
	terraform -chdir=infra init -input=false
	terraform -chdir=infra fmt -check
	terraform -chdir=infra apply -auto-approve -input=false -var='localstack_endpoint=http://localhost:4566'

apps:
	./mvnw package -DskipTests
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-apps.ps1

up: infra apps

down:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop-apps.ps1

reset:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-demo.ps1

test:
	./mvnw test

smoke:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1

demo-healthy:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1 -Scenario HEALTHY

demo-error:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1 -Scenario ERROR

demo-slow:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/demo.ps1 -Scenario SLOW

verify: test
	terraform -chdir=infra fmt -check
	terraform -chdir=infra validate
	docker compose config
