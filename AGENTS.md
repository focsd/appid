# Repository instructions for coding agents

These instructions apply to the entire AppId repository.

## Complete feature workflow

When the user asks to implement or change a feature, carry the work through to a
verified result. Before declaring the feature complete, run the checks that are
appropriate for the change. For normal Android application changes, this should
include at least:

```sh
./gradlew lintDebug assembleDebug
git diff --check
```

When Termux assets are changed, also run:

```sh
./scripts/check_termux_scripts.sh
```

After the requested feature is complete and all applicable checks pass, stage
the coherent feature change set and create a Git commit with a concise,
descriptive message. The commit is part of completing the feature; do not leave
successfully verified feature work unstaged unless the user explicitly asks not
to commit it.

Do not commit a failed, incomplete, or knowingly broken implementation. If a
check cannot run, explain why and commit only when the available evidence still
supports calling the feature complete. Never include unrelated pre-existing
user changes merely to obtain a clean working tree. Inspect the diff, preserve
the user's work, and stage only files that belong to the requested feature.

If installation or device testing is part of the request and a compatible
device is connected, install and test the debug APK before committing. Device
availability is not required for changes that can be fully verified locally.

## Source conventions

`app/src/main/assets/` is the canonical source for Termux scripts. After editing
those scripts, synchronize their checked-in `termux/` copies with the repository
helper and verify parity before committing.

Use `apply_patch` for source edits. Keep comments focused on decisions,
constraints, compatibility behavior, and non-obvious contracts rather than
restating individual lines of code.
