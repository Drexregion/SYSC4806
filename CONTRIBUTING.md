# Contributing

How we work on this project. The marking scheme puts 60% on process, so none of
this is optional.

## Branches

Never commit to `main` directly. Branch protection will stop you anyway.

Branch off `main` and name it for what it is:

| Prefix | For |
|---|---|
| `feat/` | New behaviour |
| `fix/` | A bug |
| `ci/` | Build, workflows, deployment |
| `docs/` | README, diagrams, this file |

For example `feat/create-shop`.

## Pull requests

1. One issue per pull request. Small ones get reviewed; large ones sit.
2. Put `Closes #12` in the body, so the issue closes and its board card moves
   when the pull request merges.
3. CI has to be green.
4. Two teammates have to approve. Not one, and never yourself.
5. Squash merge, then delete the branch.
6. Check the Azure deployment afterwards.

A pull request open for two days is a dead pull request. Chase your reviewers.

## Reviews

Review the same day. A stalled review blocks someone else, and the reviews
themselves are part of the mark.

Say something real. "LGTM" on a 200-line diff helps nobody, and the TA can see
that it was the only comment.

On top of the two approvals, each area has an owner who should be one of them:

| Area | Reviewer |
|---|---|
| Models | Iheanyi |
| Controllers | Rotimi |
| Templates and views | Idara |
| Tests, on every pull request | Bomi |
| Build, CI and deployment | Divine |

## Issues and the board

Every piece of work has an issue. Move your card to **In Progress** before you
start, not after you finish.

Open your own issues when you find something. Do not wait to be handed work.

## Weekly scrum

Every Monday one of us opens a `Weekly scrum - [date]` issue and everybody
comments on it. Use the Weekly scrum issue template.

## Deploying

Every merge to `main` deploys to Azure. We aim for 2-3 deploys a week, and
**everybody deploys at least once** - the rubric names deployment frequency per
person, not per team.

## Running it locally

```
mvn spring-boot:run
```

Then open http://localhost:8080. The database is in-memory H2 and resets on
every restart; browse it at `/h2-console`.

Set your IntelliJ project SDK to **Java 21**, matching `pom.xml`, CI and Azure.
A different JDK locally means failures CI will not reproduce.
