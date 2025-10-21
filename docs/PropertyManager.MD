# PropertyManager 使用说明文档

`PropertyManager` 是一个集成了缓存、加解密、安全数值操作等功能的属性管理类，适用于需要高并发、数值安全、数据加密的场景（如游戏属性管理、用户数据存储等）。  
本说明基于 [`mylibrary/src/main/java/game/data/PropertyManager.java`](https://github.com/706412584/sqlcipherManager/blob/main/mylibrary/src/main/java/game/data/PropertyManager.java) 文件。

---

## 主要功能

- 内存缓存（带加密/解密，提升频繁访问性能）
- 属性安全增减（原子操作、防止负值）
- 多字段批量原子更新（事务保证）
- 支持结构化数据（JSON/ContentValues）属性操作
- 批量数值操作、统计、范围查询

---

## 初始化

```java
// 推荐用法：传入加密密钥
PropertyManager propManager = new PropertyManager(dbManager, "your_encryption_key");

// 或仅传DB管理器（使用默认密钥）
PropertyManager propManager = new PropertyManager(dbManager);
```

---

## 单属性基本操作

### 查询属性

```java
Long value = propManager.getProperty("tableName", "property", 0L);
// 查询指定表的属性值，未找到则返回默认值
```

### 设置属性

```java
propManager.setProperty("tableName", "property", newValue, defaultValue);
// 替换指定属性为新值
```

### 增加/减少属性（数值安全，防负值）

```java
// 增加
Long newVal = propManager.increaseProperty("tableName", "property", 5L, 0L);

// 减少
Long newVal = propManager.decreaseProperty("tableName", "property", 3L, 0L);
```

### 检查属性是否存在

```java
boolean exists = propManager.hasProperty("tableName", "property");
```

---

## 数值操作（原子，缓存支持）

### 安全自增/自减

```java
long result = propManager.safeIncrement("tableName", "recordId", "fieldName", 10);
long result = propManager.safeDecrement("tableName", "recordId", "fieldName", 5);
```

### 批量原子更新

```java
Map<String, Long> updates = new HashMap<>();
updates.put("score", 100L);
updates.put("level", 2L);

boolean success = propManager.updateMultipleFields("tableName", "where id = ?", new String[]{"123"}, updates);
```

---

## 缓存加解密

### 获取带缓存的数值/字符串

```java
long val = propManager.getCachedNumericValue("tableName", "recordId", "fieldName");
String str = propManager.getCachedStringValue("tableName", "recordId", "fieldName");
```

---

## 结构化数据操作（ContentValues/JSON）

支持“增加/减少/替换”操作，自动识别数值字段：

```java
JSONObject json = new JSONObject();
json.put("数量", 10);
json.put("名称", "灵石");

Boolean success = propManager.operateProperty(
    "tableName", "数量", "增加", json, false, "id = ?", new String[]{"123"}
);
```

---

## 批量数值操作（事务）

```java
List<PropertyManager.NumericOperation> operations = new ArrayList<>();
operations.add(new PropertyManager.NumericOperation(
    "op1", "基础属性", "角色ID = ?", new String[]{"player123"},
    "灵石", PropertyManager.NumericOperation.OperationType.INCREMENT, 100
));
operations.add(new PropertyManager.NumericOperation(
    "op2", "基础属性", "角色ID = ?", new String[]{"player123"},
    "仙石", PropertyManager.NumericOperation.OperationType.DECREMENT, 50
));
Map<String, Object> results = propManager.batchNumericOperations("基础属性", operations);
```

---

## 数值统计与范围查询

### 获取统计信息

```java
Map<String, Object> stats = propManager.getNumericFieldStats(
    "tableName", "score", "level > ?", new String[]{"10"}
);
// 包含 count, sum, average, min, max
```

### 范围查询

```java
List<ContentValues> list = propManager.queryByNumericRange(
    "tableName", "score", 100, 200, "level > ?", new String[]{"10"}
);
```

---

## 核心方法简要说明

| 方法 | 功能 | 说明 |
|---|---|---|
| `safeIncrement` | 安全自增 | 原子+缓存+加密 |
| `safeDecrement` | 安全自减 | 防负值，更新缓存 |
| `safeUpdate` | 条件安全更新 | 支持自定义条件 |
| `updateMultipleFields` | 批量原子更新 | 多字段事务 |
| `getCachedNumericValue` | 获取缓存数值 | 加解密缓存 |
| `operateProperty` | 通用属性操作 | 查询/增加/减少/替换/存在检查 |
| `batchNumericOperations` | 批量数值操作 | 支持混合加/减/乘等 |

---

## 注意事项

- 依赖组件：`DBCipherManager`, `NumericFieldUpdater`, `AESUtils`, `SqlUtilManager`
- 推荐所有表有主键字段（如 id/角色ID）
- 默认缓存有效期 5 分钟，超时自动刷新
- 密钥建议每个用户/租户单独分配
- 所有数值操作防止负值（如减少到负数会归零）

---

## 代码示例（批量操作）

```java
// 批量操作示例
public void executeBatchOperations() {
    List<PropertyManager.NumericOperation> operations = new ArrayList<>();
    // 增加灵石
    operations.add(new PropertyManager.NumericOperation(
        "op1", "基础属性", "角色ID = ?", new String[]{"player123"},
        "灵石", PropertyManager.NumericOperation.OperationType.INCREMENT, 100
    ));
    // 减少仙石
    operations.add(new PropertyManager.NumericOperation(
        "op2", "基础属性", "角色ID = ?", new String[]{"player123"},
        "仙石", PropertyManager.NumericOperation.OperationType.DECREMENT, 50
    ));
    Map<String, Object> results = propManager.batchNumericOperations("基础属性", operations);
    // 处理结果
    for (Map.Entry<String, Object> entry : results.entrySet()) {
        String operationId = entry.getKey();
        Object result = entry.getValue();
        System.out.println("操作 " + operationId + " 结果: " + result);
    }
}
```

---

## 扩展与自定义

- 可扩展数值操作类型（如乘、除、求幂、三角函数等）
- 支持自定义缓存策略、加密算法
- 可结合 Android/Server 端跨平台使用

---

## 参考

- [源码 PropertyManager.java](https://github.com/706412584/sqlcipherManager/blob/main/mylibrary/src/main/java/game/data/PropertyManager.java)

如需详细接口参数说明或代码分析，请补充问题！
