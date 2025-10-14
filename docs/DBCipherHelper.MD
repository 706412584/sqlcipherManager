
---

# DBCipherHelper 类详细用法

`DBCipherHelper` 是 GameCore Database Framework 的底层数据库帮助类，继承自 SQLCipher 的 `SQLiteOpenHelper`，负责数据库文件的创建、加密连接、表结构自动维护、密码修改、安全删除、日志和连接回调等操作。建议与 `DBCipherManager` 配合使用。

---

## 1. 实例创建

通常由 `DBCipherManager` 自动管理，也可独立使用：

```java
// 推荐方式（使用 DatabaseConfig）
DatabaseConfig config = new DatabaseConfig.Builder()
    .setDatabaseName("game.db")
    .setPassword("secure_password")
    .setVersion(1)
    .addTableSchema("user", "id INTEGER PRIMARY KEY, name TEXT, score INTEGER")
    .build();

DBCipherHelper dbHelper = new DBCipherHelper(context, config);
```

也支持直接指定路径和参数：

```java
DBCipherHelper dbHelper = new DBCipherHelper(context, "/sdcard/myapp/game.db", null, 1);
```

---

## 2. 数据库连接与表结构初始化

- 连接并自动建表：

    ```java
    SQLiteDatabase db = dbHelper.connectWithConfig(config);
    ```

- 支持自动创建表和结构迁移，配置表结构时自动校验和补齐字段。

---

## 3. 日志与连接状态回调

- 设置日志回调：

    ```java
    dbHelper.setLogCallback(new DBCipherHelper.LogCallback() {
        @Override
        public void onLog(int level, String tag, String message, Throwable throwable) {
            Log.d(tag, message, throwable);
        }
    });
    ```

- 设置连接回调：

    ```java
    dbHelper.setConnectCallback(new DBCipherHelper.ConnectCallback() {
        @Override
        public void onSuccess(SQLiteDatabase db) {
            Log.i("DB", "连接成功！");
        }
        @Override
        public void onFailed(Throwable throwable, String errorMsg) {
            Log.e("DB", "连接失败：" + errorMsg, throwable);
        }
    });
    ```

---

## 4. 密码管理

- 修改数据库密码（需原密码正确）：

    ```java
    boolean success = dbHelper.changePassword("old_password", "new_password");
    ```

---

## 5. 表结构管理

- 自动建表和字段补齐（外键仅首次建表生效，已有表自动去除外键约束）：

    ```java
    dbHelper.createTablesIfNeeded(db, config.getTableSchemas());
    ```

- 检查和补充缺失字段：

    内部自动完成，无需手动调用。

---

## 6. 查询表结构信息

- 获取指定表的所有字段信息：

    ```java
    List<Map<String, String>> info = dbHelper.getTableStructure(db, "user");
    for (Map<String, String> col : info) {
        String name = col.get("name");
        String type = col.get("type");
        // 其他属性：pk, notnull, dflt_value
    }
    ```

---

## 7. 数据库安全删除

- 删除数据库及相关 WAL/SHM 文件：

    ```java
    boolean deleted = dbHelper.deleteDatabase(context, "/data/data/your.package/databases/game.db");
    ```

---

## 8. 其他辅助功能

- 获取日志回调实例：

    ```java
    DBCipherHelper.LogCallback cb = dbHelper.getLogCallback();
    ```

---

## 9. 内部设计说明

- **自动表结构升级**：表已存在时自动补齐缺失字段（不处理外键）。
- **安全密码修改**：推荐使用 `char[]` 或 `byte[]`，避免明文泄露。
- **支持多数据库文件**：路径可任意指定，并支持 SD 卡、私有目录。
- **日志系统**：支持多级别日志和自定义回调，方便调试和问题定位。
- **物理安全删除**：删除操作会尝试多次，确保文件被彻底移除。

---

## 10. 核心方法速查表

| 方法名 | 说明 | 返回类型 |
|--------|------|----------|
| `connectWithConfig` | 用配置连接加密数据库 | `SQLiteDatabase` |
| `createTablesIfNeeded` | 按配置创建/补充表结构 | `void` |
| `changePassword` | 修改数据库密码 | `boolean` |
| `getTableStructure` | 获取表结构信息 | `List<Map<String,String>>` |
| `deleteDatabase` | 删除数据库及相关文件 | `boolean` |
| `setLogCallback` | 设置日志回调 | `void` |
| `setConnectCallback` | 设置连接状态回调 | `void` |
| `getLogCallback` | 获取当前日志回调实例 | `LogCallback` |

---

## 11. 推荐场景

- 游戏主库、角色库、日志库等多数据库实例的安全管理
- 数据库加密、定期密码轮换
- 自动数据库升级与结构维护
- 安全删除数据库、用户数据保护

---

如需更高级用法或遇到特殊场景，欢迎查阅主项目文档或提交 Issue 交流！
