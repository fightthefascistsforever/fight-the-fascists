.PHONY: dev backend frontend test build migrate

dev: backend frontend

backend:
	cd backend && mvn spring-boot:run

frontend:
	cd frontend && npm run dev

test:
	cd backend && mvn test
	cd frontend && npm run build

build:
	cd backend && mvn -q package -DskipTests
	cd frontend && npm run build

migrate:
	cd backend && mvn flyway:migrate

docker-up:
	docker compose up --build
