# Upgrading Valhalla

The release baseline uses the `papagrationbiz-sketch/valhalla` fork, not an unmodified upstream
Valhalla tag. Changing the submodule is a separate routing-core change and requires explicit
approval, build verification, and behavior regression tests.

```sh
# Initialize the commit already pinned by the parent repository.
git submodule update --init --recursive

# Record the exact release baseline.
git ls-tree HEAD src/valhalla
```

The `0.6.0` baseline is `13eb61624f9b6464dbaad5081e9fd71207ec539c`. A proposed upgrade must
document the old and new commits, the fork-specific commits retained, and the iOS and Android test
results. See [../release-policy.md](../release-policy.md).
