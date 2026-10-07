# ProjectPandora CI/CD 落地指南

> 目标：让「提交 → 校验 → 构建 → 测试 → 部署 → 留痕」全流程自动化，并与团队已有的 OpenSpec 流程、GitHub Issue/PR 约定咬合。
> 本指南按 **三个阶段** 推进，当前仓库只具备执行 Phase 1 的条件。

---

## 0. 现状判断（决定了第一版该做什么）

| 事实 | 影响 |
| --- | --- |
| 仓库只有 1 个 commit，无 `package.json` / `pom.xml` / `build.gradle` | 现在**没有东西可以构建和测试** |
| README 写「技术栈待评审后确定」 | 模块级 CI 必须等技术栈定稿，否则写完就要推翻 |
| 只有 `openspec/config.yaml` + `.claude` / `.codex` skills + 文档 | **可以做、也应该做**的是规格门禁与仓库卫生门禁 |
| 使用 AI 工具（Claude Code / Codex）生成规格与代码 | CI 必须能自动判定「change 结构是否完整」，防止 AI 生成的 spec 漂移 |
| 团队约定：#1 先 Issue 再 PR、#5 至少一名非作者审阅 | 需要 PR 模板 + CODEOWNERS + 分支保护来落地 |

**结论**：CI/CD 不是等代码写完再补的东西。现在先上「规格 + 卫生」门禁（Phase 1），技术栈定稿后立刻接模块 CI（Phase 2），最后接部署（Phase 3）。

---

## 1. 总体方案

### 1.1 分支与环境模型

```
feature/*  ──PR──▶  main  ──tag v*──▶  release
   │                  │                  │
   │                  ├─▶ staging（自动部署，可随时重置）
   │                  └─▶ production（Environment 人工批准后部署）
```

- **main 永远可发布**：所有改动经 PR 合入，禁止直推。
- **staging / production 用 GitHub Environments 隔离**：production 配置 Required reviewers，实现「点一下才上线」。
- **Android 版本用 tag 触发**：`v0.1.0` 打 tag 才开始签名打包与分发。

### 1.2 三阶段任务清单

| 阶段 | 触发条件 | 内容 | 产出 |
| --- | --- | --- | --- |
| **Phase 1（现在）** | 立即 | OpenSpec 严格校验、敏感文件扫描、Workflow 语法检查、PR/Issue 模板、CODEOWNERS、Dependabot、分支保护 | `ci.yml` 三道门禁成为必需检查 |
| **Phase 2** | 技术栈评审通过、各模块有第一个可编译骨架 | 后端 / Web / Android 各自的构建 + Lint + 测试 + 产物归档（path filter 分开触发） | 三个模块 CI 绿，PR 上能看到测试报告 |
| **Phase 3** | 有可部署环境（服务器/云托管/测试机） | 镜像构建推送、staging 自动部署、production 审批部署、Android 签名分发、健康检查与回滚 | 一条命令从 main 到线上，失败可回滚 |

---

## 2. Phase 1：现在就能做完（建议本周内）

### 2.1 把文件放进仓库

```bash
# 在 ProjectPandora 仓库根目录
cp -r <本套件>/.github      .
mkdir -p docs/ci-cd && cp <本套件>/docs/ci-cd/ci-cd-guide.md docs/ci-cd/
cp    <本套件>/.gitignore   .

git checkout -b chore/add-ci-cd-scaffold
git add .github .gitignore docs/ci-cd
git commit -m "chore(ci): 增加 OpenSpec 规格门禁、PR 模板与 CI/CD 指南"
git push -u origin chore/add-ci-cd-scaffold
```

> 这本身就是一个 PR。按团队约定，PR 里链接 Issue，并请一名非作者成员审阅。

### 2.2 仓库网页端设置（一次性，Settings 里点几下）

1. **Actions 权限最小化**
   `Settings → Actions → General → Workflow permissions` → 选 `Read repository contents and packages permissions`；
   勾选 `Allow GitHub Actions to create and approve pull requests`（Dependabot 开 PR 需要）。
2. **允许 Actions 运行**：确认 `Actions` 页签未被禁用（Fork 私有仓库时尤其注意）。
3. **分支保护 / Ruleset（对 `main`）**
   `Settings → Rules → New ruleset → New branch ruleset`，目标 `main`，开启：
   - Require a pull request before merging（Required approvals: **1**，建议勾选 Dismiss stale approvals）
   - Require status checks to pass：勾选 **`规格校验 (OpenSpec)`、`敏感文件扫描`、`Workflow 语法检查`**（名字来自 `ci.yml` 里的 `name:`，首次运行后才会出现在列表里）
   - Require conversation resolution before merging
   - Block force pushes / Restrict deletions
   - （可选）Require linear history
4. **标签保护**：`Settings → Rules → New tag ruleset`，目标 `v*`，禁止删除与强推。
5. **Environments**：`Settings → Environments` 新建 `staging`（无审批）与 `production`（勾选 Required reviewers，选 1–2 人）。
6. **替换 CODEOWNERS 里的用户名**：把 `@KID258w` 换成真实 GitHub 账号名，其余 4 名成员按模块补上；被指定人需有仓库写权限，否则规则不生效。

### 2.3 这一步实际拦住了什么

- AI 或成员把 change 写成缺少 `specs/` delta、缺少 `#### Scenario:` 的半成品 → PR 直接红（实测：`Change must have at least one delta`）。
- 有人把 `release.jks`、`local.properties`、`.env`、`google-services.json` 提交进仓库 → `secret-scan` 失败（这类东西一旦进 Git 历史，就得改密钥）。
- Workflow YAML 写错（变量名拼错、`if` 表达式非法）→ `actionlint` 失败，避免合并后才发现流水线根本跑不起来。
- 每个 PR 的 Checks 摘要里会显示各 change 的任务完成度（`3/7`），评审时一眼看到进度。

---

## 3. Phase 2：技术栈确定后（模块 CI）

**前提**：Sprint 0 评审定下后端 / Web / Android 的框架与目录结构（建议 `backend/`、`web/`、`android/` 三目录 monorepo）。

### 3.1 操作步骤

1. 从 `templates/` 复制对应文件到 `.github/workflows/`，按文件头部注释改目录名与 JDK/Node 版本：
   - `ci-backend.spring.yml` → `ci-backend.yml`（若选 Node/NestJS，参照 `ci-web.vite.yml` 改写）
   - `ci-web.vite.yml` → `ci-web.yml`
   - `ci-android.yml` → `ci-android.yml`
2. 给每个工作流都加 `paths:` 过滤器（模板已带），保证改 Android 不触发后端流水线。
3. 在分支保护里把新增的 3 个 job 也设为必需检查（**只有真的能稳定通过后再加**，否则会卡住全队）。
4. 补上测试基础设施：后端 JUnit + Testcontainers、Web Vitest、Android JUnit + （可选）Compose UI 测试。
5. 让 `tasks.md` 里的每个任务都能对应到「一条可执行的验证命令」，CI 直接跑它。

### 3.2 关键设计点

- **缓存**：Maven/Gradle/npm 必须开缓存，否则 Android 单次构建 10 分钟起步。模板已配置 `cache: maven` / `gradle/actions/setup-gradle` / `cache: npm`。
- **并发取消**：同一 PR 连续 push 时取消旧运行（`concurrency.cancel-in-progress`），省 Actions 额度。
- **测试报告**：`if: always()` 上传报告与日志，失败时能下载排查，而不是只看到一片红。
- **覆盖率**：Phase 2 后期再上门禁（如 JaCoCo / Vitest coverage 阈值），一开始不要设阈值，先让流水线跑绿。
- **私有仓库额度**：免费额度 2000 分钟/月，Android 构建最费时间；若仓库设为 Public 则 Actions 免费不限量（学生项目建议 Public 或善用缓存）。
- **AI 生成代码的额外防线**：Phase 2 起 `apply` 流程产生的代码必须在 CI 里跑真实测试，不要只依赖「AI 说测试通过」。

---

## 4. Phase 3：CD（部署与发布）

### 4.1 需要的 Secrets / Variables 清单

在 `Settings → Secrets and variables → Actions` 配置：

| 名称 | 类型 | 用途 | 何时需要 |
| --- | --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Secret | 签名文件 base64 | Android 发布 |
| `ANDROID_KEYSTORE_PASSWORD` | Secret | keystore 密码 | Android 发布 |
| `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` | Secret | 签名别名与密码 | Android 发布 |
| `FIREBASE_APP_ID` / `FIREBASE_TOKEN` | Secret | 内测分发（测试同学装包） | Android 内测 |
| `PLAY_SERVICE_ACCOUNT_JSON` | Secret | Google Play 上架（25 美元账号） | 正式上架 |
| `SSH_HOST` / `SSH_USER` / `SSH_KEY` / `SSH_PORT` | Secret | 后端服务器部署 | 后端上线 |
| `DEPLOY_PATH` / `APP_DOMAIN` | Variable | 部署目录、健康检查域名 | 后端上线 |
| 云厂商 AK/SK、数据库连接串 | Secret | 换成云托管时使用 | 按选型 |

⚠️ **keystore 丢失 = 无法再更新已上架应用**。请把 `release.jks` 与密码同时备份到团队私密位置（密码管理器 / 加密网盘），不要只放在 GitHub Secrets 里。

### 4.2 操作步骤

1. 复制 `templates/cd-android-release.yml` → `.github/workflows/cd-android.yml`，改 `packageName`，启用 Firebase 或 Play 其中一种分发方式。
2. 复制 `templates/cd-service-deploy.yml` → `.github/workflows/cd-service.yml`，填好 `DEPLOY_PATH`、`APP_DOMAIN`，确认服务器上有 `docker compose` 与对应的 compose 文件。
3. Web 管理端二选一：
   - 静态托管（推荐起步）：Cloudflare Pages / Vercel / GitHub Pages，用它们自带的 Git 集成，CI 只需保证 `npm run build` 通过；
   - 自建：复用 `cd-service-deploy.yml` 的模式，用 Nginx 容器托管 `web/dist`。
4. 做第一次演练：先只部署到 `staging`，验证健康检查与回滚（`docker compose up -d` 指定上一个 IMAGE_TAG）后再开放 `production`。
5. 给 `production` 加人工批准，并在 PR 模板/README 里写清「谁有权点批准」。

### 4.3 发布节奏建议

- `main` 每次合并 → 自动部署 `staging`，团队随时可点开看最新效果。
- 打 tag `v0.1.0` → Android 走 `production` 审批后签名分发；后端可选同步部署 `production`。
- 每次发布在 GitHub Release 里写清对应的 OpenSpec change 列表（归档 `archive` 时的记录天然可用作 release notes 素材）。

---

## 5. 与 OpenSpec 流程的对接点

| 团队约定 | CI/CD 如何保证 |
| --- | --- |
| 先 Issue，再 change，再 PR | PR 模板强制填写 Issue 号与 change 名 |
| change 名 kebab-case、边界清晰 | `openspec validate --all --strict` 校验结构（delta、Scenario） |
| 先评审 proposal/spec/design/tasks 再写代码 | Phase 1 门禁通过 + 1 名非作者审阅；后续可加「代码 PR 必须存在对应未归档 change」的检查 |
| 每个任务关联证据、完成后勾选 | Checks 摘要显示 `completedTasks/totalTasks`；PR 模板要求勾选并附测试证据 |
| 归档前必须全部完成且测试通过 | 归档仓提交的 PR 同样跑 `validate --all --strict`；已归档 change 会进入主规格校验 |

可选增强（团队稳定后再加）：一个只读 job 检查 PR 标题/正文是否含 `openspec/changes/<name>`，缺失则标记失败。

---

## 6. 常见坑

1. **把必需检查设置得太早太多**：Phase 2 的 job 在跑通前设为必需，会让全队 PR 全红。原则：先跑绿、再加锁。
2. **忘了 `.gitignore`**：`local.properties`（含 SDK 路径）、`google-services.json`、`release.jks` 是最常见的三类误提交，本套件已拦截。
3. **keystore 提交进仓库后又删除**：Git 历史里仍在，等于泄露，必须重新生成密钥。
4. **用 `pull_request_target` 跑不可信代码**：会泄露 Secrets。本套件统一用 `pull_request`，Secrets 只在需要审批的部署 job 中使用。
5. **第三方 Action 不锁版本**：`uses: xxx@v1` 之外建议关注 Dependabot 的升级 PR；Dependabot 已配置只跟 actions，其他生态等模块落地后开启。
6. **Windows 上 `openspec` 执行策略问题**：本机用 `openspec.cmd`，但 CI 里统一用 `npx -y @fission-ai/openspec@1.14.1`，与本地版本保持一致（升级时改这一处并同步 `docs/openspec/openspec-guide.md`）。
7. **Actions 分钟数被 Android 构建烧光**：务必开 Gradle 缓存 + `concurrency` 取消，UI 测试单独用 `workflow_dispatch` 手动触发。

---

## 7. 验收清单

**Phase 1 完成标准**

- [ ] `.github/workflows/ci.yml` 已合并，`specs` / `secret-scan` / `workflow-lint` 三个 check 全绿
- [ ] `main` 分支保护已开启：需 PR、需 1 名审阅、三个 check 必需、禁止强推
- [ ] CODEOWNERS 已替换为真实账号名，并对 `/openspec/`、`/.github/` 生效
- [ ] PR 模板、Issue 模板可用；Dependabot 已开
- [ ] `.gitignore` 已覆盖 Android/Node/Java/密钥四类
- [ ] 人为制造一次错误（如提交一个空 change、提交 `local.properties`）确认 CI 能拦住，然后修正

**Phase 2 完成标准**

- [ ] 三个模块各自有 CI，改 A 模块不触发 B 模块
- [ ] 单元测试与 Lint 在 CI 中强制执行，测试报告可在 run 页面下载
- [ ] 缓存生效，单次 Android 构建 < 10 分钟
- [ ] 第一个 OpenSpec change（如 `define-pandora-mvp`）的全部 tasks 勾选且 CI 通过

**Phase 3 完成标准**

- [ ] `main` 合并后 staging 自动更新，团队成员可访问
- [ ] production 部署需人工批准，健康检查失败不会把故障版本留下
- [ ] Android 打 tag 能产出签名 AAB 并分发到测试组
- [ ] 完成一次回滚演练，并在文档中记录步骤
