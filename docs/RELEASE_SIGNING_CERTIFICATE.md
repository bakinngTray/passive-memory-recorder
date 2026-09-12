# Release signing certificate

Canonical signing certificate for Passive Memory Recorder releases.

- Package: `com.bakinngtray.passivememoryrecorder`
- Key alias: `passive-memory-recorder`
- Certificate SHA-256:

```text
8B:9E:5B:DD:A8:9A:BA:6B:FC:9E:29:7F:CB:BE:E7:5D:DA:03:E7:C1:2B:F4:A0:62:EF:70:23:B0:FA:8E:E0:F9
```

The private keystore and passwords are intentionally **not stored in Git**.

Every future APK published to RuStore (and any other store using the same package) should use this certificate so Android can install updates over existing versions.
