# PR 检查工作流介绍文档

## 1. 文档元信息

| 字段 | 内容 |
| :--- | :--- |
| 功能名称 | CI/CD-拉取请求（PR）自动检查工作流 |
| 文档版本 | V1.0 |
| 编写人/更新人 | WSX24 |
| 更新日期 | 2026-10-16 |
| 适用产品版本 | Project Pandora 仓库 `dev` 分支（GitHub Actions） |
| 受众 | 开发人员、评审人、CI/CD 负责人、测试人员 |

## 2. 功能概述

PR 自动检查工作流是本项目在 GitHub 上配置的持续集成能力，在目标分支为 `dev` 的 Pull Request 提交后自动运行三条模块检查（Django 检查与测试、Web 依赖安装与构建、Android 测试与 Debug 编译），并把执行结果与本地复现命令展示在 PR 页面，使缺陷在合并前被发现。

## 3. 业务背景与功能价值

### 3.1 业务痛点

1. 工作流上线前，仓库没有任何自动化检查：改动能合入 `dev` 的唯一依据是提交者本人的自述，编译错误、漏写数据库迁移、前端构建失败等问题只有等别人拉取代码时才会暴露。
2. 三个模块（后端、Web、Android）的本地环境各不相同，评审人无法凭改动内容判断"这份代码在别人机器上能否跑通"。
3. 密钥类文件（`local.properties`、`.env`、keystore）被误提交时没有任何提示。

### 3.2 目标用户

- 提交代码的开发人员：合并前自查
- 评审人（PR Reviewer）：合并前获取客观依据
- 测试人员：从 PR 运行结果中下载 Debug APK 直接安装验证
- CI/CD 负责人：维护流水线并排查失败

### 3.3 功能价值

1. **缺陷左移**：编译错误、迁移缺失、构建失败在 PR 阶段暴露，不进入 `dev` 分支。
2. **可复现**：每个检查的结论都附带可在本地原样复现的命令，避免"只有 CI 会红"。
3. **可追溯**：每次运行的结果、产物、耗时均留存在 Actions 运行记录中，作为阶段交付的过程证据。
4. **省时**：模块检查按路径过滤，改文档或改后端不会触发一次数分钟的 Android 构建。

## 4. 功能详细说明

### 4.1 功能入口与展示位置

1. **入口**：开发人员向 `dev` 分支提交 PR 后自动触发，无需手动操作。
2. **展示位置**：PR 页面的 `Checks` 区域列出本次运行的全部检查及状态；点击任一检查进入运行详情页，页面顶部 `Summary` 区域展示执行摘要与本地复现命令。
3. **默认状态**：未改动对应模块时，该模块检查不出现（而非显示为失败）。

### 4.2 检查项清单

| 检查名称（PR 页面显示） | 触发路径 | 检查内容 |
| :--- | :--- | :--- |
| Django 检查与测试 | `backend/**` | 依赖安装、`manage.py check`、`makemigrations --check`、`migrate`、`manage.py test` |
| Web 依赖安装与构建 | `web/**` | `npm ci`、`npm run build`（lint/typecheck/test 脚本存在时一并执行） |
| Android 测试与 Debug 编译 | `android/**` | `lintDebug`、`testDebugUnitTest`、`assembleDebug` |

### 4.3 各检查的执行明细

#### 4.3.1 Django 检查与测试

| 步骤 | 执行内容 | 拦截的问题 |
| :--- | :--- | :--- |
| 安装依赖 | `pip install -r requirements.txt` | 依赖版本约束冲突 |
| Django 自检 | `python manage.py check` | 配置、URL、模型定义错误 |
| 迁移检查 | `python manage.py makemigrations --check --dry-run` | 修改模型但未生成迁移文件 |
| 应用迁移 | `python manage.py migrate --noinput` | 迁移文件不可执行、依赖顺序错误 |
| 自动化测试 | `python manage.py test --verbosity 2` | 测试用例失败 |

运行环境：Python 3.11，数据库 SQLite（环境变量使用 CI 临时值，不读取任何真实密钥）。

#### 4.3.2 Web 依赖安装与构建

| 步骤 | 执行内容 | 拦截的问题 |
| :--- | :--- | :--- |
| 安装依赖 | `npm ci` | `package-lock.json` 与 `package.json` 不一致 |
| 代码规范/类型检查/单元测试 | `npm run lint/typecheck/test --if-present` | 脚本存在时执行；当前尚未定义，自动跳过 |
| 构建 | `npm run build` | 语法错误、导入路径大小写错误、资源缺失、依赖缺失 |

运行环境：Node.js 22，npm 缓存按 `web/package-lock.json` 计算。

#### 4.3.3 Android 测试与 Debug 编译

| 步骤 | 执行内容 | 拦截的问题 |
| :--- | :--- | :--- |
| Android Lint | `./gradlew lintDebug` | 未使用资源、硬编码文案、权限声明、API 版本兼容、清单问题（error 级导致失败） |
| 单元测试 | `./gradlew testDebugUnitTest` | `app/src/test` 下 JUnit 用例失败 |
| 编译 Debug APK | `./gradlew assembleDebug` | Kotlin 编译错误、依赖版本冲突、清单合并失败、资源链接失败 |

运行环境：JDK 21（AGP 9.x 要求 17 以上），Gradle 依赖缓存。

### 4.4 产出物

| 产出物 | 来源检查 | 保留期 | 用途 |
| :--- | :--- | :--- | :--- |
| `android-reports` | Android 测试与 Debug 编译 | 7 天 | 测试与 Lint 报告，失败时定位 |
| `app-debug-apk` | Android 测试与 Debug 编译 | 7 天 | 测试人员直接下载安装 |
| `web-dist` | Web 依赖安装与构建 | 7 天 | 前端构建产物校验 |

## 5. 操作流程与典型场景

### 5.1 主流程

1. 开发人员在功能分支完成修改并推送到 GitHub
2. 创建 PR，目标分支选择 `dev`
3. 系统按改动路径自动触发对应检查，PR 页面显示运行状态
4. 检查全部完成后，在 `Checks` 区域查看结果；失败时进入 `Summary` 复制本地复现命令
5. 本地修复后推送新提交，检查自动重跑；同一分支的旧运行会被自动取消
6. 全部检查通过且获得评审批准后，由有权限的成员合并

### 5.2 典型场景

#### 5.2.1 场景一：后端开发提交接口改动，自查是否漏写迁移

1. 修改 `backend/apps/accounts/models.py` 后推送并创建 PR
2. `Django 检查与测试` 自动运行
3. `检查是否有漏写的 migration` 步骤失败，日志提示模型改动未生成迁移
4. 本地执行 `python manage.py makemigrations` 并提交生成的文件
5. 推送后检查重跑并通过

#### 5.2.2 场景二：测试人员获取安卓安装包验证

1. 开发人员提交 Android 改动并创建 PR
2. `Android 测试与 Debug 编译` 通过后，在运行页面底部 `Artifacts` 下载 `app-debug-apk`
3. 安装到测试机验证
4. 发现问题后在 PR 中反馈

#### 5.2.3 场景三：评审人评估改动可否合并

1. 打开 PR，查看 `Checks` 区域的检查结论
2. 若失败，点开 `Summary` 查看失败步骤与本地复现命令，判断是代码问题还是环境问题
3. 检查通过后再进行代码评审

## 6. 业务规则与约束条件

### 6.1 基础规则

1. 触发事件为 `pull_request`，目标分支限定为 `dev`；向 `main` 分支发起的 PR 不触发模块检查。
2. 模块检查采用路径过滤：改动命中 `backend/**`、`web/**`、`android/**` 时分别触发对应检查。
3. 各工作流的定义文件自身被修改时，也会触发该检查，便于验证流水线改动。
4. 同一分支连续推送时，仅保留最新一次运行，旧运行自动取消。

### 6.2 限制规则

1. 模块检查**不在 `push` 事件上运行**：合并进 `dev` 后不会重复执行，需要时可在工作流中显式增加 `push` 触发。
2. 模块检查**不得配置为分支保护的必需检查**：因其按路径触发，并非每个 PR 都会产生，配置为必需会导致其他 PR 长期停留在等待状态。
3. 各检查超时限制：Django 与 Web 为 20 分钟，Android 为 40 分钟。
4. 产物保留期为 7 天。
5. GitHub Actions 版本固定：Workflow 语法检查使用的 actionlint 镜像固定在 `1.7.12`。

### 6.3 计算规则

1. 触发判定：`分支匹配 AND 路径匹配` 同时成立才运行。
2. 状态判定：Python 3.11 对应后端环境；Node.js 22 对应前端环境；JDK 21 对应 Android 环境；三者均由配置文件中的实际版本要求推导得出。

## 7. 异常与边界处理

### 7.1 操作异常

| 触发条件 | 系统表现 |
| :--- | :--- |
| 一次 PR 同时改动多个模块 | 对应多个检查并行运行，互不影响 |
| 只改动文档或 `.github/` 配置 | 仅触发通用门禁，不触发任何模块检查 |
| 连续推送多个提交 | 旧运行自动取消，仅最新提交的检查结果有效 |

### 7.2 各检查失败时的排查方式

| 检查 | 失败步骤 | 常见原因与处理 |
| :--- | :--- | :--- |
| Django | `check` | 设置或 URL 配置错误，本地执行同一命令查看堆栈 |
| Django | `makemigrations --check` | 改了模型未生成迁移，本地执行 `makemigrations` 并提交文件 |
| Django | `migrate` | 迁移文件冲突或依赖顺序错误 |
| Django | `test` | 按失败用例定位；可只跑相关用例 `python manage.py test apps.xxx` |
| Django | 安装依赖 | `requirements.txt` 版本区间冲突，本地新建虚拟环境复现 |
| Web | `npm ci` | lock 文件与 `package.json` 不一致，本地执行 `npm install` 后提交更新后的 lock |
| Web | `build` | 语法/导入错误、路径大小写不一致（Linux 区分大小写）、依赖缺失 |
| Android | `lintDebug` | 下载 `android-reports` 查看 Lint 报告，处理 error 级条目 |
| Android | `testDebugUnitTest` | 本地单跑用例：`./gradlew testDebugUnitTest --tests "完整类名"` |
| Android | `assembleDebug` | 依赖版本冲突（`gradle/libs.versions.toml`）、`compileSdk` 与 AGP 不匹配、资源缺失 |

### 7.3 系统异常

1. **首次运行耗时长**：Android 首次需下载 Gradle 发行版与依赖，耗时 3~6 分钟；有缓存后为 1~2 分钟。
2. **缺少 SDK platform**：若报缺 `compileSdk` 对应的平台包，可在工作流中启用 `sdkmanager` 安装步骤。
3. **权限位问题**：`android/gradlew` 在 Git 中模式为 `100644`，工作流内以 `chmod +x` 兜底。
4. **偶发失败**：可在运行页面执行 `Re-run failed jobs` 重跑；若重跑即通过，应在 Issue 中记录，不可直接忽略。

## 8. 权限说明

| 角色 | 查看检查结果 | 修改工作流 | 合并 PR | 配置分支保护 |
| :--- | :--- | :--- | :--- | :--- |
| 仓库管理员（Owner） | 可 | 可 | 可 | 可 |
| 协作成员（Write） | 可 | 可（需 CODEOWNERS 评审） | 需 1 名非作者批准 | 不可 |
| 只读成员（Read） | 可 | 不可 | 不可 | 不可 |

补充说明：

1. 检查结果对所有拥有仓库读权限的人可见；运行日志与产物同样可查看、下载。
2. 工作流文件位于 `.github/workflows/`，按 CODEOWNERS 配置需指定负责人复核。
3. 分支保护规则需仓库管理员在 `Settings → Rules` 配置，当前项目要求为目标分支必须通过 PR 且获得至少 1 名非作者批准。

## 9. 关联功能与依赖

### 9.1 前置依赖

1. 仓库已在 GitHub 上启用 Actions 功能。
2. 默认分支与 `dev` 分支存在，且分支模型为 `feature/* → dev → main`。
3. `android/gradlew`、`backend/requirements.txt`、`web/package-lock.json` 等构建入口文件存在。

### 9.2 下游关联

1. 仓库通用门禁（规格校验、敏感文件扫描、Workflow 语法检查）：与模块检查同属 PR 检查体系，配置为分支保护的必需检查。
2. 分支保护规则（Ruleset）：依赖检查结果作为合并前置条件。
3. Pull Request 模板：要求提交者在合并前确认本地校验结果。

### 9.3 系统依赖

| 依赖 | 用途 |
| :--- | :--- |
| GitHub Actions 托管运行器（ubuntu-latest） | 提供执行环境，内置 Docker、Python、Node.js、JDK 等 |
| `actions/checkout`、`actions/setup-python`、`actions/setup-node`、`actions/setup-java`、`gradle/actions/setup-gradle`、`actions/upload-artifact` | 检出代码、安装运行环境、缓存依赖、上传产物 |
| Python 3.11 + Django 5.2 依赖树 | 后端检查与测试 |
| Node.js 22 + npm | 前端依赖安装与构建 |
| JDK 21 + Gradle 9.5 + AGP 9.x | Android 检查与编译 |

## 10. 附录

### 10.1 本地复现命令

```bash
# 后端（Django）
cd backend
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
python manage.py check
python manage.py makemigrations --check --dry-run
python manage.py migrate
python manage.py test --verbosity 2

# Web 管理端（需要 Node.js 20.19+ 或 22.12+）
cd web
npm ci
npm run build

# Android（需要 JDK 17 以上，CI 使用 21）
cd android
./gradlew lintDebug testDebugUnitTest assembleDebug
```

### 10.2 术语表

| 术语 | 说明 |
| :--- | :--- |
| PR（Pull Request） | GitHub 上的合并请求 |
| 路径过滤（paths） | 只有改动命中指定目录时才触发的触发条件 |
| 必需检查（Required status check） | 分支保护中配置的、未通过则禁止合并的检查项 |
| 产物（Artifact） | 检查过程中生成的可下载文件，如测试报告、APK |
| Strict 模式 | OpenSpec 校验模式，此模式下 WARNING 与 ERROR 同等对待 |

### 10.3 变更记录

| 版本 | 日期 | 修改人 | 修改内容 |
| :--- | :--- | :--- | :--- |
| V1.0 | 2026-10-16 | WSX24 | 首版：三条模块检查上线，补充触发条件、失败排查与本地复现命令；按文档规范重排结构 |

### 10.4 参考文档

1. `docs/cicd/ci-gates.md`：门禁清单与分支保护（Ruleset）配置步骤
2. `docs/git/git-workflow.md`：团队分支与 PR 协作流程
3. `docs/openspec/openspec-guide.md`：需求与规格记录流程

### 10.5 常见问题（FAQ）

**Q1：为什么我的 PR 上没有出现 Android 检查？**
A：本次改动未命中 `android/**` 路径，属正常现象，不代表检查失败。

**Q2：检查通过就一定能合并吗？**
A：不能。还需满足分支保护要求（至少 1 名非作者批准）以及仓库通用门禁通过。

**Q3：检查失败了但我本地一切正常，怎么办？**
A：先看失败步骤属于哪一类；（1）环境差异（Node/JDK/Python 版本）——对照第 4.3 节核对版本；（2）路径大小写差异——Linux 区分大小写，本地不区分；（3）构建缓存问题——在运行页面执行 `Re-run failed jobs` 重跑确认。

**Q4：为什么模块检查不能设为必需检查？**
A：因其按路径触发，并非每个 PR 都会产生该检查，设为必需会让其他 PR 长期处于等待状态。详见第 6.2 节。
