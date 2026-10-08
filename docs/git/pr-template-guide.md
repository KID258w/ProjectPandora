# GitHub Pull Request（PR）提交模板说明

本文说明创建 PR 时如何填写标题、正文、目标分支和检查项。当前文件是团队填写指南和可复制模板；它不会自动显示在 GitHub 的新建 PR 页面。

## 创建 PR 前

1. 将个人功能分支推送到 GitHub。
2. 进入仓库 **Pull requests → New pull request**。
3. 选择目标分支（`base`）和来源分支（`compare`）。
4. 点击 **Create pull request** 后，在 **Title** 和 **Description** 中填写信息。

功能 PR 的目标分支为 `dev`；阶段发布 PR 的目标分支为 `main`。提交前确认来源、目标分支没有选反。

## 标题建议

用简短动词描述改动，例如：

- `feat(task): add team task assignment`
- `fix(log): preserve draft content when editing`
- `docs: clarify weekly work view rules`

团队也可以使用中文标题；关键是让审阅者一眼看出改动内容。

## 功能 PR：个人分支合入 `dev`

创建功能 PR 时，在正文中关联 Issue 和 OpenSpec change。GitHub 正文里提到 `#123` 可以建立交叉引用；如果需要在 Issue 的 **Development** 区域显式显示 PR，可在侧栏手动关联。

```markdown
## 变更内容
- <描述改动>

## 关联
- Issue：#123（替换为实际编号）
- OpenSpec change：`<change-name>`

## 测试
- [ ] 测试已完成
- 测试方式与结果：

## 自检
- [ ] 改动符合 Issue 验收标准
- [ ] 已检查异常和边界场景
- [ ] 未提交密钥、个人数据或无关文件
- [ ] 如涉及 UI，已附截图或录屏

## 审阅
- [ ] 已请求至少一位非作者成员审阅
```

功能 PR 只关联 Issue，不写 `Closes #123`。因为功能 PR 的目标是 `dev`，此时阶段工作尚未发布；关闭关键字应留给最终的 `dev → main` 发布 PR。

## 阶段发布 PR：`dev` 合入 `main`

阶段测试通过后创建发布 PR。描述发布内容、测试结论和遗留问题，并在正文末尾逐项添加需要随本阶段关闭的 Issue：

```markdown
## 阶段内容
- <发布内容>

## 测试结论
- [ ] 集成测试通过
- [ ] 阻塞缺陷已处理
- 结果/报告：

## 遗留问题
- 无 / <说明>

## 关联并关闭的 Issue
Closes #123
```

将 `123` 替换为实际 Issue 编号。GitHub 的 `Closes #123` 等关闭关键字会在 PR 合入仓库默认分支时自动关闭 Issue。确保 `main` 是仓库默认分支，且 Issue 对应的工作确实已完成后再使用。

## 审阅与合并

- PR 作者先完成自查和测试，再请求审阅；作者不批准自己的 PR。
- 至少一名非作者成员审阅并批准后，才能合入 `dev` 或 `main`。
- 若审阅提出修改，在原 PR 分支继续提交，确认检查通过后再合并。
- 功能 PR 合入 `dev` 后，团队在 `dev` 完成集成测试；阶段发布 PR 合入 `main` 后，同步 `dev` 到 `main` 的最新提交。
