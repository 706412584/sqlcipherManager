
---

# TableManager 类详细用法

`TableManager` 是 GameCore 数据库框架的表结构管理核心类，负责动态表结构维护、字段操作、表结构导出、批量字段管理、数据库清理等功能。可配合 `DBCipherManager` 实现高效、灵活、安全的表结构控制。

---

## 1. 实例化

建议通过 `DBCipherManager` 获取 TableManager 实例：

```java
DBCipherManager dbManager = DBCipherManager.getInstance(context, config);
TableManager tableManager = dbManager.getTableManager();
```

---

## 2. 表结构检查与操作

### 2.1 判断字段是否存在

```java
boolean exists = tableManager.isColumnExists("user", "score");
```

---

### 2.2 创建表（如果不存在）

```java
String schema = "id INTEGER PRIMARY KEY, name TEXT, score INTEGER";
boolean created = tableManager.createTableIfNotExists("user", schema);
```

---

### 2.3 判断表是否存在

```java
boolean tableExists = tableManager.isTableExists("user");
```

---

### 2.4 获取所有表名

```java
List<String> tables = tableManager.getAllTableNames();
```

---

### 2.5 获取表结构信息

```java
List<Map<String, String>> structure = tableManager.getTableStructure("user");
for (Map<String, String> column : structure) {
    String name = column.get("name");
    String type = column.get("type");
    // 其他属性：pk, notnull, dflt_value
}
```

---

### 2.6 获取全库表结构（JSON）

```java
JSONObject dbStruct = tableManager.getAllTableStructureJson();
// 可用于导出结构或分析
```

---

### 2.7 获取表的第一列名

```java
String firstCol = tableManager.getFirstColumnName(db, "user");
```

---

## 3. 字段操作

### 3.1 批量添加字段

```java
List<Map<String, String>> columns = new ArrayList<>();
Map<String, String> col1 = new HashMap<>();
col1.put("name", "gold");
col1.put("type", "INTEGER");
col1.put("defaultValue", "0");
columns.add(col1);

boolean ok = tableManager.batchAddColumns("user", columns);
```

---

### 3.2 添加单个字段（如果不存在）

```java
boolean added = tableManager.addColumnIfNotExists("user", "level", "INTEGER DEFAULT 0");
```

---

### 3.3 修改字段类型（重建表实现）

```java
boolean typeChanged = tableManager.changeColumnType("user", "score", "REAL");
```

---

## 4. 表数据与结构清理

### 4.1 清空表数据（保留结构）

```java
boolean truncated = tableManager.truncateTable("user");
```

### 4.2 删除表（不保留结构）

```java
boolean dropped = tableManager.dropTable("user");
```

### 4.3 清空所有表数据（保留结构）

```java
int count = tableManager.truncateAllTables();
```

### 4.4 删除所有表（不保留结构）

```java
int count = tableManager.dropAllTables();
```

### 4.5 删除整个数据库（包括所有表和数据）

```java
boolean deleted = tableManager.deleteDatabase();
```

---

## 5. 典型场景

- **动态添加玩家属性字段：**
    ```java
    tableManager.addColumnIfNotExists("player", "exp", "INTEGER DEFAULT 0");
    ```
- **批量迁移旧表结构：**
    ```java
    tableManager.batchAddColumns("archive", columnsList);
    ```
- **自动检测字段并修复：**
    ```java
    if (!tableManager.isColumnExists("user", "email")) {
        tableManager.addColumnIfNotExists("user", "email", "TEXT");
    }
    ```
- **导出数据库结构备份：**
    ```java
    JSONObject struct = tableManager.getAllTableStructureJson();
    // 保存 JSON 到文件或云端
    ```

---

## 6. 日志系统说明

所有操作支持日志输出及回调，便于调试和问题定位。  
日志级别和回调由 `DBCipherManager` 控制，详见主文档。

---

## 7. 设计说明与注意事项

- **字段类型修改通过新表迁移实现，原有数据自动迁移。**
- **批量添加字段会自动跳过已存在字段，确保幂等性。**
- **删除/清空操作均支持事务，保证数据一致性。**
- **主键、自增等特殊字段在重建/导出时会自动保留。**
- **所有操作均通过 DBCipherManager 的安全连接池完成，无需手动管理连接。**

---

## 8. 常用方法速查表

| 方法名 | 说明 | 返回类型 |
|--------|-----|----------|
| `isColumnExists` | 判断字段是否存在 | `boolean` |
| `addColumnIfNotExists` | 添加字段（如不存在） | `boolean` |
| `batchAddColumns` | 批量添加字段 | `boolean` |
| `changeColumnType` | 修改字段类型 | `boolean` |
| `createTableIfNotExists` | 创建表（如不存在） | `boolean` |
| `isTableExists` | 判断表是否存在 | `boolean` |
| `getAllTableNames` | 获取所有表名 | `List<String>` |
| `getTableStructure` | 获取表结构 | `List<Map<String,String>>` |
| `getAllTableStructureJson` | 获取全库表结构（JSON） | `JSONObject` |
| `truncateTable` | 清空表数据 | `boolean` |
| `dropTable` | 删除表 | `boolean` |
| `truncateAllTables` | 清空所有表数据 | `int` |
| `dropAllTables` | 删除所有表 | `int` |
| `deleteDatabase` | 删除整个数据库 | `boolean` |

---

## 9. 建议与扩展

- 推荐在数据库升级/维护时使用表结构检查和字段批量添加功能。
- 支持与 SqlUtilManager 配合实现表结构与数据的完整导入/导出。
- 支持自定义日志回调，便于在大型项目中集成日志分析平台。

---

如需进一步细节或遇到特殊场景，欢迎查阅主项目文档或提交 Issue 交流！
