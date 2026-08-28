# ADR 001: deterministic session routing

Status: accepted

Use SHA-256 of `X-Session-Id` modulo 100 for routing. This avoids request-to-request flapping, preserves session affinity for a percentage, and makes approximate distribution tests reproducible. Requests without a header receive a unique anonymous ID because they have no session affinity contract.
