# 关联信息

- Issue：#
- OpenSpec change：`add-xxx`（与 `openspec/changes/` 下目录名一致，没有就写"不涉及"）
- 目标分支：`dev`（功能） / `main`（阶段发布，仅由 dev 发起）
- 类型：feature / fix / docs / chore / refactor

## 变更内容

<!-- 做了什么、为什么这样做 -->

## 检查清单

- [ ] 分支命名符合约定：`feature/<Issue编号>-<功能>`
- [ ] 本地已执行 `npx -y @fission-ai/openspec@1.14.1 validate --all` 且无 ERROR
- [ ] `tasks.md` 中已完成的任务已改为 `- [x]`，并附测试证据
- [ ] delta specs 已同步到主规格（或本次为纯文档/工具改动，已在 `.openspec.yaml` 设 `skip_specs: true`）
- [ ] 未提交密钥、keystore、`local.properties`、`google-services.json`、IDE 配置（CI 会复核）
- [ ] 未直接向 `dev` / `main` 推送提交（这两个分支只接受 PR）

## 测试与验证

<!-- 实际执行的命令与结果；无自动化测试时给出人工验证步骤与截图 -->

```
（粘贴命令与输出摘要）
```

## 影响范围与回滚

- 影响模块：Android / 后端 / Web 管理端 / 文档与流程
- 是否需要数据库或配置变更：
- 回滚方式：
