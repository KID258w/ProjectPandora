"""王世鑫（WSX24）中期测试：头像校验与上传读取。

Issue #42 分工：
- 第 19 项：头像校验 —— AppAvatarUploadSerializer.validate_avatar()（TC-WSX-331～340）
- 第 20 项：头像上传与读取 —— AppProfileAvatarView.put() / get()（TC-WSX-341～347）

需求依据：AppAvatarUploadSerializer 实现约定（当前需求文档未单列头像章节，以下以
已确认的实现约定为预期依据，并已在本报告第 6 节登记为“需求待确认”）
> 头像不超过 5 MiB；仅支持 JPEG、PNG、WebP；单边不超过 4096，面积不超过 16_000_000 像素。

用例设计方法：等价类划分 + 边界值分析。

等价类划分
| 维度        | 有效等价类               | 无效等价类                          |
| 图片格式     | JPEG、PNG、WebP         | GIF 等其他格式；非图片内容             |
| 文件大小     | ≤ 5 MiB                | > 5 MiB                            |
| 图片尺寸     | 单边 ≤ 4096 且面积 ≤ 16,000,000 | 单边 > 4096；面积超限          |
| 读取权限     | 业务用户读取本人头像       | 未设置头像（404）；管理员访问（403）    |

边界值分析
| 边界             | 边界前        | 边界值                | 边界后                    |
| 文件大小          | 5 MiB − 1    | 5 MiB（通过）          | 5 MiB + 1（拒绝）          |
| 图片宽度          | 4095         | 4096（通过）           | 4097（拒绝）               |
| 图片面积          | 16,000,000   | 16,000,000（通过）     | 16,004,000（拒绝）         |
"""

import io

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from PIL import Image
from rest_framework.test import APIClient

from .api_serializers import AppAvatarUploadSerializer
from .models import AppOperationAuditLog, Department, Team, User

PASSWORD = "Pandora-test-938!"
MAX_BYTES = 5 * 1024 * 1024


def image_bytes(fmt="PNG", size=(8, 8), color=(10, 120, 200)):
    buffer = io.BytesIO()
    Image.new("RGB", size, color).save(buffer, format=fmt)
    return buffer.getvalue()


class FakeUpload(io.BytesIO):
    """仅承载 validate_avatar 需要的 size 属性与文件读写能力。"""

    def __init__(self, data, size):
        super().__init__(data)
        self.size = size


class AvatarValidationTests(TestCase):
    """AppAvatarUploadSerializer.validate_avatar() 单元测试（第 19 项）。"""

    serializer_class = AppAvatarUploadSerializer

    def check(self, fmt="PNG", size=(8, 8), reported_size=None):
        data = image_bytes(fmt=fmt, size=size)
        upload = FakeUpload(data, reported_size if reported_size is not None else len(data))
        return self.serializer_class().validate_avatar(upload)

    # ---------- 正常输入 ----------

    def test_png_accepted(self):
        """TC-WSX-331 正常：PNG 图片通过校验。"""
        self.assertIsNotNone(self.check("PNG"))

    def test_jpeg_accepted(self):
        """TC-WSX-332 正常：JPEG 图片通过校验。"""
        self.assertIsNotNone(self.check("JPEG"))

    def test_webp_accepted(self):
        """TC-WSX-333 正常：WebP 图片通过校验。"""
        self.assertIsNotNone(self.check("WEBP"))

    # ---------- 边界值 ----------

    def test_boundary_exactly_5mib_accepted(self):
        """TC-WSX-334 边界：文件大小恰好 5 MiB（上界）通过。"""
        self.assertIsNotNone(self.check("PNG", reported_size=MAX_BYTES))

    def test_boundary_one_byte_over_5mib_rejected(self):
        """TC-WSX-335 边界：文件大小 5 MiB + 1 字节被拒绝。"""
        with self.assertRaises(Exception) as ctx:
            self.check("PNG", reported_size=MAX_BYTES + 1)
        self.assertIn("5 MiB", str(ctx.exception.detail))

    def test_boundary_width_4096_accepted(self):
        """TC-WSX-336 边界：宽度 4096（单边上界）通过。"""
        self.assertIsNotNone(self.check("PNG", size=(4096, 1)))

    def test_boundary_width_4097_rejected(self):
        """TC-WSX-337 边界：宽度 4097（单边上界外）被拒绝。"""
        with self.assertRaises(Exception) as ctx:
            self.check("PNG", size=(4097, 1))
        self.assertIn("4096", str(ctx.exception.detail))

    def test_boundary_area_at_limit_accepted(self):
        """TC-WSX-338 边界：面积恰好 16,000,000 像素通过。"""
        self.assertIsNotNone(self.check("PNG", size=(4000, 4000)))

    def test_boundary_area_over_limit_rejected(self):
        """TC-WSX-339 边界：面积超过 16,000,000 像素被拒绝。"""
        with self.assertRaises(Exception) as ctx:
            self.check("PNG", size=(4000, 4001))
        self.assertIn("4096", str(ctx.exception.detail))

    # ---------- 异常输入 ----------

    def test_gif_rejected(self):
        """TC-WSX-340 异常：GIF 格式不在白名单内被拒绝。"""
        with self.assertRaises(Exception) as ctx:
            self.check("GIF")
        self.assertIn("仅支持", str(ctx.exception.detail))

    def test_non_image_content_rejected(self):
        """TC-WSX-341 异常：非图片内容被拒绝。"""
        upload = FakeUpload("this is not an image".encode(), 20)
        with self.assertRaises(Exception) as ctx:
            self.serializer_class().validate_avatar(upload)
        self.assertIn("无法读取该图片", str(ctx.exception.detail))


class AvatarViewTests(TestCase):
    """AppProfileAvatarView 接口集成测试（第 20 项）。"""

    def setUp(self):
        self.department = Department.objects.create(name="研发部")
        self.team = Team.objects.create(name="后端组", department=self.department)
        self.founder = User.objects.create_user(
            username="founder", password=PASSWORD, display_name="创始人",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.FOUNDER,
        )
        self.manager = User.objects.create_user(
            username="manager", password=PASSWORD, display_name="经理",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.DEPARTMENT_MANAGER,
            department=self.department, manager=self.founder,
        )
        # 头像接口要求组织链完整：团队长的直属上级必须是本部门经理
        self.user = User.objects.create_user(
            username="13800000001", password=PASSWORD, display_name="张三",
            account_type=User.AccountType.BUSINESS, business_role=User.Role.TEAM_LEADER,
            department=self.department, team=self.team, manager=self.manager,
        )
        self.client = APIClient()

    def upload(self, fmt="PNG"):
        file = SimpleUploadedFile(f"avatar.{fmt.lower()}", image_bytes(fmt), content_type=f"image/{fmt.lower()}")
        return self.client.put("/api/v1/app/me/avatar/", {"avatar": file}, format="multipart")

    def test_put_saves_avatar(self):
        """TC-WSX-342 正常：上传 PNG 返回 204 且写入数据库。"""
        self.client.force_authenticate(user=self.user)
        response = self.upload("PNG")
        self.assertEqual(response.status_code, 204, response.data)
        self.user.refresh_from_db()
        self.assertEqual(self.user.avatar_content_type, "image/png")
        self.assertTrue(self.user.avatar_data)

    def test_get_returns_image_with_safety_headers(self):
        """TC-WSX-343 正常：读取头像返回二进制及 nosniff、no-store 头。"""
        self.client.force_authenticate(user=self.user)
        self.upload("PNG")
        response = self.client.get("/api/v1/app/me/avatar/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response["Content-Type"], "image/png")
        self.assertEqual(response["X-Content-Type-Options"], "nosniff")
        self.assertEqual(response["Cache-Control"], "private, no-store")

    def test_get_without_avatar_returns_404(self):
        """TC-WSX-344 边界：未设置头像时读取返回 404。"""
        self.client.force_authenticate(user=self.user)
        self.assertEqual(self.client.get("/api/v1/app/me/avatar/").status_code, 404)

    def test_replace_avatar_updates_content_type(self):
        """TC-WSX-345 正常：再次上传不同格式时覆盖旧头像与类型。"""
        self.client.force_authenticate(user=self.user)
        self.upload("PNG")
        self.upload("JPEG")
        self.user.refresh_from_db()
        self.assertEqual(self.user.avatar_content_type, "image/jpeg")

    def test_upload_writes_audit_without_binary(self):
        """TC-WSX-346 正常：头像写入审计仅记录字段名，不保存二进制内容。"""
        self.client.force_authenticate(user=self.user)
        self.upload("PNG")
        log = AppOperationAuditLog.objects.get(module=AppOperationAuditLog.Module.PROFILE)
        self.assertEqual(log.changed_fields, ["avatar"])
        self.assertIsNone(log.before_data)
        self.assertIsNone(log.after_data)

    def test_admin_cannot_upload_avatar(self):
        """TC-WSX-347 异常：管理员不能调用 App 头像接口。"""
        admin = User.objects.create_superuser(username="admin", password=PASSWORD)
        self.client.force_authenticate(user=admin)
        response = self.upload("PNG")
        self.assertEqual(response.status_code, 403)
