# Plan: Multi-Level DLQ for all-events MQ

## Context
The project has an `all-events` message queue for processing chat bot events. Currently there's no dead letter queue (DLQ) implementation - failed messages are simply nack'd which either causes infinite retry or data loss.

We need to implement a multi-level retry system with exponential delays since RabbitMQ doesn't support exponential delay natively.

## Requirements
- Multiple retry queues with different TTL values
- Manual routing based on `x-retry-count` header
- Final DLQ that holds messages until manual restart

## Retry Levels

| Level | Queue Name | TTL | Description |
|-------|-----------|-----|-------------|
| 0 | `all-events-queue` | 0 | Main queue - 3 immediate retries |
| 1 | `retry-5s` | 5s | First delay |
| 2 | `retry-30s` | 30s | Second delay |
| 3 | `retry-1m` | 60s | Third delay |
| 4 | `retry-15m` | 900s | Fourth delay |
| 5 | `retry-1h` | 3600s | Fifth delay |
| 6 | `dlq-final` | ∞ | Final queue - manual intervention needed |

## Implementation Approach

### 1. Configuration Changes (application.yml)
Add incoming channel configurations for each retry queue and DLQ.

### 2. EventDispatcher Enhancement
Modify the error handling to manually route messages based on `x-retry-count` header:
- Read current retry count from headers (default 0)
- Determine next queue based on count
- Publish to appropriate retry queue
- Ack the original message

### 3. Flow Diagram
```
Message Processing Failed
         │
         ▼
    [Read x-retry-count]
         │
         ▼  (get next queue name from collection)
    ┌─────────────────────────────────────────┐
    │
    ▼
retry-count=0 ──▶ retry-5s
retry-count=1 ──▶ retry-30s
retry-count=2 ──▶ retry-1m
retry-count=3 ──▶ retry-15m
retry-count=4 ──▶ retry-1h
retry-count=5+ ──▶ dlq-final

Each retry queue has:
  x-dead-letter-exchange: all-events
  x-dead-letter-routing-key: queue (routes back to all-events-queue)
```

## Critical Files to Modify

1. `/private/var/folders/3b/cqww14cn08nd8tm8qcynn7mr0000gp/T/vibe-kanban/worktrees/c2ba-all-events-mq-dl/vk/src/infrastructure/src/main/resources/application.yml`
   - Add incoming channels for retry-5s, retry-30s, retry-1m, retry-15m, retry-1h, dlq-final
   - Configure TTL and DLX for each retry queue

2. `/private/var/folders/3b/cqww14cn08nd8tm8qcynn7mr0000gp/T/vibe-kanban/worktrees/c2ba-all-events-mq-dl/vk/src/infrastructure/src/main/kotlin/com/simarel/vkbot/infrastructure/mq/adapter/input/EventDispatcher.kt`
   - Add logic to read x-retry-count header
   - Add Emitters for publishing to retry queues
   - Implement retry routing logic

## Verification Steps
1. Run application and verify queues are created in RabbitMQ
2. Verify queue bindings and DLX configuration
3. Test failed message flows through retry levels
4. Verify final DLQ receives message after max retries
