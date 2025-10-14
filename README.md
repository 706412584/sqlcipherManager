# sqlcipherManager
一个基于安卓sqlcipher数据库加密的封装工具。提供了方便的工具，开箱即用！

#GameCore Database Framework / 游戏核心数据库框架

    QQ706412584-橙子小神
#开发文档

专为 Android 开发者准备的优雅而详细的开发文档，基本都能在文档找到你要的答案，请看："游戏核心数据库框架开发文档"。

#在线示例

+ 数据库配置示例："DatabaseConfig 使用示例"
+ 数据库管理示例："DBCipherManager 使用示例"
+ 表结构管理示例："TableManager 使用示例"

#快速安装

Gradle 依赖安装

在项目的 
"build.gradle" 文件中添加依赖：

dependencies {
    implementation 'game.core:database:1.0.0'
    implementation 'net.zetetic:android-database-sqlcipher:4.5.0'
}

#手动集成安装

>或者，也可以进行手动安装。下载项目源代码后，将 core 包复制到您的 Android 项目中。

#核心特性

DatabaseConfig - 数据库配置类

用于配置数据库参数，支持建造者模式设置数据库名称、密码、版本、自动优化等。
```
DatabaseConfig config = new DatabaseConfig.Builder()
    .setDatabaseName("game.db")
    .setPassword("secure_password")
    .setVersion(1)
    .setAutoOptimize(true)
    .addTableSchema("user", "id INTEGER PRIMARY KEY, name TEXT, level INTEGER")
    .build();
```
#主要方法：

- 
"setDatabaseName(String name)"：设置数据库名称。
- 
"setPassword(String password)"：设置密码（String 格式）。
- 
"setPassword(char[] password)"：设置密码（char[] 格式，更安全）。
- 
"setPassword(byte[] password)"：设置密码（byte[] 格式）。
- 
"setVersion(int version)"：设置数据库版本。
- 
"setAutoOptimize(boolean autoOptimize)"：设置是否自动优化数据库。
- 
"addTableSchema(String tableName, String schema)"：添加表结构定义。
- 
"addTableSchema(Map<String, String> schemas)"：批量添加表结构。
- 
"build()"：构建 DatabaseConfig 实例。


#DBCipherManager - 数据库核心管理器

    负责数据库连接、事务管理和数据操作，支持多数据库实例。

```获取实例对象
DBCipherManager dbManager = DBCipherManager.getInstance(context, config);
```
#主要方法：

- 
"getInstance(Context context, DatabaseConfig config)"：获取数据库管理器实例。
- 
"getConnection()"：获取数据库连接（线程安全）。
- 
"executeWithConnection(DatabaseOperation<T> operation)"：安全执行数据库操作。
- 
"executeTransaction(TransactionRunnable transaction)"：执行事务操作。
- 
"insertData(String tableName, ContentValues values)"：插入数据。
- 
"query(String tableName, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit)"：查询数据。
- 
"updateData(String tableName, ContentValues values, String whereClause, String[] whereArgs)"：更新数据。
- 
"deleteData(String tableName, String whereClause, String[] whereArgs)"：删除数据。
- 
"changePassword(String oldPassword, String newPassword)"：修改数据库密码。
- 
"testConnection()"：测试数据库连接。



#TableManager - 表结构管理

    管理数据库表结构，支持动态添加字段、检查列是否存在等。

```实例获取
TableManager tableManager = dbManager.getTableManager();
tableManager.createTableIfNotExists("user", "id INTEGER PRIMARY KEY, name TEXT");
```

#主要方法：

- 
"createTableIfNotExists(String tableName, String schema)"：创建表（如果不存在）。
- 
"isColumnExists(String tableName, String columnName)"：检查列是否存在。
- 
"addColumnIfNotExists(String tableName, String columnName, String columnType)"：添加列（如果不存在）。
- 
"batchAddColumns(String tableName, List<Map<String, String>> columnDefinitions)"：批量添加列。
- 
"changeColumnType(String tableName, String columnName, String newColumnType)"：修改列类型。
- 
"getTableStructure(String tableName)"：获取表结构信息。

#使用

    初始化数据库

// 创建数据库配置
DatabaseConfig config = new DatabaseConfig.Builder()
    .setDatabaseName("my_game.db")
    .setPassword("my_password")
    .setVersion(1)
    .build();

// 获取数据库管理器实例
DBCipherManager dbManager = DBCipherManager.getInstance(context, config);

// 测试连接
if (dbManager.testConnection()) {
    Log.i("Database", "数据库连接成功");
}

#---------------------------基础数据操作--------------------------

```插入数据：

ContentValues values = new ContentValues();
values.put("name", "玩家1");
values.put("level", 1);
long id = dbManager.insertData("user", values);
```

```查询数据：

List<ContentValues> users = dbManager.query("user", null, null, null, null, null, "level DESC");
```
```更新数据：

ContentValues updateValues = new ContentValues();
updateValues.put("level", 2);
int affected = dbManager.updateDataById("user", 1, updateValues);
```
```删除数据：

int deleted = dbManager.deleteData("user", "level < ?", new String[]{"10"});
```
```事务操作

dbManager.executeTransaction(db -> {
    // 执行多个数据库操作
    dbManager.insertData("table1", values1);
    dbManager.updateData("table2", values2, where, args);
});
```
运行效果，数据操作成功后会输出相应日志。

项目架构

#---------------------------类核心架构---------------------------

``` game.core/
├── DatabaseConfig          # 数据库配置类（建造者模式）
├── DBCipherManager         # 数据库核心管理器
├── DBCipherHelper          # 数据库帮助类（继承 SQLiteOpenHelper）
├── TableManager            # 表结构管理器
├── SqlUtilManager          # SQL 工具管理器
└── DatabaseOptimizer       # 数据库优化器
```



#---------------------------数据库连接管理---------------------------

    框架采用线程安全的连接池管理，支持多数据库实例：

// 多数据库支持
DatabaseConfig config1 = new DatabaseConfig.Builder().setDatabaseName("db1.db").build();
DatabaseConfig config2 = new DatabaseConfig.Builder().setDatabaseName("db2.db").build();

DBCipherManager db1 = DBCipherManager.getInstance(context, config1);
DBCipherManager db极速2 = DBCipherManager.getInstance(context, config2);

#---------------------------高级功能---------------------------

```表结构管理

// 动态添加字段
tableManager.addColumnIfNotExists("user", "experience", "INTEGER DEFAULT 0");

// 批量添加字段
List<Map<String, String>> columns = new ArrayList<>();
Map<String, String> column1 = new HashMap<>();
column1.put("name", "gold");
column1.put("type", "INTEGER");
column1.put("defaultValue", "0");
columns.add(column1);
tableManager.batchAdd极速Columns("user", columns);

```


#---------------------------数据库优化---------------------------

// 执行全面优化
DatabaseOptimizer optimizer = dbManager.getDatabaseOptimizer();
optimizer.optimizeDatabase();

// 检查数据库健康状态
JSONObject healthReport = optimizer.checkDatabaseHealth();


#DatabaseOptimizer 主要方法：

- 
"optimizeDatabase()"：执行全面优化（包括 WAL 模式、缓存调整等）。
- 
"vacuumDatabase()"：执行数据库整理（VACUUM）。
- 
"rebuildIndexes()"：重建所有索引。
- 
"checkDatabaseHealth()"：检查数据库健康状态，返回 JSON 报告。
- 
"getDatabaseSize()"：获取数据库大小（字节）。
- 
"getTableSize(String tableName)"：获取表大小（字节）。

#数据导出

// 导出整个数据库为 JSON
SqlUtilManager utilManager = dbManager.getSqlUtilManager();
JSONObject databaseJson = utilManager.exportDatabaseToJson();

// 导出单表数据
JSONArray tableData = utilManager.exportTableToJson("user");


#SqlUtilManager 主要方法：

- 
"exportDatabaseToJson()"：导出整个数据库为 JSON。
- 
"exportTableToJson(String tableName)"：导出单表数据为 JSON。
- 
"contentValuesToJson(ContentValues values)"：将 ContentValues 转换为 JSONObject。
- 
"jsonToContentValues(JSONObject jsonObject)"：将 JSONObject 转换为 ContentValues。
- 
"jsonToContentValues(String jsonString)"：将 JSON 字符串转换为 ContentValues。

#加密安全

// 使用 PBKDF2 算法派生加密密钥
byte[] encryptionKey = SqlUtilManager.deriveEncryptionKey(
    "username", "clientSecret", "serverToken");

// 修改数据库密码
dbManager.changePassword("new_password");

#安全特性：

- SQLCipher 数据库加密。
- PBKDF2 密钥派生算法。
- 安全的密码管理（使用 
"char[]" 而非 
"String"）。
- 自动清理敏感数据。

#-------------------------日志系统----------------------------

日志级别配置

// 设置日志级别
dbManager.setLogLevel(DBCipherManager.LogLevel.DEBUG);

// 自定义日志回调
DBCipherManager.setLogCallback(new DBCipherManager.LogCallback() {
    @Override
    public void onLog(DBCipherManager.LogLevel level, String tag, String message, Throwable throwable) {
        // 自定义日志处理
        Log.println(levelToPriority(level), tag, message);
        if (throwable != null) {
            Log.e(tag, "Exception: ", throwable);
        }
    }
});

#支持日志级别

- VERBOSE - 详细日志
- DEBUG - 调试信息
- INFO - 一般信息
- WARN - 警告信息
- ERROR - 错误信息
- NONE - 关闭日志

#---------------------------扩展生态---------------------------

工具类扩展

扩展名称 功能描述
SqlUtilManager JSON 与 ContentValues 转换、数据导出
TableManager 动态表结构管理、字段操作
DatabaseOptimizer 数据库性能优化、健康检查

#--------------------------安全特性----------------------------

+ SQLCipher 数据库加密
+ PBKDF2 密钥派生算法
+ 安全的密码管理（
"char[]" 而非 
"String"）
+ 自动清理敏感数据

#推荐使用场景

+ 游戏数据存储 - 玩家信息、游戏进度、装备数据
+ 应用本地缓存 - 用户配置、离线数据
+ 敏感数据保护 - 需要加密存储的用户信息
+ 极速多数据库应用 - 支持同时管理多个数据库

还有问题，怎么办？

#凉拌炒鸡蛋
如发现问题，或者任何问题，欢迎提交 Issue 到 "这里"。

如果喜欢，请帮忙在 "GitHub" 给个 Star。

开源许可协议 / Licence

#Apache 2.0，Apache Licence 是著名的非盈利开源组织 Apache 采用的协议。该协议和 BSD 类似，同样鼓励代码共享和尊重原作者的著作权，同样允许代码修改，再发布（作为开源或商业软件）。

#由橙子游戏开发团队 荣誉出品并持续维护。