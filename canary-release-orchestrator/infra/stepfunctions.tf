locals {
  canary_workflow_definition = jsonencode({
    Comment = "Canary release: set weight, wait, evaluate, then promote or roll back"
    StartAt = "SetCanary5"
    States = {
      SetCanary5 = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["set_weight"].arn
          Payload = {
            "releaseId.$"        = "$.releaseId"
            "serviceName.$"      = "$.serviceName"
            "stableVersion.$"    = "$.stableVersion"
            "candidateVersion.$" = "$.candidateVersion"
            percentage           = 5
            stage                = "CANARY_5"
          }
        }
        ResultSelector = { "stageStartedAtMs.$" = "$.Payload.stageStartedAtMs" }
        ResultPath     = "$.stage5"
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "WaitEvaluation5"
      }

      WaitEvaluation5 = {
        Type        = "Wait"
        SecondsPath = "$.evaluationWindowSeconds"
        Next        = "Evaluate5"
      }

      Evaluate5 = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["evaluate_health"].arn
          Payload = {
            "candidateVersion.$" = "$.candidateVersion"
            "stageStartMs.$"     = "$.stage5.stageStartedAtMs"
            "windowSeconds.$"    = "$.evaluationWindowSeconds"
          }
        }
        ResultSelector = {
          "healthy.$"          = "$.Payload.healthy"
          "requestCount.$"     = "$.Payload.requestCount"
          "errorRate.$"        = "$.Payload.errorRate"
          "averageLatencyMs.$" = "$.Payload.averageLatencyMs"
          "reasons.$"          = "$.Payload.reasons"
          "failureCode.$"      = "$.Payload.failureCode"
          "metricSource.$"     = "$.Payload.metricSource"
        }
        ResultPath = "$.evaluation5"
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "Healthy5?"
      }

      "Healthy5?" = {
        Type = "Choice"
        Choices = [{
          Variable      = "$.evaluation5.healthy"
          BooleanEquals = true
          Next          = "SetCanary25"
        }]
        Default = "RollbackFrom5"
      }

      SetCanary25 = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["set_weight"].arn
          Payload = {
            "releaseId.$"        = "$.releaseId"
            "serviceName.$"      = "$.serviceName"
            "stableVersion.$"    = "$.stableVersion"
            "candidateVersion.$" = "$.candidateVersion"
            percentage           = 25
            stage                = "CANARY_25"
          }
        }
        ResultSelector = { "stageStartedAtMs.$" = "$.Payload.stageStartedAtMs" }
        ResultPath     = "$.stage25"
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "WaitEvaluation25"
      }

      WaitEvaluation25 = {
        Type        = "Wait"
        SecondsPath = "$.evaluationWindowSeconds"
        Next        = "Evaluate25"
      }

      Evaluate25 = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["evaluate_health"].arn
          Payload = {
            "candidateVersion.$" = "$.candidateVersion"
            "stageStartMs.$"     = "$.stage25.stageStartedAtMs"
            "windowSeconds.$"    = "$.evaluationWindowSeconds"
          }
        }
        ResultSelector = {
          "healthy.$"          = "$.Payload.healthy"
          "requestCount.$"     = "$.Payload.requestCount"
          "errorRate.$"        = "$.Payload.errorRate"
          "averageLatencyMs.$" = "$.Payload.averageLatencyMs"
          "reasons.$"          = "$.Payload.reasons"
          "failureCode.$"      = "$.Payload.failureCode"
          "metricSource.$"     = "$.Payload.metricSource"
        }
        ResultPath = "$.evaluation25"
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "Healthy25?"
      }

      "Healthy25?" = {
        Type = "Choice"
        Choices = [{
          Variable      = "$.evaluation25.healthy"
          BooleanEquals = true
          Next          = "SetCanary50"
        }]
        Default = "RollbackFrom25"
      }

      SetCanary50 = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["set_weight"].arn
          Payload = {
            "releaseId.$"        = "$.releaseId"
            "serviceName.$"      = "$.serviceName"
            "stableVersion.$"    = "$.stableVersion"
            "candidateVersion.$" = "$.candidateVersion"
            percentage           = 50
            stage                = "CANARY_50"
          }
        }
        ResultSelector = { "stageStartedAtMs.$" = "$.Payload.stageStartedAtMs" }
        ResultPath     = "$.stage50"
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "WaitEvaluation50"
      }

      WaitEvaluation50 = {
        Type        = "Wait"
        SecondsPath = "$.evaluationWindowSeconds"
        Next        = "Evaluate50"
      }

      Evaluate50 = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["evaluate_health"].arn
          Payload = {
            "candidateVersion.$" = "$.candidateVersion"
            "stageStartMs.$"     = "$.stage50.stageStartedAtMs"
            "windowSeconds.$"    = "$.evaluationWindowSeconds"
          }
        }
        ResultSelector = {
          "healthy.$"          = "$.Payload.healthy"
          "requestCount.$"     = "$.Payload.requestCount"
          "errorRate.$"        = "$.Payload.errorRate"
          "averageLatencyMs.$" = "$.Payload.averageLatencyMs"
          "reasons.$"          = "$.Payload.reasons"
          "failureCode.$"      = "$.Payload.failureCode"
          "metricSource.$"     = "$.Payload.metricSource"
        }
        ResultPath = "$.evaluation50"
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "Healthy50?"
      }

      "Healthy50?" = {
        Type = "Choice"
        Choices = [{
          Variable      = "$.evaluation50.healthy"
          BooleanEquals = true
          Next          = "FinalizePromoted"
        }]
        Default = "RollbackFrom50"
      }

      RollbackFrom5 = {
        Type       = "Pass"
        Parameters = { "reason.$" = "$.evaluation5.failureCode" }
        ResultPath = "$.rollback"
        Next       = "SetCanary0"
      }

      RollbackFrom25 = {
        Type       = "Pass"
        Parameters = { "reason.$" = "$.evaluation25.failureCode" }
        ResultPath = "$.rollback"
        Next       = "SetCanary0"
      }

      RollbackFrom50 = {
        Type       = "Pass"
        Parameters = { "reason.$" = "$.evaluation50.failureCode" }
        ResultPath = "$.rollback"
        Next       = "SetCanary0"
      }

      SetCanary0 = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["set_weight"].arn
          Payload = {
            "releaseId.$"        = "$.releaseId"
            "serviceName.$"      = "$.serviceName"
            "stableVersion.$"    = "$.stableVersion"
            "candidateVersion.$" = "$.candidateVersion"
            percentage           = 0
            stage                = "ROLLING_BACK"
            "failureReason.$"    = "$.rollback.reason"
          }
        }
        ResultPath = null
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "FinalizeRolledBack"
      }

      FinalizePromoted = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["finalize_release"].arn
          Payload = {
            "releaseId.$"        = "$.releaseId"
            "serviceName.$"      = "$.serviceName"
            "stableVersion.$"    = "$.stableVersion"
            "candidateVersion.$" = "$.candidateVersion"
            decision             = "PROMOTED"
          }
        }
        ResultPath = null
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "ReleasePromoted"
      }

      FinalizeRolledBack = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.control["finalize_release"].arn
          Payload = {
            "releaseId.$"        = "$.releaseId"
            "serviceName.$"      = "$.serviceName"
            "stableVersion.$"    = "$.stableVersion"
            "candidateVersion.$" = "$.candidateVersion"
            decision             = "ROLLED_BACK"
            "failureReason.$"    = "$.rollback.reason"
          }
        }
        ResultPath = null
        Retry = [{
          ErrorEquals     = ["States.TaskFailed"]
          IntervalSeconds = 2
          MaxAttempts     = 2
          BackoffRate     = 2
        }]
        Next = "ReleaseRolledBack"
      }

      ReleasePromoted   = { Type = "Succeed" }
      ReleaseRolledBack = { Type = "Succeed" }
    }
  })
}

resource "aws_sfn_state_machine" "canary" {
  name       = "CanaryReleaseWorkflow"
  role_arn   = aws_iam_role.sfn.arn
  definition = local.canary_workflow_definition
  type       = "STANDARD"
  depends_on = [aws_iam_role_policy.sfn]
}
