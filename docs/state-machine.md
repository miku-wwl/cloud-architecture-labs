# 状态机

```text
InitializeRelease
  -> SetCanary5 -> WaitEvaluation5 -> Evaluate5 -> Choice
       pass -> SetCanary25 -> WaitEvaluation25 -> Evaluate25 -> Choice
                   pass -> SetCanary50 -> WaitEvaluation50 -> Evaluate50 -> Choice
                               pass -> FinalizePromoted -> PROMOTED
                               fail -> SetCanary0 -> FinalizeRolledBack
                   fail -> SetCanary0 -> FinalizeRolledBack
       fail -> SetCanary0 -> FinalizeRolledBack
```

`InitializeRelease` 会通过条件更新抢占发布。重复投递的 EventBridge 事件会进入 `DuplicateEventIgnored`，不会改变路由。每个阶段都为临时性的 Lambda 任务失败配置了重试策略，同时保证权重操作具备幂等性。
