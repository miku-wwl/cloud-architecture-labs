# ADR 002：事件驱动的发布编排

状态：已采纳

API 将发布请求发布到 EventBridge。EventBridge 启动 Step Functions，Step Functions 负责多阶段工作流。这样可以将事件路由与长时间运行的编排解耦，同时让 LocalStack 的资源关系图保持可检查。
