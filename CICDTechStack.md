# Continuous Integration

## Source Control

- Git
- GitHub

## CI

- GitHub Actions

## Pipeline

```text
Git Push / Pull Request ↓ Compile ↓ Unit Tests ↓ Integration Tests ↓ Code Quality — SonarQube ↓ Dependency / Vulnerability Scan ↓ Build Docker Image ↓ Push Image to Registry
```

## CI Tools

| Purpose             | Tool                      |
|---------------------|---------------------------|
| Source Control      | Git / GitHub              |
| CI                  | GitHub Actions            |
| Build               | Gradle                    |
| Unit Testing        | JUnit 5                   |
| Mocking             | Mockito                   |
| Integration Testing | Testcontainers            |
| Code Quality        | SonarQube Community Build |
| Dependency Scan     | OWASP Dependency-Check    |
| Container Scan      | Trivy                     |
| Container Build     | Docker                    |
| Container Registry  | GitHub Container Registry |

# Continuous Delivery/Deployment

## Kubernetes Deployment

- Kubernetes
- Helm
- Argo CD
- GitOps

```text
GitHub ↓ GitHub Actions ↓ Build & Test ↓ SonarQube ↓ Security Scan ↓ Docker Image ↓ GitHub Container Registry ↓ Update Helm Values ↓ Git Repository ↓ Argo CD ↓ Kubernetes
```

| Purpose            | Tool                      |
|--------------------|---------------------------|
| Containerization   | Docker                    |
| Container Registry | GitHub Container Registry |
| Orchestration      | Kubernetes                |
| Packaging          | Helm                      |
| GitOps CD          | Argo CD                   |

## AWS — Optional

```text
AWS │ ├── EKS │ └── Kubernetes │ ├── ECR │ └── Docker Images │ ├── RDS │ └── PostgreSQL │ ├── ElastiCache │ └── Redis │ ├── MSK │ └── Kafka │ ├── S3 │ └── Object Storage │ └── IAM └── Authentication / Authorization
```

## Complete Architecture

```text
                            Kubernetes Cluster
                                    │
                 ┌──────────────────┼────────────────-──┐
                 │                  │                   │
            API Gateway       Microservices       Infrastructure
                 │                  │                   │
                 │          ┌───────┼───────┐           │
                 │          │       │       │           │
                 │       User    Payment  Merchant      │
                 │       Service  Service  Service      │
                 │                                      │
                 └──────────────────────────────────────┘
                                    │
                             Observability
                                    │
               ┌────────────────────┼────────────────────┐
               │                    │                    │
           Prometheus              Loki                Tempo
               │                    │                    │
               └────────────────────┼────────────────────┘
                                    │
                                  Grafana
```

