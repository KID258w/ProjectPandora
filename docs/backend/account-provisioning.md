# Issue #6：账号录入与登录契约

关联 Issue：#6、Issue2；OpenSpec：`admin-identity-and-organization`。本文记录后端账号录入、登录和员工账号后续维护契约。Issue #1 遗留修复已完成；下文 Issue2 契约已在功能02后端 PR 实现。

## 本次 Issue #1 遗留修复

- 业务账号的 `username` 使用员工手机号，创建时必须提供身份证号；后端加密保存身份证号，普通列表和个人资料响应只返回脱敏值。
- 创建请求不再接受客户端密码或启用状态。后端自动启用账号，生成恰好 16 个 URL-safe 字符的随机初始密码，仅在本次成功创建响应返回；密码只以哈希形式保存。
- 创建响应设置 `Cache-Control: no-store`、`Pragma: no-cache` 和 `Expires: 0`。后续用户列表和 `/app/me/` 不返回初始密码或完整身份证号。

## 初始化与升级

Python 3.11+，安装 `backend/requirements.txt`，首次复制 `.env.example`（不覆盖已有 `.env`），执行 `python manage.py migrate`、`python manage.py createsuperuser`、`python manage.py runserver`。

`createsuperuser` 交互创建 `account_type=admin` 的 Web 管理员，同时具有 Django 运维后台权限。API 管理权限由 `account_type` 决定，不依赖 `is_staff`。没有公开管理员注册。密码不写入源码。

迁移 0002 将已有 superuser 标记为管理员。若旧数据库有非 superuser，迁移会停止，必须先备份、确认真实身份/组织并编写经审阅的数据迁移；不会猜测业务身份、提升权限或删除记录。回滚时停止服务，同时恢复旧代码和数据库备份。

Issue2 合并后的修复增加了迁移检查：迁移 0008 在回填身份证 HMAC 前检查原始历史重复值；迁移 0010 在规范化身份证号（去除首尾空格、字母转大写）前检查规范化后的重复值。发现重复时会中止并只报告冲突账号的 User ID，不会输出身份证号，也不会先写入部分哈希。处理冲突账号后重新执行迁移。反向迁移 0008 和 0010 保留已生成的哈希/规范化值。迁移 0009 在添加“用户不能将自己设为直属上级”的数据库约束前检查历史记录；若有冲突，同样只报告 User ID，修复记录后重跑迁移。

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

App 业务账号还必须具有从本人到启用创始人的完整直属上级链。链路因创始人、部门经理或团队长空缺/停用而中断时，App 登录和令牌刷新返回 401，已登录的 App 业务接口返回 403；管理员 Web 接口不受影响。负责人补齐后，后端重建直属关系，App 访问恢复。

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

`POST admin/users/` 由管理员提交手机号（存入唯一字段 `username`）、身份证号、姓名、业务身份和对应组织。身份证号必填，最长 64 个字符；后端去除首尾空格并将字母统一为大写后再保存及检查全局唯一性。姓名必填且最长 100；手机号存入 `username`，遵循 Django 字段规则且最长 150。客户端不得提交 `password`、`is_active` 或 `manager_id`。

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

自动上级：创始人无组织/上级；经理关联创始人；团队长关联本部门经理；员工关联本团队长。公司最多一位启用创始人，允许暂时没有启用创始人；每个部门最多一位启用经理，每个团队最多一位启用团队长，也允许暂时空缺。唯一约束仅针对启用账号，停用账号不占用负责人名额。创建、重新启用或转入岗位时，所需的直属负责人必须已启用；任命负责人以补齐本级空缺时，也必须满足候选人自身的直属负责人要求。团队必须属于指定部门。

身份证号加密存储；`GET admin/users/` 和 `GET app/me/` 仅返回 `id_number_masked`，不返回完整值。禁止传入 password、is_active、manager_id、account_type、is_staff、is_superuser 等字段；未知字段返回 400，不静默忽略。录入与关系写入在同一事务内，数据库唯一约束兜底并发重复录入，返回 400 而非 500。

## Issue2：员工账号后续维护契约（已实现）

以下契约已在功能02后端 PR 实现；不改变上文已完成的账号创建和初始密码交付流程。

### 管理端调整员工业务信息

- `GET admin/users/` 支持按姓名或手机号关键词、`business_role`、`department_id`、`team_id`、`is_active` 筛选。管理列表可查看停用员工；身份证号仍只返回 `id_number_masked`。
- `GET admin/users/{id}/` 查看员工脱敏资料。
- `PATCH admin/users/{id}/` 仅接受 `business_role`、`department_id`、`team_id`、`is_active`。拒绝修改 `display_name`、`username`、`id_number`、`password` 和 `manager_id`；Web 不提供密码重置。
- 员工身份和组织调整由服务端校验角色与部门/团队的匹配关系；停用员工、部门、团队不能作为调整候选项。直属上级由服务端计算和维护。
- 身份证号在全部业务账号中全局唯一，停用账号仍占用该身份证号；录入或 App 修改时校验，重复返回 400。底层用 `id_number_hash`（HMAC-SHA256，密钥由服务器保管，唯一）兜底并发写入。
- 公司最多一名启用创始人、每部门最多一名启用部门经理、每团队最多一名启用团队长，均允许暂时空缺。停用或撤销负责人时只将其直接下属的 `manager_id` 置空；新负责人创建、启用或任命到位后重建对应直属关系。负责人链不完整期间，受影响组织范围内的 App 账号不能登录、刷新令牌或访问 App API。
- 既有启用员工可在负责人暂缺时保留启用状态和原组织归属，但不能因此获得 App 访问。新建、重新启用或转入岗位需要有效直属负责人；任命负责人填补本级空缺时，候选人自身所需的直属负责人仍须启用。
- 团队重新启用要求所属部门已启用；团队改名不能与同部门的其他团队重名，冲突返回 400。

### 组织详情与启停

- `GET, PATCH admin/departments/{id}/`：详情含名称、说明、`is_active`、负责人（可为 null）、下属团队与成员（只读）；PATCH 仅接受 `name`、`description` 或单独 `is_active`。
- `GET, PATCH admin/teams/{id}/`：详情含名称、说明、`is_active`、所属部门、负责人（可为 null）与成员（只读）；PATCH 仅接受 `name`、`description` 或单独 `is_active`，不接受 `department_id`。
- 部门仍有启用下属团队或成员、团队仍有启用成员时，`is_active=false` 返回 400。部门和团队模型保存 `updated_at`，但当前 API 响应只包含 `created_at`，不返回 `updated_at`。

### 管理概况与审计

- `GET admin/dashboard/` 返回启用员工、部门、团队数及已发布公司重要事项数（后者当前为 0）。
- `GET admin/audit-logs/` 按 `start_date`/`end_date`（YYYY-MM-DD）、`actor_id`、`module`（users/departments/teams）筛选；`before_data`/`after_data` 已脱敏，不含明文密码、完整身份证号或日志内容。

### App 员工本人资料维护

员工使用有效的 App 业务账号 access token 调用 `GET app/me/` 读取本人资料。响应继续返回 `id_number_masked`，不返回完整身份证号。

`PATCH app/me/` 允许提交以下一个或多个字段：

```json
{
  "display_name": "张三",
  "username": "13800000002",
  "id_number": "<本人重新填写的完整身份证号>"
}
```

`display_name` 与 `id_number` 可在 App 同一个个人资料表单中一并提交，也可以分别提交；姓名与身份证号不自动联动。身份证号保存前去除首尾空格并将字母转为大写，再按规范化结果检查全局唯一性。后端对两项分别校验，并在一个数据库事务中保存：任一字段校验失败时整次更新回滚。`username` 表示员工的新手机号；后端校验其唯一性后原子更新登录标识。该接口拒绝业务身份、部门/团队、`manager_id`、`is_active` 和密码字段。

### App 员工本人修改密码

`POST app/me/password/` 仅允许当前已认证的启用业务账号修改自己的密码：

```json
{
  "current_password": "当前密码",
  "new_password": "NewStrongPassword!42",
  "new_password_confirm": "NewStrongPassword!42"
}
```

后端校验当前密码、确认字段一致、新密码不同于当前密码，并运行 Django 配置的密码校验器。成功后只更新 `User.password` 哈希，不回显或记录任何密码值。Issue2 MVP 不增加服务端多设备令牌撤销，已签发 JWT 按现有有效期失效；不提供短信验证或密码找回。

## 范围及验证

上述“Issue2”小节契约已在功能02后端 PR 实现。Issue1 已实现账号录入、管理员与业务账号认证隔离、App 登录和当前用户读取；Issue2 已实现部门/团队详情与启停、员工筛选与身份/组织调整、管理概况与审计、App 个人资料与密码维护。Django `/admin/` 仅作受信任运维入口，业务用户录入必须使用版本化 API。

在 backend 执行 `python manage.py check`、`python manage.py makemigrations --check --dry-run`、`python manage.py migrate --noinput`、`python manage.py test --verbosity 2`、`python manage.py spectacular --validate --fail-on-warn --file <临时目录/schema.yaml>`。Swagger 在 `/api/docs/`。PR 附实际测试证据，本机测试不代表 GitHub CI 已通过。
