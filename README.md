# GameCore Database Framework / 适用于单机游戏核心数据库框架
## Table of Contents
高效、安全、强扩展性的 Android 数据库解决方案。  
开箱即用，支持 SQLCipher 加密，适用于游戏数据存储、本地缓存、敏感信息保护等场景。

**作者：QQ706412584-橙子小神**

---

## 📚 开发文档

详细使用说明请参考：[游戏核心数据库框架开发文档](#)  
常见问题几乎都能在文档中找到答案。

---

## 🚀 快速安装

**Gradle 集成：**  
在项目 `build.gradle` 中添加依赖：

```gradle
dependencies {
    implementation 'com.github.706412584:sqlcipherManager:v1.0.6'
    implementation 'net.zetetic:android-database-sqlcipher:4.5.0'
}
```
在项目 `build.gradle OR setting.gradle` 中添加仓库：

```gradle
repositories {
    maven{url 'https://jitpack.io'}
}
```


**手动集成：**  
下载源码后，将 `core` 包复制到您的 Android 项目中。

---

## 🌟 核心特性

- **多数据库支持**：线程安全连接池，可同时管理多个数据库实例。
- **自动连接管理**：DBCipherManager 自动维护数据库连接，支持线程安全的连接池，无需手动 open/close，避免因连接泄漏或资源冲突导致的崩溃和性能问题，极大简化多线程和高并发场景下的数据库访问。
- **自动表结构管理**：支持表结构的自动检测和动态维护。表结构变更时会自动校验字段并补齐缺失字段（如新增字段、类型变更等），无需手动升级 SQL，大幅降低维护和扩展成本，适合频繁迭代的业务和游戏数据模型。
- **SQLCipher 加密**：内置 PBKDF2 密钥派生，支持安全密码管理。
- **动态表结构管理**：随时添加/修改字段，检查表结构。
- **强大数据操作**：支持事务、批量操作、数据导出为 JSON。
- **自动数据库优化**：WAL 模式、缓存调整、VACUUM、健康检查。
- **丰富日志系统**：多级别日志和自定义回调。

---

### 详细优点说明

#### 自动表结构管理优势

- **表结构自适应**：初始化或连接时自动检测表结构，缺失字段自动补齐，不影响已有数据。
- **动态扩展**：支持运行时动态添加字段和批量字段管理，适合游戏或业务功能快速扩展。
- **升级无忧**：无需编写复杂的升级 SQL，自动处理表结构变更，降低版本迁移风险。
- **兼容性强**：支持外键、主键、默认值等常见约束，自动处理 SQLite 特性。

#### 自动连接管理优势

- **线程安全**：所有数据库连接由连接池自动管理，适合多线程并发访问场景。
- **资源优化**：自动释放和复用连接，避免资源泄漏和性能瓶颈。
- **开发无感知**：调用数据操作接口时自动管理连接，无需开发者关心 open/close，减少易错点。
- **高性能**：支持批量事务、异步执行，适合大数据量操作和高频读写需求。

---

## 🔧 主要组件 & 用法示例

### 1. DatabaseConfig - 数据库配置

```java
DatabaseConfig config = new DatabaseConfig.Builder()
    .setDatabaseName("game.db")
    .setPassword("secure_password")
    .setVersion(1)
    .setAutoOptimize(true)
    .addTableSchema("user", "id INTEGER PRIMARY KEY, name TEXT, level INTEGER")
    .build();
```

### 2. DBCipherManager - 数据库管理器

```java
DBCipherManager dbManager = DBCipherManager.getInstance(context, config);

if (dbManager.testConnection()) {
    Log.i("Database", "数据库连接成功");
}
```

### 3. TableManager - 表结构管理

```java
TableManager tableManager = dbManager.getTableManager();
tableManager.createTableIfNotExists("user", "id INTEGER PRIMARY KEY, name TEXT");
```

### 4. 基础数据操作

**插入数据：**
```java
ContentValues values = new ContentValues();
values.put("name", "玩家1");
values.put("level", 1);
long id = dbManager.insertData("user", values);
```

**查询数据：**
```java
List<ContentValues> users = dbManager.query("user", null, null, null, null, null, "level DESC");
```

**更新数据：**
```java
ContentValues updateValues = new ContentValues();
updateValues.put("level", 2);
int affected = dbManager.updateDataById("user", 1, updateValues);
```

**删除数据：**
```java
int deleted = dbManager.deleteData("user", "level < ?", new String[]{"10"});
```

**事务操作：**
```java
dbManager.executeTransaction(db -> {
    dbManager.insertData("table1", values1);
    dbManager.updateData("table2", values2, where, args);
});
```

---

## 🏗️ 项目架构

```
game.core/
├── DatabaseConfig        # 数据库配置（建造者模式）
├── DBCipherManager       # 数据库核心管理器
├── DBCipherHelper        # 数据库帮助类（SQLiteOpenHelper）
├── TableManager          # 表结构管理器
├── SqlUtilManager        # SQL工具/数据导出
└── DatabaseOptimizer     # 数据库优化器
```

---

## ⚡ 高级功能

- **动态添加字段**
- **批量字段管理**
- **数据库健康检查与优化**
- **数据 JSON 导出/导入**
- **安全加密/密钥派生**

---

## 🔒 安全特性

- SQLCipher 数据库加密
- PBKDF2 密钥派生算法
- 安全密码管理（推荐使用 `char[]` 类型）
- 自动清理敏感数据

---

## 📝 日志系统

支持多级别日志输出，可自定义日志回调。

```java
dbManager.setLogLevel(DBCipherManager.LogLevel.DEBUG);

DBCipherManager.setLogCallback(new DBCipherManager.LogCallback() {
    @Override
    public void onLog(DBCipherManager.LogLevel level, String tag, String message, Throwable throwable) {
        // 自定义日志处理
    }
});
```
日志级别：VERBOSE, DEBUG, INFO, WARN, ERROR, NONE

---

## 🧩 扩展生态

| 工具类            | 功能描述                |
|-------------------|------------------------|
| SqlUtilManager    | JSON与ContentValues转换、数据导出 |
| TableManager      | 动态表结构管理、字段操作|
| DatabaseOptimizer | 性能优化、健康检查      |

---

## 🎮 推荐使用场景

- 游戏玩家数据、进度、装备等存储
- 应用本地缓存、离线数据管理
- 敏感数据加密存储
- 极速多数据库应用

---

## 📄 DBCipherManager类
查看[详细用法](docs/DBCipherManager.MD)


## 📄 TableManager类
查看[详细用法](docs/TableManager.MD)


## 📄 SqlUtilManager类
查看[详细用法](docs/SqlUtilManager.MD)

## 📄 DatabaseConfig类
查看[详细用法](docs/DatabaseConfig.MD)


## 📄 DBCipherHelper类
查看[详细用法](docs/DBCipherHelper.MD)

## ❓ 有问题怎么办？

如有疑问或发现 Bug，欢迎提交 Issue！  
喜欢请点个 Star ⭐

---

## 📄 许可协议

**Apache 2.0**  
自由修改、商用，尊重原作者著作权。

---

由橙子游戏开发团队荣誉出品并持续维护。
[![](https://jitpack.io/v/706412584/sqlcipherManager.svg)](https://jitpack.io/#706412584/sqlcipherManager)
---

如需更多帮助，请查阅开发文档或联系作者 QQ706412584。

---
