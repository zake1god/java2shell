# References

Primary and high-confidence sources checked on 2026-11-18:

- [Calif technical disclosure](https://calif.io/java4shell/)
- [OpenJDK vulnerability advisory, 2026-11-17](https://openjdk.org/groups/vulnerability/advisories/2026-11-17)
- [Oracle November 2026 Critical Security Patch Update](https://www.oracle.com/security-alerts/cspunov2026.html)
- [Red Hat security bulletin RHSB-2026-018](https://access.redhat.com/security/vulnerabilities/RHSB-2026-018)
- [Harness independent bounded reproduction](https://www.harness.io/blog/past-the-end-of-the-buffer-cve-2026-114938)
- [Canadian Centre for Cyber Security alert AL26-029](https://www.cyber.gc.ca/en/alerts-advisories/al26-029-critical-vulnerability-impacting-oracle-java-cve-2026-114938)

Important source differences:

- Oracle scores the CVE as CVSS 3.1 8.1 with high attack complexity.
- Calif reports CVSS 4.0 9.2 and application-specific RCE demonstrations.
- Independent public work has reproduced the out-of-bounds write and crash;
  universal application exploitability should not be inferred.
