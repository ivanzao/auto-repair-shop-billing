# Auto Repair Shop — Billing

Microsserviço de **billing** (orçamento e pagamento) da Fase 4 do Auto Repair Shop. Recebe a
reserva já priced da execução, gera o orçamento, coleta a aprovação do cliente por link de
e-mail, integra o **Mercado Pago** (preferência de checkout + webhook + estorno) e participa da
saga coreografada publicando os eventos de resultado.

Greenfield derivado do esqueleto do serviço `order` (mesma stack e convenções). RDS Postgres
próprio.

## Estrutura de Pastas

```
domain/    billing/quote/**      (Quote, QuoteApprovalToken, use cases, eventos, ports)
           billing/payment/**    (PaymentProviderPort, WebhookSignatureValidator, PaymentUseCase)
           event/**, shared/**   (infra reusada: outbox, idempotência, transação)
storage/   billing/**            (Quotes, QuoteApprovalTokens + repositórios Postgres)
           event/, idempotency/  (outbox + dedup)
           db/migration/**       (V1 events, V2 idempotency, V3 shedlock, V4 quotes, V5 tokens)
api/       billing/**            (QuoteRoutes: approve/decline; WebhookRoutes: mercadopago)
consumer/  **                    (InboundEventConsumer + handlers SuppliesReserved/ExecutionFailed)
producer/  **                    (outbox → SNS com envelope do contrato)
payment/   **                    (MercadoPagoPaymentAdapter, FakePaymentProvider, assinatura)
worker/    scheduler/**          (ShedLock + OutboxRelayTask)
main/      **                    (Koin wiring, Ktor server, config)
infra/k8s/ base + overlays/**    (Kustomize: NodePort 30081, ESO do Mercado Pago)
```

## Arquitetura — papel na saga (coreografia, sem orquestrador)

Comunicação 100% assíncrona (SNS→SQS, envelope do contrato). REST só para o link de aprovação
e o webhook do Mercado Pago. O estado é derivado por serviço (status da `Quote`), sem
orquestrador central.

**Consome** (fila `auto-repair-shop-billing-queue`, dispatch por `eventType`, ignora o resto):

| eventType | efeito |
|---|---|
| `SuppliesReserved` (de execution) | cria a `Quote` priced (persiste `reservationId`), gera token e enfileira `QuoteEmailRequested` |
| `ExecutionFailed` (de execution) | estorna o pagamento via Mercado Pago e marca a `Quote` `REFUNDED` |

**Produz** (tópico `auto-repair-shop-billing-events`, envelope + attribute `eventType`):

| eventType | gatilho |
|---|---|
| `QuoteEmailRequested` | após criar a Quote (consumido pela Lambda de e-mail, assinatura filtrada) |
| `PaymentConfirmed` | webhook MP: pagamento aprovado |
| `QuoteRejected` | link `/decline` |
| `PaymentFailed` | webhook MP: pagamento recusado/expirado |

`QuoteApproved` é **interno** (não publicado): disparado pelo link `/approve`, cria a preferência
no Mercado Pago e redireciona ao checkout.

Fluxo feliz: `SuppliesReserved` → `QuoteEmailRequested` → (cliente aprova → `QuoteApproved`
interno → checkout MP) → webhook → `PaymentConfirmed`.

Contrato completo: `auto-repair-shop-infra/docs/saga-event-contract.md`.

### Envelope, idempotência e outbox

- **Envelope** (body): `{ eventId, eventType, eventVersion, occurredAt, payload }`, JSON camelCase,
  dinheiro como decimal. Attribute SNS `eventType` (casa com o `filter_policy` da Lambda de e-mail)
  e `traceparent` (W3C).
- **Idempotência**: dedup por `(orderId, eventId)` na tabela `idempotency`; handlers também são
  idempotentes por estado da `Quote` (transições terminais não reprocessam).
- **Outbox**: mudança de estado + evento gravados na mesma transação (`events`); `SnsEventPublisher`
  publica e o `OutboxRelayTask` (ShedLock) reprocessa pendentes.

## Integração Mercado Pago

- **Preferência**: `POST /checkout/preferences` na aprovação (Checkout Pro) → redirect 302 ao
  `init_point`.
- **Webhook**: `POST /v1/webhooks/mercadopago` valida a assinatura `x-signature` (HMAC-SHA256),
  consulta o pagamento e emite `PaymentConfirmed`/`PaymentFailed`.
- **Estorno**: `POST /v1/payments/{id}/refunds` ao consumir `ExecutionFailed`.
- **Fake**: quando `MERCADOPAGO_ACCESS_TOKEN` está vazio (local/hml/testes), usa
  `FakePaymentProvider` (sem chamadas externas).

## Stack

Kotlin 2.2.10 · Ktor 3.3.3 · Koin 4.1.1 · Exposed 0.61 · Flyway 11 · HikariCP · PostgreSQL ·
AWS SDK Kotlin (sns+sqs) · Ktor client (Mercado Pago) · JUnit5 + MockK + Testcontainers
(Postgres + LocalStack) · Docker multi-stage · K8s Kustomize + External Secrets · GitHub Actions.

## Execução Local

### Docker Compose (recomendado)

```bash
docker compose up --build
```

Sobe Postgres, LocalStack (SNS+SQS, com tópico/fila do billing provisionados) e a aplicação em
`http://localhost:8080` (`/health`, `/metrics`). Sem `MERCADOPAGO_ACCESS_TOKEN`, o provedor fake é
usado.

### Sem Docker

```bash
# Postgres local em auto_repair_shop_billing_local (user app_billing_local)
./gradlew :main:run
```

## Testes

```bash
./gradlew test                    # unit (todos os módulos)
./gradlew integrationTest         # integração (Testcontainers: Postgres + LocalStack) — requer Docker
./gradlew jacocoAggregatedReport  # relatório em build/reports/jacoco/...
```

Os fluxos de saga são exercitados fim-a-fim nos testes de integração do `main`
(`QuoteCreationIntegrationTest`, `QuoteApprovalIntegrationTest`, `PaymentWebhookIntegrationTest`,
`ExecutionFailedIntegrationTest`), dirigidos pela fila SQS e pelo HTTP.

### Cobertura

| Métrica | Valor |
|---|---|
| Cobertura (SonarCloud) | **89.6%** |
| Testes | 33 |
| Quality gate | Passed |

Análise a cada PR pelo step `Sonar` do `pr-check.yaml`, no projeto
`auto-repair-shop-billing` da organização `ivanzao` no SonarCloud. O quality gate
exige 80% de cobertura em código novo.

Ficam fora da contagem de cobertura o wiring de framework (`config`, `auth`,
`metric`), o módulo `main` e os DTOs — código sem lógica de negócio própria. Eles
seguem analisados para bugs, code smells e security hotspots.

<!-- TODO: print do dashboard do SonarCloud (projeto é privado, link exige login) -->

## Deploy em Kubernetes (Kustomize)

```bash
kubectl apply -k infra/k8s/overlays/hml    # namespace auto-repair-shop-hml
kubectl apply -k infra/k8s/overlays/prod   # namespace auto-repair-shop-prod
```

- Service `NodePort 30081`, container `8080`.
- `MERCADOPAGO_ACCESS_TOKEN`/`WEBHOOK_SECRET` vêm do **External Secrets Operator** (ESO), do
  secret `auto-repair-shop/{env}/mercadopago`. `DATABASE_PASSWORD` do secret `billing-secret`
  (criado pelo CI). Demais valores no `billing-config` (patched no deploy).

### Contrato de integração via SSM (consumido pelo CI)

| Parâmetro | Uso |
|---|---|
| `/auto-repair-shop/{env}/eks/cluster-name` | cluster EKS |
| `/auto-repair-shop/{env}/billing/db/secret-arn` | credenciais do RDS |
| `/auto-repair-shop/{env}/sns/billing-events-topic-arn` | tópico produtor |
| `/auto-repair-shop/{env}/sqs/billing-saga-queue-url` | fila consumidora |
| `/auto-repair-shop/{env}/apigw/endpoint` | `APP_BASE_URL` / smoke test |
| `auto-repair-shop/{env}/mercadopago` (Secrets Manager) | populado pelo CI, lido pelo ESO |

## CI/CD

- `pr-check.yaml`: `./gradlew test` + `./gradlew integrationTest` em cada PR.
- `build-and-deploy.yaml`: testa, builda/publica a imagem no GHCR, popula o secret do Mercado
  Pago, reescreve o ConfigMap com valores de SSM e aplica o overlay Kustomize.

### Secrets necessários no GitHub

`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `GHCR_PAT`, `GHCR_TOKEN`,
`MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET`.
