# Security policy

This repository is an intentionally vulnerable, local-only research lab.

- Bind the service to loopback only. The supplied Compose file maps ports to
  `127.0.0.1`.
- Do not deploy the image to a public registry, Kubernetes cluster, shared
  server, or internet-facing host.
- Do not aim the trigger at third-party systems without explicit written
  authorization.
- Do not report a version string alone as proof of vulnerability. Confirm the
  vendor build, CPU gate, intrinsic setting, and reachable Base64 data path.

To report a security issue in this repository, open a private GitHub security
advisory. Do not include credentials, production data, or third-party targets.
