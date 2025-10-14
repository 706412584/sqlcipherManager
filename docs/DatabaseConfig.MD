
---

# DatabaseConfig 类详细用法

`DatabaseConfig` 是 GameCore Database Framework 的数据库配置核心类，采用建造者模式，负责数据库参数初始化、表结构定义、加密、安全选项等所有配置信息。它是创建数据库管理器的必备前置步骤。

---

## 1. 构造方式（建造者模式）

推荐使用 Builder 模式，链式调用配置项，最后 `.build()` 生成不可变配置对象。

```java
DatabaseConfig config = new DatabaseConfig.Builder()
    .setDatabaseName("game.db")                // 数据库名
    .setPassword("secure_password")            // 加密密码
    .setVersion(1)                             // 数据库版本
    .setAutoOptimize(true)                     // 是否自动优化
    .addTableSchema("user", "id INTEGER PRIMARY KEY, name TEXT, level INTEGER") // 表结构定义
    .build();
```

---

## 2. 常用参数说明

| 方法 | 说明 | 类型 |
|------|------|------|
| `setDatabaseName(String)` | 设置数据库名称（或完整路径） | `String` |
| `setPassword(String)` | 设置数据库密码（字符串） | `String` |
| `setPassword(char[])` | 设置数据库密码（字符数组，更安全） | `char[]` |
| `setPassword(byte[])` | 设置数据库密码（二进制，更安全） | `byte[]` |
| `setVersion(int)` | 设置数据库版本号 | `int` |
| `setAutoOptimize(boolean)` | 是否自动优化数据库 | `boolean` |
| `addTableSchema(String, String)` | 添加单张表结构 | `String, String` |
| `addTableSchema(Map<String,String>)` | 批量添加表结构 | `Map` |
| `build()` | 构建配置对象 | `DatabaseConfig` |

---

## 3. 表结构定义

- 单表定义：
    ```java
    .addTableSchema("player", "id INTEGER PRIMARY KEY, name TEXT, exp INTEGER")
    ```
- 批量定义：
    ```java
    Map<String, String> schemas = new HashMap<>();
    schemas.put("user", "id INTEGER PRIMARY KEY, name TEXT");
    schemas.put("item", "id INTEGER PRIMARY KEY, name TEXT, type TEXT");
    .addTableSchema(schemas)
    ```

---

## 4. 密码设置安全建议

优先选用 `char[]` 或 `byte[]`，可规避 Java 字符串常驻内存的安全风险。例如：

```java
char[] pwd = {'s','e','c','r','e','t'};
.setPassword(pwd)
```

---

## 5. 典型场景示例

- **初始化游戏主数据库：**
    ```java
    DatabaseConfig config = new DatabaseConfig.Builder()
        .setDatabaseName("main.db")
        .setPassword("my_game_pwd")
        .setVersion(1)
        .setAutoOptimize(true)
        .addTableSchema("player", "id INTEGER PRIMARY KEY, name TEXT, exp INTEGER")
        .build();
    ```
- **多数据库支持：**
    ```java
    DatabaseConfig config1 = new DatabaseConfig.Builder().setDatabaseName("db1.db").build();
    DatabaseConfig config2 = new DatabaseConfig.Builder().setDatabaseName("db2.db").build();
    ```

---

## 6. 配合 DBCipherManager 使用

```java
DBCipherManager dbManager = DBCipherManager.getInstance(context, config);
```

---

## 7. 高级用法

- 自动优化开启后，数据库会在连接和关闭时自动执行 VACUUM、WAL 模式设置等性能维护操作。
- 可动态添加表结构，然后调用 `.build()`，方便升级或热扩展。

---

## 8. 推荐实践

- **每个独立数据库建议单独配置 DatabaseConfig 实例。**
- **表结构变更时，建议升级版本号并在数据库升级流程中处理字段变更。**
- **密码建议用 PBKDF2 密钥派生算法生成，参考 SqlUtilManager。**

---

## 9. 常见问题

- 如果表结构定义与实际不符，数据库初始化会自动修正或抛出异常（见日志系统）。
- 密码设置错误会导致数据库无法打开，建议统一调用配置口令。

---

## 10. 相关方法速查

| 方法名 | 说明 |
|--------|------|
| `getDatabaseName()` | 获取数据库名 |
| `getPassword()` | 获取数据库密码 |
| `getVersion()` | 获取版本号 |
| `isAutoOptimize()` | 是否自动优化 |
| `getTableSchemas()` | 获取所有表结构定义 |

---

## 11. 设计说明

- 建造者模式、不可变对象，保证配置安全和多线程一致性。
- 支持灵活扩展和批量表结构定义。
- 所有配置项均有默认值，最低配置只需设置数据库名即可。

---

如需进一步细节或遇到特殊场景，欢迎查阅主项目文档或提交 Issue 交流！
