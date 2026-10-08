# Gemini reviewer on pull requests

The autonomous reviewer is `google-github-actions/run-gemini-cli` on Vertex AI. It holds no
long-lived credentials: each run exchanges the GitHub OIDC token for a workload identity token, then
impersonates the `gemini-ci` service account in `project-a6cd5f66-cb83-4cbd-a21`.

| Workflow | Fires on | What it does |
| --- | --- | --- |
| `gemini-dispatch.yml` | PR opened, issue opened/reopened, `@gemini-cli` comment | Routes the event to one of the workflows below |
| `gemini-review.yml` | dispatched | Reviews the PR diff against `.gemini/styleguide.md` |
| `gemini-triage.yml` | dispatched | Labels and triages a new issue |
| `gemini-invoke.yml` | dispatched | Answers a free-form `@gemini-cli <request>` |
| `gemini-plan-execute.yml` | dispatched | Turns a plan request into changes on a branch |

## Using it

Open a pull request and the review runs by itself. For anything else, comment on the issue or pull
request:

```
@gemini-cli /review          re-review, optionally followed by extra context
@gemini-cli /triage          triage the issue
@gemini-cli <question>       ask about the code in this repository
```

Commands are accepted only from the owner, members and collaborators. Comments posted before this
workflow reaches the default branch do nothing: GitHub takes `issue_comment` workflows from the
default branch, not from the pull request.

## Rules the reviewer follows

`GEMINI.md` is the project rulebook and `.gemini/styleguide.md` the review contract — severity
labels, `path:line` citations, the Kotlin/Android focus list, and the secrets rule. Keep `GEMINI.md`
identical to `AGENTS.md` and `CLAUDE.md` when those files exist: one rulebook, no divergence.

## Settings

Repository variables, not secrets: `GCP_WIF_PROVIDER`, `SERVICE_ACCOUNT_EMAIL`, `GOOGLE_CLOUD_PROJECT`,
`GOOGLE_CLOUD_LOCATION`, `GOOGLE_GENAI_USE_VERTEXAI`, `GOOGLE_GENAI_USE_GCA`, `GEMINI_MODEL`,
`UPLOAD_ARTIFACTS`. `GEMINI_DEBUG=true` adds a step that dumps the event context; `GEMINI_CLI_VERSION`
pins the CLI.

Changing the model is a variable change: probe the candidate first, since Vertex publishes different
model ids per region and `404 ... was not found or your project does not have access to it` means the
id is unavailable, not that credentials are wrong.
