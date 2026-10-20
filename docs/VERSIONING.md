# Release versioning

Fabric and Forge use the same versioning policy, with independent release tags and bug-fix numbers.

| Change | Example |
|---|---|
| Bug fix within the current feature baseline | `0.6.0` → `0.6.1` → `0.6.2` |
| New features or an upgrade before the first complete stable release | `0.6.x` → `0.7.0` → `0.8.0` |
| First complete stable release | `1.0.0` |
| Later major release | `2.0.0`, `3.0.0`, etc., when explicitly designated |

Before `1.0.0`, releases remain prereleases. A successful CI run alone does not designate the project complete and stable.

Fabric versions are stored as `version` in the root `gradle.properties`, with tags such as `v0.6.0`. Forge versions are stored as `forge_version` in `forge/gradle.properties`, with tags such as `forge-v0.6.0`.

A Forge-only bug fix advances only Forge's patch version; a Fabric-only bug fix advances only Fabric's. A shared fix can require separate releases for both loaders. Neither loader is bumped or republished solely because the other received a release. Feature baselines align when the corresponding functionality is available on each loader; adding Forge support starts at the existing Fabric baseline, `0.6.0`.

Publish a new tag for each release. Do not move existing release tags or replace published JARs to deliver a fix.
