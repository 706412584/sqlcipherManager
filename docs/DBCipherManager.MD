
---

# DBCipherManager 类详细用法

`DBCipherManager` 是 GameCore 数据库框架的核心管理类，负责多数据库实例管理、连接池、数据操作（增删改查）、事务控制、批量处理、安全加密、日志系统、维护优化等高级功能，所有操作均基于 SQLCipher 加密数据库。

---

## 1. 实例获取与多库管理

```java
DatabaseConfig config = new DatabaseConfig.Builder()
    .setDatabaseName("game.db")
    .setPassword("your_password")
    .setVersion(1)
    .addTableSchema("user", "id INTEGER PRIMARY KEY, name TEXT, score INTEGER")
    .build();

DBCipherManager dbManager = DBCipherManager.getInstance(context, config);
```
**多库支持：** 每个数据库名/路径对应一个独立实例，自动线程安全。

---

## 2. 日志系统

- 设置日志级别（默认 ERROR）：
    ```java
    dbManager.setLogLevel(DBCipherManager.LogLevel.DEBUG);
    ```
- 设置自定义日志回调：
    ```java
    DBCipherManager.setLogCallback(new DBCipherManager.LogCallback() {
        @Override
        public void onLog(DBCipherManager.LogLevel level, String tag, String message, Throwable throwable) {
            Log.d(tag, message, throwable);
        }
    });
    ```
- 启用/禁用控制台日志：
    ```java
    dbManager.setEnableConsoleLog(true);
    // 支持 VERBOSE, DEBUG, INFO, WARN, ERROR, NONE
    ```

---

## 3. 数据库连接管理

- 自动连接池，线程安全。
- 手动释放或关闭连接：
    ```java
    dbManager.releaseConnection();
    dbManager.closeAllConnections();
    ```
- 移除实例（关闭并删除）：
    ```java
    DBCipherManager.removeInstance("game.db");
    ```

---

## 4. 数据操作

### 4.1 插入数据

- 普通插入
    ```java
    ContentValues values = new ContentValues();
    values.put("name", "玩家A");
    values.put("score", 100);
    long id = dbManager.insertData("user", values);
    ```
- 原生SQL方式插入
    ```java
    long id = dbManager.insertDataWithDetail("user", values);
    ```
- 使用 JSON 插入
    ```java
    long id = dbManager.insertDataWithJson("user", "{\"name\":\"玩家A\",\"score\":100}");
    long id2 = dbManager.insertDataWithJson("user", new JSONObject(...));
    ```
- 批量插入
    ```java
    List<ContentValues> dataList = ...;
    int count = dbManager.batchInsertData("user", dataList);
    // 或
    JSONArray jsonArray = ...;
    int count2 = dbManager.batchInsertDataWithJson("user", jsonArray);
    ```

---

### 4.2 更新数据

- 通用更新
    ```java
    ContentValues updateValues = new ContentValues();
    updateValues.put("score", 200);
    int affected = dbManager.updateData("user", updateValues, "name = ?", new String[]{"玩家A"});
    ```
- 使用 JSON 更新
    ```java
    int affected = dbManager.updateDataWithJson("user", "{\"score\":200}", "name = ?", new String[]{"玩家A"});
    ```
- 按ID更新
    ```java
    int affected = dbManager.updateDataById("user", 1, updateValues);
    ```
- 按ID、JSON更新
    ```java
    int affected = dbManager.updateDataByIdWithJson("user", 1, "{\"score\":100}");
    ```
- 批量更新
    ```java
    List<ContentValues> updateList = ...;
    List<String[]> whereArgsList = ...; // 每行的条件参数
    int successCount = dbManager.batchUpdateData("user", updateList, "name = ?", whereArgsList);
    ```

---

### 4.3 删除数据

- 条件删除
    ```java
    int deleted = dbManager.deleteData("user", "score < ?", new String[]{"50"});
    ```
- 批量删除
    ```java
    List<String[]> whereArgsList = ...;
    int successCount = dbManager.batchDeleteData("user", "score < ?", whereArgsList);
    ```
- 按ID删除
    ```java
    int deleted = dbManager.deleteDataById("user", 1);
    ```
- 按ID列表批量删除
    ```java
    List<Long> ids = Arrays.asList(1L, 2L, 3L);
    int deletedCount = dbManager.deleteDataByIds("user", ids);
    ```
- 使用 IN 子句批量删除（高效）
    ```java
    int affected = dbManager.deleteDataWithInClause("user", "id", ids);
    ```

---

### 4.4 查询数据

- 查询所有
    ```java
    List<ContentValues> allUsers = dbManager.queryAll("user");
    ```
- 条件查询
    ```java
    List<ContentValues> users = dbManager.query("user", "score > ?", new String[]{"50"});
    ```
- 条件+排序
    ```java
    List<ContentValues> users = dbManager.query("user", "score > ?", new String[]{"50"}, "score DESC");
    ```
- 条件+排序+分页
    ```java
    List<ContentValues> users = dbManager.query("user", "score > ?", new String[]{"50"}, "score DESC", "10");
    ```
- 完整参数查询
    ```java
    List<ContentValues> users = dbManager.query("user", new String[] {"name", "score"}, 
        "score > ?", new String[] {"50"}, null, null, "score DESC", "10");
    ```
- 查询单条
    ```java
    ContentValues user = dbManager.querySingle("user", "id = ?", new String[]{"1"});
    ```
- 查询单条并转JSON
    ```java
    JSONObject userJson = dbManager.querySingleTojson("user", "id = ?", new String[]{"1"});
    ```
- 查询数量
    ```java
    long count = dbManager.queryCount("user", "score > ?", new String[]{"50"});
    ```
- 原始SQL查询
    ```java
    List<ContentValues> result = dbManager.rawQuery("SELECT * FROM user WHERE score > ?", new String[]{"50"});
    ```
- 分页查询
    ```java
    List<ContentValues> users = dbManager.queryPaged("user", null, null, null, "score DESC", 2, 10); // 第2页，每页10条
    ```

---

## 5. 事务管理

- 执行事务（可多操作自动回滚/提交）
    ```java
    dbManager.executeTransaction(db -> {
        // db.insert(...)
        // db.update(...)
    });
    ```

---

## 6. 密码管理

- 修改数据库密码
    ```java
    boolean success = dbManager.changePassword("new_password");
    ```

---

## 7. 数据库维护与优化

- 数据库优化（VACUUM）
    ```java
    dbManager.optimizeDatabase();
    ```
- 重建所有索引
    ```java
    dbManager.rebuildIndexes();
    ```
- 检查完整性
    ```java
    boolean ok = dbManager.checkDatabaseIntegrity();
    ```
- 获取数据库/表大小
    ```java
    long dbSize = dbManager.getDatabaseSize();
    long tableSize = dbManager.getTableSize("user");
    ```

---

## 8. 异步数据库操作

```java
dbManager.executeAsync(
    db -> dbManager.queryAll("user"),
    new DBCipherManager.DatabaseCallback<List<ContentValues>>() {
        @Override
        public void onSuccess(List<ContentValues> result) {
            // 处理结果
        }

        @Override
        public void onError(Exception e) {
            // 错误处理
        }
    }
);
```

---

## 9. 连接测试

```java
boolean ok = dbManager.testConnection();
```

---

## 10. 扩展工具访问

- 表结构管理器
    ```java
    TableManager tableManager = dbManager.getTableManager();
    ```
- SQL工具（JSON导入导出等）
    ```java
    SqlUtilManager utilManager = dbManager.getSqlUtilManager();
    ```
- 数值字段批量操作
    ```java
    NumericFieldUpdater updater = dbManager.getNumericFieldUpdater();
    ```

---

## 11. 其他说明

- **所有操作自动连接池管理，无需手动open/close。**
- **支持批量事务、异步处理，适合高并发和数据同步场景。**
- **日志系统可自定义输出和回调，方便调试和问题追踪。**
- **所有数据库操作均需配置正确加密密码，否则无法访问加密数据库。**

---

## 12. 常见场景速查

- 游戏玩家存档、装备、道具批量保存
- 离线缓存数据批量导入/导出
- 敏感数据加密存储
- 多数据库并存（如主库+日志库+云同步库）

---

如需进一步细节或其他组件用法，请查阅主项目文档或相关 API 文档。
