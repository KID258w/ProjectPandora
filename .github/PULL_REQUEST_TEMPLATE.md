## 关联信息

- Issue：#
- OpenSpec change：`add-xxx`（目录名，与 `openspec/changes/` 下一致）
- 类型：feature / fix / docs / chore / refactor

## 变更内容

<!-- 做了什么、为什么这样做 -->

## OpenSpec 检查

- [ ] 本地已执行 `openspec validate <change> --strict` 并通过
- [ ] `tasks.md` 中已完成的任务已改为 `- [x]`，并附测试证据
- [ ] delta specs 已同步到主规格（或本次为纯文档/工具改动，已设置 `skip_specs: true`）
- [ ] 本次改动未包含密钥、keystore、`.env`（由 CI `secret-scan` 复核）

## 测试与验证

<!-- 实际执行的命令与结果；无自动化测试时给出人工验证步骤与截图 -->

```
（粘贴命令与输出摘要）
```

## 影响范围与回滚

- 影响的模块：后端 / Web 管理端 / Android / 文档
- 是否需要数据库或配置变更：
- 回滚方式：
