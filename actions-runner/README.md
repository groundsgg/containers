# actions-runner

GitHub Actions runner image for the **ARC ephemeral CI runners** — the
`grounds-runners` scale set running on the `ci-pve-1` cluster.

Built on the upstream [`actions/actions-runner`](https://github.com/actions/actions-runner)
image, with Node.js and pnpm added so Node / Next.js pipelines (e.g.
`grounds-portal`) run directly on the runner — no job-level `container:`
required. Every runner pod is ephemeral: it runs a single job and is then
discarded, so there is no workspace state to clean up between jobs.

## Included

- GitHub Actions runner (upstream base)
- Node.js 24 + npm
- pnpm 10
- jq

## Usage

Consumed by the ARC runner scale set as its runner image — set via the
`ci-pve` Pulumi stack's `runnerImage` config. Workflows target the pool
with:

```yaml
jobs:
  build:
    runs-on: grounds-runners
```

## Image

`ghcr.io/groundsgg/actions-runner`
