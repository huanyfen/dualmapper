# 架构说明

## 分层结构

- **data/**：数据层，包含 Room 实体、DAO、Repository 实现。
- **domain/**：业务逻辑层，包含 UseCase。
- **ui/**：界面层，Jetpack Compose 实现，ViewModel 管理状态。
- **manager/**：功能管理器（连接、编辑、预设等）。
- **service/**：后台服务（悬浮窗、无障碍）。
- **util/**：工具类。

## 依赖注入

使用 Hilt 管理依赖，各层通过构造注入获取所需实例。

## 数据流

UI 事件 -> ViewModel -> UseCase / Repository -> Data Source (Room / Network)  
数据变化通过 Flow 或 StateFlow 反向传递到 UI。

## 连接管理

支持四种连接方式：蓝牙、Wi-Fi Direct、局域网、远程（互联网）。  
统一由 `ConnectionManager` 接口抽象，`ConnectionManagerRegistry` 管理当前激活的连接管理器。

## 安全

- 本地数据库使用 SQLCipher 加密，密钥存储在 EncryptedSharedPreferences。
- 网络传输使用 AES-GCM 加密。
- 密钥交换采用 ECDH。
- 远程连接使用预共享密钥（PSK）进行身份验证。