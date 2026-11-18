# References

Primary and high-confidence sources checked on 2026-11-18:

- [Calif technical disclosure](https://calif.io/java4shell/)
- [OpenJDK vulnerability advisory, 2026-11-17](https://openjdk.org/groups/vulnerability/advisories/2026-11-17)
- [Oracle November 2026 Critical Security Patch Update](https://www.oracle.com/security-alerts/cspunov2026.html)
- [Red Hat security bulletin RHSB-2026-018](https://access.redhat.com/security/vulnerabilities/RHSB-2026-018)
- [Harness independent bounded reproduction](https://www.harness.io/blog/past-the-end-of-the-buffer-cve-2026-114938)
- [Canadian Centre for Cyber Security alert AL26-029](https://www.cyber.gc.ca/en/alerts-advisories/al26-029-critical-vulnerability-impacting-oracle-java-cve-2026-114938)
- [OpenJDK fix commit 5177a70](https://github.com/openjdk/jdk/commit/5177a70b385c16f26ede191fdac7aaba0bed0f8b)
- [Tenable Java4Shell FAQ](https://www.tenable.com/blog/java4shell-cve-2026-114938-frequently-asked-questions-about-openjdk-rce-vulnerability)
- [Official Calif RCE demo video](https://www.youtube.com/watch?v=-EOYmL4UKvs)
- [N45HT Threads circulation of the Keycloak demo](https://www.threads.com/@n45htofficial/post/Df3L6lJlzYu)

Important source differences:

- Oracle scores the CVE as CVSS 3.1 8.1 with high attack complexity.
- Calif reports CVSS 4.0 9.2 and application-specific RCE demonstrations.
- Independent public work has reproduced the out-of-bounds write and crash;
  universal application exploitability should not be inferred.
- A second researcher reported a working application-specific PoC, but the
  public statement reviewed by Tenable does not say that it was released.
- Public GitHub repositories are reviewed separately in
  [PUBLIC-EXPLOIT-TRACKER.md](PUBLIC-EXPLOIT-TRACKER.md); a filename or README
  containing "exploit" is not treated as execution evidence.
