# CI 门禁说明（CI/CD 负责人维护）

> **文档定位**：本文是**维护手册**（有哪些门禁、ruleset 怎么配、还剩哪些欠债），面向 CI/CD 负责人与仓库管理员。
> 面向使用者的功能介绍（功能是什么、PR 页面怎么看结果、失败了怎么排查、本地怎么复现）见 **[pr-check-workflow.md](pr-check-workflow.md)**。
>
> 分支模型见 [docs/git/git-workflow.md](../git/git-workflow.md)：
> `feature/<Issue编号>-<功能>` → PR → `dev` → PR → `main`
> 部署方案见 [docs/cicd/cicd-guide.md](cicd-guide.md)（镜像、GHCR、Compose、服务器）。

## 1. 四个工作流

| 工作流 | 触发条件 | Job | 作用 |
| --- | --- | --- | --- |
| `ci.yml` | PR 进 dev/main、推送 dev/main | `规格校验 (OpenSpec)` | `validate --all` 拦截规格结构性错误；输出 strict 欠债报告与各 change 任务进度 |
| | | `敏感文件扫描` | 按文件名拦截 keystore、`.env` 全变体、`local.properties`、`google-services.json` 等 |
| | | `Workflow 语法检查` | actionlint（镜像固定 `1.7.12`，内含 shellcheck）检查 workflow 自身与 `run:` 脚本 |
| `ci-android.yml` | PR → dev 且 `android/**` 变化 | `Android 测试与 Debug 编译` | Lint + 单元测试 + 编译 Debug APK，上传报告与 APK |
| `ci-backend.yml` | PR → dev 且 `backend/**` 变化 | `Django 检查与测试` | `manage.py check`、`makemigrations --check`、`migrate`、`manage.py test`（Python 3.11） |
| `ci-web.yml` | PR → dev 且 `web/**` 变化 | `Web 依赖安装与构建` | `npm ci` + lint/typecheck/test（脚本存在时才跑）+ `npm run build` |

模块级工作流用 `paths` 过滤（改后端不会白跑一次 Gradle 构建），且只在目标分支为 `dev` 的 PR 上运行；详见 **[pr-check-workflow.md](pr-check-workflow.md)**（触发条件、检查内容、PR 页面查看与失败排查、本地复现命令）。

## 2. 能拦什么、拦不住什么

| 门禁 | 能拦 | 拦不住 |
| --- | --- | --- |
| 规格校验 | change 缺少 `specs/` delta；`### Requirement:` 下没有 `#### Scenario:`；主规格缺 `## Purpose`/`## Requirements` | 目录名是否 kebab-case、`tasks.md` 是否勾选完、需求写得好不好 |
| 敏感文件扫描 | 文件名层：签名文件、keystore、私钥、`.env` 及 `.env.local/.env.production` 等变体、`local.properties` | **源码里硬编码的口令/Token**（靠 GitHub 原生 Secret scanning） |
| Workflow 语法检查 | workflow 的 YAML/表达式错误、action 输入名拼错、`needs` 指向不存在的 job、`run:` 中 bash 脚本问题 | 触发条件写错但语法合法、secrets 名称写错 |
| 模块 CI | 编译/构建失败、Lint 报错、单元测试失败、漏写 migration | 真机 UI 行为、业务逻辑正确性（除非有测试覆盖） |

⚠️ **`--strict` 会把 WARNING 也当失败**。这是"格式看着对但 CI 红"的常见原因，先找输出里的 `[WARNING]`。当前阻断步骤刻意未加 `--strict`（见第 4 节）。

## 3. 分支保护（ruleset）配置

`Settings → Rules → Rulesets`，`dev` 与 `main` **各一条**：

| 设置 | 值 |
| --- | --- |
| Enforcement status | Active（Evaluate 只是试运行，不拦截） |
| Target branches | `dev`（另一条填 `main`） |
| Require a pull request before merging | ✅ Required approvals: **1**，并勾 Dismiss stale approvals、Require conversation resolution |
| Require status checks to pass | ✅ 勾 `规格校验 (OpenSpec)`、`敏感文件扫描`、`Workflow 语法检查`。**模块级 check 不要设为必需**（它们只在对应模块变化时出现，否则其他 PR 会永远等待） |
| Block force pushes | ✅ |
| Restrict deletions | ✅ |
| Bypass list | 留空 |
| Require review from Code Owners | ❌ **不要勾**——CODEOWNERS 每行目前只有 1 个 owner，作者自己的 PR 会永久卡在 "Review required" |

⚠️ 若已经存在只配置了 `update`（限制直推）的 ruleset，注意：**限制直推但不要求 PR 批准/状态检查，等于"门锁上了但没人检查"**。需要补齐上面两行。

**当前仓库实际状态**（以便区分"该怎么配"与"已经配成什么样"）：已有一条名为 `merge-main-and-dev-owner-only` 的 ruleset，覆盖 `main` 与 `dev`，状态 Active，Bypass 为空，但**只启用了限制直推（update）**，尚未配置必需批准与必需状态检查，也未开启 Require review from Code Owners。

验证方法：开一个测试 PR 什么都不做 → Merge 按钮应为灰色；本地 `git push origin dev` 应被拒绝。

## 4. 本地命令

```powershell
# 0) 版本必须与 CI 一致
npm i -g @fission-ai/openspec@1.14.1
openspec --version            # 是 --version，不是 -version

# 1) 规格校验（CI 里跑的就是这条；报 WARNING 也要处理，它会阻塞 strict）
openspec validate --all
openspec validate --all --strict

# 2) 本地复现 workflow 检查（镜像内含 shellcheck，只装 actionlint 会漏报）
pip install shellcheck-py
# 再用与本仓库同版本的 actionlint 执行：actionlint -color

# 3) 各模块本地验证（与对应工作流相同的命令）
cd android && ./gradlew lintDebug testDebugUnitTest assembleDebug
cd backend && python manage.py check && python manage.py test
cd web     && npm ci && npm run build
```

## 5. 已知欠债与待办

| 项 | 说明 |
| --- | --- |
| openspec strict 未启用 | `dev` 上仍有若干「Requirement text is very long (>500 characters)」的 WARNING。清零后把 `ci.yml` 的阻断步骤改为 `--strict` 并删掉欠债报告步骤 |
| Web 缺 lint / test 脚本 | `web/package.json` 目前只有 dev/build/preview，`ci-web.yml` 用 `--if-present` 跳过；脚本补齐后自动生效 |
| 后端仍是 SQLite | 切到 MySQL 时需在 `ci-backend.yml` 追加 `services: mysql` 与数据库环境变量 |
| `android/gradlew` 无可执行位 | Git 中模式为 `100644`，工作流内用 `chmod +x` 兜底；根治：`git update-index --chmod=+x android/gradlew` |
| `demo/.idea/` 仍有 IDE 文件被跟踪 | 建议单独 chore PR：`git rm -r --cached demo/.idea` |
| Secret scanning 未开启 | `Settings → Code security` 开启 Secret scanning + Push protection，补上源码硬编码密钥的盲区 |
| 镜像与部署未开始 | 见 docs/cicd/cicd-guide.md 的待办 3–6：Dockerfile/Compose、GHCR 推送权限、测试环境验证与回滚 |

## 6. 验收清单

- [ ] `dev` 与 `main` 至少各被一条完整 ruleset 覆盖（允许 PR、必需批准、必需检查、禁止强推）
- [ ] 开一个 PR 能看到 `规格校验 (OpenSpec)`、`敏感文件扫描`、`Workflow 语法检查`
- [ ] 改 `android/**` 的 PR 额外出现 `Android 测试与 Debug 编译`，可下载报告与 APK
- [ ] 故意提交 `.env.local` 或 `local.properties` → 被拦截（然后撤回）
- [ ] 故意建一个只有 `proposal.md` 的 change → 被拦截（然后撤回）
- [ ] 开启 Secret scanning + Push protection
- [ ] `demo/.idea/` 停止跟踪，`android/gradlew` 补上可执行位
