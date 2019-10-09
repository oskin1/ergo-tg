# Ergo Telegram Wallet

Ergo Telegram Wallet gives user an opportunity to use Ergo wallet from Telegram chat.
As soon as Telegram doesn't allow to execute third party code on the client side Ergo Telegram Wallet
operates as a service and stores seeds on the server in encrypted form. 
Any operations requiring private key access must be authorized by user entering his password.

##### Currently implemented features:
 - `/restore_wallet` - Restore wallet from mnemonic phrase
 - `/create_wallet` - Create new wallet
 - `/balance` - Get balance
 - `/payment` - Send transaction
 - `/cancel` - Cancel current scenario
 
### Screenshots

<img src="https://raw.githubusercontent.com/oskin1/static-data/master/img/wallet_screen_0.png" align="left" width="30%"/>
<img src="https://raw.githubusercontent.com/oskin1/static-data/master/img/wallet_screen_1.png" align="left" width="30%"/>