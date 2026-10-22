package com.example.pandorademo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pandorademo.ui.theme.PandoraDemoTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val Canvas = Color(0xFFF6F7F9)
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF172331)
private val Muted = Color(0xFF778393)
private val Line = Color(0xFFE8ECF0)
private val Amber = Color(0xFFF2A51A)
private val AmberPale = Color(0xFFFFF3D6)
private val Blue = Color(0xFF438FE8)
private val BluePale = Color(0xFFEAF3FF)
private val Teal = Color(0xFF20B99A)
private val TealPale = Color(0xFFE7F8F3)
private val Violet = Color(0xFF8A6BE8)
private val VioletPale = Color(0xFFF1EDFF)
private val Rose = Color(0xFFE77778)
private val RosePale = Color(0xFFFFEEEE)

private enum class RootTab(val title: String, val symbol: String) {
    HOME("首页", "⌂"),
    VIEW("视图", "▦"),
    LOG("日志", "▤"),
    AI("AI地图", "✧"),
    MINE("我的", "○")
}

private enum class ChildPage { TASKS, LOG_EDITOR, COMPANY_MATTERS, COMPANY_TASKS, PERSONAL_FOCUS, NOTIFICATIONS }
private enum class ViewMode(val title: String) { MONTH("月"), WEEK("周"), DAY("日") }
private data class WorkViewUiState(
    val mode: ViewMode = ViewMode.MONTH,
    val weekShift: Int = 0,
    val dayOffset: Int = 0,
    val monthShift: Int = 0,
    val unfinishedExpanded: Boolean = true
)

private const val DemoBaseYear = 2026
private const val DemoBaseMonth = Calendar.OCTOBER

private data class DemoTask(val title: String, val source: String, val status: String, val deadline: String, val color: Color)
private enum class WorkEventKind(val label: String, val accent: Color, val tint: Color) {
    UPDATE("任务更新", Blue, BluePale),
    SUBMISSION("完成提交", Amber, AmberPale),
    ACCEPTANCE("验收完成", Teal, TealPale),
    SUBMIT_AND_COMPLETE("提交并完成", Violet, VioletPale)
}
private data class DemoWorkEvent(
    val dayOffset: Int,
    val time: String,
    val task: DemoTask,
    val kind: WorkEventKind,
    val summary: String
)
private data class UnfinishedViewTask(val task: DemoTask, val weekEndColumn: Int)
private data class DemoMatter(val title: String, val context: String, val detail: String)
private data class DemoLogSummary(val title: String, val date: String)
private enum class DemoRole(val label: String, val actor: String, val unit: String) {
    FOUNDER("创始人", "周总", "公司"),
    DEPARTMENT_MANAGER("部门经理", "林经理", "产品部"),
    TEAM_LEADER("团队长", "周晓", "产品一组"),
    EMPLOYEE("普通员工", "陈宁", "产品一组")
}

private data class DemoPerson(val name: String, val role: DemoRole, val unit: String, val parentUnit: String?) {
    val optionLabel: String get() = "$name · ${role.label} · $unit"
}

private val demoPeople = listOf(
    DemoPerson("周总", DemoRole.FOUNDER, "公司", null),
    DemoPerson("林经理", DemoRole.DEPARTMENT_MANAGER, "产品部", "公司"),
    DemoPerson("赵经理", DemoRole.DEPARTMENT_MANAGER, "研发部", "公司"),
    DemoPerson("周晓", DemoRole.TEAM_LEADER, "产品一组", "产品部"),
    DemoPerson("王磊", DemoRole.TEAM_LEADER, "产品二组", "产品部"),
    DemoPerson("孙怡", DemoRole.TEAM_LEADER, "研发一组", "研发部"),
    DemoPerson("陈宁", DemoRole.EMPLOYEE, "产品一组", "产品一组"),
    DemoPerson("李敏", DemoRole.EMPLOYEE, "产品一组", "产品一组"),
    DemoPerson("赵薇", DemoRole.EMPLOYEE, "产品二组", "产品二组"),
    DemoPerson("郑凯", DemoRole.EMPLOYEE, "研发一组", "研发一组")
)

private enum class WorkflowStatus(val label: String) {
    UNFINISHED("未完成"),
    COMPLETED("已完成")
}

private enum class ApprovalStatus(val label: String) {
    NOT_REQUIRED("免审批"),
    PENDING("待审批"),
    RETURNED("已退回"),
    APPROVED("已通过")
}

private enum class WorkflowOrigin {
    UPPER,
    INDEPENDENT
}

private data class WorkflowProgress(val percent: Int, val note: String, val date: String)
private data class WorkflowTextUpdate(val note: String, val author: String, val date: String)
private data class WorkflowTask(
    val id: Int,
    val parentId: Int?,
    val title: String,
    val description: String,
    val acceptanceCriteria: String,
    val senderRole: DemoRole,
    val senderName: String,
    val assigneeRole: DemoRole,
    val assigneeName: String,
    val deadline: String,
    val origin: WorkflowOrigin,
    val status: WorkflowStatus,
    val started: Boolean = false,
    val awaitingAcceptance: Boolean = false,
    val progress: Int = 0,
    val updates: List<WorkflowProgress> = emptyList(),
    val completionNote: String = "",
    val resultLocation: String = "",
    val reviewNote: String = "",
    val approvalStatus: ApprovalStatus = ApprovalStatus.APPROVED,
    val approverRole: DemoRole? = null,
    val approverName: String = "",
    val approvalNote: String = "",
    val expectedDeliverables: String = "",
    val associationNote: String = "",
    val textUpdates: List<WorkflowTextUpdate> = emptyList(),
    val cancelled: Boolean = false,
    val cancellationNote: String = ""
)

private enum class TaskNoticeKind(val title: String, val accent: Color, val tint: Color) {
    APPROVAL("待你审批", Violet, VioletPale),
    ASSIGNMENT("新任务已分配", Blue, BluePale)
}

private data class TaskInboxNotice(
    val key: String,
    val kind: TaskNoticeKind,
    val task: WorkflowTask
)

private val initialWorkflowTasks = listOf(
    WorkflowTask(
        id = 1,
        parentId = null,
        title = "新产品阶段评审",
        description = "整理新产品阶段评审材料，明确产品、研发与测试的阶段交付内容。",
        acceptanceCriteria = "评审材料完整；相关负责人确认；评审结论与后续事项有记录。",
        senderRole = DemoRole.FOUNDER,
        senderName = DemoRole.FOUNDER.actor,
        assigneeRole = DemoRole.DEPARTMENT_MANAGER,
        assigneeName = DemoRole.DEPARTMENT_MANAGER.actor,
        deadline = "10月18日",
        origin = WorkflowOrigin.UPPER,
        status = WorkflowStatus.UNFINISHED
    )
)

private fun workflowTaskFromHomeDemo(task: DemoTask, id: Int, person: DemoPerson): WorkflowTask {
    val origin = when (task.source) {
        "上级分配" -> WorkflowOrigin.UPPER
        else -> WorkflowOrigin.INDEPENDENT
    }
    val sender = when (origin) {
        WorkflowOrigin.INDEPENDENT -> if (task.source == "他人派发") demoPeople.firstOrNull {
            it.role == DemoRole.DEPARTMENT_MANAGER && it.name != person.name
        } ?: person else person
        WorkflowOrigin.UPPER -> {
            val senderRole = when (person.role) {
                DemoRole.FOUNDER -> DemoRole.FOUNDER
                DemoRole.DEPARTMENT_MANAGER -> DemoRole.FOUNDER
                DemoRole.TEAM_LEADER -> DemoRole.DEPARTMENT_MANAGER
                DemoRole.EMPLOYEE -> DemoRole.TEAM_LEADER
            }
            demoPeople.first { it.role == senderRole }
        }
    }
    val status = if (task.status == "已完成") WorkflowStatus.COMPLETED else WorkflowStatus.UNFINISHED
    return WorkflowTask(
        id = id,
        parentId = null,
        title = task.title,
        description = "${task.title}。请结合任务要求推进工作，并在截止时间前完成交付。",
        acceptanceCriteria = "交付内容符合任务要求；必要材料完整；完成情况清晰可核验。",
        senderRole = sender.role,
        senderName = sender.name,
        assigneeRole = person.role,
        assigneeName = person.name,
        deadline = task.deadline,
        origin = origin,
        status = status,
        approvalStatus = ApprovalStatus.APPROVED
    )
}

private fun directSupervisor(person: DemoPerson): DemoPerson? = when (person.role) {
    DemoRole.FOUNDER -> null
    DemoRole.DEPARTMENT_MANAGER -> demoPeople.firstOrNull { it.role == DemoRole.FOUNDER }
    DemoRole.TEAM_LEADER -> demoPeople.firstOrNull {
        it.role == DemoRole.DEPARTMENT_MANAGER && it.unit == person.parentUnit
    }
    DemoRole.EMPLOYEE -> demoPeople.firstOrNull {
        it.role == DemoRole.TEAM_LEADER && it.unit == person.parentUnit
    }
}

private fun isBelowInHierarchy(candidate: DemoPerson, ancestor: DemoPerson): Boolean {
    var manager = directSupervisor(candidate)
    while (manager != null) {
        if (manager.name == ancestor.name) return true
        manager = directSupervisor(manager)
    }
    return false
}

private fun workViewTargetOptions(person: DemoPerson): List<String> =
    listOf("本人") + demoPeople
        .filter { isBelowInHierarchy(it, person) }
        .map { "${it.unit} · ${it.name}（${it.role.label}）" }

private fun taskAssigneeOptions(person: DemoPerson): List<DemoPerson> {
    if (person.role == DemoRole.FOUNDER) {
        return demoPeople.distinctBy { it.optionLabel }
    }

    val directReports = when (person.role) {
        DemoRole.FOUNDER -> emptyList()
        DemoRole.DEPARTMENT_MANAGER -> demoPeople.filter {
            it.role == DemoRole.TEAM_LEADER && it.parentUnit == person.unit
        }
        DemoRole.TEAM_LEADER -> demoPeople.filter {
            it.role == DemoRole.EMPLOYEE && it.parentUnit == person.unit
        }
        DemoRole.EMPLOYEE -> emptyList()
    }
    val permittedPeers = if (person.role == DemoRole.DEPARTMENT_MANAGER) {
        demoPeople.filter { it.role == DemoRole.DEPARTMENT_MANAGER && it.unit != person.unit }
    } else {
        emptyList()
    }
    return (listOf(person) + directReports + permittedPeers).distinctBy { it.optionLabel }
}

/**
 * The displayed source is relative to the current user's task relationship.
 * A department manager's self-created root is still an upper-assigned task
 * from the team leader's point of view once it is assigned down to them.
 */
private fun taskSourceLabel(task: WorkflowTask, role: DemoRole, actorName: String, tasks: List<WorkflowTask>): String {
    return when (role) {
        DemoRole.FOUNDER -> when (task.origin) {
            WorkflowOrigin.UPPER -> "上级分配"
            WorkflowOrigin.INDEPENDENT -> if (task.senderName == actorName) "自主创建" else "他人派发"
        }
        DemoRole.DEPARTMENT_MANAGER -> when (task.origin) {
            // Founder-created assignments can now target any organization level.
            // To the assignee, they are still work assigned from above.
            WorkflowOrigin.INDEPENDENT -> when {
                task.senderRole == DemoRole.FOUNDER && task.senderName != actorName -> "上级分配"
                task.senderName != actorName -> "他人派发"
                else -> "部门自主"
            }
            WorkflowOrigin.UPPER -> "上级分配"
        }
        DemoRole.TEAM_LEADER -> {
            if (task.senderRole == DemoRole.FOUNDER && task.senderName != actorName) return "上级分配"
            val byId = tasks.associateBy { it.id }
            var current: WorkflowTask? = task
            var receivedFromManager = false
            while (current != null) {
                if (current.assigneeRole == DemoRole.TEAM_LEADER &&
                    current.assigneeName == actorName &&
                    current.senderRole == DemoRole.DEPARTMENT_MANAGER
                ) {
                    receivedFromManager = true
                    break
                }
                current = current.parentId?.let(byId::get)
            }
            if (receivedFromManager) "上级分配" else "团队自主"
        }
        DemoRole.EMPLOYEE -> "上级分配"
    }
}

private data class MonthBarTask(
    val title: String,
    val createdOffset: Int,
    val endOffset: Int,
    val source: String,
    val completed: Boolean
)
private data class MonthTaskLane(
    val task: MonthBarTask,
    val lane: Int
)

private val companyMatters = listOf(
    DemoMatter("新产品阶段评审", "产品与研发 · 10月12日", "公司将于10月12日组织新产品阶段评审，请产品、研发与测试团队提前准备阶段成果、风险清单和后续计划。"),
    DemoMatter("年度客户服务升级", "客户体验 · 10月18日", "本年度客户服务升级工作进入落地阶段，重点完善问题响应机制、服务流程和客户反馈闭环。"),
    DemoMatter("核心系统稳定性提升", "技术保障 · 10月21日", "技术保障团队将开展核心系统稳定性专项检查，梳理高风险环节并制定改进措施。"),
    DemoMatter("重点项目交付复盘", "项目管理 · 10月24日", "对重点项目的交付过程、协作情况和质量结果进行复盘，沉淀可复用经验与待改进事项。"),
    DemoMatter("新市场合作方案", "业务拓展 · 10月28日", "业务拓展团队正在整理新市场合作方案，后续将提交管理层讨论合作目标与实施路径。"),
    DemoMatter("季度经营数据复核", "经营管理 · 10月30日", "各业务部门需配合复核本季度经营数据，确保统计口径一致、数据来源清晰。"),
    DemoMatter("员工培训计划启动", "组织发展 · 11月03日", "新一期员工培训计划将于11月启动，培训内容覆盖岗位技能、协作规范与产品知识。"),
    DemoMatter("产品安全检查", "质量保障 · 11月06日", "质量保障团队将组织产品安全检查，重点关注权限、数据处理和异常场景。"),
    DemoMatter("客户反馈集中改进", "客户体验 · 11月10日", "对近期客户反馈进行分类整理，推动高频问题进入产品改进和服务优化流程。"),
    DemoMatter("下一阶段目标规划", "战略规划 · 11月14日", "管理层将结合当前经营情况讨论下一阶段目标，明确重点方向和阶段性成果。")
)

private val companyTasks = listOf(
    DemoTask("公司级工作方案", "上级分配", "未完成", "10月14日", Blue),
    DemoTask("产品阶段评审", "上级分配", "未完成", "10月10日", Blue),
    DemoTask("季度目标拆解", "上级分配", "未完成", "10月16日", Blue),
    DemoTask("客户需求复盘", "上级分配", "未完成", "10月11日", Blue),
    DemoTask("完善产品交付规范", "部门自主", "未完成", "10月16日", Blue),
    DemoTask("产品路线确认", "部门自主", "未完成", "10月20日", Blue),
    DemoTask("研发协作流程梳理", "部门自主", "未完成", "10月22日", Blue),
    DemoTask("部门月度评审准备", "部门自主", "未完成", "10月24日", Blue),
    DemoTask("跨部门资料整理", "他人派发", "未完成", "10月14日", Blue),
    DemoTask("市场方案协同确认", "他人派发", "未完成", "10月19日", Blue)
)

private val personalFocusTasks = listOf(
    DemoTask("完成需求评审", "上级分配", "未完成", "10月10日", Blue),
    DemoTask("整理测试计划", "部门自主", "未完成", "10月12日", Blue),
    DemoTask("跨部门资料整理", "他人派发", "未完成", "10月14日", Blue),
    DemoTask("完善产品交付规范", "部门自主", "未完成", "10月16日", Blue),
    DemoTask("确认项目风险清单", "上级分配", "未完成", "10月11日", Blue),
    DemoTask("产品路线确认", "部门自主", "未完成", "10月20日", Blue),
    DemoTask("客户需求复盘", "上级分配", "未完成", "10月18日", Blue),
    DemoTask("研发协作流程梳理", "部门自主", "未完成", "10月22日", Blue),
    DemoTask("市场方案协同确认", "他人派发", "未完成", "10月19日", Blue),
    DemoTask("部门月度评审准备", "部门自主", "未完成", "10月24日", Blue)
)

private val otherMyTasks = listOf(
    DemoTask("整理团队培训材料", "部门自主", "未完成", "10月27日", Blue),
    DemoTask("复核客户反馈清单", "上级分配", "未完成", "10月26日", Blue),
    DemoTask("准备新员工入职指引", "部门自主", "未完成", "10月30日", Blue),
    DemoTask("协助财务核对项目预算", "他人派发", "未完成", "10月28日", Blue)
)

private fun demoUnfinishedViewTasks(target: String): List<UnfinishedViewTask> {
    val teamLeader = target.contains("周晓") || target.contains("王磊")
    val employee = target.contains("陈宁") || target.contains("李敏") || target.contains("赵薇") || target.contains("郑凯")
    val tasks = if (employee) {
        listOf(
            DemoTask("完善接口异常处理", "上级分配", "未完成", "10月10日", Blue) to 5,
            DemoTask("补充测试用例", "团队自主", "未完成", "10月14日", Blue) to 6,
            DemoTask("整理版本问题清单", "上级分配", "未完成", "10月11日", Blue) to 6
        )
    } else if (teamLeader) {
        listOf(
            DemoTask("完成产品阶段评审材料", "上级分配", "未完成", "10月10日", Blue) to 5,
            DemoTask("整理测试验收清单", "团队自主", "未完成", "10月14日", Blue) to 6,
            DemoTask("跟进需求变更说明", "上级分配", "未完成", "10月11日", Blue) to 6
        )
    } else {
        listOf(
            DemoTask("补充跨部门资料", "他人派发", "未完成", "10月10日", Blue) to 5,
            DemoTask("完善产品交付规范", "部门自主", "未完成", "10月15日", Blue) to 6,
            DemoTask("整理新版本评审材料", "上级分配", "未完成", "10月11日", Blue) to 6
        )
    }
    return tasks.map { (task, weekEndColumn) -> UnfinishedViewTask(task, weekEndColumn) }
}

private fun demoWorkEvents(target: String): List<DemoWorkEvent> {
    val teamLeader = target.contains("周晓") || target.contains("王磊")
    val employee = target.contains("陈宁") || target.contains("李敏") || target.contains("赵薇") || target.contains("郑凯")
    if (employee) {
        val assignedTask = DemoTask("完善接口异常处理", "上级分配", "未完成", "10月14日", Blue)
        val ownTask = DemoTask("整理测试用例", "团队自主", "已完成", "10月9日", Teal)
        return listOf(
            DemoWorkEvent(-3, "09:15", assignedTask, WorkEventKind.UPDATE, "补充异常分支说明，并完成相关接口验证。"),
            DemoWorkEvent(-1, "15:40", assignedTask, WorkEventKind.UPDATE, "修复边界问题，准备提交测试结果。"),
            DemoWorkEvent(0, "14:05", assignedTask, WorkEventKind.SUBMISSION, "接口验证结果已提交周晓验收。"),
            DemoWorkEvent(0, "16:25", assignedTask, WorkEventKind.ACCEPTANCE, "周晓验收通过，任务已完成。"),
            DemoWorkEvent(0, "18:10", ownTask, WorkEventKind.SUBMIT_AND_COMPLETE, "测试用例已整理并完成。")
        )
    }
    if (teamLeader) {
        val assignedTask = DemoTask("产品阶段评审", "上级分配", "未完成", "10月14日", Blue)
        val ownTask = DemoTask("整理团队周会事项", "团队自主", "已完成", "10月9日", Teal)
        return listOf(
            DemoWorkEvent(-3, "10:05", assignedTask, WorkEventKind.UPDATE, "补齐阶段交付清单，确认各项材料负责人。"),
            DemoWorkEvent(-1, "14:30", assignedTask, WorkEventKind.UPDATE, "完成评审材料初稿，正在核对数据。"),
            DemoWorkEvent(0, "15:20", assignedTask, WorkEventKind.SUBMISSION, "评审材料已整理完成，提交林经理验收。"),
            DemoWorkEvent(0, "17:20", assignedTask, WorkEventKind.ACCEPTANCE, "林经理验收通过，任务已完成。"),
            DemoWorkEvent(0, "19:20", ownTask, WorkEventKind.SUBMIT_AND_COMPLETE, "周会安排已整理并完成。")
        )
    }
    val assignedTask = DemoTask("公司级工作方案", "上级分配", "未完成", "10月14日", Blue)
    val peerAssignedTask = DemoTask("跨部门资料整理", "他人派发", "未完成", "10月14日", Blue)
    val ownTask = DemoTask("部门月度评审准备", "部门自主", "已完成", "10月9日", Teal)
    return listOf(
        DemoWorkEvent(-4, "09:35", assignedTask, WorkEventKind.UPDATE, "补充阶段目标和交付边界，待团队确认。"),
        DemoWorkEvent(-2, "14:10", peerAssignedTask, WorkEventKind.UPDATE, "已汇总协作资料，补充缺失的业务口径。"),
        DemoWorkEvent(-1, "16:45", assignedTask, WorkEventKind.SUBMISSION, "方案及配套材料已提交周总验收。"),
        DemoWorkEvent(0, "10:15", assignedTask, WorkEventKind.ACCEPTANCE, "周总验收通过，任务已完成。"),
        DemoWorkEvent(0, "10:40", peerAssignedTask, WorkEventKind.UPDATE, "补齐协作材料中的验收口径、交付清单和遗留问题，等待相关部门确认。"),
        DemoWorkEvent(0, "17:20", ownTask, WorkEventKind.SUBMIT_AND_COMPLETE, "本月评审安排已整理并完成。")
    )
}

private val recentLogSummaries = listOf(
    DemoLogSummary("完成产品需求评审", "10/08"),
    DemoLogSummary("补充测试计划与验收场景", "10/07"),
    DemoLogSummary("梳理跨部门协作事项", "10/06"),
    DemoLogSummary("完成接口字段初步确认", "10/05"),
    DemoLogSummary("整理本周项目会议结论", "10/02"),
    DemoLogSummary("更新产品交付规范", "10/01"),
    DemoLogSummary("跟进客户反馈问题", "09/30"),
    DemoLogSummary("确认新版本评审安排", "09/29"),
    DemoLogSummary("梳理版本发布检查项", "09/28"),
    DemoLogSummary("完成部门周会准备", "09/25")
)

private fun orderedCompanyTasks(): List<DemoTask> {
    fun sourceOrder(source: String) = when (source) {
        "上级分配" -> 0
        "部门自主" -> 1
        "他人派发" -> 0
        else -> 3
    }
    fun deadlineOrder(deadline: String) = deadline.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE

    return companyTasks.sortedWith(
        compareBy<DemoTask> { sourceOrder(it.source) }
            .thenBy { deadlineOrder(it.deadline) }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PandoraDemoTheme {
                PandoraPrototype()
            }
        }
    }
}

@Composable
private fun PandoraPrototype() {
    var selectedTab by remember { mutableStateOf(RootTab.HOME) }
    var childPage by remember { mutableStateOf<ChildPage?>(null) }
    var initialTaskDetailId by remember { mutableStateOf<Int?>(null) }
    var taskDetailReturnPage by remember { mutableStateOf<ChildPage?>(null) }
    var logTarget by remember { mutableStateOf("本人") }
    var selectedLogOffset by remember { mutableStateOf(-1) }
    var logMonthShift by remember { mutableStateOf(0) }
    var logEditorOffset by remember { mutableStateOf(0) }
    var dailyLogs by remember {
        mutableStateOf(
            mapOf(
                -3 to DemoDailyLog("整理周会纪要，明确下一阶段的重点", "跟进会议中的行动项。", "已提交"),
                -2 to DemoDailyLog("完成需求梳理，确认任务分发流程。", "补充页面交互稿。", "已提交"),
                -1 to DemoDailyLog("完成任务详情页设计并与团队同步。", "继续完善原型。", "已提交")
            )
        )
    }
    var viewTarget by remember { mutableStateOf("本人") }
    var workViewState by remember { mutableStateOf(WorkViewUiState()) }
    var taskDetailReadOnly by remember { mutableStateOf(false) }
    var orderedPersonalTasks by remember { mutableStateOf(personalFocusTasks) }
    var workflowTasks by remember { mutableStateOf(initialWorkflowTasks) }
    var demoPerson by remember { mutableStateOf(demoPeople.first { it.role == DemoRole.DEPARTMENT_MANAGER }) }
    var readTaskNotificationKeys by remember { mutableStateOf(emptySet<String>()) }
    val taskNotifications = buildList {
        workflowTasks.filter {
            !it.cancelled && it.approvalStatus == ApprovalStatus.PENDING &&
                it.approverRole == demoPerson.role && it.approverName == demoPerson.name
        }.forEach { task ->
            add(TaskInboxNotice("${demoPerson.optionLabel}:approval:${task.id}", TaskNoticeKind.APPROVAL, task))
        }
        workflowTasks.filter {
            !it.cancelled && it.approvalStatus == ApprovalStatus.APPROVED &&
                it.assigneeRole == demoPerson.role && it.assigneeName == demoPerson.name &&
                (it.senderRole != demoPerson.role || it.senderName != demoPerson.name)
        }.forEach { task ->
            add(TaskInboxNotice("${demoPerson.optionLabel}:assignment:${task.id}", TaskNoticeKind.ASSIGNMENT, task))
        }
    }.sortedByDescending { it.task.id }

    fun openTaskNotifications() {
        readTaskNotificationKeys = readTaskNotificationKeys + taskNotifications.map { it.key }
        childPage = ChildPage.NOTIFICATIONS
    }

    fun openNotifiedTask(notice: TaskInboxNotice) {
        initialTaskDetailId = notice.task.id
        taskDetailReturnPage = ChildPage.NOTIFICATIONS
        taskDetailReadOnly = false
        childPage = ChildPage.TASKS
    }

    fun openHomeTask(
        task: DemoTask,
        returnPage: ChildPage?,
        assignee: DemoPerson = demoPerson,
        readOnly: Boolean = false
    ) {
        val existing = workflowTasks.firstOrNull {
            it.title == task.title && it.assigneeRole == assignee.role && it.assigneeName == assignee.name
        }
        val selected = existing ?: workflowTaskFromHomeDemo(
            task = task,
            id = (workflowTasks.maxOfOrNull { it.id } ?: 0) + 1,
            person = assignee
        )
        val opened = if (!readOnly && selected.status == WorkflowStatus.UNFINISHED && !selected.started) {
            selected.copy(started = true)
        } else {
            selected
        }
        workflowTasks = if (existing == null) workflowTasks + opened else workflowTasks.map {
            if (it.id == opened.id) opened else it
        }
        initialTaskDetailId = opened.id
        taskDetailReturnPage = returnPage
        taskDetailReadOnly = readOnly
        childPage = ChildPage.TASKS
    }

    fun openTaskCenter() {
        initialTaskDetailId = null
        taskDetailReturnPage = null
        taskDetailReadOnly = false
        childPage = ChildPage.TASKS
    }

    BackHandler(enabled = childPage != null) {
        childPage = taskDetailReturnPage
        taskDetailReturnPage = null
        initialTaskDetailId = null
        taskDetailReadOnly = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .safeDrawingPadding()
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (childPage) {
                ChildPage.TASKS -> TaskCenterV2Screen(
                    person = demoPerson,
                    tasks = workflowTasks,
                    onPersonChange = { demoPerson = it },
                    onTasksChange = { workflowTasks = it },
                    onBack = {
                        childPage = taskDetailReturnPage
                        taskDetailReturnPage = null
                        initialTaskDetailId = null
                        taskDetailReadOnly = false
                    },
                    initialTaskId = initialTaskDetailId,
                    readOnlyDetail = taskDetailReadOnly
                )
                ChildPage.LOG_EDITOR -> DailyLogEditorScreen(
                    logDate = demoDate(logEditorOffset),
                    log = dailyLogs[logEditorOffset],
                    canEdit = logEditorOffset in -2..0,
                    onSave = { completed, plan ->
                        val oldStatus = dailyLogs[logEditorOffset]?.status
                        dailyLogs = dailyLogs + (logEditorOffset to DemoDailyLog(
                            completed,
                            plan,
                            if (oldStatus == "已提交") "已提交" else "草稿"
                        ))
                    },
                    onSubmit = { completed, plan ->
                        dailyLogs = dailyLogs + (logEditorOffset to DemoDailyLog(completed, plan, "已提交"))
                    },
                    onBack = { childPage = null }
                )
                ChildPage.COMPANY_MATTERS -> CompanyMattersScreen(onBack = { childPage = null })
                ChildPage.COMPANY_TASKS -> CompanyTasksScreen(
                    onBack = { childPage = null },
                    onTaskClick = { openHomeTask(it, ChildPage.COMPANY_TASKS) }
                )
                ChildPage.PERSONAL_FOCUS -> PersonalFocusScreen(
                    orderedTasks = orderedPersonalTasks,
                    onTasksChange = { orderedPersonalTasks = it },
                    onBack = { childPage = null },
                    onTaskClick = { openHomeTask(it, ChildPage.PERSONAL_FOCUS) }
                )
                ChildPage.NOTIFICATIONS -> TaskNotificationsScreen(
                    notifications = taskNotifications,
                    onBack = { childPage = null },
                    onOpenTask = ::openNotifiedTask
                )
                null -> when (selectedTab) {
                    RootTab.HOME -> HomeScreen(
                        personalFocus = orderedPersonalTasks,
                        hasUnreadTaskNotifications = taskNotifications.any { it.key !in readTaskNotificationKeys },
                        onNotifications = ::openTaskNotifications,
                        onLogs = { selectedTab = RootTab.LOG },
                        onWriteLog = {
                            selectedTab = RootTab.LOG
                            logTarget = "本人"
                            logMonthShift = 0
                            selectedLogOffset = 0
                            logEditorOffset = 0
                            childPage = ChildPage.LOG_EDITOR
                        },
                        onCompanyMatters = { childPage = ChildPage.COMPANY_MATTERS },
                        onCompanyTasks = { childPage = ChildPage.COMPANY_TASKS },
                        onPersonalFocus = { childPage = ChildPage.PERSONAL_FOCUS }
                    )
                    RootTab.VIEW -> WorkViewScreen(
                        viewer = demoPerson,
                        target = viewTarget,
                        state = workViewState,
                        onStateChange = { workViewState = it },
                        onTargetChange = { viewTarget = it },
                        onOpenTasks = { openTaskCenter() },
                        onOpenTask = { task ->
                            val taskOwner = if (viewTarget == "本人") demoPerson else {
                                demoPeople.firstOrNull { viewTarget.contains(it.name) } ?: demoPerson
                            }
                            openHomeTask(
                                task = task,
                                returnPage = null,
                                assignee = taskOwner,
                                readOnly = viewTarget != "本人"
                            )
                        }
                    )
                    RootTab.LOG -> LogCalendarScreen(
                        target = logTarget,
                        viewer = demoPerson,
                        selectedOffset = selectedLogOffset,
                        monthShift = logMonthShift,
                        ownLogs = dailyLogs,
                        onSelectedOffsetChange = { selectedLogOffset = it },
                        onMonthShiftChange = { logMonthShift = it },
                        onTargetChange = { logTarget = it },
                        onEditSelectedDate = { offset ->
                            logEditorOffset = offset
                            childPage = ChildPage.LOG_EDITOR
                        }
                    )
                    RootTab.AI -> AiMapScreen()
                    RootTab.MINE -> MyScreen(onTasks = { openTaskCenter() })
                }
            }
        }

        if (childPage == null) {
            BottomNavigation(selected = selectedTab, onSelect = {
                selectedTab = it
                if (it == RootTab.VIEW) workViewState = WorkViewUiState()
            })
        }
    }
}

@Composable
private fun HomeScreen(
    personalFocus: List<DemoTask>,
    hasUnreadTaskNotifications: Boolean,
    onNotifications: () -> Unit,
    onCompanyMatters: () -> Unit,
    onCompanyTasks: () -> Unit,
    onPersonalFocus: () -> Unit,
    onLogs: () -> Unit,
    onWriteLog: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
    ) {
        BrandHeader(
            showProfile = true,
            hasUnreadTaskNotifications = hasUnreadTaskNotifications,
            onNotifications = onNotifications
        )
        Spacer(Modifier.height(18.dp))
        Text("工作台", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomePanel(
                modifier = Modifier.weight(1f).height(235.dp),
                title = "公司重要事项",
                icon = "◆",
                accent = Amber,
                tint = AmberPale,
                rows = companyMatters.take(5).map { it.title },
                onClick = onCompanyMatters,
                footer = "查看全部"
            )
            HomePanel(
                modifier = Modifier.weight(1f).height(235.dp),
                title = "公司十大重要任务",
                icon = "✓",
                accent = Blue,
                tint = BluePale,
                rows = orderedCompanyTasks().take(5).map { it.title },
                onClick = onCompanyTasks,
                footer = "查看全部"
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomePanel(
                modifier = Modifier.weight(1f).height(235.dp),
                title = "个人重点工作",
                icon = "★",
                accent = Teal,
                tint = TealPale,
                rows = personalFocus.take(5).map { it.title },
                onClick = onPersonalFocus,
                footer = "查看全部"
            )
            HomePanel(
                modifier = Modifier.weight(1f).height(235.dp),
                title = "最近日志",
                icon = "▤",
                accent = Violet,
                tint = VioletPale,
                rows = recentLogSummaries.take(5).map { "${it.date}  ${it.title}" },
                onClick = onLogs,
                footer = "填写今日日志",
                footerAction = onWriteLog
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun TaskNotificationsScreen(
    notifications: List<TaskInboxNotice>,
    onBack: () -> Unit,
    onOpenTask: (TaskInboxNotice) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = Ink, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 13.dp))
            Text("任务通知", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (notifications.isEmpty()) {
            SectionCard("暂无任务通知", "♧", Muted) {
                Text("需要你处理的审批或新派发任务会显示在这里。", color = Muted, fontSize = 11.sp)
            }
        } else {
            notifications.forEach { notice ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable { onOpenTask(notice) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(notice.kind.tint),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (notice.kind == TaskNoticeKind.APPROVAL) "✓" else "▤", color = notice.kind.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(notice.kind.title, color = notice.kind.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(notice.task.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val context = if (notice.kind == TaskNoticeKind.APPROVAL) {
                                "${notice.task.senderName} 创建 · 截止 ${notice.task.deadline}"
                            } else {
                                "${notice.task.senderName} 派发 · 截止 ${notice.task.deadline}"
                            }
                            Text(context, color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("›", color = Muted, fontSize = 22.sp, modifier = Modifier.padding(start = 7.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun HomePanel(
    modifier: Modifier,
    title: String,
    icon: String,
    accent: Color,
    tint: Color,
    rows: List<String>,
    onClick: () -> Unit,
    footer: String? = null,
    footerAction: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint, accent, 34)
                Spacer(Modifier.width(8.dp))
                Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            rows.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(22.dp)) {
                    Text("${index + 1}".padStart(2, '0'), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(17.dp))
                    Text(item, color = Ink, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            if (footer != null) {
                TextButton(onClick = footerAction ?: onClick, modifier = Modifier.fillMaxWidth().height(30.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(footer, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("查看全部  ›", color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CompanyMattersScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ChildTopBar("公司重要事项", onBack)
        SectionCard(title = "当前重要事项", icon = "◆", accent = Amber, trailing = "最多10项") {
            companyMatters.forEachIndexed { index, matter ->
                RankedMatterRow(index + 1, matter)
            }
        }
    }
}

@Composable
private fun RankedMatterRow(rank: Int, matter: DemoMatter) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(if (rank <= 3) AmberPale else Canvas), contentAlignment = Alignment.Center) {
            Text(rank.toString().padStart(2, '0'), color = if (rank <= 3) Amber else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(matter.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(matter.context, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            Text(matter.detail, color = Ink, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun CompanyTasksScreen(onBack: () -> Unit, onTaskClick: (DemoTask) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ChildTopBar("公司十大重要任务", onBack)
        orderedCompanyTasks().forEachIndexed { index, task ->
            RankedTaskCard(index + 1, task, onClick = { onTaskClick(task) })
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PersonalFocusScreen(
    orderedTasks: List<DemoTask>,
    onTasksChange: (List<DemoTask>) -> Unit,
    onBack: () -> Unit,
    onTaskClick: (DemoTask) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var showCandidates by remember { mutableStateOf(false) }
    fun move(from: Int, to: Int) {
        if (to !in orderedTasks.indices) return
        val next = orderedTasks.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        onTasksChange(next)
    }
    val companyTaskTitles = companyTasks.map { it.title }.toSet()
    val candidates = (companyTasks + personalFocusTasks + otherMyTasks)
        .distinctBy { it.title }
        .filterNot { candidate -> orderedTasks.any { it.title == candidate.title } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ChildTopBar("个人重点工作", onBack)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text("${orderedTasks.size}/10", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = !editing }) {
                Text(if (editing) "完成" else "编辑", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (editing) {
            OutlinedButton(
                onClick = { showCandidates = true },
                enabled = orderedTasks.size < 10,
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(if (orderedTasks.size < 10) "＋  从我的任务中添加" else "已达10项 · 移除后可继续添加", color = if (orderedTasks.size < 10) Blue else Muted, fontSize = 12.sp)
            }
        }
        orderedTasks.forEachIndexed { index, task ->
            key(task.title) {
                RankedTaskCard(
                    rank = index + 1,
                    task = task,
                    editing = editing,
                    itemCount = orderedTasks.size,
                    onReorder = ::move,
                    onClick = { onTaskClick(task) },
                    onRemove = { onTasksChange(orderedTasks.filterIndexed { current, _ -> current != index }) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showCandidates) {
        AlertDialog(
            onDismissRequest = { showCandidates = false },
            containerColor = Paper,
            title = { Text("从我的任务中添加", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState())) {
                    candidates.forEach { task ->
                        val outsideCompanyTopTen = task.title !in companyTaskTitles
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable {
                                if (orderedTasks.size < 10) {
                                    onTasksChange(orderedTasks + task)
                                    showCandidates = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (outsideCompanyTopTen) TealPale else Canvas),
                            border = BorderStroke(1.dp, if (outsideCompanyTopTen) Teal.copy(alpha = 0.25f) else Line)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${task.source} · ${task.status} · 截止${task.deadline}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                                }
                                Text(if (outsideCompanyTopTen) "十大外" else "公司十大", color = if (outsideCompanyTopTen) Teal else Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (candidates.isEmpty()) Text("没有更多可加入的未完成任务", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCandidates = false }) { Text("完成", color = Blue) }
            }
        )
    }
}

@Composable
private fun RankedTaskCard(
    rank: Int,
    task: DemoTask,
    editing: Boolean = false,
    itemCount: Int = 0,
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onClick: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    var itemHeightPx by remember(task.title) { mutableStateOf(0f) }
    val currentIndex by rememberUpdatedState(rank - 1)
    val currentItemCount by rememberUpdatedState(itemCount)
    val reorder by rememberUpdatedState(onReorder)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .onGloballyPositioned { itemHeightPx = it.size.height.toFloat() }
            .clickable(enabled = !editing, onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(rank.toString().padStart(2, '0'), color = task.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(27.dp))
            Box(Modifier.width(3.dp).height(32.dp).clip(CircleShape).background(task.color.copy(alpha = 0.65f)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${task.source} · 截止${task.deadline}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Canvas)
                            .pointerInput(task.title, editing) {
                                var dragIndex = currentIndex
                                var accumulatedDrag = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragIndex = currentIndex
                                        accumulatedDrag = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDrag += dragAmount.y
                                        val step = (itemHeightPx + 8.dp.toPx()).coerceAtLeast(68.dp.toPx())
                                        if (accumulatedDrag >= step && dragIndex < currentItemCount - 1) {
                                            reorder(dragIndex, dragIndex + 1)
                                            dragIndex += 1
                                            accumulatedDrag -= step
                                        } else if (accumulatedDrag <= -step && dragIndex > 0) {
                                            reorder(dragIndex, dragIndex - 1)
                                            dragIndex -= 1
                                            accumulatedDrag += step
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⋮⋮", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                        }
                    }
                    Text("×", color = Rose, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onRemove).padding(horizontal = 7.dp, vertical = 7.dp))
                }
            } else {
                Text(task.status, color = task.color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BrandHeader(
    showProfile: Boolean,
    hasUnreadTaskNotifications: Boolean = false,
    onNotifications: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("PANDORA", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            Text("智能掌上工作系统", color = Muted, fontSize = 11.sp)
        }
        if (showProfile) {
            onNotifications?.let { onClick ->
                TaskNotificationBell(
                    hasUnread = hasUnreadTaskNotifications,
                    onClick = onClick
                )
                Spacer(Modifier.width(7.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("林经理  ·  部门经理", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("产品部   ·   周五 10月9日", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.width(9.dp))
            Box(Modifier.size(37.dp).clip(CircleShape).background(AmberPale), contentAlignment = Alignment.Center) {
                Text("林", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TaskNotificationBell(hasUnread: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ComposeCanvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.8.dp.toPx()
            val bell = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                cubicTo(w * 0.36f, h * 0.12f, w * 0.27f, h * 0.23f, w * 0.27f, h * 0.4f)
                lineTo(w * 0.27f, h * 0.64f)
                cubicTo(w * 0.27f, h * 0.74f, w * 0.2f, h * 0.79f, w * 0.16f, h * 0.84f)
                lineTo(w * 0.84f, h * 0.84f)
                cubicTo(w * 0.8f, h * 0.79f, w * 0.73f, h * 0.74f, w * 0.73f, h * 0.64f)
                lineTo(w * 0.73f, h * 0.4f)
                cubicTo(w * 0.73f, h * 0.23f, w * 0.64f, h * 0.12f, w * 0.5f, h * 0.12f)
                close()
            }
            drawPath(path = bell, color = Muted, style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawLine(
                color = Muted,
                start = Offset(w * 0.39f, h * 0.9f),
                end = Offset(w * 0.61f, h * 0.9f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        if (hasUnread) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)
                    .size(8.dp).clip(CircleShape).background(Rose)
            )
        }
    }
}

@Composable
private fun WorkViewScreen(
    viewer: DemoPerson,
    target: String,
    state: WorkViewUiState,
    onStateChange: (WorkViewUiState) -> Unit,
    onTargetChange: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTask: (DemoTask) -> Unit
) {
    val mode = state.mode
    val unfinishedTasks = demoUnfinishedViewTasks(target)
    val taskEvents = demoWorkEvents(target)
    var showMonthPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickerYear by remember { mutableStateOf(DemoBaseYear) }
    var pickerMonth by remember { mutableStateOf(DemoBaseMonth + 1) }
    val selectedPeriodDate = when (mode) {
        ViewMode.MONTH -> monthCalendar(state.monthShift)
        ViewMode.WEEK -> demoDate(state.dayOffset)
        ViewMode.DAY -> demoDate(state.dayOffset)
    }
    fun openPeriodPicker() {
        if (mode == ViewMode.MONTH) {
            pickerYear = selectedPeriodDate.get(Calendar.YEAR)
            pickerMonth = selectedPeriodDate.get(Calendar.MONTH) + 1
            showMonthPicker = true
        } else {
            showDatePicker = true
        }
    }
    fun selectMonth(year: Int, month: Int) {
        val monthShift = monthShiftFor(year, month)
        val selectedMonth = monthCalendar(monthShift)
        val selectedDay = when (mode) {
            ViewMode.MONTH, ViewMode.WEEK -> 1
            ViewMode.DAY -> demoDate(state.dayOffset).get(Calendar.DAY_OF_MONTH)
                .coerceAtMost(selectedMonth.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        val selectedOffset = dayOffsetFor(year, month, selectedDay)
        onStateChange(
            state.copy(
                monthShift = monthShift,
                weekShift = weekShiftForDay(selectedOffset),
                dayOffset = selectedOffset
            )
        )
        showMonthPicker = false
    }
    fun selectDate(year: Int, month: Int, day: Int) {
        val selectedOffset = dayOffsetFor(year, month, day)
        onStateChange(
            state.copy(
                monthShift = monthShiftFor(year, month),
                weekShift = weekShiftForDay(selectedOffset),
                dayOffset = selectedOffset
            )
        )
        showDatePicker = false
    }
    fun setPeriodDay(dayOffset: Int, nextMode: ViewMode = mode) {
        val date = demoDate(dayOffset)
        onStateChange(
            state.copy(
                mode = nextMode,
                dayOffset = dayOffset,
                weekShift = weekShiftForDay(dayOffset),
                monthShift = monthShiftFor(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1)
            )
        )
    }
    val screenModifier = if (mode == ViewMode.MONTH) {
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxSize()
    }

    Column(screenModifier.padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ObjectPicker(
                    label = "查看对象",
                    selected = target,
                    options = workViewTargetOptions(viewer),
                    onSelected = onTargetChange
                )
            }
            OutlinedButton(
                onClick = onOpenTasks,
                modifier = Modifier.height(42.dp),
                shape = RoundedCornerShape(13.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) { Text("任务中心", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
        Spacer(Modifier.height(7.dp))
        SegmentedChoices(
            values = ViewMode.values().toList(),
            selected = mode,
            label = { it.title },
            onSelected = {
                val focusOffset = if (mode == ViewMode.MONTH) {
                    val month = monthCalendar(state.monthShift)
                    dayOffsetFor(month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1, 1)
                } else state.dayOffset
                setPeriodDay(focusOffset, it)
            }
        )
        Spacer(Modifier.height(5.dp))
        when (mode) {
            ViewMode.WEEK -> {
                PeriodBar(
                    label = weekLabel(state.weekShift),
                    centerAction = if (state.weekShift == 0) null else "本周",
                    onPrevious = { setPeriodDay(state.dayOffset - 7) },
                    onNext = { setPeriodDay(state.dayOffset + 7) },
                    onCenter = { setPeriodDay(0) },
                    onLabel = ::openPeriodPicker
                )
                Spacer(Modifier.height(2.dp))
                WeekScheduleBoard(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    weekShift = state.weekShift,
                    selectedDayOffset = state.dayOffset,
                    tasks = if (state.weekShift == 0) unfinishedTasks else emptyList(),
                    events = taskEvents,
                    onSelectDay = { offset -> setPeriodDay(offset, ViewMode.DAY) },
                    onTaskClick = onOpenTask
                )
            }
            ViewMode.DAY -> {
                PeriodBar(
                    label = dayLabel(state.dayOffset),
                    centerAction = if (state.dayOffset == 0) null else "今天",
                    onPrevious = { setPeriodDay(state.dayOffset - 1) },
                    onNext = { setPeriodDay(state.dayOffset + 1) },
                    onCenter = { setPeriodDay(0) },
                    onLabel = ::openPeriodPicker
                )
                Spacer(Modifier.height(2.dp))
                DayScheduleBoard(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    dayOffset = state.dayOffset,
                    tasks = if (state.dayOffset == 0) unfinishedTasks else emptyList(),
                    events = taskEvents.filter { it.dayOffset == state.dayOffset },
                    onSelectDay = { offset -> setPeriodDay(offset, ViewMode.DAY) },
                    onTaskClick = onOpenTask
                )
            }
            ViewMode.MONTH -> {
                val month = monthCalendar(state.monthShift)
                PeriodBar(
                    label = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(month.time),
                    centerAction = if (state.monthShift == 0) null else "本月",
                    onPrevious = { onStateChange(state.copy(monthShift = state.monthShift - 1)) },
                    onNext = { onStateChange(state.copy(monthShift = state.monthShift + 1)) },
                    onCenter = { onStateChange(state.copy(monthShift = 0)) },
                    onLabel = ::openPeriodPicker
                )
                MonthCalendar(
                    month = month,
                    onDaySelected = { day ->
                        val selected = monthCalendar(state.monthShift).apply { set(Calendar.DAY_OF_MONTH, day) }
                        val dayOffset = dayOffsetFor(
                            selected.get(Calendar.YEAR),
                            selected.get(Calendar.MONTH) + 1,
                            selected.get(Calendar.DAY_OF_MONTH)
                        )
                        onStateChange(state.copy(dayOffset = dayOffset, weekShift = weekShiftForDay(dayOffset), mode = ViewMode.DAY))
                    },
                    onTaskClick = onOpenTask
                )
            }
        }
        if (mode == ViewMode.MONTH) Spacer(Modifier.height(12.dp))
    }

    if (showMonthPicker) {
        QuickMonthPickerDialog(
            selectedYear = pickerYear,
            selectedMonth = pickerMonth,
            onDismiss = { showMonthPicker = false },
            onYearChange = { pickerYear = it },
            onMonthSelected = { month -> selectMonth(pickerYear, month) }
        )
    }
    if (showDatePicker) {
        QuickDatePickerDialog(
            selectedYear = selectedPeriodDate.get(Calendar.YEAR),
            selectedMonth = selectedPeriodDate.get(Calendar.MONTH) + 1,
            selectedDay = selectedPeriodDate.get(Calendar.DAY_OF_MONTH),
            onDismiss = { showDatePicker = false },
            onDateSelected = ::selectDate
        )
    }
}

@Composable
private fun WeekScheduleBoard(
    modifier: Modifier,
    weekShift: Int,
    selectedDayOffset: Int,
    tasks: List<UnfinishedViewTask>,
    events: List<DemoWorkEvent>,
    onSelectDay: (Int) -> Unit,
    onTaskClick: (DemoTask) -> Unit
) {
    val mondayOffset = weekShift * 7 - 4
    ScheduleSurface(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日期", modifier = Modifier.width(TimeGutterWidth), color = Muted, fontSize = 9.sp)
                (0..6).forEach { column ->
                    val dayOffset = mondayOffset + column
                    ScheduleDayHeader(
                        dayOffset = dayOffset,
                        selected = dayOffset == selectedDayOffset,
                        compact = true,
                        modifier = Modifier.weight(1f).padding(horizontal = ScheduleColumnGap),
                        onClick = { onSelectDay(dayOffset) }
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            if (weekShift == 0) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("全天", modifier = Modifier.width(TimeGutterWidth).padding(top = 5.dp), color = Muted, fontSize = 9.sp)
                    Column(Modifier.weight(1f)) {
                        if (tasks.isEmpty()) {
                            Text("暂无未完成任务", color = Muted, fontSize = 9.sp, modifier = Modifier.height(27.dp).padding(top = 5.dp))
                        } else {
                            tasks.forEach { task -> WeekAllDayTaskBar(task, onTaskClick) }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            ScheduleDivider()
            ScheduleTimelineGrid(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                dateOffsets = (0..6).map { mondayOffset + it },
                events = events,
                compact = true,
                onTaskClick = onTaskClick
            )
        }
    }
}

@Composable
private fun DayScheduleBoard(
    modifier: Modifier,
    dayOffset: Int,
    tasks: List<UnfinishedViewTask>,
    events: List<DemoWorkEvent>,
    onSelectDay: (Int) -> Unit,
    onTaskClick: (DemoTask) -> Unit
) {
    val mondayOffset = mondayOffsetFor(dayOffset)
    ScheduleSurface(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日期", modifier = Modifier.width(TimeGutterWidth), color = Muted, fontSize = 9.sp)
                (0..6).forEach { column ->
                    val offset = mondayOffset + column
                    ScheduleDayHeader(
                        dayOffset = offset,
                        selected = offset == dayOffset,
                        compact = true,
                        modifier = Modifier.weight(1f).padding(horizontal = ScheduleColumnGap),
                        onClick = { onSelectDay(offset) }
                    )
                }
            }
            if (dayOffset == 0) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("全天", modifier = Modifier.width(TimeGutterWidth).padding(top = 5.dp), color = Muted, fontSize = 9.sp)
                    Column(Modifier.weight(1f)) {
                        if (tasks.isEmpty()) {
                            Text("暂无未完成任务", color = Muted, fontSize = 9.sp, modifier = Modifier.height(27.dp).padding(top = 5.dp))
                        } else {
                            tasks.forEach { task -> DayAllDayTaskBar(task, onTaskClick) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            ScheduleDivider()
            ScheduleTimelineGrid(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                dateOffsets = listOf(dayOffset),
                events = events,
                compact = false,
                onTaskClick = onTaskClick
            )
        }
    }
}

@Composable
private fun ScheduleDayHeader(
    dayOffset: Int,
    selected: Boolean,
    modifier: Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val date = demoDate(dayOffset)
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[(date.get(Calendar.DAY_OF_WEEK) + 5) % 7]
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(weekday, color = Muted, fontSize = if (compact) 8.sp else 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Box(
            Modifier.padding(top = 1.dp).size(if (compact) 20.dp else 27.dp).clip(CircleShape)
                .background(if (selected) AmberPale else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(date.get(Calendar.DAY_OF_MONTH).toString(), color = if (selected) Ink else Muted, fontSize = if (compact) 10.sp else 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun WeekAllDayTaskBar(item: UnfinishedViewTask, onTaskClick: (DemoTask) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(23.dp).padding(vertical = 1.dp)) {
        Row(Modifier.fillMaxSize()) {
            repeat(7) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = ScheduleColumnGap).clip(RoundedCornerShape(5.dp)).background(Canvas))
            }
        }
        val spanWidth = maxOf(maxWidth / 7 * (item.weekEndColumn.coerceIn(0, 6) + 1) - ScheduleColumnGap * 2, 20.dp)
        Surface(
            modifier = Modifier.offset(x = ScheduleColumnGap).width(spanWidth).height(21.dp).align(Alignment.CenterStart)
                .clickable { onTaskClick(item.task) },
            shape = RoundedCornerShape(5.dp),
            color = BluePale,
            border = BorderStroke(1.dp, Blue.copy(alpha = 0.12f))
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text(item.task.title, color = Ink, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DayAllDayTaskBar(item: UnfinishedViewTask, onTaskClick: (DemoTask) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).height(25.dp).clickable { onTaskClick(item.task) },
        shape = RoundedCornerShape(8.dp),
        color = BluePale,
        border = BorderStroke(1.dp, Blue.copy(alpha = 0.12f))
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
            Text(item.task.title, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ScheduleTimelineGrid(
    modifier: Modifier,
    dateOffsets: List<Int>,
    events: List<DemoWorkEvent>,
    compact: Boolean,
    onTaskClick: (DemoTask) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        repeat(24) { hour ->
            val eventsForHour = dateOffsets.map { dateOffset ->
                events.asSequence()
                    .filter { it.dayOffset == dateOffset && eventHour(it) == hour }
                    .sortedBy { it.time }
                    .toList()
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier.width(TimeGutterWidth).fillMaxHeight().defaultMinSize(minHeight = HourCellHeight),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopStart).background(Line))
                    Text("%02d:00".format(hour), color = Muted, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
                }
                dateOffsets.forEachIndexed { index, _ ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = ScheduleColumnGap)
                            .defaultMinSize(minHeight = HourCellHeight).background(Paper)
                    ) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        eventsForHour[index].forEachIndexed { eventIndex, event ->
                            if (eventIndex > 0) Spacer(Modifier.height(if (compact) 3.dp else 5.dp))
                            ScheduleEventBlock(
                                event = event,
                                compact = compact,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 1.dp else 5.dp)
                                    .defaultMinSize(minHeight = if (compact) 52.dp else 64.dp),
                                onTaskClick = onTaskClick
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun eventHour(event: DemoWorkEvent): Int = event.time.substringBefore(":").toIntOrNull()?.coerceIn(0, 23) ?: 0

@Composable
private fun ScheduleEventBlock(
    event: DemoWorkEvent,
    compact: Boolean,
    modifier: Modifier,
    onTaskClick: (DemoTask) -> Unit
) {
    Surface(
        modifier = modifier.clickable { onTaskClick(event.task) },
        shape = RoundedCornerShape(8.dp),
        color = event.kind.tint,
        border = BorderStroke(1.dp, event.kind.accent.copy(alpha = 0.25f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 2.dp else 9.dp, vertical = if (compact) 3.dp else 5.dp)) {
            if (compact) {
                Text(event.time, color = event.kind.accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(event.task.title, color = Ink, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold)
                Text(event.kind.label, color = event.kind.accent, fontSize = 7.sp, fontWeight = FontWeight.SemiBold)
                Text(event.summary, color = Muted, fontSize = 7.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.time, color = event.kind.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(event.kind.label, color = event.kind.accent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(event.task.title, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(event.summary, color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ScheduleSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Paper,
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp)) { content() }
    }
}

@Composable
private fun ScheduleDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

private val TimeGutterWidth = 40.dp
private val ScheduleColumnGap = 1.dp
private val HourCellHeight = 68.dp

private fun mondayOffsetFor(dayOffset: Int): Int {
    val dayOfWeekOffset = (demoDate(dayOffset).get(Calendar.DAY_OF_WEEK) + 5) % 7
    return dayOffset - dayOfWeekOffset
}

@Composable
private fun MonthCalendar(
    month: Calendar,
    onDaySelected: (Int) -> Unit,
    onTaskClick: (DemoTask) -> Unit
) {
    val days = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val first = (month.clone() as Calendar)
    first.set(Calendar.DAY_OF_MONTH, 1)
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val previousMonth = (month.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val previousMonthDays = previousMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = demoDate(0)
    val taskLanes = remember(month.get(Calendar.YEAR), month.get(Calendar.MONTH)) {
        monthTaskLanes(month)
    }
    val rowCount = (offset + days + 6) / 7
    val maxVisibleTasksPerDate = 3

    SectionCard(title = "任务安排", icon = "▦", accent = Blue) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, modifier = Modifier.weight(1f), color = Muted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        repeat(rowCount) { rowIndex ->
            val weekStartOffset = rowIndex * 7 - offset
            val weekEndOffset = weekStartOffset + 6
            val weekTasks = taskLanes.filter { lane ->
                lane.task.endOffset >= weekStartOffset && lane.task.createdOffset <= weekEndOffset
            }
            val renderedLanes = weekTasks.filter { it.lane < maxVisibleTasksPerDate }
            val laneCount = (renderedLanes.maxOfOrNull { it.lane } ?: -1) + 1
            val dayOverflowCounts = (0..6).map { column ->
                val day = weekStartOffset + column + 1
                val dayOffset = day - 1
                if (day in 1..days) {
                    weekTasks.count { lane ->
                        lane.lane >= maxVisibleTasksPerDate &&
                            lane.task.createdOffset <= dayOffset &&
                            lane.task.endOffset >= dayOffset
                    }
                } else 0
            }

            Column(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Row(Modifier.fillMaxWidth().height(31.dp)) {
                    for (col in 0..6) {
                        val dayOffset = weekStartOffset + col
                        val day = dayOffset + 1
                        val isCurrentMonth = day in 1..days
                        val displayDay = when {
                            day < 1 -> previousMonthDays + day
                            day > days -> day - days
                            else -> day
                        }
                        val isToday = isCurrentMonth &&
                            month.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            month.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            day == today.get(Calendar.DAY_OF_MONTH)

                        Box(
                            modifier = Modifier.weight(1f).fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = isCurrentMonth) { onDaySelected(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isToday) {
                                Box(Modifier.size(27.dp).clip(CircleShape).background(AmberPale))
                            }
                            Text(
                                displayDay.toString(),
                                color = when {
                                    isToday -> Amber
                                    isCurrentMonth -> Ink
                                    else -> Muted.copy(alpha = 0.45f)
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (laneCount > 0) {
                    BoxWithConstraints(Modifier.fillMaxWidth().height((laneCount * 34).dp)) {
                        val cellWidth = maxWidth / 7f
                        renderedLanes.forEach { lane ->
                            val segmentStart = maxOf(lane.task.createdOffset, weekStartOffset)
                            val segmentEnd = minOf(lane.task.endOffset, weekEndOffset)
                            val startColumn = segmentStart - weekStartOffset
                            val span = segmentEnd - segmentStart + 1
                            val startsHere = lane.task.createdOffset in weekStartOffset..weekEndOffset
                            val endsHere = lane.task.endOffset in weekStartOffset..weekEndOffset
                            val (barColor, barTint) = monthTaskColors(lane.task.source)
                            val compactBar = span == 1

                            Box(
                                modifier = Modifier
                                    .offset(x = cellWidth * startColumn + 1.dp, y = (lane.lane * 34).dp)
                                    .width(cellWidth * span - 2.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(barTint)
                                    .clickable { onTaskClick(monthBarDemoTask(lane.task, month)) }
                                    .padding(horizontal = 3.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (compactBar) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            lane.task.title,
                                            color = Ink,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!startsHere) {
                                            Text("‹", color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(2.dp))
                                        }
                                        Text(
                                            lane.task.title,
                                            color = Ink,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (endsHere) {
                                            Spacer(Modifier.width(2.dp))
                                            Box(Modifier.width(2.dp).height(10.dp).background(barColor))
                                        } else {
                                            Text("›", color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (dayOverflowCounts.any { it > 0 }) {
                    Row(Modifier.fillMaxWidth().height(18.dp)) {
                        dayOverflowCounts.forEachIndexed { column, hiddenCount ->
                            val day = weekStartOffset + column + 1
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize()
                                    .clickable(enabled = hiddenCount > 0) { onDaySelected(day) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (hiddenCount > 0) {
                                    Text(
                                        "+${hiddenCount}项",
                                        color = Blue,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MonthSourceLegend(color = Blue, label = "他人派发")
            MonthSourceLegend(color = Amber, label = "本人创建")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(9.dp).background(Blue))
                Spacer(Modifier.width(4.dp))
                Text("截止日期", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MonthSourceLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Muted, fontSize = 8.sp, maxLines = 1)
    }
}

private fun monthTaskColors(source: String): Pair<Color, Color> = when (source) {
    "上级分配", "他人派发" -> Blue to BluePale
    else -> Amber to AmberPale
}

private fun monthTaskLanes(month: Calendar): List<MonthTaskLane> {
    val tasks = listOf(
        MonthBarTask("延续中的项目支持", -3, 2, "上级分配", true),
        MonthBarTask("客户需求复盘", 0, 8, "上级分配", false),
        MonthBarTask("完成需求评审", 4, 8, "上级分配", false),
        MonthBarTask("跨部门资料整理", 6, 13, "他人派发", false),
        MonthBarTask("补充测试验收材料", 6, 8, "上级分配", false),
        MonthBarTask("完善产品交付规范", 10, 16, "部门自主", false),
        MonthBarTask("季度目标拆解", 15, 21, "上级分配", false),
        MonthBarTask("阶段验收准备", month.getActualMaximum(Calendar.DAY_OF_MONTH) - 4, month.getActualMaximum(Calendar.DAY_OF_MONTH) + 2, "上级分配", false)
    )
    val laneEnds = mutableListOf<Int>()
    return tasks.sortedBy { it.createdOffset }.map { task ->
        val lane = laneEnds.indexOfFirst { it < task.createdOffset }.let { available ->
            if (available >= 0) {
                laneEnds[available] = task.endOffset
                available
            } else {
                laneEnds.add(task.endOffset)
                laneEnds.lastIndex
            }
        }
        MonthTaskLane(task, lane)
    }
}

private fun monthBarDemoTask(task: MonthBarTask, month: Calendar): DemoTask = DemoTask(
    title = task.title,
    source = task.source,
    status = if (task.completed) "已完成" else "未完成",
    deadline = monthDateLabel(month, task.endOffset),
    color = if (task.completed) Teal else Blue
)

private fun monthDateLabel(month: Calendar, dayOffset: Int): String {
    val date = (month.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.DAY_OF_MONTH, dayOffset)
    }
    return if (date.get(Calendar.MONTH) == month.get(Calendar.MONTH)) {
        "${date.get(Calendar.DAY_OF_MONTH)}日"
    } else {
        "${date.get(Calendar.MONTH) + 1}/${date.get(Calendar.DAY_OF_MONTH)}"
    }
}

@Composable
private fun DailyLogEditorScreen(
    logDate: Calendar,
    log: DemoDailyLog?,
    canEdit: Boolean,
    onSave: (String, String) -> Unit,
    onSubmit: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var completed by remember(logDate.timeInMillis) { mutableStateOf(log?.completedWork.orEmpty()) }
    var plan by remember(logDate.timeInMillis) { mutableStateOf(log?.nextDayPlan.orEmpty()) }
    val dateLabel = SimpleDateFormat("yyyy年M月d日 · EEEE", Locale.CHINA).format(logDate.time)
    val status = log?.status ?: "未填写"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ChildTopBar("编辑工作日志", onBack)
        Spacer(Modifier.height(4.dp))
        SectionCard(title = dateLabel, icon = "▤", accent = Amber, trailing = status) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (canEdit) "本人可在今天、昨天和前天填写或修改" else "已超过可编辑日期，日志已冻结",
                    color = if (canEdit) Muted else Rose,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("当日完成  *", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = completed,
                onValueChange = { completed = it; message = "" },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                minLines = 4,
                enabled = canEdit,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text("次日计划（选填）", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = plan,
                onValueChange = { plan = it },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                minLines = 3,
                enabled = canEdit,
                shape = RoundedCornerShape(16.dp)
            )
            if (message.isNotBlank()) Text(message, color = if (message.startsWith("请")) Rose else Teal, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    if (status == "已提交") {
                        Button(
                            onClick = { onSave(completed, plan); message = "已保存修改，日志仍为已提交" },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink)
                        ) { Text("保存修改", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        OutlinedButton(
                            onClick = { onSave(completed, plan); message = "草稿已保存，仅本人可见" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("保存草稿", color = Ink, fontSize = 12.sp) }
                        Button(
                            onClick = {
                                if (completed.isBlank()) message = "请先填写必填项“今日完成”"
                                else { onSubmit(completed, plan); message = "日志已提交，授权管理者可查看" }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink)
                        ) { Text("提交日志", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LogCalendarScreen(
    target: String,
    viewer: DemoPerson,
    selectedOffset: Int,
    monthShift: Int,
    ownLogs: Map<Int, DemoDailyLog>,
    onSelectedOffsetChange: (Int) -> Unit,
    onMonthShiftChange: (Int) -> Unit,
    onTargetChange: (String) -> Unit,
    onEditSelectedDate: (Int) -> Unit
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    var pickerYear by remember { mutableStateOf(2026) }
    val month = remember(monthShift) { monthCalendar(monthShift) }
    val selectedDate = remember(selectedOffset) { demoDate(selectedOffset) }
    val selectedDay = selectedDate.get(Calendar.DAY_OF_MONTH)
    val selectedInMonth = selectedDate.get(Calendar.YEAR) == month.get(Calendar.YEAR) &&
        selectedDate.get(Calendar.MONTH) == month.get(Calendar.MONTH)
    val canEditSelectedDate = target == "本人" && selectedInMonth && selectedOffset in -2..0

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            LogTargetPicker(target = target, options = logPersonTargets(viewer), onTargetChange = onTargetChange)
            Spacer(Modifier.height(12.dp))
            PeriodBar(
                label = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(month.time),
                centerAction = if (monthShift == 0) null else "本月",
                onPrevious = {
                    val nextShift = monthShift - 1
                    onMonthShiftChange(nextShift)
                    val firstDay = monthCalendar(nextShift)
                    onSelectedOffsetChange(dayOffsetFor(firstDay.get(Calendar.YEAR), firstDay.get(Calendar.MONTH) + 1, 1))
                },
                onNext = {
                    val nextShift = monthShift + 1
                    onMonthShiftChange(nextShift)
                    val firstDay = monthCalendar(nextShift)
                    onSelectedOffsetChange(dayOffsetFor(firstDay.get(Calendar.YEAR), firstDay.get(Calendar.MONTH) + 1, 1))
                },
                onCenter = { onMonthShiftChange(0); onSelectedOffsetChange(0) },
                onLabel = {
                    pickerYear = month.get(Calendar.YEAR)
                    showMonthPicker = true
                }
            )
            SectionCard(title = "日志日历", icon = "▦", accent = Amber) {
                LogHistoryCalendar(month, selectedDay, target, ownLogs) { day ->
                    onSelectedOffsetChange(dayOffsetFor(month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1, day))
                }
            }
            Spacer(Modifier.height(2.dp))
            if (target == "本人") {
                val log = ownLogs[selectedOffset]
                if (log != null) {
                    LogEntryCard(
                        log = log,
                        title = "${month.get(Calendar.MONTH) + 1}月${selectedDay}日",
                        canEdit = selectedInMonth && selectedOffset in -2..0,
                        frozen = selectedInMonth && selectedOffset < -2
                    )
                } else {
                    SectionCard(title = "${month.get(Calendar.MONTH) + 1}月${selectedDay}日", icon = "▤", accent = Blue) {
                        Text(
                            if (canEditSelectedDate) "暂无日志，可补写或新建"
                            else if (selectedOffset < -2) "该日期已冻结，不能补写"
                            else if (selectedOffset > 0) "未来日期不可填写"
                            else "暂无日志",
                            color = if (selectedOffset < -2) Muted else Ink,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (hasDemoLog(target, month, selectedDay)) {
                LogReadOnlyCard(target = target, day = selectedDay, month = month)
            } else {
                SectionCard(title = "${month.get(Calendar.MONTH) + 1}月${selectedDay}日", icon = "▤", accent = Blue) {
                    Text(
                        "暂无日志",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(76.dp))
        }

        if (canEditSelectedDate) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp)
                    .size(56.dp).clip(CircleShape).background(Amber)
                    .clickable { onEditSelectedDate(selectedOffset) },
                contentAlignment = Alignment.Center
            ) {
                Text(if (ownLogs[selectedOffset] == null) "＋" else "✎", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showMonthPicker) {
        QuickMonthPickerDialog(
            selectedYear = pickerYear,
            selectedMonth = month.get(Calendar.MONTH) + 1,
            onDismiss = { showMonthPicker = false },
            onYearChange = { pickerYear = it },
            onMonthSelected = { chosenMonth ->
                val nextShift = monthShiftFor(pickerYear, chosenMonth)
                onMonthShiftChange(nextShift)
                onSelectedOffsetChange(dayOffsetFor(pickerYear, chosenMonth, 1))
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun QuickMonthPickerDialog(
    selectedYear: Int,
    selectedMonth: Int,
    onDismiss: () -> Unit,
    onYearChange: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速选择年月", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { onYearChange(selectedYear - 1) }) { Text("‹", color = Blue, fontSize = 22.sp) }
                    Text("$selectedYear 年", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onYearChange(selectedYear + 1) }) { Text("›", color = Blue, fontSize = 22.sp) }
                }
                (0..3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..3).forEach { column ->
                            val month = row * 3 + column
                            val selected = month == selectedMonth
                            Box(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AmberPale else Color.Transparent)
                                    .clickable { onMonthSelected(month) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$month 月",
                                    color = if (selected) Amber else Ink,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun QuickDatePickerDialog(
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int, Int) -> Unit
) {
    var displayYear by remember(selectedYear) { mutableStateOf(selectedYear) }
    var displayMonth by remember(selectedMonth) { mutableStateOf(selectedMonth) }
    val firstOfMonth = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val leadingDays = (firstOfMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

    fun shiftMonth(amount: Int) {
        val shifted = (firstOfMonth.clone() as Calendar).apply { add(Calendar.MONTH, amount) }
        displayYear = shifted.get(Calendar.YEAR)
        displayMonth = shifted.get(Calendar.MONTH) + 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择日期", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { shiftMonth(-1) }) { Text("‹", color = Blue, fontSize = 22.sp) }
                    Text(
                        "${displayYear}年${displayMonth}月",
                        color = Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { shiftMonth(1) }) { Text("›", color = Blue, fontSize = 22.sp) }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                        Text(
                            weekday,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                            color = Muted,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                repeat(6) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val day = row * 7 + column - leadingDays + 1
                            if (day in 1..daysInMonth) {
                                val selected = displayYear == selectedYear && displayMonth == selectedMonth && day == selectedDay
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) AmberPale else Color.Transparent)
                                        .clickable { onDateSelected(displayYear, displayMonth, day) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        day.toString(),
                                        color = if (selected) Ink else Ink.copy(alpha = 0.82f),
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun logPersonTargets(viewer: DemoPerson): List<String> {
    val directUnits = when (viewer.role) {
        DemoRole.FOUNDER -> demoPeople.filter { it.parentUnit == viewer.unit }.map { it.unit }.toSet()
        DemoRole.DEPARTMENT_MANAGER -> demoPeople.filter { it.parentUnit == viewer.unit }.map { it.unit }.toSet()
        DemoRole.TEAM_LEADER -> setOf(viewer.unit)
        DemoRole.EMPLOYEE -> emptySet()
    }
    val descendants = when (viewer.role) {
        DemoRole.FOUNDER -> demoPeople.filter { it != viewer }
        DemoRole.DEPARTMENT_MANAGER -> demoPeople.filter { it != viewer && (it.parentUnit == viewer.unit || it.parentUnit in directUnits || it.unit in directUnits) }
        DemoRole.TEAM_LEADER -> demoPeople.filter { it != viewer && it.parentUnit == viewer.unit }
        DemoRole.EMPLOYEE -> emptyList()
    }
    return listOf("本人") + descendants.map { "${it.unit} · ${it.name}（${it.role.label}）" }
}

@Composable
private fun LogTargetPicker(target: String, options: List<String>, onTargetChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = options.size > 1) { showDialog = true },
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("查看对象", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            Text(target, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (options.size > 1) Text("⌄", color = Blue, fontSize = 18.sp)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("选择查看对象", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("搜索姓名、团队或部门", fontSize = 12.sp) },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    val filtered = options.filter { it.contains(keyword.trim(), ignoreCase = true) }
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        if (filtered.isEmpty()) {
                            Text("没有匹配的人员", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                        } else {
                            filtered.forEach { option ->
                                TextButton(onClick = { onTargetChange(option); showDialog = false; keyword = "" }, modifier = Modifier.fillMaxWidth()) {
                                    Text(option, color = if (option == target) Blue else Ink, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun SubordinateTaskTargetPicker(
    selected: String,
    people: List<DemoPerson>,
    onSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var nameQuery by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("查看对象", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            Text(selected, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄", color = Blue, fontSize = 18.sp)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; nameQuery = "" },
            title = { Text("选择查看对象", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameQuery,
                        onValueChange = { nameQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("输入员工姓名", fontSize = 12.sp) },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    val filteredPeople = people.filter { it.name.contains(nameQuery.trim(), ignoreCase = true) }
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        if (nameQuery.isBlank()) {
                            TextButton(
                                onClick = { onSelected("全部下属"); showDialog = false; nameQuery = "" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("全部下属", color = if (selected == "全部下属") Blue else Ink, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (filteredPeople.isEmpty()) {
                            Text("没有找到匹配姓名的员工", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                        } else {
                            filteredPeople.forEach { person ->
                                TextButton(
                                    onClick = { onSelected(person.optionLabel); showDialog = false; nameQuery = "" },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(person.optionLabel, color = if (person.optionLabel == selected) Blue else Ink, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false; nameQuery = "" }) { Text("关闭") } }
        )
    }
}

private fun filterDateParts(value: String?): Triple<Int, Int, Int> {
    if (value != null) {
        val parts = value.split("-").mapNotNull(String::toIntOrNull)
        if (parts.size == 3) return Triple(parts[0], parts[1], parts[2])
    }
    val today = Calendar.getInstance()
    return Triple(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
}

private fun formatFilterDate(value: String?): String {
    if (value == null) return "不限"
    val (year, month, day) = filterDateParts(value)
    return "${year}年${month}月${day}日"
}

private fun taskDeadlineDateKey(deadline: String): String? {
    val match = Regex("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日").find(deadline) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: 2026
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
}

@Composable
private fun TaskListFilterDialog(
    isSubordinateList: Boolean,
    people: List<DemoPerson>,
    initialScope: String,
    initialStatus: String,
    initialStartDate: String?,
    initialEndDate: String?,
    initialTarget: String,
    initialSubordinateMode: String,
    onDismiss: () -> Unit,
    onApply: (String, String, String?, String?, String, String) -> Unit
) {
    var scope by remember(isSubordinateList, initialScope) { mutableStateOf(initialScope) }
    var status by remember(initialStatus) { mutableStateOf(initialStatus) }
    var startDate by remember(initialStartDate) { mutableStateOf(initialStartDate) }
    var endDate by remember(initialEndDate) { mutableStateOf(initialEndDate) }
    var target by remember(initialTarget) { mutableStateOf(initialTarget) }
    var subordinateMode by remember(initialSubordinateMode) { mutableStateOf(initialSubordinateMode) }
    var employeeName by remember { mutableStateOf("") }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    val invalidRange = startDate != null && endDate != null && startDate!! > endDate!!

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选任务", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
                if (!isSubordinateList) {
                    Text("任务范围", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoices(listOf("全部", "我的任务", "我创建的"), scope, { it }, { scope = it })
                    Spacer(Modifier.height(13.dp))
                } else {
                    Text("任务范围", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoices(listOf("承担的任务", "创建的任务"), subordinateMode, { it }, { subordinateMode = it })
                    Spacer(Modifier.height(13.dp))
                }

                Text("截止日期", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(
                        onClick = { datePickerTarget = "start" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("起始：${formatFilterDate(startDate)}", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(
                        onClick = { datePickerTarget = "end" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("结束：${formatFilterDate(endDate)}", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { startDate = null; endDate = null }) { Text("清除日期", color = Blue, fontSize = 11.sp) }
                }
                if (invalidRange) {
                    Text("起始日期不能晚于结束日期", color = Rose, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                }

                Text("完成状态", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                SegmentedChoices(listOf("全部", "未完成", "已完成"), status, { it }, { status = it })

                if (isSubordinateList) {
                    Spacer(Modifier.height(13.dp))
                    Text("查看对象 · 仅限授权下属", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = employeeName,
                        onValueChange = { employeeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("输入员工姓名", fontSize = 11.sp) },
                        shape = RoundedCornerShape(13.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.heightIn(max = 145.dp).verticalScroll(rememberScrollState())) {
                        TextButton(
                            onClick = { target = "全部下属" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("全部下属", color = if (target == "全部下属") Blue else Ink, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                        }
                        val matches = people.filter { it.name.contains(employeeName.trim(), ignoreCase = true) }
                        if (matches.isEmpty() && employeeName.isNotBlank()) {
                            Text("没有找到匹配姓名的授权下属", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
                        } else {
                            matches.forEach { candidate ->
                                TextButton(
                                    onClick = { target = candidate.optionLabel },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(candidate.name, color = if (target == candidate.optionLabel) Blue else Ink, fontSize = 11.sp)
                                        Text("${candidate.role.label} · ${candidate.unit}", color = Muted, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(scope, status, startDate, endDate, target, subordinateMode) },
                enabled = !invalidRange
            ) { Text("应用", color = if (invalidRange) Muted else Blue) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    scope = if (isSubordinateList) scope else "我的任务"
                    subordinateMode = "承担的任务"
                    status = "全部"
                    startDate = null
                    endDate = null
                    target = "全部下属"
                    employeeName = ""
                }) { Text("重置", color = Muted) }
                TextButton(onClick = onDismiss) { Text("取消", color = Muted) }
            }
        }
    )

    datePickerTarget?.let { targetDateField ->
        val initialDate = filterDateParts(if (targetDateField == "start") startDate else endDate)
        QuickDatePickerDialog(
            selectedYear = initialDate.first,
            selectedMonth = initialDate.second,
            selectedDay = initialDate.third,
            onDismiss = { datePickerTarget = null },
            onDateSelected = { year, month, day ->
                val date = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
                if (targetDateField == "start") startDate = date else endDate = date
                datePickerTarget = null
            }
        )
    }
}

@Composable
private fun LogHistoryCalendar(
    month: Calendar,
    selectedDay: Int,
    target: String,
    ownLogs: Map<Int, DemoDailyLog>,
    onDaySelected: (Int) -> Unit
) {
    val first = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    Row(Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(it, modifier = Modifier.weight(1f), color = Muted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    Spacer(Modifier.height(7.dp))
    val cells = ((offset + days + 6) / 7) * 7
    for (row in 0 until cells / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0..6) {
                val day = row * 7 + col - offset + 1
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (day == selectedDay) AmberPale else Color.Transparent)
                        .clickable(enabled = day in 1..days) { onDaySelected(day) },
                    contentAlignment = Alignment.Center
                ) {
                    if (day in 1..days) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(day.toString(), color = Ink, fontSize = 12.sp, fontWeight = if (day == selectedDay) FontWeight.Bold else FontWeight.Normal)
                            Spacer(Modifier.height(3.dp))
                            val offset = dayOffsetFor(month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1, day)
                            val ownLog = ownLogs[offset]
                            val hasLog = if (target == "本人") ownLog?.status == "已提交" else hasDemoLog(target, month, day)
                            if (hasLog) Box(Modifier.size(5.dp).clip(CircleShape).background(Teal))
                            else Spacer(Modifier.height(5.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun hasDemoLog(target: String, month: Calendar, day: Int): Boolean =
    target.isNotBlank() && month.get(Calendar.YEAR) == 2026 && month.get(Calendar.MONTH) == Calendar.OCTOBER && day in 6..8

private data class DemoDailyLog(
    val completedWork: String,
    val nextDayPlan: String,
    val status: String
)

@Composable
private fun LogEntryCard(log: DemoDailyLog, title: String, canEdit: Boolean, frozen: Boolean) {
    SectionCard(
        title = "$title · ${log.status}",
        icon = "▤",
        accent = if (log.status == "已提交") Teal else Amber,
        trailing = if (frozen) "已冻结" else "本人"
    ) {
        Text("当日完成", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(log.completedWork.ifBlank { "暂无内容" }, color = Ink, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
        if (log.nextDayPlan.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("次日计划", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(log.nextDayPlan, color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (!canEdit && frozen) {
            Text("超过今天、昨天和前天的可编辑范围", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun LogReadOnlyCard(target: String, day: Int, month: Calendar) {
    SectionCard(title = "${month.get(Calendar.MONTH) + 1}月${day}日 · 已提交", icon = "▤", accent = Teal, trailing = target) {
        Text("当日完成", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text("完成产品需求评审，梳理了任务分发流程与页面交互，并整理评审结论。", color = Ink, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Text("次日计划", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text("完成页面交互稿并与团队确认。", color = Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AiMapScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        BrandHeader(showProfile = false)
        Spacer(Modifier.height(16.dp))
        PageHeading("AI 地图")
        Spacer(Modifier.height(17.dp))
        AiEntryCard(
            icon = "✦", title = "工作助手",
            description = "基于本人任务与已提交日志。", accent = Amber, tint = AmberPale,
            chips = listOf("任务咨询", "日志草稿", "本周总结")
        )
        Spacer(Modifier.height(13.dp))
        AiEntryCard(
            icon = "◎", title = "MBTI 性格探索",
            description = "轻量问答，仅供本人自我探索。", accent = Violet, tint = VioletPale,
            chips = listOf("轻量问答", "沟通偏好")
        )
        Spacer(Modifier.height(13.dp))
        AiEntryCard(
            icon = "♡", title = "情绪树洞",
            description = "一般性支持，不替代专业心理服务。", accent = Teal, tint = TealPale,
            chips = listOf("先听我说", "帮我理清", "给些建议")
        )
        Text("对话和测试结果仅本人可见", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp, top = 12.dp, bottom = 18.dp))
    }
}

@Composable
private fun AiEntryCard(icon: String, title: String, description: String, accent: Color, tint: Color, chips: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint, accent, 46)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Text("›", color = accent, fontSize = 25.sp, fontWeight = FontWeight.Light)
            }
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                chips.forEach { SoftChip(it, tint, accent) }
            }
        }
    }
}

@Composable
private fun MyScreen(onTasks: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        BrandHeader(showProfile = false)
        Spacer(Modifier.height(15.dp))
        PageHeading("我的")
        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = AmberPale)) {
            Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).clip(CircleShape).background(Paper), contentAlignment = Alignment.Center) {
                    Text("林", color = Amber, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("林经理", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("部门经理", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                    Text("产品部", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Text("›", color = Muted, fontSize = 25.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onTasks,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
        ) {
            Text("▣", fontSize = 18.sp)
            Spacer(Modifier.width(9.dp))
            Text("进入任务中心", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        SectionHeading("个人设置")
        Spacer(Modifier.height(9.dp))
        MenuRow("♙", "个人资料", Amber, AmberPale)
        MenuRow("♧", "通知设置", Blue, BluePale)
        MenuRow("?", "帮助与反馈", Violet, VioletPale)
        MenuRow("↪", "退出登录", Rose, RosePale)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MenuRow(icon: String, title: String, accent: Color, tint: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, accent, 38)
            Spacer(Modifier.width(12.dp))
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("›", color = Muted, fontSize = 22.sp)
        }
    }
}

@Composable
private fun TaskCenterScreen(
    person: DemoPerson,
    tasks: List<WorkflowTask>,
    onPersonChange: (DemoPerson) -> Unit,
    onTasksChange: (List<WorkflowTask>) -> Unit,
    onBack: () -> Unit,
    initialTaskId: Int? = null,
    readOnlyDetail: Boolean = false
) {
    val role = person.role
    val actorName = person.name
    var section by remember { mutableStateOf("我的任务") }
    var source by remember { mutableStateOf("全部") }
    var detailTaskId by remember(initialTaskId) { mutableStateOf(initialTaskId) }
    var showCreateForm by remember { mutableStateOf(false) }
    var assignParentId by remember { mutableStateOf<Int?>(null) }

    val nextRole = when (role) {
        DemoRole.FOUNDER -> DemoRole.DEPARTMENT_MANAGER
        DemoRole.DEPARTMENT_MANAGER -> DemoRole.TEAM_LEADER
        DemoRole.TEAM_LEADER -> DemoRole.EMPLOYEE
        DemoRole.EMPLOYEE -> null
    }
    val recipientOptions = (demoPeople.filter { it.role == nextRole && it.parentUnit == person.unit } + person).distinct()
    val visibleSections = when (role) {
        DemoRole.DEPARTMENT_MANAGER -> listOf("我的任务", "我派发的")
        DemoRole.FOUNDER, DemoRole.TEAM_LEADER -> listOf("我的任务", "我派发的")
        DemoRole.EMPLOYEE -> listOf("我的任务")
    }
    val sourceOptions = when (role) {
        DemoRole.FOUNDER -> listOf("全部", "自主创建")
        DemoRole.DEPARTMENT_MANAGER -> listOf("全部", "上级分配", "他人派发", "部门自主")
        DemoRole.TEAM_LEADER -> listOf("全部", "上级分配", "团队自主")
        DemoRole.EMPLOYEE -> emptyList()
    }
    val assignedTasks = tasks.filter {
        it.assigneeRole == role && it.assigneeName == actorName &&
            (source == "全部" || taskSourceLabel(it, role, actorName, tasks) == source)
    }
    val dispatchedTasks = tasks.filter {
        it.senderRole == role && it.senderName == actorName &&
            (it.parentId != null || it.assigneeRole != role || it.assigneeName != actorName)
    }
    val detailTask = tasks.firstOrNull { it.id == detailTaskId }
    val assignmentParent = tasks.firstOrNull { it.id == assignParentId }

    fun saveTask(updated: WorkflowTask) {
        onTasksChange(tasks.map { if (it.id == updated.id) updated else it })
    }

    fun openTask(task: WorkflowTask) {
        if (!readOnlyDetail && task.status == WorkflowStatus.UNFINISHED && !task.started && task.assigneeRole == role && task.assigneeName == actorName) {
            saveTask(task.copy(started = true))
        }
        detailTaskId = task.id
    }

    val title = when {
        showCreateForm -> "创建任务"
        assignmentParent != null -> "拆分并派发"
        detailTask != null -> "任务详情"
        else -> "任务中心"
    }
    val backAction = when {
        showCreateForm -> { { showCreateForm = false } }
        assignmentParent != null -> { { assignParentId = null } }
        detailTask != null -> {
            {
                if (initialTaskId != null && detailTaskId == initialTaskId) onBack() else detailTaskId = null
            }
        }
        else -> onBack
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        ChildTopBar(title, backAction)

        when {
            showCreateForm -> WorkflowTaskForm(
                onCancel = { showCreateForm = false },
                onSave = { taskTitle, description, deadline, criteria ->
                    val nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
                    val created = WorkflowTask(
                        id = nextId,
                        parentId = null,
                        title = taskTitle,
                        description = description,
                        acceptanceCriteria = criteria,
                        senderRole = role,
                        senderName = actorName,
                        assigneeRole = role,
                        assigneeName = actorName,
                        deadline = deadline,
                        origin = WorkflowOrigin.INDEPENDENT,
                        status = WorkflowStatus.UNFINISHED
                    )
                    onTasksChange(tasks + created)
                    showCreateForm = false
                    detailTaskId = created.id
                }
            )

            assignmentParent != null && role != DemoRole.EMPLOYEE -> WorkflowChildTaskForm(
                role = role,
                parent = assignmentParent,
                actorName = actorName,
                recipients = recipientOptions,
                existingChildren = tasks.filter { it.parentId == assignmentParent.id },
                onCancel = { assignParentId = null },
                onCreate = { recipient, taskTitle, description, deadline, criteria ->
                    val parent = requireNotNull(assignmentParent)
                    val nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
                    val child = WorkflowTask(
                        id = nextId,
                        parentId = parent.id,
                        title = taskTitle,
                        description = description,
                        acceptanceCriteria = criteria,
                        senderRole = role,
                        senderName = actorName,
                        assigneeRole = recipient.role,
                        assigneeName = recipient.name,
                        deadline = deadline,
                        origin = parent.origin,
                        status = WorkflowStatus.UNFINISHED
                    )
                    onTasksChange(tasks + child)
                }
            )

            detailTask != null -> {
                WorkflowTaskDetail(
                    role = role,
                    actorName = actorName,
                    task = detailTask,
                    parent = tasks.firstOrNull { it.id == detailTask.parentId },
                    children = tasks.filter { it.parentId == detailTask.id },
                    allTasks = tasks,
                    onOpenTask = { child -> openTask(child) },
                    onTaskChange = { saveTask(it) },
                    onAssign = { assignParentId = detailTask.id },
                    readOnly = readOnlyDetail
                )
            }

            else -> {
                ObjectPicker(
                    label = "演示身份",
                    selected = person.optionLabel,
                    options = demoPeople.map { it.optionLabel },
                    onSelected = { selected ->
                        demoPeople.firstOrNull { it.optionLabel == selected }?.let {
                            onPersonChange(it)
                            section = "我的任务"
                            source = "全部"
                        }
                    }
                )
                SegmentedChoices(
                    values = visibleSections,
                    selected = section,
                    label = { it },
                    onSelected = { section = it }
                )
                Spacer(Modifier.height(14.dp))
                when (section) {
                    "我的任务" -> {
                        SectionHeading("${actorName}的任务")
                        if (role != DemoRole.EMPLOYEE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                sourceOptions.forEach { label ->
                                    ChoiceChip(label, source == label) { source = label }
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                        }
                        if (assignedTasks.isEmpty()) {
                            EmptyTaskHint("暂无任务")
                        } else {
                            assignedTasks.forEach { task ->
                                WorkflowTaskCard(
                                    task = task,
                                    leading = if (task.parentId == null) "主任务" else "子任务",
                                    sourceLabel = taskSourceLabel(task, role, actorName, tasks)
                                ) { openTask(task) }
                            }
                        }
                        if (role != DemoRole.EMPLOYEE) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { showCreateForm = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                Text("＋  创建本级任务", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    "我派发的" -> {
                        SectionHeading("我直接派发的")
                        if (dispatchedTasks.isEmpty()) {
                            EmptyTaskHint("暂无派发记录")
                        } else {
                            dispatchedTasks.forEach { task ->
                                WorkflowTaskCard(
                                    task = task,
                                    leading = if (task.assigneeName == actorName) "本人子任务" else task.assigneeName,
                                    sourceLabel = taskSourceLabel(task, role, actorName, tasks)
                                ) { openTask(task) }
                            }
                        }
                    }

                    else -> EmptyTaskHint("暂无任务")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WorkflowTaskForm(
    onCancel: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var taskTitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("10月20日") }
    var criteria by remember { mutableStateOf("") }
    Column {
        SectionCard("任务信息", "▤", Blue) {
            OutlinedTextField(taskTitle, { taskTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("任务标题 *") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("任务说明 *") }, minLines = 2, maxLines = 4)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(deadline, { deadline = it }, modifier = Modifier.fillMaxWidth(), label = { Text("截止时间 *") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(criteria, { criteria = it }, modifier = Modifier.fillMaxWidth(), label = { Text("完成 / 验收标准 *") }, minLines = 2, maxLines = 4)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(taskTitle.trim(), description.trim(), deadline.trim(), criteria.trim()) },
            enabled = taskTitle.isNotBlank() && description.isNotBlank() && deadline.isNotBlank() && criteria.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
        ) { Text("创建任务", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消", color = Muted) }
    }
}

@Composable
private fun WorkflowChildTaskForm(
    role: DemoRole,
    parent: WorkflowTask,
    actorName: String,
    recipients: List<DemoPerson>,
    existingChildren: List<WorkflowTask>,
    onCancel: () -> Unit,
    onCreate: (DemoPerson, String, String, String, String) -> Unit
) {
    var draftVersion by remember(parent.id) { mutableStateOf(0) }
    var selectedRecipientLabel by remember(parent.id) { mutableStateOf(recipients.first().optionLabel) }
    val selectedRecipient = recipients.firstOrNull { it.optionLabel == selectedRecipientLabel } ?: recipients.first()
    var taskTitle by remember(parent.id, draftVersion) { mutableStateOf("") }
    var description by remember(parent.id, draftVersion) { mutableStateOf("") }
    var deadline by remember(parent.id, draftVersion) { mutableStateOf(parent.deadline) }
    var criteria by remember(parent.id, draftVersion) { mutableStateOf("") }
    Column {
        SectionCard("拆分上级任务", "↳", Teal) {
            Text("主任务：${parent.title}", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("派发人：$actorName · ${role.label}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (existingChildren.isNotEmpty()) {
            SectionCard("已拆分子任务", "✓", Teal, "${existingChildren.size} 项") {
                existingChildren.forEach { child ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(child.title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("${child.assigneeName} · ${child.assigneeRole.label} · ${child.status.label}", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        SectionCard("选择本条子任务责任人", "♙", Blue) {
            ObjectPicker(
                label = "责任人",
                selected = selectedRecipient.optionLabel,
                options = recipients.map { it.optionLabel },
                onSelected = { selectedRecipientLabel = it }
            )
        }
        Spacer(Modifier.height(10.dp))
        SectionCard("子任务内容", "▤", Blue) {
            OutlinedTextField(taskTitle, { taskTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("子任务标题 *") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("任务说明 *") }, minLines = 2, maxLines = 4)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(deadline, { deadline = it }, modifier = Modifier.fillMaxWidth(), label = { Text("截止时间 *") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(criteria, { criteria = it }, modifier = Modifier.fillMaxWidth(), label = { Text("完成 / 验收标准 *") }, minLines = 2, maxLines = 4)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onCreate(selectedRecipient, taskTitle.trim(), description.trim(), deadline.trim(), criteria.trim())
                draftVersion += 1
            },
            enabled = taskTitle.isNotBlank() && description.isNotBlank() && deadline.isNotBlank() && criteria.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
        ) { Text("添加并派发给${selectedRecipient.name}", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("完成拆分，返回任务详情", color = Muted) }
    }
}

@Composable
private fun WorkflowTaskDetail(
    role: DemoRole,
    actorName: String,
    task: WorkflowTask,
    parent: WorkflowTask?,
    children: List<WorkflowTask>,
    allTasks: List<WorkflowTask>,
    onOpenTask: (WorkflowTask) -> Unit,
    onTaskChange: (WorkflowTask) -> Unit,
    onAssign: () -> Unit,
    readOnly: Boolean = false
) {
    var showProgressDialog by remember(task.id) { mutableStateOf(false) }
    var showSubmitDialog by remember(task.id) { mutableStateOf(false) }
    var showReturnDialog by remember(task.id) { mutableStateOf(false) }
    val isAssignee = task.assigneeRole == role && task.assigneeName == actorName
    val isDispatcher = task.senderRole == role && task.senderName == actorName && !isAssignee
    val isSelfAssignedChild = task.parentId != null && isAssignee && task.senderRole == role && task.senderName == actorName
    val isSelfCreatedIndependentRoot =
        (role == DemoRole.FOUNDER || role == DemoRole.DEPARTMENT_MANAGER || role == DemoRole.TEAM_LEADER) &&
            isAssignee && task.parentId == null && task.origin == WorkflowOrigin.INDEPENDENT
    val waitingForRequiredChildren = isSelfCreatedIndependentRoot && children.any { it.status != WorkflowStatus.COMPLETED }
    val canWork = isAssignee && !isSelfAssignedChild && task.status == WorkflowStatus.UNFINISHED && !task.awaitingAcceptance
    val statusColor = if (task.status == WorkflowStatus.COMPLETED) Teal else Blue

    Column {
        if (readOnly) {
            Text("下属任务 · 只读", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        SectionCard(task.title, "▤", statusColor, task.status.label) {
            Text(task.description, color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(11.dp))
            WorkflowInfoLine("派发人", "${task.senderName} · ${task.senderRole.label}")
            WorkflowInfoLine("责任人", "${task.assigneeName} · ${task.assigneeRole.label}")
            WorkflowInfoLine("截止时间", task.deadline)
            if (parent != null) WorkflowInfoLine("上级任务", parent.title)
            Text("完成 / 验收标准", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp, bottom = 3.dp))
            Text(task.acceptanceCriteria, color = Ink, fontSize = 11.sp, lineHeight = 16.sp)
        }

        if (!isSelfAssignedChild) {
            SectionCard("当前进度", "▥", Blue, "${task.progress}%") {
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Line)) {
                    Box(Modifier.fillMaxWidth(task.progress.coerceIn(0, 100) / 100f).height(7.dp).clip(CircleShape).background(Blue))
                }
                if (task.reviewNote.isNotBlank()) {
                    Text("上次验收意见：${task.reviewNote}", color = Rose, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 9.dp))
                }
                if (task.awaitingAcceptance) {
                    Text("已提交，等待直接派发者验收", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Text("完成说明：${task.completionNote}\n交付结果：${task.resultLocation}", color = Ink, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (!isSelfAssignedChild && (task.updates.isNotEmpty() || isAssignee)) {
            SectionCard("进展记录", "◷", Violet) {
                if (task.updates.isEmpty()) Text("暂无进展", color = Muted, fontSize = 10.sp)
                task.updates.asReversed().forEach { update ->
                    TaskChangeLine("${update.percent}% · ${update.date}", update.note, Blue)
                }
            }
        }

        if (children.isNotEmpty() || isAssignee && role != DemoRole.EMPLOYEE) {
            SectionCard("拆分情况", "↳", Teal, "${children.size} 项") {
                if (children.isEmpty()) Text("尚未拆分", color = Muted, fontSize = 10.sp)
                children.forEach { child ->
                    WorkflowTaskCard(
                        task = child,
                        leading = child.assigneeName,
                        sourceLabel = taskSourceLabel(child, role, actorName, allTasks)
                    ) { onOpenTask(child) }
                }
            }
        }

        if (!readOnly && canWork) {
            Button(
                onClick = { showProgressDialog = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Paper)
            ) { Text("更新任务进度") }
            if (!isSelfCreatedIndependentRoot) {
                Button(
                    onClick = { showSubmitDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
                ) { Text("提交验收") }
            }
        }

        if (!readOnly && isAssignee && role != DemoRole.EMPLOYEE && task.status != WorkflowStatus.COMPLETED && !task.awaitingAcceptance) {
            OutlinedButton(onClick = onAssign, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(14.dp)) {
                Text("＋  拆分并派发子任务", color = Ink)
            }
        }

        if (!readOnly && isAssignee && task.senderRole == role && task.senderName == actorName && (task.origin == WorkflowOrigin.INDEPENDENT || isSelfAssignedChild) && task.status != WorkflowStatus.COMPLETED) {
            if (waitingForRequiredChildren) {
                Text(
                    "请先完成并验收所有必要子任务，再结束这项部门工作。",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = { onTaskChange(task.copy(status = WorkflowStatus.COMPLETED, reviewNote = "本人标记完成")) },
                enabled = !waitingForRequiredChildren,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Paper, disabledContainerColor = Line, disabledContentColor = Muted)
            ) { Text(if (isSelfCreatedIndependentRoot) "标记任务完成" else "标记本人任务完成") }
        }

        if (!readOnly && isDispatcher && task.awaitingAcceptance) {
            Button(
                onClick = { onTaskChange(task.copy(status = WorkflowStatus.COMPLETED, awaitingAcceptance = false, reviewNote = "验收通过")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Paper)
            ) { Text("验收通过") }
            OutlinedButton(onClick = { showReturnDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("退回修改", color = Rose)
            }
        }

        if (task.status == WorkflowStatus.COMPLETED) {
            val completionTitle = if (task.reviewNote == "本人标记完成") "完成情况" else "验收结果"
            SectionCard(completionTitle, "✓", Teal) { Text(task.reviewNote.ifBlank { "任务已完成" }, color = Ink, fontSize = 11.sp) }
        }
    }

    if (showProgressDialog) {
        var percent by remember(task.id) { mutableStateOf(task.progress.toString()) }
        var note by remember(task.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showProgressDialog = false },
            title = { Text("更新进度") },
            text = {
                Column {
                    OutlinedTextField(percent, { percent = it.filter(Char::isDigit).take(3) }, label = { Text("完成百分比") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("本次进展说明") }, minLines = 2, maxLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = percent.toIntOrNull()?.coerceIn(0, 99)
                    if (value != null && note.isNotBlank()) {
                        onTaskChange(task.copy(status = WorkflowStatus.UNFINISHED, started = true, progress = value, updates = task.updates + WorkflowProgress(value, note.trim(), "今天"), reviewNote = ""))
                        showProgressDialog = false
                    }
                }) { Text("保存进度") }
            },
            dismissButton = { TextButton(onClick = { showProgressDialog = false }) { Text("取消") } }
        )
    }

    if (showSubmitDialog) {
        var completion by remember(task.id) { mutableStateOf("") }
        var result by remember(task.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubmitDialog = false },
            title = { Text("提交完成") },
            text = {
                Column {
                    Text("提交后任务仍为未完成；验收前暂不能继续更新进度。", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(completion, { completion = it }, label = { Text("完成说明 *") }, minLines = 2, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(result, { result = it }, label = { Text("交付结果 / 成果位置 *") }, minLines = 1, maxLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (completion.isNotBlank() && result.isNotBlank()) {
                        onTaskChange(task.copy(status = WorkflowStatus.UNFINISHED, awaitingAcceptance = true, progress = 100, completionNote = completion.trim(), resultLocation = result.trim(), reviewNote = ""))
                        showSubmitDialog = false
                    }
                }) { Text("提交验收") }
            },
            dismissButton = { TextButton(onClick = { showSubmitDialog = false }) { Text("继续编辑") } }
        )
    }

    if (showReturnDialog) {
        var reason by remember(task.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text("退回修改") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("退回原因（必填）") }, minLines = 2, maxLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    if (reason.isNotBlank()) {
                        onTaskChange(task.copy(status = WorkflowStatus.UNFINISHED, awaitingAcceptance = false, reviewNote = reason.trim()))
                        showReturnDialog = false
                    }
                }) { Text("确认退回", color = Rose) }
            },
            dismissButton = { TextButton(onClick = { showReturnDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun WorkflowTaskCard(task: WorkflowTask, leading: String, sourceLabel: String, onClick: () -> Unit) {
    val isSelfAssignedChild = task.parentId != null && task.senderRole == task.assigneeRole && task.senderName == task.assigneeName
    val accent = if (task.status == WorkflowStatus.COMPLETED) Teal else Blue
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$leading · 截止${task.deadline} · $sourceLabel", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                SoftChip(task.status.label, accent.copy(alpha = 0.12f), accent)
                if (task.status != WorkflowStatus.COMPLETED && !isSelfAssignedChild) Text("${task.progress}%", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun TaskCenterV2Screen(
    person: DemoPerson,
    tasks: List<WorkflowTask>,
    onPersonChange: (DemoPerson) -> Unit,
    onTasksChange: (List<WorkflowTask>) -> Unit,
    onBack: () -> Unit,
    initialTaskId: Int? = null,
    readOnlyDetail: Boolean = false
) {
    var section by remember { mutableStateOf("我的任务") }
    var showApprovalCenter by remember { mutableStateOf(false) }
    var approvalSection by remember { mutableStateOf("待你审批") }
    var taskScopeFilter by remember { mutableStateOf("我的任务") }
    var taskStatusFilter by remember { mutableStateOf("全部") }
    var deadlineStartFilter by remember { mutableStateOf<String?>(null) }
    var deadlineEndFilter by remember { mutableStateOf<String?>(null) }
    var subordinateObjectFilter by remember { mutableStateOf("全部下属") }
    var subordinateTaskMode by remember { mutableStateOf("承担的任务") }
    var showTaskFilter by remember { mutableStateOf(false) }
    var detailId by remember(initialTaskId) { mutableStateOf(initialTaskId) }
    var showCreate by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Int?>(null) }
    var readOnlySelected by remember { mutableStateOf(false) }
    val detail = tasks.firstOrNull { it.id == detailId }
    val editing = tasks.firstOrNull { it.id == editId }
    val supervisor = directSupervisor(person)
    val canReview = demoPeople.any { directSupervisor(it)?.name == person.name }
    val canInspectDescendants = demoPeople.any { isBelowInHierarchy(it, person) }
    val approvalSections = buildList {
        if (canReview) add("待你审批")
        if (supervisor != null) add("待上级审批")
    }
    val myTasks = tasks.filter {
        !it.cancelled && it.approvalStatus == ApprovalStatus.APPROVED &&
            it.assigneeRole == person.role && it.assigneeName == person.name
    }
    val createdTasks = tasks.filter {
        it.senderRole == person.role && it.senderName == person.name &&
            it.approvalStatus == ApprovalStatus.APPROVED
    }
    val awaitingSupervisor = tasks.filter {
        it.senderRole == person.role && it.senderName == person.name &&
            !it.cancelled &&
            (it.approvalStatus == ApprovalStatus.PENDING || it.approvalStatus == ApprovalStatus.RETURNED)
    }
    val awaitingReview = tasks.filter {
        !it.cancelled &&
        it.approvalStatus == ApprovalStatus.PENDING &&
            it.approverRole == person.role && it.approverName == person.name
    }
    val descendantNames = demoPeople.filter { isBelowInHierarchy(it, person) }.map { it.name }.toSet()
    val descendantPeople = demoPeople.filter { isBelowInHierarchy(it, person) }
    val activeApprovalSection = approvalSection.takeIf { it in approvalSections }
        ?: approvalSections.firstOrNull().orEmpty()
    val descendantAssignedTasks = tasks.filter {
        it.approvalStatus == ApprovalStatus.APPROVED &&
            it.assigneeName in descendantNames
    }.distinctBy { it.id }
    val descendantCreatedTasks = tasks.filter {
        it.approvalStatus == ApprovalStatus.APPROVED &&
            it.senderName in descendantNames
    }.distinctBy { it.id }

    fun updateTask(updated: WorkflowTask) {
        onTasksChange(tasks.map { if (it.id == updated.id) updated else it })
    }

    fun createOrUpdate(
        assignee: DemoPerson,
        title: String,
        description: String,
        deadline: String,
        deliverables: String,
        association: String
    ) {
        val approver = directSupervisor(person)
        if (editing != null) {
            updateTask(
                editing.copy(
                    title = title,
                    description = description,
                    deadline = deadline,
                    expectedDeliverables = deliverables,
                    acceptanceCriteria = deliverables,
                    associationNote = association,
                    assigneeRole = assignee.role,
                    assigneeName = assignee.name,
                    approvalStatus = if (approver == null) ApprovalStatus.APPROVED else ApprovalStatus.PENDING,
                    approverRole = approver?.role,
                    approverName = approver?.name.orEmpty(),
                    approvalNote = ""
                )
            )
        } else {
            val nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
            val task = WorkflowTask(
                id = nextId,
                parentId = null,
                title = title,
                description = description,
                acceptanceCriteria = deliverables,
                senderRole = person.role,
                senderName = person.name,
                assigneeRole = assignee.role,
                assigneeName = assignee.name,
                deadline = deadline,
                origin = WorkflowOrigin.INDEPENDENT,
                status = WorkflowStatus.UNFINISHED,
                approvalStatus = if (approver == null) ApprovalStatus.APPROVED else ApprovalStatus.PENDING,
                approverRole = approver?.role,
                approverName = approver?.name.orEmpty(),
                expectedDeliverables = deliverables,
                associationNote = association
            )
            onTasksChange(tasks + task)
        }
        showCreate = false
        editId = null
        detailId = null
        if (approver == null) {
            showApprovalCenter = false
            section = "我创建的"
        } else {
            showApprovalCenter = true
            approvalSection = "待上级审批"
        }
    }

    val pageTitle = when {
        showCreate -> if (editing != null) "修改并重新提交" else "创建任务"
        detail != null -> "任务详情"
        showApprovalCenter -> "审批中心"
        section == "下属任务" -> "下属任务"
        else -> "任务中心"
    }
    val back = when {
        showCreate -> { { showCreate = false; editId = null } }
        detail != null -> {
            {
                if (initialTaskId != null && detailId == initialTaskId) onBack() else detailId = null
                readOnlySelected = false
            }
        }
        showApprovalCenter -> { { showApprovalCenter = false } }
        section == "下属任务" -> { { section = "我的任务"; subordinateObjectFilter = "全部下属" } }
        else -> onBack
    }

    val isSubordinateList = !showApprovalCenter && section == "下属任务"
    val ordinaryTasks = when (taskScopeFilter) {
        "全部" -> (myTasks + createdTasks).distinctBy { it.id }
        "我创建的" -> createdTasks
        else -> myTasks
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 86.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = Ink, fontSize = 30.sp, modifier = Modifier.clickable(onClick = back).padding(end = 13.dp))
                Text(pageTitle, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (!showCreate && detail == null && !showApprovalCenter) {
                    if (approvalSections.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.clickable { showApprovalCenter = true },
                            shape = RoundedCornerShape(11.dp),
                            color = VioletPale
                        ) {
                            Text("审批中心", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp))
                        }
                    }
                    if (canInspectDescendants && !isSubordinateList) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            modifier = Modifier.clickable {
                                section = "下属任务"
                                subordinateObjectFilter = "全部下属"
                                subordinateTaskMode = "承担的任务"
                                taskStatusFilter = "全部"
                                deadlineStartFilter = null
                                deadlineEndFilter = null
                            },
                            shape = RoundedCornerShape(11.dp),
                            color = BluePale
                        ) {
                            Text("下属任务", color = Blue, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            when {
                showCreate -> StandaloneTaskForm(
                    person = person,
                    initialTask = editing,
                    onCancel = { showCreate = false; editId = null },
                    onSave = ::createOrUpdate
                )

                detail != null -> WorkflowTaskDetailV2(
                    person = person,
                    task = detail,
                    readOnly = readOnlyDetail || readOnlySelected,
                    onTaskChange = ::updateTask,
                    onApprove = { updateTask(detail.copy(approvalStatus = ApprovalStatus.APPROVED, approvalNote = "审批通过")) },
                    onReturnApproval = { reason -> updateTask(detail.copy(approvalStatus = ApprovalStatus.RETURNED, approvalNote = reason)) },
                    onEditResubmit = { editId = detail.id; showCreate = true },
                    onResubmit = { updateTask(detail.copy(approvalStatus = ApprovalStatus.PENDING, approvalNote = "")) }
                )

                else -> {
                    if (!showApprovalCenter) {
                        ObjectPicker(
                            label = "演示身份",
                            selected = person.optionLabel,
                            options = demoPeople.map { it.optionLabel },
                            onSelected = { selected ->
                                demoPeople.firstOrNull { it.optionLabel == selected }?.let {
                                    onPersonChange(it)
                                    section = "我的任务"
                                    showApprovalCenter = false
                                    taskScopeFilter = "我的任务"
                                    taskStatusFilter = "全部"
                                    deadlineStartFilter = null
                                    deadlineEndFilter = null
                                    subordinateObjectFilter = "全部下属"
                                    subordinateTaskMode = "承担的任务"
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    } else if (approvalSections.size > 1) {
                        SegmentedChoices(approvalSections, activeApprovalSection, { it }, { approvalSection = it })
                        Spacer(Modifier.height(14.dp))
                    }

                    val selectedTasks = if (showApprovalCenter) {
                        if (activeApprovalSection == "待上级审批") awaitingSupervisor else awaitingReview
                    } else if (isSubordinateList) {
                        if (subordinateTaskMode == "创建的任务") descendantCreatedTasks else descendantAssignedTasks
                    } else {
                        ordinaryTasks
                    }
                    val sectionTitle = if (showApprovalCenter) {
                        if (activeApprovalSection == "待上级审批") "等待直属上级审批" else "直属下级提交的任务"
                    } else if (isSubordinateList) {
                        "下属${subordinateTaskMode} · 只读"
                    } else {
                        when (taskScopeFilter) {
                            "全部" -> "全部任务"
                            "我创建的" -> "我创建的任务"
                            else -> "分配给我的任务"
                        }
                    }

                    if (showApprovalCenter) {
                        SectionHeading(sectionTitle)
                        Spacer(Modifier.height(9.dp))
                    } else {
                        val dateSummary = when {
                            deadlineStartFilter == null && deadlineEndFilter == null -> "截止日期不限"
                            else -> "${formatFilterDate(deadlineStartFilter)}—${formatFilterDate(deadlineEndFilter)}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 9.dp)) {
                            Column(Modifier.weight(1f)) {
                                SectionHeading(sectionTitle)
                                Text(
                                    if (isSubordinateList) "$subordinateTaskMode · $subordinateObjectFilter · $dateSummary · $taskStatusFilter"
                                    else "$dateSummary · $taskStatusFilter",
                                    color = Muted,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(onClick = { showTaskFilter = true }, shape = RoundedCornerShape(12.dp)) {
                                Text("筛选", color = Blue, fontSize = 11.sp)
                            }
                        }
                    }

                    val selectedDescendant = descendantPeople.firstOrNull { it.optionLabel == subordinateObjectFilter }
                    val visibleTasks = selectedTasks
                        .filter { task ->
                            showApprovalCenter || taskStatusFilter == "全部" || task.status.label == taskStatusFilter
                        }
                        .filter { task ->
                            if (showApprovalCenter || (deadlineStartFilter == null && deadlineEndFilter == null)) true
                            else {
                                val key = taskDeadlineDateKey(task.deadline)
                                key != null && (deadlineStartFilter == null || key >= deadlineStartFilter!!) &&
                                    (deadlineEndFilter == null || key <= deadlineEndFilter!!)
                            }
                        }
                        .filter { task ->
                            showApprovalCenter || !isSubordinateList || subordinateObjectFilter == "全部下属" ||
                                selectedDescendant?.name == if (subordinateTaskMode == "创建的任务") task.senderName else task.assigneeName
                        }
                    if (visibleTasks.isEmpty()) {
                        EmptyTaskHint(if (showApprovalCenter) "暂无审批任务" else if (isSubordinateList) "没有符合条件的任务" else "暂无任务")
                    } else {
                        visibleTasks.sortedBy { it.deadline }.forEach { task ->
                            val sub = buildString {
                                append("负责人：${task.assigneeName} · 截止${task.deadline}")
                                if (!showApprovalCenter && task.approvalStatus != ApprovalStatus.APPROVED) append(" · ${task.approvalStatus.label}")
                                if (task.cancelled) append(" · 已归档")
                            }
                            val cardStatus = if (showApprovalCenter) task.approvalStatus.label else task.status.label
                            val cardAccent = when {
                                showApprovalCenter && task.approvalStatus == ApprovalStatus.RETURNED -> Rose
                                showApprovalCenter -> Amber
                                task.status == WorkflowStatus.COMPLETED -> Teal
                                else -> Blue
                            }
                            TaskCardV2(task.title, sub, cardStatus, cardAccent) {
                                if (task.assigneeRole == person.role && task.assigneeName == person.name &&
                                    task.status == WorkflowStatus.UNFINISHED && !task.started &&
                                    task.approvalStatus == ApprovalStatus.APPROVED
                                ) {
                                    updateTask(task.copy(started = true))
                                }
                                detailId = task.id
                                readOnlySelected = !showApprovalCenter && isSubordinateList
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (!showCreate && detail == null && !showApprovalCenter) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp)
                    .size(56.dp).clip(CircleShape).background(Amber)
                    .clickable { showCreate = true; editId = null },
                contentAlignment = Alignment.Center
            ) {
                Text("＋", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showTaskFilter) {
        TaskListFilterDialog(
            isSubordinateList = isSubordinateList,
            people = descendantPeople,
            initialScope = taskScopeFilter,
            initialStatus = taskStatusFilter,
            initialStartDate = deadlineStartFilter,
            initialEndDate = deadlineEndFilter,
            initialTarget = subordinateObjectFilter,
            initialSubordinateMode = subordinateTaskMode,
            onDismiss = { showTaskFilter = false },
            onApply = { scope, status, startDate, endDate, target, subordinateMode ->
                taskScopeFilter = scope
                taskStatusFilter = status
                deadlineStartFilter = startDate
                deadlineEndFilter = endDate
                subordinateObjectFilter = target
                subordinateTaskMode = subordinateMode
                showTaskFilter = false
            }
        )
    }
}

@Composable
private fun StandaloneTaskForm(
    person: DemoPerson,
    initialTask: WorkflowTask?,
    onCancel: () -> Unit,
    onSave: (DemoPerson, String, String, String, String, String) -> Unit
) {
    val recipientOptions = taskAssigneeOptions(person)
    var recipientLabel by remember(initialTask?.id, person.name) {
        mutableStateOf((recipientOptions.firstOrNull { it.name == initialTask?.assigneeName } ?: recipientOptions.firstOrNull())?.optionLabel.orEmpty())
    }
    val recipient = recipientOptions.firstOrNull { it.optionLabel == recipientLabel } ?: recipientOptions.firstOrNull()
    var title by remember(initialTask?.id) { mutableStateOf(initialTask?.title.orEmpty()) }
    var details by remember(initialTask?.id) { mutableStateOf(initialTask?.description.orEmpty()) }
    var deadline by remember(initialTask?.id) { mutableStateOf(initialTask?.deadline ?: "10月20日") }
    var deliverables by remember(initialTask?.id) { mutableStateOf(initialTask?.expectedDeliverables.orEmpty()) }
    var association by remember(initialTask?.id) { mutableStateOf(initialTask?.associationNote.orEmpty()) }
    val supervisor = directSupervisor(person)
    Column {
        SectionCard("任务信息", "▤", Blue) {
            Text("创建人：${person.name} · ${person.role.label}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 9.dp))
            if (recipient != null) {
                ObjectPicker("负责人 *", recipient.optionLabel, recipientOptions.map { it.optionLabel }) { recipientLabel = it }
            }
            if (person.role == DemoRole.FOUNDER) {
                Text("可将任务派发给公司任一成员。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (person.role == DemoRole.DEPARTMENT_MANAGER) {
                Text("可派发给本人、本部门团队长或其他部门经理。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("任务标题 *") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(details, { details = it }, modifier = Modifier.fillMaxWidth(), label = { Text("任务详情 *") }, minLines = 2, maxLines = 4)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(deadline, { deadline = it }, modifier = Modifier.fillMaxWidth(), label = { Text("截止时间 *") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(deliverables, { deliverables = it }, modifier = Modifier.fillMaxWidth(), label = { Text("完成时需提交的材料（选填）") }, minLines = 2, maxLines = 3)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(association, { association = it }, modifier = Modifier.fillMaxWidth(), label = { Text("关联说明（选填）") }, minLines = 1, maxLines = 2)
        }
        if (supervisor != null) {
            Text("提交后由直属上级${supervisor.name}审批；通过后任务才会进入负责人的任务列表。", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
        } else {
            Text("创始人为最高层级，创建的任务无需审批。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        Button(
            onClick = { recipient?.let { onSave(it, title.trim(), details.trim(), deadline.trim(), deliverables.trim(), association.trim()) } },
            enabled = recipient != null && title.isNotBlank() && details.isNotBlank() && deadline.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
        ) { Text(if (initialTask != null) "保存并重新提交审批" else if (supervisor != null) "提交审批" else "创建任务", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消", color = Muted) }
    }
}

@Composable
private fun WorkflowTaskDetailV2(
    person: DemoPerson,
    task: WorkflowTask,
    readOnly: Boolean,
    onTaskChange: (WorkflowTask) -> Unit,
    onApprove: () -> Unit,
    onReturnApproval: (String) -> Unit,
    onEditResubmit: () -> Unit,
    onResubmit: () -> Unit
) {
    var showUpdate by remember(task.id) { mutableStateOf(false) }
    var showCompletion by remember(task.id) { mutableStateOf(false) }
    var showAcceptanceReturn by remember(task.id) { mutableStateOf(false) }
    var showApprovalReturn by remember(task.id) { mutableStateOf(false) }
    var showCancel by remember(task.id) { mutableStateOf(false) }
    val isCreator = task.senderRole == person.role && task.senderName == person.name
    val isAssignee = task.assigneeRole == person.role && task.assigneeName == person.name
    val isApprover = task.approverRole == person.role && task.approverName == person.name && task.approvalStatus == ApprovalStatus.PENDING
    val isDispatcher = isCreator
    val canExecute = isAssignee && task.approvalStatus == ApprovalStatus.APPROVED && !task.cancelled && task.status == WorkflowStatus.UNFINISHED && !task.awaitingAcceptance
    val isOwnTask = isAssignee && isCreator
    val statusColor = if (task.status == WorkflowStatus.COMPLETED) Teal else Blue

    Column {
        if (readOnly) Text("下属任务 · 只读", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
        SectionCard(task.title, "▤", statusColor, task.status.label) {
            Text(task.description, color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(9.dp))
            WorkflowInfoLine("创建人", "${task.senderName} · ${task.senderRole.label}")
            WorkflowInfoLine("负责人", "${task.assigneeName} · ${task.assigneeRole.label}")
            WorkflowInfoLine("截止时间", task.deadline)
            WorkflowInfoLine("审批状态", task.approvalStatus.label)
            if (task.expectedDeliverables.isNotBlank()) {
                Text("预期提交材料", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp, bottom = 3.dp))
                Text(task.expectedDeliverables, color = Ink, fontSize = 11.sp, lineHeight = 16.sp)
            }
            if (task.associationNote.isNotBlank()) {
                Text("关联说明", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp, bottom = 3.dp))
                Text(task.associationNote, color = Ink, fontSize = 11.sp, lineHeight = 16.sp)
            }
            if (task.approvalNote.isNotBlank()) Text("审批意见：${task.approvalNote}", color = if (task.approvalStatus == ApprovalStatus.RETURNED) Rose else Teal, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
            if (task.reviewNote.isNotBlank()) Text("验收意见：${task.reviewNote}", color = Rose, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
            if (task.cancelled) Text("已取消归档：${task.cancellationNote}", color = Rose, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
        }

        if (task.awaitingAcceptance) {
            SectionCard("待直接派发者验收", "◷", Amber) {
                Text("完成说明：${task.completionNote}", color = Ink, fontSize = 11.sp, lineHeight = 16.sp)
                if (task.resultLocation.isNotBlank()) Text("成果说明：${task.resultLocation}", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        if (task.textUpdates.isNotEmpty() || canExecute) {
            SectionCard("文字更新记录", "◷", Violet) {
                if (task.textUpdates.isEmpty()) Text("暂无更新记录", color = Muted, fontSize = 10.sp)
                task.textUpdates.asReversed().forEach { update ->
                    TaskChangeLine("${update.author} · ${update.date}", update.note, Violet)
                }
            }
        }

        if (!readOnly && isApprover) {
            Button(onClick = onApprove, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Paper)) { Text("审批通过") }
            OutlinedButton(onClick = { showApprovalReturn = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("退回创建人修改", color = Rose) }
        }
        if (!readOnly && isCreator && task.status == WorkflowStatus.UNFINISHED && !task.awaitingAcceptance && !task.cancelled) {
            OutlinedButton(onClick = onEditResubmit, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(14.dp)) { Text("修改任务并重新提交", color = Blue) }
            if (task.approvalStatus == ApprovalStatus.RETURNED) {
                Button(onClick = onResubmit, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)) { Text("不修改，重新提交审批") }
            }
        }
        if (!readOnly && canExecute) {
            OutlinedButton(onClick = { showUpdate = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(14.dp)) { Text("添加文字更新", color = Blue) }
            Button(onClick = { showCompletion = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)) { Text(if (isOwnTask) "标记任务完成" else "提交完成成果") }
        }
        if (!readOnly && isOwnTask && task.approvalStatus == ApprovalStatus.APPROVED && task.status == WorkflowStatus.UNFINISHED && !task.cancelled) {
            // Self-assigned work uses a completion marker instead of self-acceptance.
            if (canExecute) Text("本人负责的任务完成后由本人标记，不需要自我验收。", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (!readOnly && isDispatcher && !isAssignee && task.awaitingAcceptance) {
            Button(onClick = { onTaskChange(task.copy(status = WorkflowStatus.COMPLETED, awaitingAcceptance = false, reviewNote = "验收通过")) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Paper)) { Text("验收通过") }
            OutlinedButton(onClick = { showAcceptanceReturn = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(14.dp)) { Text("退回补充", color = Rose) }
        }
        if (!readOnly && isDispatcher && task.status == WorkflowStatus.UNFINISHED && !task.cancelled) {
            OutlinedButton(onClick = { showCancel = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("取消此任务", color = Rose) }
        }
        if (task.status == WorkflowStatus.COMPLETED) SectionCard("完成记录", "✓", Teal) {
            Text(task.completionNote.ifBlank { task.reviewNote.ifBlank { "任务已完成" } }, color = Ink, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }

    if (showUpdate) {
        var note by remember(task.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showUpdate = false }, title = { Text("添加文字更新") }, text = {
            OutlinedTextField(note, { note = it }, label = { Text("本次进展说明 *") }, minLines = 2, maxLines = 4)
        }, confirmButton = { TextButton(onClick = {
            if (note.isNotBlank()) onTaskChange(task.copy(textUpdates = task.textUpdates + WorkflowTextUpdate(note.trim(), person.name, "今天")))
            showUpdate = false
        }) { Text("保存更新") } }, dismissButton = { TextButton(onClick = { showUpdate = false }) { Text("取消") } })
    }
    if (showCompletion) {
        var completion by remember(task.id) { mutableStateOf("") }
        var result by remember(task.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showCompletion = false }, title = { Text(if (isOwnTask) "标记完成" else "提交完成成果") }, text = {
            Column {
                OutlinedTextField(completion, { completion = it }, label = { Text("完成说明 *") }, minLines = 2, maxLines = 4)
                if (task.expectedDeliverables.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("需提交：${task.expectedDeliverables}", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    OutlinedTextField(result, { result = it }, label = { Text("成果说明 / 位置 *") }, minLines = 1, maxLines = 3)
                }
            }
        }, confirmButton = { TextButton(onClick = {
            if (completion.isNotBlank() && (task.expectedDeliverables.isBlank() || result.isNotBlank())) {
                if (isOwnTask) onTaskChange(task.copy(status = WorkflowStatus.COMPLETED, completionNote = completion.trim(), resultLocation = result.trim()))
                else onTaskChange(task.copy(awaitingAcceptance = true, completionNote = completion.trim(), resultLocation = result.trim()))
                showCompletion = false
            }
        }) { Text(if (isOwnTask) "标记完成" else "提交验收") } }, dismissButton = { TextButton(onClick = { showCompletion = false }) { Text("取消") } })
    }
    if (showApprovalReturn) {
        var reason by remember(task.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showApprovalReturn = false }, title = { Text("退回创建人修改") }, text = {
            OutlinedTextField(reason, { reason = it }, label = { Text("审批退回原因 *") }, minLines = 2, maxLines = 4)
        }, confirmButton = { TextButton(onClick = { if (reason.isNotBlank()) onReturnApproval(reason.trim()); showApprovalReturn = false }) { Text("确认退回", color = Rose) } }, dismissButton = { TextButton(onClick = { showApprovalReturn = false }) { Text("取消") } })
    }
    if (showAcceptanceReturn) {
        var reason by remember(task.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAcceptanceReturn = false }, title = { Text("退回补充") }, text = {
            OutlinedTextField(reason, { reason = it }, label = { Text("验收退回原因 *") }, minLines = 2, maxLines = 4)
        }, confirmButton = { TextButton(onClick = { if (reason.isNotBlank()) onTaskChange(task.copy(awaitingAcceptance = false, reviewNote = reason.trim())); showAcceptanceReturn = false }) { Text("确认退回", color = Rose) } }, dismissButton = { TextButton(onClick = { showAcceptanceReturn = false }) { Text("取消") } })
    }
    if (showCancel) {
        var reason by remember(task.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showCancel = false }, title = { Text("取消任务") }, text = {
            OutlinedTextField(reason, { reason = it }, label = { Text("取消原因 *") }, minLines = 2, maxLines = 4)
        }, confirmButton = { TextButton(onClick = { if (reason.isNotBlank()) onTaskChange(task.copy(cancelled = true, cancellationNote = reason.trim())); showCancel = false }) { Text("确认取消", color = Rose) } }, dismissButton = { TextButton(onClick = { showCancel = false }) { Text("返回") } })
    }
}

@Composable
private fun TaskCardV2(title: String, subtitle: String, status: String, accent: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(7.dp))
            SoftChip(status, accent.copy(alpha = 0.12f), accent)
        }
    }
}

@Composable
private fun WorkflowInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.width(58.dp))
        Text(value, color = Ink, fontSize = 10.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyTaskHint(title: String) {
    SectionCard(title, "○", Muted) {}
}

@Composable
private fun TaskListCard(title: String, subtitle: String, status: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(status, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChildTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("‹", color = Ink, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 13.dp))
        Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ObjectPicker(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Paper),
            border = BorderStroke(1.dp, Line)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.width(10.dp))
                Text(selected, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("⌄", color = Blue, fontSize = 16.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 12.sp) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedChoices(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFECEFF3)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        values.forEach { value ->
            val active = value == selected
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (active) AmberPale else Color.Transparent)
                    .clickable { onSelected(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(value), color = if (active) Ink else Muted, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Ink else Paper,
        border = if (selected) null else BorderStroke(1.dp, Line)
    ) {
        Text(label, color = if (selected) Paper else Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

@Composable
private fun PeriodBar(label: String, centerAction: String?, onPrevious: () -> Unit, onNext: () -> Unit, onCenter: () -> Unit, onLabel: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("‹", color = Ink, fontSize = 25.sp, modifier = Modifier.clickable(onClick = onPrevious).padding(horizontal = 5.dp))
        Text(label + if (onLabel != null) "  ⌄" else "", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).then(if (onLabel != null) Modifier.clickable(onClick = onLabel) else Modifier), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (centerAction != null) {
            TextButton(onClick = onCenter) { Text(centerAction, color = Blue, fontSize = 10.sp) }
        }
        Text("›", color = Ink, fontSize = 25.sp, modifier = Modifier.clickable(onClick = onNext).padding(horizontal = 5.dp))
    }
}

@Composable
private fun SectionCard(title: String, icon: String, accent: Color, trailing: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                IconBadge(icon, accent.copy(alpha = 0.12f), accent, 32)
                Spacer(Modifier.width(9.dp))
                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (trailing != null) Text(trailing, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            content()
        }
    }
}

@Composable
private fun TaskChangeLine(title: String, detail: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun PageHeading(title: String) {
    Text(title, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun IconBadge(icon: String, background: Color, foreground: Color, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(background), contentAlignment = Alignment.Center) {
        Text(icon, color = foreground, fontSize = (size * 0.43f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SoftChip(text: String, tint: Color, accent: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = tint) {
        Text(text, color = accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), maxLines = 1)
    }
}

@Composable
private fun BottomNavigation(selected: RootTab, onSelect: (RootTab) -> Unit) {
    Surface(color = Paper, shadowElevation = 9.dp) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            RootTab.values().forEach { tab ->
                val active = tab == selected
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(tab.symbol, color = if (active) Amber else Muted, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                    Text(tab.title, color = if (active) Amber else Muted, fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
                    if (active) Box(Modifier.padding(top = 2.dp).width(18.dp).height(2.dp).clip(CircleShape).background(Amber))
                }
            }
        }
    }
}

@Composable
private fun BottomSpacer() = Spacer(Modifier.height(12.dp))

private fun demoDate(offset: Int): Calendar = Calendar.getInstance().apply {
    clear()
    set(DemoBaseYear, DemoBaseMonth, 9, 12, 0, 0)
    add(Calendar.DAY_OF_MONTH, offset)
}

private fun monthCalendar(offset: Int): Calendar = demoDate(0).apply {
    set(Calendar.DAY_OF_MONTH, 1)
    add(Calendar.MONTH, offset)
}

private fun weekLabel(shift: Int): String {
    val mondayOffset = shift * 7 - 4
    val start = demoDate(mondayOffset)
    val end = demoDate(mondayOffset + 6)
    val fmt = SimpleDateFormat("M月d日", Locale.CHINA)
    return "${fmt.format(start.time)} — ${fmt.format(end.time)}"
}

private fun dayLabel(offset: Int): String {
    val day = demoDate(offset)
    val names = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    return "${names[day.get(Calendar.DAY_OF_WEEK) - 1]} · ${SimpleDateFormat("M月d日", Locale.CHINA).format(day.time)}"
}

private fun monthShiftFor(year: Int, month: Int): Int =
    (year - DemoBaseYear) * 12 + (month - (DemoBaseMonth + 1))

private fun dayOffsetFor(year: Int, month: Int, day: Int): Int {
    val utc = TimeZone.getTimeZone("UTC")
    val selected = Calendar.getInstance(utc).apply {
        clear()
        set(year, month - 1, day, 12, 0, 0)
    }
    val base = Calendar.getInstance(utc).apply {
        clear()
        set(DemoBaseYear, DemoBaseMonth, 9, 12, 0, 0)
    }
    return ((selected.timeInMillis - base.timeInMillis) / (24L * 60L * 60L * 1000L)).toInt()
}

private fun weekShiftForDay(dayOffset: Int): Int = (mondayOffsetFor(dayOffset) + 4) / 7
