# Claude Code Project Guide

Read `AGENTS.md` first. It contains the repository-wide rules.

## Project
Smart Certificate Generator — Next.js + Spring Boot + PostgreSQL platform for organization-branded certificate design, generation, bulk issuance, and verification.

## Required behavior
- Work incrementally; implement only the requested phase.
- Inspect before editing.
- Preserve existing code and conventions.
- Do not replace real implementations with mock data.
- Never claim tests passed unless they were executed.
- Ask before changing major architecture or dependencies.

## Critical technical concerns
- Fabric.js editor must serialize to canonical, versioned JSON.
- PDF renderer must match editor geometry.
- Khmer shaping and font embedding must be proven early.
- Organization isolation must be enforced server-side.
- Bulk processing must be asynchronous and idempotent.
- Public verification must reveal minimal approved data.

## Commands
Document actual project commands in the root README after frontend/backend initialization. Do not invent commands for scripts that do not exist.
