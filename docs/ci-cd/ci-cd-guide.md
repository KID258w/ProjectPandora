# CI/CD 现状与操作指南

> 分支模型见 [docs/git/git-workflow.md](../git/git-workflow.md)：
> `feature/<Issue编号>-<功能>` → PR → `dev` → PR → `main`

---

## 1. 现状一览

### 已建成（本轮提交）

| 文件 | 作用 |
| --- | --- |
| `.github/workflows/ci.yml` | 3 个 job：`规格校验 (OpenSpec)`、`敏感文件扫描`、`Workflow 语法检查`；PR 进 `dev`/`main` 以及向这两个分支推送时触发 |
| `.github/workflows/ci-android.yml` | `demo/**` 有改动时跑 Android：Lint + 单元测试 + 构建 Debug APK，并把报告/APK 作为产物上传 |
| `.github/PULL_REQUEST_TEMPLATE.md` | 新建 PR 自动带出检查清单（Issue、change、目标分支、密钥、不直推 dev/main） |
| `.github/ISSUE_TEMPLATE/{story,bug}.yml` | 建 Issue 时可选「用户故事 / 缺陷」表单 |
| `.github/CODEOWNERS` | 自动请求审阅人（当前统一为仓库建立者） |
| `.github/dependabot.yml` | 每周检查 GitHub Actions 版本并自动开 PR |
| `.gitignore` | 覆盖 IDE、密钥、Android/Gradle、Python/Django、Node/Vue |

### 仍需在网页上配置（不配则 CI 只是"提示"）

1. **分支保护 ruleset —— 需要给 `dev` 和 `main` 各配一条**（这是本项目最容易漏的一步：功能 PR 都进 `dev`，只保护 `main` 等于没保护）
2. `Settings → Actions → General`：Workflow permissions 改为 `Read repository contents`，并允许 Actions 创建 PR
3. `Settings → Collaborators`：由仓库建立者把成员加为协作者（write 权限），成员需到 `https://github.com/KID258w/ProjectPandora/invitations` 接受邀请，否则 CODEOWNERS 静默失效
4. `Settings → Code security`：开启 **Secret scanning + Push protection**（public 仓库免费）——补上"源码里硬编码 Token/口令"这个盲区
5. 顺带修一次：`demo/.idea/` 下 11 个 IDE 文件已被提交进仓库

```bash
# 清理已入库的 IDE 配置（单独开一个 chore PR 执行）
git rm -r --cached demo/.idea
git commit -m "chore: 停止跟踪 demo/.idea 下的 IDE 配置"
```

---

## 2. 每个门禁拦什么

| 门禁 | 能拦 | 拦不住 |
| --- | --- | --- |
| **规格校验** | change 缺少 `specs/` delta；`### Requirement:` 下没有 `#### Scenario:`；主规格缺 `## Purpose`/`## Requirements` | 目录名是否 kebab-case；`tasks.md` 是否勾选完；需求写成什么样 |
| **敏感文件扫描** | 按文件名：`*.jks/keystore/p12/p8/pem/key/mobileprovision`、`.env` 及 `.env.local/.env.production` 等变体、`local.properties`、`google-services.json`、`credentials.json`、`id_rsa*` | 源码里硬编码的密钥（靠原生 Secret scanning） |
| **Workflow 语法检查** | workflow 的 YAML/表达式错误、action 输入名拼错、`needs` 指向不存在的 job | 触发条件写错但语法合法、secrets 名字写错 |
| **Android CI** | 编译错误、Lint 报错、单元测试失败 | 真机/模拟器上的 UI 行为（需要另加 instrumentation 测试） |

⚠️ **`--strict` 会把 WARNING 也算失败**。这是"格式看着对但 CI 红"的常见原因，遇到先找输出里的 `[WARNING]`。

---

## 3. 网页配置步骤

### 3.1 给 dev 和 main 各建一条 ruleset

`Settings → Rules → Rulesets → New branch ruleset`

| 项 | dev | main |
| --- | --- | --- |
| Target branches | `dev` | `main` |
| Require a pull request before merging | ✅ approvals 1 | ✅ approvals 1 |
| └ Dismiss stale approvals | ✅ | ✅ |
| └ Require conversation resolution | ✅ | ✅ |
| Require status checks to pass | ✅ 勾下面几个 check | ✅ 同左 |
| Block force pushes | ✅ | ✅ |
| Restrict deletions | ✅ | ✅ |
| Require linear history | 可选 | 可选 |

必需 check 名称（**必须完全一致**，含空格和中文括号）：

```
规格校验 (OpenSpec)
敏感文件扫描
Workflow 语法检查
Android 静态检查与测试      ← 只在改了 demo/** 时才出现，不要设为必需，否则后端/Web PR 永远缺这个 check
```

> ⚠️ 不要勾 `Require review from Code Owners`：CODEOWNERS 里目前每行只有 1 个 owner，作者自己的 PR 会永久卡在 "Review required"。要给每人配到 2 个 owner 之后再用。

> 顺序：**先让 CI 在 `dev` 上跑过一次**（check 名字才会出现在候选列表里），再去配 ruleset。

### 3.2 其它设置

| 位置 | 操作 |
| --- | --- |
| Settings → Actions → General | Workflow permissions 选 `Read repository contents and packages permissions`；勾 `Allow GitHub Actions to create and approve pull requests`（Dependabot 开 PR 需要） |
| Settings → Collaborators | 添加成员（write 权限）→ 成员到 `/invitations` 接受 |
| Settings → Code security | 开启 Secret scanning 与 Push protection |
| Settings → Rules → New tag ruleset | target `v*`，禁止删除与强推（发布打版本号时用） |

---

## 4. 本地开发命令

```powershell
# 0) 版本必须与 CI 一致（CI 固定 1.14.1）
npm i -g @fission-ai/openspec@1.14.1
openspec --version          # 注意是 --version，不是 -version

# 1) 提交前校验规格（CI 里跑的就是这条）
openspec validate --all                # 阻断项：ERROR
openspec validate --all --strict       # 含 WARNING，用来检查欠债

# 2) 查看 change 进度
openspec list
openspec status --change <change-name>

# 3) 进 Android 目录本地跑一遍（与 ci-android.yml 相同的命令）
cd demo
./gradlew lintDebug testDebugUnitTest assembleDebug
```

---

## 5. 已知欠债：strict 模式

`dev` 当前有 **10 条 WARNING**，全部是同一类：

```
Requirement text is very long (>500 characters).
Move examples and edge cases into scenarios, or split it into separate
requirements that each state one behavior.
```

分布在 4 个 change：

| change | 条数 |
| --- | --- |
| `task-dispatch-and-acceptance` | 4 |
| `month-week-day-work-views` | 4 |
| `home-dashboard-panels` | 1 |
| `cross-department-coordination` | 1 |

**当前处理方式**：CI 的阻断步骤用 `validate --all`（只拦 ERROR），strict 结果作为不阻断的"欠债报告"写在 PR 的 Checks Summary 里。把 requirement 拆短（把例子和边界情况挪进 Scenario）之后，改成 `--strict` 并删掉那个报告步骤即可。

> 注意版本差异：本地 1.13.2 **不会**报这 10 条 WARNING（这条规则是 1.14 新增的）。本地不升级就看不到 CI 在说什么。

---

## 6. 后续计划

| 阶段 | 内容 | 触发条件 |
| --- | --- | --- |
| Phase 2 | 后端 CI（Django REST Framework：`pytest` + MySQL service 容器 + `manage.py check`）、Web CI（Vue 3：`npm ci` + ESLint + Vitest + `vite build`） | 对应目录出现第一批代码 |
| Phase 2 | `change` 命名检查、`tasks.md` 完成度门禁、conventional commit 检查 | 现有三条门禁稳定运行 2 周后 |
| Phase 3 | 镜像构建与推送、staging 自动部署、production 人工审批、Android 签名发布（keystore 走 Actions Secrets） | 有可部署环境与发布需求 |

Phase 3 需要提前准备的 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`、`SSH_HOST`/`SSH_USER`/`SSH_KEY`、云厂商 AK/SK。
⚠️ keystore 与密码务必单独备份（密码管理器/加密网盘）——丢了就无法更新已上架应用。

---

## 7. 验收清单

- [ ] `dev` 上出现第一条 PR，能看到 `规格校验 (OpenSpec)`、`敏感文件扫描`、`Workflow 语法检查` 三个 check
- [ ] 改 `demo/**` 的 PR 额外出现 `Android 静态检查与测试`，且能下载到测试报告和 APK
- [ ] `dev`、`main` 两条 ruleset 已生效：直推被拒、未获批准时 Merge 按钮为灰
- [ ] 故意提交一个 `.env.local` 或 `local.properties`，确认被拦（然后撤回）
- [ ] 故意建一个只有 `proposal.md` 的 change，确认被拦（然后撤回）
- [ ] 开启 Secret scanning + Push protection
- [ ] `demo/.idea/` 已停止跟踪
