.PHONY: localstack build infra seed up down destroy test smoke security-test mcp-test verify demo

localstack:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/wait-localstack.ps1

build:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1

infra: localstack build
	terraform -chdir=infra/terraform init -input=false
	terraform -chdir=infra/terraform apply -auto-approve -input=false -parallelism=1

seed: infra
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-local-data.ps1

up: seed

down:
	@echo "LocalStack 由外部环境管理；本实验不会停止或创建容器。"

destroy:
	terraform -chdir=infra/terraform destroy -auto-approve

test:
	mvn -q test

smoke:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1

security-test:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/security-test.ps1

mcp-test:
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/mcp-test.ps1

verify: test
	terraform -chdir=infra/terraform fmt -check
	terraform -chdir=infra/terraform validate
	docker compose config
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/assert-local-only.ps1
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/security-test.ps1
	powershell -NoProfile -ExecutionPolicy Bypass -File scripts/mcp-test.ps1

demo: mcp-test
