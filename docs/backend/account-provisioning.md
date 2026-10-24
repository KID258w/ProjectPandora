# Issue #6：账号录入与登录契约

关联 Issue：#6；OpenSpec：`admin-identity-and-organization`。本文记录后端账号录入与登录接口契约，并补充本次 Issue #1 遗留修复内容。

## 本次 Issue #1 遗留修复

- 业务账号的 `username` 使用员工手机号，创建时必须提供身份证号；后端加密保存身份证号，普通列表和个人资料响应只返回脱敏值。
- 创建请求不再接受客户端密码或启用状态。后端自动启用账号，生成恰好 16 个 URL-safe 字符的随机初始密码，仅在本次成功创建响应返回；密码只以哈希形式保存。
- 创建响应设置 `Cache-Control: no-store`、`Pragma: no-cache` 和 `Expires: 0`。后续用户列表和 `/app/me/` 不返回初始密码或完整身份证号。

## 初始化与升级

Python 3.11+，安装 `backend/requirements.txt`，首次复制 `.env.example`（不覆盖已有 `.env`），执行 `python manage.py migrate`、`python manage.py createsuperuser`、`python manage.py runserver`。

`createsuperuser` 交互创建 `account_type=admin` 的 Web 管理员，同时具有 Django 运维后台权限。API 管理权限由 `account_type` 决定，不依赖 `is_staff`。没有公开管理员注册。密码不写入源码。

迁移 0002 将已有 superuser 标记为管理员。若旧数据库有非 superuser，迁移会停止，必须先备份、确认真实身份/组织并编写经审阅的数据迁移；不会猜测业务身份、提升权限或删除记录。回滚时停止服务，同时恢复旧代码和数据库备份。

## 通用格式

下述路径前缀均为 `/api/v1/`，请求 JSON，认证头 `Authorization: Bearer <access>`。成功及错误均包含 `code/message/data/requestId`，code 等于 HTTP 状态码，requestId 为服务端 UUID：

```json
{"code":200,"message":"success","data":{},"requestId":"2e6c0997-3265-45ad-b08e-6b6b0b387aff"}
```

```json
{"code":400,"message":"请求失败","data":{"team_id":["请选择所选部门下的团队。"]},"requestId":"2e6c0997-3265-45ad-b08e-6b6b0b387aff"}
```

创建成功为 201；400 为字段或业务校验错误；401 为未登录、登录失败、令牌无效/过期或账号停用；403 为有效账号访问另一端；不支持的方法返回 405；无效分页返回 404。对象级错误为 `data.non_field_errors`，认证错误为 `data.detail`。不要依赖中文文案判断身份。

列表按 ID 升序，支持 `page`（默认 1）、`page_size`（默认 20，最多 100），data 为 `{"count":1,"next":null,"previous":null,"results":[...]}`。健康检查与文档路径/响应保持原状。旧 `/api/auth/token/` 和 `/api/auth/token/refresh/` 已移除，避免未隔离认证入口。

## 认证与当前用户

| 方法/路径 | 请求 JSON | 成功 data JSON |
| --- | --- | --- |
| POST `admin/auth/token/` | `{"username":"admin","password":"<管理员密码>"}` | `{"access":"<JWT>","refresh":"<JWT>"}` |
| POST `app/auth/token/` | `{"username":"13800000001","password":"<初始密码或员工修改后的密码>"}` | `{"access":"<JWT>","refresh":"<JWT>"}` |
| POST `admin/auth/token/refresh/` | `{"refresh":"<管理员 refresh>"}` | `{"access":"<新 JWT>"}` |
| POST `app/auth/token/refresh/` | `{"refresh":"<业务 refresh>"}` | `{"access":"<新 JWT>"}` |

两端均拒绝另一种账号登录、访问和刷新；JWT 含 account_type。刷新时重新校验账号存在、启用且类型相符，访问接口同样重查账号状态。Simple JWT 默认 access 有效期 5 分钟、refresh 1 天，不轮换；退出由客户端清除本地令牌，本次不含服务端注销。

`GET admin/me/` 无请求体，返回管理员 data 示例：

```json
{"id":1,"username":"admin","account_type":"admin","business_role":"","display_name":"","department_id":null,"department_name":null,"team_id":null,"team_name":null,"manager_id":null,"manager_name":null,"is_active":true,"id_number_masked":null}
```

`GET app/me/` 无请求体，返回业务用户 data 示例：

```json
{"id":5,"username":"13800000001","account_type":"business","business_role":"employee","display_name":"张三","department_id":1,"department_name":"研发部","team_id":1,"team_name":"后端组","manager_id":4,"manager_name":"团队长","is_active":true,"id_number_masked":"***************1234"}
```

创始人组织和上级字段均为 null；部门经理团队字段为 null。响应不包含密码、哈希和 staff/superuser 标记。

## 部门和团队（仅管理员）

| 方法/路径 | 请求 JSON | 成功 data JSON |
| --- | --- | --- |
| POST `admin/departments/` | `{"name":"研发部","description":"技术研发"}` | `{"id":1,"name":"研发部","description":"技术研发","created_at":"2026-10-17T10:00:00+08:00"}` |
| GET `admin/departments/` | 无请求体 | `{"count":1,"next":null,"previous":null,"results":[{"id":1,"name":"研发部","description":"技术研发","created_at":"2026-10-17T10:00:00+08:00"}]}` |
| POST `admin/teams/` | `{"name":"后端组","department_id":1,"description":"后端研发"}` | `{"id":1,"name":"后端组","department_id":1,"description":"后端研发","created_at":"2026-10-17T10:00:00+08:00"}` |
| GET `admin/teams/?department_id=1` | 无请求体 | `{"count":1,"next":null,"previous":null,"results":[{"id":1,"name":"后端组","department_id":1,"description":"后端研发","created_at":"2026-10-17T10:00:00+08:00"}]}` |

description 可省略，默认空字符串。部门名全局唯一，团队名部门内唯一，name 最长 100 且不允许空白。团队必须关联已有部门。列表可省略 department_id 查询全部；非正整数返回 400，合法但不存在的 ID 返回空列表。

## 业务用户（仅管理员）

`POST admin/users/` 由管理员提交手机号（存入唯一字段 `username`）、身份证号、姓名、业务身份和对应组织。身份证号必填，最长 64 个字符；姓名必填且最长 100；手机号存入 `username`，遵循 Django 字段规则且最长 150。客户端不得提交 `password`、`is_active` 或 `manager_id`。

```json
{"username":"13800000001","id_number":"<身份证号>","display_name":"创始人","business_role":"founder"}
```

```json
{"username":"13800000002","id_number":"<身份证号>","display_name":"部门经理","business_role":"department_manager","department_id":1}
```

```json
{"username":"13800000003","id_number":"<身份证号>","display_name":"团队长","business_role":"team_leader","department_id":1,"team_id":1}
```

```json
{"username":"13800000004","id_number":"<身份证号>","display_name":"张三","business_role":"employee","department_id":1,"team_id":1}
```

服务端自动启用新业务账号，并生成恰好 16 个 URL-safe 字符的强随机初始密码；数据库只保存密码哈希。创建成功 HTTP 201 时，响应中的 `initial_password` 只返回这一次；响应设置 `Cache-Control: no-store, no-cache, max-age=0, private`、`Pragma: no-cache` 和 `Expires: 0`。后续读取不再返回初始密码或完整身份证号。`account_type` 由服务端强制为 business。

创建响应示例：

```json
{"code":201,"message":"success","data":{"id":8,"username":"13800000004","account_type":"business","business_role":"employee","display_name":"张三","department_id":1,"department_name":"研发部","team_id":1,"team_name":"后端组","manager_id":4,"manager_name":"团队长","is_active":true,"id_number_masked":"***************1234","initial_password":"<本次返回的16位初始密码>"},"requestId":"2e6c0997-3265-45ad-b08e-6b6b0b387aff"}
```

`GET admin/users/` 无请求体，仅列业务账号（含停用），不列管理员，示例：

```json
{"code":200,"message":"success","data":{"count":1,"next":null,"previous":null,"results":[{"id":5,"username":"13800000001","account_type":"business","business_role":"employee","display_name":"张三","department_id":1,"department_name":"研发部","team_id":1,"team_name":"后端组","manager_id":4,"manager_name":"团队长","is_active":true,"id_number_masked":"***************1234"}]},"requestId":"2e6c0997-3265-45ad-b08e-6b6b0b387aff"}
```

自动上级：创始人无组织/上级；经理关联创始人；团队长关联本部门经理；员工关联本团队长。创建时上级必须存在且启用。当前只支持一位在职创始人，避免自动选取歧义；一部门一在职经理、一团队一在职团队长，唯一约束与重复校验仅针对 `is_active=True` 的账号，停用账号不占用名额。团队必须属于指定部门。

身份证号加密存储；`GET admin/users/` 和 `GET app/me/` 仅返回 `id_number_masked`，不返回完整值。禁止传入 password、is_active、manager_id、account_type、is_staff、is_superuser 等字段；未知字段返回 400，不静默忽略。录入与关系写入在同一事务内，数据库唯一约束兜底并发重复录入，返回 400 而非 500。

## 范围及验证

本次没有详情更新、删除、密码重置、组织调动、负责人替换、停用操作 API、审计查询和自助注册；OpenSpec 的这些完整管理能力仍待后续实施。Django `/admin/` 仅作受信任运维入口，业务用户录入必须使用版本化 API。

在 backend 执行 `python manage.py check`、`python manage.py makemigrations --check --dry-run`、`python manage.py migrate --noinput`、`python manage.py test --verbosity 2`、`python manage.py spectacular --validate --fail-on-warn --file <临时目录/schema.yaml>`。Swagger 在 `/api/docs/`。PR 附实际测试证据，本机测试不代表 GitHub CI 已通过。
