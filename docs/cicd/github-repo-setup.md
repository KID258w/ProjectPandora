# 仓库配置完整步骤

全部在 `https://github.com/KID258w/ProjectPandora` 仓库里操作。**需要 Admin 权限**（Settings 标签只对管理员可见；如果你看不到 Settings，说明权限不够）。

---

## 第 1 步：进配置页

浏览器打开：

```
https://github.com/KID258w/ProjectPandora/settings/secrets/actions
```

或者点：仓库页 → **Settings** → 左侧 **Secrets and variables** → **Actions**

页面上有两个标签：**Secrets** 和 **Variables**。两者区别很重要：

| | Secrets | Variables |
|---|---|---|
| 存储 | 加密，写入后不可再查看 | 明文，随时可见 |
| 日志 | 自动打码成 `***` | 原样显示 |
| 用来放 | 私钥、Token、密码 | 路径、端口、域名 |

**放错地方 = 泄露**。私钥必须放 Secrets。

---

## 第 2 步：加 3 个 Secret

在 **Secrets** 标签页，点右上角 **New repository secret**，逐个添加：

### Secret 1

| 字段 | 值 |
|---|---|
| Name | `SSH_HOST` |
| Secret | `47.118.21.150` |

点 **Add secret**

### Secret 2

| 字段 | 值 |
|---|---|
| Name | `SSH_USER` |
| Secret | `root` |

点 **Add secret**

### Secret 3（最关键）

先在 PowerShell 里复制私钥：

```powershell
Get-Content "$HOME\.ssh\pandora_deploy" -Raw | Set-Clipboard
```

然后：

| 字段 | 值 |
|---|---|
| Name | `SSH_KEY` |
| Secret | 粘贴（Ctrl+V） |

**必须确认的 3 点**：

1. 文件是 `pandora_deploy`（419 字节），**不是** `pandora_deploy.pub`（112 字节）
2. 首行是 `-----BEGIN OPENSSH PRIVATE KEY-----`
3. 末行是 `-----END OPENSSH PRIVATE KEY-----`

贴错的表现：

| 症状 | 原因 |
|---|---|
| `error in libcrypto` | 缺 BEGIN/END 行，或粘贴被截断 |
| `invalid format` | 粘成了公钥 |
| `Permission denied (publickey)` | 粘的是另一把密钥（比如 `id_ed25519_mat`） |

---

## 第 3 步：加 1 个 Variable

切到 **Variables** 标签页，点 **New repository variable**：

| 字段 | 值 |
|---|---|
| Name | `APP_URL` |
| Value | `https://pandora.xinphotoshare.click` |

作用：部署成功后，Actions 页面会显示可点击的访问链接；`remote-deploy.sh` 还会用它验证一次公网入口。

> 另外 3 个变量 `DEPLOY_PATH`、`PANDORA_DATA_DIR`、`WEB_PORT` **不用加**，工作流里已有默认值 `/opt/pandora`、`/var/lib/pandora`、`8080`。

---

## 第 4 步：给 `production` 环境加人工审批（强烈建议）

```
https://github.com/KID258w/ProjectPandora/settings/environments
```

1. 点 **New environment**，名字填 **`production`**（必须完全一致，工作流里写的就是这个名字）
2. 创建后勾选 **Required reviewers**
3. 在搜索框里输入你自己的 GitHub 账号 → 选中 → **Save protection rules**

效果：以后每次部署，Actions 会先停下来等你点 **Approve**，防止误合 `main` 直接上生产。

---

## 第 5 步：保护 `main` 分支（强烈建议）

```
https://github.com/KID258w/ProjectPandora/settings/rules
```

点 **New ruleset** → **New branch ruleset**：

| 配置项 | 值 |
|---|---|
| Ruleset Name | `protect-main` |
| Enforcement status | **Active** |
| Target branches → Add target | **Include by pattern** → `main` |

勾选：

- ✅ **Require a pull request before merging**（Approvals 设 1）
- ✅ **Block force pushes**

点 **Create**

效果：不能直接推 `main`，必须走 PR。这正好补上 Issue 里 `add-ci-cd-gates/tasks.md` 第 5 节一直没勾的那条。

> 注意：这条开了之后，以后发布要从 `dev` 开 PR 到 `main`，不能再本地直接 `git push origin main`。

---

## 第 6 步：验证配置是否生效

**配置本身无法直接"测试"**，只能通过跑一次工作流来验。所以顺序是：

```
你配完 → 我推分支 → 你点 Run workflow → 看结果
```

配置页面上能看到 Secret 的"Updated"时间戳，说明写进去了：

```
SSH_HOST      Updated just now
SSH_USER      Updated just now
SSH_KEY       Updated just now
```

如果名字拼错（比如 `SSH-HOST` 用了连字符），工作流会报 `secret not found`，所以要**严格按上表大小写和下划线**。

---

## 完整清单（配完自查）

| # | 位置 | 名称 | 值 | 必填 |
|---|---|---|---|---|
| 1 | Secrets | `SSH_HOST` | `47.118.21.150` | ✅ 必须 |
| 2 | Secrets | `SSH_USER` | `root` | ✅ 必须 |
| 3 | Secrets | `SSH_KEY` | 私钥全文 | ✅ 必须 |
| 4 | Variables | `APP_URL` | `https://pandora.xinphotoshare.click` | ✅ 建议 |
| 5 | Environments | `production` + Required reviewers | 你自己 | ⭐ 建议 |
| 6 | Rules | `protect-main` | PR + 禁止强推 | ⭐ 建议 |
