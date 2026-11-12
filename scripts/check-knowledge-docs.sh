#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

fail() { echo "FAIL: $1" >&2; exit 1; }

test -f docs/capabilities/user-knowledge.md || fail "capability file"
grep -q '^- \*\*Статус:\*\* delivered' docs/capabilities/user-knowledge.md || fail "capability status delivered"
grep -q '](user-knowledge.md)' docs/capabilities/index.md || fail "capability index link"
grep -q 'journey-knowledge' docs/capabilities/index.md || fail "journey index"
grep -q '010-create-user-knowledge-tables' docs/development/deployment.md || fail "deployment changeset (no .yaml)"
grep -q 'indisvalid' docs/development/deployment.md || fail "deployment invalid-index diagnostics"
grep -q 'drop index concurrently' docs/development/deployment.md || fail "deployment invalid-index recovery"
grep -q 'следующий номер — 011' docs/development/deployment.md || fail "deployment next changeset number"
grep -q 'knowledge.confirm-threshold' docs/development/configuration.md || fail "configuration.md knowledge keys"
grep -A20 '^knowledge:' src/app/src/main/resources/application.yml | grep -q 'evidence-refs-limit:' || fail "application.yml nested knowledge block"
grep -q '2026-11-11-user-knowledge-facts.md' docs/README.md || fail "ADR row"
grep -q '2026-11-11-knowledge-read-model.md' docs/README.md || fail "read-model ADR row"
grep -q '2026-11-11-user-knowledge-summary.md' docs/README.md || fail "user knowledge summary ADR row"
grep -q 'FindUserKnowledgePort' docs/capabilities/user-knowledge.md || fail "capability read port"
grep -q 'FindUserKnowledgeSummaryStatePort' docs/capabilities/user-knowledge.md || fail "capability summary state port"
grep -q 'FindUserKnowledgePort' docs/architecture/modules.md || fail "modules.md read port"
grep -q 'FindUserKnowledgeSummaryStatePort' docs/architecture/modules.md || fail "modules.md summary state port"
grep -q 'FindUserKnowledgePort' docs/architecture/data-flow.md || fail "data-flow.md read port"
grep -q 'FindUserKnowledgeSummaryStatePort' docs/architecture/data-flow.md || fail "data-flow.md summary state port"
grep -q 'AC-KNOW-8' docs/capabilities/user-knowledge.md || fail "capability AC-KNOW-8"
grep -q 'AC-KNOW-11' docs/capabilities/user-knowledge.md || fail "capability AC-KNOW-11"
grep -q 'UserKnowledgeReadIntegrationTest.kt' docs/capabilities/user-knowledge.md || fail "capability R2b tests"
grep -q 'UserKnowledgeSummaryIntegrationTest.kt' docs/capabilities/user-knowledge.md || fail "capability R3a tests"
grep -q 'aiKnowledgeSummarySystemprompt' docs/development/configuration.md || fail "configuration.md summary prompt"
grep -q 'summary-fact-threshold' docs/development/configuration.md || fail "configuration.md summary threshold"
grep -A40 '^knowledge:' src/app/src/main/resources/application.yml | grep -q 'summary-interval:' || fail "application.yml summary interval"
grep -q 'user_facts' docs/architecture/modules.md || fail "modules.md knowledge tables"
grep -q 'Хранение знаний' docs/architecture/data-flow.md || fail "data-flow.md knowledge section"
grep -q 'user_facts' docs/capabilities/message-storage.md || fail "message-storage.md knowledge tables"
grep -q '2026-11-11-hidden-birthday-greetings.md' docs/README.md || fail "hidden birthday ADR row"
grep -q '2026-11-11-knowledge-extraction.md' docs/README.md || fail "knowledge extraction ADR row"
grep -q '2026-11-11-chat-knowledge-summary.md' docs/README.md || fail "chat knowledge summary ADR row"
grep -q '2026-11-11-knowledge-in-prompts.md' docs/README.md || fail "knowledge in prompts ADR row"
grep -q 'Amends:.*ADR 2026-11-11-knowledge-read-model' docs/adr/2026-11-11-hidden-birthday-greetings.md || fail "hidden birthday ADR amends read model"
grep -q 'Amended by' docs/adr/2026-11-11-knowledge-read-model.md || fail "read model ADR amended by hidden birthday"
grep -q 'greetings.hidden-birthdays.enabled' docs/development/configuration.md || fail "configuration.md hidden birthday switch"
grep -q 'FindUserBirthdayFactsPort' docs/architecture/modules.md || fail "modules.md birthday read port"
grep -q 'FindUserBirthdayFactsPort' docs/capabilities/user-knowledge.md || fail "user-knowledge birthday read port"
grep -q 'AC-BDAY-2a' docs/capabilities/daily-greetings.md || fail "daily-greetings AC-BDAY-2"
grep -q '2026-11-11-profile-knowledge-overlay.md' docs/README.md || fail "profile knowledge overlay ADR row"
grep -q 'Amended by:.*2026-11-11-profile-knowledge-overlay' docs/adr/2026-11-10-profile-dossier.md ||
    fail "profile dossier ADR amended by overlay"
grep -q 'Из общения' docs/capabilities/profile-dossier.md || fail "profile-dossier knowledge section"
grep -q 'Из общения' docs/architecture/data-flow.md || fail "data-flow knowledge section"
grep -q 'AC-PROFILE-1' docs/capabilities/profile-dossier.md || fail "profile-dossier AC-PROFILE-1"
grep -q 'AC-PROFILE-1' docs/capabilities/user-knowledge.md || fail "user-knowledge AC-PROFILE-1"
grep -q 'FindUserKnowledgePort' docs/capabilities/profile-dossier.md || fail "profile-dossier read port"
grep -q 'knowledge.news-personalization.enabled' docs/development/configuration.md || fail "configuration.md news personalization switch"
grep -A40 '^knowledge:' src/app/src/main/resources/application.yml | grep -q 'news-personalization:' || fail "application.yml news personalization block"
grep -qE 'AC-NEWS-1\.1([^0-9]|$)' docs/capabilities/chat-news-digest.md || fail "chat-news-digest AC-NEWS-1"
grep -qE 'AC-NEWS-1\.1([^0-9]|$)' docs/capabilities/user-knowledge.md || fail "user-knowledge AC-NEWS-1"
grep -q 'Новости' docs/adr/2026-11-11-knowledge-in-prompts.md || fail "knowledge-in-prompts ADR news section"
grep -q 'Интересы автора запроса' docs/architecture/data-flow.md || fail "data-flow.md R4c sections"
grep -q 'Интересы автора запроса' docs/architecture/modules.md || fail "modules.md R4c sections"
grep -q '^## Пробелы приёмки' docs/capabilities/user-knowledge.md || fail "capability acceptance gaps"
grep -q 'user_facts.value' docs/architecture/data-flow.md || fail "data-flow.md fact value PII"

for ac in AC-KNOW-1 AC-KNOW-2 AC-KNOW-3 AC-KNOW-4 AC-KNOW-5 AC-KNOW-6 AC-KNOW-7 AC-KNOW-8 \
    AC-KNOW-9 AC-KNOW-10 AC-KNOW-11 AC-KNOW-12 AC-KNOW-13 AC-KNOW-14 AC-KNOW-15 AC-KNOW-16 \
    AC-KNOW-17 AC-KNOW-18 AC-KNOW-19 AC-PROFILE-1 AC-PROFILE-2 AC-PROFILE-3 AC-ANSWER-1 \
    AC-ANSWER-2 AC-NEWS-1 AC-BDAY-1 AC-E2E-1 AC-PII-1; do
    grep -qE "$ac([^0-9]|$)" docs/capabilities/user-knowledge.md || fail "capability $ac"
done

./scripts/check-capabilities.sh

echo "OK: knowledge docs, config and capability map resolve"
