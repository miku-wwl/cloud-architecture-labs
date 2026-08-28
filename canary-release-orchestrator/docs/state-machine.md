# 状态机

```text
SetCanary5 -> WaitEvaluation5 -> Evaluate5 -> Choice
       pass -> SetCanary25 -> WaitEvaluation25 -> Evaluate25 -> Choice
                   pass -> SetCanary50 -> WaitEvaluation50 -> Evaluate50 -> Choice
                               pass -> FinalizePromoted -> PROMOTED
                               fail -> SetCanary0 -> FinalizeRolledBack
                   fail -> SetCanary0 -> FinalizeRolledBack
       fail -> SetCanary0 -> FinalizeRolledBack
```

每个阶段都由 `set_weight`、`Wait`、`evaluate_health` 和 `Choice` 组成。评估失败时统一将 candidate 调整为 0%，再由 `finalize_release` 完成 `ROLLED_BACK`。这是一个用于学习 AWS 编排的单发布 demo，不包含重复事件去重和并发发布锁。
