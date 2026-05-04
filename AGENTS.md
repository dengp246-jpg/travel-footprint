# Agent Instructions

## Repository overview

This repository contains a Java 17 Spring Boot course-design project named `旅迹 Travel Footprint`.

## Working rules

- Prefer small, reviewable changes.
- Document new commands and setup steps in `README.md`.
- Keep secrets out of the repository; use `.env` for local values.
- Preserve the existing Spring Boot + Thymeleaf stack unless a task explicitly asks for a migration.

## Repo-specific guidance

- Main package: `com.example.travelfootprint`
- UI stack: Thymeleaf templates in `src/main/resources/templates`
- Static assets: `src/main/resources/static`
- Persistence: H2 file database configured in `src/main/resources/application.properties`
- Uploaded files are stored in local `uploads/`
- Local database files are stored in local `data/`

## Commands

- Run: `mvn spring-boot:run`
- Test: `mvn test`

## When changing the stack

- Keep a clear run command.
- Keep a clear test command.
- Add stack-specific ignore rules if needed.
- Update this file with the new repo-specific guidance.
