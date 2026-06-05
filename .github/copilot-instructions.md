# Copilot custom instructions for powpeg-node

## Repository summary

powpeg-node is the specialized Rootstock PowPeg signatory node. It extends/depends on RSKj and interacts with both Rootstock and Bitcoin. PowPeg signatories use it to interact with the Bridge contract and broadcast peg-out transactions to Bitcoin.

This is a single-module Gradle Java repository. The Gradle root project is federate-node; the application main class is co.rsk.federate.FederateRunner. Source compatibility is Java 17. License: MIT.

## Security-critical code

powpeg-node participates in Rootstock's PowPeg infrastructure and handles Bitcoin transactions, signatures, and federation operations.

When working in security-sensitive areas:

* Security and correctness are more important than elegance.
* Preserve existing behavior unless the task explicitly requires a behavioral change.
* Do not refactor signing, peg-out, federation, key-management, transaction-construction, or consensus-related code unless required by the task.
* Prefer proven, explicit code over clever abstractions in consensus or money-related paths.
* Prioritize behavior preservation over stylistic improvements.
* Refactor security-sensitive code only when there is a clear benefit and adequate test coverage.
* Be conservative when changing validation, serialization, cryptographic, or transaction-building logic.

## PR review priorities

Reviewability is the top priority.

* Keep changes scoped to the stated motivation. Flag broad, unrelated, or hard-to-review diffs.
* Do not mix formatting-only changes with behavioral changes in the same commit.
* Treat changes to signing, peg-out, Bitcoin connectivity, Bridge interaction, federation logic, key handling, and HSM integration as security-sensitive.
* New behavior requires tests, including edge cases and error paths.
* Avoid flaky tests: no Thread.sleep synchronization, real network calls without stubs, real-clock assumptions, fragile filesystem assumptions, or hidden dependency on test ordering.
* Do not introduce secrets, private keys, real signatory keys, node keys, RPC credentials, or production config values into source, tests, docs, or logs.
* Be conservative with logging around signatures, private/public key material, raw transactions, Bridge calls, and Bitcoin/Rootstock RPC data.
* New or upgraded dependencies need extra scrutiny because this node participates in critical PowPeg operations.

## Rootstock coding principles

Follow the coding principles documented in docs/coding-principles.md.

Key rules:

* Code is read more often than written. Optimize for readability and maintainability.
* Use intention-revealing names.
* Include units in monetary and time-related names, such as amountInSatoshis, amountInWei, amountInRBTC, and timeoutMillis.
* Never rely on implicit monetary units.
* Prefer focused functions and classes with clear responsibilities.
* Eliminate duplication when practical.
* Prefer self-explanatory code over explanatory comments.
* Tests should be readable, independent, and cover boundary/error cases.
* Refactors must improve clarity, maintainability, or correctness. Do not introduce abstractions, indirection, or code movement without a clear benefit.

## Build and test commands

Use the in-repo Gradle wrapper. Do not use a system Gradle.

If gradle/wrapper/gradle-wrapper.jar is missing, run:

    ./configure.sh

Common commands:

    ./gradlew clean build
    ./gradlew build -x test
    ./gradlew test
    ./gradlew fatJar

The application entry point is:

`co.rsk.federate.FederateRunner`

Example local run shape:

    java -cp build/libs/<jar-name>.jar -Drsk.conf.file=<path-to-node.conf> co.rsk.federate.FederateRunner --regtest --reset

## RSKj dependency workflow

powpeg-node depends on co.rsk:rskj-core. For local development against a local RSKj checkout, use DONT-COMMIT-settings.gradle; never commit this file.

The settings file may include a local composite build that substitutes co.rsk:rskj-core when the requested version ends in SNAPSHOT or RC.

## CI gates a reviewer must predict

Workflows live in .github/workflows/.

PR-relevant checks include:

* build_and_test.yml
    * verifies signed/checksummed files using SHA256SUMS.asc;
    * checks out a matching RSKj branch when available, otherwise falls back to master;
    * sets DONT-COMMIT-settings.gradle to use the local RSKj checkout;
    * runs ./gradlew --no-daemon --stacktrace clean build -x test;
    * runs ./gradlew --no-daemon --stacktrace test;
    * runs SonarQube after tests, skipping fork PR analysis.
* rit.yml
    * runs Rootstock Integration Tests on PRs targeting master or *-rc;
    * supports PR-description branch overrides using backticked rskj:<branch>, fed:<branch>, and rit:<branch>.
* codeql.yml, dependency-review.yml, reproducible.yml, build-push-docker.yml, and scorecard.yml may also run depending on the changed files, event, and branch.

## Project layout

Important areas:

* src/main/java/co/rsk/federate/ — PowPeg/federator application logic.
* src/main/java/co/rsk/federate/bitcoin/ — Bitcoin connectivity and wallet/peer interaction.
* src/main/java/co/rsk/federate/signing/ — signing abstractions and implementations. Treat as highly security-sensitive.
* src/main/resources/ — runtime resources, version metadata, and configuration-related files.
* src/test/java/ — unit tests.

## Sensitive areas

Apply extra scrutiny to changes involving:

* BTC, RSK, or MST signer configuration.
* key-file handling, HSM handling, signing, signature serialization, and transaction signing.
* peg-out transaction construction, validation, broadcasting, or retry behavior.
* Bitcoin peer configuration and P2P/RPC assumptions.
* Bridge contract interaction and RSKj integration points.
* thread lifecycle, shutdown behavior, scheduling, retries, and network error handling.
* dependency versions for rskj-core, bitcoinj, Jackson, logging, and cryptographic/signing libraries.
* Docker image behavior, runtime paths, and mounted configuration.

## Configuration and secrets

Never hardcode production values.

Local documentation may show regtest examples, including sample keys and RPC credentials. Treat those as examples only. Do not reuse them in production-facing code, tests, or defaults.

When changing config parsing or defaults, preserve backward compatibility unless the PR explicitly documents the migration path.

## Java style guidance

* Keep consistency with the surrounding codebase whenever possible.
* Avoid unnecessary whitespace. Do not leave multiple blank lines where one is enough.
* Leave exactly one trailing newline at the end of every file.
* Remove unused variables, methods, imports, classes, and dependencies.
* Do not assign a constant to a local variable unless it improves readability or avoids repeated expensive access.
* Prefer functional style where it improves readability, but do not perform large-scale functional refactors in a single PR.

Use standard Java naming:

* packages: lowercase
* classes: UpperCamelCase
* methods and fields: lowerCamelCase
* constants: CONSTANT_CASE

Prefer constructor injection and immutable fields for new code when practical. Validate required constructor arguments. Avoid nullable returns where Optional<T> would make behavior clearer.

## Testing guidance

Unit-test new logic close to the changed class. Mock/stub Bitcoin, Rootstock, filesystem, clock, and network boundaries unless the test is explicitly an integration test. Avoid mocks for any other business logic class that can be instantied instead of mocked.

Tests for signing and peg-out behavior should assert failure paths, malformed input, and edge cases, not only the happy path.
