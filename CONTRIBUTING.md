# Contributing

Thank you for your interest. This is a small project, so the rules are short.

## Before you start

- For a bug, look through the open issues first, then open one with the steps to reproduce it.
- For a big change or a new feature, open an issue and describe it before you write the code, so that the work is not wasted.
- By sending a pull request you agree that your contribution is licensed under the same [license](LICENSE) as the project.

## Setup

You need Windows and JDK 21. See [Build from source](README.md#build-from-source) for the commands.

## Rules for a change

- Work in a branch named `feature/<short-name>` or `fix/<short-name>`, not in `master`.
- Keep a change to one thing. A commit that does one thing is easier to review and to take back.
- Commit messages are in English, short and in the imperative: `Fix the order of widgets sent to the back`.
- Run `./gradlew ktlintCheckAll` before you push. CI runs it and fails on any style error.
- Run the tests, `./gradlew :core:test :app:test :layout-editor:test`. Add a test when you fix a bug or add a rule that can be tested.
- Texts that the user sees go to the string resources in `core/src/main/composeResources` (`values` for English, `values-ru` for Russian), not into the code.
- The program is for Windows only; do not add code for other systems.
- Do not commit logs, crash dumps, settings files or paths from your own computer.

## Pull request

Describe what changed and why, and how you checked it. A change to the interface is easier to review with a screenshot.
