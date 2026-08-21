# GitHub Actions — CI / CD / Observability

Pipelines follow `CICDTechStack.md` and `OberserbilityTechStack.md`.

| Workflow      | File                                               | Trigger                          | Purpose                                              |
|---------------|----------------------------------------------------|----------------------------------|------------------------------------------------------|
| CI            | [`ci.yml`](workflows/ci.yml)                       | push/PR → `main`, `develop`      | Compile → tests → SonarQube → OWASP → Docker + Trivy |
| CD            | [`cd.yml`](workflows/cd.yml)                       | push `main` / tags `v*` / manual | Build → GHCR → bump Helm values → Argo CD            |
| Observability | [`observability.yml`](workflows/observability.yml) | PRs touching obs configs         | Validate compose + kustomize                         |

## Required secrets / vars

| Name                       | Used by | Notes                            |
|----------------------------|---------|----------------------------------|
| `SONAR_TOKEN`              | CI      | Optional — scan skipped if unset |
| `SONAR_HOST_URL`           | CI      | SonarQube / SonarCloud URL       |
| `SONAR_ORGANIZATION`       | CI      | Defaults to `paymentprocessor`   |
| `NVD_API_KEY`              | CI      | Speeds OWASP Dependency-Check    |
| `SONAR_ENABLED` (repo var) | CI      | Set to `false` to skip Sonar job |

`GITHUB_TOKEN` is used automatically for GHCR push and GitOps commits.

## Local parity

```bash
./scripts/ci-local.sh              # compile + test + jacoco
./scripts/ci-local.sh --owasp      # + dependency-check
./scripts/observability-up.sh      # Grafana LGTM stack
./scripts/docker-build.sh          # local image
./scripts/k8s-deploy.sh payment   # apply payment-service kustomize
```

## GitOps

1. Argo CD Applications live under `k8s/argocd/`.
2. CD updates `helm/payment-service/values.yaml` image tag.
3. Argo CD watches the repo and syncs the cluster.

Update `repoURL` in the Application manifests to your real GitHub remote before applying.
