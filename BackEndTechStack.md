# Backend Development

- Java 21
- Spring Boot 4.x
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Cloud
- REST APIs
- Microservices Architecture
- Gradle
- JUnit 5
- Mockito
- Testcontainers

# Complete

```text
                              GitHub
                                │
                                ▼
                        GitHub Actions
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ▼                 ▼                 ▼
            Build            Test           Security
              │                 │                 │
              └─────────────────┼─────────────────┘
                                │
                                ▼
                            SonarQube
                                │
                                ▼
                          Docker Image
                                │
                                ▼
                    GitHub Container Registry
                                │
                                ▼
                              Helm
                                │
                                ▼
                            Argo CD
                                │
                                ▼
                          Kubernetes
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ▼                 ▼                 ▼
        Microservice     Microservice     Microservice
              │                 │                 │
              └─────────────────┼─────────────────┘
                                │
                                ▼
                         OpenTelemetry
                                │
                          OTel Collector
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ▼                 ▼                 ▼
          Prometheus          Loki            Tempo
              │                 │                 │
              └─────────────────┼─────────────────┘
                                │
                                ▼
                             Grafana
```

```mermaid
graph TD
    GitHub --> GitHubActions[GitHub Actions]
    
    GitHubActions --> Build
    GitHubActions --> Test
    GitHubActions --> Security
    
    Build --> SonarQube
    Test --> SonarQube
    Security --> SonarQube
    
    SonarQube --> DockerImage[Docker Image]
    DockerImage --> GHCR[GitHub Container Registry]
    GHCR --> Helm
    Helm --> ArgoCD[Argo CD]
    ArgoCD --> K8s[Kubernetes]
    
    K8s --> MS1[Microservice 1]
    K8s --> MS2[Microservice 2]
    K8s --> MS3[Microservice 3]
    
    MS1 --> OTel[OpenTelemetry]
    MS2 --> OTel
    MS3 --> OTel
    
    OTel --> Collector[OTel Collector]
    Collector --> Prometheus[Prometheus]
    Collector --> Loki[Loki]
    Collector --> Tempo[Tempo]
    
    Prometheus --> Grafana[Grafana]
    Loki --> Grafana
    Tempo --> Grafana
```