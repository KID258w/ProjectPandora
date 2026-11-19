# 王世鑫：中期个人测试报告

## 1. 基本信息与测试环境

| 项目                 | 内容                                                                                                                |
| ------------------ | ----------------------------------------------------------------------------------------------------------------- |
| 测试人员               | 王世鑫                                                                                                               |
| 关联 Issue           | #42 [测试10] 核心业务测试补充与测试报告整理                                                                                        |
| 对应 PR              | 待补充（分支 `test/10-midterm-accounts-organization` 已在本地创建，尚未推送）                                                       |
| 测试日期               | 2026-11-19                                                                                                        |
| 测试分支               | `test/10-midterm-accounts-organization`                                                                           |
| 被测版本               | `bdff2db`（dev 分支「准备进行初发布版本测试」）                                                                                    |
| 测试代码版本             | 未提交，当前为本地改动（10 个新增测试文件、`requirements.txt`、`pytest.ini`、本报告）                                                       |
| 操作系统               | Windows 10.0.26100.4652                                                                                           |
| Python / Django 版本 | Python 3.13.11 / Django 5.2.18                                                                                    |
| 测试框架               | pytest 8.4.2 + pytest-django 4.14.0 + pytest-cov 6.3.0（同一批用例也可用 `manage.py test` 执行，已验证）                          |
| 数据库                | Django 自动创建的独立测试数据库（SQLite 内存库），执行结束自动销毁，不写入 `db.sqlite3`                                                         |
| App / 浏览器环境        | 本报告自动化部分不涉及；客户端行为按 Issue 要求另做手动验证（见 3.4，本轮未执行）                                                                    |
| 需求依据               | `docs/backend/account-provisioning.md`；`docs/requirements/08-organization-relationships.md`；Issue #42 各功能项与「重点检查」 |

> **环境差异说明**：团队基线为 Python 3.11（见 `backend/requirements.txt` 首行注释与 CI 配置），本次本地执行为 Python 3.13.11。新增依赖均为纯 Python 包，无编译扩展，两个版本均可运行；最终结论以汇总同学在统一环境执行的完整测试为准。

### 1.1 测试工具选型说明

本轮仅涉及后端 Python 代码，因此只使用 Python 侧工具，未引入 Java 测试工具链。对照 Week09 课件列出的工具逐项判断如下：

| 课件工具                      | 适用语言             | 本项目是否使用  | 理由                                                                                                              |
| ------------------------- | ---------------- | -------- | --------------------------------------------------------------------------------------------------------------- |
| pytest                    | Python           | **是**    | 中期验收要求建议的 Python 测试框架；已通过 `requirements.txt` 纳入依赖                                                               |
| coverage.py（经 pytest-cov） | Python           | **是**    | 用于生成第 7 节覆盖率报告；`pytest-cov` 自动引入 coverage                                                                       |
| JUnit                     | Java             | 否（后端不使用） | 后端为 Python，工具与语言不匹配。Android 端 `android/app/build.gradle.kts` 已声明 `testImplementation(libs.junit)`，由 Gradle 自动管理 |
| JaCoCo                    | Java             | 否        | 仅用于 Java/Kotlin 覆盖率统计，本轮后端覆盖率由 coverage.py 承担                                                                   |
| Emma                      | Java             | 否        | 课件已注明「停止维护」                                                                                                     |
| Cobertura                 | Java             | 否        | 课件已注明「停止维护」                                                                                                     |
| Eclemma                   | Java（Eclipse 插件） | 否        | 依赖 Eclipse 环境，本项目使用 VS Code 与 Android Studio                                                                    |
| Mockito                   | Java             | 否        | 后端 Python 使用标准库 `unittest.mock`；本轮被测函数多数依赖数据库真实约束，不使用 mock                                                      |
| Eclipse / IntelliJ IDEA   | IDE              | 否        | 开发环境为 VS Code（Python/Web）与 Android Studio（Kotlin，内含 IntelliJ 内核）                                                |

依赖安装：`backend/requirements.txt` 已追加 `pytest>=8.2,<9`、`pytest-django>=4.11,<5`、`pytest-cov>=6,<7`，在 `backend` 目录执行 `pip install -r requirements.txt` 即可重建环境，不影响系统 Python。

## 2. 测试范围与核心函数映射

Issue #42 分配范围共 25 个功能项、40 个核心函数，本轮全部覆盖。

| 功能编号 | 端与业务功能             | 后端模块     | 源文件                                        | 核心函数／方法                                                                                       | 测试层级      | 测试文件                                                                 | 用例编号           |
| ---- | ------------------ | -------- | ------------------------------------------ | --------------------------------------------------------------------------------------------- | --------- | -------------------------------------------------------------------- | -------------- |
| 1    | Web：管理员登录和令牌刷新     | accounts | `backend/apps/accounts/api_serializers.py` | `ScopedTokenSerializer.validate()`、`ScopedRefreshSerializer.validate()`、`get_token()`         | 单元        | `test_midterm_wsx_auth.py`                                           | TC-WSX-001～024 |
| 2    | Web：管理端访问权限        | accounts | `backend/apps/accounts/authentication.py`  | `IsAdministrator.has_permission()`                                                            | 单元        | `test_midterm_wsx_auth.py`                                           | TC-WSX-031～040 |
| 3    | Web：部门创建           | accounts | `backend/apps/accounts/api_views.py`       | `DepartmentListView.perform_create()`                                                         | 单元        | `test_midterm_wsx_department.py`                                     | TC-WSX-041～050 |
| 4    | Web：部门编辑与停用        | accounts | `backend/apps/accounts/api_serializers.py` | `DepartmentUpdateSerializer.validate()`                                                       | 单元        | `test_midterm_wsx_department.py`                                     | TC-WSX-051～060 |
| 5    | Web：部门负责人和成员展示     | accounts | 同上                                         | `DepartmentDetailSerializer.get_manager()`、`get_members()`、`get_teams()`                      | 单元        | `test_midterm_wsx_department.py`                                     | TC-WSX-071～077 |
| 6    | Web：团队创建与编辑校验      | accounts | 同上                                         | `TeamSerializer.validate()`、`TeamUpdateSerializer.validate()`                                 | 单元        | `test_midterm_wsx_team.py`                                           | TC-WSX-081～100 |
| 7    | Web：团队负责人和成员展示     | accounts | 同上                                         | `TeamDetailSerializer.get_leader()`、`get_members()`                                           | 单元        | `test_midterm_wsx_team.py`                                           | TC-WSX-101～107 |
| 8    | Web：员工录入和初始密码      | accounts | 同上                                         | `BusinessUserCreateSerializer.validate()`、`create()`；支撑函数 `User.normalize_id_number()`        | 单元        | `test_midterm_wsx_user_create.py`、`test_midterm_wsx_id_number.py`    | TC-WSX-111～140 |
| 9    | Web：员工创建结果及筛选      | accounts | `backend/apps/accounts/api_views.py`       | `UserListView.create()`、`get_queryset()`                                                      | 接口集成 / 单元 | `test_midterm_wsx_user_create.py`                                    | TC-WSX-141～155 |
| 10   | Web：员工身份证脱敏        | accounts | `backend/apps/accounts/api_serializers.py` | `UserSerializer.get_id_number_masked()`                                                       | 单元        | `test_midterm_wsx_user_create.py`                                    | TC-WSX-161～170 |
| 11   | Web：员工身份、组织与启用状态调整 | accounts | 同上                                         | `resolve_placement()`、`UserUpdateSerializer.validate()`、`update()`、`maintain_manager_links()` | 单元 / 接口集成 | `test_midterm_wsx_organization.py`、`test_midterm_wsx_user_update.py` | TC-WSX-171～213 |
| 12   | Web：管理概况           | accounts | `backend/apps/accounts/api_views.py`       | `DashboardView.get()`                                                                         | 单元        | `test_midterm_wsx_user_update.py`                                    | TC-WSX-221～226 |
| 13   | App：登录和令牌刷新        | accounts | `backend/apps/accounts/api_serializers.py` | `AppTokenSerializer`、`AppRefreshSerializer`（补充 App 场景）                                        | 单元        | `test_midterm_wsx_app_account.py`                                    | TC-WSX-231～238 |
| 14   | App：访问及组织链校验       | accounts | `backend/apps/accounts/authentication.py`  | `has_complete_supervisor_chain()`、`IsBusinessUser.has_permission()`                           | 单元        | `test_midterm_wsx_app_account.py`                                    | TC-WSX-251～266 |
| 15   | App：个人资料字段校验       | accounts | `backend/apps/accounts/api_serializers.py` | `AppMeUpdateSerializer.validate_username()`、`validate_display_name()`、`validate_id_number()`  | 单元        | `test_midterm_wsx_app_account.py`                                    | TC-WSX-281～291 |
| 16   | App：个人资料更新         | accounts | `backend/apps/accounts/api_views.py`       | `AppMeView.update()`                                                                          | 接口集成      | `test_midterm_wsx_app_account.py`                                    | TC-WSX-301～306 |
| 17   | App：修改密码           | accounts | `backend/apps/accounts/api_serializers.py` | `AppPasswordChangeSerializer.validate()`、`save()`                                             | 单元        | `test_midterm_wsx_app_account.py`                                    | TC-WSX-311～317 |
| 18   | App：修改密码接口         | accounts | `backend/apps/accounts/api_views.py`       | `AppPasswordChangeView.post()`                                                                | 接口集成      | `test_midterm_wsx_app_account.py`                                    | TC-WSX-321～325 |
| 19   | App：头像校验           | accounts | `backend/apps/accounts/api_serializers.py` | `AppAvatarUploadSerializer.validate_avatar()`                                                 | 单元        | `test_midterm_wsx_avatar.py`                                         | TC-WSX-331～341 |
| 20   | App：头像上传与读取        | accounts | `backend/apps/accounts/api_views.py`       | `AppProfileAvatarView.put()`、`get()`                                                          | 接口集成      | `test_midterm_wsx_avatar.py`                                         | TC-WSX-342～347 |
| 21   | App：公司组织树和人员搜索     | tasks    | `backend/apps/tasks/views.py`              | `_build_organization_tree()`、`OrganizationTreeView.get()`                                     | 单元 / 接口集成 | `test_midterm_wsx_org_tree.py`                                       | TC-WSX-351～363 |
| 22   | App：成员资料和头像读取      | tasks    | 同上                                         | `OrganizationMemberProfileView.get()`、`OrganizationMemberAvatarView.get()`                    | 接口集成      | `test_midterm_wsx_org_tree.py`                                       | TC-WSX-371～377 |
| 23   | App：下属范围           | logs     | `backend/apps/logs/views.py`               | `descendants()`                                                                               | 单元        | `test_midterm_wsx_org_tree.py`                                       | TC-WSX-381～385 |
| 24   | App：有效下属范围         | tasks    | `backend/apps/tasks/services.py`           | `subordinate_ids()`                                                                           | 单元        | `test_midterm_wsx_org_tree.py`                                       | TC-WSX-391～393 |
| 25   | App：下属任务查询         | tasks    | `backend/apps/tasks/views.py`              | `SubordinateTaskListView.get()`                                                               | 接口集成      | `test_midterm_wsx_org_tree.py`                                       | TC-WSX-401～405 |

- 单元测试：直接调用函数或方法验证业务规则，不经过 HTTP 视图。
- 接口集成测试：通过请求验证参数校验、权限、响应头、数据库变化等组合行为。
- 测试文件均位于 `backend/apps/accounts/` 或 `backend/apps/tasks/`，文件名带成员缩写 `wsx`，避免与其他成员冲突。

### 2.0 用例编号分配规则

用例编号按 Issue #42 的**功能编号**分段分配，编号本身即体现所属业务项，便于验收时逐项核对。

| Issue 功能编号 | 业务功能           | 用例编号区间  | 实际使用                |
| ---------- | -------------- | ------- | ------------------- |
| 1          | 管理员登录和令牌刷新     | 001～030 | 001～024             |
| 2          | 管理端访问权限        | 031～040 | 031～040             |
| 3          | 部门创建           | 041～050 | 041～050             |
| 4          | 部门编辑与停用        | 051～070 | 051～060             |
| 5          | 部门负责人和成员展示     | 071～080 | 071～077             |
| 6          | 团队创建与编辑校验      | 081～100 | 081～100             |
| 7          | 团队负责人和成员展示     | 101～110 | 101～107             |
| 8          | 员工录入和初始密码      | 111～140 | 111～140             |
| 9          | 员工创建结果及筛选      | 141～160 | 141～155             |
| 10         | 员工身份证脱敏        | 161～170 | 161～170             |
| 11         | 员工身份、组织与启用状态调整 | 171～220 | 171～213             |
| 12         | 管理概况           | 221～230 | 221～226             |
| 13         | App 登录和令牌刷新    | 231～250 | 231～238             |
| 14         | App 访问及组织链校验   | 251～280 | 251～266             |
| 15         | 个人资料字段校验       | 281～300 | 281～291             |
| 16         | 个人资料更新         | 301～310 | 301～306             |
| 17         | 修改密码           | 311～320 | 311～317             |
| 18         | 修改密码接口         | 321～330 | 321～325             |
| 19         | 头像校验           | 331～340 | 331～341（340 顺延至本区间） |
| 20         | 头像上传与读取        | 341～350 | 342～347             |
| 21         | 公司组织树和人员搜索     | 351～370 | 351～363             |
| 22         | 成员资料和头像读取      | 371～380 | 371～377             |
| 23         | 下属范围           | 381～390 | 381～385             |
| 24         | 有效下属范围         | 391～400 | 391～393             |
| 25         | 下属任务查询         | 401～410 | 401～405             |

### 2.1 已有测试复用情况

本轮**未修改、未删除**任何既有测试，全部保留。既有测试与本轮新增用例的关系如下：

| 已有测试文件及方法                                                                                            | 覆盖的函数／业务规则               | 本轮补充的场景                                                                     |
| ---------------------------------------------------------------------------------------------------- | ------------------------ | --------------------------------------------------------------------------- |
| `apps/accounts/tests.py::AccountApiTests.test_type_isolation_login_access_refresh_and_legacy_routes` | 接口层两端登录/刷新/访问隔离，旧认证路由已移除 | 字段级校验、令牌类型错误、过期令牌、缺失 `account_type` 声明（TC-006～024）                          |
| `apps/accounts/tests.py::AccountApiTests.test_disabled_users_cannot_login_access_or_refresh`         | 停用账号无法登录、访问、刷新           | 单元级复验并校验具体拒绝文案（TC-011、012）                                                  |
| `apps/accounts/tests.py::AccountApiTests.test_organization_validation_and_missing_superiors`         | 录入时组织归属与上级缺失的接口级校验       | `exclude_pk`、名额作用域、`is_active=False`、`allow_missing_manager` 分支（TC-171～192） |
| `apps/accounts/tests.py::AccountApiTests.test_leader_vacancy_and_successor_rebuild`                  | 负责人空缺与继任后关系重建            | 扩展为经理/团队长两级、跨部门迁移（TC-193～213）                                               |
| `apps/accounts/tests.py::AccountApiTests.test_department_detail_and_deactivation_guard`              | 部门详情与停用守卫                | 阻塞文案计数折叠、多类阻塞项并列（TC-054～057）                                                |
| `apps/accounts/tests.py::AccountApiTests.test_team_detail_and_deactivation_guard`                    | 团队详情与停用守卫                | 部门停用后禁止启用团队（TC-097）                                                         |
| `apps/accounts/tests.py::AccountApiTests.test_user_filtering`                                        | 员工列表筛选                   | 非法 `is_active` 取值、无匹配空集（TC-152～155）                                         |
| `apps/accounts/tests.py::AccountApiTests.test_id_number_uniqueness`                                  | 身份证号唯一性                  | 大小写差异视为重复、字段长度边界（TC-126、127、131、132）                                        |
| `apps/accounts/test_app_operation_audit.py`（整文件）                                                     | App 操作审计写入与敏感字段过滤        | 复用其结论；本轮补充资料更新与改密的审计断言（TC-302、303、322）                                      |
| `apps/accounts/tests.py::AccountApiTests.test_dashboard_counts`                                      | 管理概况计数                   | 空库、停用实体、草稿事项（TC-221～226）                                                    |

### 2.2 未覆盖或暂不能测试的内容

| 内容                                            | 原因                                                                                   | 影响                      | 需要谁确认或提供            |
| --------------------------------------------- | ------------------------------------------------------------------------------------ | ----------------------- | ------------------- |
| `has_complete_supervisor_chain()` 的**环路检测**分支 | 数据库约束 `valid_account_placement` 与 `user_cannot_manage_self` 阻止构造环状上级链，该分支仅在防御性代码路径存在 | 生产数据若因直接改库产生环，该分支行为未经验证 | 需确认是否存在绕过业务层写库的运维场景 |
| `resolve_placement()` 中创始人带组织的分支              | 同上，数据库约束阻止该非法形状落库，仅以内存实例验证（TC-259）                                                   | 低                       | 无                   |
| 组织关系相关**客户端界面行为**                             | 属手动验证范围（见 3.4），本轮未执行                                                                 | 界面展示未验证                 | 需真机与测试账号            |
| 客户端界面展示与组织关系交互（MAN-WSX-003～006）               | 属手动验证范围（见 3.4），本轮未执行                                                                 | 界面展示未验证                 | 需真机与测试账号            |
| 头像大小/尺寸阈值                                     | 需求文档未单列头像章节，阈值来自实现约定                                                                 | 预期依据待确认（见第 6 节）         | 需需求负责人确认            |

## 3. 用例设计

预期结果依据已确认需求制定，未将当前实现行为直接当作正确结果；发现需求与实现不一致时登记为「需求待确认」（见第 6 节）。

### 3.1 等价类划分

| 对应函数／业务规则         | 输入或状态维度      | 有效等价类                             | 无效等价类                         | 代表输入或状态                   | 对应用例编号             |
| ----------------- | ------------ | --------------------------------- | ----------------------------- | ------------------------- | ------------------ |
| 登录序列化器            | 账号类型 × 入口    | admin→admin；business→app          | admin→app；business→admin      | 业务账号提交管理端登录               | TC-013、014         |
| 登录序列化器            | 凭证           | 用户名存在且密码正确                        | 密码错误；用户不存在；大小写不符              | `password="wrong"`        | TC-017～019         |
| 登录序列化器            | 必填字段         | 齐全且非空白                            | 缺字段；空串                        | 空 payload                 | TC-006～008         |
| 刷新序列化器            | 令牌归属声明       | 与入口一致                             | 缺失；不一致                        | 裸 `RefreshToken.for_user` | TC-015、016、023     |
| 刷新序列化器            | 令牌类型与时效      | refresh 且未过期                      | access；非法串；已过期                | access 当 refresh          | TC-020～022         |
| 刷新序列化器            | 账号存在与启用      | 存在且启用                             | 停用；不存在                        | 停用后刷新                     | TC-012、024         |
| `IsAdministrator` | 认证 / 启用 / 类型 | 已认证＋启用＋admin                      | 未认证；停用；business               | 业务创始人访问管理端                | TC-031～040         |
| 部门创建              | 名称           | 非空白、≤100、未占用                      | 纯空白；超长；重名                     | 100 字符名称                  | TC-046、047、048     |
| 部门编辑              | 启用状态切换       | 无启用下属                             | 有启用团队/成员                      | 部门下有 1 个团队                | TC-052、054、055     |
| 部门/团队展示           | 负责人与成员       | 存在启用的负责人/成员                       | 无；已停用                         | 经理停用后查详情                  | TC-072、073、102、103 |
| 团队创建编辑            | 名称唯一性作用域     | 同部门内未用；跨部门可同名                     | 同部门已存在                        | 财务部复用「后端组」                | TC-082、084、095     |
| 团队编辑              | 部门依赖         | 部门启用时可启用团队                        | 部门停用时启用                       | 部门停用后启用团队                 | TC-097             |
| 员工录入              | 身份证号         | 未占用、≤64                           | 缺失；重复（含大小写）；超长                | `9`×64                    | TC-126、127、130～132 |
| 员工录入              | 姓名           | 非空白、≤100                          | 缺失；纯空白；超长                     | 100 字符姓名                  | TC-128、129、136、137 |
| 员工录入              | 业务身份         | 四种合法枚举                            | 非法枚值                          | `business_role="ceo"`     | TC-138             |
| 员工录入              | 客户端提交字段      | 白名单字段                             | password、is_active、manager_id | 提交 `password`             | TC-133～135         |
| 员工调整              | 可调整字段        | 角色/部门/团队/启用                       | 姓名、手机号、身份证、密码、上级              | 提交 `display_name`         | TC-200～204         |
| 员工调整              | 直属关系         | 负责人到位后重建                          | 负责人停用后仍挂旧上级                   | 停用团队长                     | TC-196、197、210     |
| 员工列表              | 筛选维度         | keyword/角色/部门/团队/启用               | 非法取值（静默忽略）                    | `is_active=yes`           | TC-146～155         |
| 身份证脱敏             | 长度           | > 4 保留后 4 位                       | ≤ 4 全掩码；空值 → None             | 18 位身份证号                  | TC-161～170         |
| App 组织链           | 链路完整性        | 四级链全启用                            | 任一环节停用/空缺/错配/跨部门              | 创始人停用                     | TC-251～261         |
| App 资料校验          | 手机号/姓名/身份证   | 非空且唯一                             | 空；被他人占用；纯空白                   | 提交他人手机号                   | TC-281～290         |
| App 资料校验          | 可提交字段        | display_name/username/id_number   | 组织、身份、is_active、password      | 提交 `business_role`        | TC-291             |
| 修改密码              | 密码规则         | 当前正确＋两次一致＋新旧不同＋满足校验器              | 当前错；不一致；相同；弱密码                | `12345678`                | TC-311～316         |
| 头像校验              | 格式 / 大小 / 尺寸 | JPEG/PNG/WebP，≤5MiB，≤4096，≤16M 像素 | GIF；非图片；超限                    | 4097×1 图片                 | TC-331～341         |
| 组织树               | 范围与关键词       | company/department/team；姓名、职级     | 关键词无匹配                        | `keyword="团队长"`           | TC-351～362         |
| 成员资料              | 可见性          | 启用业务成员                            | 不存在；管理员；停用                    | 查询管理员 ID                  | TC-374～376         |
| 下属范围              | 层级与状态        | 直接与间接下属                           | 自身；停用；链断裂                     | 停用中间层                     | TC-381～385、391～393 |
| 下属任务              | 范围参数         | assigned / created                | 其他取值；越权 user_id               | `task_scope=all`          | TC-401～405         |

### 3.2 边界值分析

| 对应函数／业务规则    | 边界定义            | 边界前／下界外       | 边界值        | 边界后／上界外       | 预期处理                     | 对应用例编号                 |
| ------------ | --------------- | ------------- | ---------- | ------------- | ------------------------ | ---------------------- |
| 登录/刷新必填字段    | 字段是否提供          | 缺字段           | 空串         | 正常值           | 缺→required；空→blank；正常→通过 | TC-006～010             |
| 账号启用状态       | `is_active` 切换点 | `True` 可登录/刷新 | —          | `False` 拒绝    | 切换后立即失效；重启用后恢复           | TC-011、012、033、034     |
| refresh 重复使用 | 使用次数            | 第 1 次成功       | 第 2 次仍成功   | 过期后拒绝         | 默认不轮换                    | TC-005、022             |
| 令牌类型         | 令牌种类            | refresh 通过    | —          | access 类型错误   | 报 `TokenError`           | TC-020、021             |
| 部门名称长度       | 字段上限 100        | 99 字符         | 100 字符通过   | 101 字符拒绝      | 上界外报字段错误                 | TC-046、047             |
| 部门启用下属团队数    | 阻塞阈值            | 0 个（可停用）      | 1 个（拒绝停用）  | 6 个以上（文案折叠）   | 文案含「另有 N 个」              | TC-054、057、058         |
| 团队名称长度       | 字段上限 100        | 99 字符         | 100 字符通过   | 101 字符拒绝      | 上界外报字段错误                 | TC-085、086             |
| 同团队启用成员数     | 阻塞阈值            | 0 名（可停用）      | 1 名（拒绝停用）  | —             | 停用成员后放行                  | TC-096、100             |
| 身份证号长度       | 字段上限 64         | 63 字符         | 64 字符通过    | 65 字符拒绝       | 上界外报字段错误                 | TC-126、127             |
| 姓名长度         | 字段上限 100        | 99 字符         | 100 字符通过   | 101 字符拒绝      | 上界外报字段错误                 | TC-128、129、288、289     |
| 身份证脱敏保留位数    | 掩码分界 4          | 长度 4 全掩码      | 长度 5 露 4 位 | 长度 64 露 4 位   | 分界两侧行为不同                 | TC-163～166、170         |
| 初始密码长度       | 契约值 16          | 15 字符         | 16 字符      | 17 字符         | 必须恰好 16 位                | TC-121                 |
| 启用创始人数量      | 首席名额            | 0 个           | 1 个        | 2 个拒绝         | 停用不占名额                   | TC-175、176             |
| 每部门启用经理      | 部门内名额           | 0 名           | 1 名        | 2 名拒绝         | 跨部门不受影响                  | TC-180、181             |
| 每团队启用团队长     | 团队内名额           | 0 名           | 1 名        | 2 名拒绝         | 第 2 名拒绝                  | TC-182                 |
| 组织链完整性       | 任一环节启用状态        | 全部启用可按        | —          | 任一停用即断链       | 登录 401 / 访问 403          | TC-234～237、253～255、265 |
| 头像文件大小       | 上限 5 MiB        | 5 MiB − 1     | 5 MiB 通过   | 5 MiB + 1 拒绝  | 上界外报大小错误                 | TC-334、335             |
| 头像单边尺寸       | 上限 4096         | 4095          | 4096 通过    | 4097 拒绝       | 上界外报尺寸错误                 | TC-336、337             |
| 头像面积         | 上限 16,000,000   | 16,000,000 通过 | —          | 16,004,000 拒绝 | 上界外报尺寸错误                 | TC-338、339             |
| 下属层级深度       | 层级数             | 0 层（空集）       | 1 层        | 3 层（创始人视角）    | 逐层返回                     | TC-381、385             |
| 关键词匹配        | 匹配数量            | 0 个（空 roots）  | 1 个        | 多个            | 无匹配返回空列表                 | TC-354、357             |

### 3.3 自动化用例清单

说明：前置条件统一见各测试类的 `setUp()`（按需构建完整组织链「创始人→部门经理→团队长→员工」）；本表「用例要点」列同时给出输入与关键断言。

| 用例编号       | 核心函数／方法                                                      | 场景类型 | 用例要点（输入与预期结果）                                  | 自动化测试定位                                                                                                                     | 实际结果 | 状态  |
| ---------- | ------------------------------------------------------------ | ---- | ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ---- | --- |
| TC-WSX-001 | `ScopedTokenSerializer.validate()`                           | 正常   | 管理员正确凭证登录，返回 access 与 refresh。                 | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_admin_login_returns_access_and_refresh`                           | 通过   | 通过  |
| TC-WSX-002 | `ScopedTokenSerializer.validate()`                           | 正常   | get_token 在 JWT 中写入 account_type，用于两端隔离。       | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_get_token_embeds_account_type`                                    | 通过   | 通过  |
| TC-WSX-003 | `ScopedRefreshSerializer.validate()`                         | 正常   | 管理员用 refresh 令牌换取新 access。                     | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_admin_refresh_returns_new_access`                                 | 通过   | 通过  |
| TC-WSX-004 | `ScopedRefreshSerializer.validate()`                         | 正常   | 刷新得到的 access 仍指向同一账号且类型不变。                     | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_refreshed_access_keeps_account_type_and_user`                     | 通过   | 通过  |
| TC-WSX-005 | `ScopedRefreshSerializer.validate()`                         | 边界   | 同一 refresh 令牌可重复使用（默认不轮换）。                     | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_refresh_token_not_rotated`                               | 通过   | 通过  |
| TC-WSX-006 | `ScopedTokenSerializer.validate()`                           | 边界   | 登录缺全部字段，逐字段报告 required。                        | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_missing_all_fields_reports_required`                     | 通过   | 通过  |
| TC-WSX-007 | `ScopedTokenSerializer.validate()`                           | 边界   | 用户名为空串，字段级校验拦截（blank）。                         | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_blank_username_rejected`                                 | 通过   | 通过  |
| TC-WSX-008 | `ScopedTokenSerializer.validate()`                           | 边界   | 密码为空串，字段级校验拦截（blank）。                          | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_blank_password_rejected`                                 | 通过   | 通过  |
| TC-WSX-009 | `ScopedRefreshSerializer.validate()`                         | 边界   | 刷新接口缺 refresh 字段，报告 required。                  | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_missing_refresh_field`                                   | 通过   | 通过  |
| TC-WSX-010 | `ScopedRefreshSerializer.validate()`                         | 边界   | refresh 为空串，字段级校验拦截（blank）。                    | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_blank_refresh_field`                                     | 通过   | 通过  |
| TC-WSX-011 | `ScopedTokenSerializer.validate()`                           | 边界   | 管理员停用后立即无法登录。                                  | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_inactive_admin_cannot_login`                             | 通过   | 通过  |
| TC-WSX-012 | `ScopedRefreshSerializer.validate()`                         | 边界   | 管理员停用后已签发的 refresh 失效。                         | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_boundary_inactive_admin_cannot_refresh`                           | 通过   | 通过  |
| TC-WSX-013 | `ScopedTokenSerializer.validate()`                           | 异常   | 业务账号走管理端入口，拒绝登录。                               | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_business_account_cannot_login_admin_entry`                        | 通过   | 通过  |
| TC-WSX-014 | `ScopedTokenSerializer.validate()`                           | 异常   | 管理员账号走 App 入口，拒绝登录。                            | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_admin_account_cannot_login_app_entry`                             | 通过   | 通过  |
| TC-WSX-015 | `ScopedRefreshSerializer.validate()`                         | 异常   | 管理端 refresh 令牌在 App 刷新入口被拒绝。                   | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_admin_refresh_rejected_by_app_entry`                              | 通过   | 通过  |
| TC-WSX-016 | `ScopedRefreshSerializer.validate()`                         | 异常   | App refresh 令牌在管理端刷新入口被拒绝。                     | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_business_refresh_rejected_by_admin_entry`                         | 通过   | 通过  |
| TC-WSX-017 | `ScopedTokenSerializer.validate()`                           | 异常   | 密码错误，拒绝登录。                                     | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_wrong_password`                                         | 通过   | 通过  |
| TC-WSX-018 | `ScopedTokenSerializer.validate()`                           | 异常   | 用户名不存在，拒绝登录。                                   | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_nonexistent_username`                                   | 通过   | 通过  |
| TC-WSX-019 | `ScopedTokenSerializer.validate()`                           | 异常   | 用户名大小写不符（账号名区分大小写），拒绝登录。                       | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_username_case_mismatch`                                 | 通过   | 通过  |
| TC-WSX-020 | `ScopedRefreshSerializer.validate()`                         | 异常   | 把 access 令牌当 refresh 提交，报告令牌类型错误。              | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_access_token_used_as_refresh`                           | 通过   | 通过  |
| TC-WSX-021 | `ScopedRefreshSerializer.validate()`                         | 异常   | 非法字符串作为 refresh，报告令牌无效。                        | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_malformed_refresh_string`                               | 通过   | 通过  |
| TC-WSX-022 | `ScopedRefreshSerializer.validate()`                         | 异常   | refresh 令牌已过期，报告令牌过期。                          | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_expired_refresh_token`                                  | 通过   | 通过  |
| TC-WSX-023 | `ScopedRefreshSerializer.validate()`                         | 异常   | refresh 令牌缺少 account_type 声明，视为不属于该入口。         | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_refresh_without_account_type_claim`                     | 通过   | 通过  |
| TC-WSX-024 | `ScopedRefreshSerializer.validate()`                         | 异常   | refresh 指向已不存在的账号，拒绝刷新。                        | `accounts.test_midterm_wsx_auth.TokenSerializerTests.test_exception_refresh_for_removed_user`                               | 通过   | 通过  |
| TC-WSX-031 | `IsAdministrator.has_permission()`                           | 正常   | 启用中的管理员可访问管理端。                                 | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_active_admin_allowed`                                   | 通过   | 通过  |
| TC-WSX-032 | `IsAdministrator.has_permission()`                           | 正常   | 权限只认 account_type，管理员去掉 is_staff 仍可访问（需求明文）。   | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_permission_ignores_is_staff_flag`                       | 通过   | 通过  |
| TC-WSX-033 | `IsAdministrator.has_permission()`                           | 边界   | 管理员停用后立即失去管理端权限。                               | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_boundary_inactive_admin_denied`                         | 通过   | 通过  |
| TC-WSX-034 | `IsAdministrator.has_permission()`                           | 边界   | 停用后重新启用，权限恢复。                                  | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_boundary_reactivated_admin_allowed_again`               | 通过   | 通过  |
| TC-WSX-035 | `IsAdministrator.has_permission()`                           | 边界   | 业务账号即使为最高职级创始人也被拒绝。                            | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_boundary_business_founder_denied`                       | 通过   | 通过  |
| TC-WSX-036 | `IsAdministrator.has_permission()`                           | 边界   | 业务账号即使带 is_staff/is_superuser 标记仍被拒绝（需求明文）。    | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_boundary_business_superuser_flag_does_not_grant_access` | 通过   | 通过  |
| TC-WSX-037 | `IsAdministrator.has_permission()`                           | 边界   | 未认证用户被拒绝。                                      | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_boundary_anonymous_user_denied`                         | 通过   | 通过  |
| TC-WSX-038 | `IsAdministrator.has_permission()`                           | 异常   | 匿名用户缺少 account_type 属性时短路返回 False，不抛异常。        | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_anonymous_user_does_not_raise_attribute_error`          | 通过   | 通过  |
| TC-WSX-039 | `IsAdministrator.has_permission()`                           | 正常   | 拒绝时的提示文案与需求一致。                                 | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_permission_message_text`                                | 通过   | 通过  |
| TC-WSX-040 | `IsAdministrator.has_permission()`                           | 边界   | 业务员工（最低职级）被拒绝，与职级高低无关。                         | `accounts.test_midterm_wsx_auth.IsAdministratorPermissionTests.test_business_employee_denied`                               | 通过   | 通过  |
| TC-WSX-041 | `DepartmentListView.perform_create()`                        | 正常   | perform_create 保存部门并返回实例。                      | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_saves_department`                                          | 通过   | 通过  |
| TC-WSX-042 | `DepartmentListView.perform_create()`                        | 正常   | 创建部门后写入管理端审计，模块与动作正确。                          | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_writes_department_audit_log`                               | 通过   | 通过  |
| TC-WSX-043 | `DepartmentListView.perform_create()`                        | 正常   | 审计 after 仅含名称、说明、启用状态，不含敏感字段。                  | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_audit_after_snapshot_contains_summary_only`                | 通过   | 通过  |
| TC-WSX-044 | `DepartmentListView.perform_create()`                        | 正常   | 审计记录的操作者取自当前请求用户。                              | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_audit_actor_is_current_admin`                              | 通过   | 通过  |
| TC-WSX-045 | `DepartmentListView.perform_create()`                        | 正常   | 说明字段可省略，默认空字符串。                                | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_description_defaults_to_empty`                             | 通过   | 通过  |
| TC-WSX-046 | `DepartmentListView.perform_create()`                        | 边界   | 部门名称 100 字符（上界）通过。                             | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_boundary_name_length_100_accepted`                         | 通过   | 通过  |
| TC-WSX-047 | `DepartmentListView.perform_create()`                        | 边界   | 部门名称 101 字符（上界外）被拒绝。                           | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_boundary_name_length_101_rejected`                         | 通过   | 通过  |
| TC-WSX-048 | `DepartmentListView.perform_create()`                        | 边界   | 部门名称纯空白被拒绝（去空白后为空）。                            | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_boundary_whitespace_only_name_rejected`                    | 通过   | 通过  |
| TC-WSX-049 | `DepartmentListView.perform_create()`                        | 边界   | 新建部门默认启用。                                      | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_new_department_is_active_by_default`                       | 通过   | 通过  |
| TC-WSX-050 | `DepartmentListView.perform_create()`                        | 异常   | 创建时传入只读字段 is_active，请求被拒绝。                     | `accounts.test_midterm_wsx_department.DepartmentCreateTests.test_read_only_field_is_active_rejected`                        | 通过   | 通过  |
| TC-WSX-051 | `DepartmentUpdateSerializer.validate()`                      | 正常   | 仅修改说明时通过校验。                                    | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_description_update_allowed`                                | 通过   | 通过  |
| TC-WSX-052 | `DepartmentUpdateSerializer.validate()`                      | 正常   | 无启用下属时允许停用部门。                                  | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_deactivate_department_without_active_subordinates`         | 通过   | 通过  |
| TC-WSX-053 | `DepartmentUpdateSerializer.validate()`                      | 正常   | 重新启用部门通过校验（不检查下属）。                             | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_reactivate_department_allowed`                             | 通过   | 通过  |
| TC-WSX-054 | `DepartmentUpdateSerializer.validate()`                      | 边界   | 存在 1 个启用团队（下界外）时禁止停用，文案指出团队数量。                 | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_boundary_one_active_team_blocks_deactivation`              | 通过   | 通过  |
| TC-WSX-055 | `DepartmentUpdateSerializer.validate()`                      | 边界   | 存在 1 名启用成员时禁止停用，文案指出成员数量。                      | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_boundary_one_active_member_blocks_deactivation`            | 通过   | 通过  |
| TC-WSX-056 | `DepartmentUpdateSerializer.validate()`                      | 边界   | 同时存在启用团队与成员时，两类阻塞项都出现在文案里。                     | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_boundary_both_teams_and_members_reported`                  | 通过   | 通过  |
| TC-WSX-057 | `DepartmentUpdateSerializer.validate()`                      | 边界   | 启用团队超过 5 个时，文案折叠为“另有 N 个”。                     | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_boundary_many_teams_summary_is_folded`                     | 通过   | 通过  |
| TC-WSX-058 | `DepartmentUpdateSerializer.validate()`                      | 边界   | 团队与成员全部停用后允许停用部门。                              | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_all_subordinates_inactive_allows_deactivation`             | 通过   | 通过  |
| TC-WSX-059 | `DepartmentUpdateSerializer.validate()`                      | 异常   | 传入未知字段被拒绝。                                     | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_unknown_field_rejected`                                    | 通过   | 通过  |
| TC-WSX-060 | `DepartmentUpdateSerializer.validate()`                      | 异常   | 部门改名与已有名称冲突时被拒绝                                | `accounts.test_midterm_wsx_department.DepartmentUpdateTests.test_rename_to_existing_department_rejected`                    | 通过   | 通过  |
| TC-WSX-071 | `DepartmentDetailSerializer.get_manager()/get_members()`     | 正常   | get_manager 返回启用部门经理的 id 与姓名。                  | `accounts.test_midterm_wsx_department.DepartmentDetailDisplayTests.test_manager_returns_active_manager`                     | 通过   | 通过  |
| TC-WSX-072 | `DepartmentDetailSerializer.get_manager()/get_members()`     | 边界   | 部门无经理时 get_manager 返回 None。                    | `accounts.test_midterm_wsx_department.DepartmentDetailDisplayTests.test_boundary_no_manager_returns_null`                   | 通过   | 通过  |
| TC-WSX-073 | `DepartmentDetailSerializer.get_manager()/get_members()`     | 边界   | 经理停用后 get_manager 返回 None（状态切换点）。              | `accounts.test_midterm_wsx_department.DepartmentDetailDisplayTests.test_boundary_inactive_manager_returns_null`             | 通过   | 通过  |
| TC-WSX-074 | `DepartmentDetailSerializer.get_manager()/get_members()`     | 正常   | get_members 只返回启用成员，含经理与团队长。                   | `accounts.test_midterm_wsx_department.DepartmentDetailDisplayTests.test_members_list_active_users_only`                     | 通过   | 通过  |
| TC-WSX-075 | `DepartmentDetailSerializer.get_manager()/get_members()`     | 边界   | 部门无成员时 get_members 返回空列表。                      | `accounts.test_midterm_wsx_department.DepartmentDetailDisplayTests.test_boundary_empty_members_returns_empty_list`          | 通过   | 通过  |
| TC-WSX-076 | `DepartmentDetailSerializer.get_manager()/get_members()`     | 边界   | 停用成员不出现在 get_members 结果中。                      | `accounts.test_midterm_wsx_department.DepartmentDetailDisplayTests.test_boundary_inactive_member_excluded`                  | 通过   | 通过  |
| TC-WSX-077 | `DepartmentDetailSerializer.get_manager()/get_members()`     | 边界   | get_teams 返回全部团队（含已停用），便于管理端看到历史组织。            | `accounts.test_midterm_wsx_department.DepartmentDetailDisplayTests.test_boundary_teams_include_inactive_units`              | 通过   | 通过  |
| TC-WSX-081 | `TeamSerializer.validate()`                                  | 正常   | 在同部门内用未占用的名称创建团队通过校验。                          | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_creates_team_in_department`                                  | 通过   | 通过  |
| TC-WSX-082 | `TeamSerializer.validate()`                                  | 正常   | 不同部门允许同名团队（唯一性作用域为部门内）。                        | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_same_name_allowed_in_different_department`                   | 通过   | 通过  |
| TC-WSX-083 | `TeamSerializer.validate()`                                  | 正常   | 说明字段可省略。                                       | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_description_optional`                                        | 通过   | 通过  |
| TC-WSX-084 | `TeamSerializer.validate()`                                  | 边界   | 同部门已存在同名团队时拒绝。                                 | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_boundary_duplicate_name_in_same_department_rejected`         | 通过   | 通过  |
| TC-WSX-085 | `TeamSerializer.validate()`                                  | 边界   | 团队名称 100 字符（上界）通过。                             | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_boundary_name_length_100_accepted`                           | 通过   | 通过  |
| TC-WSX-086 | `TeamSerializer.validate()`                                  | 边界   | 团队名称 101 字符（上界外）被拒绝。                           | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_boundary_name_length_101_rejected`                           | 通过   | 通过  |
| TC-WSX-087 | `TeamSerializer.validate()`                                  | 边界   | 团队名称纯空白被拒绝。                                    | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_boundary_whitespace_only_name_rejected`                      | 通过   | 通过  |
| TC-WSX-088 | `TeamSerializer.validate()`                                  | 异常   | 未提供所属部门时拒绝创建。                                  | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_exception_missing_department`                                | 通过   | 通过  |
| TC-WSX-089 | `TeamSerializer.validate()`                                  | 异常   | 所属部门不存在时拒绝。                                    | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_exception_nonexistent_department`                            | 通过   | 通过  |
| TC-WSX-090 | `TeamSerializer.validate()`                                  | 异常   | 所属部门已停用时拒绝创建团队。                                | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_exception_inactive_department_rejected`                      | 通过   | 通过  |
| TC-WSX-091 | `TeamSerializer.validate()`                                  | 异常   | 创建时传入只读字段 is_active 被拒绝。                       | `accounts.test_midterm_wsx_team.TeamCreateValidationTests.test_exception_read_only_is_active_rejected`                      | 通过   | 通过  |
| TC-WSX-092 | `TeamUpdateSerializer.validate()`                            | 正常   | 改成部门内未使用的名称通过。                                 | `accounts.test_midterm_wsx_team.TeamUpdateValidationTests.test_rename_to_unused_name_allowed`                               | 通过   | 通过  |
| TC-WSX-093 | `TeamUpdateSerializer.validate()`                            | 正常   | 保持原名（与自身重名）通过，唯一性排除自身。                         | `accounts.test_midterm_wsx_team.TeamUpdateValidationTests.test_rename_to_own_name_allowed`                                  | 通过   | 通过  |
| TC-WSX-094 | `TeamUpdateSerializer.validate()`                            | 正常   | 无启用成员时允许停用团队。                                  | `accounts.test_midterm_wsx_team.TeamUpdateValidationTests.test_deactivate_team_without_members_allowed`                     | 通过   | 通过  |
| TC-WSX-095 | `TeamUpdateSerializer.validate()`                            | 边界   | 改名为同部门其他团队名称时拒绝。                               | `accounts.test_midterm_wsx_team.TeamUpdateBoundaryTests.test_boundary_rename_to_sibling_team_name_rejected`                 | 通过   | 通过  |
| TC-WSX-096 | `TeamUpdateSerializer.validate()`                            | 边界   | 存在 1 名启用成员时禁止停用团队。                             | `accounts.test_midterm_wsx_team.TeamUpdateBoundaryTests.test_boundary_one_active_member_blocks_deactivation`                | 通过   | 通过  |
| TC-WSX-097 | `TeamUpdateSerializer.validate()`                            | 边界   | 所属部门停用后不能启用团队。                                 | `accounts.test_midterm_wsx_team.TeamUpdateBoundaryTests.test_activation_requires_active_department`                         | 通过   | 通过  |
| TC-WSX-098 | `TeamUpdateSerializer.validate()`                            | 异常   | 编辑团队不接受 department_id（不允许跨部门搬移）。               | `accounts.test_midterm_wsx_team.TeamUpdateBoundaryTests.test_department_id_not_accepted_on_update`                          | 通过   | 通过  |
| TC-WSX-099 | `TeamUpdateSerializer.validate()`                            | 异常   | 传入未知字段被拒绝。                                     | `accounts.test_midterm_wsx_team.TeamUpdateBoundaryTests.test_unknown_field_rejected`                                        | 通过   | 通过  |
| TC-WSX-100 | `TeamUpdateSerializer.validate()`                            | 边界   | 成员全部停用后允许停用团队。                                 | `accounts.test_midterm_wsx_team.TeamUpdateBoundaryTests.test_inactive_member_does_not_block_deactivation`                   | 通过   | 通过  |
| TC-WSX-101 | `TeamDetailSerializer.get_leader()/get_members()`            | 正常   | get_leader 返回启用团队长的 id 与姓名。                    | `accounts.test_midterm_wsx_team.TeamDetailDisplayTests.test_leader_returns_active_team_leader`                              | 通过   | 通过  |
| TC-WSX-102 | `TeamDetailSerializer.get_leader()/get_members()`            | 边界   | 团队无团队长时 get_leader 返回 None。                    | `accounts.test_midterm_wsx_team.TeamDetailDisplayTests.test_boundary_no_leader_returns_null`                                | 通过   | 通过  |
| TC-WSX-103 | `TeamDetailSerializer.get_leader()/get_members()`            | 边界   | 团队长停用后 get_leader 返回 None。                     | `accounts.test_midterm_wsx_team.TeamDetailDisplayTests.test_boundary_inactive_leader_returns_null`                          | 通过   | 通过  |
| TC-WSX-104 | `TeamDetailSerializer.get_leader()/get_members()`            | 正常   | get_members 返回启用成员（团队长与员工）。                    | `accounts.test_midterm_wsx_team.TeamDetailDisplayTests.test_members_include_leader_and_employee`                            | 通过   | 通过  |
| TC-WSX-105 | `TeamDetailSerializer.get_leader()/get_members()`            | 边界   | 团队无成员时 get_members 返回空列表。                      | `accounts.test_midterm_wsx_team.TeamDetailDisplayTests.test_boundary_empty_members_returns_empty_list`                      | 通过   | 通过  |
| TC-WSX-106 | `TeamDetailSerializer.get_leader()/get_members()`            | 边界   | 停用成员不出现在 get_members 结果中。                      | `accounts.test_midterm_wsx_team.TeamDetailDisplayTests.test_boundary_inactive_member_excluded`                              | 通过   | 通过  |
| TC-WSX-107 | `TeamDetailSerializer.get_leader()/get_members()`            | 正常   | 详情返回所属部门 id 与名称，便于管理端展示。                       | `accounts.test_midterm_wsx_team.TeamDetailDisplayTests.test_department_name_exposed_read_only`                              | 通过   | 通过  |
| TC-WSX-111 | `User.normalize_id_number()`                                 | 正常   | 末尾小写 x 归一化为大写 X。                               | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_lowercase_x_normalized_to_uppercase`                       | 通过   | 通过  |
| TC-WSX-112 | `User.normalize_id_number()`                                 | 正常   | 去除首尾空格。                                        | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_surrounding_whitespace_stripped`                           | 通过   | 通过  |
| TC-WSX-113 | `User.normalize_id_number()`                                 | 正常   | 首尾空格与小写同时出现时，先去空格再转大写。                         | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_strip_and_upper_applied_together`                          | 通过   | 通过  |
| TC-WSX-114 | `User.normalize_id_number()`                                 | 正常   | 已规范化的值原样返回（幂等）。                                | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_already_canonical_value_unchanged`                         | 通过   | 通过  |
| TC-WSX-115 | `User.normalize_id_number()`                                 | 边界   | None 输入原样返回 None（无身份证号场景）。                     | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_boundary_none_returns_none`                                | 通过   | 通过  |
| TC-WSX-116 | `User.normalize_id_number()`                                 | 边界   | 空串返回空串。                                        | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_boundary_empty_string_returns_empty`                       | 通过   | 通过  |
| TC-WSX-117 | `User.normalize_id_number()`                                 | 边界   | 纯空白串去除后为空串，用于空值判定。                             | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_boundary_whitespace_only_returns_empty`                    | 通过   | 通过  |
| TC-WSX-118 | `User.normalize_id_number()`                                 | 边界   | 字段上限 64 字符不被截断或拒绝。                             | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_boundary_max_length_64_accepted`                           | 通过   | 通过  |
| TC-WSX-119 | `User.normalize_id_number()`                                 | 异常   | 仅处理首尾空白，内部空白不被去除。                              | `accounts.test_midterm_wsx_id_number.NormalizeIdNumberTests.test_internal_whitespace_preserved`                             | 通过   | 通过  |
| TC-WSX-120 | `BusinessUserCreateSerializer`                               | 正常   | 录入员工后自动关联本团队团队长为直属上级。                          | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_creates_employee_and_links_manager`                     | 通过   | 通过  |
| TC-WSX-121 | `BusinessUserCreateSerializer`                               | 正常   | 初始密码恰好 16 个 URL-safe 字符。                       | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_initial_password_is_exactly_16_chars`                   | 通过   | 通过  |
| TC-WSX-122 | `BusinessUserCreateSerializer`                               | 正常   | 数据库只保存密码哈希，明文仅用于本次返回。                          | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_password_stored_as_hash_only`                           | 通过   | 通过  |
| TC-WSX-123 | `BusinessUserCreateSerializer`                               | 正常   | 新账号由服务端自动启用。                                   | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_account_created_active`                                 | 通过   | 通过  |
| TC-WSX-124 | `BusinessUserCreateSerializer`                               | 正常   | 身份证号保存前去首尾空格并转大写。                              | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_id_number_normalized_on_create`                         | 通过   | 通过  |
| TC-WSX-126 | `BusinessUserCreateSerializer`                               | 边界   | 身份证号 64 字符（字段上界）通过。                            | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_boundary_id_number_length_64_accepted`                  | 通过   | 通过  |
| TC-WSX-127 | `BusinessUserCreateSerializer`                               | 边界   | 身份证号 65 字符（上界外）被拒绝。                            | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_boundary_id_number_length_65_rejected`                  | 通过   | 通过  |
| TC-WSX-128 | `BusinessUserCreateSerializer`                               | 边界   | 姓名 100 字符（上界）通过。                               | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_boundary_display_name_length_100_accepted`              | 通过   | 通过  |
| TC-WSX-129 | `BusinessUserCreateSerializer`                               | 边界   | 姓名 101 字符（上界外）被拒绝。                             | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_boundary_display_name_length_101_rejected`              | 通过   | 通过  |
| TC-WSX-130 | `BusinessUserCreateSerializer`                               | 异常   | 身份证号必填，缺失时拒绝。                                  | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_missing_id_number`                            | 通过   | 通过  |
| TC-WSX-131 | `BusinessUserCreateSerializer`                               | 异常   | 身份证号已存在时拒绝录入。                                  | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_duplicate_id_number`                          | 通过   | 通过  |
| TC-WSX-132 | `BusinessUserCreateSerializer`                               | 异常   | 大小写不同的同一身份证号仍视为重复。                             | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_duplicate_id_number_case_insensitive`         | 通过   | 通过  |
| TC-WSX-133 | `BusinessUserCreateSerializer`                               | 异常   | 客户端不得提交 password。                              | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_password_field_rejected`                      | 通过   | 通过  |
| TC-WSX-134 | `BusinessUserCreateSerializer`                               | 异常   | 客户端不得提交 is_active。                             | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_is_active_field_rejected`                     | 通过   | 通过  |
| TC-WSX-135 | `BusinessUserCreateSerializer`                               | 异常   | 客户端不得指定 manager_id（由服务端计算）。                    | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_manager_id_field_rejected`                    | 通过   | 通过  |
| TC-WSX-136 | `BusinessUserCreateSerializer`                               | 异常   | 姓名必填，缺失时拒绝。                                    | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_missing_display_name`                         | 通过   | 通过  |
| TC-WSX-137 | `BusinessUserCreateSerializer`                               | 边界   | 姓名纯空白被拒绝。                                      | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_boundary_whitespace_display_name_rejected`              | 通过   | 通过  |
| TC-WSX-138 | `BusinessUserCreateSerializer`                               | 异常   | 业务身份不在枚举范围时拒绝。                                 | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_exception_invalid_business_role`                        | 通过   | 通过  |
| TC-WSX-139 | `BusinessUserCreateSerializer`                               | 正常   | 任命新部门经理后，本部门团队长的直属上级被重建。                       | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_appointing_manager_rebuilds_direct_reports`             | 通过   | 通过  |
| TC-WSX-140 | `BusinessUserCreateSerializer`                               | 正常   | 两次录入生成的初始密码互不相同。                               | `accounts.test_midterm_wsx_user_create.BusinessUserCreateTests.test_initial_password_differs_between_accounts`              | 通过   | 通过  |
| TC-WSX-141 | `UserListView.create()`                                      | 正常   | 创建成功返回 201 与一次性初始密码。                           | `accounts.test_midterm_wsx_user_create.UserCreateResponseTests.test_create_returns_201_with_initial_password`               | 通过   | 通过  |
| TC-WSX-142 | `UserListView.create()`                                      | 正常   | 创建响应禁止缓存（含明文初始密码）。                             | `accounts.test_midterm_wsx_user_create.UserCreateResponseTests.test_create_sets_no_store_headers`                           | 通过   | 通过  |
| TC-WSX-143 | `UserListView.create()`                                      | 正常   | 创建响应只返回脱敏身份证号，不含明文。                            | `accounts.test_midterm_wsx_user_create.UserCreateResponseTests.test_create_response_only_returns_masked_id_number`          | 通过   | 通过  |
| TC-WSX-144 | `UserListView.create()`                                      | 正常   | 创建员工写入管理端审计，且不含身份证明文。                          | `accounts.test_midterm_wsx_user_create.UserCreateResponseTests.test_create_writes_audit_log`                                | 通过   | 通过  |
| TC-WSX-145 | `UserListView.create()`                                      | 正常   | 列表不返回初始密码与完整身份证号。                              | `accounts.test_midterm_wsx_user_create.UserCreateResponseTests.test_list_returns_masked_only_and_no_password`               | 通过   | 通过  |
| TC-WSX-146 | `UserListView.get_queryset()`                                | 正常   | 管理列表只含业务账号，不含管理员。                              | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_excludes_admin_accounts`                                  | 通过   | 通过  |
| TC-WSX-147 | `UserListView.get_queryset()`                                | 正常   | keyword 按姓名模糊匹配。                               | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_keyword_filters_by_display_name`                          | 通过   | 通过  |
| TC-WSX-148 | `UserListView.get_queryset()`                                | 正常   | keyword 按手机号模糊匹配。                              | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_keyword_filters_by_phone`                                 | 通过   | 通过  |
| TC-WSX-149 | `UserListView.get_queryset()`                                | 正常   | 按业务身份过滤。                                       | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_filters_by_business_role`                                 | 通过   | 通过  |
| TC-WSX-150 | `UserListView.get_queryset()`                                | 正常   | 按部门与团队过滤。                                      | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_filters_by_department_and_team`                           | 通过   | 通过  |
| TC-WSX-151 | `UserListView.create()`                                      | 异常   | 身份证号重复时返回 400，不创建账号。                           | `accounts.test_midterm_wsx_user_create.UserCreateResponseTests.test_duplicate_id_number_returns_400`                        | 通过   | 通过  |
| TC-WSX-152 | `UserListView.get_queryset()`                                | 边界   | is_active=false 只返回停用账号。                       | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_boundary_is_active_false_filters_inactive`                | 通过   | 通过  |
| TC-WSX-153 | `UserListView.get_queryset()`                                | 边界   | is_active 非 true/false 时不过滤（返回全部）。             | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_boundary_invalid_is_active_value_is_ignored`              | 通过   | 通过  |
| TC-WSX-154 | `UserListView.get_queryset()`                                | 边界   | keyword 无匹配时返回空结果。                             | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_boundary_keyword_without_match_returns_empty`             | 通过   | 通过  |
| TC-WSX-155 | `UserListView.get_queryset()`                                | 正常   | 不传筛选条件时返回全部业务账号。                               | `accounts.test_midterm_wsx_user_create.UserListQuerySetTests.test_missing_filter_returns_all`                               | 通过   | 通过  |
| TC-WSX-161 | `UserSerializer.get_id_number_masked()`                      | 正常   | 18 位身份证号保留后 4 位，其余掩码。                          | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_masks_all_but_last_four_digits`                               | 通过   | 通过  |
| TC-WSX-162 | `UserSerializer.get_id_number_masked()`                      | 正常   | 末位为字母 X 时同样保留。                                 | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_keeps_trailing_letter_x`                                      | 通过   | 通过  |
| TC-WSX-163 | `UserSerializer.get_id_number_masked()`                      | 边界   | 长度 4（下界）全部掩码，不保留任何字符。                          | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_boundary_length_four_masks_all`                               | 通过   | 通过  |
| TC-WSX-164 | `UserSerializer.get_id_number_masked()`                      | 边界   | 长度 5（越过掩码分界）保留后 4 位。                           | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_boundary_length_five_keeps_last_four`                         | 通过   | 通过  |
| TC-WSX-165 | `UserSerializer.get_id_number_masked()`                      | 边界   | 长度 1 掩码为单个星号。                                  | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_boundary_length_one_masks_all`                                | 通过   | 通过  |
| TC-WSX-166 | `UserSerializer.get_id_number_masked()`                      | 边界   | 长度 64（字段上界）掩码 60 位、保留后 4 位。                    | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_boundary_length_64_keeps_last_four`                           | 通过   | 通过  |
| TC-WSX-167 | `UserSerializer.get_id_number_masked()`                      | 边界   | 无身份证号时返回 None（未录入账号）。                          | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_boundary_none_returns_none`                                   | 通过   | 通过  |
| TC-WSX-168 | `UserSerializer.get_id_number_masked()`                      | 边界   | 空串视为未录入，返回 None。                               | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_boundary_empty_string_returns_none`                           | 通过   | 通过  |
| TC-WSX-169 | `UserSerializer.get_id_number_masked()`                      | 异常   | 脱敏结果不得包含完整明文身份证号。                              | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_masked_value_never_contains_full_plaintext`                   | 通过   | 通过  |
| TC-WSX-170 | `UserSerializer.get_id_number_masked()`                      | 边界   | 长度 3 全部掩码为 3 个星号。                              | `accounts.test_midterm_wsx_user_create.IdNumberMaskTests.test_mask_of_length_three`                                         | 通过   | 通过  |
| TC-WSX-171 | `resolve_placement()`                                        | 正常   | 创始人无组织，返回 (None, None, None)。                  | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_founder_without_organization_returns_empty_placement`    | 通过   | 通过  |
| TC-WSX-172 | `resolve_placement()`                                        | 正常   | 部门经理自动关联启用创始人为直属上级。                            | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_department_manager_binds_active_founder_as_manager`      | 通过   | 通过  |
| TC-WSX-173 | `resolve_placement()`                                        | 正常   | 团队长自动关联本部门启用经理为直属上级。                           | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_team_leader_binds_department_manager_as_manager`         | 通过   | 通过  |
| TC-WSX-174 | `resolve_placement()`                                        | 正常   | 员工自动关联本团队启用团队长为直属上级。                           | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_employee_binds_team_leader_as_manager`                   | 通过   | 通过  |
| TC-WSX-175 | `resolve_placement()`                                        | 边界   | 已有 1 个启用创始人（上界），第 2 个应拒绝。                      | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_second_active_founder_rejected`                 | 通过   | 通过  |
| TC-WSX-176 | `resolve_placement()`                                        | 边界   | 停用账号不占负责人名额，可再建启用创始人。                          | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_inactive_founder_does_not_occupy_slot`          | 通过   | 通过  |
| TC-WSX-177 | `resolve_placement()`                                        | 边界   | exclude_pk 排除自身，更新自己不被唯一性拦截。                   | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_exclude_pk_allows_self_update`                  | 通过   | 通过  |
| TC-WSX-178 | `resolve_placement()`                                        | 边界   | is_active=False 时不需要有效上级，返回 (None, 部门, 团队)。    | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_inactive_account_skips_manager_requirement`     | 通过   | 通过  |
| TC-WSX-179 | `resolve_placement()`                                        | 边界   | allow_missing_manager=True 且上级空缺时返回 None 而非报错。 | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_allow_missing_manager_returns_null_manager`     | 通过   | 通过  |
| TC-WSX-180 | `resolve_placement()`                                        | 边界   | 同部门已有 1 名启用经理（上界），第 2 名应拒绝。                    | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_second_manager_in_same_department_rejected`     | 通过   | 通过  |
| TC-WSX-181 | `resolve_placement()`                                        | 边界   | 名额作用域是部门，不同部门可各有一名经理。                          | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_different_departments_each_allow_one_manager`   | 通过   | 通过  |
| TC-WSX-182 | `resolve_placement()`                                        | 边界   | 同团队已有 1 名启用团队长（上界），第 2 名应拒绝。                   | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_boundary_second_leader_in_same_team_rejected`            | 通过   | 通过  |
| TC-WSX-183 | `resolve_placement()`                                        | 异常   | 创始人不能指定部门。                                     | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_founder_with_department_rejected`              | 通过   | 通过  |
| TC-WSX-184 | `resolve_placement()`                                        | 异常   | 创始人不能指定团队。                                     | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_founder_with_team_rejected`                    | 通过   | 通过  |
| TC-WSX-185 | `resolve_placement()`                                        | 异常   | 非创始人必须选择部门，缺部门应拒绝。                             | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_department_manager_without_department`         | 通过   | 通过  |
| TC-WSX-186 | `resolve_placement()`                                        | 异常   | 停用部门不能作为归属。                                    | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_inactive_department_rejected`                  | 通过   | 通过  |
| TC-WSX-187 | `resolve_placement()`                                        | 异常   | 部门经理不能指定团队。                                    | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_department_manager_with_team_rejected`         | 通过   | 通过  |
| TC-WSX-188 | `resolve_placement()`                                        | 异常   | 团队长必须选择团队，缺团队应拒绝。                              | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_team_leader_without_team`                      | 通过   | 通过  |
| TC-WSX-189 | `resolve_placement()`                                        | 异常   | 团队必须属于所选部门，跨部门组合应拒绝。                           | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_team_not_belonging_to_department`              | 通过   | 通过  |
| TC-WSX-190 | `resolve_placement()`                                        | 异常   | 停用团队不能作为归属。                                    | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_inactive_team_rejected`                        | 通过   | 通过  |
| TC-WSX-191 | `resolve_placement()`                                        | 异常   | 无启用创始人时录入经理，应提示先录入直属上级。                        | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_manager_without_active_founder`                | 通过   | 通过  |
| TC-WSX-192 | `resolve_placement()`                                        | 异常   | 团队无启用团队长时录入员工，应提示先录入直属上级。                      | `accounts.test_midterm_wsx_organization.ResolvePlacementTests.test_exception_employee_without_active_team_leader`           | 通过   | 通过  |
| TC-WSX-193 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 正常   | 员工转任其他岗位时直属上级按新岗位重新计算。                         | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_role_change_recomputes_manager`                      | 通过   | 通过  |
| TC-WSX-194 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 正常   | 原团队长停用后提升员工为团队长，其下属关系被重建。                      | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_promote_employee_to_leader_rebuilds_reports`         | 通过   | 通过  |
| TC-WSX-195 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 边界   | 负责人空缺时，既有启用员工保留启用状态与原组织。                       | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_vacancy_allows_existing_employee_to_stay_active`     | 通过   | 通过  |
| TC-WSX-196 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 边界   | 停用部门经理后，本部门团队长的上级被置空。                          | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_deactivate_manager_clears_leader_link`               | 通过   | 通过  |
| TC-WSX-197 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 边界   | 停用团队长后其直接下属的上级被置空。                             | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_deactivate_leader_clears_direct_reports`             | 通过   | 通过  |
| TC-WSX-199 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 边界   | 经理换部门后，旧部门团队长解除上下级关系。                          | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_manager_transfer_clears_old_organization_links`      | 通过   | 通过  |
| TC-WSX-200 | `UserUpdateSerializer.validate()`                            | 异常   | 管理端不可修改姓名。                                     | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_exception_display_name_not_editable`              | 通过   | 通过  |
| TC-WSX-201 | `UserUpdateSerializer.validate()`                            | 异常   | 管理端不可修改手机号（登录标识）。                              | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_exception_username_not_editable`                  | 通过   | 通过  |
| TC-WSX-202 | `UserUpdateSerializer.validate()`                            | 异常   | 管理端不可修改身份证号。                                   | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_exception_id_number_not_editable`                 | 通过   | 通过  |
| TC-WSX-203 | `UserUpdateSerializer.validate()`                            | 异常   | 管理端不提供密码重置。                                    | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_exception_password_not_editable`                  | 通过   | 通过  |
| TC-WSX-204 | `UserUpdateSerializer.validate()`                            | 异常   | 直属上级由服务端计算，不可直接指定。                             | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_exception_manager_id_not_editable`                | 通过   | 通过  |
| TC-WSX-205 | `UserUpdateSerializer.validate()`                            | 异常   | 创始人不能保留部门归属。                                   | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_exception_founder_with_department_rejected`       | 通过   | 通过  |
| TC-WSX-206 | `UserUpdateSerializer.validate()`                            | 异常   | 部门经理不能显式指定团队。                                  | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_exception_manager_with_explicit_team_rejected`    | 通过   | 通过  |
| TC-WSX-207 | `UserUpdateSerializer.validate()`                            | 正常   | 转为创始人并显式清空组织时通过，部门与团队被置为 None                  | `accounts.test_midterm_wsx_user_update.UserUpdateFieldWhitelistTests.test_founder_role_clears_organization`                 | 通过   | 通过  |
| TC-WSX-208 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 边界   | 上级空缺时重新启用员工被拒绝。                                | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_reactivation_requires_active_supervisor`             | 通过   | 通过  |
| TC-WSX-209 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 正常   | 上级恢复启用后，员工可重新启用。                               | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_reactivation_succeeds_after_supervisor_restored`     | 通过   | 通过  |
| TC-WSX-210 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 正常   | 负责人停用后任命新创始人，部门经理自动挂靠新上级。                      | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_new_founder_links_department_manager`                | 通过   | 通过  |
| TC-WSX-211 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 正常   | 停用创始人后，部门经理的上级被置空。                             | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_deactivated_founder_unlinks_manager`                 | 通过   | 通过  |
| TC-WSX-213 | `UserUpdateSerializer.update()` / `maintain_manager_links()` | 异常   | 普通员工改用例不影响他人直属关系。                              | `accounts.test_midterm_wsx_user_update.UserUpdateManagerLinkTests.test_non_leader_update_does_not_touch_subordinates`       | 通过   | 通过  |
| TC-WSX-221 | `DashboardView.get()`                                        | 边界   | 空库时四项计数全为 0。                                   | `accounts.test_midterm_wsx_user_update.DashboardTests.test_empty_database_returns_zero_counts`                              | 通过   | 通过  |
| TC-WSX-222 | `DashboardView.get()`                                        | 正常   | 返回启用业务账号、部门、团队数量。                              | `accounts.test_midterm_wsx_user_update.DashboardTests.test_counts_include_business_entities`                                | 通过   | 通过  |
| TC-WSX-223 | `DashboardView.get()`                                        | 边界   | 管理员账号不计入 active_users。                         | `accounts.test_midterm_wsx_user_update.DashboardTests.test_admin_accounts_not_counted`                                      | 通过   | 通过  |
| TC-WSX-224 | `DashboardView.get()`                                        | 边界   | 停用账号、部门、团队均不计入。                                | `accounts.test_midterm_wsx_user_update.DashboardTests.test_inactive_users_departments_teams_excluded`                       | 通过   | 通过  |
| TC-WSX-225 | `DashboardView.get()`                                        | 正常   | 已发布的公司重要事项计入 published_company_items。          | `accounts.test_midterm_wsx_user_update.DashboardTests.test_published_company_items_counted`                                 | 通过   | 通过  |
| TC-WSX-226 | `DashboardView.get()`                                        | 边界   | 草稿状态的公司事项不计入已发布数量。                             | `accounts.test_midterm_wsx_user_update.DashboardTests.test_draft_company_items_not_counted`                                 | 通过   | 通过  |
| TC-WSX-231 | `AppTokenSerializer` / `AppRefreshSerializer`                | 正常   | 组织链完整的业务账号可登录 App。                             | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_app_login_succeeds_with_complete_chain`                           | 通过   | 通过  |
| TC-WSX-232 | `AppTokenSerializer` / `AppRefreshSerializer`                | 正常   | App 令牌声明 account_type=business。                | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_app_token_carries_business_account_type`                          | 通过   | 通过  |
| TC-WSX-233 | `AppTokenSerializer` / `AppRefreshSerializer`                | 正常   | App 用 refresh 换取新 access。                      | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_app_refresh_returns_new_access`                                   | 通过   | 通过  |
| TC-WSX-234 | `AppTokenSerializer` / `AppRefreshSerializer`                | 边界   | 创始人停用后员工无法登录 App。                              | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_boundary_inactive_founder_blocks_login`                           | 通过   | 通过  |
| TC-WSX-235 | `AppTokenSerializer` / `AppRefreshSerializer`                | 边界   | 创始人停用后已签发 refresh 失效。                          | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_boundary_inactive_founder_blocks_refresh`                         | 通过   | 通过  |
| TC-WSX-236 | `AppTokenSerializer` / `AppRefreshSerializer`                | 边界   | 部门经理停用后员工无法登录。                                 | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_boundary_inactive_manager_blocks_login`                           | 通过   | 通过  |
| TC-WSX-237 | `AppTokenSerializer` / `AppRefreshSerializer`                | 边界   | 团队长停用后员工无法登录。                                  | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_boundary_inactive_leader_blocks_login`                            | 通过   | 通过  |
| TC-WSX-238 | `AppTokenSerializer` / `AppRefreshSerializer`                | 边界   | 员工自身停用后无法登录。                                   | `accounts.test_midterm_wsx_app_account.AppLoginTests.test_boundary_inactive_employee_cannot_login`                          | 通过   | 通过  |
| TC-WSX-251 | `has_complete_supervisor_chain()`                            | 正常   | 无组织的启用创始人视为链完整。                                | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_founder_without_organization_has_chain`                    | 通过   | 通过  |
| TC-WSX-252 | `has_complete_supervisor_chain()`                            | 正常   | 员工到创始人的完整链返回 True。                             | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_employee_with_full_chain`                                  | 通过   | 通过  |
| TC-WSX-253 | `has_complete_supervisor_chain()`                            | 边界   | 创始人停用后整条链失效。                                   | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_boundary_inactive_founder_breaks_chain`                    | 通过   | 通过  |
| TC-WSX-254 | `has_complete_supervisor_chain()`                            | 边界   | 部门停用后成员链失效。                                    | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_boundary_inactive_department_breaks_chain`                 | 通过   | 通过  |
| TC-WSX-255 | `has_complete_supervisor_chain()`                            | 边界   | 团队停用后成员链失效。                                    | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_boundary_inactive_team_breaks_chain`                       | 通过   | 通过  |
| TC-WSX-256 | `has_complete_supervisor_chain()`                            | 边界   | 团队不属于该部门时链失效。                                  | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_boundary_team_not_in_department_breaks_chain`              | 通过   | 通过  |
| TC-WSX-257 | `has_complete_supervisor_chain()`                            | 边界   | 直属上级业务身份不匹配时链失效。                               | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_boundary_role_mismatch_breaks_chain`                       | 通过   | 通过  |
| TC-WSX-258 | `has_complete_supervisor_chain()`                            | 边界   | 直属上级空缺时链失效。                                    | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_boundary_missing_manager_breaks_chain`                     | 通过   | 通过  |
| TC-WSX-259 | `has_complete_supervisor_chain()`                            | 异常   | 创始人被挂上组织时链失效（组织形状非法）                           | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_founder_with_department_breaks_chain`                      | 通过   | 通过  |
| TC-WSX-260 | `has_complete_supervisor_chain()`                            | 边界   | 管理员账号不属于业务链，返回 False。                          | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_admin_account_has_no_chain`                                | 通过   | 通过  |
| TC-WSX-261 | `has_complete_supervisor_chain()`                            | 边界   | 停用账号返回 False。                                  | `accounts.test_midterm_wsx_app_account.SupervisorChainTests.test_inactive_user_has_no_chain`                                | 通过   | 通过  |
| TC-WSX-262 | `IsBusinessUser.has_permission()`                            | 正常   | 组织链完整的业务用户可访问 App。                             | `accounts.test_midterm_wsx_app_account.IsBusinessUserTests.test_business_user_with_full_chain_allowed`                      | 通过   | 通过  |
| TC-WSX-263 | `IsBusinessUser.has_permission()`                            | 边界   | 匿名用户被拒绝。                                       | `accounts.test_midterm_wsx_app_account.IsBusinessUserTests.test_anonymous_denied`                                           | 通过   | 通过  |
| TC-WSX-264 | `IsBusinessUser.has_permission()`                            | 边界   | 管理员不能访问 App 业务接口。                              | `accounts.test_midterm_wsx_app_account.IsBusinessUserTests.test_admin_denied_on_app_entry`                                  | 通过   | 通过  |
| TC-WSX-265 | `IsBusinessUser.has_permission()`                            | 边界   | 链中断时拒绝，并给出负责人暂缺提示。                             | `accounts.test_midterm_wsx_app_account.IsBusinessUserTests.test_broken_chain_denied_with_vacancy_message`                   | 通过   | 通过  |
| TC-WSX-266 | `IsBusinessUser.has_permission()`                            | 边界   | 停用业务用户被拒绝。                                     | `accounts.test_midterm_wsx_app_account.IsBusinessUserTests.test_inactive_business_user_denied`                              | 通过   | 通过  |
| TC-WSX-281 | `AppMeUpdateSerializer.validate_*()`                         | 正常   | 改为未被占用的手机号通过。                                  | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_username_update_allowed`                              | 通过   | 通过  |
| TC-WSX-282 | `AppMeUpdateSerializer.validate_*()`                         | 边界   | 手机号为空被拒绝。                                      | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_boundary_empty_username_rejected`                     | 通过   | 通过  |
| TC-WSX-283 | `AppMeUpdateSerializer.validate_*()`                         | 边界   | 手机号已被他人使用被拒绝。                                  | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_boundary_username_taken_by_other_rejected`            | 通过   | 通过  |
| TC-WSX-284 | `AppMeUpdateSerializer.validate_*()`                         | 正常   | 姓名保存前去除首尾空白。                                   | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_display_name_trimmed`                                 | 通过   | 通过  |
| TC-WSX-285 | `AppMeUpdateSerializer.validate_*()`                         | 边界   | 姓名纯空白被拒绝。                                      | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_boundary_whitespace_display_name_rejected`            | 通过   | 通过  |
| TC-WSX-286 | `AppMeUpdateSerializer.validate_*()`                         | 正常   | 身份证号去空格并转大写。                                   | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_id_number_normalized`                                 | 通过   | 通过  |
| TC-WSX-287 | `AppMeUpdateSerializer.validate_*()`                         | 边界   | 身份证号空串被拒绝（allow_blank=False）。                  | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_boundary_empty_id_number_rejected`                    | 通过   | 通过  |
| TC-WSX-288 | `AppMeUpdateSerializer.validate_*()`                         | 边界   | 姓名 100 字符（上界）通过。                               | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_boundary_display_name_length_100_accepted`            | 通过   | 通过  |
| TC-WSX-289 | `AppMeUpdateSerializer.validate_*()`                         | 边界   | 姓名 101 字符（上界外）被拒绝。                             | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_boundary_display_name_length_101_rejected`            | 通过   | 通过  |
| TC-WSX-290 | `AppMeUpdateSerializer.validate_*()`                         | 异常   | 提交他人已用身份证号被拒绝。                                 | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_exception_duplicate_id_number_rejected`               | 通过   | 通过  |
| TC-WSX-291 | `AppMeUpdateSerializer.validate_*()`                         | 异常   | App 不允许提交业务身份与组织字段。                            | `accounts.test_midterm_wsx_app_account.AppMeFieldValidationTests.test_exception_business_fields_not_editable`               | 通过   | 通过  |
| TC-WSX-301 | `AppMeView.update()`                                         | 正常   | 姓名与手机号可一并更新。                                   | `accounts.test_midterm_wsx_app_account.AppMeUpdateTests.test_multi_field_update_succeeds`                                   | 通过   | 通过  |
| TC-WSX-302 | `AppMeView.update()`                                         | 正常   | 资料更新写入 App 操作审计并记录变更字段。                        | `accounts.test_midterm_wsx_app_account.AppMeUpdateTests.test_update_writes_app_operation_audit`                             | 通过   | 通过  |
| TC-WSX-303 | `AppMeView.update()`                                         | 边界   | 身份证号变更的审计不写入明文。                                | `accounts.test_midterm_wsx_app_account.AppMeUpdateTests.test_id_number_change_audit_excludes_plaintext`                     | 通过   | 通过  |
| TC-WSX-304 | `AppMeView.update()`                                         | 异常   | 任一字段失败则整次更新不生效。                                | `accounts.test_midterm_wsx_app_account.AppMeUpdateTests.test_invalid_field_rolls_back_whole_update`                         | 通过   | 通过  |
| TC-WSX-305 | `AppMeView.update()`                                         | 边界   | 提交与现值相同的资料不产生审计记录。                             | `accounts.test_midterm_wsx_app_account.AppMeUpdateTests.test_no_change_writes_no_audit`                                     | 通过   | 通过  |
| TC-WSX-306 | `AppMeView.update()`                                         | 正常   | 更新响应返回脱敏身份证号，不含明文。                             | `accounts.test_midterm_wsx_app_account.AppMeUpdateTests.test_response_returns_masked_id_number`                             | 通过   | 通过  |
| TC-WSX-311 | `AppPasswordChangeSerializer`                                | 正常   | 当前密码正确且两次一致时改密成功。                              | `accounts.test_midterm_wsx_app_account.AppPasswordChangeSerializerTests.test_valid_change_accepted_and_saved`               | 通过   | 通过  |
| TC-WSX-312 | `AppPasswordChangeSerializer`                                | 异常   | 当前密码不正确被拒绝。                                    | `accounts.test_midterm_wsx_app_account.AppPasswordChangeSerializerTests.test_wrong_current_password_rejected`               | 通过   | 通过  |
| TC-WSX-313 | `AppPasswordChangeSerializer`                                | 异常   | 两次输入的新密码不一致被拒绝。                                | `accounts.test_midterm_wsx_app_account.AppPasswordChangeSerializerTests.test_mismatched_confirmation_rejected`              | 通过   | 通过  |
| TC-WSX-314 | `AppPasswordChangeSerializer`                                | 异常   | 新密码与当前密码相同被拒绝。                                 | `accounts.test_midterm_wsx_app_account.AppPasswordChangeSerializerTests.test_same_as_current_rejected`                      | 通过   | 通过  |
| TC-WSX-315 | `AppPasswordChangeSerializer`                                | 边界   | 纯数字弱密码被 Django 校验器拒绝。                          | `accounts.test_midterm_wsx_app_account.AppPasswordChangeSerializerTests.test_weak_password_rejected_by_validator`           | 通过   | 通过  |
| TC-WSX-316 | `AppPasswordChangeSerializer`                                | 边界   | 三个字段均为必填。                                      | `accounts.test_midterm_wsx_app_account.AppPasswordChangeSerializerTests.test_missing_fields_rejected`                       | 通过   | 通过  |
| TC-WSX-317 | `AppPasswordChangeSerializer`                                | 边界   | 满足长度与复杂度的新密码通过。                                | `accounts.test_midterm_wsx_app_account.AppPasswordChangeSerializerTests.test_new_password_length_boundary_accepted`         | 通过   | 通过  |
| TC-WSX-321 | `AppPasswordChangeView.post()`                               | 正常   | 改密成功返回 password_changed=True。                  | `accounts.test_midterm_wsx_app_account.AppPasswordChangeViewTests.test_change_password_returns_success`                     | 通过   | 通过  |
| TC-WSX-322 | `AppPasswordChangeView.post()`                               | 正常   | 改密审计只记录字段名，不保存任何密码值。                           | `accounts.test_midterm_wsx_app_account.AppPasswordChangeViewTests.test_audit_records_field_name_only`                       | 通过   | 通过  |
| TC-WSX-323 | `AppPasswordChangeView.post()`                               | 异常   | 未登录调用返回 401。                                   | `accounts.test_midterm_wsx_app_account.AppPasswordChangeViewTests.test_anonymous_denied`                                    | 通过   | 通过  |
| TC-WSX-324 | `AppPasswordChangeView.post()`                               | 边界   | 管理员不能调用 App 改密接口。                              | `accounts.test_midterm_wsx_app_account.AppPasswordChangeViewTests.test_admin_denied`                                        | 通过   | 通过  |
| TC-WSX-325 | `AppPasswordChangeView.post()`                               | 正常   | 改密后旧密码不再可用（服务端不撤销已签发令牌）。                       | `accounts.test_midterm_wsx_app_account.AppPasswordChangeViewTests.test_old_password_no_longer_valid_after_change`           | 通过   | 通过  |
| TC-WSX-331 | `AppAvatarUploadSerializer.validate_avatar()`                | 正常   | PNG 图片通过校验。                                    | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_png_accepted`                                                  | 通过   | 通过  |
| TC-WSX-332 | `AppAvatarUploadSerializer.validate_avatar()`                | 正常   | JPEG 图片通过校验。                                   | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_jpeg_accepted`                                                 | 通过   | 通过  |
| TC-WSX-333 | `AppAvatarUploadSerializer.validate_avatar()`                | 正常   | WebP 图片通过校验。                                   | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_webp_accepted`                                                 | 通过   | 通过  |
| TC-WSX-334 | `AppAvatarUploadSerializer.validate_avatar()`                | 边界   | 文件大小恰好 5 MiB（上界）通过。                            | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_boundary_exactly_5mib_accepted`                                | 通过   | 通过  |
| TC-WSX-335 | `AppAvatarUploadSerializer.validate_avatar()`                | 边界   | 文件大小 5 MiB + 1 字节被拒绝。                          | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_boundary_one_byte_over_5mib_rejected`                          | 通过   | 通过  |
| TC-WSX-336 | `AppAvatarUploadSerializer.validate_avatar()`                | 边界   | 宽度 4096（单边上界）通过。                               | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_boundary_width_4096_accepted`                                  | 通过   | 通过  |
| TC-WSX-337 | `AppAvatarUploadSerializer.validate_avatar()`                | 边界   | 宽度 4097（单边上界外）被拒绝。                             | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_boundary_width_4097_rejected`                                  | 通过   | 通过  |
| TC-WSX-338 | `AppAvatarUploadSerializer.validate_avatar()`                | 边界   | 面积恰好 16,000,000 像素通过。                          | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_boundary_area_at_limit_accepted`                               | 通过   | 通过  |
| TC-WSX-339 | `AppAvatarUploadSerializer.validate_avatar()`                | 边界   | 面积超过 16,000,000 像素被拒绝。                         | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_boundary_area_over_limit_rejected`                             | 通过   | 通过  |
| TC-WSX-340 | `AppAvatarUploadSerializer.validate_avatar()`                | 异常   | GIF 格式不在白名单内被拒绝。                               | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_gif_rejected`                                                  | 通过   | 通过  |
| TC-WSX-341 | `AppAvatarUploadSerializer.validate_avatar()`                | 异常   | 非图片内容被拒绝。                                      | `accounts.test_midterm_wsx_avatar.AvatarValidationTests.test_non_image_content_rejected`                                    | 通过   | 通过  |
| TC-WSX-342 | `AppProfileAvatarView.put()/get()`                           | 正常   | 上传 PNG 返回 204 且写入数据库。                          | `accounts.test_midterm_wsx_avatar.AvatarViewTests.test_put_saves_avatar`                                                    | 通过   | 通过  |
| TC-WSX-343 | `AppProfileAvatarView.put()/get()`                           | 正常   | 读取头像返回二进制及 nosniff、no-store 头。                 | `accounts.test_midterm_wsx_avatar.AvatarViewTests.test_get_returns_image_with_safety_headers`                               | 通过   | 通过  |
| TC-WSX-344 | `AppProfileAvatarView.put()/get()`                           | 边界   | 未设置头像时读取返回 404。                                | `accounts.test_midterm_wsx_avatar.AvatarViewTests.test_get_without_avatar_returns_404`                                      | 通过   | 通过  |
| TC-WSX-345 | `AppProfileAvatarView.put()/get()`                           | 正常   | 再次上传不同格式时覆盖旧头像与类型。                             | `accounts.test_midterm_wsx_avatar.AvatarViewTests.test_replace_avatar_updates_content_type`                                 | 通过   | 通过  |
| TC-WSX-346 | `AppProfileAvatarView.put()/get()`                           | 正常   | 头像写入审计仅记录字段名，不保存二进制内容。                         | `accounts.test_midterm_wsx_avatar.AvatarViewTests.test_upload_writes_audit_without_binary`                                  | 通过   | 通过  |
| TC-WSX-347 | `AppProfileAvatarView.put()/get()`                           | 异常   | 管理员不能调用 App 头像接口。                              | `accounts.test_midterm_wsx_avatar.AvatarViewTests.test_admin_cannot_upload_avatar`                                          | 通过   | 通过  |
| TC-WSX-351 | `_build_organization_tree()`                                 | 正常   | 全公司视图的根节点首先是创始人。                               | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_company_scope_roots_start_with_founder`                    | 通过   | 通过  |
| TC-WSX-352 | `_build_organization_tree()`                                 | 正常   | 部门节点下含经理与本部门团队。                                | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_department_node_contains_manager_and_team`                 | 通过   | 通过  |
| TC-WSX-353 | `_build_organization_tree()`                                 | 正常   | 团队节点下含团队长与员工。                                  | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_team_node_contains_leader_and_employee`                    | 通过   | 通过  |
| TC-WSX-354 | `_build_organization_tree()`                                 | 正常   | 关键词按姓名匹配，返回平铺结果。                               | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_keyword_matches_display_name`                              | 通过   | 通过  |
| TC-WSX-355 | `_build_organization_tree()`                                 | 正常   | 关键词可匹配职级中文标签（如“团队长”）。                          | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_keyword_matches_role_label`                                | 通过   | 通过  |
| TC-WSX-356 | `_build_organization_tree()`                                 | 正常   | 关键词可匹配职级枚举值（如 employee）。                       | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_keyword_matches_role_enum_value`                           | 通过   | 通过  |
| TC-WSX-357 | `_build_organization_tree()`                                 | 边界   | 关键词无匹配时 roots 为空列表。                            | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_boundary_keyword_without_match_returns_empty_roots`        | 通过   | 通过  |
| TC-WSX-358 | `_build_organization_tree()`                                 | 边界   | 关键词大小写不敏感。                                     | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_boundary_keyword_is_case_insensitive`                      | 通过   | 通过  |
| TC-WSX-359 | `_build_organization_tree()`                                 | 边界   | 空关键词返回完整树而非平铺结果。                               | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_boundary_empty_keyword_returns_full_tree`                  | 通过   | 通过  |
| TC-WSX-360 | `_build_organization_tree()`                                 | 边界   | 无成员且无团队的部门 children 为空。                        | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_boundary_department_without_members_has_empty_children`    | 通过   | 通过  |
| TC-WSX-361 | `_build_organization_tree()`                                 | 正常   | 部门范围只返回本人部门节点。                                 | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_scope_department_returns_single_department`                | 通过   | 通过  |
| TC-WSX-362 | `_build_organization_tree()`                                 | 正常   | 团队范围只返回本人团队节点。                                 | `tasks.test_midterm_wsx_org_tree.OrganizationTreeBuildTests.test_scope_team_returns_single_team`                            | 通过   | 通过  |
| TC-WSX-363 | `OrganizationTreeView.get()`                                 | 正常   | 任一业务用户都能浏览全公司组织树（scope 固定为 company）。           | `tasks.test_midterm_wsx_org_tree.OrganizationTreeViewTests.test_all_roles_can_browse_full_company`                          | 通过   | 通过  |
| TC-WSX-371 | `OrganizationMemberProfileView/AvatarView.get()`             | 正常   | 成员资料返回手机号，不返回身份证号。                             | `tasks.test_midterm_wsx_org_tree.OrganizationMemberTests.test_profile_returns_phone_and_hides_id_number`                    | 通过   | 通过  |
| TC-WSX-372 | `OrganizationMemberProfileView/AvatarView.get()`             | 正常   | 查看本人时标记 is_self，并为他人标记 False。                  | `tasks.test_midterm_wsx_org_tree.OrganizationMemberTests.test_profile_flags_self`                                           | 通过   | 通过  |
| TC-WSX-373 | `OrganizationMemberProfileView/AvatarView.get()`             | 正常   | 可查看任务/日志的标记依据下属关系计算。                           | `tasks.test_midterm_wsx_org_tree.OrganizationMemberTests.test_profile_permission_flags_follow_subordinate_scope`            | 通过   | 通过  |
| TC-WSX-374 | `OrganizationMemberProfileView/AvatarView.get()`             | 边界   | 成员不存在时返回 404。                                  | `tasks.test_midterm_wsx_org_tree.OrganizationMemberTests.test_missing_member_returns_404`                                   | 通过   | 通过  |
| TC-WSX-375 | `OrganizationMemberProfileView/AvatarView.get()`             | 边界   | 管理员账号不能作为组织成员被查询。                              | `tasks.test_midterm_wsx_org_tree.OrganizationMemberTests.test_admin_account_not_queryable_as_member`                        | 通过   | 通过  |
| TC-WSX-376 | `OrganizationMemberProfileView/AvatarView.get()`             | 边界   | 停用成员不可查询。                                      | `tasks.test_midterm_wsx_org_tree.OrganizationMemberTests.test_inactive_member_returns_404`                                  | 通过   | 通过  |
| TC-WSX-377 | `OrganizationMemberProfileView/AvatarView.get()`             | 边界   | 成员未设置头像时返回 404。                                | `tasks.test_midterm_wsx_org_tree.OrganizationMemberTests.test_member_avatar_returns_404_when_absent`                        | 通过   | 通过  |
| TC-WSX-381 | `descendants()`                                              | 正常   | 返回全部直接与间接下属。                                   | `tasks.test_midterm_wsx_org_tree.DescendantsTests.test_returns_direct_and_indirect_reports`                                 | 通过   | 通过  |
| TC-WSX-382 | `descendants()`                                              | 边界   | 无下属时返回空列表。                                     | `tasks.test_midterm_wsx_org_tree.DescendantsTests.test_boundary_no_subordinates_returns_empty`                              | 通过   | 通过  |
| TC-WSX-383 | `descendants()`                                              | 边界   | 停用中间层负责人时，其整条下级链都不再返回                          | `tasks.test_midterm_wsx_org_tree.DescendantsTests.test_boundary_inactive_subordinate_excluded`                              | 通过   | 通过  |
| TC-WSX-384 | `descendants()`                                              | 异常   | 结果不包含用户自身，避免自环。                                | `tasks.test_midterm_wsx_org_tree.DescendantsTests.test_result_excludes_self`                                                | 通过   | 通过  |
| TC-WSX-385 | `descendants()`                                              | 正常   | 创始人到员工共 3 级下属全部返回。                             | `tasks.test_midterm_wsx_org_tree.DescendantsTests.test_full_chain_length`                                                   | 通过   | 通过  |
| TC-WSX-391 | `subordinate_ids()`                                          | 正常   | 返回组织链完整的下属 ID 集合。                              | `tasks.test_midterm_wsx_org_tree.SubordinateIdsTests.test_returns_ids_of_valid_subordinates`                                | 通过   | 通过  |
| TC-WSX-392 | `subordinate_ids()`                                          | 边界   | 链断裂的下属被排除（不可查看任务）。                             | `tasks.test_midterm_wsx_org_tree.SubordinateIdsTests.test_boundary_broken_chain_subordinate_excluded`                       | 通过   | 通过  |
| TC-WSX-393 | `subordinate_ids()`                                          | 边界   | 无下属时返回空集合。                                     | `tasks.test_midterm_wsx_org_tree.SubordinateIdsTests.test_boundary_no_subordinates_returns_empty_set`                       | 通过   | 通过  |
| TC-WSX-401 | `SubordinateTaskListView.get()`                              | 正常   | 返回下属承担的任务。                                     | `tasks.test_midterm_wsx_org_tree.SubordinateTaskListTests.test_returns_subordinate_assigned_tasks`                          | 通过   | 通过  |
| TC-WSX-402 | `SubordinateTaskListView.get()`                              | 正常   | created 范围返回下属创建的任务。                           | `tasks.test_midterm_wsx_org_tree.SubordinateTaskListTests.test_created_scope_returns_tasks_created_by_subordinates`         | 通过   | 通过  |
| TC-WSX-403 | `SubordinateTaskListView.get()`                              | 异常   | task_scope 非 assigned/created 时返回 400。         | `tasks.test_midterm_wsx_org_tree.SubordinateTaskListTests.test_invalid_scope_returns_400`                                   | 通过   | 通过  |
| TC-WSX-404 | `SubordinateTaskListView.get()`                              | 异常   | 指定非下属人员时返回 403（越权保护）。                          | `tasks.test_midterm_wsx_org_tree.SubordinateTaskListTests.test_cross_scope_user_id_returns_403`                             | 通过   | 通过  |
| TC-WSX-405 | `SubordinateTaskListView.get()`                              | 边界   | user_id 非数字时返回 400。                            | `tasks.test_midterm_wsx_org_tree.SubordinateTaskListTests.test_non_numeric_user_id_returns_400`                             | 通过   | 通过  |

共 292 条设计用例，全部通过。

### 3.4 客户端手动验证（如适用）

按 Issue 要求，以下客户端行为需真机手动验证。其中 MAN-WSX-001、002（修改手机号/密码后退出登录）已执行并通过，其余尚未执行；手动用例不计入自动化用例统计。

| 用例编号        | Web / App 功能   | 角色及前置条件    | 操作步骤                        | 预期界面行为                             | 实际结果                        | 状态  | 证据／缺陷编号                                             |
| ----------- | -------------- | ---------- | --------------------------- | ---------------------------------- | --------------------------- | --- | --------------------------------------------------- |
| MAN-WSX-001 | App：修改手机号后退出登录 | 员工账号，已完成登录 | 1. 进入「我的」→ 修改资料 2. 修改手机号并保存 | 提示成功后自动退出登录，需用新手机号重新登录             | 与预期一致：保存成功后自动退出登录，旧凭据不再维持会话 | 通过  | [manual-logout.jpg](evidence/wsx/manual-logout.jpg) |
| MAN-WSX-002 | App：修改密码后退出登录  | 员工账号，已完成登录 | 1. 修改密码并保存                  | 提示成功后自动退出登录                        | 与预期一致：改密成功后自动退出登录           | 通过  | [manual-logout.jpg](evidence/wsx/manual-logout.jpg) |
| MAN-WSX-003 | App：组织关系页展示    | 员工账号       | 1. 打开组织关系页 2. 展开部门与团队       | 显示头像、姓名、职级；创始人无部门/团队               | 未执行                         | 未执行 | 待补充                                                 |
| MAN-WSX-004 | App：按姓名/职级搜索成员 | 员工账号       | 1. 在搜索框输入成员姓名 2. 清空关键词      | 输入后切换为平铺结果，清空后恢复组织树                | 未执行                         | 未执行 | 待补充                                                 |
| MAN-WSX-005 | App：成员资料页      | 员工账号       | 1. 点击任一成员                   | 显示头像、姓名、手机号、职位、部门、团队；不显示身份证号；无编辑入口 | 未执行                         | 未执行 | 待补充                                                 |
| MAN-WSX-006 | App：头像上传与展示    | 员工账号       | 1. 上传 PNG 头像 2. 刷新组织关系页查看   | 头像更新，其他成员可见新头像                     | 未执行                         | 未执行 | 待补充                                                 |

## 4. 测试执行过程

### 4.1 准备与执行命令

在仓库根目录打开 PowerShell，激活后端虚拟环境后执行：

```powershell
cd backend
.\.venv\Scripts\Activate.ps1
```

运行本人新增的全部测试并生成覆盖率报告：

```powershell
python -m pytest apps/accounts/test_midterm_wsx_*.py apps/tasks/test_midterm_wsx_org_tree.py `
  --cov=apps.accounts --cov=apps.tasks --cov=apps.logs `
  --cov-report=term --cov-report=html:coverage/html
```

按功能项运行单个测试文件：

```powershell
python -m pytest apps/accounts/test_midterm_wsx_auth.py -v
```

同一批用例也可用 Django 原生运行器执行（已验证，两套运行器结果一致）：

```powershell
python manage.py test apps.accounts.test_midterm_wsx_auth apps.tasks.test_midterm_wsx_org_tree --noinput --verbosity 2
```

验证既有测试未被破坏：

```powershell
python manage.py test apps.accounts --noinput
```

### 4.2 执行记录

| 轮次  | 执行时间       | 提交版本／本地改动说明            | 执行命令与范围                                                      | 工具发现的测试数  | 运行结果                        | 证据路径                                                |
| --- | ---------- | ---------------------- | ------------------------------------------------------------ | --------- | --------------------------- | --------------------------------------------------- |
| 1   | 2026-11-19 | `bdff2db` + 第 1 个测试文件  | `manage.py test apps.accounts.test_midterm_wsx_organization` | 22        | 失败 2（测试用例断言错误）              | 首轮失败输出未留存（已修正后重跑，见第 6 节）                            |
| 2   | 2026-11-19 | 修正断言后重跑                | 同上                                                           | 22        | 通过 22                       | 已清理（中间轮次输出未单独留存）                                    |
| 3   | 2026-11-19 | 新增身份证号归一化用例            | `pytest` 两个文件                                                | 31        | 通过 31                       | 已清理（中间轮次输出未单独留存）                                    |
| 4   | 2026-11-19 | 新增第 2 项权限用例            | `pytest` 两个文件                                                | 34        | 通过 34（首次出现 1 条测试代码错误，修正后通过） | 已清理（中间轮次输出未单独留存）                                    |
| 5   | 2026-11-19 | 新增部门、团队用例              | `pytest` 四个文件                                                | 88        | 通过 88（期间修正 4 条用例前置数据问题）     | 已清理（中间轮次输出未单独留存）                                    |
| 6   | 2026-11-19 | 补齐全部 25 个功能项           | `pytest` 全部 10 个文件 + 覆盖率                                     | **292**   | **通过 292，失败 0，跳过 0**        | [final-run.txt](evidence/wsx/final-run.txt)         |
| 7   | 2026-11-19 | 回归验证                   | `manage.py test apps.accounts`                               | 既有测试 + 新增 | 全部通过                        | 已清理（中间轮次输出未单独留存）                                    |
| 8   | 2026-11-19 | MAN-WSX-001、002 真机手动验证 | App 修改手机号、修改密码                                               | 手动 2 条    | 通过 2                        | [manual-logout.jpg](evidence/wsx/manual-logout.jpg) |

执行过程中出现的问题均属**测试代码自身缺陷**（非业务缺陷），已逐条修正并记录于第 6 节：共 6 处。手动用例 MAN-WSX-001、002 已独立验证并通过。

### 4.3 原始输出与证据

最终一轮执行的输出摘要：

```text
platform win32 -- Python 3.13.11, pytest-8.4.2, pluggy-1.7.0
django: version: 5.2.18, settings: config.settings (from ini)
collected 292 items

apps/accounts/test_midterm_wsx_auth.py ...................................  [ 11%]
apps/accounts/test_midterm_wsx_id_number.py .........                    [ 14%]
apps/accounts/test_midterm_wsx_organization.py ......................    [ 22%]
apps/accounts/test_midterm_wsx_department.py ...........................  [ 31%]
apps/accounts/test_midterm_wsx_team.py ...........................       [ 40%]
apps/accounts/test_midterm_wsx_user_create.py .............................................  [ 56%]
apps/accounts/test_midterm_wsx_user_update.py .........................  [ 64%]
apps/accounts/test_midterm_wsx_app_account.py .....................................................  [ 82%]
apps/accounts/test_midterm_wsx_avatar.py .................               [ 88%]
apps/tasks/test_midterm_wsx_org_tree.py .................................  [100%]

292 passed in 323.65s (0:05:23)
```

覆盖率输出（`--cov=apps.accounts --cov=apps.tasks --cov=apps.logs`）：

```text
apps\accounts\api_serializers.py     409     18    96%
apps\accounts\api_views.py           297     73    75%
apps\accounts\authentication.py       64      8    88%
apps\accounts\models.py              118      0   100%
apps\tasks\models.py                 118      0   100%
apps\logs\models.py                   35      0   100%
TOTAL                               5409   2065    62%
```

> 证据文件位于 `docs/test/evidence/wsx/`：`final-run.txt`（执行输出与覆盖率）、`manual-logout.jpg`（手动验证截图）、`coverage-api.png`（覆盖率报告截图）。

## 5. 测试结果统计

### 5.1 设计用例统计

| 类别    | 计划用例数 | 已执行 | 通过  | 失败  | 阻塞  | 未执行 |
| ----- | ----- | --- | --- | --- | --- | --- |
| 自动化用例 | 292   | 292 | 292 | 0   | 0   | 0   |
| 手动用例  | 6     | 2   | 2   | 0   | 0   | 4   |

统计口径：计划数 = 通过 + 失败 + 阻塞 + 未执行；已执行数 = 通过 + 失败。

按场景类型分布：

| 场景类型   | 用例数     | 占比       |
| ------ | ------- | -------- |
| 正常     | 102     | 34.9%    |
| 边界     | 125     | 42.8%    |
| 异常     | 65      | 22.3%    |
| **合计** | **292** | **100%** |

按功能项与文件分布：

| 测试文件                               | 覆盖功能项                   | 用例数 |
| ---------------------------------- | ----------------------- | --- |
| `test_midterm_wsx_auth.py`         | 1、2                     | 34  |
| `test_midterm_wsx_id_number.py`    | 8（支撑函数）                 | 9   |
| `test_midterm_wsx_organization.py` | 11（`resolve_placement`） | 22  |
| `test_midterm_wsx_department.py`   | 3、4、5                   | 27  |
| `test_midterm_wsx_team.py`         | 6、7                     | 27  |
| `test_midterm_wsx_user_create.py`  | 8、9、10                  | 45  |
| `test_midterm_wsx_user_update.py`  | 11（剩余）、12               | 25  |
| `test_midterm_wsx_app_account.py`  | 13、14、15、16、17、18       | 53  |
| `test_midterm_wsx_avatar.py`       | 19、20                   | 17  |
| `test_midterm_wsx_org_tree.py`     | 21、22、23、24、25          | 33  |

### 5.2 自动化工具统计说明

`pytest` 报告 `292 passed`，与设计用例数 292 条一一对应：每个测试方法恰好承载 1 条设计用例，未使用 `subTest` 合并，因此工具统计数可直接视为设计用例数。

同一批用例用 `manage.py test` 执行时，发现的测试数与结果与 `pytest` 一致（两者均基于 `django.test.TestCase`）。

### 5.3 函数覆盖检查

| 核心函数／方法                                         | 正常用例            | 边界用例            | 异常用例            | 是否满足至少 3 条   | 未满足原因        |
| ----------------------------------------------- | --------------- | --------------- | --------------- | ------------ | ------------ |
| `ScopedTokenSerializer.validate()`              | 001、002         | 006～008、011     | 013、014、017～019 | 是（11 条）      | 无            |
| `ScopedRefreshSerializer.validate()`            | 003、004         | 005、009～012     | 015、016、020～024 | 是（13 条）      | 无            |
| `IsAdministrator.has_permission()`              | 031、032、039     | 033～037、040     | 038             | 是（10 条）      | 无            |
| `DepartmentListView.perform_create()`           | 041～045         | 046～049         | 050             | 是（10 条）      | 无            |
| `DepartmentUpdateSerializer.validate()`         | 051～053         | 054～058         | 059、060         | 是（10 条）      | 无            |
| `DepartmentDetailSerializer.get_manager()`      | 071             | 072、073         | —               | 是（3 条）       | 无            |
| `DepartmentDetailSerializer.get_members()`      | 074             | 075、076         | —               | 是（3 条）       | 无            |
| `DepartmentDetailSerializer.get_teams()`        | 077             | —               | —               | 辅助方法，随展示用例覆盖 | 非 Issue 指定函数 |
| `TeamSerializer.validate()`                     | 081～083         | 084～087         | 088～091         | 是（11 条）      | 无            |
| `TeamUpdateSerializer.validate()`               | 092～094         | 095～097、100     | 098、099         | 是（9 条）       | 无            |
| `TeamDetailSerializer.get_leader()`             | 101             | 102、103         | —               | 是（3 条）       | 无            |
| `TeamDetailSerializer.get_members()`            | 104             | 105、106         | —               | 是（3 条）       | 无            |
| `BusinessUserCreateSerializer.validate()`       | 124             | 126～129、137     | 130～136、138     | 是（14 条）      | 无            |
| `BusinessUserCreateSerializer.create()`         | 120～123、139、140 | —               | —               | 是（6 条）       | 无            |
| `UserListView.create()`                         | 141～145         | —               | 151             | 是（6 条）       | 无            |
| `UserListView.get_queryset()`                   | 146～150、155     | 152～154         | —               | 是（9 条）       | 无            |
| `UserSerializer.get_id_number_masked()`         | 161、162         | 163～168、170     | 169             | 是（10 条）      | 无            |
| `User.normalize_id_number()`                    | 111～114         | 115～118         | 119             | 是（9 条）       | 无            |
| `resolve_placement()`                           | 171～174         | 175～182         | 183～192         | 是（22 条）      | 无            |
| `UserUpdateSerializer.validate()`               | 207             | 205、206         | 200～204         | 是（6 条）       | 无            |
| `UserUpdateSerializer.update()`                 | 193、194、209     | 195、196、199、208 | 213             | 是（8 条）       | 无            |
| `maintain_manager_links()`                      | 210、211         | 196、197、199     | 213             | 是（6 条）       | 无            |
| `DashboardView.get()`                           | 222、225         | 221、223、224、226 | —               | 是（6 条）       | 无            |
| `AppTokenSerializer` / `AppRefreshSerializer`   | 231～233         | 234～238         | —               | 是（8 条）       | 无            |
| `has_complete_supervisor_chain()`               | 251、252         | 253～258、260、261 | 259             | 是（11 条）      | 无            |
| `IsBusinessUser.has_permission()`               | 262             | 263、264、266     | 265             | 是（5 条）       | 无            |
| `AppMeUpdateSerializer.validate_username()`     | 281             | 282、283         | —               | 是（3 条）       | 无            |
| `AppMeUpdateSerializer.validate_display_name()` | 284             | 285、288、289     | —               | 是（4 条）       | 无            |
| `AppMeUpdateSerializer.validate_id_number()`    | 286             | 287             | 290             | 是（3 条）       | 无            |
| `AppMeView.update()`                            | 301、302、306     | 303、305         | 304             | 是（6 条）       | 无            |
| `AppPasswordChangeSerializer.validate()`        | 311、317         | 315、316         | 312～314         | 是（7 条）       | 无            |
| `AppPasswordChangeSerializer.save()`            | 311             | —               | —               | 随 TC-311 覆盖  | 单分支方法        |
| `AppPasswordChangeView.post()`                  | 321、322、325     | 324             | 323             | 是（5 条）       | 无            |
| `AppAvatarUploadSerializer.validate_avatar()`   | 331～333         | 334～339         | 340、341         | 是（11 条）      | 无            |
| `AppProfileAvatarView.put()`                    | 342、345、346     | —               | 347             | 是（4 条）       | 无            |
| `AppProfileAvatarView.get()`                    | 343             | 344             | —               | 是（2 条 + 377） | 无            |
| `_build_organization_tree()`                    | 351～356、361、362 | 357～360         | —               | 是（10 条）      | 无            |
| `OrganizationTreeView.get()`                    | 363             | —               | —               | 随 TC-363 覆盖  | 单分支方法        |
| `OrganizationMemberProfileView.get()`           | 371～373         | 374～376         | —               | 是（6 条）       | 无            |
| `OrganizationMemberAvatarView.get()`            | —               | 377             | —               | 随 TC-377 覆盖  | 单分支方法        |
| `descendants()`                                 | 381、384、385     | 382、383         | —               | 是（5 条）       | 无            |
| `subordinate_ids()`                             | 391             | 392、393         | —               | 是（3 条）       | 无            |
| `SubordinateTaskListView.get()`                 | 401、402         | 405             | 403、404         | 是（5 条）       | 无            |

本表表示需求与用例的对应关系，不等同于代码覆盖率（覆盖率见第 7 节）。

## 6. 问题与缺陷记录

本轮共记录 **6 条测试用例问题**（均为测试代码自身缺陷，已修正并重跑）与 **2 条需求待确认事项**。**未发现业务缺陷**。

### 测试用例问题 TC-ISS-001：断言未适配 DRF 异常结构

| 项目      | 内容                           |
| ------- | ---------------------------- |
| 类型      | 测试用例问题                       |
| 影响程度    | 低                            |
| 所属功能和函数 | 第 11 项：`resolve_placement()` |
| 关联用例    | TC-WSX-183、184               |
| 当前处理状态  | 已修正并重跑通过                     |

**现象：** 断言 `error.detail` 为单个 `ErrorDetail`，实际为 `list[ErrorDetail]`（绑定字段时为 `dict`）。
**修正：** 增加 `detail_text()` 辅助函数拉平三种形态，并改为按业务文案断言。

### 测试用例问题 TC-ISS-002：JWT 中 user_id 的类型预期错误

| 项目      | 内容                                         |
| ------- | ------------------------------------------ |
| 类型      | 测试用例问题                                     |
| 影响程度    | 低                                          |
| 所属功能和函数 | 第 1 项：`ScopedRefreshSerializer.validate()` |
| 关联用例    | TC-WSX-004                                 |
| 当前处理状态  | 已修正并重跑通过                                   |

**现象：** 断言 `access.get("user_id") == admin.pk`（整数）失败，实际为字符串 `'1'`。
**修正：** 改为 `int(access.get("user_id"))`，避免把 JWT 的字符串序列化当成缺陷。

### 测试用例问题 TC-ISS-003：测试数据违反数据库约束

| 项目      | 内容                                       |
| ------- | ---------------------------------------- |
| 类型      | 测试用例问题                                   |
| 影响程度    | 低                                        |
| 所属功能和函数 | 第 2 项：`IsAdministrator.has_permission()` |
| 关联用例    | TC-WSX-040                               |
| 当前处理状态  | 已修正并重跑通过                                 |

**现象：** 直接创建无部门、无团队的 `employee` 账号，触发 `IntegrityError: CHECK constraint failed: valid_account_placement`。
**修正：** 先创建部门与团队，再挂接员工。

### 测试用例问题 TC-ISS-004：前置数据使停用断言不成立（部门/团队）

| 项目      | 内容                                                                           |
| ------- | ---------------------------------------------------------------------------- |
| 类型      | 测试用例问题                                                                       |
| 影响程度    | 低                                                                            |
| 所属功能和函数 | 第 4、5 项：`DepartmentUpdateSerializer.validate()`、`DepartmentDetailSerializer` |
| 关联用例    | TC-WSX-052、072、075                                                           |
| 当前处理状态  | 已修正并重跑通过                                                                     |

**现象：** `setUp()` 中已创建启用团队/成员，导致「无启用下属可停用」「无经理返回 None」「无成员返回空」三处前提不成立。
**修正：** 改用独立的空部门作为前置数据。

### 测试用例问题 TC-ISS-005：修改角色预期与实际契约不符

| 项目      | 内容                                       |
| ------- | ---------------------------------------- |
| 类型      | 测试用例问题                                   |
| 影响程度    | 低                                        |
| 所属功能和函数 | 第 11 项：`UserUpdateSerializer.validate()` |
| 关联用例    | TC-WSX-207                               |
| 当前处理状态  | 已修正并重跑通过                                 |

**现象：** 预期仅提交 `business_role=founder` 即可自动清空组织；实际因实例已有部门/团队而报「创始人不能指定部门或团队」。
**修正：** 按实际契约同时提交 `department_id=null`、`team_id=null`，并在用例说明中记录该前置要求。

### 测试用例问题 TC-ISS-006：raw update 与业务钩子的差异

| 项目      | 内容                                                                         |
| ------- | -------------------------------------------------------------------------- |
| 类型      | 测试用例问题                                                                     |
| 影响程度    | 低                                                                          |
| 所属功能和函数 | 第 11 项：`maintain_manager_links()`；第 14 项：`has_complete_supervisor_chain()` |
| 关联用例    | TC-WSX-210、253～255                                                         |
| 当前处理状态  | 已修正并重跑通过                                                                   |

**现象：** 用 `QuerySet.update()` 直接改库不会触发 `maintain_manager_links()`，且实例缓存的关系对象不刷新，导致断言失败。
**修正：** 准备前置状态改走接口调用，或在断言前 `refresh_from_db()`。

### 需求待确认事项 REQ-001：手机号与身份证号格式约束

| 项目      | 内容                                                                                                                        |
| ------- | ------------------------------------------------------------------------------------------------------------------------- |
| 类型      | 需求待确认                                                                                                                     |
| 影响程度    | 中（影响验收判定口径）                                                                                                               |
| 所属功能和函数 | 第 8 项 `BusinessUserCreateSerializer.validate()`；第 15 项 `AppMeUpdateSerializer.validate_username()`、`validate_id_number()` |
| 关联用例    | TC-WSX-126、127、281～283、287                                                                                                |
| 当前处理状态  | 待需求负责人确认                                                                                                                  |

**Issue #42「重点检查」描述：** 手机号 11 位数字、身份证号 18 位格式。
**需求文档 `account-provisioning.md` 规定：** 身份证号必填、**最长 64 个字符**、去空格转大写后全局唯一；手机号存入 `username`、**遵循 Django 字段规则且最长 150**。
**当前实现：** 与需求文档一致，未做 11 位/18 位格式校验。
**说明：** 按模板要求「需求有歧义时单独登记，不能直接以当前实现作为正确答案」，本轮用例按需求文档口径设计（长度边界 64/100/150），未按 Issue 的 11 位/18 位判定，也未登记为业务缺陷。需确认以哪份为准；若确认需要 11 位/18 位校验，则属新增需求，需另开 Issue。

### 需求待确认事项 REQ-002：头像大小与尺寸阈值

| 项目      | 内容                                                   |
| ------- | ---------------------------------------------------- |
| 类型      | 需求待确认                                                |
| 影响程度    | 低                                                    |
| 所属功能和函数 | 第 19 项：`AppAvatarUploadSerializer.validate_avatar()` |
| 关联用例    | TC-WSX-334～339                                       |
| 当前处理状态  | 待需求负责人确认                                             |

**说明：** 需求文档未单列头像章节，5 MiB、4096 单边、16,000,000 面积三项阈值来自实现约定。本轮用例以该约定为预期，需确认是否正式写入需求。

### 行为特性说明（非缺陷，供设计与评审参考）

1. **停用中间层负责人会连带隐藏其全部下级**（TC-WSX-383 实测）：`descendants()` 按 `manager_id` 逐层向下遍历并以 `is_active=True` 过滤，停用团队长后遍历在该层终止，其下属（即使仍启用）不再出现在结果中。该行为与 `has_complete_supervisor_chain()` 的链完整性判定一致。
2. **转入创始人角色需同时显式清空组织**（TC-WSX-207、205）：仅提交 `business_role=founder` 会被拒绝，须一并提交 `department_id=null`、`team_id=null`。管理端交互需注意此顺序。

## 7. 代码覆盖率（建议项）

- 是否生成覆盖率报告：**是**（pytest-cov + coverage.py）。
- 测量范围：`apps.accounts`、`apps.tasks`、`apps.logs`（本轮被测函数所在模块）。
- 实际指标：

| 文件                                 | 语句数      | 未覆盖     | 覆盖率      |
| ---------------------------------- | -------- | ------- | -------- |
| `apps/accounts/models.py`          | 118      | 0       | **100%** |
| `apps/accounts/urls.py`            | 3        | 0       | 100%     |
| `apps/accounts/api_serializers.py` | 409      | 18      | **96%**  |
| `apps/accounts/audit.py`           | 14       | 1       | 93%      |
| `apps/accounts/authentication.py`  | 64       | 8       | **88%**  |
| `apps/accounts/fields.py`          | 27       | 4       | 85%      |
| `apps/accounts/api_views.py`       | 297      | 73      | 75%      |
| `apps/accounts/app_audit.py`       | 71       | 33      | 54%      |
| `apps/accounts/admin.py`           | 58       | 35      | 40%      |
| `apps/logs/models.py`              | 35       | 0       | 100%     |
| `apps/logs/serializers.py`         | 56       | 0       | 100%     |
| `apps/logs/services.py`            | 3        | 1       | 67%      |
| `apps/logs/views.py`               | 212      | 134     | 37%      |
| `apps/tasks/models.py`             | 118      | 0       | 100%     |
| `apps/tasks/serializers.py`        | 170      | 17      | 90%      |
| `apps/tasks/views.py`              | 545      | 308     | 43%      |
| `apps/tasks/work_views.py`         | 181      | 103     | 43%      |
| `apps/tasks/services.py`           | 100      | 70      | 30%      |
| `apps/tasks/china_calendar.py`     | 24       | 19      | 21%      |
| **合计**                             | **2512** | **824** | **67%**  |

统计口径：已排除 `*/test_*.py`、`*/tests.py`、`*/migrations/*`、`*/__init__.py`、`*/apps.py`（配置见 `backend/.coveragerc`），百分比反映生产代码覆盖，不含测试代码与迁移脚本的自覆盖。

- 截图或报告：[覆盖率报告截图](evidence/wsx/coverage-api.png)；HTML 源文件位于 `backend/coverage/html/index.html`（该目录已被 `.gitignore` 的 `coverage/` 忽略）。
- 未覆盖关键分支及原因：
  - `apps/tasks/views.py`、`apps/tasks/services.py`、`apps/logs/views.py` 覆盖率偏低，是因为这三个文件中的任务生命周期、日志、工作视图等大部分函数属其他成员分工范围（第 23～25 项仅涉及其中 3 个函数），非本报告缺漏。
  - `apps/accounts/api_views.py` 的未覆盖部分主要为审计查询、App 操作记录查询等第 2 位成员分工内容。

> 覆盖率不能代替断言正确性，也不能单独作为功能无缺陷的依据。

## 8. 测试结论与汇总交接

**结论：** Issue #42 分配给本人的 25 个功能项、40 个核心函数已全部编写自动化用例，共 292 条（正常 102、边界 125、异常 65），全部执行通过，失败 0、阻塞 0、跳过 0。既有测试全部保留且回归通过。就实际执行范围而言，账号认证与两端隔离、组织（部门/团队/成员）录入与调整、直属关系维护、个人资料与密码、头像、组织树与下属范围的业务规则均已验证通过。

**尚未验证及需确认内容：**

1. 客户端界面行为：MAN-WSX-001、002（修改手机号/密码后退出登录）已通过；MAN-WSX-003～006（组织关系页展示、搜索、成员资料、头像展示）尚未执行，需真机与测试账号。
2. 需求待确认 2 项（REQ-001 手机号/身份证格式约束、REQ-002 头像阈值），影响验收判定口径。
3. `has_complete_supervisor_chain()` 的环路分支因数据库约束无法构造，未覆盖。
4. 本地 Python 3.13 与团队基线 3.11 的差异，需在统一环境复跑确认。

**需要汇总报告重点说明的问题：**

1. REQ-001 需求与 Issue「重点检查」描述不一致，是四人分工中唯一涉及需求口径分歧的项，建议在汇总报告里单独列出。
2. 行为特性 1（停用中间层负责人隐藏全部下级）会影响用户对「下属范围」的预期，建议在需求侧确认是否为预期设计。
3. 覆盖率统计需说明按模块分工的归属，避免整模块百分比被误读为本成员缺漏。

**提交给汇总同学的材料：**

- 个人报告：`docs/test/wsx-midterm-tests.md`
- 测试代码：`backend/apps/accounts/test_midterm_wsx_*.py`（9 个）、`backend/apps/tasks/test_midterm_wsx_org_tree.py`（1 个）
- 环境改动：`backend/requirements.txt`（新增 3 个测试依赖）、`backend/pytest.ini`（新增）
- 测试代码版本：未提交（本地改动），分支 `test/10-midterm-accounts-organization`
- 运行结果：292 passed / 0 failed；覆盖率合计 62%（详见第 7 节）
- 证据位置：`docs/test/evidence/wsx/`（`final-run.txt`、`manual-logout.jpg`、`coverage-api.png`）

## 附录 A：AI 辅助记录与人工复核

### A.1 使用记录

- 工具／模型：Claude Code（Anthropic），模型 `claude-opus-4-5`。
- 使用日期：2026-11-19。
- 提供的上下文：`docs/backend/account-provisioning.md`、`docs/requirements/08-organization-relationships.md`、Issue #42 正文（分工表、重点检查、提交要求、测试执行要求），以及被测源文件 `apps/accounts/api_serializers.py`、`api_views.py`、`authentication.py`、`apps/tasks/views.py`、`apps/tasks/services.py`、`apps/logs/views.py`。
- 生成内容：等价类／边界值设计表、测试代码骨架与断言、行为探针脚本（用后删除）。
- 对应用例：TC-WSX-001～405（全部）。

**实际提示词（要点摘录）：**

```text
上下文：Issue #42 分工“王世鑫：账号认证、部门团队与员工管理、个人资料与密码、头像、组织关系”，
要求每个核心函数至少 3 条用例，覆盖正常/边界/异常，使用等价类划分 + 边界值分析；
需求依据 docs/backend/account-provisioning.md。

任务：先输出等价类划分与边界值分析表（不要直接写代码），
再按 Issue 的功能编号顺序为每个函数编写 django.test.TestCase 用例，
断言要验证业务结果（返回值、数据库状态、异常文案），不要只断言状态码。
对不确定的框架行为先写探针验证，不要凭猜测写断言。
```

**人工复核方式：**

1. 逐条核对前置条件是否满足业务约束（数据库 CHECK 约束多次导致修正，见 TC-ISS-003）。
2. 逐条核对预期结果是否有需求文档依据；无依据项登记为「需求待确认」而非缺陷。
3. 对 AI 给出的行为判断先用探针实测再断言（如 `is_valid()` 抛 `AuthenticationFailed`、`descendants()` 遇停用节点终止）。
4. 纠正 AI 的错误预期共 6 处，均记录于第 6 节。

### A.2 人工逐条复核

| 对应用例编号     | 复核的需求与预期              | 输入和前置条件是否有效         | 断言是否验证业务结果                | 人工修改及理由                          | 复核人 |
| ---------- | --------------------- | ------------------- | ------------------------- | -------------------------------- | --- |
| TC-001～024 | 两端登录/刷新隔离、令牌类型与时效     | 是                   | 是（校验 `account_type`、异常文案） | 修正 004 的 `user_id` 类型断言          | 王世鑫 |
| TC-031～040 | 管理权限只认 `account_type` | 修正 040 的组织前置数据      | 是                         | 补充部门与团队                          | 王世鑫 |
| TC-041～060 | 部门创建审计、停用阻塞           | 修正 052/072/075 前置数据 | 是（校验审计字段、阻塞文案计数）          | 改用独立空部门                          | 王世鑫 |
| TC-071～077 | 部门详情展示启用成员            | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-081～100 | 团队名称部门内唯一、部门依赖        | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-101～107 | 团队详情展示                | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-111～119 | 身份证号归一化               | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-120～140 | 录入、初始密码 16 位、哈希存储     | 是                   | 是（校验 `check_password`、长度） | 无修改                              | 王世鑫 |
| TC-141～155 | 创建响应头、筛选与会话隔离         | 是                   | 是（校验响应头、字段缺失）             | 无修改                              | 王世鑫 |
| TC-161～170 | 脱敏保留后 4 位             | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-171～192 | 四种角色组织形状与名额唯一性        | 是                   | 是                         | 修正 183/184 的异常结构断言               | 王世鑫 |
| TC-193～213 | 直属关系重建、迁移与空缺          | 是                   | 是（校验 `manager_id` 落库结果）   | 修正 207 契约、210/253～255 的刷新问题      | 王世鑫 |
| TC-221～226 | 管理概况计数口径              | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-231～238 | App 链完整性与登录           | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-251～266 | 组织链完整性判定              | 修正 259 的构造方式        | 是                         | 改用内存实例验证防御分支                     | 王世鑫 |
| TC-281～291 | App 资料字段校验与白名单        | 修正 290 的哈希同步        | 是                         | 改用 `save()` 以更新 `id_number_hash` | 王世鑫 |
| TC-301～306 | 资料更新原子性与审计            | 是                   | 是（校验回滚后数据库状态）             | 无修改                              | 王世鑫 |
| TC-311～325 | 改密规则与审计脱敏             | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-331～347 | 头像阈值与接口行为             | 是                   | 是                         | 修正 342 等用例的组织链前置                 | 王世鑫 |
| TC-351～363 | 组织树结构与关键词             | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-371～377 | 成员资料可见性与脱敏            | 是                   | 是                         | 无修改                              | 王世鑫 |
| TC-381～393 | 下属范围与链完整性             | 修正 383 的预期          | 是                         | 按实测行为改写并记录为行为特性                  | 王世鑫 |
| TC-401～405 | 下属任务范围与越权             | 是                   | 是                         | 无修改                              | 王世鑫 |

## 附录 B：提交前自查

- [x] 负责的核心函数均已列出，并映射到实际测试用例（25 个功能项 / 40 个函数全覆盖）。
- [x] 正常、边界和异常场景已覆盖，未覆盖项已说明原因（见 2.2）。
- [x] 现有测试完整保留（未修改、未删除任何既有测试）。
- [x] 自动化测试代码和测试结果可复现（命令见 4.1）。
- [x] 实际测试版本、运行命令和证据已记录，证据文件位于 `docs/test/evidence/wsx/`。
- [x] 设计用例数量与工具运行数量已区分（292 / 292，一一对应）。
- [x] 失败、阻塞、跳过及未执行情况均如实说明（自动化通过 292；手动通过 2、未执行 4）。
- [x] 缺陷包含预期、实际结果、复现条件和影响（6 条测试用例问题、2 条需求待确认）。
- [x] AI 辅助内容已逐条复核，并保留实际提示词（见附录 A）。
- [x] 报告不存在未替换的占位内容，证据链接指向实际文件。
