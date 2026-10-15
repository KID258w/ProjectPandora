# add-ci-cd-gates

## Why

仓库已有 android/、backend/、web/ 三份模块骨架，但没有一条自动化检查：任何人的改动都无法在合并前被验证，密钥文件（keystore、`.env`、`local.properties`）被提交时也不会有人发现。团队约定要求"每个 PR 说明关联的 OpenSpec change 与测试结果"，但目前没有机制保证这些约定被执行。

本 change 属于**工程基础设施改动**，不改变任何产品行为，因此在 `.openspec.yaml` 中声明 `skip_specs: true`（不产生 spec delta）。

## What Changes

- 增加 GitHub Actions 工作流：规格校验（OpenSpec）、敏感文件扫描、workflow 语法检查、README 中描述的三类模块检查（Android / 后端 / Web）
- 模块检查按路径过滤，只在对应模块变化时触发
- 增加协作模板：PR 模板、Issue 表单、CODEOWNERS、Dependabot（actions / npm / pip / gradle 四个生态）
- 增加根 `.gitignore`，覆盖密钥、IDE 配置与构建产物
- 增加 `docs/cicd/ci-gates.md`：门禁边界、ruleset 配置步骤、本地复现命令与待办清单

## Non-goals

- 不做镜像构建、GHCR 推送、服务器自动部署（属于 `docs/cicd/cicd-guide.md` 的待办 3–6，另行安排）
- 不引入 MySQL service（后端当前使用 SQLite，切换时再补）
- 不启用 openspec `--strict` 阻断（存量 WARNING 先以欠债报告形式跟踪）
- 不配置 Android 签名发布

## Impact

- 受影响模块：仓库级 CI/CD、协作流程；不涉及业务代码逻辑
- 需要的人工配置：`Settings → Rules` 为 `dev`/`main` 各建一条 ruleset；`Settings → Code security` 开启 Secret scanning；补齐协作者 write 权限
