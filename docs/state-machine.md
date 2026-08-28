# State machine

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

`InitializeRelease` conditionally claims the release. A duplicate EventBridge delivery enters `DuplicateEventIgnored` and does not change routing. Each stage has a retry policy for transient Lambda task failures, while the weight operation is idempotent.
