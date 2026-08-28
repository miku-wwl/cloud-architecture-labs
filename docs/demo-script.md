# Demo script

1. `make up`
2. `make smoke`
3. Open `http://localhost:8080`.
4. Start traffic at 30 RPS.
5. Click **Start Healthy Release** and watch 5 -> 25 -> 50 -> 100 and `PROMOTED`.
6. Reset the demo.
7. Start traffic again, click **Start Error Release**, and observe candidate 5% followed by `ROLLED_BACK` and 0%.
8. Reset and repeat with **Start Slow Release**.

The non-UI equivalents are `scripts/demo.ps1 -Scenario HEALTHY`, `ERROR`, and `SLOW`. Each script polls DynamoDB-backed release state through the API and checks the expected terminal state.
