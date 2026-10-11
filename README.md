# mechanical-hub-orders

Cria ordens de serviço, altera o status da ordem e expõe a API de interface do sistema (mecânicos e cliente).

Serviço da Fase 4 do Mechanical Hub (Kotlin, Java 25, Spring Boot 4.1, PostgreSQL 16, Amazon SQS FIFO). Spec: `MechanicalHub/planning/mechanical-hub-orders-spec.md`.

## Responsabilidade

**Faz:** ordem de serviço e seu status, reparos da ordem (`order_tasks`), dados de clientes, veículos e funcionários (somente leitura, vindos das seeds), catálogo de reparos (sem preço), API para mecânico e cliente, notificações ao cliente e ao mecânico, início da saga e execução dos comandos do orquestrador.

**Não faz:** estoque e materiais (`stock`), preços e pagamento (`billing`), histórico da saga (`orchestrator`), cadastro de catálogo, relatórios.

O banco do `orders` também é lido pela Lambda `authenticate` do `mechanical-hub-auth` (`users`/`profiles`, role `mechanical_hub_auth` com `GRANT SELECT` concedido pela migration `V3`).

## Papel na saga

O `orders` inicia a saga e executa os comandos do orquestrador. Toda publicação passa pelo **outbox** (mesma transação da alteração); todo comando é deduplicado em `inbox_events` por `idempotencyKey = {transactionId}:{STEP}`.

```
mecânico ──PATCH PREPARING──▶ orders ──diagnosisCompleted──▶ orquestrador ──▶ stock / billing
                                ▲                                  │
                                └──── sendApproval, markStockPending, clearStockPending,
                                      sendPaymentLink, releaseOrder, cancelOrder
cliente ──approve / reject──▶ orders ──orderApproved / orderRejected──▶ orquestrador
```

| Publica (fila `mechanical-hub-orchestrator-events.fifo`) | Quando |
|---|---|
| `diagnosisCompleted` | `PATCH /orders/{id}/status` → `PREPARING` |
| `orderApproved` / `orderRejected` | cliente aprova / recusa o orçamento |
| `orderFinished` | `PATCH /orders/{id}/status` → `FINISHED` |
| `orderCanceled` | consumo de `cancelOrder` |

| Consome (fila `mechanical-hub-orders-commands.fifo`) | Efeito | Depois do `COMMIT` |
|---|---|---|
| `sendApproval` | `WAITING_APPROVAL`, grava orçamento, saga e hash do token | e-mail/WhatsApp com orçamento e link de decisão |
| `markStockPending` / `clearStockPending` | liga / desliga `has_stock_pending` | — |
| `sendPaymentLink` | `AWAITING_PAYMENT` | link de pagamento ao cliente |
| `releaseOrder` | `READY_FOR_EXECUTION` | aviso ao mecânico responsável |
| `cancelOrder` | `CANCELED` + `orderCanceled` | — |

Falhas (`failure_handling.md`): erro transitório não apaga a mensagem e ajusta o *visibility timeout* (1 s → 8 s, até 6 recebimentos, depois DLQ nativa); erro permanente (contrato inválido, transição inválida) vai direto para `mechanical-hub-orders-commands-dlq.fifo` com `errorClass`/`errorMessage`. O relay do outbox publica a cada 1 s, em lotes de 100 por `sequence`, com liderança por `pg_try_advisory_lock`.

## Endpoints

| Rota | Quem | Observação |
|---|---|---|
| `POST /orders` | mecânico | abre a ordem em `IN_DIAGNOSIS` para cliente e veículo das seeds |
| `PATCH /orders/{orderId}/repairs` | mecânico | só em `IN_DIAGNOSIS` |
| `PATCH /orders/{orderId}/status` | mecânico | `PREPARING`, `IN_PROGRESS`, `FINISHED`, `DELIVERED` |
| `POST /orders/status` | mecânico | status do reparo (`STARTED`, `FINISHED`) com a ordem em `IN_PROGRESS` |
| `GET /orders/{orderId}`, `GET /orders?status=&customerId=&responsibleUserId=` | mecânico | inclui `hasStockPending` |
| `GET /services` | mecânico | catálogo de reparos |
| `GET /mechanical-hub/service-orders/{orderNumber}` | cliente | header `X-Approval-Token` |
| `POST /mechanical-hub/service-orders/{orderId}/approve` \| `/reject` | cliente | header `X-Approval-Token` |

Rotas do mecânico exigem os headers `x-user-id`, `x-user-role`, `x-user-name`, injetados pelo API Gateway (sem eles: `401`). Token do cliente ausente ou inválido: `404`. Transição inválida ou conflito de `version`: `409`. Falha transitória de banco: `503`. Swagger em `/swagger-ui.html` (fora do gateway; usar `kubectl port-forward` no cluster).

## Como rodar

Pré-requisitos: JDK 25 e Docker.

```bash
# Testes unitários e de integração (Testcontainers: PostgreSQL + LocalStack),
# ktlint, detekt e cobertura mínima de 80% (jacoco:check)
./mvnw verify

# Aplicação local: sobe PostgreSQL (porta 5433) e LocalStack (4566, filas FIFO + DLQs)
# pelo docker-compose e roda em http://localhost:8080
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Usuários, clientes e veículos vêm das seeds (UUIDs do monolito). Mecânico padrão: `7a2e9b41-3c5d-4f60-8e1a-000000000002` (CPF `111.444.777-35`); cliente `96052f8d-0817-4124-84ad-918b7c9ac73d` com o veículo `d0803851-6a5f-4fea-8463-a723be0e390c`.

### Simulando o orquestrador localmente

Sem o orquestrador no ar, os comandos podem ser enviados direto à fila do LocalStack. Exemplo de `sendApproval` (troque `<orderId>`):

```bash
ORDER_ID=<orderId>; TX=$(uuidgen); EVENT=$(uuidgen)
docker compose exec localstack awslocal sqs send-message \
  --queue-url http://localhost:4566/000000000000/mechanical-hub-orders-commands.fifo \
  --message-group-id "$ORDER_ID" --message-deduplication-id "$EVENT" \
  --message-body "{\"eventId\":\"$EVENT\",\"transactionId\":\"$TX\",\"orderId\":\"$ORDER_ID\",\"type\":\"sendApproval\",\"version\":1,\"timestamp\":\"2026-10-11T12:00:00Z\",\"idempotencyKey\":\"$TX:SEND_APPROVAL\",\"budgetId\":\"$(uuidgen)\",\"orderCost\":308.10}"
```

Os outros comandos seguem o mesmo envelope (`markStockPending`, `clearStockPending`, `releaseOrder`; `sendPaymentLink` com `paymentLink`; `cancelOrder` com `reason`). O e-mail ainda é registrado em log: o link de aprovação, com o token, aparece no log `Enviando email para ...`. Os eventos publicados podem ser lidos com `awslocal sqs receive-message` na fila `mechanical-hub-orchestrator-events.fifo`.

Os quatro fluxos da saga (caminho feliz, pendência de estoque, recusa e pagamento não confirmado) estão automatizados em `SagaFlowIntegrationTest`, com o teste fazendo o papel do orquestrador pela fila real do LocalStack.

### Postman

`docs/postman/mechanical-hub-orders.postman_collection.json`. Com `mode = local` e `baseUrl = http://localhost:8080`, a pasta *Mecanico* envia os headers `x-user-*` direto ao serviço. Pelo API Gateway: `mode = gateway`, `baseUrl` = URL do gateway, CPF e senha em `cpf`/`password` e rode *Auth / Login* primeiro.

## Configuração

Tudo por variáveis de ambiente (ConfigMap/Secret no cluster). Sem chave AWS: os pods usam a `LabRole` dos nós.

| Variável | Padrão local | Origem no deploy |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` | `localhost`, `5432`, `mechanical_hub_orders`, `orders_app` | output `databases["orders"]` do `mechanical-hub-database` |
| `DB_PASSWORD` | `orders_app` | secret `ORDERS_DB_APP_PASSWORD` |
| `ORDERS_COMMANDS_QUEUE_URL`, `ORDERS_COMMANDS_DLQ_URL`, `ORCHESTRATOR_EVENTS_QUEUE_URL` | nomes das filas | outputs `sqs_queue_urls` / `sqs_dlq_urls` do `mechanical-hub-infra` |
| `FRONTEND_BASE_URL` | `http://localhost:3000` | variável `FRONTEND_BASE_URL` do repositório |
| `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_TRACES_EXPORTER` | — | output `otlp_http_endpoint` (sem coletor: `none`) |

## Deploy

- `Dockerfile` multi-stage (JRE 25, usuário sem privilégios, agente OpenTelemetry com propagação do trace nos *message attributes* do SQS).
- `k8s/`: Namespace `production`, ConfigMap, Secret, Deployment (requests 250m/640Mi, limits 1 vCPU/1 GiB, probes do actuator), Service NodePort **30080** (target group do NLB no `mechanical-hub-infra`) e HPA de 1 a 2 réplicas.
- `.github/workflows/deploy.yml`: em PR, `verify` + SonarQube Cloud (quality gate); na `main`, também lê os remote states (`ci/remote-state`), publica a imagem no ECR `mechanical-hub/orders`, aplica os manifestos no EKS e roda `ci/smoke-test.sh` via `port-forward`.

Secrets/variáveis do repositório: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `AWS_ACCOUNT_ID`, `ORDERS_DB_APP_PASSWORD`, `SONAR_TOKEN`; variáveis `SONAR_ORGANIZATION` e `FRONTEND_BASE_URL`.

Ordem no ambiente zerado (`infrastructure.md` §7): `mechanical-hub-infra` → `mechanical-hub-database` (incluindo usuário `orders_app` e role `mechanical_hub_auth`) → este pipeline → `mechanical-hub-auth`.

O smoke test também roda localmente contra a imagem: `docker build -t mechanical-hub-orders:local .`, suba o container apontando para o docker-compose e execute `ci/smoke-test.sh http://localhost:<porta>`.

## Observabilidade

- `/actuator/prometheus` (raspado pelo `mechanical-hub-infra` pelo label `app.kubernetes.io/name: orders`), com `outbox_oldest_pending_age_seconds` (alerta > 60 s) e `sqs_dlq_sent_total`.
- Logs JSON com `orderId`, `transactionId`, `eventId`, `trace_id` e `span_id`.
- Traces pelo agente OpenTelemetry; o contexto atravessa o SQS até o orquestrador.

## Cobertura

Evidência da execução de `./mvnw verify` em 2026-10-11 (265 testes, 0 falhas):

| Escopo | Cobertura de linhas |
|---|---|
| Total (JaCoCo, mesmas exclusões do Sonar) | **93,8%** (1130 de 1205 linhas) |
| `domain` | 99,2% |
| `application` | 99,7% |
| `infrastructure` | 90,6% |

Cobertura de branches: 73,0%. O build falha abaixo de 80% de linhas (`jacoco:check`). O relatório HTML fica em `target/site/jacoco/index.html` e, no CI, no artefato `jacoco-report` de cada execução; o SonarQube Cloud lê o `jacoco.xml` no quality gate do PR.
