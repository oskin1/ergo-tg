# Ergo Telegram Wallet design

Ergo Telegram Wallet app pipeline is represented as a `Stream[Task, ?]`.

```
WalletApp
    |-- TransactionTracker
    |   |-- UtxPool
    |   |-- ExplorerService
    |-- Bot
    |   |-- [UserScenarios]
    |   |   |-- WalletService
    |   |   |   |-- UtxPool
    |   |   |   |-- ExplorerService
    |   |   |   |-- WalletRepo
    |   |   |   |   |-- LDBStorage
```
 - `TransactionTracker` - Polls network explorer in order to find confirmed transactions from 
                          unconfirmed transactions pool and notify users who sent them.
 - `Bot` - Handles unbounded number of user-scenarios in parallel.
 - `[UserScenarios]` - A set of chat scenarios bot can handle.
 - `UtxPool` - In-memory persistence for unconfirmed transactions.
 - `WalletService` - Implement actual wallet functionality, such as transaction creation, wallet creation etc.
 - `ExplorerService` - Provides access to the network explorer.
 - `WalletRepo` - Allows to persist a wallet associating it with some chat and read it from persistent storage.
 