# PR 检查工作流说明（模块检查）

> 本文只讲**模块检查**（后端 / Web / Android）。
> 通用门禁（规格校验、敏感文件扫描、workflow 语法检查）与分支保护配置见 [ci-gates.md](ci-gates.md)。

## 1. 触发条件

| 工作流 | Job 名（PR 页面显示） | 目标分支 | 路径过滤 | 说明 |
| --- | --- | --- | --- | --- |
| `ci-backend.yml` | `Django 检查与测试` | `dev` | `backend/**` | 只在后端代码变化时运行 |
| `ci-web.yml` | `Web 依赖安装与构建` | `dev` | `web/**` | 只在前端代码变化时运行 |
| `ci-android.yml` | `Android 测试与 Debug 编译` | `dev` | `android/**` | Gradle 构建较贵，故只在该模块变化时运行 |

三个工作流都还有一个额外触发路径：**工作流文件自身被修改时**（例如 `ci-backend.yml` 有改动），便于验证流水线本身的修改。

设计取舍：

- **只在目标分支为 `dev` 的 PR 上运行**。不改 `main`：`dev → main` 是发布 PR，改动已在 dev 阶段验证过。
- **不在 `push` 时重复运行**。合并进 dev 之后不会再把三个模块各跑一遍（Android 单次就要几分钟），需要时可在 `on:` 里加一行 `push: branches: [dev]`。
- **同时改多个模块**（例如一次 PR 同时改后端和 Web）会触发对应的多个工作流，互不影响。

## 2. 检查内容

### Django 检查与测试（`backend/`）

| 步骤 | 命令 | 作用 |
| --- | --- | --- |
| Django 自检 | `python manage.py check` | 配置、URL、模型定义的基本正确性 |
| 迁移检查 | `python manage.py makemigrations --check --dry-run` | 模型改了但**忘记生成迁移文件**时立刻失败 |
| 应用迁移 | `python manage.py migrate --noinput` | 确认迁移文件可执行（能真正建库） |
| 自动化测试 | `python manage.py test --verbosity 2` | 运行 Django 测试用例 |

环境：Python **3.11**、SQLite（`SQLITE_PATH` 指向工作区内的临时库文件），环境变量全部使用 CI 临时值。

### Web 依赖安装与构建（`web/`）

| 步骤 | 命令 | 作用 |
| --- | --- | --- |
| 安装依赖 | `npm ci` | 严格按 `package-lock.json` 安装；lock 与 `package.json` 不一致会失败 |
| 代码规范 / 类型检查 / 单元测试 | `npm run lint` 等（`--if-present`） | 目前 `web/package.json` 只有 dev/build/preview，这三步会**自动跳过**；脚本补齐后无需改工作流 |
| 构建 | `npm run build` | Vite 打包，语法错误、导入错误、缺失依赖都会在这里暴露 |

环境：Node.js **22**（要求 20.19+ 或 22.12+），npm 缓存按 `web/package-lock.json` 计算。

### Android 测试与 Debug 编译（`android/`）

| 步骤 | 命令 | 作用 |
| --- | --- | --- |
| Android Lint | `./gradlew lintDebug` | 静态检查代码与资源（未使用资源、硬编码文案、API 版本兼容、清单问题）；severity=error 的问题会导致失败 |
| 单元测试 | `./gradlew testDebugUnitTest` | 运行 `app/src/test/**` 的 JUnit 测试，纯 JVM，**不需要模拟器** |
| 编译 Debug APK | `./gradlew assembleDebug` | 编译 Kotlin、合并清单、链接资源、打包 APK |
| 上传产物 | `upload-artifact` | 测试/Lint 报告、Debug APK（测试同学可直接下载安装） |

环境：JDK **21**（AGP 9.x 需要 17+）、Gradle 缓存。注意 `android/gradlew` 在 Git 中**没有可执行位**，工作流内用 `chmod +x gradlew` 兜底。

## 3. 在 PR 页面怎么看结果

1. **Checks 区域**：PR 页面底部（或右侧 `Checks`）列出本次运行的所有检查，名称就是上表的 Job 名。灰色的表示未触发（该模块没有改动），不是因为失败。
2. **Summary**：进入某个 check 的 `Details`，页面顶部 **Summary** 里有：
   - 每一步的执行结果；
   - **本地复现命令**（工作流写入的，可直接复制到本地跑一遍）；
   - 通用门禁还会显示 strict 欠债报告与各 change 的任务进度。
3. **Artifacts**：运行页面底部的 `Artifacts` 可下载 `android-reports`、`app-debug-apk`、`web-dist`（保留 7 天）。
4. **日志**：某一步失败时展开该步骤查看完整输出；关键错误行前面通常有 `ERROR` / `FAILED` / `error:` 标记。

> 提醒：这些模块 check **不要设为必需状态检查**。它们只在对应模块有改动时才出现，设为必需会让其他 PR 永远停在 "Waiting for status to be reported"。详见 [ci-gates.md](ci-gates.md) 第 3 节。

## 4. 失败排查

### 通用三步

1. 看 Summary 里是**哪一步**失败，展开该步骤日志找第一条错误（后面的报错往往是连锁反应）。
2. 复制 Summary 里的**本地复现命令**，在本地跑一遍——能复现就按下面表格定位，不能复现再考虑环境差异。
3. 确认不是缓存/上游问题：在运行页面点 **Re-run failed jobs**。若重跑即通过，说明是偶发（网络、上游 action 升级），需要在 Issue 里记录，不要直接忽略。

### 按模块常见原因

| 模块 | 现象 | 常见原因与处理 |
| --- | --- | --- |
| Django | `manage.py check` 失败 | 设置或 URL 配置错误；本地跑同样命令看堆栈 |
| Django | `makemigrations --check` 失败 | 改了模型没生成迁移：本地执行 `python manage.py makemigrations` 并提交生成的文件 |
| Django | `migrate` 失败 | 迁移文件冲突或依赖顺序错误；检查 `apps/*/migrations/` 下是否有重名/断链 |
| Django | 测试失败 | 按失败用例定位；本地 `python manage.py test apps.xxx` 只跑相关用例更快 |
| Django | 依赖安装失败 | `requirements.txt` 版本约束冲突（当前用 `>=x,<y` 区间）；本地新建 venv 复现 |
| Web | `npm ci` 失败 | `package-lock.json` 与 `package.json` 不一致：本地执行 `npm install` 后把更新后的 lock 一起提交 |
| Web | `npm run build` 失败 | 语法/导入错误、组件路径大小写不一致（Linux 区分大小写，Windows 不区分，最常见）、缺失依赖 |
| Web | 构建成功但页面空白 | 检查 `VITE_API_BASE_URL`（构建期注入，默认 `http://127.0.0.1:8000`） |
| Android | `./gradlew` 无法执行 | 权限位问题；工作流已 `chmod +x`，本地请在 Git Bash / WSL 下执行或改用 `gradlew.bat` |
| Android | `lintDebug` 失败 | 看 `app/build/reports/lint-results-debug.html`（Artifacts 里可下载），按 severity=error 的条目修 |
| Android | `testDebugUnitTest` 失败 | 本地 `./gradlew testDebugUnitTest --tests "完整类名"` 单跑某个用例 |
| Android | `assembleDebug` 失败 | 依赖版本冲突（`gradle/libs.versions.toml`）、`compileSdk` 与 AGP 不匹配、资源文件缺失 |
| Android | 构建耗时过长 | 首次运行需下载 Gradle 发行版与依赖（3~6 分钟），有缓存后 1~2 分钟属正常；若持续超时看 `timeout-minutes: 40` |

## 5. 本地复现命令

### 后端（Django）

```bash
cd backend
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
python manage.py check
python manage.py makemigrations --check --dry-run
python manage.py migrate
python manage.py test --verbosity 2
```

### Web 管理端

```bash
cd web            # 需要 Node.js 20.19+ 或 22.12+
npm ci
npm run build
```

### Android

```bash
cd android        # 需要 JDK 17 以上（CI 使用 21）
./gradlew lintDebug testDebugUnitTest assembleDebug
```

### 通用门禁

```bash
npx -y @fission-ai/openspec@1.14.1 validate --all            # 规格校验（CI 的阻断项）
pip install shellcheck-py && actionlint -color               # workflow 语法检查（含 shellcheck）
```

## 6. 当前限制与待办

| 项 | 说明 |
| --- | --- |
| Web 无 lint / test 脚本 | `web/package.json` 只有 dev/build/preview，对应步骤自动跳过；补脚本后自动生效 |
| Android 无 UI 测试 | 未接入 `connectedDebugAndroidTest`（需要模拟器，单次 10 分钟起），按需再加 |
| 后端为 SQLite | 切到 MySQL 时需在工作流里加 `services: mysql` 与数据库环境变量 |
| `gradlew` 可执行位 | 目前靠工作流内 `chmod +x` 兜底，建议另开 chore PR 用 `git update-index --chmod=+x android/gradlew` 根治 |
| 无覆盖率门槛 | 待测试用例积累后再评估（后端 coverage / 前端 vitest / Android JaCoCo） |
