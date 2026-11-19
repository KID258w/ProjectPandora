"""王世鑫（WSX24）中期测试：身份证号归一化。

Issue #42 分工：账号认证、部门团队与员工管理、个人资料与密码、头像、组织关系。
本文件覆盖 User.normalize_id_number()。

需求依据：docs/backend/account-provisioning.md「业务用户（仅管理员）」
> 身份证号必填，最长 64 个字符；后端去除首尾空格并将字母统一为大写后再保存及检查全局唯一性。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度       | 有效等价类                     | 无效等价类                        |
| 字母大小写  | 全大写 / 全小写                | 大小写混排（同样归一化为大写）        |
| 首尾空白   | 无空白 / 有首尾空白（应去除）    | 纯空白串（去除后为空串）             |
| 存在性     | 有值                          | None（无身份证号）                 |
| 内部空白   | 无内部空白                     | 有内部空白（只 strip 首尾，不处理内部）|

边界值分析
| 边界              | 边界前   | 边界值    | 边界后                  |
| 空值              | —        | None     | 空串 ""                 |
| 空白串            | " "      | "   "    | " a "（去空白后非空）     |
| 字段长度上限 64    | 63 字符  | 64 字符   | 65 字符（由字段校验拦截）  |

用例编号按 Issue #42 的“功能编号”分段分配，完整对照表见
`docs/test/wsx-midterm-tests.md` 第 2 节。本文件属第 8 项（员工录入和初始密码），
区间 TC-WSX-111～140；normalize_id_number() 占 111～119。
"""

from django.test import SimpleTestCase

from .models import User


class NormalizeIdNumberTests(SimpleTestCase):
    """User.normalize_id_number() 单元测试。

    纯静态方法，不访问数据库，因此使用 SimpleTestCase。
    """

    # ---------- 正常输入 ----------

    def test_lowercase_x_normalized_to_uppercase(self):
        """TC-WSX-111 正常：末尾小写 x 归一化为大写 X。"""
        self.assertEqual(
            User.normalize_id_number("11010119900101123x"),
            "11010119900101123X",
        )

    def test_surrounding_whitespace_stripped(self):
        """TC-WSX-112 正常：去除首尾空格。"""
        self.assertEqual(
            User.normalize_id_number(" 11010119900101123X "),
            "11010119900101123X",
        )

    def test_strip_and_upper_applied_together(self):
        """TC-WSX-113 正常：首尾空格与小写同时出现时，先去空格再转大写。"""
        self.assertEqual(
            User.normalize_id_number(" 11010119900101123x "),
            "11010119900101123X",
        )

    def test_already_canonical_value_unchanged(self):
        """TC-WSX-114 正常：已规范化的值原样返回（幂等）。"""
        canonical = "11010119900101123X"
        self.assertEqual(User.normalize_id_number(canonical), canonical)

    # ---------- 边界值 ----------

    def test_boundary_none_returns_none(self):
        """TC-WSX-115 边界：None 输入原样返回 None（无身份证号场景）。"""
        self.assertIsNone(User.normalize_id_number(None))

    def test_boundary_empty_string_returns_empty(self):
        """TC-WSX-116 边界：空串返回空串。"""
        self.assertEqual(User.normalize_id_number(""), "")

    def test_boundary_whitespace_only_returns_empty(self):
        """TC-WSX-117 边界：纯空白串去除后为空串，用于空值判定。"""
        self.assertEqual(User.normalize_id_number("   "), "")

    def test_boundary_max_length_64_accepted(self):
        """TC-WSX-118 边界：字段上限 64 字符不被截断或拒绝。"""
        value = "9" * 64
        self.assertEqual(User.normalize_id_number(value), value)
        self.assertEqual(len(User.normalize_id_number(value)), 64)

    # ---------- 异常 / 特殊输入 ----------

    def test_internal_whitespace_preserved(self):
        """TC-WSX-119 异常：仅处理首尾空白，内部空白不被去除。"""
        self.assertEqual(
            User.normalize_id_number("110 10119900101123X"),
            "110 10119900101123X",
        )
