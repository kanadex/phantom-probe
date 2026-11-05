# Release notes

每个 tag 一份文件，文件名与 tag 完全一致：

```
docs/releases/v1.1.0.md
docs/releases/v1.2.0.md
```

`.github/workflows/build.yml` 在发布时会读取 `docs/releases/<tag>.md` 作为 release body，
文件不存在就直接失败，不会发出空壳 release。
