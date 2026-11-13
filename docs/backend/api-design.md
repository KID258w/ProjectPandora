# 后端 API 设计

> **Issue #6 实施更新**：账号录入、组织列表/创建与两端认证已实现。各接口请求/响应示例、错误、分页、身份规则以 [账号录入与登录契约](account-provisioning.md) 为准。旧 `/api/auth/token/` 入口已移除。

> **Issue #15 实施更新**：部门/团队详情与启停、员工筛选与身份/组织调整、管理概况与 Web 管理员操作审计、App 个人资料/密码/头像已实现（见 2.1 节契约）。头像二进制保存在用户数据库记录中，可在同一账号的不同设备读取。以下未标注已实现的业务内容仍为规划。

> **员工 App 操作审计（Issue #30 已实现）**：独立记录 App 成功登录和成功写操作，保存任务、日志等允许恢复的变更前后值，并由 Web 管理员只读核查；与已实现的 Web 管理员操作审计分开，契约见 2.2 节。

> **Issue #20 后端实施更新**：`/api/v1/app/logs/`、`logs/calendar/`、`logs/{id}/`、`logs/{id}/comments/`、`logs/mine/{log_date}/`、`logs/mine/{log_date}/submit/`、`log-viewers/` 与 `tags/` 已实现。日期窗口按服务端 `TIME_ZONE` 的自然日计算。日志列表对本人可返回草稿，对下属只返回已提交；日历标记仅包含已提交。可查看日志的 App 用户可读取和添加评论，评论与日志正文分开保存。创始人创建标签时提交预设颜色，启用标签可修改名称和颜色；改色同步影响已有任务、日志及任务条颜色。启用名称冲突返回 `409`，删除为不可逆停用。首页最近五篇已提交日志通过 `apps.logs.services.recent_submitted_logs` 提供数据源，首页聚合接口属于 Issue #22。

> **Issue #21 后端实施更新**：第 3.2、4.4 节所列任务、审批、进展、提交/验收、取消、组织候选人与站内通知接口已实现。任务创建、进展和完成提交返回 `201`，其他写操作返回 `200`。任务摘要和详情增加可空的 `estimated_start_at`，表示预计开始时间；普通及下属任务列表支持按预计开始日期与截止日期的起止范围筛选，日期边界均包含在内。未填写预计开始时间的任务不匹配预计开始日期范围筛选。默认仅返回活动任务。通知列表只读取，不自动标记已读；用户点击单条通知时，客户端将该通知 ID 送至 `mark-read/`；打开或浏览通知列表不会自动标记已读。任务详情返回创建人/负责人、状态、标签、审批历史、进展和提交记录；无权访问的详情返回 `404`。任务定义变更历史不保存密码等身份资料。

> **Issue #8 每日事项后端实施更新**：新增用户隔离的个人每日事项表与 `/api/v1/app/personal-items/` 列表、创建、详情、编辑、删除和拖动接口；列表接口同时返回请求日期范围内复用公司工作视图数据源的 `calendar_days`。首页通过 `GET /api/v1/app/home/` 返回最多五条日期范围覆盖业务时区今天的本人事项。该功能不接入公司任务、标签关联、日志或审计。首页旧“个人十大重点”相关字段、接口和数据保留兼容，不会自动转换成每日事项；新 App 使用每日事项面板。

> 维护说明：由项目统筹与后端同学共同维护，作为 API 设计的持续文档。接口路径、字段名和错误格式可随开发协商调整。  
> 依据：`docs/requirements/01` 至 `09`、`docs/cooperate/swagger-guide.md` 与当前 Django 骨架。需求变化时以最新确认的需求为准。

## 1. 设计约定

### 1.1 当前实现与目标接口

当前已实现健康检查、文档、版本化账号/组织接口、Issue #20、#21、#22 的业务端接口及 Issue #29 的工作视图接口。版本化 API 已采用统一外层响应；健康检查保持原响应。

| 当前已存在 | 路径 |
|---|---|
| 健康检查 | `GET /api/health/` |
| 获取 JWT | `POST /api/v1/admin/auth/token/`、`POST /api/v1/app/auth/token/` |
| 刷新 JWT | `POST /api/v1/admin/auth/token/refresh/`、`POST /api/v1/app/auth/token/refresh/` |
| 管理概况 | `GET /api/v1/admin/dashboard/` |
| 组织详情/启停 | `GET, PATCH /api/v1/admin/departments/{id}/`、`GET, PATCH /api/v1/admin/teams/{id}/` |
| 员工筛选/详情/调整 | `GET /api/v1/admin/users/`、`GET, PATCH /api/v1/admin/users/{id}/` |
| 管理操作审计 | `GET /api/v1/admin/audit-logs/` |
| App 个人资料、头像与密码 | `GET, PATCH /api/v1/app/me/`、`GET, PUT /api/v1/app/me/avatar/`、`POST /api/v1/app/me/password/` |
| App 日志与授权查看对象 | `GET /api/v1/app/logs/`、`GET /api/v1/app/logs/calendar/`、`GET /api/v1/app/logs/{id}/`、`GET, POST /api/v1/app/logs/{id}/comments/`、`GET, PUT /api/v1/app/logs/mine/{log_date}/`、`POST /api/v1/app/logs/mine/{log_date}/submit/`、`GET /api/v1/app/log-viewers/` |
| App 标签 | `GET, POST /api/v1/app/tags/`、`PATCH, DELETE /api/v1/app/tags/{id}/` |
| App 任务、审批及历史 | `GET, POST /api/v1/app/tasks/`、`GET /api/v1/app/tasks/pinned/`、`POST, DELETE /api/v1/app/tasks/{id}/pin/`、`GET /api/v1/app/tasks/subordinates/`、`GET, PATCH /api/v1/app/tasks/{id}/`、`GET /api/v1/app/tasks/{id}/history/`、`GET /api/v1/app/task-approvals/inbox/`、`GET /api/v1/app/task-approvals/outbox/`，以及第 3.2 节所列操作路径 |
| App 任务通知和人员选择 | `GET /api/v1/app/notifications/`、`GET /api/v1/app/notifications/unread-count/`、`POST /api/v1/app/notifications/mark-read/`、`GET /api/v1/app/organization/assignable-users/`、`GET /api/v1/app/organization/visible-subordinates/` |
| App 工作视图 | `GET /api/v1/app/work-views/month/`、`GET /api/v1/app/work-views/week/`、`GET /api/v1/app/work-views/day/` |
| App 每日事项（Issue #8） | `GET, POST /api/v1/app/personal-items/`、`GET, PATCH, DELETE /api/v1/app/personal-items/{id}/`、`POST /api/v1/app/personal-items/{id}/move/` |
| 公司重要事项管理（管理端） | `GET, POST /api/v1/admin/company-items/`、`GET, PATCH /api/v1/admin/company-items/{id}/`、`POST .../publish/`、`POST .../offline/`、`PUT .../order/`、`POST .../attachments/`、`DELETE .../attachments/{attachment_id}/` |
| App 首页数据与公司事项 | `GET /api/v1/app/home/`、`GET /api/v1/app/company-items/`、`GET /api/v1/app/company-items/{id}/`、`GET /api/v1/app/home/company-top-tasks/`、`GET, PUT /api/v1/app/home/personal-focus/`、`GET .../personal-focus/candidates/` |
| OpenAPI Schema / Swagger UI / ReDoc | `/api/schema/`、`/api/docs/`、`/api/redoc/` |

Issue #8 已实现路由：`GET, POST /api/v1/app/personal-items/`、`GET, PATCH, DELETE /api/v1/app/personal-items/{id}/`、`POST /api/v1/app/personal-items/{id}/move/`；现有 `GET /api/v1/app/home/` 响应增加 `personal_daily_items`。旧个人重点字段、路由和数据在兼容期内保留。

下文 `/api/v1/...` 中 Issue #6 的认证、me、部门/团队/用户列表与创建已实现；Issue #15 的部门/团队详情与启停、员工筛选与身份/组织调整、管理概况、Web 管理员操作审计及 App 个人资料与密码已实现（契约见 2.1）；Issue #20 的日志和标签接口、Issue #21 的任务、审批、通知接口、Issue #22 的公司重要事项、首页四面板与个人重点接口，以及 Issue #30 的员工 App 操作审计（见 2.2）均已实现。

需求 09 的每日事项接口与首页内嵌月历已由 Issue #8 实现。月历由每日事项日期范围接口读取；`GET /api/v1/app/home/` 中保留的 `personal_daily_items` 是兼容摘要，不是首页完整月历的数据源。每日事项替代首页旧个人重点面板的数据用途，但不复用旧表，也不自动迁移旧个人重点数据。

事项附件后端实现约定（Issue #22）：管理端 `POST .../attachments/` 使用 `multipart/form-data`，字段名 `file`；开发环境文件写入 `MEDIA_ROOT`（默认 `backend/media/`）并由 Django 在 `DEBUG` 下提供 `/media/` 访问，正式部署须改为对象存储或由反向代理提供持久化目录。默认上限 10 MB，类型白名单为 PDF、PNG/JPEG、纯文本、Word、Excel，可用环境变量 `COMPANY_ITEM_ATTACHMENT_MAX_BYTES` 与 `COMPANY_ITEM_ATTACHMENT_MIME_TYPES` 覆盖。该能力及管理端上传/删除 API 保留在后端，但当前 Web 页面和 App 公司事项流程均不提供附件入口；App 正文接口不返回附件元数据或 URL。未来如需增加公司事项详情页及附件上传/下载，另行确认客户端需求和文件访问规则。当前任务模块的材料字段是文字说明，不包含文件附件；如需任务附件，应作为任务模块的独立需求设计上传/下载接口和存储规则。
| App 组织关系 | `GET /api/v1/app/organization/tree/`、`GET /api/v1/app/organization/members/{id}/`、`GET /api/v1/app/organization/members/{id}/avatar/` |
| OpenAPI Schema / Swagger UI / ReDoc | `/api/schema/`、`/api/docs/`、`/api/redoc/` |

下文 `/api/v1/...` 中 Issue #6 的认证、me、部门/团队/用户列表与创建已实现；Issue #15 的部门/团队详情与启停、员工筛选与身份/组织调整、管理概况、Web 管理员操作审计及 App 个人资料、头像和密码已实现（契约见 2.1）；Issue #20 的日志和标签接口及 Issue #21 的任务、审批、通知接口已实现；Issue #30 的员工 App 操作审计已实现（见 2.2）。其余未标注已实现的接口不代表已上线。

### 1.2 通用约定

- 传输：HTTPS 上的 REST/JSON；不需要 WebSocket。开发环境可用 HTTP。
- 建议前缀：`/api/v1/app/`（安卓业务端）与 `/api/v1/admin/`（Web 管理端），明确区分调用方和权限边界。
- 认证：`Authorization: Bearer <access_token>`。Web 管理账号与安卓业务账号分开认证，禁止业务账号通过管理端登录入口登录，反之亦然。
- 日期时间：请求与响应使用 ISO 8601；数据库统一存储带时区时间，客户端按 `Asia/Shanghai` 显示。纯日期使用 `YYYY-MM-DD`。
- 命名：JSON 字段建议统一使用 `snake_case`；主键统一为整数 ID 或 UUID，实际项目选定一种后保持一致。
- 列表：建议统一 `page`、`page_size` 查询参数；响应包含 `count`、`next`、`previous`、`results`。日历/视图等按日期区间查询的接口也应限制区间长度。
- 成功响应：业务接口统一使用 `{ "code": 200, "message": "success", "data": {}, "requestId": "<uuid>" }`；单条资源、操作结果或分页对象放入 `data`。
- 二进制头像接口 `GET /api/v1/app/me/avatar/` 是例外，直接返回受认证保护的图片字节；头像 PUT 成功后返回 `204` 空响应，不使用通用 JSON envelope。
- 错误：使用相同外层结构，`code` 与 HTTP 状态码一致，`message` 为可读说明，`data` 放字段校验或错误详情，`requestId` 用于日志追踪。
- 头像接口是错误响应格式的例外：上传校验和认证失败使用标准 DRF HTTP 错误响应；未设置头像的 GET 返回空的 `404`。
- 状态码：`400` 参数/业务校验失败，`401` 未登录或令牌失效，`403` 已登录但无权，`404` 对象不存在或对当前用户不可见，`409` 状态冲突，`500` 未预期服务错误。
- 每次请求都必须在服务端重新验证数据范围和对象权限；前端隐藏按钮不构成权限控制。

### 1.3 关键状态与权限规则

任务有三个相互独立的状态维度，避免把“等待审批”“等待验收”误当成任务完成状态：

| 维度 | 初步取值 | 含义 |
|---|---|---|
| `approval_status` | `pending`、`returned`、`approved` | 创建任务的上级审批；创始人创建的任务直接为 `approved` |
| `completion_status` | `unfinished`、`completed` | 业务任务只保留未完成、已完成两种完成状态 |
| `lifecycle_status` | `active`、`cancelled` | 任务是否被创建人取消；取消需记录原因，不等同于完成 |

审批通过后任务才进入创建人的“我创建的”和负责人的“我的任务”；审批中的任务分别出现在上级“待你审批”和创建人“待上级审批”。验收由直接派发者处理；创建人与负责人为同一人时，提交即完成。上级向下查看仅看下属承担的任务，不另提供“下属创建的任务”列表。部门经理向其他部门经理派发任务仍使用普通创建入口和同一审批流程；审批通过后负责人直接获得任务，不需要负责人另行接受。

## 2. Web 管理端 API

Web 管理端管理账号、组织、公司重要事项和审计记录；不提供常规任务、员工日志或 AI 数据接口。员工 App 操作记录接口是审计例外，只允许管理员读取操作元数据及限定的历史变更快照，不提供常规任务/日志列表或详情接口。以下为建议接口，`{id}` 表示资源 ID。

| 功能 | 方法与路径 | 用途 / 主要权限 |
|---|---|---|
| 管理端登录 | `POST /api/v1/admin/auth/token/` | 独立管理账号登录，签发管理端令牌 |
| 刷新令牌 | `POST /api/v1/admin/auth/token/refresh/` | 刷新管理端令牌 |
| 当前管理员 | `GET /api/v1/admin/me/` | 返回管理账号资料及管理权限 |
| 管理首页 | `GET /api/v1/admin/dashboard/` | 返回账号、组织、重要事项等管理统计；不汇总任务和日志 |
| 部门列表/创建 | `GET, POST /api/v1/admin/departments/` | 管理员查看（含停用项）或创建部门；列表承担启停操作入口 |
| 部门详情/更新 | `GET, PATCH /api/v1/admin/departments/{id}/` | 详情提供负责人、下属团队和成员只读信息；PATCH 只改名称、说明 |
| 团队列表/创建 | `GET, POST /api/v1/admin/teams/` | 查看或创建团队；创建时指定启用部门；列表承担启停操作入口 |
| 团队详情/更新 | `GET, PATCH /api/v1/admin/teams/{id}/` | 详情提供所属部门、负责人和成员只读信息；PATCH 只改名称、说明 |
| 员工账号列表/创建 | `GET, POST /api/v1/admin/users/` | 按姓名/手机号、身份、组织和状态筛选；创建需姓名、手机号、身份证号及组织身份，自动启用并一次性返回初始密码 |
| 员工账号详情/更新 | `GET, PATCH /api/v1/admin/users/{id}/` | 查看脱敏个人资料；PATCH 只改业务身份、部门/团队归属和启用状态 |
| 公司重要事项列表/创建 | `GET, POST /api/v1/admin/company-items/` | 查询或新建事项草稿 |
| 事项详情/更新 | `GET, PATCH /api/v1/admin/company-items/{id}/` | 编辑标题、摘要、正文；附件由下列现有管理端附件接口维护 |
| 发布/下线事项 | `POST /api/v1/admin/company-items/{id}/publish/`、`.../offline/` | 发布或下线事项；发布数量最多 10 条 |
| 调整事项排序 | `PUT /api/v1/admin/company-items/order/` | 按请求中的 ID 顺序批量保存手动排序 |
| 上传事项附件 | `POST /api/v1/admin/company-items/{id}/attachments/` | 后端已实现但当前 Web 页面不调用；附件存储和限制见本节上方 |
| 删除事项附件 | `DELETE /api/v1/admin/company-items/{id}/attachments/{attachment_id}/` | 后端已实现但当前 Web 页面不调用；删除指定附件引用及对应文件，仅管理员可操作 |
| 管理员操作记录 | `GET /api/v1/admin/audit-logs/` | 按时间、管理员、模块筛选 Web 管理操作；只读 |
| 员工 App 操作记录 | `GET /api/v1/admin/app-operation-logs/`、`GET /api/v1/admin/app-operation-logs/{id}/` | 全局最新成功 App 操作记录，可按员工筛选；列表仅返回摘要，详情才返回允许展示的修改前后值；只读 |

Issue #1 的账号创建对齐已实现：`POST /api/v1/admin/users/` 使用 `username` 接收手机号，要求身份证号，自动启用账号并只在本次成功响应返回后端生成的初始密码。下文的员工筛选/更新、组织维护和 App 自助资料修改已在 Issue #15 实现：列表保留停用对象供管理员查询，但用于员工身份/组织调整的候选项只返回启用员工、部门和团队；用户筛选支持姓名或手机号关键词、`department_id`、`team_id`、`business_role`、`is_active`。组织负责人在员工管理界面通过员工业务身份和组织归属调整，不提供组织详情页直接换负责人或独立负责人写接口。停用部门/团队前后端都要校验没有仍启用的下级组织或成员。团队的所属部门仅在创建时指定。手机号继续使用现有唯一 `username` 字段存储；App 登录 JSON 的 `username` 字段不改名，业务账号的值为手机号。

### 2.1 Issue2 账号与组织管理契约（已实现）

本节是 Issue2 的契约，已在 Issue #15 后端实现并同步到 Swagger。现有接口路径沿用 Issue #6，新增字段和行为与实现一致。

- 管理员员工列表和详情可读手机号、姓名与掩码字段 `id_number_masked`，不返回身份证号完整值或密码。`PATCH /admin/users/{id}/` 仅接受 `business_role`、`department_id`、`team_id`、`is_active`；拒绝姓名、手机号、身份证号、密码以及 `manager_id`。不提供 Web 密码重置接口。
- 部门详情读取负责人、下属团队和成员；团队详情读取所属部门、负责人和成员。没有启用负责人的 `manager`/`leader` 关系返回 `null`。资源 PATCH 可更新 `name`、`description`，或单独更新 `is_active`；列表开关只提交 `is_active`，详情编辑表单只提交 `name`、`description`。团队 PATCH 不接受 `department_id`。
- 每个部门最多一名启用部门经理，每个团队最多一名启用团队长；负责人允许为空。调整负责人须先撤销原负责人的负责人身份或停用原账号，再调整新负责人的身份及组织归属；已有启用负责人的组织不得再接收第二名启用负责人，冲突时返回对应身份字段的校验错误。后端不自动指定接任者；直属上级 `manager_id` 由后端维护，负责人空缺时允许受影响下属暂时无直属上级，新负责人创建、启用或任命后重建对应关系。
- 公司最多一名启用创始人，也允许暂时没有启用创始人。创始人停用或撤销身份时，部门经理的 `manager_id` 置空；创始人补齐后，后端重新关联直属部门经理。部门经理或团队长空缺时，同样只清空其直接下属的关系，补齐后重建。
- 已有员工可以在负责人空缺期间保留启用状态和原组织归属，但负责人链不完整时，受影响范围暂时不能使用 App。后端在 App 登录、令牌刷新和所有 App 业务接口中检查负责人链；登录/刷新返回 401，已登录请求返回 403。补齐负责人并恢复关系后，App 访问恢复。管理员 Web 管理接口不受影响。
- 新建、启用或转入一个岗位时，其必需的直属负责人必须已启用；任命负责人填补本级空缺时，也必须满足该候选人自身的直属负责人要求。停用组织前仍有启用下级对象时拒绝停用，并在校验错误中返回阻塞对象类型、数量及最多五个对象名称；Web 应将该错误完整显示给管理员。已停用员工、部门和团队不得作为身份/归属选择器的候选项；管理列表仍可筛选、查看及重新启用。
- 团队重新启用时所属部门必须启用；团队改名不能与同部门其他团队重名，冲突返回名称字段校验错误。
- 身份证号在所有业务账号中全局唯一，停用账号仍占用身份证号。创建和 App 修改时，后端去除首尾空格并将字母统一为大写后再保存和计算唯一哈希，因此末位 `x` 与 `X` 视为同一号码。迁移 0008 回填前检查原始历史重复值；迁移 0010 在规范化前检查大小写/空格规范化后重复的历史值，均只报告冲突账号 ID、不输出身份证号明文，解决冲突后方可重跑迁移。回退数据迁移保留哈希数据，避免暂时失去唯一性校验。
- App `GET /api/v1/app/me/` 不返回完整身份证号，并返回 `account_created_date`（账号创建日期，按 `Asia/Shanghai` 时区序列化），供 App 个人页以账号创建日期为起算日，按 Asia/Shanghai 日历周年算法计算并显示完整的“X年X个月X天”任职时长；字段结构保持不变。`PATCH /api/v1/app/me/` 允许员工本人修改 `display_name`、手机号（请求字段仍为 `username`，成功后更新登录标识）和 `id_number`，不接受身份、组织、直属上级或启用状态。姓名和身份证号可在同一请求中一起提交，也可分别修改；两者作为一个事务保存，任一字段校验失败时均不更新。手机号更新成功后，App 清除本机登录会话并返回登录页，提示员工使用新手机号重新登录。员工本人通过 `POST /api/v1/app/me/password/` 修改密码，提交当前密码、新密码和新密码确认；MVP 不要求短信验证，密码找回流程未定。密码更新成功后，App 清除本机登录会话并要求员工使用新密码重新登录；服务端不主动撤销已签发令牌，它们仍按现有有效期处理。
- App `GET /api/v1/app/me/` 和 `PATCH /api/v1/app/me/` 响应还包含当前账号的 `avatar_url`（未设置时为 `null`）。头像接口 `GET, PUT /api/v1/app/me/avatar/` 仅允许业务账号操作令牌所属账号；头像由 App 上传到用户数据库行，读取返回原始图片二进制，PUT 为 multipart 上传并替换头像。它不是 JSON envelope 响应；格式和大小限制见下方契约。Web 管理员不获得头像读写能力。

### 2.2 员工 App 操作审计契约（Issue #30 已实现）

员工 App 操作审计独立于 `GET /api/v1/admin/audit-logs/`：后者只返回 Web 管理员的管理操作。新接口只向有效的 Web 管理员令牌开放，不向业务 App 用户开放，不提供新增、修改、删除或恢复审计记录的接口。

系统在成功登录后及成功提交受支持的 App 状态变更后记录操作；写操作的业务变更与审计记录在同一数据库事务中提交，审计写入失败时业务变更一起回滚。审计事件范围为个人资料/头像/密码变更、任务及审批/进度/验收操作、日志创建/保存/提交/编辑/评论，以及创始人标签创建/修改/停用。失败操作、只读请求、无实际变更的重复保存、个人重点排序和通知已读不记录。

`module` 取值：`auth`、`profile`、`tasks`、`logs`、`tags`。`action` 取值：`login`、`create`、`update`、`resubmit`、`approve`、`return`、`progress`、`submit`、`accept`、`cancel`、`comment`、`deactivate`。

#### GET /api/v1/admin/app-operation-logs/

**权限：** 仅 Web 管理员。**查询参数：** `actor_id`（可选，按员工筛选）、`page`、`page_size`。未提供 `actor_id` 时查询全体员工。固定按 `created_at`、`id` 倒序分页；不得按任务或日志正文搜索。

**列表响应 JSON**（列表不返回修改前后正文）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 1,
    "results": [
      {
        "id": 501,
        "actor_id": 42,
        "actor_name": "张三",
        "actor_business_role": "employee",
        "actor_department_name": "产品部",
        "actor_team_name": "客户端组",
        "occurred_at": "2026-11-03T09:30:00+08:00",
        "module": "logs",
        "action": "update",
        "object_type": "daily_log",
        "object_id": "901",
        "object_label": "2026-11-03",
        "changed_fields": ["completed_work", "tag_ids"],
        "result": "success"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

姓名、业务职位、部门和团队名称，以及目标对象显示名，均取操作发生时的快照。对象显示名为任务标题、日志日期或标签名称等，不包含正文。`object_id` 为字符串形式；登录等无业务对象的记录中 `object_type`、`object_id` 和 `object_label` 为空字符串。

#### GET /api/v1/admin/app-operation-logs/{id}/

返回列表摘要字段，并额外返回 `before_data` 与 `after_data` 中允许展示的变更字段值。任务、日志及其进度/验收/评论等用于人工找回的业务内容可在详情中显示；密码、完整身份证号、令牌和头像二进制永不返回。标签关联的 `tag_ids` 快照使用 `[{"id": 标签ID, "name": "操作时标签名称"}]`；任务负责人的 `assignee_id` 快照使用 `{"id": 员工ID, "display_name": "操作时姓名"}`。名称均在操作时固化；旧记录若只有数字 ID，服务端读取时尽可能依据保留的标签或员工记录补出名称。创建操作的 `before_data` 为空。该接口不提供一键恢复，管理员需按正常业务流程人工处理。

列表与详情响应均设置 `Cache-Control: no-store`。未登录返回 `401`，非管理员返回 `403`，记录不存在返回 `404`。审计读取不改变记录状态，也不产生新的员工 App 操作记录。

## 3. Android 业务端 API

### 3.1 登录、用户与首页

| 方法与路径 | 用途 |
|---|---|
| `POST /api/v1/app/auth/token/` | 业务账号登录 |
| `POST /api/v1/app/auth/token/refresh/` | 刷新业务端令牌 |
| `GET /api/v1/app/me/` | 当前用户姓名、手机号、业务角色、部门/团队、直属上级等；不返回完整身份证号 |
| `PATCH /api/v1/app/me/` | 员工本人修改姓名、手机号、身份证号；手机号更新现有 `username` 登录标识 |
| `GET, PUT /api/v1/app/me/avatar/` | 读取、上传或替换当前业务用户自己的头像；图片二进制存入账号数据库记录 |
| `POST /api/v1/app/me/password/` | 员工本人验证当前密码后修改密码；不提供 Web 重置入口 |
| `GET /api/v1/app/home/` | 返回首页公司事项、任务及兼容摘要字段与任务通知未读数；最新 App 使用 `company_items`、`company_top_tasks` 展示上方概览卡片，并通过每日事项列表接口加载完整月历 |
| `GET /api/v1/app/personal-items/?start_date=&end_date=` | 返回当前账号在闭区间日期范围内的每日事项，以及该区间内已配置的法定节假日、休息日和调休上班标记；事项按范围相交筛选，返回全部结果供月历完整绘制，不分页 |
| `POST /api/v1/app/personal-items/` | 创建本人每日事项；归属人从令牌确定，不接受客户端传入 `owner_id` |
| `GET /api/v1/app/personal-items/{id}/` | 查看本人每日事项详情；返回说明及完整事项字段 |
| `PATCH /api/v1/app/personal-items/{id}/` | 部分更新本人事项标题、日期范围、说明或预设颜色；校验更新后的完整日期范围 |
| `POST /api/v1/app/personal-items/{id}/move/` | 拖动事项任务条时整体平移日期范围；请求目标起始日，服务端按原跨度计算新结束日 |
| `DELETE /api/v1/app/personal-items/{id}/` | 删除本人事项；删除确认由客户端负责，服务端物理删除记录 |
| `GET /api/v1/app/company-items/` | 为“事件纪”提供已发布公司事项列表，最多 10 条 |
| `GET /api/v1/app/company-items/{id}/` | 返回单条已发布事项正文数据，供列表项在当前页展开；不返回附件元数据或 URL，也不要求跳转到独立详情页 |

首页“任务看版”是根据当前用户承担的未完成任务计算出的最多 10 条，不是管理员维护的全公司排行榜；按当前需求文档规定的来源和截止时间排序。每日事项是员工自行创建的备忘录，首页只预览日期范围覆盖业务时区今天的本人事项，最多五条，创建和管理入口位于个人月历。旧个人重点接口仅为已有客户端兼容保留；新 App 不调用这些接口，也不把其任务数据展示为每日事项。

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/app/home/company-top-tasks/` | 返回当前用户“任务看版”完整列表，最多 10 条；保留现有接口路径 |
| `GET /api/v1/app/home/personal-focus/`（旧接口，弃用） | 为旧客户端返回个人重点任务及顺序；首次使用时初始化；不供每日事项页面调用 |
| `GET /api/v1/app/home/personal-focus/candidates/`（旧接口，弃用） | 为旧客户端返回可加入个人重点的本人未完成任务 |
| `PUT /api/v1/app/home/personal-focus/`（旧接口，弃用） | 为旧客户端保存任务 ID 有序数组；不写入每日事项 |

旧 `personal-focus` 路由及 `home_personal_focus_task`、`home_personal_focus_state` 数据在兼容期内保持独立，不自动复制或转换。具体移除时间另行确定。

### 3.2 任务中心、创建、审批与验收

| 方法与路径 | 用途 / 规则 |
|---|---|
| `GET /api/v1/app/tasks/` | 普通任务中心列表；`scope=all|mine|created`，支持预计开始日期、截止日期、完成状态及归档状态筛选；`all` 合并去重 |
| `GET /api/v1/app/tasks/pinned/` | 返回当前账号置顶的全部已审批且本人创建或承担的任务，按最近置顶时间倒序；不受普通列表筛选条件影响 |
| `POST /api/v1/app/tasks/{id}/pin/` | 将本人普通任务中心可见的已审批任务置顶；请求体为空，重复调用保持幂等 |
| `DELETE /api/v1/app/tasks/{id}/pin/` | 取消当前账号对该任务的置顶；请求体为空，重复调用保持幂等 |
| `GET /api/v1/app/tasks/subordinates/` | 当前用户有权查看的下属已审批任务；支持 `task_scope=assigned|created`、预计开始日期、截止日期、完成状态和授权下属筛选 |
| `GET /api/v1/app/task-approvals/inbox/` | 待当前上级审批任务；不提供已完成/未完成筛选 |
| `GET /api/v1/app/task-approvals/outbox/` | 当前用户创建、等待上级审批或已退回待修改的任务；不提供完成状态筛选 |
| `GET /api/v1/app/tasks/{id}/` | 任务详情；返回审批/验收历史、进展和全部标签；服务端校验原任务可见性，App 按创建人、直属审批人、负责人或只读查看者关系展示相应内容 |
| `GET /api/v1/app/tasks/{id}/history/` | 返回当前用户有权查看的任务创建及定义变更记录，含操作人、时间和变更字段；标签及负责人前后值包含 ID 与名称 |
| `POST /api/v1/app/tasks/` | 普通入口创建任务；可选提交 `tag_ids`；创始人可指派给本公司任一成员，其他角色按各自组织范围选择负责人；创建人有直属上级则进入待审批 |
| `PATCH /api/v1/app/tasks/{id}/` | 按原任务编辑权限修改任务；可选提交 `estimated_start_at`（ISO 8601 或 `null`）；计划开始或截止时间变更按关键定义重新审批；单独改标签不触发重新审批 |
| `POST /api/v1/app/tasks/{id}/resubmit/` | 创建人完成退回任务编辑后重新送直属上级审批 |
| `POST /api/v1/app/tasks/{id}/approval/` | 上级审批通过或退回；退回必须附原因 |
| `POST /api/v1/app/tasks/{id}/updates/` | 负责人追加进展说明；只追加记录，不设置百分比进度 |
| `POST /api/v1/app/tasks/{id}/submissions/` | 负责人提交完成说明及所需材料说明；不是创建任务表单 |
| `POST /api/v1/app/tasks/{id}/submissions/{submission_id}/review/` | 直接派发者验收通过或退回；验收通过后任务完成 |
| `POST /api/v1/app/tasks/{id}/cancel/` | 创建人取消本人创建的未完成任务，必须填写原因 |
| `GET /api/v1/app/notifications/` | 返回当前用户的任务通知列表，支持未读筛选和分页；未读筛选排除流程已结束的通知；仅通知接收人可读 |
| `GET /api/v1/app/notifications/unread-count/` | 返回当前用户未读且流程未结束的任务通知数量，供首页铃铛显示红点 |
| `POST /api/v1/app/notifications/mark-read/` | 将请求中明确列出的本人通知标记为已读；不改变任务状态 |
| `GET /api/v1/app/organization/assignable-users/` | 查询当前用户在普通任务表单中有权选择的负责人；创始人可查询本公司全体成员，其他角色按其派发范围返回，支持姓名关键词 |
| `GET /api/v1/app/organization/visible-subordinates/` | 查看对象选择器可访问的下属；逐级查看时关键词仅匹配授权下属姓名 |
| `GET /api/v1/app/organization/tree/` | 返回公司组织树；创始人与部门并列。所有 App 业务用户均可浏览；可选 `keyword` 按成员姓名或职级搜索，搜索时返回平铺人员列表 |
| `GET /api/v1/app/organization/members/{id}/` | 返回启用业务成员的只读资料；所有 App 业务用户均可读取，不可用于修改成员信息 |
| `GET /api/v1/app/organization/members/{id}/avatar/` | 返回启用业务成员已上传的头像二进制；所有 App 业务用户均可读取 |

逾期标签不新增任务列表 API 字段或数据库状态。App 根据任务列表已返回的 `approval_status`、`completion_status`、`lifecycle_status` 和 `due_at`，判断任务是否已审批、未完成、未取消且截止时间早于当前时间，并在列表中显示“逾期”标签；原完成状态筛选保持不变。

任务置顶为当前账号的个人偏好，不改变任务状态，不创建通知或操作审计记录。置顶列表只返回已审批且由当前用户创建或承担的任务，包含任务摘要；完成或取消状态不自动清除置顶。置顶区独立于普通列表筛选显示；App 将置顶任务从当前普通列表结果中去重。审批中心及下属只读任务列表不提供置顶接口权限。

`GET /api/v1/app/tasks/pinned/` 使用标准分页响应，结果按 `pinned_at` 倒序。置顶与取消置顶均使用空请求体；首次创建置顶关系返回 `201`，对已置顶任务重复调用返回 `200`；删除不存在的置顶关系也成功返回，不产生重复记录。尝试置顶待审批任务、或本人既非创建人也非负责人的任务，返回权限错误；不存在的任务返回未找到。

```http
POST /api/v1/app/tasks/101/pin/
Authorization: Bearer <access-token>
Content-Type: application/json
{}
```

```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": 101,
    "title": "完成客户需求梳理",
    "creator_name": "王世鑫",
    "assignee_name": "闵世宇",
    "approval_status": "approved",
    "completion_status": "unfinished",
    "lifecycle_status": "active"
  },
  "requestId": "<uuid>"
}
```

上例中的 `data` 展示任务摘要的主要字段。

取消置顶使用 `DELETE /api/v1/app/tasks/101/pin/`，成功时返回 `200`，`data` 为 `{"pinned": false}`。

任务详情的 Android 展示按当前用户关系区分：创建人和本轮直属审批人可查看审批状态及审批记录；非创建人/审批人的负责人和层级只读查看者不显示审批状态或审批记录。负责人及授权只读查看者可查看已审批任务的执行信息；具体操作仍由服务端逐项校验。用户同时具备多种关系时，客户端合并展示其有权查看的信息。创始人免审批任务不展示空审批记录区块。

普通任务列表通过一个筛选接口支持 `scope=all|mine|created`、`completion_status=all|unfinished|completed`、`lifecycle_status=active|cancelled|all`、`due_from`、`due_to`、`estimated_start_from` 和 `estimated_start_to`（启用预计开始日期筛选时，未设置预计开始时间的任务不匹配）；日期边界均包含在结果中，筛选精确到日。默认 `scope=mine`、`completion_status=all`、`lifecycle_status=active` 且不限日期。`scope=all` 返回本人承担或创建的已审批任务并按任务 ID 去重。取消任务保留为归档记录，可通过 `lifecycle_status=cancelled` 查询。下属任务接口支持 `task_scope=assigned|created`，只返回审批通过的记录；并使用相同的预计开始日期、截止日期、完成状态及归档状态筛选；具体人员筛选只能在服务端授权的下属范围内。审批列表仅按审批流程筛选。负责人选择接口只返回创建者有权派发给的对象：创始人为本公司全体成员；部门经理为本人、本部门团队长或其他部门经理；团队长为本人或本团队普通员工；普通员工仅本人。部门经理指派给其他部门经理仍调用统一创建接口，并由服务端按创建人的直属上级规则发起审批。任务被直属上级退回后，创建人通过 `PATCH` 修改，再调用 `POST /tasks/{id}/resubmit/` 新增审批轮次；审批通过后负责人、任务描述、预计开始时间、截止时间或预期材料等关键定义字段发生变化时，服务端自动重新送审并写入新审批轮次。单独修改标签不触发重新审批。

通知只通过 App 内列表提供，不发送操作系统推送。创建人有直属上级时，任务进入待审批状态即向该直属上级生成审批通知；每轮审批通过或退回后，另向创建人生成“审批通过”或“审批退回”结果通知，退回通知包含原因。审批通过后，他人创建并派发给当前用户的任务生成新任务通知。本人创建并由本人承担的任务不生成新任务通知。

任务通知类型为 `approval_required`、`approval_approved`、`approval_returned`、`assignment_received`、`task_cancelled`、`submission_received`、`submission_accepted` 和 `submission_returned`。`approval_required` 发给待审批的直属上级；审批通过或退回后结束该条待审批通知，并按审批轮次向创建人生成独立结果通知。结果通知保留本轮决定和意见，不因后续重新送审而覆盖。取消待审批任务时，关闭其待审批通知并通知当前待审批的直属上级；取消已审批且派发给他人的任务时，关闭尚未处理的派发/提交/退回通知并通知负责人。审批退回后、重新审批通过前取消的任务尚未进入负责人任务列表，不向负责人发送取消通知。提交完成结果通知直接派发人；验收通过或退回通知负责人，退回通知包含原因。重新提交会结束上一次验收退回通知。取消、验收或重新提交等流程变化通过 `resolved_at` 结束待处理通知，但审批结果事件通知不会因后续轮次自动结束。`unread_only=true` 与未读数只包含 `is_read=false` 且 `resolved_at=null` 的通知。

创建任务时 `assignee_id`、`title`、`description`、`due_at` 为必填字段；`estimated_start_at`、`required_materials`、`association_note` 和 `tag_ids` 为可选字段。`estimated_start_at` 是可空的计划开始时间，不能晚于必填的 `due_at`；编辑时可以传 ISO 8601 日期时间或 `null` 清除。截止时间仍精确到分钟，列表筛选 `due_from`、`due_to`、`estimated_start_from` 和 `estimated_start_to` 均为含边界的 `YYYY-MM-DD` 日期筛选。编辑任务使用部分更新，字段可按需提交。任务创建及编辑的 `TaskSummary`、`TaskDetail` 响应始终包含 `estimated_start_at`，未设置时返回 `null`。

建议创建任务请求示例：

```json
{
  "assignee_id": 42,
  "title": "完成客户需求梳理",
  "description": "整理本周收集到的需求并形成结论",
  "required_materials": "需求清单与评审结论",
  "estimated_start_at": "2026-10-19T09:30:00+08:00",
  "due_at": "2026-10-20T18:00:00+08:00",
  "association_note": "与 Q4 项目方案相关"
}
```

服务端根据登录人、组织关系和目标负责人决定是否需要审批，客户端不得自行传入/覆盖 `approval_status`、创建人或完成状态。接口不接收跨部门标记；是否允许负责人由服务端按角色、部门关系校验。关联说明为自由文本，不建立上级任务外键或任务树。任务创建请求可选 `tag_ids`；首次创建按数组顺序添加关联。更新请求只有在字段存在时才变更标签关联，`[]` 表示清空；保存时保留仍在目标集合中的原关联顺序，新增标签按数组顺序追加。停用标签只允许保留当前仍存在的关联，移除后不能重新关联。

### 3.3 日志

| 方法与路径 | 用途 / 规则 |
|---|---|
| `GET /api/v1/app/logs/calendar/` | 按 `year`、`month` 返回本人或选中查看对象已提交日志的日期标记 |
| `GET /api/v1/app/logs/` | 查询本人历史日志或有权查看的下属日志；按用户/日期筛选 |
| `GET /api/v1/app/logs/{id}/` | 查看日志内容及全部标签名称/颜色；管理者只读且只能查看已提交日志 |
| `GET /api/v1/app/logs/{id}/comments/` | 按创建时间查询当前用户可见日志的评论；沿用日志本身的可见范围 |
| `POST /api/v1/app/logs/{id}/comments/` | 为当前用户可见的日志添加评论；作者、权限范围内的管理者均可评论 |
| `GET /api/v1/app/logs/mine/{log_date}/` | 获取本人指定日期日志（含草稿及标签）；未创建时返回空表单态，并返回是否可编辑 |
| `PUT /api/v1/app/logs/mine/{log_date}/` | 新建或更新本人指定日期唯一日志，可选提交 `tag_ids`；只允许今天、昨天或前天 |
| `POST /api/v1/app/logs/mine/{log_date}/submit/` | 提交本人指定日期日志；只允许今天、昨天或前天 |
| `GET /api/v1/app/log-viewers/` | 返回当前用户可选择查看的下属，支持姓名、团队或部门关键词搜索 |

建议日志请求字段：`completed_work`（提交必填）、`next_day_plan`（可选）、`tag_ids`（可选标签 ID 数组）。MVP 不提供关联任务、问题与风险字段、附件和日志正文关键字搜索；标签选择器按标签名称搜索，不提供日志标签筛选。每人每天最多一条。作者可新建、修改及提交业务时区内今天、昨天和前天的日志；该窗口按自然日滚动，不是滚动 72 小时，超过前天的日期及未来日期拒绝修改正文并只读展示。读取本人历史记录仍允许。管理者查询草稿与无日志日期时不应泄露草稿内容；管理者不能修改日志正文，但可通过评论接口对授权范围内已提交日志进行评论。详情返回标签关联名称和颜色；已停用标签不标明状态。更新中省略 `tag_ids` 表示不改关联，空数组表示清空；保存时仅保留已有关系并新增/移除差异。评论长度为 1–2000 个字符；评论列表按创建时间升序，显示评论人姓名、职位和部门/团队（如有），日志作者评论额外标记 `is_log_author=true`。评论不修改日志正文、状态或 `updated_at`，不提供编辑、删除和回复接口。

### 3.4 月、周、日工作视图

> **Issue #29 后端已实现**：三个只读接口的 `year`/`month` 与 `date` 可省略，分别默认业务时区当前月、今天；`user_id` 省略时为本人。非法日期返回 `400`，越权目标返回 `403`。成员资料响应增加 `can_view_work_views`，用于显示“查看视图”入口；接口仍逐次独立校验权限。所有响应沿用 `code/message/data/requestId` 外层格式。

| 方法与路径 | 用途 |
|---|---|
| `GET /api/v1/app/work-views/month/?user_id=&year=&month=` | 月视图：返回完整周网格内日期跨度相交的已审批、未取消负责人任务（不按完成或逾期状态过滤）；相邻月份日期也参与任务条绘制。任务条颜色 `bar_color` 为首个添加的当前关联标签颜色，无标签时为中性灰色。`start_date/end_date` 是自然月边界，`calendar_start_date/calendar_end_date` 是可见网格边界；`calendar_days` 覆盖可见网格 |
| `GET /api/v1/app/work-views/week/?user_id=&date=` | 周视图：返回与所选周相交的负责人任务全天条（已完成、未完成或逾期均可），带 `bar_color`；另含日期条、按实际操作人归属的 24 小时时间轴动态事件，以及所选周的 `calendar_days` |
| `GET /api/v1/app/work-views/day/?user_id=&date=` | 日视图：返回日期跨度覆盖所选日的负责人任务条（已完成、未完成或逾期均可），带 `bar_color`；另含按实际操作人归属的 24 小时时间轴动态事件，以及顶部整周日期条范围的 `calendar_days` |

视图为只读聚合，不单独保存日历副本。普通 App 用户默认查询本人视图；查看下属视图统一从“组织关系”成员资料页的“查看视图”入口进入。`user_id` 可指定本人或当前用户有权限查看的下属，服务端每次请求均校验目标成员是否在该用户的工作视图授权范围内；组织树和成员资料可见不代表视图数据可见。任务条只返回已审批且未取消的负责人任务；任务是否完成或逾期均不作为独立过滤条件，是否显示只由任务日期跨度与所选月、周或日是否相交决定。日期跨度起点优先取 `estimated_start_at` 的业务时区日期；该字段为空时取任务原始 `created_at` 的业务时区日期，终点为截止日期。回退值仅用于展示，不是实际开始时间；待审批任务不进入任务条，审批通过或重新审批不会改写原始创建时间。月视图任务及周/日全天任务响应均提供 `bar_color`，服务端按当前仍关联标签中最早建立的关系确定颜色；停用标签仍提供原颜色，无关联标签时提供中性灰色。任务条仅显示标题，工作视图响应不提供供任务条展示的标签名称。

任务条与动态采用不同归属口径：月、周、日任务条按 `assignee_id` 查询；周/日动态按具体操作记录中的操作人查询。动态包括任务创建/提交审批、任务定义修改、重新提交审批、审批通过/退回、任务取消、负责人进展更新、完成提交、验收通过/退回；自我负责任务提交即完成只产生一条“提交并完成”动态。创建/修改/审批/取消等操作显示在实际操作人的视图，不复制给负责人；验收事件归验收人，不归负责人。待审批任务可有创建人或审批人的动态，但不显示任务条。每条动态还须通过当前请求人的任务可见权限校验，不能因操作人视图扩大任务详情访问权。

周/日动态响应至少包含 `occurred_at`、`task_id`、`task_title`、`event_type`、`tag_color`。`tag_color` 根据任务当前最早建立的关联标签计算；无标签时为中性灰色，不返回供动态卡片展示的标签名称。周、日动态卡片采用相同展示信息：时间点、任务标题和动态类型；响应不提供动态摘要字段。周视图全天任务可横跨多天；固定日期条和全天区域不随时间轴滚动，动态按实际发生日期/小时归列。

工作视图实际响应结构（以下均指外层 `data`）：

- 月：`{user_id, year, month, start_date, end_date, days}`。`days` 包含自然月内每日 `{date, bars, overflow_count}`；每个 `bars` 最多 3 条，`overflow_count` 是其余任务的准确数量。任务条为 `{task_id, title, bar_color, start_date, end_date}`，起止日保留原跨度，客户端按当前日裁剪绘制。任务条包含已审批、未取消且日期跨度与所选月份相交的任务，不按完成或逾期状态过滤。
- 周：`{user_id, start_date, end_date, dates, all_day_tasks, events}`。`dates` 为周一至周日 7 个日期；`all_day_tasks` 在任意所选周返回已审批、未取消且日期跨度与所选周相交的负责人任务，不按完成或逾期状态过滤。起点为预计开始日期，未填时回退到最初创建日期；`start_date`、`end_date` 为任务跨度与所选周的交集，起止日均包含。不相交的任务不返回。
- 日：`{user_id, date, start_date, end_date, dates, all_day_tasks, events}`。`dates` 是所选日期所在周；`all_day_tasks` 在任意所选日期返回已审批、未取消且日期跨度覆盖所选日的负责人任务，不按完成或逾期状态过滤。任务跨度采用预计开始日期（未填时为最初创建日期）至截止日期，起止日均包含；响应任务条的起止日均裁剪为所选日期。
- `events` 只包含 `{task_id, task_title, event_type, occurred_at, tag_color}`，按真实时间升序，时间戳带时区，客户端按业务时区分组到 24 小时时间轴。事件类型：`created`（免审批创建）、`created_and_submitted`（创建并送审）、`updated`、`resubmitted`、`approval_approved`、`approval_returned`、`cancelled`、`progress_updated`、`completion_submitted`、`submitted_and_completed`、`acceptance_approved`、`acceptance_returned`。首次送审与创建合并；重提使用新审批轮次的创建时间；自行完成只返回一条事件。因任务详情权限变化而当前无法查看的历史事件不返回。
- 任务条无标签时 `bar_color`、事件无标签时 `tag_color` 均为 `#D1D5DB`。响应无任务正文、进展摘要、标签名称或日志数据。

### 3.5 标签系统

| 方法与路径 | 用途 / 规则 |
|---|---|
| `GET /api/v1/app/tags/` | 所有 App 角色查询启用标签并按名称搜索；仅创始人可通过 `include_inactive=true` 查询停用标签，管理中心列表只展示启用标签 |
| `POST /api/v1/app/tags/` | 创始人在 App“我的页面”的标签管理中心创建标签，并从固定调色板中选择颜色 |
| `PATCH /api/v1/app/tags/{id}/` | 创始人修改启用标签名称和/或颜色；不接受状态字段 |
| `DELETE /api/v1/app/tags/{id}/` | 创始人不可逆停用标签；保留标签数据及任务/日志历史关联，不物理删除 |

上述标签管理接口仅接受 Android 业务用户令牌；标签管理操作仅创始人可用。所有 App 角色可在本人有权编辑的任务/日志表单中关联多个启用标签。详情返回关联标签的 ID、名称和颜色，但不向关联标签附加停用状态标记。任务/日志列表不增加标签筛选。

### 3.6 AI 地图与辅助功能

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
        "is_active": true
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
  "code": 201,
  "message": "success",
  "data": {
    "id": 10,
    "name": "产品部",
    "description": "产品研发",
    "is_active": true,
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
    "is_active": true,
    "manager": null,
    "teams": [],
    "members": [],
    "teams_count": 0,
    "active_users_count": 0
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
    "is_active": true,
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
        "is_active": true
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
  "code": 201,
  "message": "success",
  "data": {
    "id": 21,
    "name": "客户端组",
    "department_id": 10,
    "description": "安卓与 Web 客户端",
    "is_active": true
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
    "is_active": true,
    "leader": null,
    "members": [],
    "active_users_count": 0
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/admin/teams/21/（Issue2 目标；仅名称和说明）

**请求 JSON**

```json
{
  "name": "客户端研发组",
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
    "name": "客户端研发组",
    "department_id": 10,
    "description": "安卓与 Web 客户端",
    "is_active": true,
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
        "username": "13800000001",
        "display_name": "张三",
        "id_number_masked": "***************1234",
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

#### POST /api/v1/admin/users/（Issue1 账号创建对齐；已实现）

**请求 JSON**

```json
{
  "username": "13800000001",
  "id_number": "<身份证号>",
  "display_name": "张三",
  "business_role": "employee",
  "department_id": 10,
  "team_id": 21
}
```

**响应 JSON**

```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": 42,
  "username": "13800000001",
    "display_name": "张三",
    "account_type": "business",
    "business_role": "employee",
    "department_id": 10,
    "team_id": 21,
    "manager_id": 31,
    "is_active": true,
    "id_number_masked": "***************1234",
    "initial_password": "<仅本次响应返回的强随机初始密码>"
  },
  "requestId": "<uuid>"
}
```

客户端不得提交 `password`、`is_active` 或 `manager_id`。后端只保存密码哈希；成功响应设置 `Cache-Control: no-store`、`Pragma: no-cache` 和 `Expires: 0`。初始密码仅出现在这次创建响应，不写入后续读取、日志或审计数据。

#### GET /api/v1/admin/users/42/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "username": "13800000001",
    "display_name": "张三",
    "business_role": "employee",
    "department_id": 10,
    "team_id": 21,
    "manager_id": 31,
    "id_number_masked": "***************1234",
    "is_active": true,
    "created_at": "2026-10-11T10:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/admin/users/42/（Issue2 目标；覆盖旧草案）

**请求 JSON**

```json
{
  "business_role": "employee",
  "department_id": 10,
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
    "username": "13800000001",
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

Issue2 不提供 `POST /api/v1/admin/users/{id}/reset-password/`。员工密码由员工本人通过 App 修改；管理端不能代替员工修改密码。

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
  "code": 201,
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
    "published_at": "2026-10-01T09:00:00+08:00",
    "offline_at": null,
    "offline_reason": ""
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
    "offline_at": "2026-10-11T10:20:00+08:00",
    "offline_reason": "内容已过期"
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
  "code": 201,
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

#### DELETE /api/v1/admin/company-items/7/attachments/88/

**请求：** 无 JSON 请求体。仅能删除当前事项下的附件；不影响事项正文或其他附件。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": { "deleted": true },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/admin/audit-logs/?start_date=2026-10-01&end_date=2026-10-31&actor_id=1&module=users

**请求：** 无 JSON 请求体。`start_date`、`end_date` 按日期筛选，`module` 按模块筛选；可用 `actor_id` 按管理员 ID 筛选，必须为正整数，否则返回 `400` 字段校验错误。

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
    "username": "13800000001",
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
      "username": "13800000001",
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
    "username": "13800000001",
    "account_type": "business",
    "display_name": "张三",
    "id_number_masked": "***************1234",
    "business_role": "employee",
    "department_id": 10,
    "department_name": "产品部",
    "team_id": 21,
    "team_name": "客户端组",
    "manager_id": 31,
    "manager_name": "李经理",
    "is_active": true,
    "avatar_url": "/api/v1/app/me/avatar/",
    "account_created_date": "2026-11-06"
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/app/me/

**请求 JSON**

```json
{
  "display_name": "张三",
  "username": "13800000002",
  "id_number": "<本人提交的有效身份证号>"
}
```

`username` 在此请求中表示员工的新手机号。后端必须校验手机号唯一后更新该业务用户的登录标识；该接口不接受业务身份、部门/团队、`manager_id`、`is_active` 或密码。`display_name` 和 `id_number` 可以同次提交，也可以单独提交；二者不自动联动，服务端在单个数据库事务中校验并保存，任何字段校验失败时整个更新回滚。读取当前用户资料时只返回身份证掩码，不返回 `id_number` 完整值。若手机号发生变化并更新成功，App 客户端应删除本地会话、返回登录页，并提示使用新手机号登录。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "username": "13800000002",
    "display_name": "张三",
    "id_number_masked": "***************1234",
    "business_role": "employee",
    "department_name": "产品部",
    "team_name": "客户端组",
    "avatar_url": "/api/v1/app/me/avatar/",
    "account_created_date": "2026-11-06"
  },
  "requestId": "<uuid>"
}
```

#### GET, PUT /api/v1/app/me/avatar/

**认证与权限：** 必须携带当前有效的 App 业务账号 access token。所有方法只作用于令牌对应的当前业务账号；Web 管理员不能通过此接口查看或修改头像。

- `GET` 返回当前账号头像的原始图片二进制，不使用通用 JSON envelope。未设置头像时返回 `404`。响应设置 `Cache-Control: private, no-store` 与 `X-Content-Type-Options: nosniff`。
- `PUT` 使用 `multipart/form-data` 上传，字段名为 `avatar`。支持 JPEG、PNG、WebP，文件最大 5 MiB，图片宽高各不超过 4096 且总像素不超过 1600 万。成功返回 `204` 并替换该账号原有头像；校验失败返回 `400`。
头像内容以二进制和检测出的 MIME 类型保存在 `accounts_user` 数据库行中，不写入本机偏好、静态目录或媒体文件目录。同一账号在任意设备登录后可通过 `avatar_url` 获取最新头像。

#### POST /api/v1/app/me/password/

**认证与权限：** 必须携带当前有效的 App 业务账号 access token。仅允许已启用的业务账号修改自己的密码；接口不接受用户 ID，不提供代改他人密码的能力。

**请求 JSON**

```json
{
  "current_password": "当前密码",
  "new_password": "NewStrongPassword!42",
  "new_password_confirm": "NewStrongPassword!42"
}
```

后端先校验当前密码，再校验两次新密码完全一致，要求新密码不同于当前密码，并使用 Django 当前配置的密码校验器验证新密码。通过后仅更新当前用户的 Django 密码哈希。密码字段不得出现在任何响应、应用日志或审计记录中。成功变更后 App 客户端应删除本地会话、返回登录页，并要求员工使用新密码重新登录；服务端不主动吊销此前签发的 access/refresh token，这些令牌仍按现有有效期处理。本 MVP 不提供短信验证或密码找回。

**成功响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "changed": true
  },
  "requestId": "<uuid>"
}
```

当前密码错误、新密码与当前密码相同、新密码确认不一致或新密码未通过密码校验器时返回 `400`，在 `data` 中给出对应字段错误；未登录或令牌失效返回 `401`。响应不得回显任何密码。

#### GET /api/v1/app/home/（Issue #8 实施后响应）

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
        "summary": "查看值班表",
        "published_at": "2026-10-01T09:00:00+08:00",
        "sort_order": 1
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
    "personal_daily_items": [
      {
        "id": 602,
        "title": "整理项目资料"
      }
    ],
    "personal_focus_tasks": [
      {
        "id": 101,
        "title": "完成需求梳理",
        "sort_order": 1,
        "completion_status": "unfinished",
        "due_at": "2026-10-20T18:00:00+08:00"
      }
    ],
    "recent_logs": [
      {
        "id": 301,
        "log_date": "2026-10-27",
        "summary": "完成接口梳理"
      }
    ],
    "unread_notification_count": 2
  },
  "requestId": "<uuid>"
}
```

`GET /api/v1/app/home/` 返回首页公司事项、任务、个人事项摘要及通知未读数。最新 App 上方只使用 `company_items` 和 `company_top_tasks` 显示两个概览卡片；首页下方的每日事项月历通过 `GET /api/v1/app/personal-items/?start_date=&end_date=` 加载完整可见日期范围。`personal_daily_items` 仍作为兼容摘要字段，仅含当前账号且日期范围覆盖服务端业务时区今天的事项，按开始日、结束日、ID 升序排列，只返回 `id` 和 `title`。`recent_logs` 只含当前用户已提交日志，按日期倒序，不含草稿；它们不再组成最新 App 首页卡片。公司重要事项对所有 App 用户相同；任务和日志字段按当前用户权限返回。`unread_notification_count` 供任务铃铛显示红点，通知列表仍通过独立通知接口读取。`personal_focus_tasks` 是旧版兼容字段，保留期间只供旧客户端使用。

`GET /api/v1/app/home/` 不创建每日事项，也不对每日事项执行首次初始化。旧 `personal_focus_tasks` 的首次预填仅属于弃用的兼容功能，不影响 `personal_daily_items`。

#### GET /api/v1/app/personal-items/?start_date=2026-10-01&end_date=2026-10-31

**请求：** 必须提供 `start_date` 和 `end_date`，格式均为 `YYYY-MM-DD`；两端日期均包含。月历请求当前可见日期网格的起止日；查看单日事项时将两个参数设为同一天。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": 602,
        "title": "整理项目资料",
        "start_date": "2026-10-29",
        "end_date": "2026-11-02",
        "color": "#C7DDFF"
      },
      {
        "id": 605,
        "title": "准备会议材料",
        "start_date": "2026-10-31",
        "end_date": "2026-10-31",
        "color": "#FFE59E"
      }
    ],
    "calendar_days": [
      {"date": "2026-10-01", "day_type": "holiday", "holiday_name": "国庆节", "label": "国庆"},
      {"date": "2026-10-02", "day_type": "rest_day", "holiday_name": "国庆节", "label": "休"},
      {"date": "2026-10-10", "day_type": "makeup_workday", "holiday_name": null, "label": "班"}
    ]
  },
  "requestId": "<uuid>"
}
```

返回满足 `start_date <= 查询 end_date AND end_date >= 查询 start_date` 的本人事项；返回该日期区间内所有相交事项，不分页，以免月历无法准确计算“+N项”和完整绘制跨周任务条。结果按 `start_date`、`end_date`、`id` 升序排列；列表响应不含说明正文，点击事项后由详情接口读取。

`calendar_days` 使用与公司工作视图相同的版本化节假日安排，覆盖请求闭区间；月历客户端应以完整可见周网格的首尾日期发起请求，从而也能标注相邻月份日期。字段结构与工作视图相同；不在普通周末生成标记，也不推算未配置年份的日期。

#### POST /api/v1/app/personal-items/

**请求 JSON**

```json
{
  "title": "整理项目资料",
  "start_date": "2026-10-29",
  "end_date": "2026-11-02",
  "description": "归档本周项目文件",
  "color": "#C7DDFF"
}
```

`title`、`start_date`、`end_date`、`color` 必填；标题去除首尾空格后不能为空，最多 200 个字符；`description` 可省略或为空。日期必须为有效的 `YYYY-MM-DD`，结束日不能早于开始日。`color` 必须为以下预设色值之一：`#C7DDFF`、`#BDEAF2`、`#BFEBDD`、`#CFE6B4`、`#E7ECB0`、`#FFE59E`、`#FFD0A3`、`#FFBFB3`、`#F1C1D8`、`#D7C8F2`。`owner_id`、重复规则、任务状态、标签 ID 和排序字段不接受客户端输入。成功创建返回 `201` 及完整事项对象。

#### GET /api/v1/app/personal-items/602/

返回当前账号的完整事项对象：`id`、`title`、`start_date`、`end_date`、`description`、`color`、`created_at`、`updated_at`。示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 602,
    "title": "整理项目资料",
    "start_date": "2026-10-29",
    "end_date": "2026-11-02",
    "description": "归档本周项目文件",
    "color": "#C7DDFF",
    "created_at": "2026-10-27T09:30:00+08:00",
    "updated_at": "2026-10-27T09:30:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/app/personal-items/602/

接受 `title`、`start_date`、`end_date`、`description`、`color` 中一个或多个字段；未提交字段保持不变。服务端使用更新后的完整字段校验日期范围。清空说明可提交 `"description": ""`；颜色仅接受标签系统的十种预设色值。成功返回更新后的完整事项对象。

#### POST /api/v1/app/personal-items/602/move/

**请求 JSON**

```json
{
  "target_start_date": "2026-11-03"
}
```

客户端根据拖动开始时手指所在日与松手目标日计算新的起始日并提交。服务端按当前事项原有跨度整体平移起止日期，不允许用该接口改变跨度；重复提交相同 `target_start_date` 不会继续累加位移。成功返回新的完整事项对象。

#### DELETE /api/v1/app/personal-items/602/

客户端先显示删除确认框；确认后调用此接口。服务端以 `200` 物理删除当前账号的事项并返回 `{"deleted": true, "id": 602}`；该操作不产生 App 或 Web 审计记录，不提供恢复接口。

上述每日事项接口仅接受有效的 App 业务用户令牌，所有读取和写入均自动限定 `owner_id` 为当前用户。访问其他用户的事项 ID 与不存在的事项统一返回 `404`。日期格式错误、结束日早于开始日、超出预设调色板或缺少必填字段时返回 `400`；接口不接受管理员 Web 令牌。

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
    "initialized": true,
    "tasks": [
      {
        "task_id": 101,
        "title": "完成需求梳理",
        "sort_order": 1,
        "completion_status": "unfinished",
        "due_at": "2026-10-20T18:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

首次调用时，如果尚无个人重点初始化记录，服务端在事务中按当时的任务看版预填最多 10 项并设置 `initialized=true`；之后不因任务看版变化而自动重置。若用户在首次打开首页前就已直接调用本接口，初始化在本接口完成；两个入口共用同一初始化逻辑，不会重复预填。`PUT` 首次保存同样会写入初始化状态。

#### GET /api/v1/app/home/company-top-tasks/?page=1&page_size=10

**请求：** 无 JSON 请求体；仅返回当前用户本人未完成且已审批的任务，最多 10 条。

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
        "completion_status": "unfinished",
        "due_at": "2026-10-20T18:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/home/personal-focus/candidates/?keyword=需求&page=1&page_size=20

**请求：** 无 JSON 请求体；仅列出当前用户本人承担、已审批且未完成的任务，支持标题关键词；不得包含仅由本人派发给下属的任务。

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
        "completion_status": "unfinished",
        "due_at": "2026-10-20T18:00:00+08:00"
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

#### GET /api/v1/app/tasks/?scope=mine&completion_status=unfinished&lifecycle_status=active&page=1&page_size=20

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
        "estimated_start_at": "2026-10-19T09:30:00+08:00",
        "due_at": "2026-10-20T18:00:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/tasks/?scope=created&completion_status=all&lifecycle_status=active&page=1&page_size=20

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

#### GET /api/v1/app/tasks/subordinates/?task_scope=assigned&user_id=31&keyword=王&completion_status=unfinished&page=1&page_size=20

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
    "tags": [
      { "id": 21, "name": "客户跟进", "color": "#C7DDFF" },
      { "id": 24, "name": "需求分析", "color": "#BDEAF2" }
    ],
    "estimated_start_at": "2026-10-19T09:30:00+08:00",
    "due_at": "2026-10-20T18:00:00+08:00",
    "approval_status": "approved",
    "completion_status": "unfinished",
    "lifecycle_status": "active",
    "approval_history": [
      {
        "round": 1,
        "approver_name": "王总",
        "decision": "approved",
        "comment": "目标和截止时间明确",
        "submitted_at": "2026-10-11T10:00:00+08:00",
        "decided_at": "2026-10-11T10:20:00+08:00"
      }
    ],
    "updates": [],
    "submissions": []
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/tasks/101/history/

**请求：** 无 JSON 请求体。服务端按任务详情的相同权限范围校验访问权限。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "results": [
      {
        "id": 701,
        "change_type": "updated",
        "actor": { "id": 31, "display_name": "李经理" },
        "changes": {
          "due_at": {
            "before": "2026-10-20T18:00:00+08:00",
            "after": "2026-10-21T18:00:00+08:00"
          },
          "assignee_id": {
            "before": {"id": 42, "display_name": "王小明"},
            "after": {"id": 63, "display_name": "陈小丽"}
          },
          "tag_ids": {
            "before": [{"id": 21, "name": "客户跟进"}],
            "after": [
              {"id": 21, "name": "客户跟进"},
              {"id": 24, "name": "需求分析"}
            ]
          }
        },
        "created_at": "2026-10-11T10:15:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

`tag_ids.before` 与 `tag_ids.after` 均为有序对象数组，按各自关联顺序返回 `{id, name}`；`name` 是记录产生时的标签名称快照。历史数据中若仍是数字 ID，读取接口会尽可能从标签记录补充名称。

`assignee_id.before` 与 `assignee_id.after` 返回 `{id, display_name}` 快照；姓名取操作发生时的员工姓名。旧历史若只有数字 ID，读取接口会尽可能从员工记录补充姓名。Android 和 Web 均以“姓名（ID）”显示负责人变更。

#### POST /api/v1/app/tasks/

**请求 JSON**

```json
{
  "assignee_id": 42,
  "title": "完成客户需求梳理",
  "description": "整理需求并形成结论",
  "required_materials": "需求清单与评审结论",
  "estimated_start_at": "2026-10-19T09:30:00+08:00",
  "due_at": "2026-10-20T18:00:00+08:00",
  "association_note": "与 Q4 项目方案相关",
  "tag_ids": [21, 24]
}
```

**响应 JSON**

```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": 105,
    "creator_id": 31,
    "assignee_id": 63,
    "title": "完成客户需求梳理",
    "approval_status": "pending",
    "completion_status": "unfinished",
    "lifecycle_status": "active",
    "tags": [
      { "id": 21, "name": "客户跟进", "color": "#C7DDFF" },
      { "id": 24, "name": "需求分析", "color": "#BDEAF2" }
    ],
    "estimated_start_at": "2026-10-19T09:30:00+08:00",
    "due_at": "2026-10-20T18:00:00+08:00",
    "created_at": "2026-10-11T10:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### PATCH /api/v1/app/tasks/105/

**请求 JSON**

```json
{
  "title": "完成客户需求与范围梳理",
  "estimated_start_at": "2026-10-19T10:00:00+08:00",
  "due_at": "2026-10-21T18:00:00+08:00",
  "tag_ids": [21, 24]
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
    "estimated_start_at": "2026-10-19T10:00:00+08:00",
    "due_at": "2026-10-21T18:00:00+08:00",
    "tags": [
      { "id": 21, "name": "客户跟进", "color": "#C7DDFF" },
      { "id": 24, "name": "需求分析", "color": "#BDEAF2" }
    ],
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

#### POST /api/v1/app/tasks/105/resubmit/

**请求：** 无 JSON 请求体。创建人须先通过 `PATCH /api/v1/app/tasks/{id}/` 修改被退回的任务。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "task_id": 105,
    "approval_status": "pending",
    "approval_round": 2,
    "approver_id": 1,
    "submitted_at": "2026-10-11T10:25:00+08:00"
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
  "code": 201,
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
  "code": 201,
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

#### GET /api/v1/app/notifications/?unread_only=true&page=1&page_size=20

**请求：** 无 JSON 请求体。按创建时间倒序返回当前用户收到的通知；省略 `unread_only` 时返回完整历史（含已读和流程已结束通知）；`unread_only=true` 只返回未读且流程尚未结束的通知。

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
        "notification_type": "approval_required",
        "approval_round": 2,
        "approval_decision": "pending",
        "task_id": 104,
        "task_title": "整理客户反馈",
        "task_lifecycle_status": "active",
        "task_approval_status": "pending",
        "task_completion_status": "unfinished",
        "submission_id": null,
        "summary": "",
        "is_read": false,
        "resolved_at": null,
        "created_at": "2026-10-11T09:00:00+08:00",
        "read_at": null
      }
    ]
  },
  "requestId": "<uuid>"
}
```

审批相关通知（`approval_required`、`approval_approved`、`approval_returned`）返回 `approval_round` 和 `approval_decision`，分别表示该通知对应的审批轮次及该轮审批结果（`pending`、`returned`、`approved`）；其他通知的这两个字段为 `null`。审批结果通知的 `summary` 返回本轮审批意见；退回时为必填退回原因。通知保留所属轮次的结果，任务重新提交后旧通知不会显示成新一轮的状态。

创建人收到审批退回通知时，响应关键字段示例：

```json
{
  "notification_type": "approval_returned",
  "approval_round": 2,
  "approval_decision": "returned",
  "summary": "请补充任务交付标准后重新提交。"
}
```

#### GET /api/v1/app/notifications/unread-count/

**请求：** 无 JSON 请求体。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": { "unread_count": 2 },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/notifications/mark-read/

**请求 JSON**

```json
{
  "notification_ids": [501, 503]
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": { "marked_read_count": 2 },
  "requestId": "<uuid>"
}
```

仅将请求用户有权读取且明确列出的通知标记为已读；重复调用幂等。打开或浏览通知列表不会自动标记已读；用户点击单条通知时，App 仅提交该通知 ID。未被点击的通知（包括之后产生的新通知）保持未读。点击后仍须通过对应任务详情或审批中心入口进行权限校验。

#### GET /api/v1/app/organization/assignable-users/?keyword=张

根据当前登录用户返回统一任务表单可选负责人。创始人结果范围为本公司全体成员；部门经理可选择本人、本部门团队长或其他部门经理；团队长可选择本人或本团队普通员工；普通员工仅可选择本人。服务端必须在创建时再次校验选择范围；此接口不区分或返回跨部门任务类型。

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

人员关键词仅匹配当前用户有权只读查看的下属姓名；`department_id`（可选）用于限定结果部门，不会扩大授权范围。

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

#### GET /api/v1/app/organization/tree/

返回 App“组织关系”页的公司组织树。所有已登录 App 用户（创始人、部门经理、团队长和普通员工）均可读取启用的业务成员、部门和团队；Web 管理员不适用业务 App 接口。创始人与各启用部门作为并列根节点返回。人员节点包含 `department_name` 和 `team_name`（无对应组织时为 `null`）。树节点不内嵌头像图片；App 对可见成员使用 `GET /api/v1/app/organization/members/{id}/avatar/` 单独加载头像，无头像时显示姓名首字。

可选查询参数 `keyword` 按姓名或业务职级搜索。关键词非空时，响应中的 `roots` 是平铺的匹配人员列表，每项保留 `node_type: person`、姓名、职位及部门/团队名称，不包含部门或团队节点；清空关键词时恢复组织树。App 搜索行在姓名下方展示部门/团队（创始人不展示），职位在右侧显示。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scope": "company",
    "roots": [
      {
        "node_type": "person",
        "id": 1,
        "display_name": "王总",
        "business_role": "founder",
        "department_name": null,
        "team_name": null,
        "children": []
      },
      {
        "node_type": "department",
        "id": 10,
        "name": "产品部",
        "children": [
          {
            "node_type": "person",
            "id": 20,
            "display_name": "张经理",
            "business_role": "department_manager",
            "department_name": "产品部",
            "team_name": null,
            "children": []
          },
          {
            "node_type": "team",
            "id": 30,
            "name": "客户端组",
            "children": [
              {
                "node_type": "person",
                "id": 31,
                "display_name": "李组长",
                "business_role": "team_leader",
                "department_name": "产品部",
                "team_name": "客户端组",
                "children": []
              }
            ]
          }
        ]
      }
    ]
  },
  "requestId": "<uuid>"
}
```

例如搜索 `keyword=bo` 时，`roots` 只含匹配到的人员并列返回，不包含部门/团队树节点：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "scope": "company",
    "roots": [
      {
        "node_type": "person",
        "id": 1,
        "display_name": "Boss",
        "business_role": "founder",
        "department_name": null,
        "team_name": null,
        "children": []
      },
      {
        "node_type": "person",
        "id": 42,
        "display_name": "Boy",
        "business_role": "employee",
        "department_name": "产品部",
        "team_name": "客户端组",
        "children": []
      }
    ]
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/organization/members/{id}/

所有已登录 App 用户均可读取公司组织树中启用业务成员的只读资料。资料仅返回显示姓名、手机号、业务身份、部门和团队，不返回身份证号、直属上级或账号状态。不存在、非业务账号或停用成员返回 `404`。该资料权限不扩大任务、日志和工作视图的访问范围；`can_view_tasks`、`can_view_logs` 和 `can_view_views` 分别表示当前用户能否通过既有下属查看接口读取此成员的任务、日志或月/周/日工作视图。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 42,
    "is_self": false,
    "can_view_tasks": true,
    "can_view_logs": true,
    "can_view_views": true,
    "display_name": "张三",
    "phone": "13800000000",
    "business_role": "employee",
    "department_name": "产品部",
    "team_name": "客户端组"
  },
  "requestId": "<uuid>"
}
```

`phone` 为业务账号当前使用的手机号；资料接口不返回身份证号。`can_view_tasks`、`can_view_logs` 和 `can_view_views` 分别指示当前用户是否可按各自既有权限访问该成员的任务、日志或工作视图；对应值为 `true` 且目标不是本人时，App 才显示相应快捷入口。任务和日志入口复用现有下属任务、日志日历/详情接口；视图入口复用月、周、日工作视图接口，并以 `user_id` 查询参数指定成员。后端仍逐次校验目标成员授权；任务仅返回已审批记录，日志仅返回已提交记录。该功能复用现有 `User`、`Department`、`Team` 和直属关系，不新增数据库实体或迁移。

#### GET /api/v1/app/organization/members/{id}/avatar/

必须携带有效 App 业务账号令牌。所有 App 用户均可读取启用业务成员已上传的头像；无头像、成员不存在、停用或非业务账号均返回空的 `404`。成功时直接返回头像二进制及保存时检测出的图片 MIME 类型，不使用 JSON envelope，并设置 `Cache-Control: private, no-store` 和 `X-Content-Type-Options: nosniff`。头像内容沿用保存在用户数据库记录中的数据，不新增字段或迁移。


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
    "tags": [
      { "id": 21, "name": "客户跟进", "color": "#C7DDFF" },
      { "id": 24, "name": "需求分析", "color": "#BDEAF2" }
    ],
    "created_at": "2026-10-11T16:00:00+08:00",
    "submitted_at": "2026-10-11T18:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

进入日志详情后，不直接打开编辑表单。评论区紧跟日志正文，客户端通过下方独立接口读取列表，不将评论嵌入日志正文响应。详情页底部使用文本框样式的评论入口，点击后打开输入表单；所有可读取该日志的 App 用户都能评论。作者在今天、昨天或前天的日志详情页还会看到右侧“修改”按钮。

#### GET /api/v1/app/logs/301/comments/?page=1&page_size=20

仅当前有权读取父日志的 App 用户可读取评论。日志作者可读取本人日志的评论；其他人员仅可读取自己授权范围内的已提交日志评论。评论按创建时间升序返回。读取草稿评论时只有日志作者能通过现有本人日志权限访问。

**请求：** 无 JSON 请求体；支持 `page`、`page_size` 分页参数。

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
        "id": 7001,
        "content": "辛苦了，客户反馈已经同步到项目群。",
        "author": {
          "id": 31,
          "display_name": "李组长",
          "business_role": "team_leader",
          "department_name": "产品部",
          "team_name": "客户端组"
        },
        "is_log_author": false,
        "created_at": "2026-10-11T18:30:00+08:00"
      }
    ]
  },
  "requestId": "<uuid>"
}
```

`is_log_author=true` 表示评论由该日志作者发布，App 将该评论标记为“日志本人”。评论人姓名、业务职位及当前部门/团队由用户资料返回；不会返回手机号或身份证号。

#### POST /api/v1/app/logs/301/comments/

日志作者和当前权限范围内能读取该日志的 App 用户都可以评论。下属草稿和越权日志仍按现有日志可见性规则拒绝访问。评论与日志正文分开保存，不受日志正文三日编辑窗口限制，不改变日志状态或更新时间；评论不可修改、删除或回复。

**请求 JSON**

```json
{
  "content": "辛苦了，客户反馈已经同步到项目群。"
}
```

`content` 去除首尾空格后必填，长度为 1–2000 个字符。空内容或超长内容返回 `400`；对象不存在、草稿对当前用户不可见或超出组织范围时返回 `404`。

**响应 JSON（HTTP 201）**

```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": 7001,
    "content": "辛苦了，客户反馈已经同步到项目群。",
    "author": {
      "id": 31,
      "display_name": "李组长",
      "business_role": "team_leader",
      "department_name": "产品部",
      "team_name": "客户端组"
    },
    "is_log_author": false,
    "created_at": "2026-10-11T18:30:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### GET /api/v1/app/logs/mine/2026-10-11/

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "log_date": "2026-10-11",
    "log": null,
    "status": "not_created",
    "can_edit": true
  },
  "requestId": "<uuid>"
}
```

#### PUT /api/v1/app/logs/mine/2026-10-11/

**请求 JSON**

```json
{
  "completed_work": "完成接口梳理",
  "next_day_plan": "补充测试用例",
  "tag_ids": [21, 24]
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
    "tags": [
      { "id": 21, "name": "客户跟进", "color": "#C7DDFF" },
      { "id": 24, "name": "需求分析", "color": "#BDEAF2" }
    ],
    "updated_at": "2026-10-11T17:30:00+08:00",
    "can_edit": true
  },
  "requestId": "<uuid>"
}
```

#### POST /api/v1/app/logs/mine/2026-10-11/submit/

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
    "submitted_at": "2026-10-11T18:00:00+08:00",
    "can_edit": true
  },
  "requestId": "<uuid>"
}
```

以上 `log_date` 为路径参数中的示例日期。对于早于今天两天的日期或未来日期，读取本人日志仍可返回内容及 `can_edit: false`；对应 `PUT`、`submit` 请求应拒绝且不得改变数据。服务端以统一配置的业务时区计算自然日窗口，不能信任客户端传来的日期范围或 `can_edit` 值。日志详情及本人编辑读取返回 `tags: [{id, name, color}]`；已停用历史关联照常返回，但不返回停用标记。

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


### 4.6 Android 标签系统

标签接口使用 Android 业务用户令牌。所有 App 角色都可读取启用标签；只有创始人可从“我的页面”的标签管理中心创建、改名、改色或停用标签；管理中心列表只展示启用标签。管理接口不接受 Web 管理端令牌。创始人创建时选择一种固定调色板颜色，启用标签可改为其他预设颜色；停用标签不可编辑。改色同步影响所有已有任务、日志关联和工作视图任务条。

#### GET /api/v1/app/tags/?keyword=客户&page=1&page_size=20

默认只返回启用标签，支持按名称搜索。创始人可通过 `include_inactive=true` 查询停用标签，但管理中心列表使用默认查询，只展示启用标签及其名称和颜色；其他角色请求该参数返回 `403`。创始人任务/日志表单仍使用默认启用标签结果。

**请求：** 无 JSON 请求体；查询参数：`keyword`（可选）、`include_inactive`（可选，创始人专用）、`page`、`page_size`。

**响应 JSON（创始人管理列表）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 2,
    "next": null,
    "previous": null,
    "results": [
      { "id": 21, "name": "客户跟进", "color": "#C7DDFF", "is_active": true },
      { "id": 18, "name": "客户跟进", "color": "#BDEAF2", "is_active": false }
    ]
  },
  "requestId": "<uuid>"
}
```

常规标签选择器只返回 `is_active=true` 的标签。`include_inactive=true` 仅供创始人显式查询停用标签；管理中心和任务/日志选择器均不传该参数。

#### POST /api/v1/app/tags/

仅创始人可调用。请求必须提交名称和固定调色板中的一个颜色。标签数超过 10 个时允许重复选择同一种颜色。可选色值为 `#C7DDFF`、`#BDEAF2`、`#BFEBDD`、`#CFE6B4`、`#E7ECB0`、`#FFE59E`、`#FFD0A3`、`#FFBFB3`、`#F1C1D8`、`#D7C8F2`。

**请求 JSON**

```json
{
  "name": "客户跟进",
  "color": "#C7DDFF"
}
```

**响应 JSON**

```json
{
  "code": 201,
  "message": "success",
  "data": {
    "id": 25,
    "name": "客户跟进",
    "color": "#C7DDFF",
    "is_active": true,
    "created_at": "2026-10-27T10:00:00+08:00"
  },
  "requestId": "<uuid>"
}
```

缺少颜色、颜色不在固定调色板中或其他字段校验失败时返回 `400`。创建或改名为另一个启用标签已使用的名称时返回 `409`；停用标签不参与名称唯一性校验。

#### PATCH /api/v1/app/tags/25/

仅创始人可调用，且仅启用标签可编辑。可只提交 `name`、只提交 `color`，或同时提交两者；至少提交一项。`color` 只能是上述固定调色板中的值，`is_active` 等字段不接受。修改颜色后，已有任务和日志关联以及工作视图任务条同步显示新颜色，不改变关联关系或业务状态。

**请求 JSON**

```json
{
  "name": "重点客户",
  "color": "#D7C8F2"
}
```

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 25,
    "name": "重点客户",
    "color": "#D7C8F2",
    "is_active": true,
    "updated_at": "2026-10-27T10:15:00+08:00"
  },
  "requestId": "<uuid>"
}
```

#### DELETE /api/v1/app/tags/25/

仅创始人可调用。此操作将标签不可逆地设为停用，不物理删除记录或任务/日志关联；重复调用保持停用状态。App 调用前应要求创始人确认。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 25,
    "is_active": false
  },
  "requestId": "<uuid>"
}
```

任务 `POST`/`PATCH` 和日志 `PUT` 使用可选 `tag_ids` 数组保存关联。新建只能关联启用标签；编辑时仍关联于当前记录的停用标签可原样保留，若本次移除后再次尝试关联则返回 `400`。更新省略 `tag_ids` 表示不改关联，显式传空数组表示移除全部关联。无效 ID 或非法停用标签导致整次请求回滚，不部分保存。

#### 工作视图响应字段

月视图的 `tasks`、周/日视图的 `all_day_tasks` 每项增加 `bar_color`。颜色按当前关联中最早新增的关系计算；移除最早标签后顺延到剩余最早关联；无标签时返回固定中性灰色。工作视图不返回标签名称或停用标记供任务条展示。

月、周、日工作视图响应增加 `calendar_days` 数组，列出当前响应日期范围内已配置的中国大陆节假日安排。月视图范围为完整可见日期网格；周视图范围为所选周；日视图范围为顶部七日日期条所在周。普通日期和未配置年份的日期不返回数组项。

月视图响应的 `start_date`、`end_date` 保持所选自然月边界；新增 `calendar_start_date`、`calendar_end_date`，分别表示日期网格首个周一和末个周日。`days` 从 `calendar_start_date` 到 `calendar_end_date` 按自然日连续返回，含上月/下月日期；相邻月份日期可被客户端浅色显示并可点击。任务跨度与这些可见日期相交时，也需出现在对应 `days[].bars` 中，任务条超出完整网格时由网格边缘裁剪。月视图 `calendar_days` 的范围同样为完整可见网格。

每项结构如下：

| 字段 | 类型 | 说明 |
|---|---|---|
| `date` | `YYYY-MM-DD` | 标记所属日期 |
| `day_type` | 字符串 | `holiday`（节日名称对应的正式节日日期）、`rest_day`（放假安排中的其他休息日）或 `makeup_workday`（调休上班日） |
| `holiday_name` | 字符串或 `null` | 节假日名称；调休上班日为 `null` |
| `label` | 字符串 | 客户端展示文字：节日日期使用短节日名，其他放假日使用“休”，调休上班日使用“班”；节日名称优先于“休” |

年度日期来自后端版本化日历数据，不新增接口或数据库表。未配置年份返回空数组；客户端不得按周末或农历推算。第一版数据覆盖 2025—2027 年，并按国务院办公厅公布的年度安排维护。

月视图中的完整日期数组示例：

```json
{
  "start_date": "2025-10-01",
  "end_date": "2025-10-31",
  "calendar_start_date": "2025-09-29",
  "calendar_end_date": "2025-11-02",
  "days": [
    {"date": "2025-09-29", "bars": [], "overflow_count": 0},
    {"date": "2025-09-30", "bars": [], "overflow_count": 0},
    {"date": "2025-10-01", "bars": [], "overflow_count": 0},
    {"date": "2025-10-30", "bars": [{"task_id": 101, "title": "跨月任务", "bar_color": "#C7DDFF", "start_date": "2025-10-30", "end_date": "2025-11-02"}], "overflow_count": 0},
    {"date": "2025-10-31", "bars": [{"task_id": 101, "title": "跨月任务", "bar_color": "#C7DDFF", "start_date": "2025-10-30", "end_date": "2025-11-02"}], "overflow_count": 0},
    {"date": "2025-11-01", "bars": [{"task_id": 101, "title": "跨月任务", "bar_color": "#C7DDFF", "start_date": "2025-10-30", "end_date": "2025-11-02"}], "overflow_count": 0},
    {"date": "2025-11-02", "bars": [{"task_id": 101, "title": "跨月任务", "bar_color": "#C7DDFF", "start_date": "2025-10-30", "end_date": "2025-11-02"}], "overflow_count": 0}
  ],
  "calendar_days": [
    {"date": "2025-10-01", "day_type": "holiday", "holiday_name": "国庆节、中秋节", "label": "国庆"},
    {"date": "2025-10-06", "day_type": "holiday", "holiday_name": "中秋节", "label": "中秋"}
  ]
}
```

上例 `days` 为结构节选；真实响应会按日期连续包含 2025-09-29 至 2025-11-02 的全部 35 天。2025-10-06 是连续假期内的正式中秋节日期，因此名称“中秋”优先显示，不返回通用“休”标签。

### 4.7 Android 月、周、日视图

#### GET /api/v1/app/work-views/month/?user_id=42&year=2026&month=10

**请求：** 无 JSON 请求体；如有筛选条件，查询参数见接口 URL。

**响应说明：** `days` 按完整可见网格连续返回；下例只展示代表日期。

**响应 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "user_id": 42,
    "year": 2026,
    "month": 10,
    "start_date": "2026-10-01",
    "end_date": "2026-10-31",
    "calendar_start_date": "2026-09-28",
    "calendar_end_date": "2026-11-01",
    "days": [
      {
        "date": "2026-10-01",
        "bars": [{"task_id": 101, "title": "完成需求梳理", "bar_color": "#C7DDFF", "start_date": "2026-10-01", "end_date": "2026-10-05"}],
        "overflow_count": 0
      }
    ],
    "calendar_days": [
      {"date": "2026-10-01", "day_type": "holiday", "holiday_name": "国庆节", "label": "国庆"},
      {"date": "2026-10-02", "day_type": "rest_day", "holiday_name": "国庆节", "label": "休"},
      {"date": "2026-10-10", "day_type": "makeup_workday", "holiday_name": null, "label": "班"}
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
    "start_date": "2026-10-05",
    "end_date": "2026-10-11",
    "dates": ["2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"],
    "all_day_tasks": [{"task_id": 101, "title": "完成需求梳理", "bar_color": "#C7DDFF", "start_date": "2026-10-05", "end_date": "2026-10-07"}],
    "events": [{"task_id": 101, "task_title": "完成需求梳理", "event_type": "progress_updated", "occurred_at": "2026-10-11T11:00:00+08:00", "tag_color": "#159A84"}],
    "calendar_days": [
      {"date": "2026-10-10", "day_type": "makeup_workday", "holiday_name": null, "label": "班"}
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
    "start_date": "2026-10-05",
    "end_date": "2026-10-11",
    "dates": ["2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11"],
    "all_day_tasks": [{"task_id": 101, "title": "完成需求梳理", "bar_color": "#C7DDFF", "start_date": "2026-10-11", "end_date": "2026-10-11"}],
    "events": [{"task_id": 101, "task_title": "完成需求梳理", "event_type": "completion_submitted", "occurred_at": "2026-10-11T16:00:00+08:00", "tag_color": "#159A84"}],
    "calendar_days": [
      {"date": "2026-10-10", "day_type": "makeup_workday", "holiday_name": null, "label": "班"}
    ]
  },
  "requestId": "<uuid>"
}
```


### 4.8 Android AI 功能

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


### 4.9 错误响应示例

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

标签相关失败约定：非创始人调用标签管理操作或请求停用标签管理列表返回 `403`；启用标签名称重复返回 `409`；任务/日志请求新关联未知或已停用标签返回 `400`，且整次请求回滚；未知标签 ID 返回 `404`。停用标签的重复 `DELETE` 返回成功并保持停用。

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

1. 管理员与业务用户采用独立 Django 用户模型还是同一认证表的类型隔离；JWT 的受众和密钥策略。
2. 组织调整后，历史日志/任务查看权限按当前组织关系还是创建时组织快照计算。
3. 事项附件的正式存储介质（当前开发期使用本地 `MEDIA_ROOT`，正式环境需确定对象存储与清理策略），以及 AI 厂商数据留存、MBTI 题目和计分方案。
4. 精确分页上限、字段长度、错误码清单、访问频率限制和刷新令牌有效期。

## 7. 与 Swagger 的关系

本设计稿是接口契约讨论稿。开发接口时应在 DRF View/Serializer 上补充中文 `summary`、`description`、参数和响应 Schema，使 `/api/docs/` 中的契约与实现同步；不要把本文档中尚未实现的路径当成 Swagger 已提供的接口。使用步骤见 [`swagger-guide.md`](../cooperate/swagger-guide.md)。
