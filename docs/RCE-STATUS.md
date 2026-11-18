# RCE and shell status

The discoverer reports successful, application-specific RCE chains and has
published a demonstration video. The same disclosure explicitly says those
weaponized product exploits are being withheld for now.

This repository therefore does **not** present heap corruption as if it were a
shell. It contains no reverse shell, web shell, product-specific exploit, or
synthetic command-execution backdoor.

The public, reproducible boundary implemented here is:

```text
attacker-controlled HTTP body
  -> java.util.Base64.Decoder
  -> AVX-512 intrinsic
  -> bytes changed past the expected output size
  -> clean negative control with -XX:-UseBASE64Intrinsics
```

A future shell claim should only be added when all of the following are true:

1. the exploit is public from a credible source;
2. it is reproduced from a clean clone against the local lab only;
3. command execution is demonstrated with a benign marker such as `id`;
4. the exact runtime, CPU, container digest, and output are recorded;
5. the exploit is not made portable to unrelated public targets.

Until then, the honest result is memory corruption with reported RCE impact,
not a public universal shell exploit.
