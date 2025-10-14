
---

# SqlUtilManager 类详细用法

`SqlUtilManager` 是 GameCore 数据库框架的 SQL 工具类，负责数据与 JSON 的转换、数据库导入导出、PBKDF2 密钥派生等高级功能，是数据同步、备份、加密等场景的理想助手。

---

## 1. 实例化

通常通过 `DBCipherManager` 获取工具类实例：

```java
DBCipherManager dbManager = DBCipherManager.getInstance(context, config);
SqlUtilManager utilManager = dbManager.getSqlUtilManager();
```

---

## 2. 密钥派生与加密安全

### 2.1 PBKDF2 密钥派生

用于生成高强度加密密钥，适合做 SQLCipher 数据库密码：

```java
byte[] encryptionKey = SqlUtilManager.deriveEncryptionKey(
    "username", "clientSecret", "serverToken"
);
// 建议转为字符串或直接用 byte[] 做密码
```

---

## 3. ContentValues 与 JSON 互转

### 3.1 ContentValues → JSONObject

```java
ContentValues values = new ContentValues();
values.put("name", "玩家1");
values.put("score", 100);
JSONObject json = SqlUtilManager.contentValuesToJson(values);
```

### 3.2 JSONObject → ContentValues

```java
ContentValues values = SqlUtilManager.jsonToContentValues(jsonObject);
```

### 3.3 JSON字符串 → ContentValues

```java
String jsonString = "{\"name\":\"玩家1\",\"score\":100}";
ContentValues values = SqlUtilManager.jsonToContentValues(jsonString);
```

---

## 4. 数据库导出功能

### 4.1 导出整个数据库为 JSON

```java
JSONObject dbJson = utilManager.exportDatabaseToJson();
// 可序列化保存本地或上传备份
```

### 4.2 导出指定表为 JSON

```java
Object tableData = utilManager.exportTableToJson("user");
// 返回 JSONObject（单行）或 JSONArray（多行）
```

---

## 5. 数据库导入功能

### 5.1 从 JSON 导入整个数据库

```java
int importedTables = utilManager.importDatabaseFromJson(dbJson, true); // true：导入前清空表
```

### 5.2 导入单表数据

```java
Object userTableData = ...; // 可以是 JSONObject 或 JSONArray
boolean success = utilManager.importDatabaseFromJson(
    new JSONObject().put("tables", new JSONObject().put("user", userTableData)), true
) > 0;
```

---

## 6. 典型场景示例

- **数据备份：**
    ```java
    JSONObject backupJson = utilManager.exportDatabaseToJson();
    // 保存到文件或云端
    ```
- **数据恢复：**
    ```java
    utilManager.importDatabaseFromJson(backupJson, true);
    ```
- **敏感数据加密存储：**
    ```java
    byte[] key = SqlUtilManager.deriveEncryptionKey("user", "pwd", "token");
    // 用于 SQLCipher 数据库密码
    ```

---

## 7. 主要方法速查表

| 方法名 | 说明 | 返回类型 |
|--------|------|----------|
| `deriveEncryptionKey` | PBKDF2密钥派生 | `byte[]` |
| `contentValuesToJson` | ContentValues转JSON | `JSONObject` |
| `jsonToContentValues` | JSONObject转ContentValues | `ContentValues` |
| `jsonToContentValues(String)` | JSON字符串转ContentValues | `ContentValues` |
| `exportDatabaseToJson` | 导出整个数据库为JSON | `JSONObject` |
| `exportTableToJson` | 导出单表数据为JSON | `Object` |
| `importDatabaseFromJson` | 导入整个数据库 | `int` |
| `importTableData` | 导入单表数据（内部方法） | `boolean` |

---

## 8. 注意与扩展

- 支持导出数据库结构 + 数据，便于跨设备、云同步、快速恢复。
- PBKDF2 密钥派生算法，配合 SQLCipher 有效提升安全性。
- 支持 ContentValues 与 JSON 的全类型互转，兼容多种业务场景。
- 批量导入/导出自动事务处理，确保数据一致性。

---

## 9. 推荐使用场景

- 游戏存档和玩家数据的跨设备同步
- 本地离线缓存的结构化导出和恢复
- 敏感数据加密、批量导入/导出
- 日志数据、配置信息的备份与迁移

---

## 10. 日志调试

所有操作会自动调用 DBCipherManager 的日志系统，便于追踪和调试。

---

如需更高级用法或遇到特殊场景，欢迎查阅主项目文档或提交 Issue 交流！
