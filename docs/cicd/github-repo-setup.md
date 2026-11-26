# GitHub 仓库配置手册（自动部署所需）

本文说明让 `main` 自动部署跑起来，需要在 **GitHub 仓库侧**完成的一次性配置：
Secrets、Variables、Environment 与分支保护规则。

- 服务器侧准备（Docker、数据目录、宿主 Nginx 反向代理）见 [server-deploy.md](server-deploy.md)。
- 工作流设计与触发规则见 [cicd-guide.md](cicd-guide.md)。

## 0. 前提

| 项 | 要求 |
| --- | --- |
| 仓库权限 | **Admin**（否则看不到 `Settings` 标签） |
| 服务器 | 已装 Docker + Compose，SSH 可用密钥登录（见第 1 节） |
| 部署分支 | 已推送，含 `.github/workflows/cd-deploy.yml` |

> 配置本身无法单独"测试"，只能通过触发一次工作流验证。因此顺序是：
> **配完本节 → 推送分支 → 手动 Run workflow → 看结果**。

## 1. 前置：准备部署密钥

GitHub Actions 以**客户端**身份通过 SSH 连接服务器，因此需要一对密钥：

| 文件 | 比喻 | 放在哪 |
| --- | --- | --- |
| 公钥 `*.pub` | 锁 | 服务器 `~/.ssh/authorized_keys` |
| 私钥 | 钥匙 | GitHub Secret `SSH_KEY` |

公钥只能验证签名、不能签名，所以把公钥放进 Secret 是无效的。

### 1.1 生成专用密钥（本地 PowerShell）

```powershell
ssh-keygen -t ed25519 -C "github-actions-pandora-deploy" -f "$HOME\.ssh\pandora_deploy" -N '""'
```

刻意使用**专用密钥**而不是复用个人密钥：可随时单独吊销，不影响其他用途。

### 1.2 授权到服务器

把公钥内容追加到服务器（在服务器终端或阿里云控制台执行）：

```bash
mkdir -p ~/.ssh && chmod 700 ~/.ssh
cat >> ~/.ssh/authorized_keys <<'KEY'
把 ~/.ssh/pandora_deploy.pub 的整行内容粘在这里
KEY
chmod 600 ~/.ssh/authorized_keys
```

> 用 `<<'KEY'` heredoc 而不是 `echo '...'`：粘贴内容不会被 shell 解析，
> 可避免中文标点、引号截断导致写错文件（例如生成 `authorized_keys、` 这种无效文件）。

### 1.3 验证

**服务器侧**校验公钥格式（比 `cat` 可靠，能发现隐藏字符）：

```bash
ssh-keygen -lf ~/.ssh/authorized_keys
# 期望：256 SHA256:xxxx... github-actions-pandora-deploy (ED25519)
```

**本地侧**校验私钥可用：

```powershell
ssh -i "$HOME\.ssh\pandora_deploy" -o IdentitiesOnly=yes root@<服务器IP> "echo SSH_OK; docker --version"
```

> Windows 下若报 `Permissions for 'pandora_deploy' are too open`，
> 用 `icacls "$HOME\.ssh\pandora_deploy" /inheritance:r /grant:r "$($env:USERNAME):(R)"` 收紧权限。

## 2. 配置清单总览

| # | 位置 | 名称 | 值 | 必填 |
| --- | --- | --- | --- | --- |
| 1 | Secrets | `SSH_HOST` | 服务器公网 IP | ✅ |
| 2 | Secrets | `SSH_USER` | 登录用户名 | ✅ |
| 3 | Secrets | `SSH_KEY` | 私钥全文 | ✅ |
| 4 | Secrets | `SSH_PORT` | 非 22 时填写 | 可选 |
| 5 | Secrets | `GHCR_PULL_USER` / `GHCR_PULL_TOKEN` | 镜像包为私有时填写 | 可选 |
| 6 | Variables | `APP_URL` | `https://<域名>` | 建议 |
| 7 | Variables | `DEPLOY_PATH` | 默认 `/opt/pandora` | 可选 |
| 8 | Variables | `PANDORA_DATA_DIR` | 默认 `/var/lib/pandora` | 可选 |
| 9 | Variables | `WEB_PORT` | 默认 `8080`（仅绑 127.0.0.1） | 可选 |
| 10 | Variables | `VITE_API_BASE_URL` | 留空 = 同域相对路径 | 可选 |
| 11 | Environments | `production` + Required reviewers | 部署前人工批准 | 建议 |
| 12 | Rules | `protect-main` | PR + 禁止强推 | 建议 |

第 7～10 项在工作流里已有默认值，**不填也能跑**；只有需要改默认行为时才加。

## 3. Secrets 与 Variables 的区别（放错即泄露）

| | Secrets | Variables |
| --- | --- | --- |
| 存储 | 加密，写入后不可再查看 | 明文，随时可见 |
| 日志 | 自动打码为 `***` | 原样显示 |
| 放什么 | 私钥、Token、密码 | 路径、端口、域名 |

私钥、访问令牌**只能**放 Secrets。Variables 的内容会出现在工作流日志里。

## 4. 操作步骤

### 4.1 添加 Secrets

打开 `https://github.com/<owner>/<repo>/settings/secrets/actions`，
或在仓库页点击 **Settings → Secrets and variables → Actions → Secrets** 标签页。

逐个点 **New repository secret**：

| Name | Secret |
| --- | --- |
| `SSH_HOST` | 服务器公网 IP |
| `SSH_USER` | 登录用户名（如 `root`） |
| `SSH_KEY` | 私钥文件**全部内容** |

复制私钥到剪贴板：

```powershell
Get-Content "$HOME\.ssh\pandora_deploy" -Raw | Set-Clipboard
```

粘贴到 Secret 时确认三点：

1. 复制的是私钥（无 `.pub` 后缀，约 400 字节），不是公钥；
2. 首行是 `-----BEGIN OPENSSH PRIVATE KEY-----`；
3. 末行是 `-----END OPENSSH PRIVATE KEY-----`。

### 4.2 添加 Variables

同一页面切到 **Variables** 标签页 → **New repository variable**：

| Name | Value |
| --- | --- |
| `APP_URL` | `https://<域名>` |

作用：部署成功后 Actions 页面显示可点击的访问地址，`remote-deploy.sh` 用它再验一次公网入口。

### 4.3 配置 Environment（部署前人工审批）

打开 `https://github.com/<owner>/<repo>/settings/environments`：

1. **New environment**，名称必须为 **`production`**（与 `cd-deploy.yml` 的 `environment.name` 一致）；
2. 勾选 **Required reviewers**，加入需要审批的账号；
3. **Save protection rules**。

效果：每次部署先暂停等待 Approve，避免误合 `main` 立即上生产。

### 4.4 保护 `main` 分支

打开 `https://github.com/<owner>/<repo>/settings/rules` → **New ruleset → New branch ruleset**：

| 配置 | 值 |
| --- | --- |
| Ruleset Name | `protect-main` |
| Enforcement status | `Active` |
| Target branches | Include by pattern → `main` |

勾选：

- **Require a pull request before merging**（Approvals 建议 1）
- **Block force pushes**

效果：`main` 只能通过 PR 合入。**启用后不能再本地 `git push origin main`**，
发布流程改为 `dev` → PR → `main`。

## 5. 验证

配置完成后：

1. 推送含 `cd-deploy.yml` 的分支；
2. 打开 **Actions → CD Deploy → Run workflow**，选择该分支；
3. 观察两个 job：`构建并推送镜像` → `部署到服务器`。

首次建议验证以下几点：

| 观察点 | 期望 |
| --- | --- |
| `构建并推送镜像` | 两个镜像均 push 成功，标签为 commit sha 前 12 位 |
| `部署到服务器` | 上传 Compose 与脚本、SSH 执行、健康检查全部成功 |
| Step Summary | 显示镜像标签、访问地址与回滚命令 |
| `APP_URL` 公网检查 | 输出「公网入口正常」，否则提示检查宿主 Nginx |

## 6. 排错对照表

| 报错 | 原因 | 处理 |
| --- | --- | --- |
| `secret not found` / 变量为空 | 名称拼错（大小写、连字符、下划线） | 严格按第 2 节名称重建 |
| `error in libcrypto` | 私钥缺 BEGIN/END 行或被截断 | 重新完整复制私钥 |
| `Load key ... invalid format` | 粘成了公钥 | 换成无 `.pub` 后缀的文件 |
| `Permission denied (publickey)` | 公钥未授权，或粘了另一把密钥 | 用第 1.3 节的指纹比对确认 |
| `Connection timed out` | 安全组未放行 SSH 端口 | 云控制台入方向放行该端口 |
| `unauthorized` 拉取镜像 | 镜像包为私有 | 配置 `GHCR_PULL_USER` / `GHCR_PULL_TOKEN`（`read:packages`） |
| 部署 job 卡在 waiting | 配了 Required reviewers | 到 Actions 页面点 **Review deployments → Approve** |
| `port is already allocated` | 宿主已有服务占用该端口 | 调整 `WEB_PORT` 或检查 `PANDORA_DATA_DIR` 配置 |

## 7. 安全注意事项

- **私钥只存在于 GitHub Secret**：不要贴到 Issue、PR、聊天记录或文档里。
- **`Variables` 是明文**，任何敏感值都不能放这里。
- **限制仓库写权限**：能推 `main` 的人等于能通过工作流在服务器上执行命令，
  因此务必启用第 4.4 节的分支保护与第 4.3 节的部署审批。
- **密钥轮换**：私钥泄露时，在服务器 `~/.ssh/authorized_keys` 删除对应行，
  重新生成密钥对并更新 Secret 即可，无需改动其他配置。
- **同机多站点**：若服务器还承载其他服务，部署脚本的误操作会连带影响，
  建议部署审批与备份同时到位。
- **生产密钥（`DJANGO_SECRET_KEY`、`IDENTITY_NUMBER_ENCRYPTION_KEY`）不进 GitHub**，
  它们只在服务器 `/opt/pandora/.env`，详见 [server-deploy.md](server-deploy.md) 第 3 节。
