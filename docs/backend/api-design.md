# 后端 API 设计

> **Issue #6 实施更新**：账号录入、组织列表/创建与两端认证已实现。各接口请求/响应示例、错误、分页、身份规则以 [账号录入与登录契约](account-provisioning.md) 为准。以下其它业务内容仍为规划，旧 `/api/auth/token/` 入口已移除。

> 维护说明：由项目统筹与后端同学共同维护，作为 API 设计的持续文档。接口路径、字段名和错误格式可随开发协商调整。  
> 依据：`docs/requirements/01` 至 `06`、`docs/cooperate/swagger-guide.md` 与当前 Django 骨架。需求变化时以最新确认的需求为准。

## 1. 设计约定

### 1.1 当前实现与目标接口

当前已实现健康检查、文档及 Issue #6 的版本化账号/组织接口。版本化 API 已采用统一外层响应；健康检查保持原响应。

| 当前已存在 | 路径 |
|---|---|
| 健康检查 | `GET /api/health/` |
| 获取 JWT | `POST /api/v1/admin/auth/token/`、`POST /api/v1/app/auth/token/` |
| 刷新 JWT | `POST /api/v1/admin/auth/token/refresh/`、`POST /api/v1/app/auth/token/refresh/` |
| OpenAPI Schema / Swagger UI / ReDoc | `/api/schema/`、`/api/docs/`、`/api/redoc/` |

下文 `/api/v1/...` 中 Issue #6 的认证、me、部门/团队/用户列表与创建已实现，其余仍为建议接口，不代表已上线。

### 1.2 通用约定

- 传输：HTTPS 上的 REST/JSON；不需要 WebSocket。开发环境可用 HTTP。
- 建议前缀：`/api/v1/app/`（安卓业务端）与 `/api/v1/admin/`（Web 管理端），明确区分调用方和权限边界。
- 认证：`Authorization: Bearer <access_token>`。Web 管理账号与安卓业务账号分开认证，禁止业务账号通过管理端登录入口登录，反之亦然。
- 日期时间：请求与响应使用 ISO 8601；数据库统一存储带时区时间，客户端按 `Asia/Shanghai` 显示。纯日期使用 `YYYY-MM-DD`。
- 命名：JSON 字段建议统一使用 `snake_case`；主键统一为整数 ID 或 UUID，实际项目选定一种后保持一致。
- 列表：建议统一 `page`、`page_size` 查询参数；响应包含 `count`、`next`、`previous`、`results`。日历/视图等按日期区间查询的接口也应限制区间长度。
- 成功响应：业务接口统一使用 `{ "code": 200, "message": "success", "data": {}, "requestId": "<uuid>" }`；单条资源、操作结果或分页对象放入 `data`。
- 错误：使用相同外层结构，`code` 与 HTTP 状态码一致，`message` 为可读说明，`data` 放字段校验或错误详情，`requestId` 用于日志追踪。
- 状态码：`400` 参数/业务校验失败，`401` 未登录或令牌失效，`403` 已登录但无权，`404` 对象不存在或对当前用户不可见，`409` 状态冲突，`500` 未预期服务错误。
- 每次请求都必须在服务端重新验证数据范围和对象权限；前端隐藏按钮不构成权限控制。

### 1.3 关键状态与权限规则

任务有三个相互独立的状态维度，避免把“等待审批”“等待验收”误当成任务完成状态：

| 维度 | 初步取值 | 含义 |
|---|---|---|
| `approval_status` | `pending`、`returned`、`approved` | 创建任务的上级审批；创始人创建的任务直接为 `approved` |
| `completion_status` | `unfinished`、`completed` | 业务任务只保留未完成、已完成两种完成状态 |
| `lifecycle_status` | `active`、`cancelled` | 任务是否被创建人取消；取消需记录原因，不等同于完成 |

审批通过后任务才进入创建人的“我创建的”和负责人的“我的任务”；审批中的任务分别出现在上级“待你审批”和创建人“待上级审批”。验收由直接派发者处理；创建人与负责人为同一人时，提交即完成。上级向下查看仅看下属承担的任务，不另提供“下属创建的任务”列表。跨部门任务使用独立创建入口，审批通过后负责人直接获得任务，不需要负责人另行接受。

## 2. Web 管理端 API

Web 管理端只管理账号、组织、公司重要事项和审计记录；**不提供任务、员工日志或 AI 数据接口**。以下为建议接口，`{id}` 表示资源 ID。

| 功能 | 方法与路径 | 用途 / 主要权限 |
|---|---|---|
| 管理端登录 | `POST /api/v1/admin/auth/token/` | 独立管理账号登录，签发管理端令牌 |
| 刷新令牌 | `POST /api/v1/admin/auth/token/refresh/` | 刷新管理端令牌 |
| 当前管理员 | `GET /api/v1/admin/me/` | 返回管理账号资料及管理权限 |
| 管理首页 | `GET /api/v1/admin/dashboard/` | 返回账号、组织、重要事项等管理统计；不汇总任务和日志 |
| 部门列表/创建 | `GET, POST /api/v1/admin/departments/` | 列出或创建部门 |
| 部门详情/更新 | `GET, PATCH /api/v1/admin/departments/{id}/` | 查看或修改部门名称、说明、状态等 |
| 团队列表/创建 | `GET, POST /api/v1/admin/teams/` | 列出或创建团队；创建时指定所属部门 |
| 团队详情/更新 | `GET, PATCH /api/v1/admin/teams/{id}/` | 修改团队、所属部门或状态 |
| 员工账号列表/创建 | `GET, POST /api/v1/admin/users/` | 查询或录入业务账号、姓名、业务角色和组织归属 |
| 员工账号详情/更新 | `GET, PATCH /api/v1/admin/users/{id}/` | 修改姓名、角色、部门/团队、启用状态 |
| 重置员工密码 | `POST /api/v1/admin/users/{id}/reset-password/` | 重置登录密码；初始密码如何交付待定 |
| 公司重要事项列表/创建 | `GET, POST /api/v1/admin/company-items/` | 查询或新建事项草稿 |
| 事项详情/更新 | `GET, PATCH /api/v1/admin/company-items/{id}/` | 编辑标题、摘要、正文、附件等 |
| 发布/下线事项 | `POST /api/v1/admin/company-items/{id}/publish/`、`.../offline/` | 发布或下线事项；发布数量最多 10 条 |
| 调整事项排序 | `PUT /api/v1/admin/company-items/order/` | 按请求中的 ID 顺序批量保存手动排序 |
| 上传事项附件 | `POST /api/v1/admin/company-items/{id}/attachments/` | 可选；附件存储方案和限制待定 |
| 审计记录 | `GET /api/v1/admin/audit-logs/` | 按时间、管理员、模块筛选审计记录；只读 |

建议管理端用户查询支持 `keyword`（姓名/账号）、`department_id`、`team_id`、`business_role`、`is_active` 等筛选；团队查询支持 `department_id`。禁用部门/团队前由后端校验不得仍有启用的下级组织或成员。管理端账号体系与业务账号的具体存储方式（独立用户表或同一认证表加账号类型约束）在数据库实现前确定。

## 3. Android 业务端 API

### 3.1 登录、用户与首页

| 方法与路径 | 用途 |
|---|---|
| `POST /api/v1/app/auth/token/` | 业务账号登录 |
| `POST /api/v1/app/auth/token/refresh/` | 刷新业务端令牌 |
| `GET /api/v1/app/me/` | 当前用户姓名、业务角色、部门/团队、直属上级等 |
| `PATCH /api/v1/app/me/` | 修改允许本人维护的个人资料字段 |
| `GET /api/v1/app/home/` | 聚合首页四卡摘要及最近日志摘要；每卡最多 5 条 |
| `GET /api/v1/app/company-items/` | 已发布的公司重要事项列表，最多 10 条 |
| `GET /api/v1/app/company-items/{id}/` | 公司重要事项详情 |

首页“公司十大重要任务”是根据当前用户承担的未完成任务计算出的最多 10 条，不是管理员维护的全公司排行榜；按当前需求文档规定的来源和截止时间排序。个人十大重要任务由用户选择并拖拽排序，服务端持久化顺序；最多 10 条，任务完成、取消或不再由本人承担时应从可选范围中移除。

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/app/home/personal-focus/` | 返回个人重点任务及顺序 |
| `PUT /api/v1/app/home/personal-focus/` | 一次性保存任务 ID 有序数组；校验均为本人未完成任务且不超过 10 条 |

### 3.2 任务中心、创建、审批与验收

| 方法与路径 | 用途 / 规则 |
|---|---|
| `GET /api/v1/app/tasks/mine/` | 当前用户承担的已审批任务；支持 `completion_status`、关键词等筛选 |
| `GET /api/v1/app/tasks/created/` | 当前用户创建的已审批任务；支持完成状态筛选 |
| `GET /api/v1/app/tasks/subordinates/` | 当前用户有权查看的下属承担任务；仅含承担人视角，不单列下属创建列表 |
| `GET /api/v1/app/task-approvals/inbox/` | 待当前上级审批任务；不提供已完成/未完成筛选 |
| `GET /api/v1/app/task-approvals/outbox/` | 当前用户等待上级审批的任务；不提供已完成/未完成筛选 |
| `GET /api/v1/app/tasks/{id}/` | 任务详情；服务端按创建人、负责人、授权上级等校验可见性 |
| `POST /api/v1/app/tasks/` | 普通入口创建任务；负责人为本人或符合规则的直接下属，有直属上级则进入待审批 |
| `POST /api/v1/app/cross-department-tasks/` | 部门经理跨部门任务独立入口；负责人为其他部门经理，审批人按规则为创始人 |
| `PATCH /api/v1/app/tasks/{id}/` | 修改有权修改且仍可编辑的任务；审批后关键定义变更需重新审批 |
| `POST /api/v1/app/tasks/{id}/approval/` | 上级审批通过或退回；退回必须附原因 |
| `POST /api/v1/app/tasks/{id}/updates/` | 负责人追加进展说明；只追加记录，不设置百分比进度 |
| `POST /api/v1/app/tasks/{id}/submissions/` | 负责人提交完成说明及所需材料说明；不是创建任务表单 |
| `POST /api/v1/app/tasks/{id}/submissions/{submission_id}/review/` | 直接派发者验收通过或退回；验收通过后任务完成 |
| `POST /api/v1/app/tasks/{id}/cancel/` | 创建人取消本人创建的未完成任务，必须填写原因 |
| `GET /api/v1/app/organization/direct-reports/` | 查询有权分配任务的直接下属，支持姓名关键词 |
| `GET /api/v1/app/organization/visible-subordinates/` | 查看对象选择器可访问的下属；逐级查看时支持姓名/团队/部门搜索 |

任务列表可支持 `completion_status=all|unfinished|completed`；`lifecycle_status=cancelled` 可作为单独筛选或独立历史列表，具体交互待最终确认。审批列表仅按审批状态筛选。负责人选择接口只返回创建者有权派发给的对象；普通员工只能创建指派给自己的任务。跨部门入口不与普通负责人选择混在一起。

建议创建任务请求示例：

```json
{
  "assignee_id": 42,
  "title": "完成客户需求梳理",
  "description": "整理本周收集到的需求并形成结论",
  "required_materials": "需求清单与评审结论",
  "due_at": "2026-10-20T18:00:00+08:00",
  "association_note": "与 Q4 项目方案相关"
}
```

服务端根据登录人、组织关系和目标负责人决定是否需要审批，客户端不得自行传入/覆盖 `approval_status`、创建人、跨部门标记或完成状态。关联说明为自由文本，不建立上级任务外键或任务树。

### 3.3 日志

| 方法与路径 | 用途 / 规则 |
|---|---|
| `GET /api/v1/app/logs/calendar/` | 按 `year`、`month` 返回本人或选中查看对象已提交日志的日期标记 |
| `GET /api/v1/app/logs/` | 查询本人历史日志或有权查看的下属日志；按用户/日期筛选 |
| `GET /api/v1/app/logs/{id}/` | 查看日志内容；管理者只读且只能查看已提交日志 |
| `GET /api/v1/app/logs/today/` | 获取本人今日日志草稿/已提交状态，未创建时返回空表单态 |
| `PUT /api/v1/app/logs/today/` | 新建或更新本人今日唯一日志；字段为完成工作、下一步计划等 |
| `POST /api/v1/app/logs/today/submit/` | 提交本人今日日志 |
| `GET /api/v1/app/log-viewers/` | 返回当前用户可选择查看的下属，支持姓名关键词搜索 |

建议日志请求字段：`completed_work`（必填）、`next_day_plan`（可选）。MVP 不提供关联任务、问题与风险字段、附件和日志正文关键字搜索。每人每天最多一条；历史日志只读；自然日结束后不可修改。管理者查询草稿与无日志日期时不应泄露草稿内容。

### 3.4 月、周、日工作视图

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/app/work-views/month/?user_id=&year=&month=` | 月视图：返回按任务创建日期至截止日期绘制的任务条及当日状态；可选查看本人或有权查看的单一下属 |
| `GET /api/v1/app/work-views/week/?user_id=&date=` | 周视图：一周日期条、未完成任务全天任务条、24 小时时间轴动态事件 |
| `GET /api/v1/app/work-views/day/?user_id=&date=` | 日视图：当天未完成任务条及 24 小时时间轴动态事件 |

视图为只读聚合，不单独保存日历副本。月视图任务条可点击进入详情，显示标题、未完成/已完成；个人重点可附 `is_personal_focus`。周/日动态项包括负责人进展更新、负责人提交、直接派发者验收通过；自我负责任务提交即完成只产生一条合并动态。上级验收事件归属于负责人的视图，不出现在验收人的视图。动态响应至少包含 `occurred_at`、`task_id`、`task_title`、`event_type`、`summary`。周视图全天未完成任务可横跨多天；固定日期条和全天区域不随时间轴滚动，动态按实际发生日期/小时归列。

### 3.5 AI 地图与辅助功能

| 方法与路径 | 用途 / 规则 |
|---|---|
| `GET /api/v1/app/ai/consent/` | 查看当前 AI 数据处理授权状态/版本 |
| `POST /api/v1/app/ai/consent/` | 用户首次使用前明确同意；记录同意版本和时间 |
| `GET, POST /api/v1/app/ai/conversations/` | 列出/创建本人独立会话，类型 `work` 或 `treehole` |
| `GET, DELETE /api/v1/app/ai/conversations/{id}/` | 查看或删除本人会话；删除后不再用于后续上下文 |
| `GET /api/v1/app/ai/conversations/{id}/messages/` | 查询会话消息 |
| `POST /api/v1/app/ai/conversations/{id}/messages/` | 发送消息；树洞与工作助手会话隔离 |
| `GET /api/v1/app/ai/mbti/questions/` | 获取轻量自我探索问题集及版本 |
| `POST /api/v1/app/ai/mbti/answers/` | 提交作答并计算结果 |
| `GET /api/v1/app/ai/mbti/result/` | 获取本人最新结果；不向经理或管理员开放 |

工作助手只访问用户在请求中明确选择的本人任务/已提交日志，并只能生成建议或草稿，不自动写入任务/日志。树洞不读取业务数据；支持倾听、梳理、建议模式。AI 服务密钥保存在后端，不发送给客户端。模型厂商留存策略仍待确认。

## 4. 各 API 请求与响应示例

以下示例覆盖本文列出的每个接口方法。它们是供联调使用的具体契约草案；字段仍可在产品统筹与后端同学确认后调整。每个接口分别列出请求与响应；GET/DELETE 会注明查询参数或无请求体。

### 4.1 当前骨架接口

#### GET /api/health/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "ok"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/auth/token/

**请求 JSON**

```json
{
  "username": "demo",
  "password": "示例密码"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "access": "<access_token>",
    "refresh": "<refresh_token>"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/auth/token/refresh/

**请求 JSON**

```json
{
  "refresh": "<refresh_token>"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "access": "<new_access_token>"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/schema/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON（OpenAPI 文档，不使用业务响应外壳）**

```json
{
  "openapi": "3.0.3",
  "info": {
    "title": "Pandora API",
    "version": "1.0.0"
  },
  "paths": {}
}
```

#### GET /api/docs/ 与 GET /api/redoc/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应：** HTML 文档页面，不是业务 JSON 接口


### 4.2 Web 管理端

分页响应中的 `next`、`previous` 示例为 `null`；实际有后续页时返回对应 URL。资源字段以各接口示例为准绳逐步冻结。

#### POST /api/v1/admin/auth/token/

**请求 JSON**

```json
{
  "username": "admin01",
  "password": "示例密码"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<admin_jwt_token>",
    "refresh_token": "<admin_refresh_token>",
    "user": {
      "id": 1,
      "username": "admin01",
      "role": "admin"
    }
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/auth/token/refresh/

**请求 JSON**

```json
{
  "refresh_token": "<admin_refresh_token>"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<new_admin_jwt_token>"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/me/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin01",
    "display_name": "系统管理员",
    "account_type": "admin",
    "permissions": [
      "users.manage",
      "organization.manage"
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/dashboard/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "active_users": 24,
    "departments": 3,
    "teams": 6,
    "published_company_items": 5
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/departments/?page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 10,
        "name": "产品部",
        "description": "产品研发",
        "status": "active"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/departments/

**请求 JSON**

```json
{
  "name": "产品部",
  "description": "产品研发"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 10,
    "name": "产品部",
    "description": "产品研发",
    "status": "active",
    "created_at": "2026-10-11T10:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/departments/10/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 10,
    "name": "产品部",
    "description": "产品研发",
    "status": "active",
    "teams_count": 2,
    "active_users_count": 8
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/admin/departments/10/

**请求 JSON**

```json
{
  "description": "产品与体验设计部"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 10,
    "name": "产品部",
    "description": "产品与体验设计部",
    "status": "active",
    "updated_at": "2026-10-11T10:10:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/teams/?department_id=10&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 21,
        "name": "客户端组",
        "department_id": 10,
        "status": "active"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/teams/

**请求 JSON**

```json
{
  "name": "客户端组",
  "department_id": 10,
  "description": "安卓与 Web 客户端"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 21,
    "name": "客户端组",
    "department_id": 10,
    "description": "安卓与 Web 客户端",
    "status": "active"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/teams/21/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 21,
    "name": "客户端组",
    "department_id": 10,
    "department_name": "产品部",
    "description": "安卓与 Web 客户端",
    "status": "active",
    "active_users_count": 4
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/admin/teams/21/

**请求 JSON**

```json
{
  "department_id": 10,
  "status": "inactive"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 21,
    "name": "客户端组",
    "department_id": 10,
    "description": "安卓与 Web 客户端",
    "status": "inactive",
    "updated_at": "2026-10-11T10:10:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/users/?keyword=张&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 42,
        "username": "zhangsan",
        "display_name": "张三",
        "business_role": "employee",
        "department_id": 10,
        "team_id": 21,
        "is_active": true
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/users/

**请求 JSON**

```json
{
  "username": "zhangsan",
  "initial_password": "临时密码",
  "display_name": "张三",
  "business_role": "employee",
  "department_id": 10,
  "team_id": 21,
  "manager_id": 31
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "username": "zhangsan",
    "display_name": "张三",
    "account_type": "business",
    "business_role": "employee",
    "department_id": 10,
    "team_id": 21,
    "manager_id": 31,
    "is_active": true
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/users/42/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "username": "zhangsan",
    "display_name": "张三",
    "business_role": "employee",
    "department_id": 10,
    "team_id": 21,
    "manager_id": 31,
    "is_active": true,
    "created_at": "2026-10-11T10:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/admin/users/42/

**请求 JSON**

```json
{
  "display_name": "张三",
  "team_id": 22,
  "is_active": true
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "username": "zhangsan",
    "display_name": "张三",
    "business_role": "employee",
    "department_id": 10,
    "team_id": 22,
    "manager_id": 31,
    "is_active": true
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/users/42/reset-password/

**请求 JSON**

```json
{
  "new_password": "新临时密码"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user_id": 42,
    "password_reset": true,
    "changed_at": "2026-10-11T10:20:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/company-items/?status=published&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 7,
        "title": "国庆值班安排",
        "summary": "查看假期值班表",
        "status": "published",
        "sort_order": 1,
        "published_at": "2026-10-01T09:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/company-items/

**请求 JSON**

```json
{
  "title": "国庆值班安排",
  "summary": "查看假期值班表",
  "body": "各部门值班安排详见附件。"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "title": "国庆值班安排",
    "summary": "查看假期值班表",
    "body": "各部门值班安排详见附件。",
    "status": "draft",
    "sort_order": 0,
    "created_at": "2026-10-11T10:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/company-items/7/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "title": "国庆值班安排",
    "summary": "查看假期值班表",
    "body": "各部门值班安排详见附件。",
    "status": "published",
    "sort_order": 1,
    "attachments": [],
    "published_at": "2026-10-01T09:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/admin/company-items/7/

**请求 JSON**

```json
{
  "summary": "查看各部门假期值班表",
  "body": "更新后的安排。"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "title": "国庆值班安排",
    "summary": "查看各部门假期值班表",
    "body": "更新后的安排。",
    "status": "published",
    "sort_order": 1,
    "updated_at": "2026-10-11T10:10:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/company-items/7/publish/

**请求 JSON**

```json
{
  "sort_order": 1
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "status": "published",
    "published_at": "2026-10-11T10:15:00+08:00",
    "sort_order": 1
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/company-items/7/offline/

**请求 JSON**

```json
{
  "reason": "内容已过期"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "status": "offline",
    "offline_at": "2026-10-11T10:20:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### PUT /api/v1/admin/company-items/order/

**请求 JSON**

```json
{
  "item_ids": [
    7,
    9,
    5
  ]
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "updated": 3,
    "items": [
      {
        "id": 7,
        "sort_order": 1
      },
      {
        "id": 9,
        "sort_order": 2
      },
      {
        "id": 5,
        "sort_order": 3
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/admin/company-items/7/attachments/

**请求：** `multipart/form-data`；字段 `file` 为文件二进制内容。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 88,
    "company_item_id": 7,
    "original_name": "值班表.pdf",
    "mime_type": "application/pdf",
    "size_bytes": 204800,
    "url": "/media/company-items/值班表.pdf"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/audit-logs/?start_date=2026-10-01&end_date=2026-10-31&module=users

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 501,
        "actor_id": 1,
        "actor_name": "系统管理员",
        "module": "users",
        "action": "update",
        "object_type": "user",
        "object_id": "42",
        "result": "success",
        "created_at": "2026-10-11T10:10:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```


### 4.3 Android 登录、首页与重点任务

#### POST /api/v1/app/auth/token/

**请求 JSON**

```json
{
  "username": "zhangsan",
  "password": "示例密码"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<app_jwt_token>",
    "refresh_token": "<app_refresh_token>",
    "user": {
      "id": 42,
      "username": "zhangsan",
      "role": "employee",
      "display_name": "张三"
    }
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/auth/token/refresh/

**请求 JSON**

```json
{
  "refresh_token": "<app_refresh_token>"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "<new_app_jwt_token>"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/me/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "display_name": "张三",
    "business_role": "employee",
    "department": {
      "id": 10,
      "name": "产品部"
    },
    "team": {
      "id": 21,
      "name": "客户端组"
    },
    "manager": {
      "id": 31,
      "display_name": "李经理"
    }
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/app/me/

**请求 JSON**

```json
{
  "display_name": "张三"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "display_name": "张三",
    "business_role": "employee",
    "department_name": "产品部",
    "team_name": "客户端组"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/home/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "company_items": [
      {
        "id": 7,
        "title": "国庆值班安排",
        "summary": "查看值班表"
      }
    ],
    "company_top_tasks": [
      {
        "id": 101,
        "title": "完成需求梳理",
        "completion_status": "unfinished",
        "due_at": "2026-10-20T18:00:00+08:00"
      }
    ],
    "personal_focus_tasks": [
      {
        "id": 101,
        "title": "完成需求梳理",
        "sort_order": 1
      }
    ],
    "today_log": {
      "id": 301,
      "status": "submitted",
      "summary": "完成接口梳理"
    }
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/company-items/?page=1&page_size=10

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 7,
        "title": "国庆值班安排",
        "summary": "查看值班表",
        "published_at": "2026-10-01T09:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/company-items/7/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 7,
    "title": "国庆值班安排",
    "summary": "查看值班表",
    "body": "各部门值班安排详见附件。",
    "attachments": [],
    "published_at": "2026-10-01T09:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/home/personal-focus/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tasks": [
      {
        "task_id": 101,
        "title": "完成需求梳理",
        "sort_order": 1,
        "completion_status": "unfinished"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### PUT /api/v1/app/home/personal-focus/

**请求 JSON**

```json
{
  "task_ids": [
    101,
    108,
    105
  ]
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tasks": [
      {
        "task_id": 101,
        "sort_order": 1
      },
      {
        "task_id": 108,
        "sort_order": 2
      },
      {
        "task_id": 105,
        "sort_order": 3
      }
    ]
  },
  "requestId": "<uuid>"
}
```


### 4.4 Android 任务、审批与验收

任务列表接口统一返回分页结果；示例中的筛选参数均在 URL 查询字符串中，不放在 GET 请求体。

#### GET /api/v1/app/tasks/mine/?completion_status=unfinished&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 101,
        "title": "完成需求梳理",
        "creator_name": "李经理",
        "assignee_name": "张三",
        "completion_status": "unfinished",
        "approval_status": "approved",
        "due_at": "2026-10-20T18:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/tasks/created/?completion_status=all&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 102,
        "title": "整理测试报告",
        "creator_name": "张三",
        "assignee_name": "张三",
        "completion_status": "unfinished",
        "approval_status": "approved",
        "due_at": "2026-10-21T18:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/tasks/subordinates/?user_id=31&keyword=王&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 103,
        "title": "补充接口测试",
        "creator_name": "王五",
        "assignee": {
          "id": 54,
          "display_name": "赵六"
        },
        "completion_status": "unfinished",
        "due_at": "2026-10-22T18:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/task-approvals/inbox/?page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "task_id": 104,
        "title": "整理客户反馈",
        "creator": {
          "id": 31,
          "display_name": "李经理"
        },
        "assignee": {
          "id": 42,
          "display_name": "张三"
        },
        "approval_status": "pending",
        "submitted_at": "2026-10-11T09:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/task-approvals/outbox/?page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "task_id": 104,
        "title": "整理客户反馈",
        "approver": {
          "id": 1,
          "display_name": "王总"
        },
        "approval_status": "pending",
        "submitted_at": "2026-10-11T09:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/tasks/101/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 101,
    "creator": {
      "id": 31,
      "display_name": "李经理"
    },
    "assignee": {
      "id": 42,
      "display_name": "张三"
    },
    "title": "完成需求梳理",
    "description": "整理本周收集到的需求并形成结论",
    "required_materials": "需求清单与评审结论",
    "association_note": "与 Q4 项目方案相关",
    "is_cross_department": false,
    "due_at": "2026-10-20T18:00:00+08:00",
    "approval_status": "approved",
    "completion_status": "unfinished",
    "lifecycle_status": "active",
    "updates": [],
    "submissions": []
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/tasks/

**请求 JSON**

```json
{
  "assignee_id": 42,
  "title": "完成客户需求梳理",
  "description": "整理需求并形成结论",
  "required_materials": "需求清单与评审结论",
  "due_at": "2026-10-20T18:00:00+08:00",
  "association_note": "与 Q4 项目方案相关"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 105,
    "creator_id": 42,
    "assignee_id": 42,
    "title": "完成客户需求梳理",
    "approval_status": "pending",
    "completion_status": "unfinished",
    "lifecycle_status": "active",
    "due_at": "2026-10-20T18:00:00+08:00",
    "created_at": "2026-10-11T10:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/cross-department-tasks/

**请求 JSON**

```json
{
  "assignee_id": 63,
  "title": "提供客户数据分析",
  "description": "分析本季度客户反馈",
  "required_materials": "分析结论",
  "due_at": "2026-10-25T18:00:00+08:00",
  "association_note": "跨部门协作事项说明"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 106,
    "creator_id": 31,
    "assignee_id": 63,
    "title": "提供客户数据分析",
    "is_cross_department": true,
    "approval_status": "pending",
    "completion_status": "unfinished",
    "lifecycle_status": "active",
    "due_at": "2026-10-25T18:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/app/tasks/105/

**请求 JSON**

```json
{
  "title": "完成客户需求与范围梳理",
  "due_at": "2026-10-21T18:00:00+08:00"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 105,
    "title": "完成客户需求与范围梳理",
    "due_at": "2026-10-21T18:00:00+08:00",
    "approval_status": "pending",
    "updated_at": "2026-10-11T10:15:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/tasks/105/approval/

**请求 JSON**

```json
{
  "decision": "approved",
  "comment": "目标和截止时间明确"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "task_id": 105,
    "approval_status": "approved",
    "decision": "approved",
    "decided_at": "2026-10-11T10:20:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/tasks/101/updates/

**请求 JSON**

```json
{
  "content": "已完成访谈提纲，正在整理反馈。"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 801,
    "task_id": 101,
    "author_id": 42,
    "content": "已完成访谈提纲，正在整理反馈。",
    "created_at": "2026-10-11T11:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/tasks/101/submissions/

**请求 JSON**

```json
{
  "completion_note": "已整理并与产品组确认需求。",
  "materials_note": "需求清单.xlsx，评审结论.md"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 901,
    "task_id": 101,
    "review_status": "waiting",
    "completion_note": "已整理并与产品组确认需求。",
    "materials_note": "需求清单.xlsx，评审结论.md",
    "submitted_at": "2026-10-11T16:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/tasks/101/submissions/901/review/

**请求 JSON**

```json
{
  "decision": "accepted",
  "comment": "材料完整，验收通过"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "submission_id": 901,
    "review_status": "accepted",
    "reviewed_by_id": 31,
    "reviewed_at": "2026-10-11T16:30:00+08:00",
    "task_completion_status": "completed",
    "completed_at": "2026-10-11T16:30:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/tasks/105/cancel/

**请求 JSON**

```json
{
  "reason": "计划调整，该任务不再需要"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "task_id": 105,
    "lifecycle_status": "cancelled",
    "cancelled_at": "2026-10-11T17:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/organization/direct-reports/?keyword=张

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 42,
        "display_name": "张三",
        "business_role": "employee",
        "department_name": "产品部",
        "team_name": "客户端组"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/organization/visible-subordinates/?keyword=张&department_id=10

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 42,
        "display_name": "张三",
        "business_role": "employee",
        "department_name": "产品部",
        "team_name": "客户端组",
        "level": 2
      }
    ]
  },
  "requestId": "<uuid>"
}
```


审批退回使用同一路径，`decision` 改为 `returned` 且必须提供原因：

```json
{
  "decision": "returned",
  "comment": "请补充任务交付标准后重新提交。"
}
```

`POST /api/v1/app/tasks/105/approval/` 的响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "task_id": 105,
    "approval_status": "returned",
    "decision": "returned",
    "comment": "请补充任务交付标准后重新提交。",
    "decided_at": "2026-10-11T10:20:00+08:00"
  },
  "requestId": "<uuid>"
}
```

验收退回同理，使用 `POST /api/v1/app/tasks/{id}/submissions/{submission_id}/review/`：

```json
{
  "decision": "returned",
  "comment": "请补充分析结论后再次提交。"
}
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "submission_id": 901,
    "review_status": "returned",
    "reviewed_by_id": 31,
    "review_comment": "请补充分析结论后再次提交。",
    "reviewed_at": "2026-10-11T16:30:00+08:00",
    "task_completion_status": "unfinished"
  },
  "requestId": "<uuid>"
}
```

### 4.5 Android 日志

#### GET /api/v1/app/logs/calendar/?year=2026&month=10&user_id=42

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "year": 2026,
    "month": 10,
    "days": [
      {
        "date": "2026-10-10",
        "has_submitted_log": true
      },
      {
        "date": "2026-10-11",
        "has_submitted_log": true
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/logs/?user_id=42&start_date=2026-10-01&end_date=2026-10-31&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 301,
        "author_id": 42,
        "author_name": "张三",
        "log_date": "2026-10-11",
        "status": "submitted",
        "completed_work": "完成接口梳理",
        "next_day_plan": "补充测试用例",
        "submitted_at": "2026-10-11T18:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/logs/301/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 301,
    "author": {
      "id": 42,
      "display_name": "张三"
    },
    "log_date": "2026-10-11",
    "status": "submitted",
    "completed_work": "完成接口梳理",
    "next_day_plan": "补充测试用例",
    "created_at": "2026-10-11T16:00:00+08:00",
    "submitted_at": "2026-10-11T18:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/logs/today/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "log_date": "2026-10-11",
    "log": null,
    "status": "not_created"
  },
  "requestId": "<uuid>"
}
```

#### PUT /api/v1/app/logs/today/

**请求 JSON**

```json
{
  "completed_work": "完成接口梳理",
  "next_day_plan": "补充测试用例"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 302,
    "log_date": "2026-10-11",
    "status": "draft",
    "completed_work": "完成接口梳理",
    "next_day_plan": "补充测试用例",
    "updated_at": "2026-10-11T17:30:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/logs/today/submit/

**请求 JSON**

```json
{}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 302,
    "log_date": "2026-10-11",
    "status": "submitted",
    "submitted_at": "2026-10-11T18:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/log-viewers/?keyword=张

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 42,
        "display_name": "张三",
        "business_role": "employee",
        "department_name": "产品部",
        "team_name": "客户端组"
      }
    ]
  },
  "requestId": "<uuid>"
}
```


### 4.6 Android 月、周、日视图

#### GET /api/v1/app/work-views/month/?user_id=42&year=2026&month=10

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user_id": 42,
    "year": 2026,
    "month": 10,
    "tasks": [
      {
        "task_id": 101,
        "title": "完成需求梳理",
        "start_date": "2026-10-05",
        "due_date": "2026-10-20",
        "completion_status": "unfinished",
        "is_personal_focus": true
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/work-views/week/?user_id=42&date=2026-10-11

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user_id": 42,
    "week_start": "2026-10-05",
    "week_end": "2026-10-11",
    "days": [
      {
        "date": "2026-10-05",
        "weekday": "周一"
      },
      {
        "date": "2026-10-11",
        "weekday": "周日"
      }
    ],
    "all_day_tasks": [
      {
        "task_id": 101,
        "title": "完成需求梳理",
        "start_date": "2026-10-05",
        "due_date": "2026-10-20",
        "completion_status": "unfinished"
      }
    ],
    "events": [
      {
        "id": "update-801",
        "date": "2026-10-11",
        "occurred_at": "2026-10-11T11:00:00+08:00",
        "task_id": 101,
        "task_title": "完成需求梳理",
        "event_type": "update",
        "summary": "已完成访谈提纲，正在整理反馈。"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/work-views/day/?user_id=42&date=2026-10-11

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user_id": 42,
    "date": "2026-10-11",
    "all_day_tasks": [
      {
        "task_id": 101,
        "title": "完成需求梳理",
        "start_date": "2026-10-05",
        "due_date": "2026-10-20",
        "completion_status": "unfinished"
      }
    ],
    "events": [
      {
        "id": "submission-901",
        "occurred_at": "2026-10-11T16:00:00+08:00",
        "task_id": 101,
        "task_title": "完成需求梳理",
        "event_type": "submitted",
        "summary": "已整理并与产品组确认需求。"
      }
    ]
  },
  "requestId": "<uuid>"
}
```


### 4.7 Android AI 功能

#### GET /api/v1/app/ai/consent/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "has_consented": false,
    "current_policy_version": "1.0",
    "consented_at": null
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/ai/consent/

**请求 JSON**

```json
{
  "policy_version": "1.0",
  "consent": true
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "has_consented": true,
    "policy_version": "1.0",
    "consented_at": "2026-10-11T12:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/ai/conversations/?type=work&page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 700,
        "type": "work",
        "title": "本周工作总结",
        "created_at": "2026-10-11T12:00:00+08:00",
        "updated_at": "2026-10-11T12:10:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/ai/conversations/

**请求 JSON**

```json
{
  "type": "work",
  "title": "本周工作总结"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 700,
    "type": "work",
    "title": "本周工作总结",
    "created_at": "2026-10-11T12:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/ai/conversations/700/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 700,
    "type": "work",
    "title": "本周工作总结",
    "created_at": "2026-10-11T12:00:00+08:00",
    "updated_at": "2026-10-11T12:10:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### DELETE /api/v1/app/ai/conversations/700/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deleted": true
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/ai/conversations/700/messages/?page=1&page_size=20

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 701,
        "role": "assistant",
        "content": "我可以帮你整理本周的工作重点。",
        "status": "completed",
        "created_at": "2026-10-11T12:10:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/ai/conversations/700/messages/

**请求 JSON**

```json
{
  "content": "请根据我选择的日志总结本周工作。",
  "references": [
    {
      "type": "log",
      "id": 301
    }
  ]
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user_message": {
      "id": 702,
      "role": "user",
      "content": "请根据我选择的日志总结本周工作。",
      "created_at": "2026-10-11T12:11:00+08:00"
    },
    "assistant_message": {
      "id": 703,
      "role": "assistant",
      "content": "本周主要完成了接口梳理，并计划补充测试用例。",
      "status": "completed",
      "created_at": "2026-10-11T12:11:02+08:00"
    }
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/ai/mbti/questions/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "questionnaire_version": "v1",
    "questions": [
      {
        "id": "q1",
        "text": "做决定时，我更倾向于……",
        "options": [
          {
            "id": "a",
            "text": "先看事实"
          },
          {
            "id": "b",
            "text": "先看可能性"
          }
        ]
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/ai/mbti/answers/

**请求 JSON**

```json
{
  "questionnaire_version": "v1",
  "answers": [
    {
      "question_id": "q1",
      "option_id": "a"
    }
  ]
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "result_code": "ISTJ",
    "summary": "偏好有计划地处理具体事务。",
    "completed_at": "2026-10-11T12:20:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/ai/mbti/result/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "result_code": "ISTJ",
    "summary": "偏好有计划地处理具体事务。",
    "questionnaire_version": "v1",
    "completed_at": "2026-10-11T12:20:00+08:00"
  },
  "requestId": "<uuid>"
}
```


### 4.8 错误响应示例

所有业务接口的失败响应也使用相同外层结构；字段校验信息放在 `data` 中：

```json
{
  "code": 400,
  "message": "请求参数不正确",
  "data": {
    "details": {
      "due_at": [
        "截止时间不能为空"
      ]
    }
  },
  "requestId": "<uuid>"
}
```

权限错误示例：

```json
{
  "code": 403,
  "message": "无权查看该任务",
  "data": {},
  "requestId": "<uuid>"
}
```

## 5. 统一响应结构约定

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "next": null,
    "previous": null,
    "results": [
      {
        "id": 101,
        "title": "完成客户需求梳理"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

以上业务接口示例统一采用 `code/message/data/requestId` 外壳。成功时 `code` 为 `200`、`message` 为 `success`；分页接口的 `count/next/previous/results` 位于 `data` 内。失败时 `code` 与 HTTP 状态码一致，例如 `400`、`401`、`403`、`404`、`409`，`data` 内放错误详情。`requestId` 为每次请求生成的追踪标识，应同时写入后端日志。

`/api/schema/` 返回 OpenAPI 文档本身，`/api/docs/` 和 `/api/redoc/` 返回 HTML，因此它们不套业务响应外壳。文件上传请求使用 `multipart/form-data`，其 JSON 响应仍使用统一外壳。

## 6. 联调前待定项

1. 最终是否采用 `/api/v1` 以及当前 `/api/auth/token/` 是否迁移/兼容。
2. 管理员与业务用户采用独立 Django 用户模型还是同一认证表的类型隔离；JWT 的受众和密钥策略。
3. 任务普通创建的可分配人规则、退回后编辑重提规则、取消任务在列表中的呈现方式，以及“公司十大任务”来源排序与最新扁平任务模型的映射。
4. 组织调整后，历史日志/任务查看权限按当前组织关系还是创建时组织快照计算。
5. 事项附件的存储方式、文件大小/类型限制，以及 AI 厂商数据留存、MBTI 题目和计分方案。
6. 精确分页上限、字段长度、错误码清单、访问频率限制和刷新令牌有效期。

## 7. 与 Swagger 的关系

本设计稿是接口契约讨论稿。开发接口时应在 DRF View/Serializer 上补充中文 `summary`、`description`、参数和响应 Schema，使 `/api/docs/` 中的契约与实现同步；不要把本文档中尚未实现的路径当成 Swagger 已提供的接口。使用步骤见 [`swagger-guide.md`](../cooperate/swagger-guide.md)。
